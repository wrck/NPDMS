package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.normalclosure.*;
import java.util.List;

public final class NormalClosureViews {
    private NormalClosureViews() {}
    public record Check(String code, boolean passed, String reason, Long subjectId) {}
    public record Overview(Long projectId, Integer version, Long treeVersion, String currentStage,
                           String lifecycleStatus, boolean policyAvailable, List<Check> checks,
                           NormalClosureSnapshotDO latestSnapshot, NormalClosureApplicationDO latestApplication,
                           List<String> allowedActions) {}
    public record ApplicationDetail(Long projectId, NormalClosureApplicationDO application,
                                    NormalClosureSnapshotDO snapshot, List<NormalClosureReviewDO> reviews) {}
}
