package com.stocktrade.backtest;

import java.util.List;

/**
 * 定投策略: 每周期买入固定金额, 持仓收益率达目标全部卖出
 */
public class DcaStrategy implements Strategy {

    private final int period;
    private final double amount;
    private final double targetReturn;
    private int dayCount = 0;

    public DcaStrategy(int period, double amount, double targetReturn) {
        this.period = period;
        this.amount = amount;
        this.targetReturn = targetReturn;
    }

    @Override
    public String getKey() { return "dca"; }

    @Override
    public String getName() { return "定投策略"; }

    @Override
    public String getDescription() { return "每周期买入固定金额, 收益率达目标全部卖出"; }

    @Override
    public List<ParamDef> getDefaultParams() {
        return List.of(
                new ParamDef("period", "定投周期(天)", "int", 5, 1, 60),
                new ParamDef("amount", "每次金额(元)", "double", 1000, 100, 100000),
                new ParamDef("targetReturn", "目标收益率(%)", "double", 20, 1, 200)
        );
    }

    @Override
    public Signal generateSignal(List<KlineBar> history, int currentIndex, PositionState state) {
        dayCount++;
        double close = history.get(currentIndex).close();

        if (state.shares() > 0 && state.avgCost() > 0) {
            double returnRate = (close - state.avgCost()) / state.avgCost() * 100;
            if (returnRate >= targetReturn) {
                return Signal.sellAll();
            }
        }

        if (state.shares() == 0) {
            dayCount = 1;
        }

        if (dayCount % period == 0) {
            int shares = (int) (amount / close / 100) * 100;
            if (shares >= 100) {
                return Signal.buy(shares);
            }
        }
        return Signal.HOLD;
    }
}
