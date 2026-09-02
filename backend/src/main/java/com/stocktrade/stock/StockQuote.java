package com.stocktrade.stock;

public record StockQuote(String code, String name, double prevClose, double price,
                         double high, double low, String updatedAt) {
    public double change() { return price - prevClose; }
    public double changePct() { return prevClose == 0 ? 0 : change() / prevClose * 100; }
}
