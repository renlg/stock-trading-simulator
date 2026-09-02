# 股票交易模拟器后端

基于 Java 17、Spring Boot 3.2、Spring JDBC 与 SQLite 的自包含 A 股模拟交易服务。系统不连接外部行情源，也不进行真实交易；内置行情引擎每 2 秒按 ±0.5% 随机游走更新价格，并按市场最新模拟价立即成交。

## 本地启动

环境要求：JDK 17、Maven 3.8 或更高版本。

```bash
cd backend
mvn clean package
java -jar target/stock-trading-simulator-1.0.0.jar
```

也可直接运行：

```bash
mvn spring-boot:run
```

服务默认监听 `8080` 端口，数据库自动创建于 `./data/stock.db`。可通过环境变量修改新账户初始资金：

```bash
STOCK_INIT_BALANCE=2000000 java -jar target/stock-trading-simulator-1.0.0.jar --server.port=9090
```

运行测试：

```bash
mvn test
```

## 鉴权与响应格式

除注册和登录外，普通接口需携带登录令牌：

```http
Authorization: Bearer <登录返回的token>
```

`/api/v1/**` 外部接口只接受通过密钥管理接口生成的 API Key：

```http
Authorization: Bearer <apiKey>
```

所有接口使用统一响应包：

```json
{"code": 0, "message": "成功", "data": {}}
```

业务错误使用对应 HTTP 状态码（400、401、404），同时返回非零 `code` 和中文错误消息。

## API 文档

### 注册与登录

| 方法 | 路径 | 请求体/说明 |
| --- | --- | --- |
| POST | `/api/auth/register` | `{"username":"demo","password":"123456"}`，创建初始资金账户 |
| POST | `/api/auth/login` | `{"username":"demo","password":"123456"}`，返回 7 天有效的 32 位十六进制 token |
| GET | `/api/auth/me` | 返回当前用户编号、用户名、余额、创建时间 |

### 行情

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/quote` | 返回全部内置股票行情 |
| GET | `/api/quote/{code}` | 返回单只股票行情 |

行情字段包含 `code`、`name`、`price`、`prevClose`、`change`、`changePct`、`high`、`low`、`updatedAt`。首次启动会写入 24 只常见 A 股。

### 市场价交易

| 方法 | 路径 | 请求体/说明 |
| --- | --- | --- |
| POST | `/api/trade/buy` | `{"code":"000001","quantity":100}` |
| POST | `/api/trade/sell` | `{"code":"000001","quantity":100}` |
| GET | `/api/orders?page=1&size=20` | 成交历史，按编号倒序，`size` 最大为 100 |

数量必须是大于等于 1 的整数。买入按最新模拟价扣款并计算加权平均成本；卖出按最新模拟价入账，持仓归零时自动删除持仓行。

### 条件单

| 方法 | 路径 | 请求体/说明 |
| --- | --- | --- |
| POST | `/api/conditions` | `{"code":"000001","type":"buy","triggerPrice":10.00,"quantity":100}` |
| GET | `/api/conditions` | 返回当前用户全部条件单及状态 |
| POST | `/api/conditions/{id}/cancel` | 取消仍为 `ACTIVE` 的条件单 |

买入条件在最新价格小于等于触发价时执行，卖出条件在最新价格大于等于触发价时执行。状态包括 `ACTIVE`、`PROCESSING`、`TRIGGERED`、`FAILED`、`CANCELLED`。执行前通过条件更新原子抢占，避免重复成交；资金或持仓不足会变为 `FAILED`。

### 账户与持仓

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/account` | 现金、持仓市值、总资产、持仓总盈亏及持仓明细 |
| GET | `/api/portfolio` | 仅返回持仓列表 |

持仓明细包含股票名称、数量、平均成本、最新价、市值、盈亏及盈亏百分比。

### API Key 管理

| 方法 | 路径 | 请求体/说明 |
| --- | --- | --- |
| POST | `/api/apikeys` | `{"name":"量化脚本"}`，完整密钥仅在此次响应展示 |
| GET | `/api/apikeys` | 密钥仅显示前 8 位和 `***`，含最后使用时间 |
| DELETE | `/api/apikeys/{id}` | 删除本人名下的密钥 |

### 外部交易 API

以下接口使用 API Key 鉴权：

| 方法 | 路径 | 请求体/说明 |
| --- | --- | --- |
| GET | `/api/v1/quote/{code}` | 单只股票行情 |
| POST | `/api/v1/orders` | `{"code":"000001","side":"buy","quantity":100}`，`side` 可为 `buy` 或 `sell` |
| GET | `/api/v1/account` | 资金与持仓统计 |
| GET | `/api/v1/orders?page=1&size=20` | 外部接口成交历史 |

## 数据与实现说明

- `schema.sql` 在每次启动时幂等执行，所有表和索引均使用 `IF NOT EXISTS`。
- 资金、持仓和订单写入由 Spring 事务统一管理。
- 行情常驻内存以供即时成交，每 30 个 tick 批量写回 SQLite。
- API Key 当前以明文存储以支持直接查询认证，但除创建响应外不会通过接口返回完整值。
- 该项目仅供模拟和开发用途，不构成投资建议，也不会连接券商或真实市场。
