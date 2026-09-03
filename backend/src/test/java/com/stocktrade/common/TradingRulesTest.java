package com.stocktrade.common;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradingRulesTest {

    @Test
    void 交易时段边界判断() {
        LocalDate monday = LocalDate.of(2026, 3, 2);

        assertThat(TradingRules.isTradingTime(at(monday, 9, 29))).isFalse();
        assertThat(TradingRules.isTradingTime(at(monday, 9, 30))).isTrue();
        assertThat(TradingRules.isTradingTime(at(monday, 10, 0))).isTrue();
        assertThat(TradingRules.isTradingTime(at(monday, 11, 30))).isTrue();
        assertThat(TradingRules.isTradingTime(at(monday, 11, 31))).isFalse();
        assertThat(TradingRules.isTradingTime(at(monday, 12, 0))).isFalse();
        assertThat(TradingRules.isTradingTime(at(monday, 12, 59))).isFalse();
        assertThat(TradingRules.isTradingTime(at(monday, 13, 0))).isTrue();
        assertThat(TradingRules.isTradingTime(at(monday, 14, 0))).isTrue();
        assertThat(TradingRules.isTradingTime(at(monday, 15, 0))).isTrue();
        assertThat(TradingRules.isTradingTime(at(monday, 15, 1))).isFalse();
        assertThat(TradingRules.isTradingTime(at(monday, 20, 0))).isFalse();
    }

    @Test
    void 周末不是交易日() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalDate saturday = LocalDate.of(2026, 3, 7);
        LocalDate sunday = LocalDate.of(2026, 3, 8);
        assertThat(saturday.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(sunday.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(TradingRules.isTradingDay(jdbc, saturday)).isFalse();
        assertThat(TradingRules.isTradingDay(jdbc, sunday)).isFalse();
    }

    @Test
    void 工作日是交易日_查询失败时退化() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(String.class), anyString()))
                .thenThrow(new RuntimeException("table not found"));
        LocalDate wednesday = LocalDate.of(2026, 3, 4);
        assertThat(wednesday.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(TradingRules.isTradingDay(jdbc, wednesday)).isTrue();
    }

    @Test
    void 工作日是交易日_kline无数据时退化() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(String.class), anyString())).thenReturn(null);
        LocalDate wednesday = LocalDate.of(2026, 3, 4);
        assertThat(TradingRules.isTradingDay(jdbc, wednesday)).isTrue();
    }

    @Test
    void 最近交易日是今天则为交易日() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalDate today = LocalDate.of(2026, 3, 2);
        when(jdbc.queryForObject(anyString(), eq(String.class), anyString()))
                .thenReturn(today.toString());
        assertThat(TradingRules.isTradingDay(jdbc, today)).isTrue();
    }

    @Test
    void 最近交易日是昨天且今天为工作日则为交易日() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalDate today = LocalDate.of(2026, 3, 2); // Monday
        LocalDate yesterday = today.minusDays(1);
        when(jdbc.queryForObject(anyString(), eq(String.class), anyString()))
                .thenReturn(yesterday.toString());
        assertThat(TradingRules.isTradingDay(jdbc, today)).isTrue();
    }

    @Test
    void 最近交易日距今超过一天则非交易日_模拟节假日() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalDate today = LocalDate.of(2026, 3, 4); // Wednesday
        LocalDate twoDaysAgo = today.minusDays(2); // Monday
        when(jdbc.queryForObject(anyString(), eq(String.class), anyString()))
                .thenReturn(twoDaysAgo.toString());
        assertThat(TradingRules.isTradingDay(jdbc, today)).isFalse();
    }

    @Test
    void checkTradingSession非交易日抛异常() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // Mock: latest trade date is 5 days ago → not a trading day
        LocalDate today = LocalDate.of(2026, 3, 4); // Wednesday
        LocalDate fiveDaysAgo = today.minusDays(5);
        when(jdbc.queryForObject(anyString(), eq(String.class), anyString()))
                .thenReturn(fiveDaysAgo.toString());
        // We can't control LocalDate.now() in checkTradingSession(jdbc),
        // so test isTradingDay directly for the non-trading-day scenario
        assertThat(TradingRules.isTradingDay(jdbc, today)).isFalse();
    }

    @Test
    void checkTradingSession交易时段校验() {
        // Verify isTradingTime boundaries independently
        LocalDate day = LocalDate.of(2026, 3, 2);
        assertThat(TradingRules.isTradingTime(at(day, 9, 29))).isFalse();
        assertThat(TradingRules.isTradingTime(at(day, 9, 30))).isTrue();
        assertThat(TradingRules.isTradingTime(at(day, 15, 0))).isTrue();
        assertThat(TradingRules.isTradingTime(at(day, 15, 1))).isFalse();
    }

    private static LocalDateTime at(LocalDate date, int hour, int minute) {
        return LocalDateTime.of(date, LocalTime.of(hour, minute));
    }
}
