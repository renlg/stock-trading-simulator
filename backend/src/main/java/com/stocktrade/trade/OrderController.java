package com.stocktrade.trade;

import com.stocktrade.account.AccountService;
import com.stocktrade.auth.AuthContext;
import com.stocktrade.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class OrderController {
    private final TradeService trades;
    private final AccountService accounts;
    public OrderController(TradeService trades, AccountService accounts) { this.trades = trades; this.accounts = accounts; }

    @PostMapping("/api/trade/buy")
    public Result<Map<String,Object>> buy(@RequestBody TradeRequest body, HttpServletRequest request) {
        return Result.success(trades.execute(AuthContext.userId(request), body.code(), "buy", body.quantity(), "web"));
    }

    @PostMapping("/api/trade/sell")
    public Result<Map<String,Object>> sell(@RequestBody TradeRequest body, HttpServletRequest request) {
        return Result.success(trades.execute(AuthContext.userId(request), body.code(), "sell", body.quantity(), "web"));
    }

    @PostMapping("/api/v1/orders")
    public Result<Map<String,Object>> external(@RequestBody ExternalOrder body, HttpServletRequest request) {
        return Result.success(trades.execute(AuthContext.userId(request), body.code(), body.side(), body.quantity(), "api"));
    }

    @GetMapping({"/api/orders", "/api/v1/orders"})
    public Result<Map<String,Object>> orders(@RequestParam(defaultValue="1") int page,
                                             @RequestParam(defaultValue="20") int size,
                                             HttpServletRequest request) {
        return Result.success(accounts.orders(AuthContext.userId(request), page, size));
    }

    public record TradeRequest(String code, Integer quantity) {}
    public record ExternalOrder(String code, String side, Integer quantity) {}
}
