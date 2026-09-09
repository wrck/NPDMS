package cn.iocoder.yudao.module.pms.platform.service.businessview;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.*;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.businessview.BusinessViewRevisionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.businessview.BusinessViewRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.businessview.query.*;
import cn.iocoder.yudao.module.pms.platform.domain.businessview.BusinessViewDescriptor;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.service.businessview.BusinessViewErrors.*;

/** PM-03 / F-PLT-003: registration commands only; no domain entity command or completion fact. */
@Service
@RequiredArgsConstructor
public class BusinessViewApplicationService implements BusinessViewQueryApi {
    private final BusinessViewRevisionMapper mapper;
    private final BusinessViewComponentRegistry registry;
    private final BusinessViewAccess access;
    private final PlatformCommandExecutionApi commands;
    private final TransactionTemplate transactions;

    public record Selection(String entityType, String viewKey, String componentKey,
                            String componentVersion, Long dynamicFormRevisionId) { }
    public record Issue(String field, String code, String message) { }
    public record Validation(boolean valid, List<Issue> issues) {
        public Validation { issues = List.copyOf(issues); }
    }
    private record Intent(String action, Long id, Integer expectedVersion, Selection selection) { }

    public List<BusinessViewComponentProvider.Component> components() {
        return access.trusted(() -> {
            Context context = access.context();
            access.require(context, "query");
            return registry.components(context);
        });
    }

    public PageResult<BusinessViewRevision> page(int pageNo, int pageSize, String entityType, ViewSource source) {
        return access.trusted(() -> {
            Context context = access.context();
            access.require(context, "query");
            if (pageNo < 1 || pageSize < 1 || pageSize > 100 || (entityType != null
                    && !entityType.matches("[A-Za-z][A-Za-z0-9_.:-]{0,63}"))) throw exception(INVALID);
            var rows = mapper.selectPage(new BusinessViewPageQuery(context.tenantId(), entityType,
                    source == null ? null : source.name(), registry.readableOwners(context), pageNo, pageSize));
            return new PageResult<>(rows.getList().stream().map(row -> response(context, row, true)).toList(), rows.getTotal());
        });
    }

    public BusinessViewRevision get(Long id) {
        return access.trusted(() -> {
            Context context = access.context();
            access.require(context, "query");
            var row = requireRow(context, id);
            registry.requireOwner(context, row.getOwnerContext(), ConfigurationAction.QUERY);
            return response(context, row, true);
        });
    }

