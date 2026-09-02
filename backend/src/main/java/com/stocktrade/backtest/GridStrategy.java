package com.stocktrade.backtest;

import java.util.List;

/**
 * 网格策略: 以起始价建基准网格, 价格跌一格买入, 涨一格卖出
 */
public class GridStrategy implements Strategy {

    private final int gridCount;
    private final double gridSpacingPct;
    private double basePrice = -1;
    private int nextBuyLevel = 1;
    private int nextSellLevel = 1;

    public GridStrategy(int gridCount, double gridSpacingPct) {
        this.gridCount = gridCount;
        this.gridSpacingPct = gridSpacingPct;
    }

    @Override
    public String getKey() { return "grid"; }

    @Override
    public String getName() { return "网格策略"; }

    @Override
    public String getDescription() { return "以起始价建基准网格, 价格跌一格买入, 涨一格卖出"; }

    @Override
    public List<ParamDef> getDefaultParams() {
        return List.of(
                new ParamDef("gridCount", "网格数", "int", 10, 3, 50),
                new ParamDef("gridSpacingPct", "每格间距(%)", "double", 5, 1, 20)
        );
    }

    @Override
    public Signal generateSignal(List<KlineBar> history, int currentIndex, PositionState state) {
        if (basePrice < 0) {
            basePrice = history.get(0).close();
        }

        double close = history.get(currentIndex).close();
        double gridUnit = basePrice * gridSpacingPct / 100;

        if (gridUnit <= 0) return Signal.HOLD;

        double buyLevel = basePrice - nextBuyLevel * gridUnit;
        double sellLevel = basePrice + nextSellLevel * gridUnit;

        if (close <= buyLevel && nextBuyLevel <= gridCount) {
            nextBuyLevel++;
            return Signal.buy(100);
        }
        if (close >= sellLevel && nextSellLevel <= gridCount && state.shares() >= 100) {
            nextSellLevel++;
            return Signal.sell(100);
        }
        return Signal.HOLD;
    }
}
