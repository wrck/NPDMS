package cn.iocoder.yudao.module.pms.project.service.projectattribute;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectattribute.ProjectTemplateMatchHistoryDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectattribute.ProjectTemplateMatchHistoryMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectattribute.query.ProjectTemplateMatchHistoryPageQuery;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/** 按项目可见范围分页读取不可变模板匹配历史。 */
@Service
public class ProjectTemplateMatchHistoryQueryService {

    @Resource
    private ProjectManualCreationService projectService;
    @Resource
    private ProjectTemplateMatchHistoryMapper historyMapper;

    public PageResult<ProjectTemplateMatchHistoryDO> page(
            ProjectTemplateMatchHistoryPageQuery query, Actor actor) {
        if (query == null || query.tenantId() == null || query.projectId() == null
                || query.pageParam() == null || actor == null || actor.tenantId() == null
                || actor.actorId() == null || !query.tenantId().equals(actor.tenantId())) {
            throw new IllegalArgumentException("模板匹配历史查询不完整");
        }
        projectService.getProject(query.projectId(),
                new ProjectManualCreationService.ProjectAccessActor(actor.tenantId(), actor.actorId()));
        return historyMapper.selectPage(query);
    }

    public record Actor(Long tenantId, Long actorId) {
    }
}
