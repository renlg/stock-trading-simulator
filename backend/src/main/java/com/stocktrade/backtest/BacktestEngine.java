package com.stocktrade.backtest;

import java.util.*;

/**
 * 回测引擎: 逐日遍历K线, 撮合策略信号, 计算收益指标
 *
 * 交易成本模型:
 * - 佣金: 成交金额 × 0.025%, 双边收取, 单笔最低5元
 * - 印花税: 卖出时成交金额 × 0.05%
 * - 滑点: 买入 = 收盘价 × 1.001, 卖出 = 收盘价 × 0.999
 * - 涨跌停: 收盘价 ≥ 涨停价 → 买入不成交; 收盘价 ≤ 跌停价 → 卖出不成交
 * - 停牌: 当日无K线数据 → 不产生交易
 */
public class BacktestEngine {

    private static final double COMMISSION_RATE = 0.00025;
    private static final double MIN_COMMISSION = 5.0;
    private static final double STAMP_DUTY_RATE = 0.0005;
    private static final double BUY_SLIPPAGE = 1.001;
    private static final double SELL_SLIPPAGE = 0.999;
    private static final double RISK_FREE_RATE = 0.03;

    private final String code;
    private final String stockName;
    private final Strategy strategy;
    private final double initialCapital;

    private double cash;
    private int shares;
    private double avgCost;
    private final List<Map<String, Object>> equityCurve = new ArrayList<>();
    private final List<Map<String, Object>> trades = new ArrayList<>();
    private int winCount = 0;
    private int sellCount = 0;

    public BacktestEngine(String code, String stockName, Strategy strategy, double initialCapital) {
        this.code = code;
        this.stockName = stockName;
        this.strategy = strategy;
        this.initialCapital = initialCapital;
    }

    public Map<String, Object> run(List<KlineBar> klines) {
        if (klines == null || klines.isEmpty()) {
            throw new IllegalArgumentException("K线数据为空");
        }

        cash = initialCapital;
        shares = 0;
        avgCost = 0;
        equityCurve.clear();
        trades.clear();
        winCount = 0;
        sellCount = 0;

        double prevClose = 0;

        for (int i = 0; i < klines.size(); i++) {
            KlineBar bar = klines.get(i);

            boolean isFirstDay = (i == 0);
            double limitPct = getLimitPct(code, stockName);

            double limitUp = 0;
            double limitDown = 0;
            if (!isFirstDay && prevClose > 0) {
                limitUp = Math.round(prevClose * (1 + limitPct) * 100.0) / 100.0;
                limitDown = Math.round(prevClose * (1 - limitPct) * 100.0) / 100.0;
            }

            PositionState state = new PositionState(cash, shares, avgCost);
            Signal signal = strategy.generateSignal(klines, i, state);

            if (signal.action() == Signal.Action.BUY) {
                executeBuy(bar, signal, prevClose, limitUp, isFirstDay);
            } else if (signal.action() == Signal.Action.SELL) {
                executeSell(bar, signal, prevClose, limitDown, isFirstDay);
            }

            double equity = cash + shares * bar.close();
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", bar.date());
            point.put("value", Math.round(equity * 100.0) / 100.0);
            equityCurve.add(point);

            prevClose = bar.close();
        }

        return buildResult(klines);
    }

    private void executeBuy(KlineBar bar, Signal signal, double prevClose, double limitUp, boolean isFirstDay) {
        if (!isFirstDay && prevClose > 0 && bar.close() >= limitUp) {
            return;
        }

        double buyPrice = Math.round(bar.close() * BUY_SLIPPAGE * 100.0) / 100.0;
        int buyShares;

        if (signal.shares() == -1) {
            buyShares = (int) (cash / buyPrice / 100) * 100;
        } else {
            buyShares = (signal.shares() / 100) * 100;
        }

        if (buyShares < 100) return;

        double amount = buyShares * buyPrice;
        double commission = Math.max(amount * COMMISSION_RATE, MIN_COMMISSION);
        double totalCost = amount + commission;

        if (totalCost > cash) {
            buyShares = (int) (cash / (buyPrice * (1 + COMMISSION_RATE)) / 100) * 100;
            if (buyShares < 100) return;
            amount = buyShares * buyPrice;
            commission = Math.max(amount * COMMISSION_RATE, MIN_COMMISSION);
            totalCost = amount + commission;
            if (totalCost > cash) return;
        }

        cash -= totalCost;
        avgCost = (shares > 0) ? (avgCost * shares + buyPrice * buyShares) / (shares + buyShares) : buyPrice;
        shares += buyShares;

        Map<String, Object> trade = new LinkedHashMap<>();
        trade.put("date", bar.date());
        trade.put("action", "BUY");
        trade.put("price", buyPrice);
        trade.put("shares", buyShares);
        trade.put("amount", Math.round(amount * 100.0) / 100.0);
        trade.put("fee", Math.round(commission * 100.0) / 100.0);
        trade.put("cash", Math.round(cash * 100.0) / 100.0);
        trades.add(trade);
    }

