package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.customer.api.masterdata.dto.*;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;

/** P10 direct entry: local mapping/version checks precede any CRM-owned writes. */
@Component
public class CrmDirectCustomerTask extends AbstractSynchronizeTask<CrmDirectCustomerTask.Execution,List<CrmDirectCustomerTask.Planned>,List<DataSyncAdapter.Change>> implements DataSyncAdapter {
    record Execution(Batch batch,boolean apply) {}
    record Planned(Row row,CustomerMasterDataCommand command,CustomerMasterDataResult result,String action,String issue) {}
    private final DirectCustomerPersistence persistence;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    private final CrmCustomerSyncAdapter interpreter;
    CrmDirectCustomerTask(DirectCustomerMapper mapper,DataSource dataSource,SyncTaskService tasks,SyncRunMapper runs) {
        this.persistence=new DirectCustomerPersistence(mapper,dataSource);this.tasks=tasks;this.runs=runs;
        this.interpreter=new CrmCustomerSyncAdapter(persistence);
    }
    @Override public Descriptor descriptor() {
        var original=interpreter.descriptor();
        return new Descriptor(CrmDirectCustomerTemplate.ADAPTER,"CRM客户主数据（直写）",original.objects(),original.missingPolicies(),original.loadingModes(),false,true);
    }
    @Override public boolean supportsStreaming(){return true;}
    @Override public boolean requiresAllBindings(){return false;}
    @Override public void refreshCaches(){}
    @Override public List<Change> preview(Batch batch){return syncData(new Execution(batch,false));}
    @Override public List<Change> apply(Batch batch){return syncData(new Execution(batch,true));}
    @Override protected void syncDataBefore(Execution execution) {
        var batch=execution.batch();long tenant=TenantContextHolder.getRequiredTenantId();
        if(batch.owner()==null || !batch.owner().matches("integration:"+tenant+":[1-9][0-9]*")
                || !batch.full() || batch.clearBeforeLoad() || batch.adoptExisting() || !"RETAIN".equals(batch.missingPolicy()) || !"UPSERT".equals(batch.loadingMode()))
            throw new IllegalArgumentException("CRM客户直写运行范围无效");
        var task=tasks.required(SyncFieldMapper.primaryKey(batch.owner().substring(batch.owner().lastIndexOf(':')+1)));
        var definition=JsonUtils.parseObject(task.getDefinition(),SyncDefinition.class);
        var run=task.getActiveRunId()==null?null:runs.selectScoped(new SyncQueries.Id(tenant,task.getActiveRunId()));
        if(!CrmDirectCustomerTemplate.ADAPTER.equals(task.getAdapter()) || !"CRM".equals(task.getSourceSystem())
                || definition==null || !"ONCE".equals(definition.mode()) || definition.resetMappingsBeforeLoad()
                || run==null || !Objects.equals(run.getTenantId(),tenant) || !Objects.equals(run.getTaskId(),task.getId())
                || !Objects.equals(run.getConfigVersion(),task.getVersion()) || !Objects.equals(run.getPreview(),!execution.apply())
                || !(execution.apply()?Set.of("READING","APPLYING"):Set.of("READING","VALIDATING")).contains(run.getStatus()))
            throw new IllegalArgumentException("CRM客户直写任务或配置版本已变化");
    }
    @Override protected List<Planned> syncDataQuery(Execution execution) {
        var result=new ArrayList<Planned>();var sourceKeys=new HashSet<String>();var codes=new HashSet<String>();
        for(var row:execution.batch().rows()) {
            if(!"CUSTOMER".equals(row.object()) || !sourceKeys.add(row.sourceKey()))throw new IllegalArgumentException("CRM客户来源对象或身份重复");
            String issue=text(row.fields(),"migrationIssue");
            if(issue!=null){result.add(new Planned(row,null,null,"ISSUE",issue));continue;}
            var command=command(row);
            if(command.customerCode()!=null && !codes.add(command.customerCode().toLowerCase(Locale.ROOT))) {
                result.add(new Planned(row,command,null,"CONFLICT","同一分块的正式客户编码重复"));continue;
            }
            var planned=persistence.preview(command);
            result.add(new Planned(row,command,planned,planned.replayed()?"UNCHANGED":planned.customerId()==null?"CREATED":"UPDATED",null));
        }
        return List.copyOf(result);
    }
    @Override protected List<Change> syncDataInsertWrapper(Execution execution,List<Planned> plan) {
        if(execution.apply()) {
            if(plan.stream().anyMatch(item->item.issue()!=null))throw new IllegalArgumentException("CRM客户直写存在未决问题，当前分块未写入");
            var changes=new ArrayList<Change>();
            for(var item:plan) {
                var result=persistence.apply(item.command());
                String action=result.replayed()?"UNCHANGED":item.result().customerId()==null?"CREATED":"UPDATED";
                changes.add(new Change(item.row().object(),item.row().sourceKey(),result.customerId(),action,Map.of(),item.row().fields(),null));
            }
            return List.copyOf(changes);
        }
        return plan.stream().map(item->new Change(item.row().object(),item.row().sourceKey(),item.result()==null?item.row().targetId():item.result().customerId(),
                item.action(),Map.of(),item.row().fields(),item.issue())).toList();
    }
    private static CustomerMasterDataCommand command(Row row) {
        var f=row.fields();String version=text(f,"sourceVersion");
        return new CustomerMasterDataCommand(TenantContextHolder.getRequiredTenantId(),row.targetId(),text(f,"customerCode"),text(f,"erpCustomerCode"),text(f,"customerName"),text(f,"shortName"),
                text(f,"contactPhone"),text(f,"contactEmail"),text(f,"address"),text(f,"departmentCode"),text(f,"departmentName"),text(f,"marketCode"),text(f,"marketName"),
                text(f,"systemCode"),text(f,"systemName"),text(f,"expendCode"),text(f,"expendName"),text(f,"industryCode"),text(f,"industryName"),text(f,"lifecycleStatus"),
                row.sourceKey(),version,LocalDateTime.parse(text(f,"dataAsOf").replace(' ','T')),"CRM:"+row.sourceKey()+":"+version,null);
    }
    private static String text(Map<String,Object> fields,String name){Object value=fields.get(name);return value==null?null:value.toString();}
}
