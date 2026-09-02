package com.stocktrade.backtest;

public record PositionState(double cash, int shares, double avgCost) {
}
