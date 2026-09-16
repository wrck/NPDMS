package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.common.util.SecurityUtils;
import com.dp.plat.lowcode.entity.LowCodeConfigAuditLog;
import com.dp.plat.lowcode.mapper.LowCodeConfigAuditLogMapper;
import com.dp.plat.lowcode.service.LowCodeConfigAuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 低代码配置审计日志服务实现（缺口2）。
 *
 * <p>所有写入均为 best-effort：序列化或持久化失败时仅记 WARN 日志，
 * 不向调用方抛出异常，确保审计逻辑不影响主业务流程。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowCodeConfigAuditLogServiceImpl
        extends ServiceImpl<LowCodeConfigAuditLogMapper, LowCodeConfigAuditLog>
        implements LowCodeConfigAuditLogService {

    private final ObjectMapper objectMapper;

    @Override
    public void record(String actor, String configType, Long configId, String configCode,
                       String action, Object before, Object after, String diffSummary) {
        try {
            LowCodeConfigAuditLog logEntry = LowCodeConfigAuditLog.builder()
                    .actor(actor == null ? SecurityUtils.getCurrentUsername() : actor)
                    .configType(configType)
                    .configId(configId)
                    .configCode(configCode)
                    .action(action)
                    .beforeSnapshot(toJson(before))
                    .afterSnapshot(toJson(after))
                    .diffSummary(truncate(diffSummary, 512))
                    .ip(resolveClientIp())
                    .userAgent(resolveUserAgent())
                    .operateTime(LocalDateTime.now())
                    .build();
            baseMapper.insert(logEntry);
        } catch (Exception e) {
            // 审计写入失败不影响主业务
            log.warn("[ConfigAudit] 写入审计日志失败: configType={}, configId={}, action={}, err={}",
                    configType, configId, action, e.getMessage());
        }
    }

    /** 序列化为 JSON（best-effort，失败返回 null） */
    private String toJson(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[ConfigAudit] 序列化快照失败: {}", e.getMessage());
            return null;
        }
    }

    /** 截断字符串到指定长度 */
    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** 从当前 HTTP 请求上下文解析客户端 IP（best-effort） */
    private String resolveClientIp() {
        try {
            HttpServletRequest request = currentRequest();
            if (request == null) return null;
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return truncate(comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim(), 64);
            }
            String real = request.getHeader("X-Real-IP");
            if (real != null && !real.isBlank()) return truncate(real.trim(), 64);
            return truncate(request.getRemoteAddr(), 64);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从当前 HTTP 请求上下文解析 User-Agent（best-effort） */
    private String resolveUserAgent() {
        try {
            HttpServletRequest request = currentRequest();
            if (request == null) return null;
            String ua = request.getHeader("User-Agent");
            return ua == null ? null : truncate(ua, 256);
        } catch (Exception e) {
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }
}
