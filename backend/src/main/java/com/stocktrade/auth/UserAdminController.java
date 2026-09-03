package com.stocktrade.auth;

import com.stocktrade.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {
    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.success(service.listUsers());
    }

    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable long id, @RequestBody Map<String, String> body) {
        service.resetPassword(id, body.get("password"));
        return Result.success();
    }

    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable long id, HttpServletRequest request) {
        service.setStatus(id, "disabled", AuthContext.userId(request));
        return Result.success();
    }

    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable long id, HttpServletRequest request) {
        service.setStatus(id, "active", AuthContext.userId(request));
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id, HttpServletRequest request) {
        service.deleteUser(id, AuthContext.userId(request));
        return Result.success();
    }
}
