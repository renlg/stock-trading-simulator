package com.stocktrade.trade;

import com.stocktrade.auth.AuthService;
import com.stocktrade.common.BusinessException;
import com.stocktrade.common.TradingRules;
import com.stocktrade.stock.RealTimeQuoteService;
import com.stocktrade.stock.StockPoolService;
import com.stocktrade.stock.StockQuote;
import com.stocktrade.stock.StockService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TradeService {
    private final JdbcTemplate jdbc;
    private final JdbcTemplate aStockJdbc;
    private final StockService stocks;
    private final StockPoolService pool;
    private final RealTimeQuoteService realTime;

    public TradeService(JdbcTemplate jdbc, @Qualifier("aStockJdbc") JdbcTemplate aStockJdbc,
                        StockService stocks, StockPoolService pool, RealTimeQuoteService realTime) {
        this.jdbc = jdbc;
        this.aStockJdbc = aStockJdbc;
        this.stocks = stocks;
        this.pool = pool;
        this.realTime = realTime;
    }

    @PostConstruct
    void migratePositions() {
        List<Map<String, Object>> columns = jdbc.queryForList("PRAGMA table_info(positions)");
        boolean hasAvailableDate = columns.stream().anyMatch(c -> "available_date".equals(c.get("name")));
        if (!hasAvailableDate) {
            jdbc.execute("ALTER TABLE positions ADD COLUMN available_date TEXT");
        }
    }

    @Transactional
    public Map<String, Object> execute(long userId, String code, String side, Integer quantity, String source) {
        checkTradingSession();
        if (quantity == null || quantity < 1) throw BusinessException.badRequest("交易数量必须为正整数");
        if (!"buy".equals(side) && !"sell".equals(side)) throw BusinessException.badRequest("交易方向只能是buy或sell");

        if ("buy".equals(side) && quantity % 100 != 0) {
            throw BusinessException.badRequest("买入数量必须为100股整数倍");
        }

        StockQuote quote = ensureLoaded(userId, code);

        RealTimeQuoteService.RealtimeQuote rtQuote = realTime.fetchQuote(code);
        double execPrice;
        double prevClose;
        boolean suspended = false;

        if (rtQuote != null) {
            execPrice = rtQuote.price();
            prevClose = rtQuote.prevClose();
            if (execPrice <= 0) {
                execPrice = quote.price();
                prevClose = quote.prevClose();
            }
            if (rtQuote.volume() == 0 && execPrice > 0) {
                suspended = true;
            }
        } else {
            execPrice = quote.price();
            prevClose = quote.prevClose();
        }

        if (execPrice <= 0) throw BusinessException.badRequest("无法获取有效成交价格");

        if (suspended) {
            throw BusinessException.badRequest("股票停牌无法交易");
        }

        double limitPct = TradingRules.getLimitPct(code, quote.name());
        if (prevClose > 0) {
            double limitUp = Math.round(prevClose * (1 + limitPct) * 100.0) / 100.0;
            double limitDown = Math.round(prevClose * (1 - limitPct) * 100.0) / 100.0;
            if ("buy".equals(side) && execPrice >= limitUp) {
                throw BusinessException.badRequest("涨停无法买入");
            }
            if ("sell".equals(side) && execPrice <= limitDown) {
                throw BusinessException.badRequest("跌停无法卖出");
            }
        }

        double amount = execPrice * quantity;
        double commission = TradingRules.calcCommission(amount);

        if ("buy".equals(side)) {
            buy(userId, quote, quantity, amount, execPrice, commission);
        } else {
            sell(userId, code, quantity, amount, execPrice, commission);
        }

        String now = AuthService.now();
        jdbc.update("INSERT INTO orders(user_id,code,side,price,quantity,amount,status,source,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                userId, quote.code(), side, execPrice, quantity, amount, "SUCCESS", source, now);
        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("code", quote.code());
        result.put("side", side);
        result.put("price", execPrice);
        result.put("quantity", quantity);
        result.put("amount", amount);
        result.put("commission", Math.round(commission * 100.0) / 100.0);
        if ("sell".equals(side)) {
            double stampDuty = TradingRules.calcStampDuty(amount);
            result.put("stampDuty", Math.round(stampDuty * 100.0) / 100.0);
        }
        result.put("status", "SUCCESS");
        result.put("source", source);
        result.put("createdAt", now);
        return result;
    }

    public void checkTradingSession() {
        TradingRules.checkTradingSession(jdbc);
    }

    private void buy(long userId, StockQuote quote, int quantity, double amount, double execPrice, double commission) {
        double totalCost = amount + commission;
        Double balance = balance(userId);
        if (balance + 1e-8 < totalCost) throw BusinessException.badRequest("可用资金不足");

        jdbc.update("UPDATE users SET balance=balance-? WHERE id=?", totalCost, userId);

        String availableDate = TradingRules.nextTradingDay();
        var positions = jdbc.query("SELECT quantity,avg_cost FROM positions WHERE user_id=? AND code=?",
                (rs, n) -> new Position(rs.getInt(1), rs.getDouble(2)), userId, quote.code());

        if (positions.isEmpty()) {
            jdbc.update("INSERT INTO positions(user_id,code,quantity,avg_cost,available_date,updated_at) VALUES(?,?,?,?,?,?)",
                    userId, quote.code(), quantity, execPrice, availableDate, AuthService.now());
        } else {
            Position old = positions.get(0);
            int total = old.quantity() + quantity;
            double avg = (old.quantity() * old.avgCost() + amount) / total;
            jdbc.update("UPDATE positions SET quantity=?,avg_cost=?,available_date=?,updated_at=? WHERE user_id=? AND code=?",
                    total, avg, availableDate, AuthService.now(), userId, quote.code());
        }
    }

    private void sell(long userId, String code, int quantity, double amount, double execPrice, double commission) {
        var held = jdbc.query("SELECT quantity,available_date FROM positions WHERE user_id=? AND code=?",
                (rs, n) -> new PositionWithDate(rs.getInt(1), rs.getString(2)), userId, code);
        if (held.isEmpty() || held.get(0).quantity() < quantity) throw BusinessException.badRequest("持仓不足");

        String availableDate = held.get(0).availableDate();
        if (!TradingRules.canSell(availableDate)) {
            throw BusinessException.badRequest("T+1限制：当天买入的股票当天不能卖出");
        }

        double stampDuty = TradingRules.calcStampDuty(amount);
        double netProceeds = amount - commission - stampDuty;

        int remaining = held.get(0).quantity() - quantity;
        if (remaining == 0) {
            jdbc.update("DELETE FROM positions WHERE user_id=? AND code=?", userId, code);
        } else {
            jdbc.update("UPDATE positions SET quantity=?,updated_at=? WHERE user_id=? AND code=?",
                    remaining, AuthService.now(), userId, code);
        }
        jdbc.update("UPDATE users SET balance=balance+? WHERE id=?", netProceeds, userId);
    }

    private double balance(long userId) {
        List<Double> values = jdbc.query("SELECT balance FROM users WHERE id=?", (rs, n) -> rs.getDouble(1), userId);
        if (values.isEmpty()) throw BusinessException.notFound("用户不存在");
        return values.get(0);
    }

    private StockQuote ensureLoaded(long userId, String code) {
        StockQuote quote = stocks.getOrNull(code);
        if (quote != null) return quote;
        Map<String, Object> q = pool.realQuote(code);
        if (q == null) throw BusinessException.notFound("股票不存在或无行情: " + code);
        String name = (String) q.get("name");
        double price = ((Number) q.get("price")).doubleValue();
        double prevClose = ((Number) q.get("prevClose")).doubleValue();
        double high = ((Number) q.get("high")).doubleValue();
        double low = ((Number) q.get("low")).doubleValue();
        StockQuote loaded = new StockQuote(code, name, prevClose, price, high, low, AuthService.now());
        stocks.put(loaded);
        try { pool.addWatch(userId, code); } catch (Exception ignored) {}
        return loaded;
    }

    private record Position(int quantity, double avgCost) {}
    private record PositionWithDate(int quantity, String availableDate) {}
}
