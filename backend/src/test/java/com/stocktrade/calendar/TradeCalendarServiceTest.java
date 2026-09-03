package com.stocktrade.calendar;

import com.stocktrade.stock.QuoteEngine;
import com.stocktrade.stock.RealTimeQuoteService;
import com.stocktrade.stock.StockPoolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:/Users/renlinggao/workspace/stock-trading-simulator/backend/target/calendar-test.db",
        "stock.quote.interval-ms=3600000",
        "stock.quote.persist-ticks=999999",
        "newsfeed.base-url=http://127.0.0.1:8891",
        "newsfeed.username=admin",
        "newsfeed.password=admin123"
})
class TradeCalendarServiceTest {

    @Autowired TradeCalendarService calendarService;
    @Autowired JdbcTemplate jdbc;
    @MockBean StockPoolService pool;
    @MockBean RealTimeQuoteService realTime;
    @MockBean QuoteEngine quoteEngine;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM trade_calendar");
    }

    @Test
    void 标记非交易日应从日历删除() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        jdbc.update("INSERT INTO trade_calendar(trade_date) VALUES(?)", monday.toString());
        assertThat(calendarService.isTradingDay(monday)).isTrue();

        calendarService.markHoliday(monday.toString(), true, "测试节假日");
        assertThat(calendarService.isTradingDay(monday)).isFalse();
    }

    @Test
    void 恢复交易日应插入日历() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        assertThat(calendarService.isTradingDay(monday)).isFalse();

        calendarService.markHoliday(monday.toString(), false, "调休补班");
        assertThat(calendarService.isTradingDay(monday)).isTrue();
    }

    @Test
    void 按年份查询交易日() {
        jdbc.update("INSERT INTO trade_calendar(trade_date) VALUES(?)", "2026-09-07");
        jdbc.update("INSERT INTO trade_calendar(trade_date) VALUES(?)", "2026-09-08");
        jdbc.update("INSERT INTO trade_calendar(trade_date) VALUES(?)", "2027-01-04");

        List<String> days2026 = calendarService.listTradeDays(2026, 2026);
        assertThat(days2026).hasSize(2).contains("2026-09-07", "2026-09-08");

        List<String> daysAll = calendarService.listTradeDays(2026, 2027);
        assertThat(daysAll).hasSize(3);
    }

    @Test
    void 周末不应是交易日() {
        LocalDate saturday = LocalDate.of(2026, 9, 5);
        jdbc.update("INSERT INTO trade_calendar(trade_date) VALUES(?)", saturday.toString());
        assertThat(calendarService.isTradingDay(saturday)).isTrue();
    }
}
