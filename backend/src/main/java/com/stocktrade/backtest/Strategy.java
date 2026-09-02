package com.stocktrade.backtest;

import java.util.List;

public interface Strategy {

    String getKey();

    String getName();

    String getDescription();

    List<ParamDef> getDefaultParams();

    Signal generateSignal(List<KlineBar> history, int currentIndex, PositionState state);
}
