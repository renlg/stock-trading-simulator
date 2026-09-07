# 股票模拟盘：自选股分钟线历史回填方案（2026-09-07）

## 一、问题

用户添加自选股后，新股票的 5 分钟线历史不会补充，只有从"添加时刻"起的数据。

**现状（实测）**：
- 新浪 `getKLineData scale=5&datalen=N` 最多给最近 ~100 根（约 3 个交易日），**无法翻历史**
- 腾讯 `ifzq mkline m5` 同理只给 ~700 根（约 3 个交易日）
- 自选高频流程（30）每 2 分钟拉 **datalen=2 增量**，从加入时刻才开写
- 全市场低频流程（31）每小时 `datalen=100` 但 **只写当天**
- 全市场批量回填（backfill_kline.py）只回填**日线**，不碰分钟线

**后果**：9/03 添加的 688825 长鑫科技，分钟线只覆盖 2026-07-27 起（上市日），而 3/02~7/26 的历史永远空缺；其它 9/02 首批自选 600519/000725/300750 等有 3/02~今全量（因为首日就有全市场流程在跑）。

## 二、数据源选择：baostock（唯一免费长历史分钟线源）

| 项 | 值 |
|----|-----|
| 库 | baostock（服务器已装 00.9.30，`/usr/bin/python3` 直接 import） |
| 接口 | `query_history_k_data_plus("sh.688825", "date,time,open,high,low,close,volume,amount", start, end, frequency="5", adjustflag="3")` |
| **复权** | **必须 `adjustflag=3`（不复权）**——实测与现有新浪 kline_min5 价格完全一致（1311.89 开/1307.61 收）；1 前复权/2 后复权会引入巨大偏差（茅台 09:35 开变 10061） |
| 速度 | 6 个月历史 **0.3s/只**（实测 688825 1344 根） |
| 限制 | `bs.login()` 必须先调（无 token）；返回 `date=2026-07-27, time=20260727093500000` 需格式化；**没有数据的股票（未上市区间）自动跳过** |

## 三、方案：添加时回填（A）+ 每日兜底扫缺口（B）双保险

### 方案 A：模拟盘后端，添加自选时立即回填（改动最小）

**改动点**：`StockPoolService.addWatch()` 成功后，**异步**调"分钟线历史回填"。

```java
// StockPoolService.java
public void addWatch(long userId, String code, String name) {
    // ... 现有逻辑（INSERT OR REPLACE watch_stocks）...
    // 新增：异步回填历史分钟线（不阻塞添加响应）
    min5BackfillExecutor.submit(() -> backfillMin5(code));
}

@Async
public void backfillMin5(String code) {
    // 1. 查 kline_min5 该股最早 trade_time
    //    - 无数据 → 从基准日 2026-03-02 拉
    //    - 有数据且 earliest > 基准日 → 从 earliest 往前推一个交易日拉（补中间缺口）
    //    - 有数据且 earliest <= 基准日 → 已覆盖，跳过
    // 2. 调服务器 baostock（Runtime.exec python3 或新增 Python 脚本 /opt/a-stock/backfill_min5.py）
    // 3. INSERT OR REPLACE 幂等回填
}
```

**关键点**：
- 用 `@Async` + 独立线程池，**不阻塞添加自选的 HTTP 响应**（0.3s 其实也不慢，但保险）
- 幂等：`INSERT OR REPLACE (sec_code, trade_time)` 主键，重复回填无副作用
- baostock 失败（网络/服务不可用）**静默降级**：不抛错、不影响添加功能，次日由方案 B 兜底

**依赖**：服务器需有回填脚本 `/opt/a-stock/backfill_min5.py`（或 Java 直接 Runtime.exec，推荐独立脚本便于复用）

### 方案 B：dataAnalyse 新工作流，每日兜底扫缺口（推荐）

**流程 N「分钟线历史回填」**：
- `start → python(扫缺口) → python(baostock回填) → end`
- **cron `0 40 15 * * ?`**（日线/估值/资金流 15:30-32 之后，错开写锁）
- 逻辑：
  1. 读模拟盘 `watch_stocks` 全部 code（DISTINCT）
  2. 对每只查 `kline_min5` 最早 trade_time，与基准日（2026-03-02）比较，找出有缺口的
  3. baostock 回填缺口区间（单只 0.3s，自选 ~10 只 < 5s）
  4. `INSERT OR REPLACE` 写库
