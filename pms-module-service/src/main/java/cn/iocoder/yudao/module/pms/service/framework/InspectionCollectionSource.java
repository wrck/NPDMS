package cn.iocoder.yudao.module.pms.service.framework;

import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionSourceAdapter;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionOnlineAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class InspectionCollectionSource implements CollectionSourceAdapter {
    private final InspectionOnlineAuthorization authorization;
    @Override public String entry() { return "inspection"; }
    @Override public Source authorize(AuthorizationQuery query) {
        var bound = authorization.authorize(query); var row = bound.execution();
        return Source.builder().entry(entry()).objectId(row.getId()).sourceContext("SRV").sourceObjectType("INSPECTION_EXECUTION")
                .projectId(bound.task().getProjectId()).deviceId(row.getDeviceId()).deviceName(bound.device().getDeviceName())
                .version(row.getVersion()).manualAllowed(false).canExecute("PENDING".equals(row.getStatusCode())
                        && Set.of("PRECHECK_PENDING", "EXECUTING").contains(bound.task().getStatusCode()))
                .completionMode("BUSINESS_CONSUMPTION").title(bound.task().getTaskName()).executionProfile(authorization.profile(row)).build();
    }
    @Override public void authorizeConnection(PreparedConnection query) { authorization.authorizeConnection(query); }
}
