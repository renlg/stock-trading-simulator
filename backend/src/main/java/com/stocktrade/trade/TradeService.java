package com.stocktrade.trade;

import com.stocktrade.auth.AuthService;
import com.stocktrade.common.BusinessException;
import com.stocktrade.stock.StockQuote;
import com.stocktrade.stock.StockService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TradeService {
    private final JdbcTemplate jdbc;
    private final StockService stocks;
    public TradeService(JdbcTemplate jdbc, StockService stocks) { this.jdbc = jdbc; this.stocks = stocks; }

    @Transactional
    public Map<String, Object> execute(long userId, String code, String side, Integer quantity, String source) {
        if (quantity == null || quantity < 1) throw BusinessException.badRequest("交易数量必须为正整数");
        if (!"buy".equals(side) && !"sell".equals(side)) throw BusinessException.badRequest("交易方向只能是buy或sell");
        StockQuote quote = stocks.get(code);
        double amount = quote.price() * quantity;
        if ("buy".equals(side)) buy(userId, quote, quantity, amount);
        else sell(userId, quote, quantity, amount);
        String now = AuthService.now();
        jdbc.update("INSERT INTO orders(user_id,code,side,price,quantity,amount,status,source,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                userId, quote.code(), side, quote.price(), quantity, amount, "SUCCESS", source, now);
        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id); result.put("code", quote.code()); result.put("side", side);
        result.put("price", quote.price()); result.put("quantity", quantity); result.put("amount", amount);
        result.put("status", "SUCCESS"); result.put("source", source); result.put("createdAt", now);
        return result;
    }

    private void buy(long userId, StockQuote quote, int quantity, double amount) {
        Double balance = balance(userId);
        if (balance + 1e-8 < amount) throw BusinessException.badRequest("可用资金不足");
        jdbc.update("UPDATE users SET balance=balance-? WHERE id=?", amount, userId);
        var positions = jdbc.query("SELECT quantity,avg_cost FROM positions WHERE user_id=? AND code=?",
                (rs, n) -> new Position(rs.getInt(1), rs.getDouble(2)), userId, quote.code());
        if (positions.isEmpty()) {
            jdbc.update("INSERT INTO positions(user_id,code,quantity,avg_cost,updated_at) VALUES(?,?,?,?,?)",
                    userId, quote.code(), quantity, quote.price(), AuthService.now());
        } else {
            Position old = positions.get(0);
            int total = old.quantity() + quantity;
            double avg = (old.quantity() * old.avgCost() + amount) / total;
            jdbc.update("UPDATE positions SET quantity=?,avg_cost=?,updated_at=? WHERE user_id=? AND code=?",
                    total, avg, AuthService.now(), userId, quote.code());
        }
    }

    private void sell(long userId, StockQuote quote, int quantity, double amount) {
        var held = jdbc.query("SELECT quantity FROM positions WHERE user_id=? AND code=?",
                (rs, n) -> rs.getInt(1), userId, quote.code());
        if (held.isEmpty() || held.get(0) < quantity) throw BusinessException.badRequest("持仓不足");
        int remaining = held.get(0) - quantity;
        if (remaining == 0) jdbc.update("DELETE FROM positions WHERE user_id=? AND code=?", userId, quote.code());
        else jdbc.update("UPDATE positions SET quantity=?,updated_at=? WHERE user_id=? AND code=?",
                remaining, AuthService.now(), userId, quote.code());
        jdbc.update("UPDATE users SET balance=balance+? WHERE id=?", amount, userId);
    }

    private double balance(long userId) {
        List<Double> values = jdbc.query("SELECT balance FROM users WHERE id=?", (rs, n) -> rs.getDouble(1), userId);
        if (values.isEmpty()) throw BusinessException.notFound("用户不存在");
        return values.get(0);
    }

    private record Position(int quantity, double avgCost) {}
}