- **复用方案 A 的 `backfill_min5.py`**（参数化：`--code` 单只 / `--all` 全自选）
- 好处：即使方案 A 失败/漏掉/新用户加自选，**次日自动兜底**，永不漏

### 基准日策略（关键决策）

| 选项 | 说明 | 推荐 |
|------|------|------|
| 固定 `2026-03-02` | 与现有最早数据对齐（600519 等首批自选从 3/02 起） | ✅ 推荐 |
| 动态：股票上市日 | 每只从上市日起，但需查上市日（多一次查询） | 次选 |
| 全量 2005 起 | 拉 20 年分钟线，数据量大且免费源不稳 | ❌ |

**推荐固定 `2026-03-02`**：存量基准，简单一致，用户看到的所有股票分钟线都在同一时间轴。

> 注：全市场低频流程（31）是每小时全量跑，新股票 1 小时内也会被拉当天数据；但**历史缺口只有本方案能补**。

## 四、实现任务拆解（给 codex）

### Task 1：写回填脚本 `backfill_min5.py`（服务器 /opt/a-stock/）

```python
# 用法: python3 backfill_min5.py --code 688825 | --all | --code 688825 --full
# 依赖: baostock (已装), sqlite3
# 逻辑:
#   1. bs.login()
#   2. 对每只 code:
#      a. market(code) -> "sh"/"sz" (6/9开头=sh, 否则sz)
#      b. 查 kline_min5 该股 min(trade_time)
#      c. 无数据或 earliest > 基准日 2026-03-02:
#           start = 基准日, end = 今天
#         earliest <= 基准日: 跳过
#      d. bs.query_history_k_data_plus(... adjustflag="3")
#      e. 格式 time "20260727093500000" -> "2026-07-27 09:35"
#      f. INSERT OR REPLACE 写库
#   3. bs.logout()
# 输出: {"codes":N, "filled":N, "rows":N, "errors":[...]}
# 注意: 未上市区间自动跳过(baostock返回空); 失败重试3次; 单只0.3s
```

### Task 2：模拟盘后端加"添加时回填"（方案 A）

- `StockPoolService.addWatch()` 后异步触发
- 新增 `Min5BackfillService`（或复用现有线程池）
- 调 `/opt/a-stock/backfill_min5.py --code <code>`（ProcessBuilder / Runtime.exec）
- 失败静默（写日志不抛错）
- `mvn test` 全绿 + `./deploy.sh`

### Task 3：dataAnalyse 新建流程 N「分钟线历史回填」（方案 B）

- `POST /api/workflows` 创建
- `PUT /api/workflows/{id}/nodes`：start → python(读自选缺口) → python(baostock回填) → end
- `PUT /api/workflows/{id}`：status=active + cron `0 40 15 * * ?`
- 两个 python 节点都 exec `/opt/a-stock/backfill_min5.py` 的函数（`__name__` 防护）

### Task 4：验证

1. 手动触发回填脚本：`python3 backfill_min5.py --code 688825` → 应补 7/27 前缺口（若有）
2. 查库：688825 最早 trade_time 应= 2026-07-27（上市日）或基准日
3. 添加一只新自选（如 000858 五粮液）→ 立即查 kline_min5 → 应有 3/02 起历史
4. 流程 N 手动 run → success + 无新增缺口（幂等）
5. `mvn test`（模拟盘）+ 流程 run 日志

## 五、风险与备选

| 风险 | 应对 |
|------|------|
| baostock 偶发连不上 | 重试 3 次 + 失败静默；方案 B 每日兜底 |
| baostock 不复权 vs 新浪不复权 微小差异 | 实测一致（1311.89/1307.61），收盘价 0.19 差异是接口时点，可接受 |
| 自选很多时回填耗时 | 单只 0.3s，100 只 < 30s；方案 A 异步 + 方案 B 错峰 |
| 新上市股票无历史 | baostock 自动返回空，天然跳过 |
| 与 backfill_kline.py 写锁冲突 | 回填脚本用 `timeout=30` + `busy_timeout=30000` + WAL；cron 错开 15:30-32 |

## 六、不做的事

- ❌ 不做分钟线全量历史（2005 起）——数据量大、免费源不稳、无实际需求
- ❌ 不改新浪/腾讯接口（它们本来就翻不了历史）
- ❌ 不改全市场低频流程（31）行为（当天数据它已覆盖）
