package cn.iocoder.yudao.module.pms.customer.service.contact;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.*;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.*;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query.*;
import cn.iocoder.yudao.module.pms.project.api.contact.ProjectContactContextApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Map;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.*;

/** Project-local contact facts owned by CUS; PROJ only supplies scope/customer identity. */
@Service
@RequiredArgsConstructor
public class ProjectCustomerContactService {
    private final ProjectContactContextApi projects;
    private final CustomerContactMasterMapper sources;
    private final ProjectCustomerContactMapper contacts;
    private final ContactHistoryMapper history;
    private final PlatformCommandExecutionApi commands;
    private final cn.iocoder.yudao.module.pms.customer.service.query.CustomerQueryService customerQuery;
    private final cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeContextService customerScopes;
    private final ContactDictionaryPolicy dictionaryPolicy;

    @Transactional(rollbackFor = Exception.class)
    public ProjectContactContextApi.Context associateCustomer(CustomerContactMasterService.Actor actor, Long projectId, Integer version, Long customerId) {
        var customer = customerQuery.get(actor.tenantId(), customerId, customerScopes.resolve(actor.tenantId(), actor.userId()));
        if (customer == null || !"ENABLED".equals(customer.getLifecycleStatus())) throw exception(CUSTOMER_SCOPE_DENIED);
        return projects.associateCustomer(new ProjectContactContextApi.AssociateQuery(actor.tenantId(), actor.userId(), projectId, version, customerId));
    }

    public ProjectContactContextApi.Context context(CustomerContactMasterService.Actor actor, Long projectId) {
        return projects.inspect(new ProjectContactContextApi.Query(actor.tenantId(), actor.userId(), projectId));
    }

