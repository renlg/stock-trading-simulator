package com.stocktrade.condition;

import com.stocktrade.auth.AuthContext;
import com.stocktrade.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conditions")
public class ConditionController {
    private final ConditionService service;
    public ConditionController(ConditionService service) { this.service = service; }

    @PostMapping
    public Result<Map<String,Object>> create(@RequestBody ConditionRequest body, HttpServletRequest request) {
        return Result.success(service.create(AuthContext.userId(request), body.code(), body.type(), body.triggerPrice(), body.quantity()));
    }

    @GetMapping
    public Result<List<Map<String,Object>>> list(HttpServletRequest request) {
        return Result.success(service.list(AuthContext.userId(request)));
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable long id, HttpServletRequest request) {
        service.cancel(AuthContext.userId(request), id);
        return Result.success();
    }

    public record ConditionRequest(String code, String type, Double triggerPrice, Integer quantity) {}
}
