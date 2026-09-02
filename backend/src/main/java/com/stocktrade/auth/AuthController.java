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

    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody RefreshRequest request) {
        return Result.success(service.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && header.length() > 7) {
            service.logout(header.substring(7).trim());
        }
        return Result.success();
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(HttpServletRequest request) {
        return Result.success(service.me(AuthContext.userId(request)));
    }

    public record Credentials(String username, String password) {}
    public record RefreshRequest(String refreshToken) {}
}
