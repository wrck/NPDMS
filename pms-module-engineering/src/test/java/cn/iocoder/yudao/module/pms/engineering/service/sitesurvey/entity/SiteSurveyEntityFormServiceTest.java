package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntitySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiteSurveyEntityFormServiceTest {
    private final DynamicFormBusinessInstanceApi forms = mock(DynamicFormBusinessInstanceApi.class);
    private final EntityFormApi bindings = mock(EntityFormApi.class);
    private final EntityExtensionApi extensions = mock(EntityExtensionApi.class);
    private final SiteSurveyDetails details = mock(SiteSurveyDetails.class);
    private final SiteSurveyEntityFormService service = new SiteSurveyEntityFormService(forms, bindings, extensions, details);

    @BeforeEach void login() {
        TenantContextHolder.setTenantId(3L);
        var user = new LoginUser(); user.setId(9L); user.setTenantId(3L);
        SecurityFrameworkUtils.setLoginUser(user, new MockHttpServletRequest());
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void ordinaryEntityWritesTypedColumnsAndDetailsWithoutAFormOrVersionProvider() {
        var row = new SiteSurveyEntityDO(); row.setProjectId(7L);
        var request = new SiteSurveyEntitySaveReqVO();
        request.setBusinessValues(Map.of("cabinetReady", false, "powerTypes", List.of("AC"),
                "selectedMaterials", List.of(Map.of("projectId", 7, "deviceId", 8, "sn", "SN-1", "reason", "接口"))));
        request.setExtensionValues(Map.of("extra_note", "extension"));
        service.prepare(row, request);
        assertEquals(false, row.getCabinetReady()); assertEquals(List.of("AC"), row.getPowerTypes());
        assertEquals("SN-1", row.getSelectedMaterials().getFirst().getSn());
        verifyNoInteractions(forms, bindings, extensions, details);
    }

    @Test void rejectsForgedManagementFieldsAndFixedValuesInExtensionPayload() {
        var row = new SiteSurveyEntityDO(); row.setProjectId(7L);
        var request = new SiteSurveyEntitySaveReqVO(); request.setBusinessValues(Map.of("status", 3));
        assertThrows(RuntimeException.class, () -> service.prepare(row, request));
        assertNull(row.getStatus());
        request.setBusinessValues(Map.of()); request.setExtensionValues(Map.of("cabinetReady", true));
        assertThrows(RuntimeException.class, () -> service.prepare(row, request));
        request.setExtensionValues(Map.of()); request.setBusinessValues(Map.of("powerTypes", List.of("INVALID")));
        assertThrows(RuntimeException.class, () -> service.prepare(row, request));
        verifyNoInteractions(forms, bindings, extensions, details);
    }

    @Test void responseKeepsBusinessValuesSeparateAndReturnsSavedBinding() {
        var row = saved(); row.setCabinetReady(false);
        var binding = new EntityFormApi.Binding(10L, 11L, Map.of("extra_cabinetReady", "cabinetReady", "note", "extra_note"), 2);
        when(bindings.layout(any(), any())).thenReturn(new EntityFormApi.Layout(binding, 12L, 1, 1, "FORM_CREATE", "1", "1", "{}", "[]", List.of()));
        when(extensions.read(any(), any())).thenReturn(new EntityExtensionApi.Values(11L, Map.of("extra_note", "kept"), 4));
        var response = service.response(row);
        assertEquals(false, response.getBusinessValues().get("cabinetReady"));
        assertEquals(Map.of("extra_note", "kept"), response.getExtensionValues());
        assertEquals(binding.fieldBindings(), response.getFieldBindings());
        assertTrue(response.getFieldCatalog().stream().anyMatch(field -> field.code().equals("selectedMaterials")));
        assertFalse(response.getBusinessValues().containsKey("version"));
    }

    @Test void persistsOnlyDynamicValuesToExtensionStorage() {
        var row = saved(); row.setCabinetReady(false);
        when(extensions.read(any(), any())).thenReturn(new EntityExtensionApi.Values(11L, Map.of(), 2));
        var request = new SiteSurveyEntitySaveReqVO();
        request.setBusinessValues(Map.of("cabinetReady", false)); request.setExtensionValues(Map.of("count", 0));
        service.persist(row, request);
        var command = ArgumentCaptor.forClass(EntityExtensionApi.Save.class);
        verify(extensions).save(command.capture());
        assertEquals(Map.of("count", 0), command.getValue().fields());
        assertEquals(2, command.getValue().expectedValueVersion());
        verify(details).save(row, 9L); verifyNoInteractions(forms);
    }

    @Test void oldTemplateKeysAreLayoutAliasesNotStorageFieldNames() {
        var schema = mock(DynamicFormRevisionFact.class);
        when(schema.fields()).thenReturn(List.of(field("extra_cabinetReady"), field("extra_note"), field("powerSupply")));
        assertEquals(Map.of("extra_cabinetReady", "cabinetReady", "extra_note", "extra_note", "powerSupply", "powerSupply"), service.fieldBindings(schema));
    }

    @Test void changedDeadlineUsesProjectApiAndStopsSurveyWriteOnProjectConflict() {
        var mapper = mock(cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper.class);
        var access = mock(SiteSurveyEntityWriteAccess.class);
        var deadlines = mock(cn.iocoder.yudao.module.pms.project.api.deadline.ProjectEndDateApi.class);
        var events = mock(cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents.class);
        var owner = new SiteSurveyEntityServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(owner, "siteSurveyEntityMapper", mapper);
        org.springframework.test.util.ReflectionTestUtils.setField(owner, "writeAccess", access);
        org.springframework.test.util.ReflectionTestUtils.setField(owner, "formService", service);
        org.springframework.test.util.ReflectionTestUtils.setField(owner, "projectEndDateApi", deadlines);
        org.springframework.test.util.ReflectionTestUtils.setField(owner, "ruleEvents", events);
        var row = saved(); row.setCode("S"); row.setStatus(0); row.setRequiredEndDate(java.time.LocalDate.of(2026, 9, 20));
        when(mapper.selectById(5L)).thenReturn(row);
        var request = new SiteSurveyEntitySaveReqVO(); request.setId(5L); request.setProjectId(7L);
        request.setCode("S"); request.setVersion(1); request.setLocation("机房"); request.setProjectEndDateVersion(6);
        request.setBusinessValues(Map.of("requiredEndDate", "2026-09-21"));
        doThrow(new IllegalStateException("project conflict")).when(deadlines).updateFromSurvey(any());
        assertEquals("project conflict", assertThrows(IllegalStateException.class, () -> owner.updateSiteSurveyEntity(request)).getMessage());
        var command = ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.project.api.deadline.ProjectEndDateCommand.class);
        verify(deadlines).updateFromSurvey(command.capture());
        assertEquals(java.time.LocalDate.of(2026, 9, 21), command.getValue().endDate());
        assertEquals(6, command.getValue().expectedProjectVersion());
        verify(access).lock(7L, "pms:eng-site-survey:update", null);
        verify(mapper, never()).updateById(any(SiteSurveyEntityDO.class));
        verifyNoInteractions(events, extensions, details);
    }

    private SiteSurveyEntityDO saved() {
        var row = new SiteSurveyEntityDO(); row.setId(5L); row.setProjectId(7L); row.setTenantId(3L); row.setVersion(1); return row;
    }

    @Test void newSurveyResolvesExtensionDefinitionBeforeSavingValues() {
        var row = saved();
        when(extensions.read(any(), any())).thenReturn(new EntityExtensionApi.Values(null, Map.of(), 0));
        var schema = mock(DynamicFormRevisionFact.class);
        when(schema.fields()).thenReturn(List.of(field("extra_note"), field("extra_cabinetReady")));
        when(forms.inspectRevisionForUsage(any())).thenReturn(schema);
        when(bindings.bind(any())).thenReturn(new EntityFormApi.Binding(10L, 11L,
                Map.of("extra_note", "extra_note", "extra_cabinetReady", "cabinetReady"), 1));
        var request = new SiteSurveyEntitySaveReqVO();
        request.setFormRevisionId(10L); request.setFormRevisionVersion(1);
        request.setExtensionValues(Map.of("extra_note", "new value"));
        service.persist(row, request);
        var ordered = inOrder(bindings, extensions);
        ordered.verify(bindings).bind(any());
        ordered.verify(extensions).save(argThat(save -> save.definitionRevisionId().equals(11L)
                && save.fields().equals(request.getExtensionValues())
                && save.actor().correlationId() != null && !save.actor().correlationId().isBlank()));
    }

    @Test void standardFormSavesWithoutCreatingExtensionValues() {
        var row = saved();
        when(extensions.read(any(), any())).thenReturn(new EntityExtensionApi.Values(null, Map.of(), 0));
        var schema = mock(DynamicFormRevisionFact.class);
        when(schema.fields()).thenReturn(List.of(field("extra_cabinetReady")));
        when(forms.inspectRevisionForUsage(any())).thenReturn(schema);
        when(bindings.bind(any())).thenReturn(new EntityFormApi.Binding(10L, null, Map.of("extra_cabinetReady", "cabinetReady"), 1));
        var request = new SiteSurveyEntitySaveReqVO(); request.setFormRevisionId(10L); request.setFormRevisionVersion(1);
        request.setBusinessValues(Map.of("cabinetReady", false));
        service.persist(row, request);
        verify(extensions, never()).save(any());
        verify(details).save(row, 9L);
    }
    private DynamicFormFieldDescriptor field(String key) {
        return new DynamicFormFieldDescriptor(key, "input", false, false, "TEXT", null, null, null, List.of());
    }
}
