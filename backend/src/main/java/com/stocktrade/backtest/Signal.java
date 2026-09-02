package com.stocktrade.backtest;

public record Signal(Action action, int shares) {

    public enum Action { BUY, SELL, HOLD }

    public static final Signal HOLD = new Signal(Action.HOLD, 0);

    public static Signal buyAll() { return new Signal(Action.BUY, -1); }
    public static Signal buy(int shares) { return new Signal(Action.BUY, shares); }
    public static Signal sellAll() { return new Signal(Action.SELL, -1); }
    public static Signal sell(int shares) { return new Signal(Action.SELL, shares); }
}
