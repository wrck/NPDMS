package cn.iocoder.yudao.module.pms.project.service.projecttree.command;

public record MoveProjectSubtreeCommand(Long projectId, Long targetParentId, Long expectedTreeVersion,
                                        String reason, String idempotencyKey, String requestDigest) {
}
