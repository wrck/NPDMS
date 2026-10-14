package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskListMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskPageQuery;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.CutoverDashboardQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardContext;
import cn.iocoder.yudao.module.pms.cutover.service.task.query.CutoverTaskListQuery;
import cn.iocoder.yudao.module.pms.cutover.service.task.view.CutoverTaskViews;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskQueryService.require;
import static cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationException.Code.*;

/** 任务列表先限定项目或审批授权范围，再按来源、阶段、状态分页。 */
@Service
@RequiredArgsConstructor
public class CutoverTaskListService {
    private final CutoverTaskListMapper mapper;
    private final CutoverDashboardQueryService dashboard;
    private final CutoverTaskQueryService tasks;
    private final AdminUserApi users;

    public PageResult<Item> page(CutoverDashboardContext context, CutoverTaskListQuery query) {
        require(context != null && query != null && query.listKind() != null
                && query.pageNo() > 0 && query.pageSize() > 0 && query.pageSize() <= 100,
                INVALID_REQUEST, "任务列表分页参数非法");
        require(query.projectId() == null || query.projectId() > 0, INVALID_REQUEST, "项目编号非法");
        require(query.currentStage() == null || Set.of("P2", "P3", "P4", "P5", "P6").contains(query.currentStage()),
                INVALID_REQUEST, "割接步骤非法");
        require(query.taskStatus() == null || Set.of("GRADE_CONFIRMING", "SURVEYING", "PLAN_DRAFTING", "APPROVING",
                "CLOSURE_IN_PROGRESS", "ARCHIVED").contains(query.taskStatus()), INVALID_REQUEST, "任务状态非法");
        var scope = dashboard.scope(context);
        Set<Long> visible = scope.visibleProjectIds();
        Set<Long> taskIds = dashboard.taskIds(new CutoverDashboardQueryService.Selection(context, scope), query.listKind());
        if (taskIds != null && taskIds.isEmpty()) return PageResult.empty();
        Set<Long> approvalTasks = query.listKind() == CutoverTaskListQuery.ListKind.SELF_CREATED
                ? Set.of() : scope.approvalInstances().keySet();
        if (visible.isEmpty() && approvalTasks.isEmpty()) return PageResult.empty();
        require(query.projectId() == null || visible.contains(query.projectId()) || !approvalTasks.isEmpty(),
                DATA_SCOPE_FORBIDDEN, "项目不在授权范围内");
        PageParam pagination = new PageParam();
        pagination.setPageNo(query.pageNo()); pagination.setPageSize(query.pageSize());
        var result = mapper.selectTaskPage(CutoverTaskPageQuery.builder()
                .tenantId(context.task().tenantId()).visibleProjectIds(visible).approvalTaskIds(approvalTasks).taskIds(taskIds)
                .intakeSourceType(query.listKind() == CutoverTaskListQuery.ListKind.SELF_CREATED ? "SELF_CREATED" : null)
                .projectId(query.projectId()).currentStage(query.currentStage()).taskStatus(query.taskStatus())
                .pageParam(pagination).build());
        Set<Long> creators = result.getList().stream()
                .filter(row -> visible.contains(row.getProjectId())
                        && !(query.listKind() == CutoverTaskListQuery.ListKind.TODO && approvalTasks.contains(row.getId())))
                .map(row -> creatorId(row.getCreator()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        var names = creators.isEmpty() ? Map.<Long, String>of() : users.getUserList(creators).stream()
                .collect(Collectors.toMap(user -> user.getId(), user -> user.getNickname()));
        return new PageResult<>(result.getList().stream().map(row -> {
            Long creator = creatorId(row.getCreator());
            boolean approvalOnly = !visible.contains(row.getProjectId())
                    || query.listKind() == CutoverTaskListQuery.ListKind.TODO && approvalTasks.contains(row.getId());
            return new Item(tasks.summary(row).withApprovalOnly(approvalOnly),
                    approvalOnly || creator == null ? null : names.get(creator), scope.approvalInstances().get(row.getId()));
        }).toList(), result.getTotal());
    }

    private static Long creatorId(String creator) {
        if (creator == null || !creator.matches("[1-9][0-9]*")) return null;
        try { return Long.valueOf(creator); } catch (NumberFormatException ex) { return null; }
    }

    public record Item(CutoverTaskViews.Summary task, String creatorName, Long approvalInstanceId) { }
}
