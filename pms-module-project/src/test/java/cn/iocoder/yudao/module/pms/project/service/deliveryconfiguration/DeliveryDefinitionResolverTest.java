package cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewQueryApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.deliveryconfiguration.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration.*;
import cn.iocoder.yudao.module.pms.project.domain.template.DeliveryDefinitionReference;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProviderRegistry;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PM-03: dangling, tenant, disabled, cycle and unavailable Owner failures never mean unconfigured. */
class DeliveryDefinitionResolverTest {
    DeliveryDefinitionRevisionMapper revisions = mock(DeliveryDefinitionRevisionMapper.class);
    DeliveryDefinitionReferenceMapper references = mock(DeliveryDefinitionReferenceMapper.class);
    ProjectStageGateProviderRegistry providers = mock(ProjectStageGateProviderRegistry.class);
    DeliveryDefinitionResolver resolver = new DeliveryDefinitionResolver(revisions, references,
            mock(BusinessViewQueryApi.class), providers, mock(ProjectStageGateProcessOwnerApi.class));
    @BeforeEach void setup() { TenantContextHolder.setTenantId(7L); when(references.selectReferences(any())).thenReturn(List.of()); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void missingForeignDraftAndDisabledTargetsRejected() {
        assertThrows(ServiceException.class, () -> resolve(1L));
        var row = row(1L); row.setTenantId(8L); when(revisions.selectById(1L)).thenReturn(row);
        assertThrows(ServiceException.class, () -> resolve(1L));
        row.setTenantId(7L); row.setRevisionState("DRAFT"); assertThrows(ServiceException.class, () -> resolve(1L));
        row.setRevisionState("PUBLISHED"); row.setDisabledAt(LocalDateTime.now()); assertThrows(ServiceException.class, () -> resolve(1L));
    }
    @Test void selfAndTransitiveCyclesRejected() {
        when(revisions.selectById(1L)).thenReturn(row(1L)); when(revisions.selectById(2L)).thenReturn(row(2L));
        when(references.selectReferences(any())).thenAnswer(call -> {
            var query = (cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration.query.DeliveryDefinitionReferencesQuery)call.getArgument(0);
            var edge = new DeliveryDefinitionReferenceDO(); edge.setReferenceKey("next");
            edge.setTargetRevisionId(query.ownerRevisionId() == 1L ? 2L : 1L); return List.of(edge);
        });
        assertThrows(ServiceException.class, () -> resolve(1L));
        assertThrows(ServiceException.class, () -> resolver.resolve(List.of(new DeliveryDefinitionReference("self",1L)),1L,false));
    }
    @Test void providerMustExistAndStableVersionRecheckedUnderLock() {
        var row = row(1L); when(revisions.selectById(1L)).thenReturn(row); when(revisions.lockRevision(any())).thenReturn(row);
        assertThrows(ServiceException.class, () -> resolve(1L));
        when(providers.hasProvider("PROJ_TASK")).thenReturn(true);
        var result = resolver.resolve(List.of(new DeliveryDefinitionReference("rule",1L)),null,true);
        assertEquals(1, result.size()); assertTrue(result.get(1L).definition().payload().has("parameters"));
        verify(revisions).lockRevision(any());
        var stale = row(1L); stale.setVersion(8); when(revisions.lockRevision(any())).thenReturn(stale);
        assertThrows(ServiceException.class, () -> resolver.resolve(List.of(new DeliveryDefinitionReference("rule",1L)),null,true));
    }
    private void resolve(Long id) { resolver.resolve(List.of(new DeliveryDefinitionReference("rule",id)),null,false); }
    private DeliveryDefinitionRevisionDO row(Long id) {
        var row = new DeliveryDefinitionRevisionDO(); row.setId(id); row.setTenantId(7L); row.setRevisionState("PUBLISHED");
        row.setPublishedAt(LocalDateTime.now()); row.setVersion(0); row.setRevisionNo(1L); row.setSchemaVersion(1);
        row.setDefinitionKind("COMPLETION_RULE"); row.setDefinitionCode("RULE");
        row.setPayload("{\"predicate\":\"TASK_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}}"); return row;
    }
}
