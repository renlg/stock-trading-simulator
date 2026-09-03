package com.stocktrade.stock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:/Users/renlinggao/workspace/stock-trading-simulator/backend/target/quote-test.db",
        "stock.quote.interval-ms=3600000",
        "stock.quote.persist-ticks=1",
        "newsfeed.base-url=http://127.0.0.1:8891",
        "newsfeed.username=admin",
        "newsfeed.password=admin123"
})
class QuoteEngineTest {
    @Autowired QuoteEngine engine;
    @Autowired StockService stocks;
    @MockBean StockPoolService pool;

    @BeforeEach
    void prepare() {
        stocks.reload();
        stocks.put(new StockQuote("000001", "平安银行", 10.0, 10.5, 10.8, 10.2, "now"));
        // mock 所有用户自选并集 + 批量真实行情
        Mockito.when(pool.allWatchList()).thenReturn(List.of(
                Map.of("code", "000001", "name", "平安银行")
        ));
        Mockito.when(pool.realQuoteBatch(Mockito.anyList())).thenReturn(Map.of(
                "000001", Map.of(
                        "code", "000001", "name", "平安银行",
                        "price", 10.9, "prevClose", 10.0,
                        "high", 11.0, "low", 10.6, "open", 10.5, "time", "2026-09-02 15:00",
                        "change", 0.9, "changePct", 9.0)));
    }

    @Test
    void 单次行情更新应从真实数据源拉取并维护高低价() {
        engine.updatePricesOnce();
        StockQuote q = stocks.get("000001");
        // 价格来自真实数据(10.9), 非随机游走
        assertThat(q.price()).isEqualTo(10.9);
        // 昨收来自真实数据(10.0)
        assertThat(q.prevClose()).isEqualTo(10.0);
        // 高低价取历史最高/最低(引擎内已有 10.8/10.2 与真实 11.0/10.6 合并)
        assertThat(q.high()).isEqualTo(11.0);
        assertThat(q.low()).isEqualTo(10.2);
        assertThat(q.updatedAt()).isNotBlank();
    }
}
