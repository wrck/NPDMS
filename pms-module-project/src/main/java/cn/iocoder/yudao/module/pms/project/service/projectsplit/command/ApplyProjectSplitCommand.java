package cn.iocoder.yudao.module.pms.project.service.projectsplit.command;

public record ApplyProjectSplitCommand(Long requestId, Integer expectedDraftVersion,
                                       Integer expectedParentVersion, Long expectedScopeVersion,
                                       Long expectedTreeVersion, String idempotencyKey,
                                       String requestDigest) {
}
