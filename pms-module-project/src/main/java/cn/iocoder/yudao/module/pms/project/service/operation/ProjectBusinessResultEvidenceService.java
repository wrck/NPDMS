package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.result.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor
public class ProjectBusinessResultEvidenceService implements ProjectBusinessResultEvidenceApi {
    private final ProjectBusinessResultSources sources;
    @Override public List<BusinessResultSource.Descriptor> types() { return sources.descriptors(); }
    @Override public BusinessResultInventorySource.InventoryPage candidates(BusinessResultInventorySource.InventoryQuery query) {
        return sources.inventory(query);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public BusinessResultSource.Observation lockAndInspect(BusinessResultSource.Query query) {
        return sources.lockAndInspect(query);
    }
}
