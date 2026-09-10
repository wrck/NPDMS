package cn.iocoder.yudao.module.pms.platform.businessview.application;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.*;
import cn.iocoder.yudao.module.pms.platform.domain.businessview.BusinessViewDescriptor;
import cn.iocoder.yudao.module.pms.platform.service.businessview.*;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PM-03: real PLATFORM adapter over mocked existing Owner services, never instance creation. */
class BusinessViewProviderApplicationTest {
    private static final Context CONTEXT = new Context(1L, 7L);
    private BusinessViewAccess access;
    private PermissionApi permissions;
    private DynamicFormQueryService queries;
    private DynamicFormTemplateMapper templates;
    private DynamicFormTemplateRevisionMapper revisions;
    private PlatformDynamicFormBusinessViewProvider provider;
    private DynamicFormTemplateDO template;
    private DynamicFormTemplateRevisionDO revision;
    @BeforeEach void setup() {
        access = mock(BusinessViewAccess.class); when(access.context()).thenReturn(CONTEXT);
        permissions = mock(PermissionApi.class); queries = mock(DynamicFormQueryService.class);
        templates = mock(DynamicFormTemplateMapper.class); revisions = mock(DynamicFormTemplateRevisionMapper.class);
        provider = new PlatformDynamicFormBusinessViewProvider(access, permissions, queries, templates, revisions);
        template = new DynamicFormTemplateDO(); template.setId(20L); template.setTenantId(1L); template.setAvailabilityCode("ENABLED");
        revision = new DynamicFormTemplateRevisionDO(); revision.setId(30L); revision.setTenantId(1L); revision.setTemplateId(20L);
        revision.setStatusCode("PUBLISHED"); revision.setPublishedAt(LocalDateTime.now());
        when(queries.getRevision(new DynamicFormCommands.Actor(1L, 7L), 30L)).thenReturn(
                new DynamicFormViews.Revision(30L, 20L, 1, "PUBLISHED", null, JsonUtils.parseTree("{}"), JsonUtils.parseTree("[]"),
                        "FORM_CREATE", "1", "1", 1, 7L, revision.getPublishedAt(), Set.of()));
        when(templates.selectByRow(new DynamicFormTemplateRowQuery(1L, 20L))).thenReturn(template);
        when(templates.selectForUpdate(new DynamicFormTemplateLockQuery(1L, 20L))).thenReturn(template);
        when(revisions.selectByRow(new DynamicFormRevisionRowQuery(1L, 30L))).thenReturn(revision);
        when(revisions.selectForUpdate(new DynamicFormRevisionLockQuery(1L, 20L, 30L))).thenReturn(revision);
    }
    @Test void exactPublishedEnabledRevisionIsAvailableAndLocksFollowExistingOwnerOrder() {
        assertTrue(provider.validateConfiguration(CONTEXT, 30L, ValidationMode.INSPECT).dynamicFormAvailable());
        clearInvocations(queries, templates, revisions);
        assertTrue(provider.validateConfiguration(CONTEXT, 30L, ValidationMode.LOCK_FOR_PUBLISH).dynamicFormAvailable());
        var order = inOrder(queries, templates, revisions);
        order.verify(queries).getRevision(new DynamicFormCommands.Actor(1L, 7L), 30L);
        order.verify(templates).selectForUpdate(new DynamicFormTemplateLockQuery(1L, 20L));
        order.verify(revisions).selectForUpdate(new DynamicFormRevisionLockQuery(1L, 20L, 30L));
        verify(queries, never()).getInstance(any(), any());
    }
    @Test void disabledDraftMissingOrCrossTenantDependenciesAreNeverAvailable() {
        template.setAvailabilityCode("DISABLED");
        assertFalse(provider.validateConfiguration(CONTEXT, 30L, ValidationMode.INSPECT).dynamicFormAvailable());
        template.setAvailabilityCode("ENABLED"); revision.setStatusCode("DRAFT");
        assertFalse(provider.validateConfiguration(CONTEXT, 30L, ValidationMode.INSPECT).dynamicFormAvailable());
        revision.setStatusCode("PUBLISHED"); revision.setTenantId(2L);
        assertFalse(provider.validateConfiguration(CONTEXT, 30L, ValidationMode.INSPECT).dynamicFormAvailable());
        revision.setTenantId(1L); revision.setTemplateId(99L);
        assertFalse(provider.validateConfiguration(CONTEXT, 30L, ValidationMode.INSPECT).dynamicFormAvailable());
        assertFalse(provider.validateConfiguration(CONTEXT, null, ValidationMode.INSPECT).dynamicFormAvailable());
    }
    @Test void configurationPermissionsUseGenuineActorAndExistingOwnerPermissionCodes() {
        when(permissions.hasAnyPermissions(7L, DynamicFormActionProjection.TEMPLATE_MANAGE)).thenReturn(true);
        assertTrue(provider.canConfigure(CONTEXT, ConfigurationAction.MANAGE));
        assertFalse(provider.canConfigure(CONTEXT, ConfigurationAction.PUBLISH));
        verify(permissions).hasAnyPermissions(7L, DynamicFormActionProjection.TEMPLATE_PUBLISH);
        assertEquals(403, assertThrows(ServiceException.class,
                () -> provider.canConfigure(new Context(1L, 99L), ConfigurationAction.MANAGE)).getCode());
        assertEquals(403, assertThrows(ServiceException.class,
                () -> provider.validateConfiguration(new Context(2L, 7L), 30L, ValidationMode.INSPECT)).getCode());
    }
    @Test void catalogChecksActualFormFactsAndNeverBuildsTrustedMetadataFromRegistration() {
        var registry = new BusinessViewComponentRegistry(List.of(provider)); var c = provider.component();
        var descriptor = new BusinessViewDescriptor(1L, c.entityType(), c.ownerContext(), "form", 1L,
                BusinessViewDescriptor.ViewSource.DYNAMIC_FORM, c.componentKey(), c.componentVersion(), 30L,
                c.contextSchema(), c.supportedActions(), c.queryProviderKey(), c.commandProviderKey(), c.permissionProviderKey());
        assertDoesNotThrow(() -> registry.validate(CONTEXT, descriptor, ValidationMode.INSPECT));
        template.setAvailabilityCode("DISABLED");
        assertThrows(IllegalArgumentException.class, () -> registry.validate(CONTEXT, descriptor, ValidationMode.INSPECT));
        template.setAvailabilityCode("ENABLED");
        var forged = new BusinessViewDescriptor(1L, c.entityType(), c.ownerContext(), "form", 1L,
                BusinessViewDescriptor.ViewSource.DYNAMIC_FORM, c.componentKey(), c.componentVersion(), 30L,
                JsonUtils.parseTree("{\"forged\":true}"), c.supportedActions(), c.queryProviderKey(), c.commandProviderKey(), c.permissionProviderKey());
        assertThrows(IllegalArgumentException.class, () -> registry.validate(CONTEXT, forged, ValidationMode.INSPECT));
    }
    @Test void batchLocksAllFormTemplatesThenAllRevisionsInStableDependencyOrder() {
        var registry = new BusinessViewComponentRegistry(List.of(provider)); var c = provider.component();
        var secondTemplate = new DynamicFormTemplateDO(); secondTemplate.setId(10L); secondTemplate.setTenantId(1L);
        secondTemplate.setAvailabilityCode("ENABLED");
        var secondRevision = new DynamicFormTemplateRevisionDO(); secondRevision.setId(40L); secondRevision.setTemplateId(10L);
        secondRevision.setTenantId(1L); secondRevision.setStatusCode("PUBLISHED"); secondRevision.setPublishedAt(LocalDateTime.now());
        when(queries.getRevision(new DynamicFormCommands.Actor(1L, 7L), 40L)).thenReturn(
                new DynamicFormViews.Revision(40L, 10L, 1, "PUBLISHED", null, JsonUtils.parseTree("{}"), JsonUtils.parseTree("[]"),
                        "FORM_CREATE", "1", "1", 1, 7L, secondRevision.getPublishedAt(), Set.of()));
        when(templates.selectForUpdate(new DynamicFormTemplateLockQuery(1L, 10L))).thenReturn(secondTemplate);
        when(revisions.selectForUpdate(new DynamicFormRevisionLockQuery(1L, 10L, 40L))).thenReturn(secondRevision);
        var first = new BusinessViewDescriptor(1L, c.entityType(), c.ownerContext(), "a", 1L,
                BusinessViewDescriptor.ViewSource.DYNAMIC_FORM, c.componentKey(), c.componentVersion(), 30L,
                c.contextSchema(), c.supportedActions(), c.queryProviderKey(), c.commandProviderKey(), c.permissionProviderKey());
        var second = new BusinessViewDescriptor(1L, c.entityType(), c.ownerContext(), "z", 1L,
                BusinessViewDescriptor.ViewSource.DYNAMIC_FORM, c.componentKey(), c.componentVersion(), 40L,
                c.contextSchema(), c.supportedActions(), c.queryProviderKey(), c.commandProviderKey(), c.permissionProviderKey());
        registry.lockDependencies(CONTEXT, List.of(first, second, first));
        var order = inOrder(templates, revisions);
        order.verify(templates).selectForUpdate(new DynamicFormTemplateLockQuery(1L, 10L));
        order.verify(templates).selectForUpdate(new DynamicFormTemplateLockQuery(1L, 20L));
        order.verify(revisions).selectForUpdate(new DynamicFormRevisionLockQuery(1L, 10L, 40L));
        order.verify(revisions).selectForUpdate(new DynamicFormRevisionLockQuery(1L, 20L, 30L));
        order.verifyNoMoreInteractions();
    }

