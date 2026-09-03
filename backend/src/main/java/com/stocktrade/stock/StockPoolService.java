package com.stocktrade.stock;

import com.stocktrade.common.BusinessException;
import com.stocktrade.auth.AuthService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A股股票池服务:
 * - 搜索全市场(/opt/a-stock stock_pool): 支持代码/名称模糊
 * - 管理关注池 watch_stocks(按 user_id 隔离, 每个账号有自己的自选列表)
 * - 读取真实行情(最新分钟线 + 昨日收盘)
 */
@Service
public class StockPoolService {
    /** SQLite 单语句绑定变量上限为999, IN 列表按100只分批留足余量 */
    private static final int SQL_IN_BATCH = 100;
    /** 昨收回看窗口天数 */
    private static final int PREV_CLOSE_LOOKBACK_DAYS = 30;

    private final JdbcTemplate jdbc;      // 模拟盘自己的库
    private final JdbcTemplate astock;    // /opt/a-stock 只读库
    private final NewsFeedClient newsFeed;

    public StockPoolService(JdbcTemplate jdbc, @Qualifier("aStockJdbc") JdbcTemplate astock, NewsFeedClient newsFeed) {
        this.jdbc = jdbc;
        this.astock = astock;
        this.newsFeed = newsFeed;
    }

    /** 启动迁移: 检测 watch_stocks 是否缺 user_id 列, 缺则 DROP 重建 */
    @PostConstruct
    void migrateWatchStocks() {
        List<Map<String, Object>> columns = jdbc.queryForList("PRAGMA table_info(watch_stocks)");
        boolean hasUserId = columns.stream().anyMatch(c -> "user_id".equals(c.get("name")));
        if (!hasUserId) {
            jdbc.execute("DROP TABLE IF EXISTS watch_stocks");
            jdbc.execute("CREATE TABLE watch_stocks (user_id INTEGER NOT NULL, code TEXT NOT NULL, name TEXT, added_at TEXT, PRIMARY KEY(user_id, code))");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_watch_stocks_user ON watch_stocks(user_id)");
        }
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

    /** 关注池列表(按用户隔离) */
    public List<Map<String, Object>> watchList(long userId) {
        return jdbc.query("SELECT code,name,added_at FROM watch_stocks WHERE user_id=? ORDER BY added_at",
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", rs.getString("code"));
                    m.put("name", rs.getString("name"));
                    m.put("addedAt", rs.getString("added_at"));
                    return m;
                }, userId);
    }

    /** 添加关注(按用户隔离, 股票必须存在于 A股全市场) */
    public void addWatch(long userId, String code) {
        if (code == null || code.trim().isEmpty()) throw BusinessException.badRequest("股票代码不能为空");
        code = code.trim();
        // 从 stock_pool 取名称, 不存在则拒绝
        List<String> names = astock.query("SELECT sec_name FROM stock_pool WHERE sec_code=?",
                (rs, n) -> rs.getString(1), code);
        if (names.isEmpty()) throw BusinessException.notFound("股票不存在: " + code);
        String name = names.get(0);
        int rows = jdbc.update("INSERT OR IGNORE INTO watch_stocks(user_id,code,name,added_at) VALUES(?,?,?,?)",
                userId, code, name, AuthService.now());
        if (rows == 0) throw BusinessException.badRequest("股票已在关注池: " + code);
    }

    /** 移除关注(按用户隔离) */
    public void removeWatch(long userId, String code) {
        if (code == null || code.trim().isEmpty()) throw BusinessException.badRequest("股票代码不能为空");
        jdbc.update("DELETE FROM watch_stocks WHERE user_id=? AND code=?", userId, code.trim());
    }

    /** 所有用户自选股的并集(去重), 供行情引擎全局刷新 */
    public List<Map<String, Object>> allWatchList() {
        return jdbc.query("SELECT DISTINCT code, name FROM watch_stocks",
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", rs.getString("code"));
                    m.put("name", rs.getString("name"));
                    return m;
                });
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

