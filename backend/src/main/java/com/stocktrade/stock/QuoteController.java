package com.stocktrade.stock;

import com.stocktrade.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class QuoteController {
    private final StockService stocks;
    private final StockPoolService pool;
    public QuoteController(StockService stocks, StockPoolService pool) {
        this.stocks = stocks;
        this.pool = pool;
    }

    /** 行情列表(关注池, 来自真实分钟线) */
    @GetMapping("/api/quote")
    public Result<List<QuoteResponse>> all() {
        return Result.success(stocks.all().stream().map(QuoteResponse::from).toList());
    }

    /** 单只行情(关注池内, 引擎缓存) */
    @GetMapping({"/api/quote/{code}", "/api/v1/quote/{code}"})
    public Result<QuoteResponse> one(@PathVariable String code) {
        return Result.success(QuoteResponse.from(stocks.get(code)));
    }

    /** 搜索全市场A股: q=代码或名称 */
    @GetMapping("/api/stocks/search")
    public Result<List<Map<String, Object>>> search(@RequestParam("q") String q,
                                                    @RequestParam(defaultValue = "20") int limit) {
        return Result.success(pool.search(q, limit));
    }

    /** 关注池列表 */
    @GetMapping("/api/stocks/watch")
    public Result<List<Map<String, Object>>> watchList() {
        return Result.success(pool.watchList());
    }

    /** 添加关注(从A股全市场) */
    @PostMapping("/api/stocks/watch")
    public Result<Map<String, Object>> addWatch(@RequestBody WatchRequest body) {
        pool.addWatch(body.code());
        return Result.success(Map.of("code", body.code(), "watched", true));
    }

    /** 移除关注 */
    @DeleteMapping("/api/stocks/watch/{code}")
    public Result<Map<String, Object>> removeWatch(@PathVariable String code) {
        pool.removeWatch(code);
        return Result.success(Map.of("code", code, "watched", false));
    }

    /** 单只真实行情(任意A股, 按需直读 /opt/a-stock, 无需在关注池) */
    @GetMapping("/api/stocks/quote/{code}")
    public Result<Map<String, Object>> realQuote(@PathVariable String code) {
        Map<String, Object> q = pool.realQuote(code);
        if (q == null) throw com.stocktrade.common.BusinessException.notFound("无该股票行情数据: " + code);
        return Result.success(q);
    }

    /** K线数据: type=day|min5, limit 默认120 */
    @GetMapping("/api/stocks/kline/{code}")
    public Result<Map<String, Object>> kline(@PathVariable String code,
                                              @RequestParam(defaultValue = "day") String type,
                                              @RequestParam(defaultValue = "120") int limit) {
        String name = pool.stockName(code);
        List<Map<String, Object>> bars;
        if ("min5".equals(type)) {
            bars = pool.klineMin5(code, Math.min(Math.max(limit, 1), 500));
        } else {
            bars = pool.klineDaily(code, Math.min(Math.max(limit, 1), 500));
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("code", code);
        result.put("name", name);
        result.put("type", "min5".equals(type) ? "min5" : "day");
        result.put("bars", bars);
        return Result.success(result);
    }

    public record WatchRequest(String code) {}
    public record QuoteResponse(String code, String name, double price, double prevClose,
                                double change, double changePct, double high, double low, String updatedAt) {
        static QuoteResponse from(StockQuote q) {
            return new QuoteResponse(q.code(), q.name(), q.price(), q.prevClose(), q.change(),
                    q.changePct(), q.high(), q.low(), q.updatedAt());
        }
    }
}
