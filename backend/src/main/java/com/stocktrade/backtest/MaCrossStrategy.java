package com.stocktrade.backtest;

import java.util.List;

/**
 * 均线金叉策略: 快线上穿慢线买入, 下穿卖出
 */
public class MaCrossStrategy implements Strategy {

    private final int fastPeriod;
    private final int slowPeriod;

    public MaCrossStrategy(int fastPeriod, int slowPeriod) {
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
    }

    @Override
    public String getKey() { return "maCross"; }

    @Override
    public String getName() { return "均线金叉"; }

    @Override
    public String getDescription() { return "快线上穿慢线(金叉)买入, 下穿(死叉)卖出"; }

    @Override
    public List<ParamDef> getDefaultParams() {
        return List.of(
                new ParamDef("fastPeriod", "快线周期", "int", 5, 2, 120),
                new ParamDef("slowPeriod", "慢线周期", "int", 10, 5, 250)
        );
    }

    @Override
    public Signal generateSignal(List<KlineBar> history, int currentIndex, PositionState state) {
        if (currentIndex < slowPeriod) return Signal.HOLD;

        double fastMaToday = ma(history, currentIndex, fastPeriod);
        double slowMaToday = ma(history, currentIndex, slowPeriod);
        double fastMaYesterday = ma(history, currentIndex - 1, fastPeriod);
        double slowMaYesterday = ma(history, currentIndex - 1, slowPeriod);

        boolean goldenCross = fastMaYesterday <= slowMaYesterday && fastMaToday > slowMaToday;
        boolean deathCross = fastMaYesterday >= slowMaYesterday && fastMaToday < slowMaToday;

        if (goldenCross && state.shares() == 0) {
            return Signal.buyAll();
        }
        if (deathCross && state.shares() > 0) {
            return Signal.sellAll();
        }
        return Signal.HOLD;
    }

    private double ma(List<KlineBar> history, int endIndex, int period) {
        double sum = 0;
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            sum += history.get(i).close();
        }
        return sum / period;
    }
}
