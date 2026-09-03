package com.stocktrade.account;

import com.stocktrade.common.BusinessException;
import com.stocktrade.stock.StockQuote;
import com.stocktrade.stock.StockService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AccountService {
    private final JdbcTemplate jdbc;
    private final StockService stocks;
    public AccountService(JdbcTemplate jdbc, StockService stocks) { this.jdbc = jdbc; this.stocks = stocks; }

    public Map<String, Object> account(long userId) {
        List<Double> balances = jdbc.query("SELECT balance FROM users WHERE id=?", (rs, n) -> rs.getDouble(1), userId);
        if (balances.isEmpty()) throw BusinessException.notFound("用户不存在");
        List<Map<String, Object>> positions = positions(userId);
        double marketValue = positions.stream().mapToDouble(p -> (double) p.get("marketValue")).sum();
        double totalProfit = positions.stream().mapToDouble(p -> (double) p.get("profit")).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("balance", balances.get(0));
        result.put("marketValue", marketValue);
        result.put("totalAssets", balances.get(0) + marketValue);
        result.put("totalProfit", totalProfit);
        result.put("positions", positions);
        return result;
    }

    public List<Map<String, Object>> positions(long userId) {
        return jdbc.query("SELECT code,quantity,avg_cost,updated_at FROM positions WHERE user_id=? ORDER BY code",
                (rs, n) -> {
                    String code = rs.getString("code");
                    int quantity = rs.getInt("quantity");
                    double avgCost = rs.getDouble("avg_cost");
                    // 行情缓存缺失(如引擎尚未刷新该股)时降级用成本价/代码, 保证持仓接口不整体失败
                    StockQuote quote = stocks.getOrNull(code);
                    double price = quote != null ? quote.price() : avgCost;
                    String name = quote != null ? quote.name() : code;
                    double marketValue = price * quantity;
                    double profit = (price - avgCost) * quantity;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("code", code); row.put("name", name); row.put("quantity", quantity);
                    row.put("avgCost", avgCost); row.put("price", price); row.put("marketValue", marketValue);
                    row.put("profit", profit); row.put("profitPct", avgCost == 0 ? 0 : (price - avgCost) / avgCost * 100);
                    row.put("updatedAt", rs.getString("updated_at"));
                    return row;
                }, userId);
    }

    public Map<String, Object> orders(long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw BusinessException.badRequest("分页参数不正确");
        int offset = page == 0 ? 0 : (page - 1) * size; // 兼容 0 基和 1 基分页
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=?", Integer.class, userId);
        List<Map<String, Object>> items = jdbc.query("SELECT id,code,side,price,quantity,amount,status,source,created_at " +
                        "FROM orders WHERE user_id=? ORDER BY id DESC LIMIT ? OFFSET ?", (rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id")); row.put("code", rs.getString("code"));
                    row.put("side", rs.getString("side")); row.put("price", rs.getDouble("price"));
                    row.put("quantity", rs.getInt("quantity")); row.put("amount", rs.getDouble("amount"));
                    row.put("status", rs.getString("status")); row.put("source", rs.getString("source"));
                    row.put("createdAt", rs.getString("created_at"));
                    return row;
                }, userId, size, offset);
        return Map.of(
                "page", page, "size", size,
                "total", total == null ? 0 : total, "items", items,
                "content", items, "totalElements", total == null ? 0 : total);
    }
}
