package com.stocktrade.auth;

import com.stocktrade.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final double initialBalance;
    private final long expireDays;

    public AuthService(JdbcTemplate jdbc,
                       @Value("${stock.initial-balance:1000000}") double initialBalance,
                       @Value("${stock.token-expire-days:7}") long expireDays) {
        this.jdbc = jdbc;
        this.initialBalance = initialBalance;
        this.expireDays = expireDays;
    }

    @Transactional
    public Map<String, Object> register(String username, String password) {
        validateCredentials(username, password);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username=?", Integer.class, username.trim());
        if (count != null && count > 0) throw BusinessException.badRequest("用户名已存在");
        String now = now();
        jdbc.update("INSERT INTO users(username,password_hash,balance,created_at) VALUES(?,?,?,?)",
                username.trim(), encoder.encode(password), initialBalance, now);
        return Map.of("username", username.trim(), "balance", initialBalance, "createdAt", now);
    }

    @Transactional
    public Map<String, Object> login(String username, String password) {
        validateCredentials(username, password);
        var users = jdbc.query("SELECT id,username,password_hash FROM users WHERE username=?", (rs, n) ->
                new UserAuth(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash")), username.trim());
        if (users.isEmpty() || !encoder.matches(password, users.get(0).passwordHash()))
            throw BusinessException.unauthorized("用户名或密码错误");
        UserAuth user = users.get(0);
        String token = randomHex();
        LocalDateTime created = LocalDateTime.now();
        jdbc.update("INSERT INTO auth_tokens(token,user_id,created_at,expires_at) VALUES(?,?,?,?)",
                token, user.id(), created.format(FORMAT), created.plusDays(expireDays).format(FORMAT));
        return Map.of("token", token, "username", user.username());
    }

    public Map<String, Object> me(long userId) {
        var rows = jdbc.query("SELECT id,username,balance,created_at FROM users WHERE id=?", (rs, n) -> Map.<String,Object>of(
                "id", rs.getLong("id"), "username", rs.getString("username"),
                "balance", rs.getDouble("balance"), "createdAt", rs.getString("created_at")), userId);
        if (rows.isEmpty()) throw BusinessException.notFound("用户不存在");
        return rows.get(0);
    }

    public Long authenticateToken(String token) {
        var ids = jdbc.query("SELECT user_id FROM auth_tokens WHERE token=? AND expires_at>?", (rs, n) -> rs.getLong(1), token, now());
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void validateCredentials(String username, String password) {
        if (username == null || username.trim().length() < 3 || username.trim().length() > 32)
            throw BusinessException.badRequest("用户名长度应为3到32个字符");
        if (password == null || password.length() < 6 || password.length() > 72)
            throw BusinessException.badRequest("密码长度应为6到72个字符");
    }

    private static String randomHex() {
        UUID uuid = UUID.randomUUID();
        byte[] bytes = new byte[16];
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (uuid.getMostSignificantBits() >>> (56 - i * 8));
            bytes[i + 8] = (byte) (uuid.getLeastSignificantBits() >>> (56 - i * 8));
        }
        return HexFormat.of().formatHex(bytes);
    }

    public static String now() { return LocalDateTime.now().format(FORMAT); }
    private record UserAuth(long id, String username, String passwordHash) {}
}
