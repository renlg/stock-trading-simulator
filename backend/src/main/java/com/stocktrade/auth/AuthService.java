package com.stocktrade.auth;

import com.stocktrade.common.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final long ACCESS_TOKEN_HOURS = 8;
    private static final long REFRESH_TOKEN_DAYS = 30;

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final double initialBalance;

    public AuthService(JdbcTemplate jdbc,
                       @Value("${stock.initial-balance:1000000}") double initialBalance) {
        this.jdbc = jdbc;
        this.initialBalance = initialBalance;
    }

    @PostConstruct
    void migrateAuthTokens() {
        List<Map<String, Object>> columns = jdbc.queryForList("PRAGMA table_info(auth_tokens)");
        boolean hasAccessToken = columns.stream().anyMatch(c -> "access_token".equals(c.get("name")));
        if (!hasAccessToken) {
            jdbc.execute("DROP TABLE IF EXISTS auth_tokens");
            jdbc.execute("""
                    CREATE TABLE auth_tokens (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL,
                        access_token TEXT NOT NULL,
                        refresh_token TEXT NOT NULL,
                        created_at TEXT,
                        access_expires_at TEXT,
                        refresh_expires_at TEXT
                    )""");
        }
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_tokens_access ON auth_tokens(access_token)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_tokens_refresh ON auth_tokens(refresh_token)");
        jdbc.execute("DROP TABLE IF EXISTS api_keys");
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
        return createTokenPair(user.id(), user.username());
    }

    @Transactional
    public Map<String, Object> refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank())
            throw BusinessException.badRequest("请提供刷新令牌");
        var rows = jdbc.query("SELECT id,user_id FROM auth_tokens WHERE refresh_token=? AND refresh_expires_at>?",
                (rs, n) -> new TokenRow(rs.getLong("id"), rs.getLong("user_id")),
                refreshToken, now());
        if (rows.isEmpty()) throw BusinessException.unauthorized("刷新令牌无效或已过期");
        TokenRow row = rows.get(0);

        jdbc.update("DELETE FROM auth_tokens WHERE id=?", row.id);

        var userRows = jdbc.query("SELECT username FROM users WHERE id=?", (rs, n) -> rs.getString(1), row.userId);
        String username = userRows.isEmpty() ? "" : userRows.get(0);
        return createTokenPair(row.userId, username);
    }

    @Transactional
    public void logout(String accessToken) {
        jdbc.update("DELETE FROM auth_tokens WHERE access_token=?", accessToken);
    }

    public Map<String, Object> me(long userId) {
        var rows = jdbc.query("SELECT id,username,balance,created_at FROM users WHERE id=?", (rs, n) -> Map.<String,Object>of(
                "id", rs.getLong("id"), "username", rs.getString("username"),
                "balance", rs.getDouble("balance"), "createdAt", rs.getString("created_at")), userId);
        if (rows.isEmpty()) throw BusinessException.notFound("用户不存在");
        return rows.get(0);
    }

    public Long authenticateAccessToken(String token) {
        var ids = jdbc.query("SELECT user_id FROM auth_tokens WHERE access_token=? AND access_expires_at>?",
                (rs, n) -> rs.getLong(1), token, now());
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Map<String, Object> createTokenPair(long userId, String username) {
        String accessToken = randomHex();
        String refreshToken = randomHex();
        LocalDateTime created = LocalDateTime.now();
        String accessExpires = created.plusHours(ACCESS_TOKEN_HOURS).format(FORMAT);
        String refreshExpires = created.plusDays(REFRESH_TOKEN_DAYS).format(FORMAT);
        jdbc.update("INSERT INTO auth_tokens(user_id,access_token,refresh_token,created_at,access_expires_at,refresh_expires_at) VALUES(?,?,?,?,?,?)",
                userId, accessToken, refreshToken, created.format(FORMAT), accessExpires, refreshExpires);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", ACCESS_TOKEN_HOURS * 3600);
        result.put("refreshExpiresIn", REFRESH_TOKEN_DAYS * 86400);
        result.put("username", username);
        return result;
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
    private record TokenRow(long id, long userId) {}
}
