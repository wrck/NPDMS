package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.ProjectMasterController;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.SelectedCustomerProjectController;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectMatchTemplatesReqVO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchCandidate;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Controller mapping only; no claim of HTTP security/browser acceptance. */
class ProjectCreationMatchControllerTest {
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void oldCreationEntryPassesTypedFactsAndReturnsVersionDiagnostics() {
        TenantContextHolder.setTenantId(7L);
        var service = mock(ProjectManualCreationApplicationService.class);
        var controller = new ProjectMasterController();
        ReflectionTestUtils.setField(controller, "projectManualCreationApplicationService", service);
        var result = result();
        when(service.previewMatching(any(), eq(10L), eq(20L), any())).thenReturn(result);
        var response = controller.matchTemplates(request()).getData();
        assertEquals("MATCHED", response.getOutcome());
        assertEquals("watermark", response.getCandidateWatermark());
        assertEquals(result.getEvaluations(), response.getEvaluations());
        assertEquals("工前准备适用规则", response.getCandidates().getFirst().getRuleName());
        verify(service).previewMatching(argThat(draft -> "现场工勘".equals(draft.getProjectName())
                && "C-001".equals(draft.getCustomerCode()) && draft.getParentId() == null), eq(10L), eq(20L),
                argThat(actor -> actor.tenantId().equals(7L)));
    }
    @Test void selectedCustomerEntryUsesOwnerResolvingPreviewNotLegacyPreview() {
        TenantContextHolder.setTenantId(7L);
        var service = mock(ProjectManualCreationApplicationService.class);
        when(service.previewWithSelectedCustomer(any(), eq(10L), eq(20L), any())).thenReturn(result());
        var response = new SelectedCustomerProjectController(service).matchTemplates(request()).getData();
        assertEquals("watermark", response.getCandidateWatermark());
        assertEquals(22L, response.getCandidates().getFirst().getTemplateRevisionId());
        verify(service, never()).previewMatching(any(), any(), any(), any());
    }
    @Test void previewFailuresCannotExposeInputsThroughGlobalUnexpectedErrorLogging() throws Exception {
        TenantContextHolder.setTenantId(7L);
        var service = mock(ProjectManualCreationApplicationService.class);
        when(service.previewMatching(any(), any(), any(), any())).thenThrow(new IllegalStateException("private-customer-input"));
        when(service.previewWithSelectedCustomer(any(), any(), any(), any())).thenThrow(new IllegalStateException("private-customer-input"));
        var legacy = new ProjectMasterController();
        ReflectionTestUtils.setField(legacy, "projectManualCreationApplicationService", service);
        var current = new SelectedCustomerProjectController(service);
        var failure = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> legacy.matchTemplates(request()));
        assertEquals(cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_MATCH_PREVIEW_FAILED.getCode(), failure.getCode());
        assertFalse(failure.getMessage().contains("private-customer-input"));
        assertNull(failure.getCause());
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> current.matchTemplates(request()));
        var denied = new cn.iocoder.yudao.framework.common.exception.ServiceException(cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN);
        doThrow(denied).when(service).previewMatching(any(), any(), any(), any());
        assertSame(denied, assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> legacy.matchTemplates(request())));
        var templates = mock(ProjectTemplateService.class);
        when(templates.matchPreview(any())).thenThrow(new IllegalStateException("private-facts"));
        var templateController = new cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.ProjectTemplateController();
        ReflectionTestUtils.setField(templateController, "projectTemplateService", templates);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> templateController.matchPreview(
                new cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateMatchPreviewReqVO()));
        var drafts = mock(cn.iocoder.yudao.module.pms.project.service.projectsplit.ProjectSplitDraftService.class);
        when(drafts.requireTemplateSelectionParent(any(), any())).thenThrow(new IllegalStateException("private-child-input"));
        var childController = new cn.iocoder.yudao.module.pms.project.controller.admin.projectsplit.ProjectChildTemplateController(
                drafts, mock(ProjectTemplateSelectionService.class),
                new cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectChildDraftFactory(
                        mock(cn.iocoder.yudao.module.system.api.dept.DeptApi.class)));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> childController.options(
                100L, new cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplatePageReqVO(),
                "现场工勘", "CHILD", "OFFICE"));
        for (var type : List.of(ProjectMasterController.class, SelectedCustomerProjectController.class)) {
            var logging = type.getMethod("matchTemplates", ProjectMatchTemplatesReqVO.class)
                    .getAnnotation(cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog.class);
            assertFalse(logging.requestEnable()); assertFalse(logging.responseEnable());
        }
    }

    private ProjectMatchTemplatesReqVO request() {
        var request = new ProjectMatchTemplatesReqVO(); request.setProjectName("现场工勘"); request.setCustomerCode("C-001");
        request.setOrderOfficeCompanyId(10L); request.setOrderOfficeDepartmentId(20L);
        request.setSigningMethod("DIRECT_SIGN"); request.setProjectCategory("GENERAL"); request.setImplementationMode("DIRECT_SERVICE");
        return request;
    }
    private TemplateMatchResult result() {
        var candidate = new TemplateMatchCandidate(); candidate.setTemplateRevisionId(22L); candidate.setRuleName("工前准备适用规则");
        var result = TemplateMatchResult.matched(candidate); result.setCandidateWatermark("watermark");
        result.setEvaluations(List.of(new TemplateMatchResult.Evaluation(2L, 22L, "工前准备适用规则",
                new RuleEvaluation("revision:22", RuleEvaluation.Outcome.MATCHED, null, List.of(), List.of(), List.of()))));
        return result;
    }
}
