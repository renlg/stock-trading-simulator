package com.stocktrade.stock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** realQuoteBatch 批量行情查询 SQL 语义测试: 用临时 SQLite 库模拟 /opt/a-stock 只读库 */
class StockPoolServiceTest {
    @TempDir
    Path tempDir;

    private StockPoolService pool;

    @BeforeEach
    void setUp() {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("astock.db"));
        JdbcTemplate astock = new JdbcTemplate(ds);
        astock.execute("CREATE TABLE stock_pool (sec_code TEXT, sec_name TEXT, industry TEXT)");
        astock.execute("CREATE TABLE kline_daily (sec_code TEXT, trade_date TEXT, open REAL, close REAL, high REAL, low REAL, volume REAL, amount REAL, pct_chg REAL)");
        astock.execute("CREATE TABLE kline_min5 (sec_code TEXT, trade_time TEXT, open REAL, high REAL, low REAL, close REAL, volume REAL, amount REAL)");
        astock.execute("CREATE INDEX idx_kline_daily ON kline_daily(sec_code, trade_date)");
        astock.execute("CREATE INDEX idx_kline_min5 ON kline_min5(sec_code, trade_time)");

        // 正常股票: 昨收取倒数第二根日线
        astock.update("INSERT INTO stock_pool VALUES('000001','平安银行','银行')");
        insertDaily(astock, "000001", "2026-08-28", 10.0);
        insertDaily(astock, "000001", "2026-09-01", 10.5);
        insertDaily(astock, "000001", "2026-09-02", 11.0);
        insertMin5(astock, "000001", "2026-09-02 14:55:00", 10.7, 10.75, 10.65, 10.7);
        insertMin5(astock, "000001", "2026-09-02 15:00:00", 10.5, 11.0, 10.6, 10.9);

        // 长期停牌股票: 日线均在30天回看窗口外, 走单只回退查询
        astock.update("INSERT INTO stock_pool VALUES('000002','万科A','地产')");
        insertDaily(astock, "000002", "2026-05-01", 8.0);
        insertDaily(astock, "000002", "2026-05-02", 8.5);
        insertMin5(astock, "000002", "2026-05-02 15:00:00", 8.3, 8.6, 8.2, 8.4);

        // 超过单批上限(100只)的一组股票, 验证分批后结果完整
        for (int i = 1; i <= 150; i++) {
            String code = String.valueOf(600000 + i);
            astock.update("INSERT INTO stock_pool VALUES(?,'批量测试股','行业')", code);
            insertDaily(astock, code, "2026-09-01", 4.5);
            insertDaily(astock, code, "2026-09-02", 5.0);
            insertMin5(astock, code, "2026-09-02 15:00:00", 4.8, 5.1, 4.7, 5.0);
        }

        pool = new StockPoolService(Mockito.mock(JdbcTemplate.class), astock, Mockito.mock(NewsFeedClient.class));
    }

    @Test
    void 批量查询结果应与单只查询一致() {
        Map<String, Map<String, Object>> batch = pool.realQuoteBatch(List.of("000001", "000002", "000003"));
        assertThat(batch).containsKey("000001");
        assertThat(batch.get("000001")).isEqualTo(pool.realQuote("000001"));
    }

    @Test
    void 长期停牌股票应回退单只查询取昨收() {
        Map<String, Map<String, Object>> batch = pool.realQuoteBatch(List.of("000002"));
        Map<String, Object> q = batch.get("000002");
        assertThat(q).isNotNull();
        assertThat(((Number) q.get("price")).doubleValue()).isEqualTo(8.4);
        assertThat(((Number) q.get("prevClose")).doubleValue()).isEqualTo(8.0);
    }

    @Test
    void 无分钟线数据的股票不出现在批量结果中() {
        assertThat(pool.realQuoteBatch(List.of("000003"))).doesNotContainKey("000003");
    }

    @Test
    void 超过单批上限应分批查询且结果完整() {
        List<String> codes = new ArrayList<>();
        for (int i = 1; i <= 150; i++) codes.add(String.valueOf(600000 + i));
        codes.add("000001");
        Map<String, Map<String, Object>> batch = pool.realQuoteBatch(codes);
        assertThat(batch).hasSize(151);
        Map<String, Object> q = batch.get("600150");
        assertThat(((Number) q.get("price")).doubleValue()).isEqualTo(5.0);
        assertThat(((Number) q.get("prevClose")).doubleValue()).isEqualTo(4.5);
    }

    private void insertDaily(JdbcTemplate astock, String code, String date, double close) {
        astock.update("INSERT INTO kline_daily(sec_code,trade_date,close) VALUES(?,?,?)", code, date, close);
    }

    private void insertMin5(JdbcTemplate astock, String code, String time, double open, double high, double low, double close) {
        astock.update("INSERT INTO kline_min5(sec_code,trade_time,open,high,low,close) VALUES(?,?,?,?,?,?)",
                code, time, open, high, low, close);
    }
}
