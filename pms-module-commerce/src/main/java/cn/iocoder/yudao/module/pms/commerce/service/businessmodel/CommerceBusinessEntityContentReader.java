package cn.iocoder.yudao.module.pms.commerce.service.businessmodel;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractAccessService;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.Completeness;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.model.BusinessModelIntrospector;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;

/** Both commerce roots reuse native scope resolution and existing REST projections.
 * Native pages keep company-code binary matching, project relationships and current Owner checks.
 * No generic SQL, positive authorization cache or business write path is introduced. */
@Component
@RequiredArgsConstructor
public class CommerceBusinessEntityContentReader implements BusinessEntityContentReader {
    private static final int NATIVE_PAGE_SIZE = 200;
    // ContractRespVO and independent SalesOrderRespVO fields, intersected with the public model catalog.
    private static final Set<String> CONTRACT_FIELDS = Set.of("companyCode", "companyName", "contractNo",
            "contractType", "customerCode", "customerName", "contractName", "contractAmount", "currencyCode", "status");
    private static final Set<String> ORDER_FIELDS = Set.of("sourceSystem", "sourceVersion", "companyCode",
            "companyName", "orderType", "orderNo", "customerCode", "customerName", "status");
    private static final Set<String> CONTRACT_SENSITIVE = Set.of("contractType", "customerCode", "customerName",
            "contractAmount", "currencyCode");
    private final ContractAccessService nativeAccess;
    private final BusinessModelCatalog catalog;
    private final BusinessAccessGuard guard;
    private final PermissionApi permissions;

    @Override public boolean supports(String owner, String type) {
        return "COM".equals(owner) && Set.of("contract", "salesOrder").contains(type);
    }
    @Override public boolean supportsQueries() { return true; }

    @Override public BusinessEntityData read(EntityDataRef target, EntityActor actor) {
        actor.requireTenant(target.entity());
        String type = target.entity().entityType();
        requireReadable(type, actor);
        if (target.isRevision()) throw failure("REVISION_UNSUPPORTED", "Commerce root has no native revision reader");
        BaseBusinessEntity row;
        try {
            row = "contract".equals(type)
                    ? nativeAccess.getContractRoot(actor.tenantId(), actor.userId(), actor.correlationId(), target.entity().entityId())
                    : nativeAccess.getSalesOrderRoot(actor.tenantId(), actor.userId(), actor.correlationId(), target.entity().entityId());
        } catch (ServiceException denied) {
            if (FORBIDDEN.getCode().equals(denied.getCode())) throw failure("ENTITY_SCOPE_DENIED", "Native commerce root is not visible");
            throw denied;
        }
        return project(type, row, actor, sensitive(actor));
    }

    @Override public BusinessEntitySlice query(BusinessEntityPageQuery query, EntityActor actor) {
        requireReadable(query.entityType(), actor);
        if (query.pageSize() < 1 || query.pageSize() > 200) throw failure("PAGE_SIZE_INVALID", "Page size must be between 1 and 200");
        var cursor = cursor(query.entityType(), query.cursor());
        boolean sensitive = sensitive(actor);
        var filters = query.filters() == null ? List.<BusinessFieldFilter>of() : query.filters();
        for (var filter : filters) {
            if (!fields(query.entityType()).contains(filter.fieldCode())
                    || catalog.require("COM", query.entityType()).fields().stream().noneMatch(field -> field.readable() && field.code().equals(filter.fieldCode()))
                    || "contract".equals(query.entityType()) && !sensitive && CONTRACT_SENSITIVE.contains(filter.fieldCode()))
                throw failure("FIELD_NOT_OPEN", "Field is not available in native projection: " + filter.fieldCode());
            if (!Set.of(BusinessFieldFilter.Operator.EQ, BusinessFieldFilter.Operator.NE, BusinessFieldFilter.Operator.IN,
                    BusinessFieldFilter.Operator.LIKE, BusinessFieldFilter.Operator.IS_NULL, BusinessFieldFilter.Operator.NOT_NULL).contains(filter.operator()))
                throw failure("FILTER_OPERATOR_UNSUPPORTED", "Native projection does not support this comparison");
        }
        var members = new ArrayList<BusinessEntityData>();
        String resume = query.cursor();
        // One bounded native query per request. Sparse filters can return an empty PARTIAL slice;
        // its cursor advances through authorized roots, without walking the entire directory.
        var rows = nativePage(query.entityType(), actor, cursor);
        for (int index = 0; index < Math.min(rows.size(), NATIVE_PAGE_SIZE); index++) {
            var row = rows.get(index);
            var data = project(query.entityType(), row, actor, sensitive);
            if (filters.stream().allMatch(filter -> matches(data.fieldValues().get(filter.fieldCode()), filter))) {
                if (members.size() == query.pageSize())
                    return new BusinessEntitySlice(members, resume, Completeness.PARTIAL, null);
                members.add(data);
            }
            resume = cursor(query.entityType(), row);
        }
        return rows.size() > NATIVE_PAGE_SIZE
                ? new BusinessEntitySlice(members, resume, Completeness.PARTIAL, null)
                : new BusinessEntitySlice(members, null, Completeness.COMPLETE, null);
    }

