package cn.iocoder.yudao.module.pms.customer.service.contact;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ContactHistoryDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.CustomerContactMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.ContactHistoryMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.CustomerContactMasterMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query.*;
import cn.iocoder.yudao.module.pms.customer.service.query.CustomerQueryService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeContextService;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.*;

/** Independent customer-contact operations; project records will consume source identities separately. */
@Service
@RequiredArgsConstructor
public class CustomerContactMasterService {
    private final CustomerContactMasterMapper contacts;
    private final ContactHistoryMapper history;
    private final CustomerQueryService customers;
    private final CustomerScopeContextService scopes;
    private final PlatformCommandExecutionApi commands;

    public record Actor(Long tenantId, Long userId) {}

    public PageResult<CustomerContactMasterDO> page(Actor actor, Long customerId, String name, Integer status, PageParam page) {
        requireCustomer(actor, customerId, false);
        return contacts.selectPage(new ContactMasterPageQuery(actor.tenantId(), customerId, name, status, page));
    }

    public CustomerContactMasterDO get(Actor actor, Long customerId, Long contactId) {
        requireCustomer(actor, customerId, false);
        return requireRow(actor, customerId, contactId);
    }

    @Transactional(rollbackFor = Exception.class)
    public CustomerContactMasterDO create(Actor actor, ContactMasterWrite command, String idempotencyKey) {
        requireCustomer(actor, command.customerId(), true);
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw exception(CONTACT_VALUES_INVALID, "缺少有效的新增操作标识");
        }
        var execution = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                actor.tenantId(), "POST:/api/v1/pms/customer-contacts", actor.userId(), idempotencyKey),
                DigestUtil.sha256Hex(JsonUtils.toJsonString(command)), CustomerContactMasterDO.class,
                () -> createOnce(actor, command),
                row -> new PlatformCommandExecutionApi.SuccessFacts("CUSTOMER_CONTACT_CREATE", "CustomerContact",
                        String.valueOf(row.getId()), idempotencyKey,
                        JsonUtils.toJsonString(java.util.Map.of("customerId", row.getCustomerId(), "contactId", row.getId())),
                        null, null));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        return execution.response();
    }

    private CustomerContactMasterDO createOnce(Actor actor, ContactMasterWrite command) {
        ContactValues values = validated(command);
        requirePrimary(actor, command, null);
        CustomerContactMasterDO row = new CustomerContactMasterDO();
        apply(row, values, command);
        row.setTenantId(actor.tenantId());
        row.setVersion(0);
        contacts.insert(row);
        appendHistory(actor, row.getId(), "CREATE", null, row);
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public CustomerContactMasterDO update(Actor actor, ContactMasterWrite command) {
        requireCustomer(actor, command.customerId(), true, command.status() == 0 || command.primary());
        CustomerContactMasterDO existing = requireRow(actor, command.customerId(), command.contactId());
        requireVersion(existing, command.expectedVersion());
        ContactValues values = validated(command);
        requirePrimary(actor, command, existing.getId());
        CustomerContactMasterDO update = BeanUtils.toBean(existing, CustomerContactMasterDO.class);
        apply(update, values, command);
        if (contacts.updateById(update) != 1) throw exception(CONTACT_VERSION_CONFLICT);
        appendHistory(actor, existing.getId(), "UPDATE", existing, update);
        return update;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Actor actor, Long customerId, Long contactId, Integer expectedVersion) {
        requireCustomer(actor, customerId, true, false);
        CustomerContactMasterDO existing = requireRow(actor, customerId, contactId);
        requireVersion(existing, expectedVersion);
        if (contacts.deleteUnreferenced(new ContactMasterDeleteCommand(actor.tenantId(), customerId,
                contactId, expectedVersion, String.valueOf(actor.userId()))) != 1) throw exception(CONTACT_DELETE_REFERENCED);
        appendHistory(actor, contactId, "DELETE", existing, null);
    }

    private void requireCustomer(Actor actor, Long customerId, boolean writable) {
        requireCustomer(actor, customerId, writable, writable);
    }

    private void requireCustomer(Actor actor, Long customerId, boolean writable, boolean requireEnabled) {
        if (actor == null || actor.tenantId() == null || actor.userId() == null || customerId == null) throw exception(CUSTOMER_SCOPE_DENIED);
        var customer = customers.get(actor.tenantId(), customerId, scopes.resolve(actor.tenantId(), actor.userId()));
        if (customer == null || requireEnabled && !"ENABLED".equals(customer.getLifecycleStatus())) throw exception(CUSTOMER_SCOPE_DENIED);
        if (writable) {
            var locked = contacts.selectCustomerForUpdate(new ContactCustomerReferenceQuery(actor.tenantId(), customerId));
            if (locked == null || requireEnabled && !"ENABLED".equals(locked.getLifecycleStatus())) throw exception(CUSTOMER_SCOPE_DENIED);
        }
    }

    private CustomerContactMasterDO requireRow(Actor actor, Long customerId, Long contactId) {
        if (contactId == null) throw exception(CONTACT_NOT_EXISTS);
        var row = contacts.selectByRow(new ContactMasterRowQuery(actor.tenantId(), customerId, contactId));
        if (row == null) throw exception(CONTACT_NOT_EXISTS);
        return row;
    }

    private void requireVersion(CustomerContactMasterDO row, Integer expected) {
        if (expected == null || !Objects.equals(row.getVersion(), expected)) throw exception(CONTACT_VERSION_CONFLICT);
    }

    private ContactValues validated(ContactMasterWrite command) {
        try { return ContactRules.normalize(command.values(), command.status()); }
        catch (IllegalArgumentException invalid) { throw exception(CONTACT_VALUES_INVALID, invalid.getMessage()); }
    }

    private void requirePrimary(Actor actor, ContactMasterWrite command, Long targetId) {
        if (!command.primary()) return;
        var current = contacts.selectActivePrimaryForUpdate(new ContactCustomerReferenceQuery(actor.tenantId(), command.customerId()));
        if (current != null && !Objects.equals(current.getId(), targetId)) throw exception(CONTACT_VALUES_INVALID, "客户已存在启用主联系人，请先取消原主联系人主标识");
        try { ContactRules.requirePrimaryAllowed(command.status(), true, current == null ? null : current.getId(), targetId); }
        catch (IllegalArgumentException invalid) { throw exception(CONTACT_VALUES_INVALID, invalid.getMessage()); }
    }

    private void apply(CustomerContactMasterDO row, ContactValues values, ContactMasterWrite command) {
        row.setCustomerId(command.customerId()); row.setName(values.name()); row.setDepartment(values.department());
        row.setTitle(values.title()); row.setMobile(values.mobile()); row.setPhone(values.phone()); row.setEmail(values.email());
        row.setPrimaryFlag(command.primary()); row.setStatus(command.status()); row.setRemark(values.remark());
    }

    private void appendHistory(Actor actor, Long contactId, String action, Object before, Object after) {
        ContactHistoryDO event = new ContactHistoryDO();
        event.setTenantId(actor.tenantId()); event.setCustomerContactId(contactId); event.setActorUserId(actor.userId());
        event.setActionCode(action); event.setBeforeValues(before == null ? null : JsonUtils.toJsonString(before));
        event.setAfterValues(after == null ? null : JsonUtils.toJsonString(after)); event.setOccurredAt(LocalDateTime.now());
        history.insert(event);
    }
}
