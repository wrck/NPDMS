package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.ProductCatalogMigrationApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.AssetProductOfficialMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.query.ProductCatalogIdentityQuery;
import cn.iocoder.yudao.module.pms.asset.service.product.OfficialProductCatalogLabels;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** AST owns directory writes; PLT owns the already existing formal entity property tables. */
@Service
@RequiredArgsConstructor
public class ProductCatalogMigrationApiImpl implements ProductCatalogMigrationApi {
    private final AssetProductOfficialMapper products;
    private final EntityExtensionApi extensions;
    private final PermissionApi permissions;
    @Override
    @Transactional(rollbackFor=Exception.class)
    public Long configureDefinition(Long tenantId, Long actorId, Long existingRevisionId, String correlationId) {
        if (!Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId()) || actorId == null || actorId <= 0
                || !permissions.hasAnyPermissions(actorId, "pms:integration:configure"))
            throw new IllegalArgumentException("PRODUCT_CATALOG_DEFINITION_FORBIDDEN");
        var actor = new EntityActor(tenantId, actorId, correlationId);
        var definition = existingRevisionId == null
                ? extensions.publishDefinition(tenantId, "AST", "ProductOfficial", List.of(OfficialProductCatalogLabels.definition()), actor)
                : extensions.definition(existingRevisionId, new EntityRef(tenantId, "AST", "ProductOfficial", 1L), actor);
        requireApprovedDefinition(definition);
        return definition.id();
    }
    @Override
    @Transactional(propagation=Propagation.MANDATORY, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ, rollbackFor=Exception.class)
    public List<Result> preview(Request request) { return execute(request, false); }
    @Override
    @Transactional(propagation=Propagation.MANDATORY, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ, rollbackFor=Exception.class)
    public List<Result> apply(Request request) { return execute(request, true); }

    @Override
    @Transactional(propagation=Propagation.MANDATORY, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ, rollbackFor=Exception.class)
    public List<Snapshot> resolve(ResolveRequest request) {
        if (request == null || !Objects.equals(request.tenantId(), TenantContextHolder.getRequiredTenantId())
                || request.actorId() == null || request.actorId() <= 0 || request.correlationId() == null
                || request.correlationId().isBlank() || request.labels() == null || request.labels().size() > 10000)
            throw new IllegalArgumentException("PRODUCT_CATALOG_RESOLVE_REQUEST_INVALID");
        if (!permissions.hasAnyPermissions(request.actorId(), "pms:integration:execute"))
            throw new IllegalArgumentException("PRODUCT_CATALOG_MIGRATION_FORBIDDEN");
        var groups = new TreeMap<String, List<Label>>();
        for (var label : request.labels()) {
            if (label == null || label.productCode() == null || label.productCode().isBlank()
                    || label.productCode().trim().length() > 64)
                throw new IllegalArgumentException("PRODUCT_CATALOG_SOURCE_IDENTITY_OR_LENGTH_INVALID");
            groups.computeIfAbsent(label.productCode().trim(), ignored -> new ArrayList<>()).add(label);
        }
        var result = new ArrayList<Snapshot>();
        var actor = new EntityActor(request.tenantId(), request.actorId(), request.correlationId());
        for (var group : groups.entrySet()) {
            var product = products.selectByProductCodeForUpdate(new ProductCatalogIdentityQuery(request.tenantId(), group.getKey()));
            if (product == null || product.getId() == null || product.getId() <= 0 || Boolean.TRUE.equals(product.getDeleted())
                    || !Objects.equals(product.getTenantId(), request.tenantId()) || !group.getKey().equals(product.getProductCode()))
                throw new IllegalStateException("PRODUCT_CATALOG_FORMAL_TARGET_UNRESOLVED");
            var current = extensions.read(EntityDataRef.current(ref(product)),
                    new EntityActor(request.tenantId(), 0L, EntityActor.SYSTEM_OBSERVER));
            if (current == null || current.definitionRevisionId() == null)
                throw new IllegalStateException("PRODUCT_CATALOG_FORMAL_LABELS_UNRESOLVED");
            requireApprovedDefinition(extensions.definition(current.definitionRevisionId(), ref(product), actor));
            var labels = OfficialProductCatalogLabels.decode(current.fields(), group.getKey());
            if (!labels.containsAll(group.getValue()))
                throw new IllegalStateException("PRODUCT_CATALOG_FORMAL_LABELS_UNRESOLVED");
            result.add(new Snapshot(product.getId(), product.getProductCode(), labels));
        }
        return List.copyOf(result);
    }

    private record Plan(AssetProductOfficialDO product, EntityExtensionApi.Values values, Snapshot before,
                        List<Label> labels, List<Entry> entries) {}
    private List<Result> execute(Request request, boolean apply) {
        if (request == null || !Objects.equals(request.tenantId(), TenantContextHolder.getRequiredTenantId())
                || request.actorId() == null || request.actorId() <= 0 || request.definitionRevisionId() == null
                || request.correlationId() == null || request.correlationId().isBlank() || request.entries() == null
                || request.entries().size() > 10000)
            throw new IllegalArgumentException("PRODUCT_CATALOG_MIGRATION_REQUEST_INVALID");
        if (!permissions.hasAnyPermissions(request.actorId(), apply ? new String[]{"pms:integration:execute"}
                : new String[]{"pms:integration:configure", "pms:integration:view", "pms:integration:execute"}))
            throw new IllegalArgumentException("PRODUCT_CATALOG_MIGRATION_FORBIDDEN");
        var actor = new EntityActor(request.tenantId(), request.actorId(), request.correlationId());
        var definition = extensions.definition(request.definitionRevisionId(), new EntityRef(request.tenantId(), "AST", "ProductOfficial", 1L), actor);
        requireApprovedDefinition(definition);
        var groups = new TreeMap<String,List<Entry>>();var identities = new HashSet<String>();
        for (var entry : request.entries()) {
            if (entry == null || entry.sourceKey() == null || entry.label() == null
                    || !Objects.equals(entry.sourceKey(), entry.label().productCode()) || entry.sourceKey().isBlank()
                    || entry.sourceKey().trim().length() > 64 || !identities.add(entry.sourceKey())
                    || entry.label().productName() != null && entry.label().productName().length() > 128
                    || entry.label().productModel() != null && entry.label().productModel().length() > 128)
                throw new IllegalArgumentException("PRODUCT_CATALOG_SOURCE_IDENTITY_OR_LENGTH_INVALID");
            groups.computeIfAbsent(entry.sourceKey().trim(), ignored -> new ArrayList<>()).add(entry);
        }
        var plans = new ArrayList<Plan>();
        for (var group : groups.entrySet()) {
            var product = products.selectByProductCodeForUpdate(new ProductCatalogIdentityQuery(request.tenantId(), group.getKey()));
            if (product == null && group.getValue().stream().map(Entry::label).map(label ->
                    Arrays.asList(label.productName(), label.productModel())).distinct().count() > 1)
                throw new IllegalStateException("PRODUCT_CATALOG_NEW_PRODUCT_LABEL_CONFLICT");
            if (product != null && (Boolean.TRUE.equals(product.getDeleted()) || !Objects.equals(product.getTenantId(), request.tenantId())
                    || !group.getKey().equals(product.getProductCode())))
                throw new IllegalStateException("PRODUCT_CATALOG_TARGET_IDENTITY_CONFLICT");
            var current = product == null ? new EntityExtensionApi.Values(null, Map.of(), 0)
                    : extensions.read(EntityDataRef.current(ref(product)), new EntityActor(request.tenantId(),0L,EntityActor.SYSTEM_OBSERVER));
            if (current.definitionRevisionId() != null && !Objects.equals(current.definitionRevisionId(), request.definitionRevisionId()))
                throw new IllegalStateException("PRODUCT_CATALOG_EXTENSION_DEFINITION_CONFLICT");
            var old = OfficialProductCatalogLabels.decode(current.fields(), group.getKey());
            var labels = new ArrayList<>(old);
            var before = product == null ? null : new Snapshot(product.getId(), product.getProductCode(), old);
            for (var entry : group.getValue()) {
                if (entry.targetId() != null && (product == null || !Objects.equals(entry.targetId(), product.getId()))
                        || entry.lastApplied() != null && (before == null
                        || !Objects.equals(entry.lastApplied().productId(), before.productId())
                        || !Objects.equals(entry.lastApplied().productCode(), before.productCode())
                        || !old.containsAll(entry.lastApplied().labels())))
                    throw new IllegalStateException("PRODUCT_CATALOG_BINDING_OR_MANUAL_CHANGE_CONFLICT");
                if (!labels.contains(entry.label())) labels.add(entry.label());
            }
            plans.add(new Plan(product, current, before, List.copyOf(labels), group.getValue()));
        }
        var result = new ArrayList<Result>();
        for (var plan : plans) {
            var product = plan.product();
            boolean inserted = product == null;
            if (apply && inserted) {
                var label = plan.entries().getFirst().label();
                product = new AssetProductOfficialDO();product.setId(IdWorker.getId());product.setTenantId(request.tenantId());
                product.setProductCode(label.productCode().trim());product.setProductName(label.productName());product.setProductDesc(label.productName());
                product.setProductModel(label.productModel());product.setStatus("ACTIVE");product.setVersion(0L);
                product.setSourceSystem("DPPMS");product.setSourceKey(label.productCode());product.setSyncStatus("SUCCESS");
                product.setSyncedAt(LocalDateTime.now());product.setCreator(request.actorId().toString());product.setUpdater(request.actorId().toString());
                if (products.insert(product) != 1) throw new IllegalStateException("PRODUCT_CATALOG_INSERT_FAILED");
            }
            boolean changed = inserted || !plan.before().labels().equals(plan.labels());
            if (apply && changed) {
                extensions.save(new EntityExtensionApi.Save(EntityDataRef.current(ref(product)), actor, product.getVersion(),
                        plan.values().version(), request.definitionRevisionId(), Map.of(OfficialProductCatalogLabels.FIELD,
                        OfficialProductCatalogLabels.encode(plan.labels()))));
            }
            Long id = product == null ? null : product.getId();
            var after = new Snapshot(id, plan.entries().getFirst().sourceKey().trim(), plan.labels());
            for (var entry : plan.entries()) result.add(new Result(entry.sourceKey(), id,
                    inserted ? "INSERT" : changed ? "UPDATE" : "UNCHANGED", plan.before(), after));
        }
        return List.copyOf(result);
    }
    private static EntityRef ref(AssetProductOfficialDO product) {
        return new EntityRef(product.getTenantId(), "AST", "ProductOfficial", product.getId());
    }
    private static void requireApprovedDefinition(EntityExtensionApi.DefinitionRevision definition) {
        if (definition == null || definition.fields() == null || definition.fields().stream().noneMatch(field ->
                OfficialProductCatalogLabels.definition().equals(field)))
            throw new IllegalArgumentException("PRODUCT_CATALOG_LABEL_DEFINITION_NOT_APPROVED");
    }
}
