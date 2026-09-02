package com.stocktrade.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocktrade.apikey.ApiKeyService;
import com.stocktrade.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthService authService;
    private final ApiKeyService apiKeyService;
    private final ObjectMapper mapper;

    public AuthInterceptor(AuthService authService, ApiKeyService apiKeyService, ObjectMapper mapper) {
        this.authService = authService;
        this.apiKeyService = apiKeyService;
        this.mapper = mapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7)
            return reject(response, "请提供有效的身份凭证");
        String credential = header.substring(7).trim();
        Long userId = request.getRequestURI().startsWith("/api/v1/")
                ? apiKeyService.authenticate(credential) : authService.authenticateToken(credential);
        if (userId == null) return reject(response, "未登录或凭证已失效");
        request.setAttribute(AuthContext.USER_ID, userId);
        return true;
    }

    private boolean reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getWriter(), Result.failure(401, message));
        return false;
    }
}
