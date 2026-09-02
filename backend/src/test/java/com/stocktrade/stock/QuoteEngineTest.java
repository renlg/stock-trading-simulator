package com.stocktrade.stock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:./target/quote-test.db",
        "stock.quote.interval-ms=3600000",
        "stock.quote.persist-ticks=1"
})
class QuoteEngineTest {
    @Autowired QuoteEngine engine;
    @Autowired StockService stocks;

    @Test
    void 单次行情更新应遵守随机游走范围并维护高低价() {
        Map<String, StockQuote> before = stocks.all().stream().collect(Collectors.toMap(StockQuote::code, q -> q));
        engine.updatePricesOnce();

        assertThat(stocks.all()).hasSizeGreaterThanOrEqualTo(20).allSatisfy(after -> {
            StockQuote old = before.get(after.code());
            assertThat(after.price()).isBetween(old.price() * 0.995 - 0.011, old.price() * 1.005 + 0.011);
            assertThat(after.high()).isGreaterThanOrEqualTo(after.price());
            assertThat(after.low()).isLessThanOrEqualTo(after.price());
            assertThat(after.updatedAt()).isNotBlank();
        });
    }
}
