package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsSoftwareHistorySyncTemplate.*;
@Component @RequiredArgsConstructor
public class DppmsSoftwareHistorySyncAdapter implements DataSyncAdapter {
    private final SyncOwnerRunContext context;
    private final DppmsPublishedProjectScopeApi published;
    private final ProjectMasterMigrationApi projects;
    private final DppmsAssetSourceHistoryApi owner;
    @Override public boolean supportsStreaming(){return true;}
    @Override public boolean requiresAllBindings(){return false;}
    @Override public Descriptor descriptor() {
        return new Descriptor(ADAPTER,"DPPMS软件批次与版本历史",List.of(object(BATCH,"DeviceSoftwareChangeBatch","ast_device_software_change_batch"),
            object(VERSION,"SoftwareVersionRecord","ast_device_software_version_record")),List.of("RETAIN"),List.of("UPSERT"),false,true);
    }
    private static ObjectDescriptor object(String object,String type,String table) {return new ObjectDescriptor(object,object,columns(object).stream().map(n->new Field(n,n,"STRING",false)).toList(),"AST",type,table,false);}
    @Override public void refreshCaches() {}
    @Override @Transactional(rollbackFor=Exception.class) public List<Change> preview(Batch batch){return execute(batch,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class) public List<Change> apply(Batch batch){return execute(batch,true);}
    private List<Change> execute(Batch batch,boolean apply) {
        var run=context.require(new SyncOwnerRunContext.Request(batch,ADAPTER,apply));
        String selectedDefinition=Objects.toString(run.definition().sources().getFirst().parameters().get("extensionDefinitionId"),"");
        if(selectedDefinition.isBlank())fail("EXTENSION_DEFINITION_REQUIRED");
        Long definitionId=SyncFieldMapper.primaryKey(selectedDefinition);
        if(definitionId<=0)fail("EXTENSION_DEFINITION_REQUIRED");
        var prior=new HashMap<String,Binding>();for(var b:batch.bindings())if(prior.put(b.object()+":"+b.sourceKey(),b)!=null)fail("BINDING_DUPLICATE");
        var rawRows=new LinkedHashMap<String,Map<String,String>>();var sourceProjects=new LinkedHashSet<String>();
        for(var row:batch.rows()) {
            if(!Set.of(BATCH,VERSION).contains(row.object()) || !row.fields().keySet().containsAll(columns(row.object())))fail("RAW_COLUMNS_MISSING");
            var raw=new LinkedHashMap<String,String>();(BATCH.equals(row.object())?BATCH_COLUMNS:VERSION_COLUMNS).forEach(n->raw.put(n,text(row.fields().get(n))));
            String key=row.object()+":"+row.sourceKey();
            if(!Objects.equals(row.sourceKey(),raw.get("id")) || rawRows.put(key,raw)!=null)fail("SOURCE_INVALID");
            var old=prior.get(key);
            if(old==null && row.targetId()!=null || old!=null && (!Objects.equals(row.targetId(),old.targetId())
                || !(old.lastFields().get("originalFields") instanceof Map<?,?> fields) || !fields.keySet().equals(raw.keySet())
                || raw.keySet().stream().anyMatch(n->!Objects.equals(raw.get(n),text(fields.get(n))))))fail("BOUND_SOURCE_CHANGED");
            sourceProjects.add(raw.get("projectId"));
        }
        if(batch.rows().isEmpty())return List.of();
        if(sourceProjects.size()>500)fail("PROJECT_SCOPE_LIMIT");
        var scope=published.resolve(new DppmsPublishedProjectScopeApi.Query(run.tenantId(),run.task().getConnectionId(),List.copyOf(sourceProjects)));
        var references=new HashMap<String,DppmsProjectBindingApi.ProjectBinding>();
        for(var project:scope.projects())if(!sourceProjects.contains(project.sourceProjectKey()) || references.put(project.sourceProjectKey(),project)!=null)fail("PROJECT_AMBIGUOUS");
        if(!references.keySet().equals(sourceProjects))fail("PROJECT_SCOPE_CHANGED");
        var groups=new TreeMap<String,List<Row>>();
        for(var row:batch.rows()) {
            var raw=rawRows.get(row.object()+":"+row.sourceKey());var reference=references.get(raw.get("projectId"));
            if(!Objects.equals(text(row.fields().get("projectType")),reference.projectType())
                || !Objects.equals(text(row.fields().get("originalProjectCode")),reference.originalProjectCode()))fail("PROJECT_IDENTITY_CHANGED");
            groups.computeIfAbsent(raw.get("projectId"),k->new ArrayList<>()).add(row);
        }
        var changes=new HashMap<String,Change>();
        for(var group:groups.entrySet()) {
            var reference=references.get(group.getKey());
            var facts=projects.inspectBindings(new ProjectMasterMigrationApi.IdentityInspection(run.tenantId(),run.actorId(),
                List.of(new ProjectMasterMigrationApi.IdentityTarget(reference.projectId(),reference.projectType(),reference.projectCode()))));
            if(facts.size()!=1 || !Objects.equals(facts.getFirst().id(),reference.projectId()))fail("PROJECT_UNRESOLVED");
            projects.lockBindings(new ProjectMasterMigrationApi.BindingVerification(run.tenantId(),run.actorId(),facts));
            var parents=new HashMap<Long,Row>();var parentCommands=new ArrayList<SoftwareBatch>();var children=new ArrayList<SoftwareObservation>();
            for(var row:group.getValue()) {
                var old=prior.get(row.object()+":"+row.sourceKey());
                if(old!=null && !Objects.equals(text(old.lastFields().get("projectId")),reference.projectId().toString()))fail("BOUND_PROJECT_CHANGED");
                var raw=rawRows.get(row.object()+":"+row.sourceKey());
                if(BATCH.equals(row.object())) {
                    Long id=positive(raw.get("id"));if(parents.put(id,row)!=null)fail("PARENT_DUPLICATE");
                    parentCommands.add(new SoftwareBatch(id,positive(raw.get("projectId")),raw.get("changeVersion"),raw.get("changeRemark"),integer(raw.get("latest")),raw.get("createBy"),time(raw.get("createTime")),raw.get("updateBy"),time(raw.get("updateTime")),row.targetId()));
                }
            }
            for(var row:group.getValue())if(VERSION.equals(row.object())) {
                var raw=rawRows.get(row.object()+":"+row.sourceKey());Long log=positive(raw.get("logId"));
                var parent=parents.get(log);if(parent==null)fail("PARENT_MISSING");
                children.add(new SoftwareObservation(positive(raw.get("id")),positive(raw.get("projectId")),log,raw.get("contractNo"),raw.get("itemCode"),raw.get("barCode"),
                    raw.get("conp"),raw.get("conpType"),raw.get("conpSeries"),raw.get("conpMark"),raw.get("conpBak"),integer(raw.get("conpChange")),raw.get("cpld"),raw.get("cpldBak"),integer(raw.get("cpldChange")),
                    raw.get("boot"),raw.get("bootBak"),integer(raw.get("bootChange")),raw.get("pcb"),raw.get("pcbBak"),integer(raw.get("pcbChange")),date(raw.get("executeTime")),integer(raw.get("datastate")),
                    time(raw.get("createTime")),raw.get("createBy"),time(raw.get("updateTime")),raw.get("updateBy"),raw.get("customInfo")).withMapping(row.targetId(),parent.targetId(),definitionId));
            }
            var command=new Command(run.tenantId(),run.actorId(),reference.projectId(),group.getKey(),"sync:"+run.run().getId(),
                run.run().getSourceUpper()==null?run.run().getStartedAt():run.run().getSourceUpper(),children,parentCommands,List.of());
            var mapped=new HashMap<String,Long>();
            if(apply)for(var receipt:owner.importSourceHistory(command)) {
                String object=sourceObject(receipt.domain());String key=object+":"+receipt.sourceKey();
                String table=BATCH.equals(object)?"ast_device_software_change_batch":"ast_device_software_version_record";
                if(receipt.reasonCode()!=null || receipt.targets().size()!=1 || !table.equals(receipt.targets().getFirst().targetTable())
                    || mapped.put(key,receipt.targets().getFirst().targetId())!=null)fail("OWNER_INVALID");
            }
            else for(var receipt:owner.previewSourceHistory(command))
                if(mapped.put(sourceObject(receipt.domain())+":"+receipt.sourceKey(),receipt.targetId())!=null)fail("OWNER_INVALID");
            if(mapped.size()!=group.getValue().size())fail("OWNER_INCOMPLETE");
            for(var row:group.getValue()) {
                String key=row.object()+":"+row.sourceKey();Long id=mapped.get(key);var old=prior.get(key);
                if(id==null || apply&&id<=0 || old!=null&&!Objects.equals(old.targetId(),id))fail("OWNER_TARGET_CHANGED");
                var raw=rawRows.get(key);String json=raw.entrySet().stream().map(e->JsonUtils.toJsonString(e.getKey())+":"+(e.getValue()==null?"null":JsonUtils.toJsonString(e.getValue())))
                    .collect(java.util.stream.Collectors.joining(",","{","}"));
                var after=new LinkedHashMap<String,Object>();after.put("originalFields",JsonUtils.parseTree(json));after.put("projectId",reference.projectId());after.put("extensionDefinitionId",definitionId);
                changes.put(key,new Change(row.object(),row.sourceKey(),id<=0?null:id,old==null?"CREATED":"UNCHANGED",old==null?Map.of():old.lastFields(),after,"原批次与版本历史，不更新当前设备版本"));
            }
        }
        return batch.rows().stream().map(row->changes.get(row.object()+":"+row.sourceKey())).toList();
    }
    private static String sourceObject(String domain) {
        return switch(domain){case "software-changes"->BATCH;case "software-versions"->VERSION;default->throw new IllegalArgumentException("DPPMS_SOFTWARE_OWNER_DOMAIN_INVALID");};
    }
    private static Long positive(String s){long id=SyncFieldMapper.primaryKey(s);if(id<=0)fail("SOURCE_ID_INVALID");return id;}
    private static Integer integer(String s){return s==null?null:new java.math.BigDecimal(s).intValueExact();}
    private static LocalDateTime time(String s){return s==null?null:LocalDateTime.parse(s.replace(' ','T'));}
    private static LocalDate date(String s){return s==null?null:LocalDate.parse(s);}
    private static String text(Object v){return v==null || v instanceof tools.jackson.databind.JsonNode n&&n.isNull()?null:v.toString();}
    private static void fail(String code){throw new IllegalArgumentException("DPPMS_SOFTWARE_"+code);}
}
