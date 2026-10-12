package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractCollectionPlanMigrationApi;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.integration.sync.generic.GenericSyncEngine;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContractCollectionPlanSpringAssemblyTest {
    @Test void realTaskValidatorAndPlanAdapterCanBeConstructedTogether() {
        try (var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(SyncTaskMapper.class,()->mock(SyncTaskMapper.class));
            context.registerBean(SyncBindingMapper.class,()->mock(SyncBindingMapper.class));
            context.registerBean(SyncRunMapper.class,()->mock(SyncRunMapper.class));
            context.registerBean(SyncConnectionService.class,()->mock(SyncConnectionService.class));
            context.registerBean(ContractCollectionPlanMigrationApi.class,()->mock(ContractCollectionPlanMigrationApi.class));
            context.registerBean(GenericSyncEngine.class,()->mock(GenericSyncEngine.class));
            context.register(SyncTaskService.class,SyncDefinitionValidator.class,DppmsContractCollectionPlanSyncAdapter.class);
            context.refresh();
            var adapter=context.getBean(SyncDefinitionValidator.class).adapter("DPPMS_CONTRACT_COLLECTION_PLAN");
            assertSame(context.getBean(DppmsContractCollectionPlanSyncAdapter.class),adapter);
            assertNotNull(context.getBean(SyncTaskService.class));
        }
    }
}
