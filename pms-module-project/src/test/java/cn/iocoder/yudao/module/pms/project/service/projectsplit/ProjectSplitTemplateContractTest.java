package cn.iocoder.yudao.module.pms.project.service.projectsplit;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectsplit.ProjectSplitRequestController;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectsplit.vo.ProjectSplitDraftSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitItemDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitRequestDO;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ProjectSplitDraftCommand;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectSplitTemplateContractTest {
    @Test void draftRequestAndResponseKeepTwoIndependentTemplateSelections() throws Exception {
        var json = new ClassPathResource("project-template/independent-child-split.json").getContentAsString(StandardCharsets.UTF_8);
        var input = JsonUtils.parseObject(json, ProjectSplitDraftSaveReqVO.class);
        var drafts = mock(ProjectSplitDraftService.class);
        when(drafts.saveDraft(any(), any())).thenAnswer(call -> {
            ProjectSplitDraftCommand command = call.getArgument(0);
            assertEquals(993009245201L, command.items().get(0).templateRevisionId());
            assertEquals(993009245202L, command.items().get(1).templateRevisionId());
            var request = new ProjectSplitRequestDO(); request.setId(20L); request.setParentProjectId(command.parentProjectId());
            var items = command.items().stream().map(source -> {
                var item = new ProjectSplitItemDO(); item.setId(source.templateRevisionId());
                item.setClientItemKey(source.clientItemKey()); item.setProjectName(source.projectName());
                item.setTemplateRevisionId(source.templateRevisionId()); item.setTemplateSelectionReason(source.templateSelectionReason());
                return item;
            }).toList();
            return new ProjectSplitDraftService.DraftResult(request, items, List.of());
        });
        // Direct controller invocation verifies DTO conversion only, not HTTP authentication or persistence.
        var controller = new ProjectSplitRequestController(drafts, mock(ProjectSplitPreviewService.class), mock(ProjectSplitApplicationService.class));
        var response = controller.create(input).getData();
        assertEquals(993009245201L, response.getItems().get(0).getTemplateRevisionId());
        assertEquals(993009245202L, response.getItems().get(1).getTemplateRevisionId());
        assertEquals("需求分析独立交付", response.getItems().get(1).getTemplateSelectionReason());
    }
}
