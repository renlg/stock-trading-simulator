package com.stocktrade.trade;

import com.stocktrade.common.BusinessException;
import com.stocktrade.stock.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:./target/trade-test.db",
        "stock.quote.interval-ms=3600000"
})
class TradeServiceTest {
    @Autowired TradeService trades;
    @Autowired JdbcTemplate jdbc;
    @Autowired StockService stocks;
    private long userId;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM positions");
        jdbc.update("DELETE FROM conditions");
        jdbc.update("DELETE FROM auth_tokens");
        jdbc.update("DELETE FROM api_keys");
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users(username,password_hash,balance,created_at) VALUES('交易测试','x',1000000,'now')");
        userId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        stocks.reload();
    }

    @Test
    void 买入应扣减资金并建立持仓() {
        double price = stocks.get("000001").price();
        trades.execute(userId, "000001", "buy", 100, "web");

        assertThat(jdbc.queryForObject("SELECT balance FROM users WHERE id=?", Double.class, userId))
                .isEqualTo(1_000_000 - price * 100);
        assertThat(jdbc.queryForObject("SELECT quantity FROM positions WHERE user_id=? AND code='000001'", Integer.class, userId))
                .isEqualTo(100);
    }

    @Test
    void 卖出应增加资金并减少持仓() {
        double price = stocks.get("000001").price();
        trades.execute(userId, "000001", "buy", 100, "web");
        trades.execute(userId, "000001", "sell", 40, "web");

        assertThat(jdbc.queryForObject("SELECT quantity FROM positions WHERE user_id=? AND code='000001'", Integer.class, userId))
                .isEqualTo(60);
        assertThat(jdbc.queryForObject("SELECT balance FROM users WHERE id=?", Double.class, userId))
                .isEqualTo(1_000_000 - price * 60);
    }

    @Test
    void 资金不足时应拒绝买入且不写订单() {
        jdbc.update("UPDATE users SET balance=1 WHERE id=?", userId);
        assertThatThrownBy(() -> trades.execute(userId, "600519", "buy", 1, "web"))
                .isInstanceOf(BusinessException.class).hasMessage("可用资金不足");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }
}
