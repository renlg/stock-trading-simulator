package com.stocktrade.condition;

import com.stocktrade.stock.StockQuote;
import com.stocktrade.stock.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:./target/condition-test.db",
        "stock.quote.interval-ms=3600000"
})
class ConditionServiceTest {
    @Autowired ConditionService conditions;
    @Autowired StockService stocks;
    @Autowired JdbcTemplate jdbc;
    private long userId;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM orders"); jdbc.update("DELETE FROM positions");
        jdbc.update("DELETE FROM conditions"); jdbc.update("DELETE FROM auth_tokens");
        jdbc.update("DELETE FROM api_keys"); jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users(username,password_hash,balance,created_at) VALUES('条件测试','x',1000000,'now')");
        userId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        stocks.reload();
    }

    @Test
    void 买入价达到条件时应触发() {
        double price = stocks.get("000001").price();
        conditions.create(userId, "000001", "buy", price, 10);
        conditions.checkAndTrigger();
        assertStatus("TRIGGERED");
        assertThat(jdbc.queryForObject("SELECT quantity FROM positions WHERE user_id=?", Integer.class, userId)).isEqualTo(10);
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
        conditions.create(userId, "000001", "buy", price - 1, 10);
        conditions.checkAndTrigger();
        assertStatus("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }

    @Test
    void 资金不足时应标记失败() {
        jdbc.update("UPDATE users SET balance=0 WHERE id=?", userId);
        double price = stocks.get("600519").price();
        conditions.create(userId, "600519", "buy", price, 1);
        conditions.checkAndTrigger();
        assertStatus("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }

    private void assertStatus(String expected) {
        assertThat(jdbc.queryForObject("SELECT status FROM conditions ORDER BY id DESC LIMIT 1", String.class)).isEqualTo(expected);
    }
}
