package com.stocktrade.stock;

import com.stocktrade.auth.AuthContext;
import com.stocktrade.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class QuoteController {
    private final StockService stocks;
    private final StockPoolService pool;
    public QuoteController(StockService stocks, StockPoolService pool) {
        this.stocks = stocks;
        this.pool = pool;
    }

    /** 行情列表(当前用户自选股, 来自引擎缓存) */
    @GetMapping("/api/quote")
    public Result<List<QuoteResponse>> all(HttpServletRequest request) {
        long userId = AuthContext.userId(request);
        Set<String> codes = pool.watchList(userId).stream()
                .map(w -> (String) w.get("code")).collect(Collectors.toSet());
        return Result.success(stocks.all().stream()
                .filter(q -> codes.contains(q.code()))
                .map(QuoteResponse::from).toList());
    }

    /** 单只行情(引擎缓存) */
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

    /** 关注池列表(当前用户) */
    @GetMapping("/api/stocks/watch")
    public Result<List<Map<String, Object>>> watchList(HttpServletRequest request) {
        long userId = AuthContext.userId(request);
        return Result.success(pool.watchList(userId));
    }

    /** 添加关注(当前用户, 从A股全市场) */
    @PostMapping("/api/stocks/watch")
    public Result<Map<String, Object>> addWatch(@RequestBody WatchRequest body, HttpServletRequest request) {
        long userId = AuthContext.userId(request);
        pool.addWatch(userId, body.code());
        return Result.success(Map.of("code", body.code(), "watched", true));
    }

    /** 移除关注(当前用户) */
    @DeleteMapping("/api/stocks/watch/{code}")
    public Result<Map<String, Object>> removeWatch(@PathVariable String code, HttpServletRequest request) {
        long userId = AuthContext.userId(request);
        pool.removeWatch(userId, code);
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

    /** 股票详情页聚合数据: 估值/资金/股东/两融/一致预期/北向/财报(news-feed)/重大事件(news-feed) */
    @GetMapping("/api/stocks/detail/{code}")
    public Result<Map<String, Object>> detail(@PathVariable String code) {
        return Result.success(pool.stockDetail(code));
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
