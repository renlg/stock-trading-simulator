package com.stocktrade.apikey;

import com.stocktrade.auth.AuthContext;
import com.stocktrade.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/apikeys")
public class ApiKeyController {
    private final ApiKeyService service;
    public ApiKeyController(ApiKeyService service) { this.service = service; }

    @PostMapping
    public Result<Map<String,Object>> create(@RequestBody ApiKeyRequest body, HttpServletRequest request) {
        return Result.success(service.create(AuthContext.userId(request), body.name()));
    }

    @GetMapping
    public Result<List<Map<String,Object>>> list(HttpServletRequest request) {
        return Result.success(service.list(AuthContext.userId(request)));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id, HttpServletRequest request) {
        service.delete(AuthContext.userId(request), id);
        return Result.success();
    }

    public record ApiKeyRequest(String name) {}
}
