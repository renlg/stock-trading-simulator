package com.stocktrade.common;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
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
    void 交易日历为空时退化为工作日判断() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("SELECT COUNT(*) FROM trade_calendar"), eq(Integer.class)))
                .thenReturn(0);
        LocalDate wednesday = LocalDate.of(2026, 3, 4);
        assertThat(TradingRules.isTradingDay(jdbc, wednesday)).isTrue();
    }

    @Test
    void 查询异常时退化为工作日判断() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new RuntimeException("table not found"));
        LocalDate wednesday = LocalDate.of(2026, 3, 4);
        assertThat(TradingRules.isTradingDay(jdbc, wednesday)).isTrue();
    }

    @Test
    void 日历中有今天则为交易日() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalDate today = LocalDate.of(2026, 3, 2);
        when(jdbc.queryForObject(eq("SELECT COUNT(*) FROM trade_calendar"), eq(Integer.class)))
                .thenReturn(6000);
        when(jdbc.queryForObject(contains("WHERE trade_date = ?"), eq(Integer.class), eq(today.toString())))
                .thenReturn(1);
        assertThat(TradingRules.isTradingDay(jdbc, today)).isTrue();
    }

    @Test
    void 日历中无今天则非交易日() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalDate today = LocalDate.of(2026, 3, 4);
        when(jdbc.queryForObject(eq("SELECT COUNT(*) FROM trade_calendar"), eq(Integer.class)))
                .thenReturn(6000);
        when(jdbc.queryForObject(contains("WHERE trade_date = ?"), eq(Integer.class), eq(today.toString())))
                .thenReturn(0);
        assertThat(TradingRules.isTradingDay(jdbc, today)).isFalse();
    }

    @Test
    void 节假日后恢复交易日() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalDate today = LocalDate.of(2026, 3, 2);
        when(jdbc.queryForObject(eq("SELECT COUNT(*) FROM trade_calendar"), eq(Integer.class)))
                .thenReturn(6000);
        when(jdbc.queryForObject(contains("WHERE trade_date = ?"), eq(Integer.class), eq(today.toString())))
                .thenReturn(1);
        assertThat(TradingRules.isTradingDay(jdbc, today)).isTrue();
    }

    @Test
    void checkTradingSession交易时段校验() {
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
