package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerLifecycleStatus;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerCodeQuery;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.customer.ProjectCustomerReferenceProvider;
import cn.iocoder.yudao.module.pms.project.api.customer.ProjectCustomerReferenceProvider.Source;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectContactCustomerUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectRules.LIFECYCLE_STATUS_ACTIVE;

/** Z02：客户关联更正；不迁移下游数据，不改旧资料编辑入口。 */
@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class ProjectCustomerCorrectionService {
    public static final String PERMISSION = "pms:project:update";
    public static final String OPERATION = "PROJECT_CUSTOMER_CORRECT";
    private final ProjectManualCreationService projects;
    private final ProjectMasterMapper mapper;
    private final PermissionCommonApi permissions;
    private final ProjectScopeApi scopes;
    private final CustomerQueryApi customers;
    private final PlatformCommandExecutionApi commands;
    private final List<ProjectCustomerReferenceProvider> referenceProviders;

    public Inspection inspect(Long projectId, Actor actor) {
        var project = authorizedProject(projectId, actor);
        var references = references(projectId, actor.tenantId());
        return new Inspection(projectId, project.getVersion(), project.getCustomerCode(), project.getCustomerName(),
                LIFECYCLE_STATUS_ACTIVE.equals(project.getLifecycleStatus()) && references.isEmpty(), references);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Result correct(Command command, Actor actor) {
        if (command == null || command.expectedVersion() == null || command.expectedVersion() < 0
                || command.customerCode() == null || command.customerCode().isBlank()
                || command.customerCode().length() > 64 || command.key() == null || command.key().isBlank()
                || command.key().length() > 128 || command.reason() != null && command.reason().length() > 500)
            throw new IllegalArgumentException("客户更正参数无效");
        authorizedProject(command.projectId(), actor);
        var execution = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(actor.tenantId(),
                OPERATION, actor.userId(), command.key()), DigestUtil.sha256Hex(JsonUtils.toJsonString(command)), Result.class,
                () -> correctOnce(command, actor), result -> new PlatformCommandExecutionApi.SuccessFacts(
                        OPERATION, "Project", String.valueOf(command.projectId()), actor.correlationId(),
                        JsonUtils.toJsonString(result), null, null));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        return execution.response();
    }

    private Result correctOnce(Command command, Actor actor) {
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(), actor.userId(),
                command.projectId(), ProjectScopeApi.ACTION_MANAGE));
        var locked = scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(actor.tenantId(), actor.userId(),
                command.projectId(), ProjectScopeApi.ACTION_MANAGE, scope.treeVersion()));
        if (!locked.fullProjectIds().contains(command.projectId())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        var project = mapper.selectByIdForUpdate(command.projectId());
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())
                || !LIFECYCLE_STATUS_ACTIVE.equals(project.getLifecycleStatus())) throw exception(PROJECT_AUTHORIZATION_FORBIDDEN);
        if (!Objects.equals(project.getVersion(), command.expectedVersion())) throw exception(PROJECT_VERSION_CONFLICT);
        if (command.customerCode().trim().equals(project.getCustomerCode()))
            return result(project, project.getCustomerId(), project.getCustomerCode(), project.getCustomerName(), false, command.reason());
        var references = references(project.getId(), actor.tenantId());
        if (!references.isEmpty()) throw exception(PROJECT_CUSTOMER_REFERENCED,
                references.stream().map(item -> item.label() + " " + item.count() + " 条").collect(Collectors.joining("；")));
        var customer = customers.lockCustomerByCode(new CustomerCodeQuery(command.customerCode().trim(), actor.userId()));
        if (customer == null || !Objects.equals(customer.tenantId(), actor.tenantId())
                || !CustomerLifecycleStatus.ENABLED.name().equals(customer.lifecycleStatus())) throw exception(PROJECT_CUSTOMER_UNAVAILABLE);
        if (mapper.correctCustomerIfMatch(new ProjectContactCustomerUpdate(actor.tenantId(), project.getId(),
                command.expectedVersion(), customer.id(), customer.code(), customer.name(), String.valueOf(actor.userId()))) != 1)
            throw exception(PROJECT_VERSION_CONFLICT);
        return result(project, customer.id(), customer.code(), customer.name(), true, command.reason());
    }

    private ProjectMasterDO authorizedProject(Long id, Actor actor) {
        if (id == null || id <= 0 || actor == null || actor.tenantId() == null || actor.userId() == null
                || actor.userId() <= 0 || !Objects.equals(actor.tenantId(), TenantContextHolder.getTenantId())
                || !permissions.hasAnyPermissions(actor.userId(), PERMISSION)) throw exception(PROJECT_AUTHORIZATION_FORBIDDEN);
        return projects.getProjectForManage(id,
                new ProjectManualCreationService.ProjectAccessActor(actor.tenantId(), actor.userId()));
    }

    private List<Reference> references(Long id, Long tenantId) {
        var providers = new EnumMap<Source, ProjectCustomerReferenceProvider>(Source.class);
        for (var provider : referenceProviders) {
            if (providers.put(provider.source(), provider) != null)
                throw exception(PROJECT_CUSTOMER_REFERENCE_UNAVAILABLE, provider.source().label());
        }
        var result = new ArrayList<Reference>();
        for (Source source : Source.values()) {
            var provider = providers.get(source);
            if (provider == null) throw exception(PROJECT_CUSTOMER_REFERENCE_UNAVAILABLE, source.label());
            long count;
            try { count = provider.countReferences(new ProjectCustomerReferenceProvider.Query(tenantId, id)); }
            catch (RuntimeException failure) {
                log.warn("Customer reference lookup failed: source={}, projectId={}", source, id, failure);
                throw exception(PROJECT_CUSTOMER_REFERENCE_UNAVAILABLE, source.label());
            }
            if (count < 0) throw exception(PROJECT_CUSTOMER_REFERENCE_UNAVAILABLE, source.label());
            if (count > 0) result.add(new Reference(source.name(), source.label(), count));
        }
        return List.copyOf(result);
    }

    private Result result(ProjectMasterDO project, Long customerId, String code, String name, boolean changed, String reason) {
        return new Result(project.getId(), project.getVersion() + (changed ? 1 : 0), project.getCustomerId(),
                project.getCustomerCode(), project.getCustomerName(), customerId, code, name, changed, reason);
    }
    public record Actor(Long tenantId, Long userId, String correlationId) { }
    public record Command(Long projectId, Integer expectedVersion, String customerCode, String reason, String key) { }
    public record Reference(String source, String label, long count) { }
    public record Inspection(Long projectId, Integer version, String customerCode, String customerName,
                             boolean canCorrect, List<Reference> references) { }
    public record Result(Long projectId, Integer version, Long previousCustomerId, String previousCustomerCode,
                         String previousCustomerName, Long customerId, String customerCode, String customerName,
                         boolean changed, String reason) { }
}
