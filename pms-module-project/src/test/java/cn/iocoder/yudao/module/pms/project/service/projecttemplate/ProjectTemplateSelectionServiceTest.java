package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchCandidate;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectTemplateSelectionServiceTest {
    private final ProjectTemplateService templates = mock(ProjectTemplateService.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final ProjectTemplateSelectionService service = new ProjectTemplateSelectionService(templates, permissions);

    @Test void acceptsRecommendedChildRevisionWithoutInheritingParentOrRequiringOverride() {
        var parent = parent(); var revision = available();
        var candidate = new TemplateMatchCandidate(); candidate.setTemplateRevisionId(201L);
        when(templates.matchPreview("A", "GENERAL", "B", null)).thenReturn(TemplateMatchResult.matched(candidate));
        var result = service.select(parent, 201L, null, 9L);
        assertSame(revision, result.revision()); assertFalse(result.override());
        assertEquals(100L, parent.getLifecycleTemplateRevisionId());
        verifyNoInteractions(permissions);
        verify(templates, never()).getRevisionById(100L);
    }

    @Test void overrideRequiresSeparatePermissionAndReasonAndUsesExactPublishedVersion() {
        var revision = available();
        when(templates.matchPreview("A", "GENERAL", "B", null)).thenReturn(TemplateMatchResult.noMatch("none"));
        assertThrows(ServiceException.class, () -> service.select(parent(), 201L, "工勘独立交付", 9L));
        when(permissions.hasAnyPermissions(9L, ProjectTemplateSelectionService.OVERRIDE_PERMISSION)).thenReturn(true);
        assertThrows(ServiceException.class, () -> service.select(parent(), 201L, " ", 9L));
        assertThrows(ServiceException.class, () -> service.select(parent(), 201L, "x".repeat(513), 9L));
        var result = service.select(parent(), 201L, "工勘独立交付", 9L);
        assertTrue(result.override()); assertSame(revision, result.revision());
        verify(templates, never()).getRevisionList(anyLong());
    }

    @Test void missingSelectionNeverFallsBackToParent() {
        assertThrows(ServiceException.class, () -> service.select(parent(), null, null, 9L));
        verifyNoInteractions(templates, permissions);
    }

    @Test void rejectsForeignTenantDraftRetiredAndBrokenExecutionSnapshot() {
        var revision = available();
        revision.setTenantId(2L);
        assertThrows(ServiceException.class, () -> service.requireAvailable(201L, 1L));
        revision.setTenantId(1L); revision.setStatus("DRAFT");
        assertThrows(ServiceException.class, () -> service.requireAvailable(201L, 1L));
        revision.setStatus("PUBLISHED");
        templates.getProjectTemplate(20L).setStatus("RETIRED");
        assertThrows(ServiceException.class, () -> service.requireAvailable(201L, 1L));
        templates.getProjectTemplate(20L).setStatus("ACTIVE");
        when(templates.getExecutionSnapshot(20L, 1)).thenThrow(new IllegalStateException("invalid snapshot"));
        assertThrows(IllegalStateException.class, () -> service.requireAvailable(201L, 1L));
    }

    @Test void pagedOptionsDoNotGrantOverrideAndPublishedUpdatesDoNotRewriteSelection() {
        var revision = available();
        var page = new ProjectTemplatePageReqVO(); page.setPageNo(2); page.setPageSize(20);
        var template = templates.getProjectTemplate(20L);
        when(templates.getProjectTemplatePage(page)).thenReturn(new PageResult<>(List.of(template), 21L));
        when(templates.getRevisionList(20L)).thenReturn(List.of(revision));
        when(templates.matchPreview("A", "GENERAL", "B", null)).thenReturn(TemplateMatchResult.noMatch("none"));
        var result = service.options(parent(), page, 9L);
        assertEquals(21L, result.getTotal()); assertEquals(2, page.getPageNo());
        assertEquals("ACTIVE", page.getStatus()); assertFalse(result.getList().getFirst().selectable());
        when(permissions.hasAnyPermissions(9L, ProjectTemplateSelectionService.OVERRIDE_PERMISSION)).thenReturn(true);
        assertTrue(service.options(parent(), page, 9L).getList().getFirst().selectable());
        when(templates.getExecutionSnapshot(20L, 1)).thenThrow(new IllegalStateException("broken"));
        assertFalse(service.options(parent(), page, 9L).getList().getFirst().selectable());
    }

    private ProjectTemplateRevisionDO available() {
        var template = new ProjectTemplateDO(); template.setId(20L); template.setTenantId(1L);
        template.setName("工前准备"); template.setStatus("ACTIVE");
        var revision = new ProjectTemplateRevisionDO(); revision.setId(201L); revision.setTenantId(1L);
        revision.setTemplateId(20L); revision.setRevisionNo(1); revision.setStatus("PUBLISHED");
        when(templates.getRevisionById(201L)).thenReturn(revision);
        when(templates.getProjectTemplate(20L)).thenReturn(template);
        return revision;
    }
    private ProjectMasterDO parent() {
        var value = new ProjectMasterDO(); value.setId(1L); value.setTenantId(1L);
        value.setLifecycleTemplateRevisionId(100L); value.setSigningMethod("A");
        value.setProjectCategory("GENERAL"); value.setImplementationMode("B"); return value;
    }
}
