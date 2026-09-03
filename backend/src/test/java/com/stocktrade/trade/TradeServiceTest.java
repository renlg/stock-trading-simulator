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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    @MockBean QuoteEngine quoteEngine;
    private long userId;

    @BeforeEach
    void prepare() {
        Mockito.when(realTime.fetchQuote(Mockito.anyString()))
                .thenReturn(new RealTimeQuoteService.RealtimeQuote(100.0, 95.0, 10000));
        Mockito.when(pool.realQuote(Mockito.anyString())).thenReturn(Map.of(
                "code", "000001", "name", "平安银行",
                "price", 11.0, "prevClose", 10.0,
                "high", 11.5, "low", 10.5, "open", 10.8, "time", "2026-09-02 15:00",
                "change", 1.0, "changePct", 10.0));
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM positions");
        jdbc.update("DELETE FROM conditions");
        jdbc.update("DELETE FROM auth_tokens");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM watch_stocks");
        jdbc.update("DELETE FROM stocks");
        jdbc.update("INSERT INTO users(username,password_hash,balance,created_at) VALUES('交易测试','x',1000000,'now')");
        userId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        stocks.reload();
        stocks.put(new StockQuote("000001", "平安银行", 10.0, 10.5, 11.5, 10.5, "now"));
    }

    @Test
    void 买入应扣减资金并建立持仓() {
        trades.execute(userId, "000001", "buy", 100, "web");

        double commission = Math.max(100.0 * 100 * 0.00025, 5.0);
        double expectedBalance = 1_000_000 - 100.0 * 100 - commission;
        assertThat(jdbc.queryForObject("SELECT balance FROM users WHERE id=?", Double.class, userId))
                .isEqualTo(expectedBalance);
        assertThat(jdbc.queryForObject("SELECT quantity FROM positions WHERE user_id=? AND code='000001'", Integer.class, userId))
                .isEqualTo(100);
    }

    @Test
    void 卖出应增加资金并减少持仓() {
        trades.execute(userId, "000001", "buy", 100, "web");
        String pastDate = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        jdbc.update("UPDATE positions SET available_date=? WHERE user_id=? AND code='000001'",
                pastDate, userId);

        trades.execute(userId, "000001", "sell", 40, "web");

        double buyCommission = Math.max(100.0 * 100 * 0.00025, 5.0);
        double sellAmount = 100.0 * 40;
        double sellCommission = Math.max(sellAmount * 0.00025, 5.0);
        double stampDuty = sellAmount * 0.0005;
        double expectedBalance = 1_000_000 - 100.0 * 60 - buyCommission - sellCommission - stampDuty;

        assertThat(jdbc.queryForObject("SELECT quantity FROM positions WHERE user_id=? AND code='000001'", Integer.class, userId))
                .isEqualTo(60);
        assertThat(jdbc.queryForObject("SELECT balance FROM users WHERE id=?", Double.class, userId))
                .isEqualTo(expectedBalance);
    }

    @Test
    void 资金不足时应拒绝买入且不写订单() {
        jdbc.update("UPDATE users SET balance=1 WHERE id=?", userId);
        assertThatThrownBy(() -> trades.execute(userId, "600519", "buy", 100, "web"))
                .isInstanceOf(BusinessException.class).hasMessage("可用资金不足");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }

    @Test
    void 实时价失败时应降级为本地行情价成交() {
        Mockito.when(realTime.fetchQuote(Mockito.anyString())).thenReturn(null);
        double localPrice = stocks.get("000001").price();
        trades.execute(userId, "000001", "buy", 100, "web");
        assertThat(jdbc.queryForObject("SELECT price FROM orders WHERE code='000001'", Double.class))
                .isEqualTo(localPrice);
    }

    @Test
    void 未在行情池的股票应从真实数据源加载并成交() {
        jdbc.update("DELETE FROM stocks WHERE code='000001'");
        stocks.reload();
        trades.execute(userId, "000001", "buy", 100, "web");
        assertThat(jdbc.queryForObject("SELECT quantity FROM positions WHERE user_id=? AND code='000001'", Integer.class, userId))
                .isEqualTo(100);
    }

    @Test
    void 买入数量非100整数倍应拒绝() {
        assertThatThrownBy(() -> trades.execute(userId, "000001", "buy", 50, "web"))
                .isInstanceOf(BusinessException.class).hasMessage("买入数量必须为100股整数倍");
    }

    @Test
    void 停牌股票应拒绝交易() {
        Mockito.when(realTime.fetchQuote(Mockito.anyString()))
                .thenReturn(new RealTimeQuoteService.RealtimeQuote(100.0, 95.0, 0));
        assertThatThrownBy(() -> trades.execute(userId, "000001", "buy", 100, "web"))
                .isInstanceOf(BusinessException.class).hasMessage("股票停牌无法交易");
    }

    @Test
    void 涨停股票应拒绝买入() {
        Mockito.when(realTime.fetchQuote(Mockito.anyString()))
                .thenReturn(new RealTimeQuoteService.RealtimeQuote(11.0, 10.0, 10000));
        assertThatThrownBy(() -> trades.execute(userId, "000001", "buy", 100, "web"))
                .isInstanceOf(BusinessException.class).hasMessage("涨停无法买入");
    }

    @Test
    void 跌停股票应拒绝卖出() {
        trades.execute(userId, "000001", "buy", 100, "web");
        String pastDate = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        jdbc.update("UPDATE positions SET available_date=? WHERE user_id=? AND code='000001'",
                pastDate, userId);

        Mockito.when(realTime.fetchQuote(Mockito.anyString()))
                .thenReturn(new RealTimeQuoteService.RealtimeQuote(9.0, 10.0, 10000));
        assertThatThrownBy(() -> trades.execute(userId, "000001", "sell", 100, "web"))
                .isInstanceOf(BusinessException.class).hasMessage("跌停无法卖出");
    }

    @Test
    void T1限制当天买入不能当天卖出() {
        trades.execute(userId, "000001", "buy", 100, "web");
        assertThatThrownBy(() -> trades.execute(userId, "000001", "sell", 100, "web"))
                .isInstanceOf(BusinessException.class).hasMessage("T+1限制：当天买入的股票当天不能卖出");
    }
}
