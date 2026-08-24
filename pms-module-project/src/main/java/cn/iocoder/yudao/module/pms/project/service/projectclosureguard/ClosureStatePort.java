package cn.iocoder.yudao.module.pms.project.service.projectclosureguard;

import java.util.Collection;
import java.util.Map;

public interface ClosureStatePort {

    Map<Long, ClosureState> findByProjectIds(Long tenantId, Collection<Long> projectIds);

    enum ClosureState {
        EXECUTING,
        PAUSED,
        CLOSURE_APPROVING,
        CLOSED
    }
}
