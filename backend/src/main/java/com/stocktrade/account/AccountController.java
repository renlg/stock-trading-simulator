package com.stocktrade.account;

import com.stocktrade.auth.AuthContext;
import com.stocktrade.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }

    @GetMapping({"/api/account", "/api/v1/account"})
    public Result<Map<String,Object>> account(HttpServletRequest request) {
        return Result.success(service.account(AuthContext.userId(request)));
    }

    @GetMapping("/api/portfolio")
    public Result<List<Map<String,Object>>> portfolio(HttpServletRequest request) {
        return Result.success(service.positions(AuthContext.userId(request)));
    }
}
