package cn.iocoder.yudao.module.pms.cutover.service.taskv2;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverWorkbenchTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverWorkbenchTaskPageQuery;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.CutoverRuntimeDashboardService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRuntimeRoleApprovalAudience;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.context.CutoverTaskContext;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.query.CutoverWorkbenchTaskListQuery;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.view.CutoverTaskViews;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskQueryService.require;
import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException.Code.*;
import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.query.CutoverWorkbenchTaskListQuery.ListKind.TODO;

/** 首页分类先限定授权任务与条件，再分页；创建人使用持久化creator，独立于owner。 */
@Service
@RequiredArgsConstructor
public class CutoverWorkbenchTaskQueryService {
    private final CutoverWorkbenchTaskMapper mapper;
    private final CutoverProjectScopePort scopes;
    private final CutoverRuntimeDashboardService dashboard;
    private final CutoverRuntimeTaskQueryService tasks;
    private final AdminUserApi users;
    private final CutoverRuntimeRoleApprovalAudience approvalAudience;

    public PageResult<Item> page(CutoverTaskContext context, CutoverWorkbenchTaskListQuery query) {
        require(context != null && query != null && query.listKind() != null
                && query.pageNo() > 0 && query.pageSize() > 0 && query.pageSize() <= 100,
                INVALID_REQUEST, "首页列表分页参数非法");
        require(query.projectId() == null || query.projectId() > 0, INVALID_REQUEST, "项目编号非法");
        require(query.currentStage() == null || Set.of("P2", "P3", "P4", "P5", "P6").contains(query.currentStage()),
                INVALID_REQUEST, "割接步骤非法");
        require(query.taskStatus() == null || Set.of("GRADE_CONFIRMING", "SURVEYING", "PLAN_DRAFTING", "APPROVING",
                "CLOSURE_IN_PROGRESS", "ARCHIVED", "LEGACY_UNKNOWN").contains(query.taskStatus()), INVALID_REQUEST, "任务状态非法");
        Set<Long> visible = scopes.resolveAllCurrent(context.actorId(), "ACTION_VIEW");
        require(visible != null, PROJ_PROVIDER_UNAVAILABLE, "项目范围暂时不可用");
        Set<Long> taskIds = query.listKind() == TODO ? dashboard.taskIds("todo") : null;
        if (taskIds != null && taskIds.isEmpty()) return PageResult.empty();
        var approvalTasks = query.listKind() == TODO ? approvalAudience.authorizedTaskIds(
                new CutoverRuntimeRoleApprovalAudience.Selection(context.tenantId(), context.actorId(), taskIds)) : Set.<Long>of();
        if (visible.isEmpty() && approvalTasks.isEmpty()) return PageResult.empty();
        require(query.projectId() == null || visible.contains(query.projectId()) || !approvalTasks.isEmpty(),
                DATA_SCOPE_FORBIDDEN, "项目不在授权范围内");
        PageParam pagination = new PageParam();
        pagination.setPageNo(query.pageNo()); pagination.setPageSize(query.pageSize());
        var result = mapper.selectWorkbenchPage(CutoverWorkbenchTaskPageQuery.builder()
                .tenantId(context.tenantId()).visibleProjectIds(visible).approvalTaskIds(approvalTasks).taskIds(taskIds)
                .intakeSourceType(query.listKind() == TODO ? null : "SELF_CREATED")
                .projectId(query.projectId()).currentStage(query.currentStage()).taskStatus(query.taskStatus())
                .pageParam(pagination).build());
        Set<Long> creators = result.getList().stream().map(row -> creatorId(row.getCreator()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        var names = creators.isEmpty() ? Map.<Long, String>of() : users.getUserList(creators).stream()
                .collect(Collectors.toMap(user -> user.getId(), user -> user.getNickname()));
        return new PageResult<>(result.getList().stream().map(row -> {
            Long creator = creatorId(row.getCreator());
            return new Item(tasks.summary(row).withApprovalOnly(approvalTasks.contains(row.getId())
                    || !visible.contains(row.getProjectId())), creator == null ? null : names.get(creator));
        }).toList(), result.getTotal());
    }

    private static Long creatorId(String creator) {
        if (creator == null || !creator.matches("[1-9][0-9]*")) return null;
        try { return Long.valueOf(creator); } catch (NumberFormatException ex) { return null; }
    }

    public record Item(CutoverTaskViews.Summary task, String creatorName) { }
}
