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
    void migrateAndSeed() {
        migrateAuthTokens();
        migrateUsers();
        seedAdmin();
    }

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

    private void migrateUsers() {
        List<Map<String, Object>> columns = jdbc.queryForList("PRAGMA table_info(users)");
        boolean hasRole = columns.stream().anyMatch(c -> "role".equals(c.get("name")));
        if (!hasRole) {
            jdbc.execute("ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'user'");
        }
        boolean hasStatus = columns.stream().anyMatch(c -> "status".equals(c.get("name")));
        if (!hasStatus) {
            jdbc.execute("ALTER TABLE users ADD COLUMN status TEXT NOT NULL DEFAULT 'active'");
        }
    }

    private void seedAdmin() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username='admin'", Integer.class);
        if (count == null || count == 0) {
            String now = now();
            jdbc.update("INSERT INTO users(username,password_hash,balance,role,status,created_at) VALUES(?,?,?,?,?,?)",
                    "admin", encoder.encode("admin123"), initialBalance, "admin", "active", now);
        }
        jdbc.update("UPDATE users SET role='admin' WHERE username='admin' AND role='user'");
    }

    @Transactional
    public Map<String, Object> register(String username, String password) {
        validateCredentials(username, password);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username=?", Integer.class, username.trim());
        if (count != null && count > 0) throw BusinessException.badRequest("用户名已存在");
        String now = now();
        jdbc.update("INSERT INTO users(username,password_hash,balance,role,status,created_at) VALUES(?,?,?,?,?,?)",
                username.trim(), encoder.encode(password), initialBalance, "user", "active", now);
        return Map.of("username", username.trim(), "balance", initialBalance, "createdAt", now);
    }

    @Transactional
    public Map<String, Object> login(String username, String password) {
        validateCredentials(username, password);
        var users = jdbc.query("SELECT id,username,password_hash,status FROM users WHERE username=?", (rs, n) ->
                new UserAuth(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"), rs.getString("status")), username.trim());
        if (users.isEmpty() || !encoder.matches(password, users.get(0).passwordHash()))
            throw BusinessException.unauthorized("用户名或密码错误");
        UserAuth user = users.get(0);
        if ("disabled".equals(user.status()))
            throw BusinessException.forbidden("该账号已被禁用");
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

        var userRows = jdbc.query("SELECT username,status FROM users WHERE id=?", (rs, n) ->
                new String[]{rs.getString("username"), rs.getString("status")}, row.userId);
        if (userRows.isEmpty()) throw BusinessException.unauthorized("用户不存在");
        if ("disabled".equals(userRows.get(0)[1]))
            throw BusinessException.unauthorized("该账号已被禁用");
        return createTokenPair(row.userId, userRows.get(0)[0]);
    }

    @Transactional
    public void logout(String accessToken) {
        // 删除该用户全部 token(含 refresh token), 使已签发的刷新令牌一并失效
        var userIds = jdbc.query("SELECT user_id FROM auth_tokens WHERE access_token=?",
                (rs, n) -> rs.getLong(1), accessToken);
        if (userIds.isEmpty()) return;
        jdbc.update("DELETE FROM auth_tokens WHERE user_id=?", userIds.get(0));
    }

    public Map<String, Object> me(long userId) {
        var rows = jdbc.query("SELECT id,username,balance,role,status,created_at FROM users WHERE id=?", (rs, n) -> Map.<String,Object>of(
                "id", rs.getLong("id"), "username", rs.getString("username"),
                "balance", rs.getDouble("balance"), "role", rs.getString("role"),
                "status", rs.getString("status"), "createdAt", rs.getString("created_at")), userId);
        if (rows.isEmpty()) throw BusinessException.notFound("用户不存在");
        return rows.get(0);
    }

    public Long authenticateAccessToken(String token) {
        var ids = jdbc.query(
                "SELECT t.user_id FROM auth_tokens t JOIN users u ON t.user_id=u.id WHERE t.access_token=? AND t.access_expires_at>? AND u.status='active'",
                (rs, n) -> rs.getLong(1), token, now());
        return ids.isEmpty() ? null : ids.get(0);
    }

    public boolean isAdmin(long userId) {
        var roles = jdbc.query("SELECT role FROM users WHERE id=?", (rs, n) -> rs.getString(1), userId);
        return !roles.isEmpty() && "admin".equals(roles.get(0));
    }

    private Map<String, Object> createTokenPair(long userId, String username) {
        String accessToken = randomHex();
        String refreshToken = randomHex();
        LocalDateTime created = LocalDateTime.now();
        String accessExpires = created.plusHours(ACCESS_TOKEN_HOURS).format(FORMAT);
        String refreshExpires = created.plusDays(REFRESH_TOKEN_DAYS).format(FORMAT);
        jdbc.update("INSERT INTO auth_tokens(user_id,access_token,refresh_token,created_at,access_expires_at,refresh_expires_at) VALUES(?,?,?,?,?,?)",
                userId, accessToken, refreshToken, created.format(FORMAT), accessExpires, refreshExpires);
        var roleRows = jdbc.query("SELECT role FROM users WHERE id=?", (rs, n) -> rs.getString(1), userId);
        String role = roleRows.isEmpty() ? "user" : roleRows.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", ACCESS_TOKEN_HOURS * 3600);
        result.put("refreshExpiresIn", REFRESH_TOKEN_DAYS * 86400);
        result.put("username", username);
        result.put("role", role);
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
    private record UserAuth(long id, String username, String passwordHash, String status) {}
    private record TokenRow(long id, long userId) {}
}
