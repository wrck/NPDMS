package cn.iocoder.yudao.module.pms.project.service.projectattribute;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectattribute.ProjectTemplateMatchHistoryDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectattribute.ProjectTemplateMatchHistoryMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectattribute.query.ProjectTemplateMatchHistoryPageQuery;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTemplateMatchHistoryQueryServiceTest {

    @Mock private ProjectManualCreationService projectService;
    @Mock private ProjectTemplateMatchHistoryMapper historyMapper;
    @InjectMocks private ProjectTemplateMatchHistoryQueryService service;

    @Test
    void queryRequiresVisibleProjectBeforePaging() {
        ProjectTemplateMatchHistoryPageQuery query = new ProjectTemplateMatchHistoryPageQuery(
                1L, 100L, new PageParam(), null, null, null, null, null, null, false);
        PageResult<ProjectTemplateMatchHistoryDO> expected = PageResult.empty();
        when(historyMapper.selectPage(query)).thenReturn(expected);

        var result = service.page(query, new ProjectTemplateMatchHistoryQueryService.Actor(1L, 7L));

        assertSame(expected, result);
        verify(projectService).getProject(100L,
                new ProjectManualCreationService.ProjectAccessActor(1L, 7L));
        verify(historyMapper).selectPage(query);
    }
}
