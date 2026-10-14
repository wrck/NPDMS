package cn.iocoder.yudao.module.pms.cutover.framework.adapter;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureRowQuery;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;

import java.util.*;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class CutoverCollectionSources {
    public static final String CHECKLIST = "cutover-checklist";
    public static final Map<String, String> CLOSURE_STAGES = Map.of(
            "cutover-pre-check", "PRE_CHECK", "cutover-execution", "EXECUTION",
            "cutover-test", "TEST", "cutover-rollback", "ROLLBACK", "cutover-post-collection", "POST_COLLECTION");
    private final CutoverTaskMapper tasks;
    private final CutoverFrozenDeviceMapper devices;
    private final CutoverChecklistMapper checklists;
    private final CutoverChecklistItemMapper items;
    private final CutoverClosureMapper closures;
    private final CutoverProjectScopePort scopes;
    private final PermissionApi permissions;
    private final cn.iocoder.yudao.module.pms.cutover.service.task.CutoverExecutionReadinessPolicy readiness;

    @Bean CollectionSourceAdapter cutoverChecklistCollectionSource() { return source(CHECKLIST, "调研采集"); }
    @Bean CollectionSourceAdapter cutoverPreCheckCollectionSource() { return source("cutover-pre-check", "割接前检查"); }
    @Bean CollectionSourceAdapter cutoverExecutionCollectionSource() { return source("cutover-execution", "割接执行核查"); }
    @Bean CollectionSourceAdapter cutoverTestCollectionSource() { return source("cutover-test", "割接测试"); }
    @Bean CollectionSourceAdapter cutoverRollbackCollectionSource() { return source("cutover-rollback", "回退核查"); }
    @Bean CollectionSourceAdapter cutoverPostCollectionSource() { return source("cutover-post-collection", "割接后采集"); }

    private CollectionSourceAdapter source(String entry, String title) {
        return new CollectionSourceAdapter() {
            @Override public String entry() { return entry; }
            @Override public Source authorize(AuthorizationQuery query) {
                Long tenant = query.tenantId();
                Long actor = query.actorId();
                Long id = query.objectId();
                Long device = query.deviceId();
                Access access = query.access();
                Long expected = query.expectedVersion();
                boolean edit = access != Access.READ;
                String permission = edit ? "request-collection" : CHECKLIST.equals(entry) ? "query" : "query-closure";
                if (actor == null || !permissions.hasAnyPermissions(actor, "pms:cutover-task:" + permission)) {
                    throw new AccessDeniedException("无割接采集权限");
                }
                Owner owner = owner(tenant, entry, id, false);
                var task = owner.task();
                String action = edit ? "ACTION_EDIT" : "ACTION_VIEW";
                var scope = scopes.inspect(actor, task.getProjectId(), action);
                if (scope == null || !scope.allowed()) throw new AccessDeniedException("无项目权限");
                if (edit && !Objects.equals(actor, task.getOwnerUserId())) throw new AccessDeniedException("仅任务负责人可操作采集");
                if (access == Access.EXECUTE || access == Access.CONSUME) {
                    var locked = scopes.lockAndRevalidate(CutoverProjectScopePort.RevalidationQuery.builder()
                .actorId(actor)
                .projectId(task.getProjectId())
                .action(action)
                .expectedProjectScopeVersion(scope.projectScopeVersion())
                .build());
                    if (locked == null || !locked.allowed()) throw new AccessDeniedException("项目权限已变化");
                    owner = owner(tenant, entry, id, true);
                    task = owner.task();
                }
                var candidates = taskDevices(tenant, task.getId());
                Long selected = device != null ? device : candidates.size() == 1 ? candidates.getFirst().getDeviceId() : null;
                var selectedRow = candidates.stream().filter(d -> Objects.equals(d.getDeviceId(), selected)).findFirst().orElse(null);
                if (selected != null && selectedRow == null) throw new CollectionOperationException("设备不属于当前割接范围");
                if (access == Access.EXECUTE && (!owner.editable() || !Objects.equals(expected, owner.version()) || selected == null)) {
                    throw new CollectionOperationException("割接记录已变化或不可采集，请刷新并选择设备");
                }
                if (access == Access.EXECUTE && !CHECKLIST.equals(entry)) readiness.requireCurrent(task);
                boolean canExecute = owner.editable() && Objects.equals(actor, task.getOwnerUserId())
                        && permissions.hasAnyPermissions(actor, "pms:cutover-task:request-collection");
                return Source.builder()
                        .entry(entry)
                        .objectId(id)
                        .sourceContext("CUT")
                        .sourceObjectType(owner.objectType())
                        .projectId(task.getProjectId())
                        .deviceId(selected)
                        .deviceName(selectedRow == null ? "" : selectedRow.getSerialNumberSnapshot())
                        .version(owner.version())
                        .manualAllowed(!CHECKLIST.equals(entry))
                        .canExecute(canExecute)
                        .completionMode("BUSINESS_CONSUMPTION")
                        .title(title)
                        .build();
            }
        };
    }

    /** 仅解析 CUT 自己的归属；对外操作须经上面的权限检查。回传接收器用此核对不可变来源。 */
    public Owner owner(Long tenant, String entry, Long id, boolean lock) {
        boolean checklist = CHECKLIST.equals(entry);
        if (!checklist && !CLOSURE_STAGES.containsKey(entry)) throw new CollectionOperationException("割接采集入口无效");
        Long taskId;
        Long checklistId = null;
        if (checklist) {
            var item = items.selectById(id);
            if (item == null || !Objects.equals(item.getTenantId(), tenant)) throw new CollectionOperationException("清单项不存在");
            checklistId = item.getChecklistId();
            var list = checklists.selectById(checklistId);
            if (list == null || !Objects.equals(list.getTenantId(), tenant)) throw new CollectionOperationException("清单不存在");
            taskId = list.getCutoverTaskId();
        } else {
            var closure = closures.selectById(id);
            if (closure == null || !Objects.equals(closure.getTenantId(), tenant)) throw new CollectionOperationException("闭环记录不存在");
            taskId = closure.getTaskId();
        }
        var task = lock ? tasks.selectForUpdate(new CutoverTaskRowQuery(tenant, taskId)) : tasks.selectById(taskId);
        if (task == null || !Objects.equals(task.getTenantId(), tenant)) throw new CollectionOperationException("割接任务不存在");
        if (checklist) {
            var list = lock ? checklists.selectCurrentForUpdate(new CutoverChecklistRowQuery(tenant, taskId, checklistId))
                    : checklists.selectById(checklistId);
            var item = lock ? items.selectListForUpdate(new CutoverChecklistItemsQuery(tenant, checklistId)).stream()
                    .filter(i -> Objects.equals(i.getId(), id)).findFirst().orElse(null) : items.selectById(id);
            if (list == null || item == null) throw new CollectionOperationException("清单项不存在");
            boolean editable = "NEW_PLATFORM".equals(task.getTaskOrigin()) && "P3".equals(task.getCurrentStage())
                    && "SURVEYING".equals(task.getTaskStatus()) && "DRAFT".equals(list.getStatusCode())
                    && Boolean.TRUE.equals(item.getApplicableFlag()) && "COLLECTION".equals(item.getWorkModeCode());
            return new Owner(task, "CUTOVER_CHECKLIST_ITEM", list.getVersion(), editable);
        }
        var closure = lock ? closures.selectByTaskForUpdate(new CutoverClosureRowQuery(tenant, taskId)) : closures.selectById(id);
        if (closure == null || !Objects.equals(closure.getId(), id)) throw new CollectionOperationException("闭环记录不存在");
        return new Owner(task, "CUTOVER_CLOSURE", closure.getVersion(), "NEW_PLATFORM".equals(task.getTaskOrigin())
                && "P6".equals(task.getCurrentStage()) && "CLOSURE_IN_PROGRESS".equals(task.getTaskStatus())
                && "DRAFT".equals(closure.getStatusCode()));
    }

    public List<CutoverTaskDeviceScopeDO> taskDevices(Long tenant, Long taskId) {
        return devices.byTask(new cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskDeviceListQuery(tenant, taskId));
    }

    public record Owner(CutoverTaskDO task, String objectType, Long version, boolean editable) { }
}
