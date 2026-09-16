package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 低代码协同编辑会话（批次5-T6，借鉴 Mendix 协同编辑）。
 *
 * <p>记录每个配置（configType+configId）的协同会话与在线用户。
 * 用户加入编辑时创建/更新会话，定期心跳保活；超时未心跳视为离线。</p>
 *
 * <p>注：需求文档要求 Yjs + y-websocket，但环境无网络无法安装 npm 包，
 * 本实现采用 HTTP 轮询的简化协同方案。CollaborationService 接口预留
 * WebSocket 升级点，后续可平滑替换为 Yjs CRDT 实现。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("pms_lowcode_collaboration_session")
public class LowCodeCollaborationSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置类型 */
    private String configType;

    /** 配置 ID */
    private Long configId;

    /** 在线用户列表（JSON 数组：[{userId, userName, avatar, joinedAt, lastHeartbeat}]） */
    private String onlineUsers;

    /** 最近变更序号（用于增量同步） */
    private Long changeSeq;

    /** 会话创建时间 */
    private LocalDateTime createdAt;

    private LocalDateTime updateTime;
}
