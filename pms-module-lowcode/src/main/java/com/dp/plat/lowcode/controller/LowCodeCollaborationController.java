package com.dp.plat.lowcode.controller;

import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.dto.CollaborationChange;
import com.dp.plat.lowcode.dto.OnlineUser;
import com.dp.plat.lowcode.service.CollaborationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 低代码协同编辑 Controller（批次5-T6）。
 */
@Tag(name = "低代码协同编辑", description = "LowCode collaboration")
@RestController
@RequestMapping("/api/lowcode/collaboration")
@RequiredArgsConstructor
public class LowCodeCollaborationController {

    private final CollaborationService collaborationService;

    @Operation(summary = "加入协同会话")
    @PostMapping("/join")
    @PreAuthorize("@ss.hasPermission('lowcode:collaboration:join')")
    public Result<Void> join(@RequestBody JoinRequest req) {
        collaborationService.join(req.getConfigType(), req.getConfigId(), req.getUser());
        return Result.ok();
    }

    @Operation(summary = "离开协同会话")
    @PostMapping("/leave")
    @PreAuthorize("@ss.hasPermission('lowcode:collaboration:join')")
    public Result<Void> leave(@RequestBody LeaveRequest req) {
        collaborationService.leave(req.getConfigType(), req.getConfigId(), req.getUserId());
        return Result.ok();
    }

    @Operation(summary = "心跳保活")
    @PostMapping("/heartbeat")
    @PreAuthorize("@ss.hasPermission('lowcode:collaboration:join')")
    public Result<Void> heartbeat(@RequestBody HeartbeatRequest req) {
        collaborationService.heartbeat(req.getConfigType(), req.getConfigId(), req.getUserId());
        return Result.ok();
    }

    @Operation(summary = "查询在线用户")
    @GetMapping("/online")
    @PreAuthorize("@ss.hasPermission('lowcode:collaboration:list')")
    public Result<List<OnlineUser>> online(@RequestParam String configType,
                                             @RequestParam Long configId) {
        return Result.ok(collaborationService.getOnlineUsers(configType, configId));
    }

    @Operation(summary = "广播变更")
    @PostMapping("/change")
    @PreAuthorize("@ss.hasPermission('lowcode:collaboration:join')")
    public Result<Void> broadcastChange(@RequestBody ChangeRequest req) {
        collaborationService.broadcastChange(req.getConfigType(), req.getConfigId(), req.getChange());
        return Result.ok();
    }

    @Operation(summary = "拉取增量变更")
    @GetMapping("/changes")
    @PreAuthorize("@ss.hasPermission('lowcode:collaboration:list')")
    public Result<List<CollaborationChange>> changes(@RequestParam String configType,
                                                       @RequestParam Long configId,
                                                       @RequestParam(required = false, defaultValue = "0") Long sinceSeq) {
        return Result.ok(collaborationService.getChanges(configType, configId, sinceSeq));
    }

    @Data
    @Schema(description = "加入协同请求")
    public static class JoinRequest {
        private String configType;
        private Long configId;
        private OnlineUser user;
    }

    @Data
    @Schema(description = "离开协同请求")
    public static class LeaveRequest {
        private String configType;
        private Long configId;
        private Long userId;
    }

    @Data
    @Schema(description = "心跳请求")
    public static class HeartbeatRequest {
        private String configType;
        private Long configId;
        private Long userId;
    }

    @Data
    @Schema(description = "变更广播请求")
    public static class ChangeRequest {
        private String configType;
        private Long configId;
        private CollaborationChange change;
    }
}
