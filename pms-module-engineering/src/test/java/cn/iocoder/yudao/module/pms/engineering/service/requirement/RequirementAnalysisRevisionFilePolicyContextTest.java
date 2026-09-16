package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequirementAnalysisRevisionFilePolicyContextTest {
    @Test void fileRegistryCanInitializeBeforeFormsAndResolvesLayoutWhenCheckingFiles() {
        var forms = mock(EntityFormApi.class);
        var access = mock(RequirementAnalysisAccess.class);
        var row = new RequirementAnalysisRevisionDO(); row.setId(3L); row.setEntityId(2L); row.setTenantId(1L);
        when(access.read(eq(3L), any())).thenReturn(row);
        try (var context = new AnnotationConfigApplicationContext()) {
            // The form service reaches the file registry through its dynamic-form dependency.
            context.registerBean(EntityFormApi.class, () -> {
                context.getBean(FileBusinessObjectPolicyRegistry.class);
                return forms;
            });
            context.registerBean(RequirementAnalysisMapper.class, () -> mock(RequirementAnalysisMapper.class));
            context.registerBean(RequirementAnalysisAccess.class, () -> access);
            context.register(FileBusinessObjectPolicyRegistry.class, RequirementAnalysisRevisionFilePolicy.class);
            assertDoesNotThrow(context::refresh);
            verifyNoInteractions(forms);
            var policy = context.getBean(RequirementAnalysisRevisionFilePolicy.class);
            var result = policy.inspect(new FileBusinessObjectPolicyQuery(1L, 9L, "SOL", "REQUIREMENT_ANALYSIS_REVISION",
                    "3", FormAttachmentPolicy.PURPOSE_PREFIX + "evidence", "ref", FileActionCodes.READ));
            assertFalse(result.allowed());
            verify(forms).layout(EntityDataRef.revision(row.revisionRef()), new EntityActor(1L, 9L, null));
        }
    }
}