    @Test void unimplementedFormBatchProtocolIsRejectedRatherThanMarkedOnline() {
        var unknown = mock(BusinessViewComponentProvider.class);
        when(unknown.component()).thenReturn(provider.component());
        var registry = new BusinessViewComponentRegistry(List.of(unknown)); var c = provider.component();
        var form = new BusinessViewDescriptor(1L, c.entityType(), c.ownerContext(), "a", 1L,
                BusinessViewDescriptor.ViewSource.DYNAMIC_FORM, c.componentKey(), c.componentVersion(), 30L,
                c.contextSchema(), c.supportedActions(), c.queryProviderKey(), c.commandProviderKey(), c.permissionProviderKey());
        assertThrows(ServiceException.class, () -> registry.lockDependencies(CONTEXT, List.of(form)));
        verify(unknown, never()).validateConfiguration(any(), any(), any());
    }

    @Test void conflictingProviderKeysAcrossDistinctComponentsAreExcluded() {
        var first = mock(BusinessViewComponentProvider.class); var second = mock(BusinessViewComponentProvider.class);
        var component = provider.component();
        when(first.component()).thenReturn(component);
        when(second.component()).thenReturn(new Component("OTHER_ENTITY", component.ownerContext(), component.viewSource(),
                "ANOTHER_FORM", "1", component.contextSchema(), component.supportedActions(), component.queryProviderKey(),
                component.commandProviderKey(), component.permissionProviderKey(), "另一表单"));
        assertTrue(new BusinessViewComponentRegistry(List.of(first, second)).components(CONTEXT).isEmpty());
    }
}
