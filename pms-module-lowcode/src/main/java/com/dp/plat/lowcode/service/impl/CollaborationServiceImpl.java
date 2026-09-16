package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dp.plat.lowcode.dto.CollaborationChange;
import com.dp.plat.lowcode.dto.OnlineUser;
import com.dp.plat.lowcode.entity.LowCodeCollaborationSession;
import com.dp.plat.lowcode.mapper.LowCodeCollaborationSessionMapper;
import com.dp.plat.lowcode.service.CollaborationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 协同编辑服务实现（批次5-T6）。
 *
 * <p>当前实现基于 HTTP 轮询：
 * <ul>
 *   <li>会话与在线用户持久化到 pms_lowcode_collaboration_session 表</li>
 *   <li>变更消息存内存 ConcurrentLinkedQueue（最多保留 100 条/会话），重启丢失</li>
 *   <li>心跳超时阈值 30s（超过则视为离线）</li>
 * </ul></p>
 *
 * <p>升级路径：将本实现替换为基于 y-websocket 的 WebSocket 版本，
 * 接口签名保持不变，前端 useCollaboration composable 仅需切换传输层。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborationServiceImpl implements CollaborationService {

    private static final int MAX_CHANGES_PER_SESSION = 100;
    private static final int HEARTBEAT_TIMEOUT_SECONDS = 30;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final LowCodeCollaborationSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    /** 内存变更队列：key = configType:configId */
    private final ConcurrentMap<String, ConcurrentLinkedQueue<CollaborationChange>> changeQueues = new ConcurrentHashMap<>();

    @Override
    public void join(String configType, Long configId, OnlineUser user) {
        LowCodeCollaborationSession session = getOrCreateSession(configType, configId);
        List<OnlineUser> users = parseOnlineUsers(session.getOnlineUsers());
        // 移除同 userId 旧记录（避免重复）
        users.removeIf(u -> u.getUserId() != null && u.getUserId().equals(user.getUserId()));
        // 设置加入时间与心跳
        String now = LocalDateTime.now().format(ISO_FORMATTER);
        user.setJoinedAt(now);
        user.setLastHeartbeat(now);
        users.add(user);
        session.setOnlineUsers(toJson(users));
        sessionMapper.updateById(session);
        log.info("用户加入协同: config={}/{} user={}", configType, configId, user.getUserName());
    }

    @Override
    public void leave(String configType, Long configId, Long userId) {
        LowCodeCollaborationSession session = findSession(configType, configId);
        if (session == null) return;
        List<OnlineUser> users = parseOnlineUsers(session.getOnlineUsers());
        boolean removed = users.removeIf(u -> u.getUserId() != null && u.getUserId().equals(userId));
        if (removed) {
            session.setOnlineUsers(toJson(users));
            sessionMapper.updateById(session);
            log.info("用户离开协同: config={}/{} userId={}", configType, configId, userId);
        }
    }

    @Override
    public void heartbeat(String configType, Long configId, Long userId) {
        LowCodeCollaborationSession session = findSession(configType, configId);
        if (session == null) return;
        List<OnlineUser> users = parseOnlineUsers(session.getOnlineUsers());
        String now = LocalDateTime.now().format(ISO_FORMATTER);
        boolean updated = false;
        for (OnlineUser u : users) {
            if (u.getUserId() != null && u.getUserId().equals(userId)) {
                u.setLastHeartbeat(now);
                updated = true;
                break;
            }
        }
        // 清理超时用户
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(HEARTBEAT_TIMEOUT_SECONDS);
        Iterator<OnlineUser> it = users.iterator();
        while (it.hasNext()) {
            OnlineUser u = it.next();
            if (u.getLastHeartbeat() != null) {
                try {
                    LocalDateTime hb = LocalDateTime.parse(u.getLastHeartbeat(), ISO_FORMATTER);
                    if (hb.isBefore(cutoff)) {
                        it.remove();
                        updated = true;
                        log.debug("超时清理: userId={}", u.getUserId());
                    }
                } catch (Exception e) {
                    // 心跳时间格式错误，直接清理
                    it.remove();
                    updated = true;
                }
            }
        }
        if (updated) {
            session.setOnlineUsers(toJson(users));
            sessionMapper.updateById(session);
        }
    }

    @Override
    public List<OnlineUser> getOnlineUsers(String configType, Long configId) {
        LowCodeCollaborationSession session = findSession(configType, configId);
        if (session == null) return new ArrayList<>();
        // 触发一次超时清理
        heartbeat(configType, configId, -1L);
        // 重新查询（heartbeat 可能已更新）
        session = findSession(configType, configId);
        return session == null ? new ArrayList<>() : parseOnlineUsers(session.getOnlineUsers());
    }

    @Override
    public void broadcastChange(String configType, Long configId, CollaborationChange change) {
        LowCodeCollaborationSession session = getOrCreateSession(configType, configId);
        // 递增 seq
        Long newSeq = (session.getChangeSeq() == null ? 0L : session.getChangeSeq()) + 1;
        session.setChangeSeq(newSeq);
        change.setSeq(newSeq);
        change.setTimestamp(LocalDateTime.now().format(ISO_FORMATTER));
        sessionMapper.updateById(session);

        // 存入内存队列
        String key = sessionKey(configType, configId);
        ConcurrentLinkedQueue<CollaborationChange> queue =
                changeQueues.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
        queue.add(change);
        // 超过上限丢弃最旧的
        while (queue.size() > MAX_CHANGES_PER_SESSION) {
            queue.poll();
        }
    }

    @Override
    public List<CollaborationChange> getChanges(String configType, Long configId, Long sinceSeq) {
        String key = sessionKey(configType, configId);
        ConcurrentLinkedQueue<CollaborationChange> queue = changeQueues.get(key);
        if (queue == null) return new ArrayList<>();
        List<CollaborationChange> result = new ArrayList<>();
        long since = sinceSeq == null ? 0L : sinceSeq;
        for (CollaborationChange c : queue) {
            if (c.getSeq() != null && c.getSeq() > since) {
                result.add(c);
            }
        }
        return result;
    }

    private LowCodeCollaborationSession getOrCreateSession(String configType, Long configId) {
        LowCodeCollaborationSession session = findSession(configType, configId);
        if (session != null) return session;
        session = LowCodeCollaborationSession.builder()
                .configType(configType)
                .configId(configId)
                .onlineUsers("[]")
                .changeSeq(0L)
                .createdAt(LocalDateTime.now())
                .build();
        sessionMapper.insert(session);
        return session;
    }

    private LowCodeCollaborationSession findSession(String configType, Long configId) {
        List<LowCodeCollaborationSession> list = sessionMapper.selectList(
                new LambdaQueryWrapper<LowCodeCollaborationSession>()
                        .eq(LowCodeCollaborationSession::getConfigType, configType)
                        .eq(LowCodeCollaborationSession::getConfigId, configId)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private List<OnlineUser> parseOnlineUsers(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<OnlineUser>>() {});
        } catch (Exception e) {
            log.warn("解析在线用户 JSON 失败: {}", json, e);
            return new ArrayList<>();
        }
    }

    private String toJson(List<OnlineUser> users) {
        try {
            return objectMapper.writeValueAsString(users);
        } catch (Exception e) {
            log.warn("序列化在线用户失败", e);
            return "[]";
        }
    }

    private String sessionKey(String configType, Long configId) {
        return configType + ":" + configId;
    }
}
