package com.stocktrade.backtest;

public record KlineBar(String date, double open, double close, double high, double low,
                       double volume, double amount, double pctChg) {
}
