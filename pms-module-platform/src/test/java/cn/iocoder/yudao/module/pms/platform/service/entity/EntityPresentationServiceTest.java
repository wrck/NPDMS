package cn.iocoder.yudao.module.pms.platform.service.entity;

import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.*;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormSchemaService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityPresentationServiceTest {
    final EntityProviderRegistry registry = mock(EntityProviderRegistry.class);
    final DynamicFormTemplateMapper templates = mock(DynamicFormTemplateMapper.class);
    final DynamicFormTemplateRevisionMapper revisions = mock(DynamicFormTemplateRevisionMapper.class);
    final DynamicFormSchemaService schemas = mock(DynamicFormSchemaService.class);
    final EntityPresentationService service = new EntityPresentationService(registry, templates, revisions, schemas);
    final EntityPresentationApi.Query query = new EntityPresentationApi.Query(
            EntityDataRef.revision(new RevisionRef(new EntityRef(1L,"SOL","REQUIREMENT_ANALYSIS",9L),10L)),
            new EntityActor(1L,7L,null),"REQUIREMENT_ANALYSIS");

    @Test void listingRequiresBusinessReadScopeAndDoesNotCreateBindings() {
        when(templates.selectPage(any())).thenReturn(List.of());
        assertTrue(service.list(query).isEmpty());
        verify(registry).requireReadable(query.target(),query.actor());
        verify(templates).selectPage(argThat(q -> q.tenantId().equals(1L) && q.selectionOnly()
                && q.availabilityCode().equals("ENABLED") && q.categoryCode().equals("REQUIREMENT_ANALYSIS")));
        verifyNoInteractions(revisions,schemas);
    }

    @Test void revokedReadOrCrossTenantFailsBeforeCatalogLookup() {
        doThrow(new IllegalStateException("FORBIDDEN")).when(registry).requireReadable(any(),any());
        assertThrows(RuntimeException.class, () -> service.list(query));
        assertThrows(RuntimeException.class, () -> service.list(new EntityPresentationApi.Query(query.target(),new EntityActor(2L,7L,null),query.categoryCode())));
        verifyNoInteractions(templates,revisions,schemas);
    }

    @Test void missingUnpublishedAndCrossTenantRevisionsAreNotSelectable() {
        var template = new DynamicFormTemplateDO(); template.setId(30L); template.setCurrentPublishedRevisionId(40L);
        when(templates.selectPage(any())).thenReturn(List.of(template));
        assertTrue(service.list(query).isEmpty());
        var revision = new DynamicFormTemplateRevisionDO(); revision.setTemplateId(30L); revision.setTenantId(1L); revision.setStatusCode("DRAFT");
        when(revisions.selectByRow(any())).thenReturn(revision);
        assertTrue(service.list(query).isEmpty());
        revision.setStatusCode("PUBLISHED"); revision.setTenantId(2L);
        assertTrue(service.list(query).isEmpty());
        verifyNoInteractions(schemas);
    }
    @Test void invalidPublishedSchemaDoesNotHideValidCandidates() {
        var first = new DynamicFormTemplateDO(); first.setId(30L); first.setCurrentPublishedRevisionId(40L);
        var second = new DynamicFormTemplateDO(); second.setId(31L); second.setCurrentPublishedRevisionId(41L);
        when(templates.selectPage(any())).thenReturn(List.of(first,second));
        when(revisions.selectByRow(any())).thenAnswer(call -> {
            var query = call.getArgument(0, cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormRevisionRowQuery.class);
            var row = new DynamicFormTemplateRevisionDO(); row.setId(query.revisionId()); row.setTemplateId(query.revisionId()-10);
            row.setTenantId(1L); row.setStatusCode("PUBLISHED"); row.setRevisionNo(1); row.setVersion(1);
            return row;
        });
        when(schemas.parseAndValidate(any(),any(),any(),any(),any()))
                .thenThrow(new cn.iocoder.yudao.framework.common.exception.ServiceException(400,"invalid schema"))
                .thenReturn(new DynamicFormSchemaService.SchemaFields(List.of(),List.of()));
        var result = service.list(query);
        assertEquals(1,result.size()); assertEquals(41L,result.getFirst().revisionId());
    }

}
