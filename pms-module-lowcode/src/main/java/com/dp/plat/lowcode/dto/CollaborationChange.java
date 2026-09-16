package com.dp.plat.lowcode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 协同编辑变更消息（用户编辑操作的广播）。
 *
 * <p>用于在线用户间同步编辑操作。简化方案下记录最近 N 条变更，
 * 客户端轮询拉取增量（based on changeSeq）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationChange {
    /** 变更序号 */
    private Long seq;
    /** 操作类型: UPDATE/INSERT/DELETE/CURSOR */
    private String operation;
    /** 操作路径（如 formConfig.fields[0].name） */
    private String path;
    /** 旧值 */
    private String oldValue;
    /** 新值 */
    private String newValue;
    /** 操作用户 ID */
    private Long userId;
    /** 操作用户名 */
    private String userName;
    /** 操作时间 */
    private String timestamp;
}
