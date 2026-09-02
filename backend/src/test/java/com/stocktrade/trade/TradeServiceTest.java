package com.stocktrade.trade;

import com.stocktrade.common.BusinessException;
import com.stocktrade.stock.QuoteEngine;
import com.stocktrade.stock.RealTimeQuoteService;
import com.stocktrade.stock.StockPoolService;
import com.stocktrade.stock.StockQuote;
import com.stocktrade.stock.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:/Users/renlinggao/workspace/stock-trading-simulator/backend/target/trade-test.db",
        "stock.quote.interval-ms=3600000",
        "stock.quote.persist-ticks=999999",
        "newsfeed.base-url=http://127.0.0.1:8891",
        "newsfeed.username=admin",
        "newsfeed.password=admin123"
})
class TradeServiceTest {
    @Autowired TradeService trades;
    @Autowired JdbcTemplate jdbc;
    @Autowired StockService stocks;
    @MockBean RealTimeQuoteService realTime;
    @MockBean StockPoolService pool;
    @MockBean QuoteEngine quoteEngine; // 覆盖真实引擎, 避免真实初始化连库
    private long userId;

    @BeforeEach
    void prepare() {
        // 实时报价 mock 固定返回 100.0, 成交价确定可断言
        Mockito.when(realTime.fetchPrice(Mockito.anyString())).thenReturn(100.0);
        // 股票池 mock: realQuote 返回固定真实价
        Mockito.when(pool.realQuote(Mockito.anyString())).thenReturn(Map.of(
                "code", "000001", "name", "平安银行",
                "price", 11.0, "prevClose", 10.0,
                "high", 11.5, "low", 10.5, "open", 10.8, "time", "2026-09-02 15:00",
                "change", 1.0, "changePct", 10.0));
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM positions");
        jdbc.update("DELETE FROM conditions");
        jdbc.update("DELETE FROM auth_tokens");
        jdbc.update("DELETE FROM api_keys");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM watch_stocks");
        jdbc.update("DELETE FROM stocks");
        jdbc.update("INSERT INTO users(username,password_hash,balance,created_at) VALUES('交易测试','x',1000000,'now')");
        userId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        // 预置 000001 到内存行情(否则 ensureLoaded 走 mock realQuote)
        stocks.reload();
        stocks.put(new StockQuote("000001", "平安银行", 10.0, 11.0, 11.5, 10.5, "now"));
    }

    @Test
    void 买入应扣减资金并建立持仓() {
        trades.execute(userId, "000001", "buy", 100, "web");

        assertThat(jdbc.queryForObject("SELECT balance FROM users WHERE id=?", Double.class, userId))
                .isEqualTo(1_000_000 - 100.0 * 100);
        assertThat(jdbc.queryForObject("SELECT quantity FROM positions WHERE user_id=? AND code='000001'", Integer.class, userId))
                .isEqualTo(100);
    }

    @Test
    void 卖出应增加资金并减少持仓() {
        trades.execute(userId, "000001", "buy", 100, "web");
        trades.execute(userId, "000001", "sell", 40, "web");

        assertThat(jdbc.queryForObject("SELECT quantity FROM positions WHERE user_id=? AND code='000001'", Integer.class, userId))
                .isEqualTo(60);
        assertThat(jdbc.queryForObject("SELECT balance FROM users WHERE id=?", Double.class, userId))
                .isEqualTo(1_000_000 - 100.0 * 60);
    }

    @Test
    void 资金不足时应拒绝买入且不写订单() {
        jdbc.update("UPDATE users SET balance=1 WHERE id=?", userId);
        assertThatThrownBy(() -> trades.execute(userId, "600519", "buy", 1, "web"))
                .isInstanceOf(BusinessException.class).hasMessage("可用资金不足");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }

    @Test
    void 实时价失败时应降级为本地行情价成交() {
        // mock 返回 -1 模拟实时接口失败 -> 应降级到本地行情价
        Mockito.when(realTime.fetchPrice(Mockito.anyString())).thenReturn(-1.0);
        double localPrice = stocks.get("000001").price();
        trades.execute(userId, "000001", "buy", 10, "web");
        assertThat(jdbc.queryForObject("SELECT price FROM orders WHERE code='000001'", Double.class))
                .isEqualTo(localPrice);
    }

    @Test
    void 未在行情池的股票应从真实数据源加载并成交() {
        // 移除预置, 让 ensureLoaded 走 mock realQuote
        jdbc.update("DELETE FROM stocks WHERE code='000001'");
        stocks.reload();
        trades.execute(userId, "000001", "buy", 10, "web");
        assertThat(jdbc.queryForObject("SELECT quantity FROM positions WHERE user_id=? AND code='000001'", Integer.class, userId))
                .isEqualTo(10);
    }
}
