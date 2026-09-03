package com.stocktrade.config;

import com.stocktrade.auth.AdminInterceptor;
import com.stocktrade.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final AuthInterceptor interceptor;
    private final AdminInterceptor adminInterceptor;

    public WebMvcConfig(AuthInterceptor interceptor, AdminInterceptor adminInterceptor) {
        this.interceptor = interceptor;
        this.adminInterceptor = adminInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/error",
                        "/api/stocks/search", "/api/stocks/quote/*",
                        "/api/stocks/kline/**", "/api/stocks/detail/**");
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**")
                .addPathPatterns("/api/calendar/holidays");
    }
}