    public PageResult<ContactHistoryDO> history(CustomerContactMasterService.Actor actor, Long projectId, PageParam page) {
        var context = context(actor, projectId);
        if (!context.canViewHistory()) throw exception(CUSTOMER_SCOPE_DENIED);
        var result = history.selectProjectPage(new ProjectContactHistoryQuery(actor.tenantId(), projectId, page));
        var ids = result.getList().stream().filter(entry -> "DELETE".equals(entry.getActionCode()))
                .map(ContactHistoryDO::getProjectRelationId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        var states = ids.isEmpty() ? Map.<Long, ProjectContactHistoryState>of() : contacts.selectHistoryStates(
                new ProjectContactHistoryStateQuery(actor.tenantId(), projectId, context.customerId(), ids)).stream()
                .collect(java.util.stream.Collectors.toMap(ProjectContactHistoryState::getId, state -> state));
        result.getList().forEach(entry -> {
            entry.setRestorable(false);
            var state = states.get(entry.getProjectRelationId());
            if (!context.canManage() || state == null || !Boolean.TRUE.equals(state.getRestorable()) || !"DELETE".equals(entry.getActionCode()) || entry.getBeforeValues() == null) return;
            var before = JsonUtils.parseObject(entry.getBeforeValues(), ProjectCustomerContactDO.class);
            entry.setRestorable(before.getVersion() != null && Objects.equals(state.getVersion(), before.getVersion()+1));
        });
        return result;
    }

    public PageResult<ProjectCustomerContactDO> page(CustomerContactMasterService.Actor actor, Long projectId, Integer status, String name, PageParam page) {
        var context = context(actor, projectId);
        return contacts.selectPage(new ProjectContactPageQuery(actor.tenantId(), projectId, context.canViewHistory() ? status : Integer.valueOf(0), name, page));
    }

    public PageResult<CustomerContactMasterDO> sources(CustomerContactMasterService.Actor actor, Long projectId, String name, PageParam page) {
        var context = context(actor, projectId);
        if (!context.canManage()) throw exception(CUSTOMER_SCOPE_DENIED);
        if (context.customerId() == null) return PageResult.empty();
        var query = new AvailableContactSourceQuery(actor.tenantId(), projectId, context.customerId(), name, page);
        long total = sources.selectAvailableSourceCount(query);
        return total == 0 ? PageResult.empty() : new PageResult<>(sources.selectAvailableSourcePage(query), total);
    }

    @Transactional(rollbackFor = Exception.class)
    public int importDefaults(CustomerContactMasterService.Actor actor, Long projectId, Integer projectVersion) {
        var context = lock(actor, projectId, projectVersion);
        int created = 0;
        PageParam page = new PageParam(); page.setPageSize(100); page.setPageNo(1);
        while (true) {
            var sourcePage = sources.selectPage(new ContactMasterPageQuery(actor.tenantId(), context.customerId(), null, 0, page));
            for (var source : sourcePage.getList()) {
                if (contacts.selectSourceIncludingDeletedForUpdate(new ProjectContactSourceQuery(actor.tenantId(), projectId, source.getId())) != null) continue;
                insertRelation(actor, projectId, source, values(source), false, 0);
                created++;
            }
            if ((long) page.getPageNo() * page.getPageSize() >= sourcePage.getTotal()) break;
            page.setPageNo(page.getPageNo() + 1);
        }
        return created;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectCustomerContactDO create(CustomerContactMasterService.Actor actor, ProjectContactWrite command, String key) {
        var context = lock(actor, command.projectId(), command.expectedProjectVersion());
        if (key == null || key.isBlank() || key.length() > 128) throw exception(CONTACT_VALUES_INVALID, "缺少有效的新增操作标识");
        var execution = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(actor.tenantId(),
                "POST:PROJECT_CONTACT:" + command.projectId(), actor.userId(), key),
                DigestUtil.sha256Hex(JsonUtils.toJsonString(command)), ProjectCustomerContactDO.class,
                () -> createOnce(actor, context, command),
                row -> new PlatformCommandExecutionApi.SuccessFacts("PROJECT_CONTACT_CREATE", "ProjectCustomerContact",
                        String.valueOf(row.getId()), key, JsonUtils.toJsonString(Map.of("projectId", row.getProjectId(), "contactId", row.getId())), null, null));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        return execution.response();
    }

    private ProjectCustomerContactDO createOnce(CustomerContactMasterService.Actor actor, ProjectContactContextApi.Context context, ProjectContactWrite command) {
        requirePrimary(actor, command.projectId(), null, command.primary(), command.status());
        CustomerContactMasterDO source;
        ContactValues values;
        if (command.sourceContactId() != null) {
            source = requireSource(actor, context.customerId(), command.sourceContactId());
            if (contacts.selectSourceIncludingDeletedForUpdate(new ProjectContactSourceQuery(actor.tenantId(), command.projectId(), source.getId())) != null) {
                throw exception(CONTACT_VALUES_INVALID, "该来源已被项目引用，含停用或已删除记录；请维护原记录");
            }
            values = command.values() == null ? values(source) : normalize(command.values(), command.status());
            dictionaryPolicy.validateChanges(values, values(source));
        } else {
            values = normalize(command.values(), command.status());
            dictionaryPolicy.validateChanges(values, null);
            source = new CustomerContactMasterDO();
            source.setCustomerId(context.customerId()); source.setTenantId(actor.tenantId()); source.setVersion(0);
            source.setName(values.name()); source.setDepartment(values.department()); source.setTitle(values.title());
            source.setMobile(values.mobile()); source.setPhone(values.phone()); source.setEmail(values.email());
            // Project primary/role/status are not customer-master primary/role/status.
            source.setPrimaryFlag(false); source.setStatus(0); source.setRemark(null);
            normalize(values, 0);
            sources.insert(source);
            appendHistory(actor, source.getId(), null, null, "CREATE", null, source);
        }
        return insertRelation(actor, command.projectId(), source, values, command.primary(), command.status());
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectCustomerContactDO update(CustomerContactMasterService.Actor actor, ProjectContactWrite command) {
        var context = lock(actor, command.projectId(), command.expectedProjectVersion(), command.status() == 0 || command.primary());
        var existing = requireRow(actor, command.projectId(), command.contactId(), command.expectedVersion());
        if (!Objects.equals(existing.getCustomerId(), context.customerId())) throw exception(CONTACT_VALUES_INVALID, "联系人不属于项目当前客户");
        ContactValues values = normalize(command.values(), command.status());
        dictionaryPolicy.validateChanges(values, new ContactValues(existing.getName(), existing.getDepartment(), existing.getTitle(),
                existing.getMobile(), existing.getPhone(), existing.getEmail(), existing.getRoleCode(), existing.getRemark()));
        if (command.status() == 0 || command.primary()) requireSource(actor, context.customerId(), existing.getCustomerContactId());
        requirePrimary(actor, command.projectId(), existing.getId(), command.primary(), command.status());
        if (Boolean.TRUE.equals(existing.getPrimaryFlag()) && command.status() != 0) {
            try { ContactRules.requireRemovalAllowed(true, command.confirmNoPrimary()); }
            catch (IllegalArgumentException invalid) { throw exception(CONTACT_VALUES_INVALID, invalid.getMessage()); }
        }
        var update = BeanUtils.toBean(existing, ProjectCustomerContactDO.class);
        apply(update, values); update.setStatus(command.status()); update.setPrimaryFlag(command.primary());
        if (command.primary() && !Boolean.TRUE.equals(existing.getPrimaryFlag())) update.setPrimarySetTime(LocalDateTime.now());
        if (!command.primary()) update.setPrimarySetTime(null);
        if (contacts.updateById(update) != 1) throw exception(CONTACT_VERSION_CONFLICT);
        appendHistory(actor, existing.getCustomerContactId(), command.projectId(), existing.getId(), "UPDATE", existing, update);
        return update;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(CustomerContactMasterService.Actor actor, ProjectContactWrite command) {
        lock(actor, command.projectId(), command.expectedProjectVersion(), false);
        var existing = requireRow(actor, command.projectId(), command.contactId(), command.expectedVersion());
        try { ContactRules.requireRemovalAllowed(Boolean.TRUE.equals(existing.getPrimaryFlag()), command.confirmNoPrimary()); }
        catch (IllegalArgumentException invalid) { throw exception(CONTACT_VALUES_INVALID, invalid.getMessage()); }
        if (contacts.deleteByVersion(new ProjectContactDeleteCommand(actor.tenantId(), command.projectId(), existing.getId(), command.expectedVersion(), actor.userId())) != 1) throw exception(CONTACT_VERSION_CONFLICT);
        appendHistory(actor, existing.getCustomerContactId(), command.projectId(), existing.getId(), "DELETE", existing, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void restore(CustomerContactMasterService.Actor actor, Long projectId, Long id, Integer projectVersion, Integer expectedVersion, int status) {
        var context = lock(actor, projectId, projectVersion, status == 0);
        var existing = contacts.selectIncludingDeletedForUpdate(new ProjectContactRowQuery(actor.tenantId(), projectId, id));
        if (existing == null || !Boolean.TRUE.equals(existing.getDeleted()) || !Objects.equals(existing.getVersion(), expectedVersion)) throw exception(CONTACT_VERSION_CONFLICT);
        if (!Objects.equals(existing.getCustomerId(), context.customerId())) throw exception(CONTACT_VALUES_INVALID, "联系人不属于项目当前客户");
        if (status == 0) requireSource(actor, context.customerId(), existing.getCustomerContactId());
        normalize(new ContactValues(existing.getName(), existing.getDepartment(), existing.getTitle(), existing.getMobile(),
                existing.getPhone(), existing.getEmail(), existing.getRoleCode(), existing.getRemark()), status);
        if (contacts.restoreByVersion(new ProjectContactRestoreCommand(actor.tenantId(), projectId, id, expectedVersion, actor.userId(), status)) != 1) throw exception(CONTACT_VERSION_CONFLICT);
        var restored = BeanUtils.toBean(existing, ProjectCustomerContactDO.class);
        restored.setDeleted(false); restored.setStatus(status); restored.setPrimaryFlag(false); restored.setPrimarySetTime(null);
        restored.setDeletedAt(null); restored.setDeletedBy(null); restored.setVersion(expectedVersion+1);
        appendHistory(actor, existing.getCustomerContactId(), projectId, id, "RESTORE", existing, restored);
    }

    private ProjectContactContextApi.Context lock(CustomerContactMasterService.Actor actor, Long projectId, Integer version) {
        return lock(actor, projectId, version, true);
    }

    private ProjectContactContextApi.Context lock(CustomerContactMasterService.Actor actor, Long projectId, Integer version, boolean requireEnabledCustomer) {
        var context = projects.lockForWrite(new ProjectContactContextApi.WriteQuery(actor.tenantId(), actor.userId(), projectId, version));
        if (context.customerId() == null) throw exception(CONTACT_VALUES_INVALID, "项目尚未关联客户，不能建立联系人");
        var customer = sources.selectCustomerForUpdate(new ContactCustomerReferenceQuery(actor.tenantId(), context.customerId()));
        if (customer == null || requireEnabledCustomer && !"ENABLED".equals(customer.getLifecycleStatus())) throw exception(CONTACT_VALUES_INVALID, "项目客户不可用");
        return context;
    }

    private CustomerContactMasterDO requireSource(CustomerContactMasterService.Actor actor, Long customerId, Long sourceId) {
        var source = sources.selectForUpdate(new ContactMasterRowQuery(actor.tenantId(), customerId, sourceId));
        if (source == null || !Integer.valueOf(0).equals(source.getStatus())) throw exception(CONTACT_NOT_EXISTS);
        return source;
    }

    private ProjectCustomerContactDO requireRow(CustomerContactMasterService.Actor actor, Long projectId, Long id, Integer version) {
        var row = contacts.selectForUpdate(new ProjectContactRowQuery(actor.tenantId(), projectId, id));
        if (row == null) throw exception(CONTACT_NOT_EXISTS);
        if (version == null || !Objects.equals(row.getVersion(), version)) throw exception(CONTACT_VERSION_CONFLICT);
        return row;
    }

    private void requirePrimary(CustomerContactMasterService.Actor actor, Long projectId, Long id, boolean primary, int status) {
        if (!primary) return;
        var current = contacts.selectPrimaryForUpdate(new ProjectContactRowQuery(actor.tenantId(), projectId, null));
        try { ContactRules.requirePrimaryAllowed(status, primary, current == null ? null : current.getId(), id); }
        catch (IllegalArgumentException invalid) { throw exception(CONTACT_VALUES_INVALID, invalid.getMessage()); }
    }

    private ProjectCustomerContactDO insertRelation(CustomerContactMasterService.Actor actor, Long projectId,
            CustomerContactMasterDO source, ContactValues values, boolean primary, int status) {
        var row = new ProjectCustomerContactDO();
        apply(row, normalize(values, status));
        row.setTenantId(actor.tenantId()); row.setProjectId(projectId); row.setCustomerId(source.getCustomerId()); row.setCustomerContactId(source.getId());
        row.setPrimaryFlag(primary); row.setStatus(status); row.setVersion(0);
        if (primary) row.setPrimarySetTime(LocalDateTime.now());
        contacts.insert(row);
        appendHistory(actor, source.getId(), projectId, row.getId(), "REFERENCE", null, row);
        return row;
    }

    private ContactValues normalize(ContactValues values, int status) {
        try { return ContactRules.normalize(values, status); }
        catch (IllegalArgumentException invalid) { throw exception(CONTACT_VALUES_INVALID, invalid.getMessage()); }
    }
    private ContactValues values(CustomerContactMasterDO source) {
        return new ContactValues(source.getName(), source.getDepartment(), source.getTitle(), source.getMobile(), source.getPhone(), source.getEmail(), null, null);
    }
    private void apply(ProjectCustomerContactDO row, ContactValues values) {
        row.setName(values.name()); row.setDepartment(values.department()); row.setTitle(values.title()); row.setMobile(values.mobile());
        row.setPhone(values.phone()); row.setEmail(values.email()); row.setRoleCode(values.roleCode()); row.setRemark(values.remark());
    }
    private void appendHistory(CustomerContactMasterService.Actor actor, Long sourceId, Long projectId, Long relationId, String action, Object before, Object after) {
        ContactHistoryDO entry = new ContactHistoryDO(); entry.setTenantId(actor.tenantId()); entry.setActorUserId(actor.userId());
        entry.setCustomerContactId(sourceId); entry.setProjectId(projectId); entry.setProjectRelationId(relationId); entry.setActionCode(action);
        entry.setBeforeValues(before == null ? null : JsonUtils.toJsonString(before)); entry.setAfterValues(after == null ? null : JsonUtils.toJsonString(after));
        entry.setOccurredAt(LocalDateTime.now()); history.insert(entry);
    }
}
