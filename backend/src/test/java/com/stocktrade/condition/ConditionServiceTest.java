package com.stocktrade.condition;

import com.stocktrade.stock.QuoteEngine;
import com.stocktrade.stock.StockQuote;
import com.stocktrade.stock.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:/Users/renlinggao/workspace/stock-trading-simulator/backend/target/condition-test.db",
        "stock.quote.interval-ms=3600000",
        "newsfeed.base-url=http://127.0.0.1:8891",
        "newsfeed.username=admin",
        "newsfeed.password=admin123"
})
class ConditionServiceTest {
    @Autowired ConditionService conditions;
    @Autowired StockService stocks;
    @Autowired JdbcTemplate jdbc;
    @SpyBean com.stocktrade.trade.TradeService trades;
    @MockBean QuoteEngine quoteEngine; // 覆盖真实引擎, 避免真实初始化连 /opt/a-stock
    private long userId;

    @BeforeEach
    void prepare() {
        doNothing().when(trades).checkTradingSession();
        jdbc.update("DELETE FROM orders"); jdbc.update("DELETE FROM positions");
        jdbc.update("DELETE FROM conditions"); jdbc.update("DELETE FROM auth_tokens");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM stocks");
        jdbc.update("INSERT INTO users(username,password_hash,balance,created_at) VALUES('条件测试','x',1000000,'now')");
        userId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        // 预置 000001/600519 到内存行情(QuoteEngine 被 mock, 不会自动填充)
        stocks.reload();
        stocks.put(new StockQuote("000001", "平安银行", 10.0, 11.0, 11.5, 10.5, "now"));
        stocks.put(new StockQuote("600519", "贵州茅台", 1299.56, 1297.5, 1300.0, 1290.0, "now"));
    }

    @Test
    void 买入价达到条件时应触发() {
        double price = stocks.get("000001").price();
        conditions.create(userId, "000001", "buy", price, 100);
        conditions.checkAndTrigger();
        assertStatus("TRIGGERED");
        assertThat(jdbc.queryForObject("SELECT quantity FROM positions WHERE user_id=?", Integer.class, userId)).isEqualTo(100);
    }

    @Test
    void 卖出价达到条件时应触发() {
        StockQuote quote = stocks.get("000001");
        jdbc.update("INSERT INTO positions(user_id,code,quantity,avg_cost,updated_at) VALUES(?,?,?,?,?)",
                userId, quote.code(), 20, quote.price(), "now");
        conditions.create(userId, "000001", "sell", quote.price(), 5);
        conditions.checkAndTrigger();
        assertStatus("TRIGGERED");
        assertThat(jdbc.queryForObject("SELECT quantity FROM positions WHERE user_id=?", Integer.class, userId)).isEqualTo(15);
    }

    @Test
    void 价格未达到时应保持活动() {
        double price = stocks.get("000001").price();
        conditions.create(userId, "000001", "buy", price - 1, 100);
        conditions.checkAndTrigger();
        assertStatus("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }

    @Test
    void 资金不足时应标记失败() {
        jdbc.update("UPDATE users SET balance=0 WHERE id=?", userId);
        double price = stocks.get("600519").price();
        conditions.create(userId, "600519", "buy", price, 100);
        conditions.checkAndTrigger();
        assertStatus("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }

    private void assertStatus(String expected) {
        assertThat(jdbc.queryForObject("SELECT status FROM conditions ORDER BY id DESC LIMIT 1", String.class)).isEqualTo(expected);
    }
}
