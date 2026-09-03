package com.stocktrade.auth;

import com.stocktrade.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserAdminService {
    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserAdminService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listUsers() {
        return jdbc.query(
                "SELECT id,username,role,balance,status,created_at FROM users ORDER BY id",
                (rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("username", rs.getString("username"));
                    row.put("role", rs.getString("role"));
                    row.put("balance", rs.getDouble("balance"));
                    row.put("status", rs.getString("status"));
                    row.put("createdAt", rs.getString("created_at"));
                    return row;
                });
    }

    public void resetPassword(long targetUserId, String newPassword, long operatorId) {
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 72)
            throw BusinessException.badRequest("密码长度应为6到72个字符");
        int rows = jdbc.update("UPDATE users SET password_hash=? WHERE id=?",
                encoder.encode(newPassword), targetUserId);
        if (rows == 0) throw BusinessException.notFound("用户不存在");
        log.info("[审计] 操作者{}重置了用户{}的密码", operatorId, targetUserId);
    }

    public void setStatus(long targetUserId, String status, long operatorId) {
        if (targetUserId == operatorId)
            throw BusinessException.badRequest("不能操作自己的账号");
        int rows = jdbc.update("UPDATE users SET status=? WHERE id=?", status, targetUserId);
        if (rows == 0) throw BusinessException.notFound("用户不存在");
        if ("disabled".equals(status)) {
            jdbc.update("DELETE FROM auth_tokens WHERE user_id=?", targetUserId);
        }
        log.info("[审计] 操作者{}将用户{}状态改为{}", operatorId, targetUserId, status);
    }

    public void deleteUser(long targetUserId, long operatorId) {
        if (targetUserId == operatorId)
            throw BusinessException.badRequest("不能删除自己的账号");
        var role = jdbc.query("SELECT role FROM users WHERE id=?", (rs, n) -> rs.getString(1), targetUserId);
        if (role.isEmpty()) throw BusinessException.notFound("用户不存在");
        jdbc.update("DELETE FROM auth_tokens WHERE user_id=?", targetUserId);
        jdbc.update("DELETE FROM conditions WHERE user_id=?", targetUserId);
        jdbc.update("DELETE FROM orders WHERE user_id=?", targetUserId);
        jdbc.update("DELETE FROM positions WHERE user_id=?", targetUserId);
        jdbc.update("DELETE FROM watch_stocks WHERE user_id=?", targetUserId);
        jdbc.update("DELETE FROM users WHERE id=?", targetUserId);
        log.info("[审计] 操作者{}删除了用户{}", operatorId, targetUserId);
    }
}
