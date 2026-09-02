package com.stocktrade.trade;

import com.stocktrade.common.BusinessException;
import com.stocktrade.stock.RealTimeQuoteService;
import com.stocktrade.stock.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
    @MockBean RealTimeQuoteService realTime;
    private long userId;

    @BeforeEach
    void prepare() {
        // 实时报价 mock 固定返回 100.0, 成交价确定可断言
        Mockito.when(realTime.fetchPrice(Mockito.anyString())).thenReturn(100.0);
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
    void 实时价失败时应降级为本地模拟价成交() {
        // mock 返回 -1 模拟实时接口失败 -> 应降级到本地模拟价
        Mockito.when(realTime.fetchPrice(Mockito.anyString())).thenReturn(-1.0);
        double localPrice = stocks.get("000001").price();
        trades.execute(userId, "000001", "buy", 10, "web");
        assertThat(jdbc.queryForObject("SELECT price FROM orders WHERE code='000001'", Double.class))
                .isEqualTo(localPrice);
    }
}
