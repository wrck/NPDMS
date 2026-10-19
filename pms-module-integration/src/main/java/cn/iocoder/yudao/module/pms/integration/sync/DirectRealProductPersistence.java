package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.module.pms.project.api.migration.*;


import cn.iocoder.yudao.module.pms.integration.sync.DirectProjectAccess.Actor;
import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static cn.iocoder.yudao.module.pms.project.api.migration.ProjectRealProductMigrationApi.*;

@RequiredArgsConstructor
final class DirectRealProductPersistence {
    private final ProjectMigrationIdentityContextApi projects;
    private final DirectRealProductPreparation products;
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> preview(Command command) { return execute(command,false); }
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> apply(Command command) { return execute(command,true); }
    private List<Result> execute(Command command,boolean apply) {
        if(command==null || command.identity()==null)throw new IllegalArgumentException("DPPMS_REAL_PRODUCT_INPUT_INVALID");
        var identity=command.identity();
        if(apply)projects.lockAndRevalidate(identity);else projects.resolve(identity);
        var actor=new Actor(identity.tenantId(),identity.actorId());
        var plan=products.prepareSources(new DirectRealProductPreparation.DirectPreparation(
                identity.projectId(),identity.sourceProjectKey(),command.rows(),actor));
        return products.sourceResults(new DirectRealProductPreparation.SourceExecution(plan,actor,apply));
    }
}
