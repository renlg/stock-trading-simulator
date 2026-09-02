package com.stocktrade.stock;

import com.stocktrade.common.BusinessException;
import com.stocktrade.auth.AuthService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A股股票池服务:
 * - 搜索全市场(/opt/a-stock stock_pool): 支持代码/名称模糊
 * - 管理关注池 watch_stocks(模拟盘展示的行情池)
 * - 读取真实行情(最新分钟线 + 昨日收盘)
 */
@Service
public class StockPoolService {
    private final JdbcTemplate jdbc;      // 模拟盘自己的库
    private final JdbcTemplate astock;    // /opt/a-stock 只读库

    public StockPoolService(JdbcTemplate jdbc, @Qualifier("aStockJdbc") JdbcTemplate astock) {
        this.jdbc = jdbc;
        this.astock = astock;
    }

    /** 搜索全市场, q 匹配代码或名称, limit 上限 */
    public List<Map<String, Object>> search(String q, int limit) {
        if (q == null || q.trim().isEmpty()) throw BusinessException.badRequest("搜索关键字不能为空");
        String like = "%" + q.trim() + "%";
        return astock.query(
                "SELECT sec_code,sec_name,industry FROM stock_pool WHERE sec_code LIKE ? OR sec_name LIKE ? ORDER BY sec_code LIMIT ?",
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", rs.getString("sec_code"));
                    m.put("name", rs.getString("sec_name"));
                    m.put("industry", rs.getString("industry"));
                    return m;
                }, like, like, Math.min(Math.max(limit, 1), 100));
    }

    /** 关注池列表 */
    public List<Map<String, Object>> watchList() {
        return jdbc.query("SELECT code,name,added_at FROM watch_stocks ORDER BY added_at",
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", rs.getString("code"));
                    m.put("name", rs.getString("name"));
                    m.put("addedAt", rs.getString("added_at"));
                    return m;
                });
    }

    /** 添加关注(股票必须存在于 A股全市场) */
    public void addWatch(String code) {
        if (code == null || code.trim().isEmpty()) throw BusinessException.badRequest("股票代码不能为空");
        code = code.trim();
        // 从 stock_pool 取名称, 不存在则拒绝
        List<String> names = astock.query("SELECT sec_name FROM stock_pool WHERE sec_code=?",
                (rs, n) -> rs.getString(1), code);
        if (names.isEmpty()) throw BusinessException.notFound("股票不存在: " + code);
        String name = names.get(0);
        int rows = jdbc.update("INSERT OR IGNORE INTO watch_stocks(code,name,added_at) VALUES(?,?,?)",
                code, name, AuthService.now());
        if (rows == 0) throw BusinessException.badRequest("股票已在关注池: " + code);
    }

    /** 移除关注 */
    public void removeWatch(String code) {
        if (code == null || code.trim().isEmpty()) throw BusinessException.badRequest("股票代码不能为空");
        jdbc.update("DELETE FROM watch_stocks WHERE code=?", code.trim());
    }

    /** 初始化默认精选池: 空时自动从 A股市场挑一批代表性股票(按市值/成交热度, 取 stock_pool 前 N 只) */
    public void ensureDefaultPool(int count) {
        Integer cnt = jdbc.queryForObject("SELECT COUNT(*) FROM watch_stocks", Integer.class);
        if (cnt != null && cnt > 0) return;
        List<Map<String, Object>> seed = astock.query(
                "SELECT sec_code,sec_name FROM stock_pool ORDER BY sec_code LIMIT ?",
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", rs.getString("sec_code"));
                    m.put("name", rs.getString("sec_name"));
                    return m;
                }, count);
        for (Map<String, Object> s : seed) {
            jdbc.update("INSERT OR IGNORE INTO watch_stocks(code,name,added_at) VALUES(?,?,?)",
                    s.get("code"), s.get("name"), AuthService.now());
        }
    }

    /** 读取单只真实行情: 最新分钟线 + 昨日收盘 */
    public Map<String, Object> realQuote(String code) {
        List<Map<String, Object>> k = astock.query(
                "SELECT trade_time,open,high,low,close FROM kline_min5 WHERE sec_code=? ORDER BY trade_time DESC LIMIT 1",
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("time", rs.getString("trade_time"));
                    m.put("open", rs.getDouble("open"));
                    m.put("high", rs.getDouble("high"));
                    m.put("low", rs.getDouble("low"));
                    m.put("close", rs.getDouble("close"));
                    return m;
                }, code);
        if (k.isEmpty()) return null;
        Map<String, Object> quote = k.get(0);

        // 昨日收盘: kline_daily 倒数第二根(倒数第一是今天)
        List<Double> daily = astock.query(
                "SELECT close FROM kline_daily WHERE sec_code=? ORDER BY trade_date DESC LIMIT 2",
                (rs, n) -> rs.getDouble(1), code);
        double prevClose = daily.size() >= 2 ? daily.get(1) : (daily.size() == 1 ? daily.get(0) : 0);

        // 名称
        List<String> names = astock.query("SELECT sec_name FROM stock_pool WHERE sec_code=?",
                (rs, n) -> rs.getString(1), code);
        String name = names.isEmpty() ? code : names.get(0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("name", name);
        result.put("price", quote.get("close"));
        result.put("prevClose", prevClose);
        result.put("high", quote.get("high"));
        result.put("low", quote.get("low"));
        result.put("open", quote.get("open"));
        result.put("time", quote.get("time"));
        result.put("change", ((Number) quote.get("close")).doubleValue() - prevClose);
        result.put("changePct", prevClose == 0 ? 0 : (((Number) quote.get("close")).doubleValue() - prevClose) / prevClose * 100);
        return result;
    }

    /** 批量读取关注池真实行情 */
    public List<Map<String, Object>> realQuotes(List<String> codes) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String code : codes) {
            Map<String, Object> q = realQuote(code);
            if (q != null) result.add(q);
        }
        return result;
    }
}
