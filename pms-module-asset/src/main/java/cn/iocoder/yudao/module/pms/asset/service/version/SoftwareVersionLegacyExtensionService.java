package cn.iocoder.yudao.module.pms.asset.service.version;

import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Invoked by the verified INT/AST import transaction, never exposed as a normal history editing API. */
@Service
@RequiredArgsConstructor
public class SoftwareVersionLegacyExtensionService {
    private final EntityExtensionApi extensions;
    private final cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions;
    public static final Set<String> CODES = Set.of("conp_bak", "boot_bak", "cpld_bak", "pcb_bak",
            "conp_change", "boot_change", "cpld_change", "pcb_change");
    public static List<EntityExtensionApi.Definition> definitions() {
        var fields = new ArrayList<EntityExtensionApi.Definition>();
        for (String version : List.of("conp", "boot", "cpld", "pcb")) {
            fields.add(new EntityExtensionApi.Definition(version+"_bak", version.toUpperCase(Locale.ROOT)+"原变更前备份",
                    EntityField.Type.TEXT, false, 255, List.of()));
            fields.add(new EntityExtensionApi.Definition(version+"_change", version.toUpperCase(Locale.ROOT)+"原变化标志",
                    EntityField.Type.NUMBER, false, null, List.of()));
        }
        return List.copyOf(fields);
    }

    @Transactional(rollbackFor=Exception.class)
    public Long configureDefinition(Long tenant, Long actor, Long existingRevisionId) {
        if (!Objects.equals(tenant,cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId())
                || actor==null || actor<=0 || !permissions.hasAnyPermissions(actor,"pms:integration:configure")
                || !permissions.hasAnyPermissions(actor,"pms:dynamic-form-template:manage"))
            throw new IllegalArgumentException("Software history definition configuration forbidden");
        var entityActor=new EntityActor(tenant,actor,"DPPMS_SOFTWARE_HISTORY_DEFINITION");
        var definition=existingRevisionId==null
                ? extensions.publishDefinition(tenant,"AST","SoftwareVersionRecord",definitions(),entityActor)
                : extensions.definition(existingRevisionId,new EntityRef(tenant,"AST","SoftwareVersionRecord",1L),entityActor);
        requireDefinition(tenant,definition);
        return definition.id();
    }

    private static void requireDefinition(Long tenant,EntityExtensionApi.DefinitionRevision definition) {
        if (definition==null || definition.id()==null || !Objects.equals(definition.tenantId(),tenant)
                || !"AST".equals(definition.ownerModule()) || !"SoftwareVersionRecord".equals(definition.entityType())
                || definition.fields().size()!=8 || !new HashSet<>(definition.fields()).equals(new HashSet<>(definitions())))
            throw new IllegalArgumentException("Software history definition differs from the eight approved properties");
    }

    @Transactional(propagation=Propagation.MANDATORY, rollbackFor=Exception.class)
    public EntityExtensionApi.Values initialize(Long tenant, Long actor, Long recordId, Long definitionId, Map<String,Object> values) {
        if (values == null || !values.keySet().equals(CODES)) throw new IllegalArgumentException("All eight legacy version attributes are required");
        for (var entry : values.entrySet()) {
            var value = entry.getValue();
            if (value == null) continue;
            if (entry.getKey().endsWith("_bak") && !(value instanceof String)
                    || entry.getKey().endsWith("_change") && (!(value instanceof Number n) || n.intValue() < 0 || n.intValue() > 1
                    || new java.math.BigDecimal(n.toString()).compareTo(java.math.BigDecimal.valueOf(n.intValue())) != 0))
                throw new IllegalArgumentException("Legacy software attribute type or flag invalid");
        }
        var ref = new EntityRef(tenant, "AST", "SoftwareVersionRecord", recordId);
        var target = EntityDataRef.current(ref);
        var entityActor = new EntityActor(tenant, actor, "DPPMS_SOFTWARE_HISTORY_IMPORT");
        requireDefinition(tenant,extensions.definition(definitionId,ref,entityActor));
        return SoftwareVersionRecordEntityProvider.initialize(ref, () -> {
            var existing = extensions.read(target, entityActor);
            if (existing.version() != 0) {
                if (!Objects.equals(existing.definitionRevisionId(), definitionId) || !same(existing.fields(), values))
                    throw new IllegalStateException("Immutable legacy software extensions differ from the replay");
                return existing;
            }
            return extensions.save(new EntityExtensionApi.Save(target, entityActor, 0L, 0, definitionId,
                    Collections.unmodifiableMap(new LinkedHashMap<>(values))));
        });
    }
    private boolean same(Map<String,Object> a, Map<String,Object> b) {
        if (!a.keySet().equals(b.keySet())) return false;
        for (String key : a.keySet()) {
            Object x=a.get(key), y=b.get(key);
            if (x instanceof Number nx && y instanceof Number ny) {
                if (new java.math.BigDecimal(nx.toString()).compareTo(new java.math.BigDecimal(ny.toString())) != 0) return false;
            } else if (!Objects.equals(x,y)) return false;
        }
        return true;
    }
}
