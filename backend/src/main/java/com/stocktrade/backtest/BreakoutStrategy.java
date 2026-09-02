package com.stocktrade.backtest;

import java.util.List;

/**
 * 突破策略: 创N日新高买入, 跌破M日新低卖出
 */
public class BreakoutStrategy implements Strategy {

    private final int n;
    private final int m;

    public BreakoutStrategy(int n, int m) {
        this.n = n;
        this.m = m;
    }

    @Override
    public String getKey() { return "breakout"; }

    @Override
    public String getName() { return "突破策略"; }

    @Override
    public String getDescription() { return "创N日新高买入, 跌破M日新低卖出"; }

    @Override
    public List<ParamDef> getDefaultParams() {
        return List.of(
                new ParamDef("n", "新高周期(天)", "int", 20, 5, 250),
                new ParamDef("m", "新低周期(天)", "int", 10, 3, 120)
        );
    }

    @Override
    public Signal generateSignal(List<KlineBar> history, int currentIndex, PositionState state) {
        if (currentIndex < Math.max(n, m)) return Signal.HOLD;

        double close = history.get(currentIndex).close();

        if (state.shares() == 0 && isNewHigh(history, currentIndex, n, close)) {
            return Signal.buyAll();
        }
        if (state.shares() > 0 && isNewLow(history, currentIndex, m, close)) {
            return Signal.sellAll();
        }
        return Signal.HOLD;
    }

    private boolean isNewHigh(List<KlineBar> history, int endIndex, int period, double close) {
        for (int i = endIndex - period; i < endIndex; i++) {
            if (history.get(i).close() >= close) return false;
        }
        return true;
    }

    private boolean isNewLow(List<KlineBar> history, int endIndex, int period, double close) {
        for (int i = endIndex - period; i < endIndex; i++) {
            if (history.get(i).close() <= close) return false;
        }
        return true;
    }
}
