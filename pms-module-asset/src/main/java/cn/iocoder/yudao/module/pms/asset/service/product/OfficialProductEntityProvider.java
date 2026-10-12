package cn.iocoder.yudao.module.pms.asset.service.product;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.AssetProductOfficialMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.query.ProductCatalogIdentityQuery;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registers extension properties without creating a fictitious product revision capability. */
@Component
@RequiredArgsConstructor
public class OfficialProductEntityProvider implements EntityFieldProvider {
    private final AssetProductOfficialMapper products;
    private final PermissionApi permissions;
    @Override public String ownerModule() { return "AST"; }
    @Override public String entityType() { return "ProductOfficial"; }
    @Override public List<EntityField> fields() {
        return List.of(new EntityField("productCode", EntityField.Type.TEXT, true),
                new EntityField("productName", EntityField.Type.TEXT, false),
                new EntityField("productModel", EntityField.Type.TEXT, false),
                new EntityField("productDesc", EntityField.Type.TEXT, false));
    }
    @Override public boolean usesValidatedExtensionPatch() { return true; }
    @Override public Map<String, EntityFieldValue> read(EntityDataRef target, EntityActor actor) {
        var row = authorized(target, actor, false);
        var values = new LinkedHashMap<String, EntityFieldValue>();
        values.put("productCode", EntityFieldValue.known(row.getProductCode()));
        values.put("productName", EntityFieldValue.known(row.getProductName()));
        values.put("productModel", EntityFieldValue.known(row.getProductModel()));
        values.put("productDesc", EntityFieldValue.known(row.getProductDesc()));
        return values;
    }
    @Override public void requireReadable(EntityDataRef target, EntityActor actor) { authorized(target, actor, false); }
    @Override public Long concurrencyBasis(EntityDataRef target, EntityActor actor) {
        return authorized(target, actor, false).getVersion();
    }
    @Override public void lockForWrite(EntityDataRef target, EntityActor actor, Long expectedVersion) {
        var observed = authorized(target, actor, true);
        var row = products.selectByProductCodeForUpdate(new ProductCatalogIdentityQuery(actor.tenantId(), observed.getProductCode()));
        if (row == null || Boolean.TRUE.equals(row.getDeleted()) || !Objects.equals(row.getId(), observed.getId())
                || !Objects.equals(row.getVersion(), expectedVersion))
            throw new IllegalStateException("PRODUCT_EXTENSION_VERSION_OR_IDENTITY_CONFLICT");
    }
    private AssetProductOfficialDO authorized(EntityDataRef target, EntityActor actor, boolean write) {
        actor.requireTenant(target.entity());
        if (!Objects.equals(actor.tenantId(), TenantContextHolder.getRequiredTenantId()) || target.isRevision()
                || !ownerModule().equals(target.entity().ownerModule()) || !entityType().equals(target.entity().entityType()))
            throw new IllegalArgumentException("PRODUCT_EXTENSION_IDENTITY_INVALID");
        // A system observer is only accepted by a trusted, already authorized read-only Owner.
        boolean allowed = !write && actor.isSystemObserver() || !actor.isSystemObserver()
                && permissions.hasAnyPermissions(actor.userId(), write ? new String[]{"pms:integration:execute"}
                : new String[]{"pms:imp-material-exch:query", "pms:integration:view", "pms:integration:execute"});
        if (!allowed) throw new IllegalArgumentException("PRODUCT_EXTENSION_FORBIDDEN");
        var row = products.selectById(target.entity().entityId());
        if (row == null || Boolean.TRUE.equals(row.getDeleted()) || !Objects.equals(row.getTenantId(), actor.tenantId()))
            throw new IllegalArgumentException("PRODUCT_EXTENSION_IDENTITY_INVALID");
        return row;
    }
}
