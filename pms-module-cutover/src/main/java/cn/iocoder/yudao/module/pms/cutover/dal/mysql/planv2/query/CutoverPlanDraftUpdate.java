package cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query;

import java.time.LocalDateTime;

public record CutoverPlanDraftUpdate(Long tenantId, Long planRevisionId,
                                     Integer expectedVersion, Integer newVersion,
                                     String editModeCode, String contentSnapshot,
                                     Long fileArtifactId, Integer fileVersionNo,
                                     String fileReferenceKey, String fileFactVersion,
                                     Long fileScopeVersion, String fileSha256,
                                     Boolean ownershipConfirmed,
                                     String updater, LocalDateTime updateTime) {
}
