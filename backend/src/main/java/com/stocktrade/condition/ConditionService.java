package com.stocktrade.condition;

import com.stocktrade.auth.AuthService;
import com.stocktrade.common.BusinessException;
import com.stocktrade.stock.StockService;
import com.stocktrade.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConditionService {
    private static final Logger log = LoggerFactory.getLogger(ConditionService.class);
    private final JdbcTemplate jdbc;
    private final StockService stocks;
    private final TradeService trades;

    public ConditionService(JdbcTemplate jdbc, StockService stocks, TradeService trades) {
        this.jdbc = jdbc; this.stocks = stocks; this.trades = trades;
    }

    public Map<String, Object> create(long userId, String code, String type, Double triggerPrice, Integer quantity) {
        if (!"buy".equals(type) && !"sell".equals(type)) throw BusinessException.badRequest("条件单类型只能是buy或sell");
        if (triggerPrice == null || !Double.isFinite(triggerPrice) || triggerPrice <= 0)
            throw BusinessException.badRequest("触发价格必须大于0");
        if (quantity == null || quantity < 1) throw BusinessException.badRequest("交易数量必须为正整数");
        stocks.get(code);
        String now = AuthService.now();
        jdbc.update("INSERT INTO conditions(user_id,code,type,trigger_price,quantity,status,created_at) VALUES(?,?,?,?,?,?,?)",
                userId, code.trim(), type, triggerPrice, quantity, "ACTIVE", now);
        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id); result.put("code", code.trim()); result.put("type", type);
        result.put("triggerPrice", triggerPrice); result.put("quantity", quantity);
        result.put("status", "ACTIVE"); result.put("createdAt", now);
        return result;
    }

    public List<Map<String, Object>> list(long userId) {
        return jdbc.query("SELECT id,code,type,trigger_price,quantity,status,created_at,triggered_at " +
                "FROM conditions WHERE user_id=? ORDER BY id DESC", (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong("id")); row.put("code", rs.getString("code"));
            row.put("type", rs.getString("type")); row.put("triggerPrice", rs.getDouble("trigger_price"));
            row.put("quantity", rs.getInt("quantity")); row.put("status", rs.getString("status"));
            row.put("createdAt", rs.getString("created_at")); row.put("triggeredAt", rs.getString("triggered_at"));
            return row;
        }, userId);
    }

    public void cancel(long userId, long id) {
        int changed = jdbc.update("UPDATE conditions SET status='CANCELLED' WHERE id=? AND user_id=? AND status='ACTIVE'", id, userId);
        if (changed == 0) throw BusinessException.badRequest("条件单不存在或已无法取消");
    }

    public void checkAndTrigger() {
        List<Condition> active = jdbc.query("SELECT id,user_id,code,type,trigger_price,quantity FROM conditions WHERE status='ACTIVE'",
                (rs, n) -> new Condition(rs.getLong("id"), rs.getLong("user_id"), rs.getString("code"),
                        rs.getString("type"), rs.getDouble("trigger_price"), rs.getInt("quantity")));
        for (Condition condition : active) {
            double price;
            try { price = stocks.get(condition.code()).price(); }
            catch (BusinessException e) { mark(condition.id(), "FAILED"); continue; }
            boolean hit = "buy".equals(condition.type()) ? price <= condition.triggerPrice() : price >= condition.triggerPrice();
            if (!hit) continue;
            if (jdbc.update("UPDATE conditions SET status='PROCESSING' WHERE id=? AND status='ACTIVE'", condition.id()) != 1) continue;
            try {
                trades.execute(condition.userId(), condition.code(), condition.type(), condition.quantity(), "condition");
                mark(condition.id(), "TRIGGERED");
                log.info("条件单{}已触发执行", condition.id());
            } catch (RuntimeException e) {
                mark(condition.id(), "FAILED");
                log.warn("条件单{}执行失败：{}", condition.id(), e.getMessage());
            }
        }
    }

    private void mark(long id, String status) {
        jdbc.update("UPDATE conditions SET status=?,triggered_at=? WHERE id=?", status, AuthService.now(), id);
    }

    private record Condition(long id, long userId, String code, String type, double triggerPrice, int quantity) {}
}
