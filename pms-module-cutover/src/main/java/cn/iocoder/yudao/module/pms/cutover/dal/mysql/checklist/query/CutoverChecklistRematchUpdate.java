package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query;

public record CutoverChecklistRematchUpdate(Long tenantId, Long checklistId, Long expectedVersion,
                                            Long nextChecklistVersion, Long assessmentId,
                                            Long assessmentVersion, String inputSnapshot,
                                            String inputSnapshotHash, String matchTrace,
                                            String configGapSnapshot, Long actorId) {
}
