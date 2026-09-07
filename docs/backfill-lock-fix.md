# 日线回填写锁冲突修复记录（2026-09-07）

## 问题
A股日线历史回填 `backfill_kline.py` 每天 01:00 由 cron 启动，跑全市场 12 批（每批 500 只）。因 covered 判断 bug，永远判定所有股票"未覆盖"，进程从早跑到晚（20+ 小时），长期持有 stock.db 写锁 → 与 15:30 起的日线/估值/资金流同步流程（dataAnalyse 流程22/23/24）冲突，导致这些流程从 9/04 起连续 `database is locked` 失败，资金流/日线/估值数据停在 9/04。

## 根因
`backfill_kline.py` 的 covered 判断：
```python
START = "2001-01-01"
if existing_earliest and existing_earliest <= START:
    return 0, "covered"
```
2001-01-01 是元旦休市，第一根 bar 是 2001-01-02。所有股票 earliest=2001-01-02 > 2001-01-01 → **永远判定"未覆盖"** → 每次启动都无限跑 12 批全市场，20+ 小时跑不完，进程跨天叠加（flock 只挡并发启动，不挡已运行进程）。

## 修复
1. **covered 容差**：新增 `START_TOLERANCE = "2001-01-15"`，earliest <= 该值即 covered（元旦 3 天假 + 首个交易日 1/02，容差到 1/15 安全）
2. **全量提前退出**：`run_batch` 返回是否整批 covered（无新增），main 检测到即提前 break
3. **14:00 硬截止**：main 里超 14:00 强制退出，彻底杜绝占用到下午撞同步流程
4. **cron 降频 + timeout**：从每天 `0 1 * * *` 改为每周日 `0 1 * * 0`，且包 `timeout 10800`（3h）——历史补完后每周只需查新入池/新上市股票，3h 足够，且绝不跨天

## 验证
- covered 判断单测：000001/000002/000006（earliest=2001-01-02）全部正确返回 covered，0 次网络拉取
- 全量模式在 14:00 后启动正确触发"提前退出"
- 手动触发流程 24/23/22 补 9/07 数据 → 日线 5202/资金流 5211/估值 5211 行全部落地，VERDICT PASS

---

## 第二层锁冲突：dataAnalyse 流程互撞（同日发现修复）

### 问题
backfill_kline 修好后，**15:30 cron 轮次（13092/13093/13095）仍全部 failed**——排查发现不是 backfill，而是 dataAnalyse 自己的流程互撞：
- 日线22 cron 15:30、估值23 cron 15:31、资金流24 cron 15:32，每个跑 10-20 分钟 → **互相重叠**
- dataAnalyse `WorkflowExecutorManager` **按 workflowId 分线程池，没有全局写锁** → 两个流程同时写同一 SQLite → `database is locked`
- 另有 流程35（财报财务指标 15:45）和 流程30（分钟线高频每2分钟）也写库，加剧冲突

### 修复：cron 大幅错开（每流程跑完下一个才启动）
| 流程 | 旧 cron | 新 cron |
|------|---------|---------|
| 日线22 | 0 30 15 * * ? | 0 30 15 * * ?（不变） |
| 估值23 | 0 31 15 * * ? | 0 0 16 * * ? |
| 资金流24 | 0 32 15 * * ? | 0 30 16 * * ? |

（PUT /api/workflows/{id}，body 需含 name+description+config，config 内 cron+concurrency=1）

### 关键经验
1. **SQLite 单写者**：多个 dataAnalyse 流程写同一库必须串行，cron 要按"每流程最长耗时"错开（估值 ~20min、日线 ~17min、资金流 ~11min）
2. **流程35（财务指标）15:45 与 22/23/24 也会撞**——建议后续也错开或改 17:00 后
3. 手动触发多个流程时**一次只触发一个**，等完成再触发下一个
4. 补数据流程：新浪资金流接口**收盘后 16-18 点才更新当天**（实测 16:11 更新 9/07），15:05 触发拿不到当天资金流；估值接口 15:05 已有

### 验证
- 串行补跑后：日线 9/07 5202 行、资金流 5211 行、估值 5211 行，三流程 success，VERDICT PASS

## 相关文件
- `/opt/a-stock/backfill_kline.py`（服务器，已修复）
- `scripts/backfill_kline.py`（本仓库，归档副本）
