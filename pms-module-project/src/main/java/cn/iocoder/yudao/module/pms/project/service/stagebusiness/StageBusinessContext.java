package cn.iocoder.yudao.module.pms.project.service.stagebusiness;

import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewRevision;
import java.util.Set;

public record StageBusinessContext(Long projectId, Long stageId, String stageCode,
        Long executionContractId, Integer contractVersion, String bindingType,
        String instanceResolutionStrategy, BusinessViewRevision businessView,
        Set<String> ownerActions, boolean readonly, String recoverableError) {
    public StageBusinessContext { ownerActions = Set.copyOf(ownerActions); }
}
