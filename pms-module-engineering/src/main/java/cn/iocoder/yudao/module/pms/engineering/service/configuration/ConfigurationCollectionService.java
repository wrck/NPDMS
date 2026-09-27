package cn.iocoder.yudao.module.pms.engineering.service.configuration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo.ConfigurationManualCommandReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.query.ConfigurationCollectionPageQuery;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/** EXE-03: manual command execution is explicitly authorized; no fake published template is created. */
@Service
@RequiredArgsConstructor
public class ConfigurationCollectionService {
    private final ConfigurationCollectionOwnerMapper configurations;
    private final ConfigurationCollectionMapper links;
    private final ProjectDeviceSelectionApi devices;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;
    private final CollectionTaskApi tasks;
    private final CollectionCallbackApi callbacks;
    private final CollectionLogAccessApi logs;
    private final ObjectProvider<CollectionDispatchApi> dispatcher;
    private final PlatformTransactionManager transactions;

    public Execution submit(Long configurationId, Long actor, ConfigurationManualCommandReqVO request) {
        try {
            validate(request);
            var dispatch = requiredDispatcher();
            String commandText = request.getCommands().replace("\r\n", "\n").replace('\r', '\n');
            List<String> commands = commandText.lines().filter(line -> !line.isBlank()).toList();
            if (commands.isEmpty()) throw rejected("请输入命令");
            String hash = digest(String.join("\n", commands));
            String requestDigest = digest(JsonUtils.toJsonString(List.of(configurationId, actor,
                    request.getExpectedVersion(), request.getHost(), request.getPort(), request.getProtocol(),
                    request.getUsername(), hash)));
            Prepared prepared = new TransactionTemplate(transactions).execute(status ->
                    prepare(configurationId, actor, request, hash, requestDigest, String.join("\n", commands)));
            if (prepared == null) throw rejected("下发记录创建失败");
            if (prepared.dispatch()) {
                try {
                    dispatch.dispatchManual(new CollectionDispatchApi.Command(tenant(), prepared.link().getPlatformTaskId(),
                            commands, request.getUsername(), request.getPassword(), request.getRequestKey()));
                } catch (RuntimeException failure) {
                    // PLT retains FAILED / RECONCILING facts. Return the durable task, never resend implicitly.
                    var task = task(prepared.link());
                    if ("PENDING_DISPATCH".equals(task.technicalStage())) throw rejected("任务已创建但尚未下发，请查看执行历史");
                }
            }
            return view(prepared.link());
        } finally {
            if (request != null && request.getPassword() != null) Arrays.fill(request.getPassword(), '\0');
        }
    }

    private Prepared prepare(Long id, Long actor, ConfigurationManualCommandReqVO request, String hash, String requestDigest,
                             String commandText) {
        ConfigurationDO configuration = authorize(id, actor, true, true);
        var existing = links.findRequest(tenant(), request.getRequestKey());
        if (existing != null) {
            if (!Objects.equals(existing.getConfigurationId(), id) || !requestDigest.equals(existing.getRequestDigest()))
                throw rejected("同一下发请求的内容已变化，请刷新后重新发起");
            return new Prepared(existing, false);
        }
        if (!Set.of(0, 1, 3).contains(configuration.getStatus())) throw rejected("已完成的配置记录不能下发命令");
        if (!Objects.equals(configuration.getVersion(), request.getExpectedVersion() == null ? null : request.getExpectedVersion().longValue())) throw rejected("配置记录已变化，请刷新");
        if (configuration.getEquipmentId() == null) throw rejected("请先关联项目设备");
        var selected = devices.validateSelection(configuration.getProjectId(), List.of(configuration.getEquipmentId()));
        if (selected.size() != 1 || !Objects.equals(selected.getFirst().equipmentId(), configuration.getEquipmentId()))
            throw rejected("设备不属于当前项目");
        String objectId = String.valueOf(id);
        var batch = tasks.createBatch(new CollectionBatchCreateCommand(tenant(), actor,
                "imp-manual-" + request.getRequestKey(), requestDigest, "IMP", "Configuration", objectId,
                String.valueOf(configuration.getProjectId()), "BUSINESS_CONSUMPTION",
                List.of(new CollectionTaskCreateItem(String.valueOf(configuration.getEquipmentId()),
                        selected.getFirst().name(), request.getHost(), request.getPort(), request.getProtocol(),
                        "manual-" + id, request.getRequestKey(), hash, "TEMPORARY_SECRET", null, null,
                        request.getRequestKey(), "IMP", "Configuration", objectId))));
        var link = new ConfigurationCollectionDO();
        link.setTenantId(tenant()); link.setConfigurationId(id); link.setEquipmentId(configuration.getEquipmentId());
        link.setActorId(actor); link.setRequestKey(request.getRequestKey()); link.setRequestDigest(requestDigest);
        link.setPlatformTaskId(batch.tasks().getFirst().platformTaskId()); link.setCreator(String.valueOf(actor));
        link.setCommandText(commandText);
        if (links.insert(link) != 1) throw rejected("执行记录保存失败");
        return new Prepared(link, true);
    }

    public PageResult<Execution> page(Long id, Long actor, int pageNo, int pageSize) {
        authorize(id, actor, false, false);
        if (pageNo < 1 || pageSize < 1 || pageSize > 50) throw rejected("分页参数无效");
        var page = links.page(new ConfigurationCollectionPageQuery(tenant(), id, pageNo, pageSize));
        return new PageResult<>(page.getList().stream().map(this::view).toList(), page.getTotal());
    }

