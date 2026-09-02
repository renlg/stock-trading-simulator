package com.stocktrade.auth;

import com.stocktrade.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Credentials request) {
        return Result.success(service.register(request.username(), request.password()));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Credentials request) {
        return Result.success(service.login(request.username(), request.password()));
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(HttpServletRequest request) {
        return Result.success(service.me(AuthContext.userId(request)));
    }

    public record Credentials(String username, String password) {}
}
