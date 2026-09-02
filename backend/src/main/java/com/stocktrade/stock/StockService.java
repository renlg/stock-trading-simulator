package com.stocktrade.stock;

import com.stocktrade.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StockService {
    private final JdbcTemplate jdbc;
    private final Map<String, StockQuote> quotes = new ConcurrentHashMap<>();

    public StockService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void reload() {
        quotes.clear();
        jdbc.query("SELECT code,name,prev_close,price,high,low,updated_at FROM stocks", rs -> {
            StockQuote quote = new StockQuote(rs.getString("code"), rs.getString("name"),
                    rs.getDouble("prev_close"), rs.getDouble("price"), rs.getDouble("high"),
                    rs.getDouble("low"), rs.getString("updated_at"));
            quotes.put(quote.code(), quote);
        });
    }

    public StockQuote get(String code) {
        if (code == null) throw BusinessException.badRequest("股票代码不能为空");
        StockQuote quote = quotes.get(code.trim());
        if (quote == null) throw BusinessException.notFound("股票不存在");
        return quote;
    }

    public StockQuote getOrNull(String code) {
        return code == null ? null : quotes.get(code.trim());
    }

    public List<StockQuote> all() {
        return quotes.values().stream().sorted(Comparator.comparing(StockQuote::code)).toList();
    }

    public void put(StockQuote quote) { quotes.put(quote.code(), quote); }
}
