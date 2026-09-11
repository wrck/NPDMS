package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewQueryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewRevision;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TemplateDesignerDependencyValidatorTest {

    @Test
    void validateAcceptsExactPublishedBusinessView() {
        BusinessViewQueryApi api = mock(BusinessViewQueryApi.class);
        when(api.getRevision(any())).thenReturn(revision());
        var validator = new TemplateDesignerDependencyValidator(api);

        assertTrue(validator.validate(designer(), false).isEmpty());
        verify(api).getRevision(new BusinessViewQueryApi.Query(91L,
                BusinessViewQueryApi.Purpose.NEW_REFERENCE, 7));
        verify(api, never()).lockAndRevalidateAll(any());
    }

    @Test
    void publishUsesBatchLockAndRejectsSnapshotDrift() {
        BusinessViewQueryApi api = mock(BusinessViewQueryApi.class);
        BusinessViewRevision changed = new BusinessViewRevision(91L, "SITE_SURVEY", "VIEW", 3L,
                "SOL", BusinessViewComponentProvider.ViewSource.PAGE,
                "SOL_SITE_SURVEY_V2", "2", null,
                JsonUtils.parseTree("{\"required\":[\"project\"]}"), JsonUtils.parseTree("[\"VIEW\"]"),
                "Q", "C", "P", LocalDateTime.now(), null, 7, "PUBLISHED", Set.of());
        when(api.lockAndRevalidateAll(any())).thenReturn(List.of(changed));
        var validator = new TemplateDesignerDependencyValidator(api);

        var issues = validator.validate(designer(), true);

        assertTrue(issues.stream().anyMatch(issue -> "BUSINESS_VIEW_SNAPSHOT_MISMATCH".equals(issue.code())));
        verify(api).lockAndRevalidateAll(List.of(new BusinessViewQueryApi.Query(91L,
                BusinessViewQueryApi.Purpose.NEW_REFERENCE, 7)));
        verify(api, never()).getRevision(any());
    }

    @Test
    void unavailableViewFailsClosedAtExactBinding() {
        BusinessViewQueryApi api = mock(BusinessViewQueryApi.class);
        when(api.getRevision(any())).thenThrow(new IllegalArgumentException("disabled"));
        var validator = new TemplateDesignerDependencyValidator(api);

        var issues = validator.validate(designer(), false);

        assertEquals(1, issues.size());
        assertEquals("tasks[0].workBinding", issues.getFirst().field());
        assertEquals("BUSINESS_VIEW_UNAVAILABLE", issues.getFirst().code());
    }

    private TemplateDesignerDocument designer() {
        var designer = new TemplateDesignerDocument();
        var task = new TemplateDesignerDocument.TaskNode();
        task.setNodeKey("task:T1");
        task.setCode("T1");
        task.setStageCode("S1");
        var binding = new TemplateDesignerDocument.WorkBindingSpec();
        binding.setType("BUSINESS_COMPONENT");
        binding.setTargetContextCode("SOL");
        binding.setTargetObjectType("SITE_SURVEY");
        binding.setTargetObjectKey("PROJECT_SITE_SURVEY");
        binding.setComponentKey("SOL_SITE_SURVEY");
        binding.setParameters(JsonUtils.parseTree("{\"businessViewRevisionId\":91}"));
        binding.setBusinessViewSnapshot(JsonUtils.parseTree("""
                {"id":91,"version":7,"status":"PUBLISHED","ownerContext":"SOL","entityType":"SITE_SURVEY",
                 "componentKey":"SOL_SITE_SURVEY","componentVersion":"1","viewSource":"PAGE"}
                """));
        task.setWorkBinding(binding);
        designer.getTasks().add(task);
        return designer;
    }

    private BusinessViewRevision revision() {
        return new BusinessViewRevision(91L, "SITE_SURVEY", "VIEW", 3L,
                "SOL", BusinessViewComponentProvider.ViewSource.PAGE,
                "SOL_SITE_SURVEY", "1", null,
                JsonUtils.parseTree("{\"required\":[\"project\"]}"), JsonUtils.parseTree("[\"VIEW\"]"),
                "Q", "C", "P", LocalDateTime.now(), null, 7, "PUBLISHED", Set.of());
    }
}
