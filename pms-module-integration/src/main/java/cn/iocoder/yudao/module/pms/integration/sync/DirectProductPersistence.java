package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import javax.sql.DataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import java.sql.Connection;
import java.sql.SQLException;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import static cn.iocoder.yudao.module.pms.asset.api.migration.ProductCatalogMigrationApi.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;

/** AST preserves existing official fields and only creates missing product codes. */
@RequiredArgsConstructor
final class DirectProductPersistence {
    private final DirectProductMapper products;
    private final PermissionApi permissions;
    private final DataSource dataSource;
    public List<Result> preview(Request request) { requireTransaction();return execute(request, false); }
    public List<Result> apply(Request request) { requireTransaction();return execute(request, true); }

    private record Plan(DirectProductRow product, Snapshot before, List<Entry> entries) {}
    private List<Result> execute(Request request, boolean apply) {
        if (request == null || !Objects.equals(request.tenantId(), TenantContextHolder.getRequiredTenantId())
                || request.actorId() == null || request.actorId() <= 0
                || request.correlationId() == null || request.correlationId().isBlank() || request.entries() == null
                || request.entries().size() > 10000)
            throw new IllegalArgumentException("PRODUCT_CATALOG_MIGRATION_REQUEST_INVALID");
        if (!permissions.hasAnyPermissions(request.actorId(), apply ? new String[]{"pms:integration:execute"}
                : new String[]{"pms:integration:configure", "pms:integration:view", "pms:integration:execute"}))
            throw new IllegalArgumentException("PRODUCT_CATALOG_MIGRATION_FORBIDDEN");
        var groups = new TreeMap<String,List<Entry>>();var identities = new HashSet<String>();
        for (var entry : request.entries()) {
            if (entry == null || entry.sourceKey() == null || entry.label() == null
                    || !entry.sourceKey().equals(entry.label().productCode()) || entry.label().productCode()==null || entry.label().productCode().isBlank()
                    || entry.label().productCode().trim().length() > 64 || !identities.add(entry.sourceKey())
                    || entry.label().productName() != null && entry.label().productName().length() > 128
                    || entry.label().productModel() != null && entry.label().productModel().length() > 128)
                throw new IllegalArgumentException("PRODUCT_CATALOG_SOURCE_IDENTITY_OR_LENGTH_INVALID");
            groups.computeIfAbsent(entry.label().productCode().trim(), ignored -> new ArrayList<>()).add(entry);
        }
        var plans = new ArrayList<Plan>();
        for (var group : groups.entrySet()) {
            var product = products.selectByProductCodeForUpdate(new DirectProductMapper.CodeQuery(request.tenantId(), group.getKey()));
            if (product == null && group.getValue().stream().map(Entry::label).map(label ->
                    Arrays.asList(label.productName(), label.productModel())).distinct().count() > 1)
                throw new IllegalStateException("PRODUCT_CATALOG_NEW_PRODUCT_LABEL_CONFLICT");
            if (product != null && (Boolean.TRUE.equals(product.getDeleted()) || !Objects.equals(product.getTenantId(), request.tenantId())
                    || !group.getKey().equals(product.getProductCode())))
                throw new IllegalStateException("PRODUCT_CATALOG_TARGET_IDENTITY_CONFLICT");
            var before = product == null ? null : snapshot(product);
            for (var entry : group.getValue()) {
                if (entry.targetId() != null && (product == null || !Objects.equals(entry.targetId(), product.getId()))
                        || entry.lastApplied() != null && (before == null
                        || !Objects.equals(entry.lastApplied().productId(), before.productId())
                        || !Objects.equals(entry.lastApplied().productCode(), before.productCode())))
                    throw new IllegalStateException("PRODUCT_CATALOG_BINDING_OR_MANUAL_CHANGE_CONFLICT");
            }
            plans.add(new Plan(product, before, group.getValue()));
        }
        var result = new ArrayList<Result>();
        for (var plan : plans) {
            var product = plan.product();
            boolean inserted = product == null;
            if (apply && inserted) {
                var label = plan.entries().getFirst().label();
                product = new DirectProductRow();product.setId(IdWorker.getId());product.setTenantId(request.tenantId());
                product.setProductCode(label.productCode().trim());product.setProductName(label.productName());product.setProductDesc(label.productName());
                product.setProductModel(label.productModel());product.setStatus("ACTIVE");product.setVersion(0L);
                product.setSourceSystem("DPPMS");product.setSourceKey(label.productCode());product.setSyncStatus("SUCCESS");
                product.setSyncedAt(LocalDateTime.now());product.setCreator(request.actorId().toString());product.setUpdater(request.actorId().toString());
                if (products.insertProduct(product) != 1) throw new IllegalStateException("PRODUCT_CATALOG_INSERT_FAILED");
            }
            Long id = product == null ? null : product.getId();
            var label = plan.entries().getFirst().label();
            var after = product == null ? new Snapshot(null, label.productCode().trim(),
                    List.of(new Label(label.productCode().trim(), label.productName(), label.productModel()))) : snapshot(product);
            for (var entry : plan.entries()) result.add(new Result(entry.sourceKey(), id,
                    inserted ? "INSERT" : "UNCHANGED", plan.before(), after));
        }
        return List.copyOf(result);
    }
    private static Snapshot snapshot(DirectProductRow product) {
        return new Snapshot(product.getId(), product.getProductCode(), List.of(
                new Label(product.getProductCode(), product.getProductName(), product.getProductModel())));
    }
    private void requireTransaction() {
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("产品直写规划及写入须处于活动事务");
        var connection=DataSourceUtils.getConnection(dataSource);
        try{if(connection.getTransactionIsolation()!=Connection.TRANSACTION_REPEATABLE_READ)throw new IllegalStateException("产品直写需要REPEATABLE_READ");}
        catch(SQLException error){throw new IllegalStateException("无法确认产品直写事务隔离",error);}
        finally{DataSourceUtils.releaseConnection(connection,dataSource);}
    }

}
