package com.stocktrade.apikey;

import com.stocktrade.auth.AuthService;
import com.stocktrade.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApiKeyService {
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    public ApiKeyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> create(long userId, String name) {
        if (name == null || name.isBlank() || name.trim().length() > 50)
            throw BusinessException.badRequest("密钥名称不能为空且最多50个字符");
        byte[] bytes = new byte[16]; random.nextBytes(bytes);
        String key = HexFormat.of().formatHex(bytes);
        String now = AuthService.now();
        jdbc.update("INSERT INTO api_keys(user_id,name,api_key,created_at) VALUES(?,?,?,?)", userId, name.trim(), key, now);
        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id); result.put("name", name.trim()); result.put("apiKey", key); result.put("createdAt", now);
        return result;
    }

    public List<Map<String, Object>> list(long userId) {
        return jdbc.query("SELECT id,name,api_key,created_at,last_used_at FROM api_keys WHERE user_id=? ORDER BY id DESC", (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            String key = rs.getString("api_key");
            String mask = key.substring(0, Math.min(8, key.length())) + "***";
            row.put("id", rs.getLong("id")); row.put("name", rs.getString("name"));
            row.put("apiKey", mask); row.put("apiKeyMask", mask);
            row.put("createdAt", rs.getString("created_at")); row.put("lastUsedAt", rs.getString("last_used_at"));
            return row;
        }, userId);
    }

    public void delete(long userId, long id) {
        if (jdbc.update("DELETE FROM api_keys WHERE id=? AND user_id=?", id, userId) == 0)
            throw BusinessException.notFound("API密钥不存在");
    }

    public Long authenticate(String key) {
        var users = jdbc.query("SELECT user_id FROM api_keys WHERE api_key=?", (rs, n) -> rs.getLong(1), key);
        if (users.isEmpty()) return null;
        jdbc.update("UPDATE api_keys SET last_used_at=? WHERE api_key=?", AuthService.now(), key);
        return users.get(0);
    }
}
