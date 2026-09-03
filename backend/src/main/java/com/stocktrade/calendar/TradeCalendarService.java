package com.stocktrade.calendar;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;

@Service
public class TradeCalendarService {
    private static final Logger log = LoggerFactory.getLogger(TradeCalendarService.class);

    private final JdbcTemplate jdbc;
    private final JdbcTemplate aStockJdbc;

    public TradeCalendarService(JdbcTemplate jdbc,
                                @Qualifier("aStockJdbc") JdbcTemplate aStockJdbc) {
        this.jdbc = jdbc;
        this.aStockJdbc = aStockJdbc;
    }

    @PostConstruct
    void init() {
        migrateNoteColumn();
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM trade_calendar", Integer.class);
        if (count != null && count > 0) {
            log.info("trade_calendar 已有 {} 条记录, 跳过初始化", count);
            return;
        }
        importHistoricalTradingDays();
        prefillFutureWeekdays();
    }

    private void migrateNoteColumn() {
        List<Map<String, Object>> columns = jdbc.queryForList("PRAGMA table_info(trade_calendar)");
        boolean hasNote = columns.stream().anyMatch(c -> "note".equals(c.get("name")));
        if (!hasNote) {
            jdbc.execute("ALTER TABLE trade_calendar ADD COLUMN note TEXT");
        }
    }

    private void importHistoricalTradingDays() {
        try {
            List<String> dates = aStockJdbc.queryForList(
                    "SELECT DISTINCT trade_date FROM kline_daily ORDER BY trade_date", String.class);
            int imported = 0;
            for (String date : dates) {
                int rows = jdbc.update(
                        "INSERT OR IGNORE INTO trade_calendar(trade_date) VALUES(?)", date);
                imported += rows;
            }
            log.info("从 a-stock 导入 {} 个交易日 (共查询 {} 个)", imported, dates.size());
        } catch (Exception e) {
            log.warn("从 a-stock 导入交易日失败: {}", e.getMessage());
        }
    }

    private void prefillFutureWeekdays() {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.of(Year.now().getValue() + 1, 12, 31);
        int count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) continue;
            int rows = jdbc.update(
                    "INSERT OR IGNORE INTO trade_calendar(trade_date) VALUES(?)", d.toString());
            count += rows;
        }
        log.info("预填未来工作日: {} 天 ({} ~ {})", count, start, end);
    }

    public boolean isTradingDay(LocalDate date) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM trade_calendar WHERE trade_date = ?", Integer.class, date.toString());
        return count != null && count > 0;
    }

    public List<String> listTradeDays(int startYear, int endYear) {
        return jdbc.queryForList(
                "SELECT trade_date FROM trade_calendar WHERE trade_date >= ? AND trade_date <= ? ORDER BY trade_date",
                String.class, startYear + "-01-01", endYear + "-12-31");
    }

    public void markHoliday(String date, boolean isHoliday, String note) {
        if (isHoliday) {
            jdbc.update("DELETE FROM trade_calendar WHERE trade_date = ?", date);
        } else {
            jdbc.update("INSERT OR REPLACE INTO trade_calendar(trade_date, note) VALUES(?, ?)", date, note);
        }
    }
}
