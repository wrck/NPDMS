package cn.iocoder.yudao.module.pms.asset.service.version;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceSoftwareVersionRecordDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceSoftwareVersionRecordMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.DeviceSoftwareVersionRecordLockQuery;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** The record is immutable. Only its verified one-time legacy import may initialize its eight extensions. */
@Component
@RequiredArgsConstructor
public class SoftwareVersionRecordEntityProvider implements EntityFieldProvider {
    private final DeviceSoftwareVersionRecordMapper records;
    private final DeviceAccessScopeService access;
    private final PermissionApi permissions;
    // This server-only scope cannot be constructed through the generic PLT extension endpoint.
    private static final ThreadLocal<EntityRef> INITIALIZING = new ThreadLocal<>();
    static <T> T initialize(EntityRef ref, java.util.function.Supplier<T> work) {
        if (INITIALIZING.get() != null) throw new IllegalStateException("Nested software history initialization");
        INITIALIZING.set(ref);
        try { return work.get(); } finally { INITIALIZING.remove(); }
    }
    @Override public String ownerModule() { return "AST"; }
    @Override public String entityType() { return "SoftwareVersionRecord"; }
    @Override public List<EntityField> fields() {
        return List.of(new EntityField("deviceSn", EntityField.Type.TEXT, false),
                new EntityField("conpVersion", EntityField.Type.TEXT, false),
                new EntityField("bootVersion", EntityField.Type.TEXT, false),
                new EntityField("cpldVersion", EntityField.Type.TEXT, false),
                new EntityField("pcbVersion", EntityField.Type.TEXT, false));
    }
    @Override public boolean usesValidatedExtensionPatch() { return true; }
    @Override public Map<String, EntityFieldValue> read(EntityDataRef target, EntityActor actor) {
        var row = authorized(target, actor);
        var result = new LinkedHashMap<String, EntityFieldValue>();
        result.put("deviceSn", EntityFieldValue.known(row.getDeviceSn()));
        result.put("conpVersion", EntityFieldValue.known(row.getConpVersion()));
        result.put("bootVersion", EntityFieldValue.known(row.getBootVersion()));
        result.put("cpldVersion", EntityFieldValue.known(row.getCpldVersion()));
        result.put("pcbVersion", EntityFieldValue.known(row.getPcbVersion()));
        return result;
    }
    @Override public void requireReadable(EntityDataRef target, EntityActor actor) { authorized(target, actor); }
    @Override public Long concurrencyBasis(EntityDataRef target, EntityActor actor) { authorized(target, actor); return 0L; }
    @Override public void lockForWrite(EntityDataRef target, EntityActor actor, Long expectedVersion) {
        var row = authorized(target, actor);
        if (!Objects.equals(INITIALIZING.get(), target.entity()) || !Objects.equals(expectedVersion, 0L)
                || !"DPPMS".equals(row.getDataSource()) || actor.isSystemObserver()
                || !permissions.hasAnyPermissions(actor.userId(), "pms:integration:execute"))
            throw new IllegalArgumentException("Software history extensions are read-only");
        var locked = records.selectForUpdate(new DeviceSoftwareVersionRecordLockQuery(actor.tenantId(), row.getId()));
        if (!Objects.equals(row, locked)) throw new IllegalStateException("Software history changed during extension initialization");
    }
    private DeviceSoftwareVersionRecordDO authorized(EntityDataRef target, EntityActor actor) {
        actor.requireTenant(target.entity());
        if (target.isRevision() || !Objects.equals(TenantContextHolder.getRequiredTenantId(), actor.tenantId())
                || !ownerModule().equals(target.entity().ownerModule()) || !entityType().equals(target.entity().entityType())
                || actor.isSystemObserver()) throw new IllegalArgumentException("Software history identity invalid");
        boolean initializing = Objects.equals(INITIALIZING.get(), target.entity());
        if (!permissions.hasAnyPermissions(actor.userId(), initializing ? new String[]{"pms:integration:execute"}
                : new String[]{"pms:device:query"})) throw new IllegalArgumentException("Software history is not readable");
        var row = records.selectById(target.entity().entityId());
        if (row == null || !Objects.equals(row.getTenantId(), actor.tenantId()) || Boolean.TRUE.equals(row.getDeleted()))
            throw new IllegalArgumentException("Software history is not readable");
        if (!initializing) access.assertReadable(new DeviceAccessScopeService.ReadAccess(actor.tenantId(), actor.userId(), row.getDeviceId()));
        return row;
    }
}