    /** 批量读取真实行情: 每批一次 IN 查询取最新分钟线/昨收/名称, 避免逐只 3 条 SQL 的 N+1 */
    public Map<String, Map<String, Object>> realQuoteBatch(List<String> codes) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (codes == null || codes.isEmpty()) return result;
        List<String> distinct = codes.stream().filter(c -> c != null && !c.isBlank()).distinct().toList();
        if (distinct.isEmpty()) return result;
        String cutoff = prevCloseCutoff();
        for (int i = 0; i < distinct.size(); i += SQL_IN_BATCH) {
            result.putAll(realQuoteBatch0(distinct.subList(i, Math.min(i + SQL_IN_BATCH, distinct.size())), cutoff));
        }
        return result;
    }

    /** 昨收回看窗口起点: 全局最新交易日往前30天(覆盖长假), 窗口内数据不足的股票由单只查询兜底 */
    private String prevCloseCutoff() {
        List<String> latest = astock.query("SELECT MAX(trade_date) FROM kline_daily", (rs, n) -> rs.getString(1));
        if (latest.isEmpty() || latest.get(0) == null) return "0000-00-00";
        return LocalDate.parse(latest.get(0)).minusDays(PREV_CLOSE_LOOKBACK_DAYS).toString();
    }

    private Map<String, Map<String, Object>> realQuoteBatch0(List<String> batch, String cutoff) {
        String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));

        // 最新分钟线: 先按 (sec_code, trade_time) 索引聚合每只最新时间, 再回表取整根K线
        Map<String, Map<String, Object>> latest = new LinkedHashMap<>();
        for (Map<String, Object> row : astock.queryForList("SELECT m.sec_code,m.trade_time,m.open,m.high,m.low,m.close FROM kline_min5 m " +
                "JOIN (SELECT sec_code,MAX(trade_time) AS mt FROM kline_min5 WHERE sec_code IN (" + placeholders + ") GROUP BY sec_code) l " +
                "ON m.sec_code=l.sec_code AND m.trade_time=l.mt", batch.toArray())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", row.get("trade_time"));
            m.put("open", row.get("open"));
            m.put("high", row.get("high"));
            m.put("low", row.get("low"));
            m.put("close", row.get("close"));
            latest.put((String) row.get("sec_code"), m);
        }

        // 昨收: 窗口内每只最新两根日线; 长期停牌导致窗口内不足两根时, 回退单只 LIMIT 2 保证与逐只查询同语义
        Map<String, List<Double>> daily = new LinkedHashMap<>();
        Object[] dailyArgs = java.util.stream.Stream.concat(batch.stream(), java.util.stream.Stream.of(cutoff)).toArray();
        for (Map<String, Object> row : astock.queryForList("SELECT sec_code,close FROM (" +
                "SELECT sec_code,trade_date,close,ROW_NUMBER() OVER (PARTITION BY sec_code ORDER BY trade_date DESC) rn " +
                "FROM kline_daily WHERE sec_code IN (" + placeholders + ") AND trade_date >= ?) " +
                "WHERE rn<=2 ORDER BY sec_code,trade_date DESC", dailyArgs)) {
            daily.computeIfAbsent((String) row.get("sec_code"), k -> new ArrayList<>()).add((Double) row.get("close"));
        }
        for (String code : batch) {
            if (daily.getOrDefault(code, List.of()).size() >= 2) continue;
            List<Double> closes = astock.query("SELECT close FROM kline_daily WHERE sec_code=? ORDER BY trade_date DESC LIMIT 2",
                    (rs, n) -> rs.getDouble(1), code);
            if (!closes.isEmpty()) daily.put(code, closes);
        }

        Map<String, String> names = new LinkedHashMap<>();
        for (Map<String, Object> row : astock.queryForList("SELECT sec_code,sec_name FROM stock_pool WHERE sec_code IN (" + placeholders + ")",
                batch.toArray())) {
            names.put((String) row.get("sec_code"), (String) row.get("sec_name"));
        }

        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String code : batch) {
            Map<String, Object> k = latest.get(code);
            if (k == null) continue; // 无分钟线数据, 跳过
            List<Double> closes = daily.get(code);
            double prevClose = closes == null || closes.isEmpty() ? 0 : (closes.size() >= 2 ? closes.get(1) : closes.get(0));
            double price = ((Number) k.get("close")).doubleValue();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("code", code);
            r.put("name", names.getOrDefault(code, code));
            r.put("price", price);
            r.put("prevClose", prevClose);
            r.put("high", k.get("high"));
            r.put("low", k.get("low"));
            r.put("open", k.get("open"));
            r.put("time", k.get("time"));
            r.put("change", price - prevClose);
            r.put("changePct", prevClose == 0 ? 0 : (price - prevClose) / prevClose * 100);
            result.put(code, r);
        }
        return result;
    }

    /** 查询股票名称 */
    public String stockName(String code) {
        List<String> names = astock.query("SELECT sec_name FROM stock_pool WHERE sec_code=?",
                (rs, n) -> rs.getString(1), code);
        return names.isEmpty() ? code : names.get(0);
    }

    /** 日线K线: 取最近 limit 根, 按时间正序返回 */
    public List<Map<String, Object>> klineDaily(String code, int limit) {
        List<Map<String, Object>> rows = astock.query(
                "SELECT trade_date,open,close,high,low,volume,amount,pct_chg FROM kline_daily WHERE sec_code=? ORDER BY trade_date DESC LIMIT ?",
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("time", rs.getString("trade_date"));
                    m.put("open", rs.getDouble("open"));
                    m.put("close", rs.getDouble("close"));
                    m.put("high", rs.getDouble("high"));
                    m.put("low", rs.getDouble("low"));
                    m.put("volume", rs.getDouble("volume"));
                    m.put("amount", rs.getDouble("amount"));
                    m.put("pctChg", rs.getDouble("pct_chg"));
                    return m;
                }, code, limit);
        java.util.Collections.reverse(rows);
        return rows;
    }

    /** 5分钟K线: 取最近 limit 根, 按时间正序返回 */
    public List<Map<String, Object>> klineMin5(String code, int limit) {
        List<Map<String, Object>> rows = astock.query(
                "SELECT trade_time,open,close,high,low,volume,amount FROM kline_min5 WHERE sec_code=? ORDER BY trade_time DESC LIMIT ?",
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("time", rs.getString("trade_time"));
                    m.put("open", rs.getDouble("open"));
                    m.put("close", rs.getDouble("close"));
                    m.put("high", rs.getDouble("high"));
                    m.put("low", rs.getDouble("low"));
                    m.put("volume", rs.getDouble("volume"));
                    m.put("amount", rs.getDouble("amount"));
                    return m;
                }, code, limit);
        java.util.Collections.reverse(rows);
        return rows;
    }

    /** 股票详情页聚合数据 */
    public Map<String, Object> stockDetail(String code) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("name", stockName(code));
        result.put("valuation", valuation(code, 30));
        result.put("moneyflow", moneyflow(code, 20));
        result.put("holder", holderNum(code));
        result.put("margin", margin(code, 20));
        result.put("consensus", consensus(code, 5));
        result.put("northbound", northbound(code));
        result.put("financial", newsFeed.financial(code));
        result.put("events", newsFeed.events(code));
        return result;
    }

    /** 估值数据: 最近 limit 期, 按日期倒序 */
    public List<Map<String, Object>> valuation(String code, int limit) {
        try {
            return astock.query(
                    "SELECT trade_date,pe_ttm,pb,ps_ttm,total_mv,circ_mv,div_yield FROM valuation WHERE sec_code=? ORDER BY trade_date DESC LIMIT ?",
                    (rs, n) -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("tradeDate", rs.getString("trade_date"));
                        m.put("peTtm", rs.getDouble("pe_ttm"));
                        m.put("pb", rs.getDouble("pb"));
                        m.put("psTtm", rs.getDouble("ps_ttm"));
                        m.put("totalMv", rs.getDouble("total_mv"));
                        m.put("circMv", rs.getDouble("circ_mv"));
                        m.put("divYield", rs.getDouble("div_yield"));
                        return m;
                    }, code, limit);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 资金流向: 最近 limit 条, 按日期倒序 */
    public List<Map<String, Object>> moneyflow(String code, int limit) {
        try {
            return astock.query(
                    "SELECT trade_date,main_net,super_net,big_net,mid_net,small_net FROM moneyflow WHERE sec_code=? ORDER BY trade_date DESC LIMIT ?",
                    (rs, n) -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("tradeDate", rs.getString("trade_date"));
                        m.put("mainNet", rs.getDouble("main_net"));
                        m.put("superNet", rs.getDouble("super_net"));
                        m.put("bigNet", rs.getDouble("big_net"));
                        m.put("midNet", rs.getDouble("mid_net"));
                        m.put("smallNet", rs.getDouble("small_net"));
                        return m;
                    }, code, limit);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 股东户数: 全部季度数据 */
    public List<Map<String, Object>> holderNum(String code) {
        try {
            return astock.query(
                    "SELECT end_date,holder_num,holder_num_chg,avg_hold FROM holder_num WHERE sec_code=? ORDER BY end_date DESC",
                    (rs, n) -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("endDate", rs.getString("end_date"));
                        m.put("holderNum", rs.getDouble("holder_num"));
                        m.put("holderNumChg", rs.getDouble("holder_num_chg"));
                        m.put("avgHold", rs.getDouble("avg_hold"));
                        return m;
                    }, code);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 两融数据: 最近 limit 条, 按日期倒序 */
    public List<Map<String, Object>> margin(String code, int limit) {
        try {
            return astock.query(
                    "SELECT trade_date,rz_balance,rq_volume,rzrq_balance,rq_balance,rq_mcl,rzrq_chg FROM margin WHERE sec_code=? ORDER BY trade_date DESC LIMIT ?",
                    (rs, n) -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("tradeDate", rs.getString("trade_date"));
                        m.put("rzBalance", rs.getDouble("rz_balance"));
                        m.put("rqVolume", rs.getDouble("rq_volume"));
                        m.put("rzrqBalance", rs.getDouble("rzrq_balance"));
                        m.put("rqBalance", rs.getDouble("rq_balance"));
                        m.put("rqMcl", rs.getDouble("rq_mcl"));
                        m.put("rzrqChg", rs.getDouble("rzrq_chg"));
                        return m;
                    }, code, limit);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 一致预期: 最近 limit 条 */
    public List<Map<String, Object>> consensus(String code, int limit) {
        try {
            return astock.query(
                    "SELECT sec_name,rating_org_num,rating_buy,rating_add,rating_neutral,rating_reduce,rating_sale,eps1,year1,eps2,year2,eps3,year3,eps4,year4,aimprice_max,aimprice_min,fetch_date FROM consensus WHERE sec_code=? ORDER BY fetch_date DESC LIMIT ?",
                    (rs, n) -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("secName", rs.getString("sec_name"));
                        m.put("ratingOrgNum", rs.getInt("rating_org_num"));
                        m.put("ratingBuy", rs.getInt("rating_buy"));
                        m.put("ratingAdd", rs.getInt("rating_add"));
                        m.put("ratingNeutral", rs.getInt("rating_neutral"));
                        m.put("ratingReduce", rs.getInt("rating_reduce"));
                        m.put("ratingSale", rs.getInt("rating_sale"));
                        m.put("eps1", rs.getDouble("eps1"));
                        m.put("year1", rs.getString("year1"));
                        m.put("eps2", rs.getDouble("eps2"));
                        m.put("year2", rs.getString("year2"));
                        m.put("eps3", rs.getDouble("eps3"));
                        m.put("year3", rs.getString("year3"));
                        m.put("eps4", rs.getDouble("eps4"));
                        m.put("year4", rs.getString("year4"));
                        m.put("aimpriceMax", rs.getDouble("aimprice_max"));
                        m.put("aimpriceMin", rs.getDouble("aimprice_min"));
                        m.put("fetchDate", rs.getString("fetch_date"));
                        return m;
                    }, code, limit);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 北向持股: 全部季度数据 */
    public List<Map<String, Object>> northbound(String code) {
        try {
            return astock.query(
                    "SELECT end_date,sec_name,hold_shares,hold_shares_ratio,hold_market_cap,org_quantity,total_shares_ratio,date_type FROM northbound_hold WHERE sec_code=? ORDER BY end_date DESC",
                    (rs, n) -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("endDate", rs.getString("end_date"));
                        m.put("secName", rs.getString("sec_name"));
                        m.put("holdShares", rs.getDouble("hold_shares"));
                        m.put("holdSharesRatio", rs.getDouble("hold_shares_ratio"));
                        m.put("holdMarketCap", rs.getDouble("hold_market_cap"));
                        m.put("orgQuantity", rs.getInt("org_quantity"));
                        m.put("totalSharesRatio", rs.getDouble("total_shares_ratio"));
                        m.put("dateType", rs.getString("date_type"));
                        return m;
                    }, code);
        } catch (Exception e) {
            return List.of();
        }
    }
}
