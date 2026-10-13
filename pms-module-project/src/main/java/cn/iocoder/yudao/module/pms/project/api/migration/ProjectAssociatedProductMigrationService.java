package cn.iocoder.yudao.module.pms.project.api.migration;

import cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLeaseProductImportService;
import cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectProductConfigurationImportService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.List;

@Service @RequiredArgsConstructor
public class ProjectAssociatedProductMigrationService implements ProjectAssociatedProductMigrationApi {
    private final ProjectMigrationIdentityContextApi projects;
    private final ProjectLeaseProductImportService leases;
    private final ProjectProductConfigurationImportService configurations;
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> preview(Command command) { return execute(command,false); }
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> apply(Command command) { return execute(command,true); }
    private List<Result> execute(Command command,boolean apply) {
        if(command==null || command.identity()==null || command.kind()==null)
            throw new IllegalArgumentException("DPPMS_ASSOCIATED_PRODUCT_INPUT_INVALID");
        var identity=command.identity();
        if(apply)projects.lockAndRevalidate(identity);else projects.resolve(identity);
        var actor=new ProjectAccessActor(identity.tenantId(),identity.actorId());
        return switch(command.kind()) {
            case LEASE_PRODUCT -> {
                var plan=leases.prepareSources(new ProjectLeaseProductImportService.DirectPreparation(
                        identity.projectId(),command.originalProjectCode(),command.rows(),actor));
                yield leases.sourceResults(new ProjectLeaseProductImportService.SourceExecution(plan,actor,apply));
            }
            case PRODUCT_CONFIGURATION -> {
                var plan=configurations.prepareSources(new ProjectProductConfigurationImportService.DirectPreparation(
                        identity.projectId(),command.originalProjectCode(),command.rows(),actor));
                yield configurations.sourceResults(new ProjectProductConfigurationImportService.SourceExecution(plan,actor,apply));
            }
        };
    }
}
