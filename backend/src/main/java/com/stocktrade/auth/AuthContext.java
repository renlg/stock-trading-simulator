package com.stocktrade.auth;

import com.stocktrade.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;

public final class AuthContext {
    public static final String USER_ID = "当前用户编号";
    private AuthContext() {}

    public static long userId(HttpServletRequest request) {
        Object value = request.getAttribute(USER_ID);
        if (value instanceof Long id) return id;
        if (value instanceof Integer id) return id.longValue();
        throw BusinessException.unauthorized("未登录或凭证已失效");
    }
}
