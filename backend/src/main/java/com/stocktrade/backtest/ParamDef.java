package com.stocktrade.backtest;

public record ParamDef(String key, String label, String type, double defaultValue, double min, double max) {
}
