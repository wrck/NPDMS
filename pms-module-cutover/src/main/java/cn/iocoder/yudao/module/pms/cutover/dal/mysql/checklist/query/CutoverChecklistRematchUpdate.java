package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query;

public record CutoverChecklistRematchUpdate(Long tenantId, Long checklistId, Integer expectedVersion,
                                            Integer nextChecklistVersion, Long assessmentId,
                                            Integer assessmentVersion, String inputSnapshot,
                                            String inputSnapshotHash, String matchTrace,
                                            String configGapSnapshot, Long actorId) {
}
