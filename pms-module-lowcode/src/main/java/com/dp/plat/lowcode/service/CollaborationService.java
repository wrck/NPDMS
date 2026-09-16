package com.dp.plat.lowcode.service;

import com.dp.plat.lowcode.dto.CollaborationChange;
import com.dp.plat.lowcode.dto.OnlineUser;

import java.util.List;

/**
 * 协同编辑服务（批次5-T6）。
 *
 * <p>提供加入/离开会话、心跳保活、在线用户查询、变更广播与增量拉取。
 * 当前实现基于 HTTP 轮询，预留 WebSocket 升级点。</p>
 */
public interface CollaborationService {

    /**
     * 加入协同会话（若会话不存在则创建）。
     */
    void join(String configType, Long configId, OnlineUser user);

    /**
     * 离开协同会话。
     */
    void leave(String configType, Long configId, Long userId);

    /**
     * 心跳保活（更新 lastHeartbeat，超时用户自动离线）。
     */
    void heartbeat(String configType, Long configId, Long userId);

    /**
     * 查询在线用户列表。
     */
    List<OnlineUser> getOnlineUsers(String configType, Long configId);

    /**
     * 广播变更（用户编辑操作）。
     */
    void broadcastChange(String configType, Long configId, CollaborationChange change);

    /**
     * 拉取增量变更（seq > sinceSeq）。
     */
    List<CollaborationChange> getChanges(String configType, Long configId, Long sinceSeq);
}
