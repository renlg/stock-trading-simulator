package com.stocktrade.backtest;

import com.stocktrade.common.BusinessException;
import com.stocktrade.stock.StockPoolService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BacktestService {

    private final JdbcTemplate astock;
    private final StockPoolService stockPoolService;

    public BacktestService(@Qualifier("aStockJdbc") JdbcTemplate astock, StockPoolService stockPoolService) {
        this.astock = astock;
        this.stockPoolService = stockPoolService;
    }

    public List<Map<String, Object>> listStrategies() {
        List<Strategy> all = List.of(
                new MaCrossStrategy(5, 10),
                new BreakoutStrategy(20, 10),
                new DcaStrategy(5, 1000, 20),
                new GridStrategy(10, 5)
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (Strategy s : all) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("key", s.getKey());
            info.put("name", s.getName());
            info.put("description", s.getDescription());
            List<Map<String, Object>> params = new ArrayList<>();
            for (ParamDef p : s.getDefaultParams()) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("key", p.key());
                pm.put("label", p.label());
                pm.put("type", p.type());
                pm.put("defaultValue", p.defaultValue());
                pm.put("min", p.min());
                pm.put("max", p.max());
                params.add(pm);
            }
            info.put("params", params);
            result.add(info);
        }
        return result;
    }

    public Map<String, Object> runBacktest(String code, String strategyKey, Map<String, Object> params,
                                           String startDate, String endDate, double initialCapital) {
        if (code == null || code.trim().isEmpty()) throw BusinessException.badRequest("股票代码不能为空");
        if (strategyKey == null || strategyKey.trim().isEmpty()) throw BusinessException.badRequest("策略不能为空");
        if (startDate == null || endDate == null) throw BusinessException.badRequest("起止日期不能为空");
        if (initialCapital <= 0) initialCapital = 100000;

        code = code.trim();
        String stockName = stockPoolService.stockName(code);

        List<KlineBar> klines = loadKlines(code, startDate, endDate);
        if (klines.isEmpty()) {
            throw BusinessException.notFound("回测区间内无K线数据: " + code + " " + startDate + "~" + endDate);
        }

        Strategy strategy = createStrategy(strategyKey, params);

        BacktestEngine engine = new BacktestEngine(code, stockName, strategy, initialCapital);
        return engine.run(klines);
    }

    private List<KlineBar> loadKlines(String code, String startDate, String endDate) {
        List<KlineBar> bars = astock.query(
                "SELECT trade_date,open,close,high,low,volume,amount,pct_chg FROM kline_daily WHERE sec_code=? AND trade_date>=? AND trade_date<=? ORDER BY trade_date",
                (rs, n) -> new KlineBar(
                        rs.getString("trade_date"),
                        rs.getDouble("open"),
                        rs.getDouble("close"),
                        rs.getDouble("high"),
                        rs.getDouble("low"),
                        rs.getDouble("volume"),
                        rs.getDouble("amount"),
                        rs.getDouble("pct_chg")
                ), code, startDate, endDate);
        return bars;
    }

    private Strategy createStrategy(String key, Map<String, Object> params) {
        if (params == null) params = Map.of();
        return switch (key) {
            case "maCross" -> new MaCrossStrategy(
                    getInt(params, "fastPeriod", 5),
                    getInt(params, "slowPeriod", 10));
            case "breakout" -> new BreakoutStrategy(
                    getInt(params, "n", 20),
                    getInt(params, "m", 10));
            case "dca" -> new DcaStrategy(
                    getInt(params, "period", 5),
                    getDouble(params, "amount", 1000),
                    getDouble(params, "targetReturn", 20));
            case "grid" -> new GridStrategy(
                    getInt(params, "gridCount", 10),
                    getDouble(params, "gridSpacingPct", 5));
            default -> throw BusinessException.badRequest("未知策略: " + key);
        };
    }

    private int getInt(Map<String, Object> params, String key, int defaultVal) {
        Object v = params.get(key);
        if (v == null) return defaultVal;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }

    private double getDouble(Map<String, Object> params, String key, double defaultVal) {
        Object v = params.get(key);
        if (v == null) return defaultVal;
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(v.toString());
    }
}