    private void executeSell(KlineBar bar, Signal signal, double prevClose, double limitDown, boolean isFirstDay) {
        if (shares <= 0) return;

        if (!isFirstDay && prevClose > 0 && bar.close() <= limitDown) {
            return;
        }

        double sellPrice = Math.round(bar.close() * SELL_SLIPPAGE * 100.0) / 100.0;
        int sellShares = (signal.shares() == -1) ? shares : Math.min(signal.shares(), shares);

        if (sellShares <= 0) return;

        double amount = sellShares * sellPrice;
        double commission = Math.max(amount * COMMISSION_RATE, MIN_COMMISSION);
        double stampDuty = amount * STAMP_DUTY_RATE;
        double netProceeds = amount - commission - stampDuty;

        if (sellPrice > avgCost) {
            winCount++;
        }
        sellCount++;

        cash += netProceeds;
        shares -= sellShares;
        if (shares == 0) {
            avgCost = 0;
        }

        Map<String, Object> trade = new LinkedHashMap<>();
        trade.put("date", bar.date());
        trade.put("action", "SELL");
        trade.put("price", sellPrice);
        trade.put("shares", sellShares);
        trade.put("amount", Math.round(amount * 100.0) / 100.0);
        trade.put("fee", Math.round((commission + stampDuty) * 100.0) / 100.0);
        trade.put("cash", Math.round(cash * 100.0) / 100.0);
        trades.add(trade);
    }

    private Map<String, Object> buildResult(List<KlineBar> klines) {
        double finalEquity = cash + shares * klines.get(klines.size() - 1).close();
        double totalReturnPct = (finalEquity - initialCapital) / initialCapital * 100;

        int tradingDays = equityCurve.size();
        double annualReturnPct = 0;
        if (tradingDays > 1 && finalEquity > 0) {
            annualReturnPct = (Math.pow(finalEquity / initialCapital, 252.0 / tradingDays) - 1) * 100;
        }

        double maxDrawdownPct = calcMaxDrawdown();
        double sharpe = calcSharpe();
        double winRate = sellCount > 0 ? (double) winCount / sellCount * 100 : 0;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalReturnPct", Math.round(totalReturnPct * 100.0) / 100.0);
        metrics.put("annualReturnPct", Math.round(annualReturnPct * 100.0) / 100.0);
        metrics.put("maxDrawdownPct", Math.round(maxDrawdownPct * 100.0) / 100.0);
        metrics.put("sharpe", Math.round(sharpe * 100.0) / 100.0);
        metrics.put("winRate", Math.round(winRate * 100.0) / 100.0);
        metrics.put("tradeCount", trades.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("name", stockName);
        result.put("strategy", strategy.getKey());
        result.put("initialCapital", initialCapital);
        result.put("finalEquity", Math.round(finalEquity * 100.0) / 100.0);
        result.put("metrics", metrics);
        result.put("equityCurve", equityCurve);
        result.put("trades", trades);
        return result;
    }

    private double calcMaxDrawdown() {
        double peak = 0;
        double maxDd = 0;
        for (Map<String, Object> point : equityCurve) {
            double value = ((Number) point.get("value")).doubleValue();
            if (value > peak) peak = value;
            if (peak > 0) {
                double dd = (peak - value) / peak * 100;
                if (dd > maxDd) maxDd = dd;
            }
        }
        return maxDd;
    }

    private double calcSharpe() {
        if (equityCurve.size() < 2) return 0;

        List<Double> dailyReturns = new ArrayList<>();
        for (int i = 1; i < equityCurve.size(); i++) {
            double prev = ((Number) equityCurve.get(i - 1).get("value")).doubleValue();
            double curr = ((Number) equityCurve.get(i).get("value")).doubleValue();
            if (prev > 0) {
                dailyReturns.add((curr - prev) / prev);
            }
        }
        if (dailyReturns.isEmpty()) return 0;

        double mean = dailyReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = dailyReturns.stream().mapToDouble(r -> (r - mean) * (r - mean)).average().orElse(0);
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0) return 0;

        double annualReturn = mean * 252;
        double annualStd = stdDev * Math.sqrt(252);
        return (annualReturn - RISK_FREE_RATE) / annualStd;
    }

    /**
     * 根据股票代码判断涨跌停幅度
     * - 300/301开头: 创业板 20%
     * - 688开头: 科创板 20%
     * - 名称含ST: 5%
     * - 其他(主板): 10%
     */
    static double getLimitPct(String code, String name) {
        if (code != null) {
            if (code.startsWith("300") || code.startsWith("301") || code.startsWith("688") || code.startsWith("689")) {
                return 0.20;
            }
        }
        if (name != null && name.contains("ST")) {
            return 0.05;
        }
        return 0.10;
    }
}