    private List<? extends BaseBusinessEntity> nativePage(String type, EntityActor actor, ContractAccessService.RootReadCursor cursor) {
        return "contract".equals(type)
                ? nativeAccess.readContractRoots(actor.tenantId(), actor.userId(), actor.correlationId(), cursor, NATIVE_PAGE_SIZE + 1)
                : nativeAccess.readSalesOrderRoots(actor.tenantId(), actor.userId(), actor.correlationId(), cursor, NATIVE_PAGE_SIZE + 1);
    }
    private void requireReadable(String type, EntityActor actor) {
        if (!supports("COM", type)) throw failure("CONTENT_READER_UNSUPPORTED", "Unsupported commerce root");
        guard.requireReadable(catalog.require("COM", type), actor, "native-content");
    }
    private boolean sensitive(EntityActor actor) {
        return permissions.hasAnyPermissions(actor.userId(), "pms:commerce:contract:sensitive-read");
    }
    private Set<String> fields(String type) { return "contract".equals(type) ? CONTRACT_FIELDS : ORDER_FIELDS; }
    private BusinessEntityData project(String type, BaseBusinessEntity row, EntityActor actor, boolean sensitive) {
        if (!actor.tenantId().equals(row.getTenantId())) throw failure("ENTITY_SCOPE_DENIED", "Native row tenant mismatch");
        var descriptor = catalog.require("COM", type);
        var values = new LinkedHashMap<>(BusinessModelIntrospector.readValues(row,
                BusinessModelIntrospector.businessFields(row.getClass()).stream()
                        .filter(field -> fields(type).contains(field.code()) && descriptor.fields().stream().anyMatch(open -> open.readable() && open.code().equals(field.code()))).toList()));
        if ("contract".equals(type) && !sensitive)
            CONTRACT_SENSITIVE.forEach(code -> { if (values.containsKey(code)) values.put(code, null); });
        return new BusinessEntityData(new EntityRef(actor.tenantId(), "COM", type, row.getId()), null,
                Collections.unmodifiableMap(values), row.getVersion(), true, null);
    }
    private boolean matches(Object actual, BusinessFieldFilter filter) {
        var values = filter.values() == null ? List.of() : filter.values();
        Object expected = values.isEmpty() ? null : values.getFirst();
        return switch (filter.operator()) {
            case EQ -> equal(actual, expected);
            case NE -> actual != null && expected != null && !equal(actual, expected);
            case IN -> actual != null && values.stream().anyMatch(value -> equal(actual, value));
            case LIKE -> actual instanceof String text && expected instanceof String part && text.contains(part);
            case IS_NULL -> actual == null;
            case NOT_NULL -> actual != null;
            default -> throw failure("FILTER_OPERATOR_UNSUPPORTED", "Unsupported native projection comparison");
        };
    }
    private boolean equal(Object actual, Object expected) {
        if (actual instanceof Number left && expected instanceof Number right)
            return new java.math.BigDecimal(left.toString()).compareTo(new java.math.BigDecimal(right.toString())) == 0;
        return Objects.equals(actual, expected);
    }
    private ContractAccessService.RootReadCursor cursor(String type, String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            var parts = cursor.split(":", -1);
            if (cursor.length() > 1024 || parts.length != 4 || !"native".equals(parts[0]) || !type.equals(parts[1]))
                throw new IllegalArgumentException();
            long id = Long.parseLong(parts[2]);
            if (id <= 0) throw new IllegalArgumentException();
            return new ContractAccessService.RootReadCursor(new String(Base64.getUrlDecoder().decode(parts[3]),
                    java.nio.charset.StandardCharsets.UTF_8), id);
        } catch (IllegalArgumentException invalid) { throw failure("CURSOR_INVALID", "Invalid native page cursor"); }
    }
    private String cursor(String type, BaseBusinessEntity row) {
        String code = "contract".equals(type) ? ((ContractDO) row).getContractNo() : ((SalesOrderDO) row).getOrderNo();
        return "native:" + type + ":" + row.getId() + ":" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static BusinessContractException failure(String code, String message) { return new BusinessContractException(code, message); }
}