    /** No registry-management authorization here: caller owns template/runtime object authorization. */
    @Override
    public BusinessViewRevision getRevision(Query query) {
        return access.trusted(() -> {
            if (query == null || query.purpose() == null) throw exception(INVALID);
            Context context = access.context();
            var row = requireRow(context, query.revisionId());
            if (row.getPublishedAt() == null || (query.purpose() == Purpose.NEW_REFERENCE
                    && row.getDisabledAt() != null)) throw exception(UNAVAILABLE);
            return response(context, row, false);
        });
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public BusinessViewRevision lockAndRevalidate(Query query) {
        if (query == null) throw exception(INVALID);
        return lockAndRevalidateAll(List.of(query)).getFirst();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<BusinessViewRevision> lockAndRevalidateAll(List<Query> queries) {
        return access.trusted(() -> {
            if (queries == null) throw exception(INVALID);
            Context context = access.context();
            Map<Long, BusinessViewRevisionDO> inspected = new LinkedHashMap<>();
            for (Query query : queries) {
                if (query == null || query.purpose() != Purpose.NEW_REFERENCE) throw exception(INVALID);
                requireVersionArgument(query.expectedVersion());
                inspected.computeIfAbsent(query.revisionId(), id -> requireRow(context, id));
            }
            var identities = inspected.values().stream().map(row -> new BusinessViewIdentityQuery(
                    context.tenantId(), row.getEntityType(), row.getViewKey())).distinct()
                    .sorted(Comparator.comparing(BusinessViewIdentityQuery::entityType).thenComparing(BusinessViewIdentityQuery::viewKey)).toList();
            Map<Long, BusinessViewRevisionDO> locked = new HashMap<>();
            for (var identity : identities) {
                for (var row : mapper.selectIdentityForUpdate(identity)) locked.put(row.getId(), row);
            }
            List<BusinessViewRevisionDO> selected = new ArrayList<>();
            for (Query query : queries) {
                var row = locked.get(query.revisionId());
                if (row == null) throw exception(NOT_FOUND);
                if (!Objects.equals(row.getVersion(), query.expectedVersion())) throw exception(VERSION_CONFLICT);
                if (row.getPublishedAt() == null || row.getDisabledAt() != null) throw exception(UNAVAILABLE);
                selected.add(row);
            }
            registry.lockDependencies(context, selected.stream().map(BusinessViewApplicationService::descriptor).toList());
            for (var row : selected) {
                // Every dependency is already locked, so Owner revalidation adds no new locks.
                if (!validateDependencies(context, row, ValidationMode.LOCK_FOR_PUBLISH).valid()) throw exception(UNAVAILABLE);
            }
            return selected.stream().map(row -> response(context, row, false)).toList();
        });
    }

    public BusinessViewRevision create(String key, Selection selection) {
        return access.trusted(() -> {
            Context context = access.context();
            access.require(context, "manage");
            var descriptor = selected(context, selection, 1L);
            registry.requireConfiguration(context, descriptor.componentKey(), descriptor.componentVersion(), ConfigurationAction.MANAGE);
            return execute(context, key, new Intent("CREATE", null, null, selection), () -> {
                var existing = lockIdentity(context, descriptor.entityType(), descriptor.viewKey());
                if (!existing.isEmpty()) throw exception(IDENTITY_CONFLICT);
                var row = newRow(context, descriptor);
                insert(row);
                return response(context, row, true);
            });
        });
    }

    public BusinessViewRevision update(Long id, Integer version, String key, Selection selection) {
        return access.trusted(() -> {
            Context context = access.context();
            access.require(context, "manage");
            requireVersionArgument(version);
            var inspected = requireRow(context, id);
            registry.requireOwner(context, inspected.getOwnerContext(), ConfigurationAction.MANAGE);
            var replacement = selected(context, selection, inspected.getRevisionNo());
            if (!inspected.getEntityType().equals(replacement.entityType())
                    || !inspected.getViewKey().equals(replacement.viewKey())
                    || !inspected.getOwnerContext().equals(replacement.ownerContext())) throw exception(INVALID);
            registry.requireConfiguration(context, replacement.componentKey(), replacement.componentVersion(), ConfigurationAction.MANAGE);
            return execute(context, key, new Intent("UPDATE", id, version, selection), () -> {
                var row = selectedLocked(context, inspected, version);
                requireDraft(row);
                apply(row, replacement);
                row.setUpdater(context.actorId().toString());
                if (mapper.updateDraftIfMatch(row) != 1) throw exception(VERSION_CONFLICT);
                row.setVersion(Math.incrementExact(row.getVersion()));
                return response(context, row, true);
            });
        });
    }

    public BusinessViewRevision copy(Long id, Integer version, String key) {
        return access.trusted(() -> {
            Context context = access.context();
            access.require(context, "manage");
            requireVersionArgument(version);
            var inspected = requireRow(context, id);
            registry.requireOwner(context, inspected.getOwnerContext(), ConfigurationAction.MANAGE);
            return execute(context, key, new Intent("COPY", id, version, null), () -> {
                var rows = lockIdentity(context, inspected.getEntityType(), inspected.getViewKey());
                var source = findLocked(rows, id, version);
                if (rows.stream().anyMatch(BusinessViewApplicationService::isDraft)) throw exception(DRAFT_EXISTS);
                long next = Math.incrementExact(rows.stream().mapToLong(BusinessViewRevisionDO::getRevisionNo).max().orElseThrow());
                var row = newRow(context, descriptor(source).withRevision(next));
                insert(row);
                return response(context, row, true);
            });
        });
    }

    public Validation validate(Long id) {
        return access.trusted(() -> {
            Context context = access.context();
            access.require(context, "manage");
            var row = requireRow(context, id);
            registry.requireOwner(context, row.getOwnerContext(), ConfigurationAction.MANAGE);
            requireDraft(row);
            return validateDependencies(context, row, ValidationMode.INSPECT);
        });
    }

    public BusinessViewRevision publish(Long id, Integer version, String key) {
        return lifecycle(id, version, key, true);
    }

    public BusinessViewRevision disable(Long id, Integer version, String key) {
        return lifecycle(id, version, key, false);
    }

    private BusinessViewRevision lifecycle(Long id, Integer version, String key, boolean publish) {
        return access.trusted(() -> {
            Context context = access.context();
            access.require(context, publish ? "publish" : "disable");
            requireVersionArgument(version);
            var inspected = requireRow(context, id);
            var configurationAction = publish ? ConfigurationAction.PUBLISH : ConfigurationAction.DISABLE;
            registry.requireOwner(context, inspected.getOwnerContext(), configurationAction);
            return execute(context, key, new Intent(publish ? "PUBLISH" : "DISABLE", id, version, null), () -> {
                var row = selectedLocked(context, inspected, version);
                row.setUpdater(context.actorId().toString());
                if (publish) {
                    requireDraft(row);
                    registry.requireConfiguration(context, row.getComponentKey(), row.getComponentVersion(), configurationAction);
                    registry.lockDependencies(context, List.of(descriptor(row)));
                    if (!validateDependencies(context, row, ValidationMode.LOCK_FOR_PUBLISH).valid()) throw exception(UNAVAILABLE);
                    row.setPublishedAt(LocalDateTime.now());
                    if (mapper.publishIfMatch(row) != 1) throw exception(VERSION_CONFLICT);
                } else {
                    if (row.getPublishedAt() == null || row.getDisabledAt() != null) throw exception(STATE_INVALID);
                    LocalDateTime now = LocalDateTime.now();
                    if (now.isBefore(row.getPublishedAt())) throw exception(STATE_INVALID);
                    row.setDisabledAt(now);
                    if (mapper.disableIfMatch(row) != 1) throw exception(VERSION_CONFLICT);
                }
                row.setVersion(Math.incrementExact(row.getVersion()));
                return response(context, row, true);
            });
        });
    }

    private Validation validateDependencies(Context context, BusinessViewRevisionDO row, ValidationMode mode) {
        try {
            registry.validate(context, descriptor(row), mode);
            return new Validation(true, List.of());
        } catch (ServiceException failure) {
            if (Objects.equals(failure.getCode(), FORBIDDEN.getCode())) throw failure;
            return new Validation(false, List.of(new Issue("componentKey", "DEPENDENCY_UNAVAILABLE", "组件或精确表单修订不可用")));
        } catch (IllegalArgumentException failure) {
            String message = failure.getMessage();
            String field = message != null && message.startsWith("dynamicFormRevisionId") ? "dynamicFormRevisionId" : "componentKey";
            return new Validation(false, List.of(new Issue(field, "CONFIGURATION_INVALID", "配置与当前受控目录或依赖不兼容")));
        }
    }

    private BusinessViewRevision execute(Context context, String key, Intent intent, Supplier<BusinessViewRevision> action) {
        if (key == null || key.isBlank() || key.length() > 128) throw exception(INVALID);
        String digest = digest(intent);
        try {
            return transactions.execute(status -> {
                var result = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(context.tenantId(),
                                "PLT:BUSINESS_VIEW:" + intent.action(), context.actorId(), key), digest,
                        BusinessViewRevision.class, action, value -> new PlatformCommandExecutionApi.SuccessFacts(
                                "BUSINESS_VIEW_" + intent.action(), "BusinessViewRegistration", value.id().toString(), key,
                                JsonUtils.toJsonString(new Audit(intent.action(), intent.id(), intent.expectedVersion(),
                                        value.id(), value.revisionNo(), value.version(), value.status())), null, null));
                if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(KEY_CONFLICT);
                if (result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || result.response() == null) throw exception(IN_PROGRESS);
                return result.response();
            });
        } catch (DuplicateKeyException conflict) {
            throw exception(IDENTITY_CONFLICT);
        }
    }
    private record Audit(String action, Long sourceId, Integer expectedVersion, Long revisionId,
                         Long revisionNo, Integer version, String status) { }

    private BusinessViewDescriptor selected(Context context, Selection selection, long revisionNo) {
        if (selection == null) throw exception(INVALID);
        var component = registry.requireComponent(selection.componentKey(), selection.componentVersion());
        if (!Objects.equals(component.entityType(), selection.entityType())) throw exception(INVALID);
        try {
            return new BusinessViewDescriptor(context.tenantId(), selection.entityType(), component.ownerContext(),
                    selection.viewKey(), revisionNo, BusinessViewDescriptor.ViewSource.valueOf(component.viewSource().name()),
                    component.componentKey(), component.componentVersion(), selection.dynamicFormRevisionId(),
                    component.contextSchema(), component.supportedActions(), component.queryProviderKey(),
                    component.commandProviderKey(), component.permissionProviderKey());
        } catch (IllegalArgumentException invalid) { throw exception(INVALID); }
    }

    private BusinessViewRevisionDO requireRow(Context context, Long id) {
        if (id == null || id <= 0) throw exception(INVALID);
        var row = mapper.selectByRow(new BusinessViewRowQuery(context.tenantId(), id));
        if (row == null || !Objects.equals(row.getTenantId(), context.tenantId())) throw exception(NOT_FOUND);
        return row;
    }
    private List<BusinessViewRevisionDO> lockIdentity(Context context, String entityType, String viewKey) {
        return mapper.selectIdentityForUpdate(new BusinessViewIdentityQuery(context.tenantId(), entityType, viewKey));
    }
    private BusinessViewRevisionDO selectedLocked(Context context, BusinessViewRevisionDO inspected, Integer version) {
        return findLocked(lockIdentity(context, inspected.getEntityType(), inspected.getViewKey()), inspected.getId(), version);
    }
    private BusinessViewRevisionDO findLocked(List<BusinessViewRevisionDO> rows, Long id, Integer version) {
        var row = rows.stream().filter(value -> value.getId().equals(id)).findFirst().orElseThrow(() -> exception(NOT_FOUND));
        if (!Objects.equals(row.getVersion(), version) || version == Integer.MAX_VALUE) throw exception(VERSION_CONFLICT);
        return row;
    }
    private static void requireVersionArgument(Integer version) {
        if (version == null || version < 0) throw exception(INVALID);
    }
    private static boolean isDraft(BusinessViewRevisionDO row) { return row.getPublishedAt() == null && row.getDisabledAt() == null; }
    private static void requireDraft(BusinessViewRevisionDO row) { if (!isDraft(row)) throw exception(STATE_INVALID); }
    private void insert(BusinessViewRevisionDO row) {
        if (mapper.insert(row) != 1) throw exception(IDENTITY_CONFLICT);
    }
    private BusinessViewRevisionDO newRow(Context context, BusinessViewDescriptor descriptor) {
        var row = new BusinessViewRevisionDO();
        row.setId(IdWorker.getId());
        row.setTenantId(context.tenantId());
        row.setVersion(0);
        row.setCreator(context.actorId().toString());
        row.setUpdater(context.actorId().toString());
        row.setDeleted(false);
        apply(row, descriptor);
        return row;
    }
    private static void apply(BusinessViewRevisionDO row, BusinessViewDescriptor descriptor) {
        row.setEntityType(descriptor.entityType()); row.setViewKey(descriptor.viewKey()); row.setRevisionNo(descriptor.revisionNo());
        row.setOwnerContext(descriptor.ownerContext()); row.setViewSource(descriptor.viewSource().name());
        row.setComponentKey(descriptor.componentKey()); row.setComponentVersion(descriptor.componentVersion());
        row.setDynamicFormRevisionId(descriptor.dynamicFormRevisionId());
        row.setContextSchema(JsonUtils.toJsonString(descriptor.contextSchema()));
        row.setSupportedActions(JsonUtils.toJsonString(descriptor.supportedActions()));
        row.setQueryProviderKey(descriptor.queryProviderKey()); row.setCommandProviderKey(descriptor.commandProviderKey());
        row.setPermissionProviderKey(descriptor.permissionProviderKey());
    }
    private static BusinessViewDescriptor descriptor(BusinessViewRevisionDO row) {
        return new BusinessViewDescriptor(row.getTenantId(), row.getEntityType(), row.getOwnerContext(), row.getViewKey(),
                row.getRevisionNo(), BusinessViewDescriptor.ViewSource.valueOf(row.getViewSource()), row.getComponentKey(),
                row.getComponentVersion(), row.getDynamicFormRevisionId(), JsonUtils.parseTree(row.getContextSchema()),
                JsonUtils.parseTree(row.getSupportedActions()), row.getQueryProviderKey(), row.getCommandProviderKey(), row.getPermissionProviderKey());
    }
    private BusinessViewRevision response(Context context, BusinessViewRevisionDO row, boolean management) {
        Set<String> actions = new LinkedHashSet<>();
        if (management) {
            if (access.has(context, "query") && registry.canConfigureOwner(context, row.getOwnerContext(), ConfigurationAction.QUERY)) actions.add("QUERY");
            if (access.has(context, "manage") && registry.canConfigureOwner(context, row.getOwnerContext(), ConfigurationAction.MANAGE)) {
                if (isDraft(row)) { actions.add("UPDATE"); actions.add("VALIDATE"); }
                else actions.add("COPY");
            }
            if (isDraft(row) && access.has(context, "publish") && registry.canConfigureOwner(context, row.getOwnerContext(), ConfigurationAction.PUBLISH)) actions.add("PUBLISH");
            if (row.getPublishedAt() != null && row.getDisabledAt() == null && access.has(context, "disable")
                    && registry.canConfigureOwner(context, row.getOwnerContext(), ConfigurationAction.DISABLE)) actions.add("DISABLE");
        }
        return new BusinessViewRevision(row.getId(), row.getEntityType(), row.getViewKey(), row.getRevisionNo(),
                row.getOwnerContext(), ViewSource.valueOf(row.getViewSource()), row.getComponentKey(), row.getComponentVersion(),
                row.getDynamicFormRevisionId(), JsonUtils.parseTree(row.getContextSchema()), JsonUtils.parseTree(row.getSupportedActions()),
                row.getQueryProviderKey(), row.getCommandProviderKey(), row.getPermissionProviderKey(), row.getPublishedAt(),
                row.getDisabledAt(), row.getVersion(), row.getPublishedAt() == null ? "DRAFT" : row.getDisabledAt() == null ? "PUBLISHED" : "DISABLED", actions);
    }
    private static String digest(Intent intent) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(intent).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
