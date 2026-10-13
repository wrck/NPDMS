package cn.iocoder.yudao.module.pms.project.api.migration;

import cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectRealProductImportService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor
public class ProjectRealProductMigrationService implements ProjectRealProductMigrationApi {
    private final ProjectMigrationIdentityContextApi projects;
    private final ProjectRealProductImportService products;
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> preview(Command command) { return execute(command,false); }
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> apply(Command command) { return execute(command,true); }
    private List<Result> execute(Command command,boolean apply) {
        if(command==null || command.identity()==null)throw new IllegalArgumentException("DPPMS_REAL_PRODUCT_INPUT_INVALID");
        var identity=command.identity();
        if(apply)projects.lockAndRevalidate(identity);else projects.resolve(identity);
        var actor=new ProjectAccessActor(identity.tenantId(),identity.actorId());
        var plan=products.prepareSources(new ProjectRealProductImportService.DirectPreparation(
                identity.projectId(),identity.sourceProjectKey(),command.rows(),actor));
        return products.sourceResults(new ProjectRealProductImportService.SourceExecution(plan,actor,apply));
    }
}
