package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectOrganizationMapper.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

/** Direct P01 sink. Never invokes an organization/dept Owner or changes the original task. */
@Component
@RequiredArgsConstructor
public class DppmsDirectOrganizationTask extends AbstractSynchronizeTask<DppmsDirectOrganizationTask.Execution, DirectOrganizationPlan.Result, List<DataSyncAdapter.Change>> implements DataSyncAdapter {
    private final DirectOrganizationMapper mapper;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    private final SyncBindingMapper bindings;
    private final DataSource dataSource;
    private final CacheManager caches;
    record Execution(Batch batch, boolean apply, long tenant, Long connectionId, String actor, LocalDateTime time) {}
    @Override public Descriptor descriptor() {
        return new Descriptor(DppmsDirectOrganizationTemplate.ADAPTER,"P01公司部门直写",DppmsDirectOrganizationTemplate.OBJECTS.stream().map(object -> {
            boolean company = Set.of("COMPANY","COMPANY_REFERENCE").contains(object);
            List<String> names = "COMPANY_REFERENCE".equals(object) ? List.of("sourceCompanyId","sourceCompanyCode") : DppmsDirectOrganizationTemplate.FIELDS;
            return new ObjectDescriptor(object,object,names.stream().map(name -> new Field(name,name,"STRING",true)).toList(),
                    "SYSTEM",company ? "Company" : "Department",company ? "system_company" : "system_dept",false);
        }).toList(),List.of("RETAIN"),List.of("UPSERT"),false);
    }
    @Override public boolean sharesTargetAcrossSources() { return true; }
    @Override public List<Change> preview(Batch batch) { return syncData(context(batch,false)); }
    @Override public List<Change> apply(Batch batch) { return syncData(context(batch,true)); }
    private Execution context(Batch batch,boolean apply) {
        long tenant = TenantContextHolder.getRequiredTenantId();
        String[] identity = batch.owner() == null ? new String[0] : batch.owner().split(":");
        if (identity.length != 3 || !"integration".equals(identity[0]) || !Long.toString(tenant).equals(identity[1]))
            throw new IllegalArgumentException("P01运行租户身份无效");
        var task = tasks.required(SyncFieldMapper.primaryKey(identity[2]));
        if (!DppmsDirectOrganizationTemplate.ADAPTER.equals(task.getAdapter()) || !"DPPMS".equals(task.getSourceSystem()) || task.getActiveRunId() == null)
            throw new IllegalArgumentException("P01直写任务身份无效");
        var run = runs.selectScoped(new SyncQueries.Id(tenant,task.getActiveRunId()));
        if (run == null || !Objects.equals(run.getTenantId(),tenant) || !Objects.equals(run.getTaskId(),task.getId())
                || !Objects.equals(run.getConfigVersion(),task.getVersion()) || !Objects.equals(run.getPreview(),!apply)
                || !(apply ? "APPLYING" : "VALIDATING").equals(run.getStatus()) || run.getStartedAt() == null)
            throw new IllegalArgumentException("P01运行阶段、配置版本或审计身份无效");
        var definition = JsonUtils.parseObject(task.getDefinition(),SyncDefinition.class);
        if (!"ONCE".equals(definition.mode()) || !"SNAPSHOT".equals(definition.effectiveReadStrategy()) || definition.resetMappingsBeforeLoad())
            throw new IllegalArgumentException("P01须完整快照保留绑定，不能分块提交组织树");
        return new Execution(batch,apply,tenant,task.getConnectionId(),Long.toString(SyncFieldMapper.primaryKey(run.getCreator())),run.getStartedAt().withNano(0));
    }
    @Override protected void syncDataBefore(Execution context) {
        Batch batch = context.batch();
        if (!batch.full() || batch.clearBeforeLoad() || !"RETAIN".equals(batch.missingPolicy()) || !"UPSERT".equals(batch.loadingMode()))
            throw new IllegalArgumentException("P01直写必须完整核对并保留正式目录");
        if (context.apply()) {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("P01写表必须与绑定回执处于同一事务");
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                if (connection.getTransactionIsolation() != Connection.TRANSACTION_REPEATABLE_READ)
                    throw new IllegalStateException("P01写表须使用REPEATABLE_READ目录锁");
            } catch (SQLException failure) { throw new IllegalStateException("无法确认目标事务隔离",failure); }
            finally { DataSourceUtils.releaseConnection(connection,dataSource); }
        }
    }
    @Override protected DirectOrganizationPlan.Result syncDataQuery(Execution context) {
        var scope = new Scope(context.tenant());
        List<Node> catalogue = new ArrayList<>(context.apply() ? mapper.lockCompanies(scope) : mapper.selectCompanies(scope));
        catalogue.addAll(context.apply() ? mapper.lockDepartments(scope) : mapper.selectDepartments(scope));
        return DirectOrganizationPlan.build(new DirectOrganizationPlan.Input(context.batch(),catalogue,leaders(context),context.actor(),context.time(),context.apply() ? IdWorker::getId : null));
    }
    private Map<String,Long> leaders(Execution context) {
        Set<String> employees = new HashSet<>();
        for (Row row : context.batch().rows()) if ("ehr_department".equals(row.object())) {
            String employee = DirectOrganizationPlan.text(DirectOrganizationPlan.json(row.fields().get("canonicalFields")),"rawDirector");
            if (employee != null) employees.add(employee);
        }
        if (employees.isEmpty()) return Map.of();
        Map<String,Set<Long>> matches = new HashMap<>();
        for (var binding : bindings.selectEhrEmployeeUsers(new SyncQueries.EhrEmployeeUsers(context.tenant(),context.connectionId(),employees))) {
            var fields = JsonUtils.parseMap(binding.getFieldsJson());
            String employee = Objects.toString(fields.get("_verifiedEhrEmployeeId"),null);
            if (!employees.contains(employee) || binding.getTargetId() == null || binding.getTargetId() <= 0 || Boolean.TRUE.equals(fields.get("_auditOnly")))
                throw new IllegalArgumentException("主管账号来源回执无效");
            matches.computeIfAbsent(employee,ignored -> new HashSet<>()).add(binding.getTargetId());
        }
        Map<String,Long> result = new HashMap<>();
        matches.forEach((employee,ids) -> { if (ids.size() == 1) result.put(employee,ids.iterator().next()); });
        Set<Long> ids = new HashSet<>(result.values());
        if (!ids.isEmpty() && !ids.equals(new HashSet<>(mapper.selectLeaders(new Leaders(context.tenant(),ids)))))
            throw new IllegalArgumentException("主管用户不存在、跨租户或已删除");
        return result;
    }
    @Override protected List<Change> syncDataInsertWrapper(Execution context,DirectOrganizationPlan.Result plan) {
        if (context.apply()) for (var mutation : plan.writes()) {
            int affected;
            if (mutation.before() != null) affected = mapper.completeLeader(new LeaderWrite(context.tenant(),mutation.before(),mutation.after()));
            else {
                var write = new Write(context.tenant(),mutation.after());
                affected = "COMPANY".equals(mutation.after().type()) ? mapper.insertCompany(write) : mapper.insertDepartment(write);
            }
            if (affected != 1) throw new IllegalStateException("P01目标写入发生并发变化，整批回滚");
        }
        return plan.changes();
    }
    @Override public void refreshCaches() {
        var cache = caches.getCache(cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
        if (cache != null) cache.clear();
    }
}
