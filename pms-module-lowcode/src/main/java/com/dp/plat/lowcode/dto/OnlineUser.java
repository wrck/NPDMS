package com.dp.plat.lowcode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 在线用户信息（协同编辑会话中的用户）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineUser {
    private Long userId;
    private String userName;
    private String avatar;
    private String joinedAt;
    private String lastHeartbeat;
}
