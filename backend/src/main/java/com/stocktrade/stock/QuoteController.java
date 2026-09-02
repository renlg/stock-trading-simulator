package com.stocktrade.stock;

import com.stocktrade.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class QuoteController {
    private final StockService stocks;
    public QuoteController(StockService stocks) { this.stocks = stocks; }

    @GetMapping("/api/quote")
    public Result<List<QuoteResponse>> all() {
        return Result.success(stocks.all().stream().map(QuoteResponse::from).toList());
    }

    @GetMapping({"/api/quote/{code}", "/api/v1/quote/{code}"})
    public Result<QuoteResponse> one(@PathVariable String code) {
        return Result.success(QuoteResponse.from(stocks.get(code)));
    }

    public record QuoteResponse(String code, String name, double price, double prevClose,
                                double change, double changePct, double high, double low, String updatedAt) {
        static QuoteResponse from(StockQuote q) {
            return new QuoteResponse(q.code(), q.name(), q.price(), q.prevClose(), q.change(),
                    q.changePct(), q.high(), q.low(), q.updatedAt());
        }
    }
}
