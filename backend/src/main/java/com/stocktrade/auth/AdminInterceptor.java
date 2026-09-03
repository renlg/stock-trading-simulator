package com.stocktrade.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocktrade.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class AdminInterceptor implements HandlerInterceptor {
    private final AuthService authService;
    private final ObjectMapper mapper;

    public AdminInterceptor(AuthService authService, ObjectMapper mapper) {
        this.authService = authService;
        this.mapper = mapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        Object attr = request.getAttribute(AuthContext.USER_ID);
        if (attr == null) return reject(response, 401, "请提供有效的身份凭证");
        long userId = attr instanceof Long id ? id : ((Integer) attr).longValue();
        if (!authService.isAdmin(userId)) return reject(response, 403, "需要管理员权限");
        return true;
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getWriter(), Result.failure(status, message));
        return false;
    }
}