    public Execution consume(Long id, Long executionId, Long actor) {
        return new TransactionTemplate(transactions).execute(status -> {
            ConfigurationDO configuration = authorize(id, actor, true, true);
            var link = link(id, executionId);
            var task = task(link);
            if (!Objects.equals(String.valueOf(configuration.getProjectId()), task.projectId())
                    || !Objects.equals(configuration.getEquipmentId(), link.getEquipmentId()))
                throw rejected("记录的项目或设备已变化，不能关联历史执行结果");
            if (task.fileVersionId() == null || task.resultVersion() == null) throw rejected("尚无可关联的日志");
            if (link.getConsumedResultVersion() != null) return view(link);
            if (!"RESULT_AVAILABLE".equals(task.status())) throw rejected("当前执行结果不能关联");
            callbacks.confirmConsumption(new CollectionConsumptionCommand(task.platformTaskId(), "IMP", "Configuration",
                    String.valueOf(id), task.resultVersion(), link.getRequestKey()));
            link.setConsumedResultVersion(task.resultVersion());
            if (links.updateById(link) != 1) throw rejected("日志关联保存失败");
            return view(link);
        });
    }

    public Execution findByRequestKey(Long id, Long actor, String requestKey) {
        authorize(id, actor, false, false);
        if (requestKey == null || !requestKey.matches("[a-zA-Z0-9-]{16,64}")) throw rejected("请求标识无效");
        var link = links.findRequest(tenant(), requestKey);
        return link == null || !Objects.equals(id, link.getConfigurationId()) ? null : view(link);
    }

    public void cancel(Long id, Long executionId, Long actor) {
        authorize(id, actor, true, false);
        requiredDispatcher().cancel(tenant(), task(link(id, executionId)).platformTaskId());
    }

    public String download(Long id, Long executionId, Long actor) {
        authorize(id, actor, false, false);
        return logs.download(tenant(), actor, link(id, executionId).getPlatformTaskId());
    }

    private ConfigurationDO authorize(Long id, Long actor, boolean edit, boolean lock) {
        if (actor == null || !permissions.hasAnyPermissions(actor, edit ? "pms:imp-configuration:update" : "pms:imp-configuration:query"))
            throw rejected("无配置调试操作权限");
        var configuration = lock ? configurations.lockById(id) : configurations.selectById(id);
        if (configuration == null || !Objects.equals(tenant(), configuration.getTenantId())) throw rejected("配置记录不存在");
        String action = edit ? ProjectScopeApi.ACTION_EDIT : ProjectScopeApi.ACTION_VIEW;
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant(), actor, configuration.getProjectId(), action));
        if (scope == null || !scope.fullProjectIds().contains(configuration.getProjectId())) throw rejected("无当前项目权限");
        if (lock) {
            var locked = scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant(), actor,
                    configuration.getProjectId(), action, scope.treeVersion()));
            if (locked == null || !Objects.equals(locked.treeVersion(), scope.treeVersion())
                    || !locked.fullProjectIds().contains(configuration.getProjectId())) throw rejected("项目权限已变化");
        }
        return configuration;
    }
    private ConfigurationCollectionDO link(Long id, Long executionId) {
        var link = links.selectById(executionId);
        if (link == null || !Objects.equals(tenant(), link.getTenantId()) || !Objects.equals(id, link.getConfigurationId()))
            throw rejected("执行记录不存在");
        return link;
    }
    private CollectionTaskDTO task(ConfigurationCollectionDO link) {
        var task = tasks.getTask(tenant(), link.getPlatformTaskId());
        var owner = configurations.selectById(link.getConfigurationId());
        if (task == null || !"IMP".equals(task.sourceContext()) || !"Configuration".equals(task.sourceObjectType())
                || !String.valueOf(link.getConfigurationId()).equals(task.sourceObjectId())) throw rejected("执行记录绑定异常");
        if (owner == null || !Objects.equals(owner.getTenantId(), tenant())
                || !Objects.equals(String.valueOf(owner.getProjectId()), task.projectId())) {
            throw rejected("配置记录的项目已变化，原执行记录不可在新项目操作");
        }
        return task;
    }
    private Execution view(ConfigurationCollectionDO link) { return new Execution(link.getId(), link.getActorId(),
            link.getCreateTime(), link.getConsumedResultVersion(), task(link), link.getCommandText()); }
    private CollectionDispatchApi requiredDispatcher() {
        var result = dispatcher.getIfAvailable();
        if (result == null) throw rejected("DAC 尚未配置启用");
        return result;
    }
    private static Long tenant() { return TenantContextHolder.getRequiredTenantId(); }
    private static void validate(ConfigurationManualCommandReqVO r) {
        if (r == null || r.getRequestKey() == null || !r.getRequestKey().matches("[a-zA-Z0-9-]{16,64}")
                || r.getExpectedVersion() == null || r.getHost() == null || !r.getHost().matches("[a-zA-Z0-9.:-]{1,253}")
                || r.getPort() == null || r.getPort() < 1 || r.getPort() > 65535
                || r.getProtocol() == null || !Set.of("SSH", "TELNET").contains(r.getProtocol())
                || r.getUsername() == null || r.getUsername().isBlank() || r.getUsername().length() > 128
                || r.getPassword() == null || r.getPassword().length == 0 || r.getPassword().length > 4096
                || r.getCommands() == null || r.getCommands().isBlank() || r.getCommands().length() > 65536
                || r.getCommands().indexOf('\0') >= 0) throw rejected("请填写有效的设备地址、连接凭证及命令");
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static cn.iocoder.yudao.framework.common.exception.ServiceException rejected(String message) {
        return cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONFIGURATION_COLLECTION_REJECTED, message);
    }
    private record Prepared(ConfigurationCollectionDO link, boolean dispatch) { }
    public record Execution(Long id, Long actorId, LocalDateTime createdAt, Long consumedResultVersion,
                            CollectionTaskDTO task, String commandText) { }
}
