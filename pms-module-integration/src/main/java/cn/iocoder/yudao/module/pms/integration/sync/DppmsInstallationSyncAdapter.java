package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.engineering.api.installation.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** INT resolves published identities; IMP performs the existing historical carry and AST location rules. */
@Component @RequiredArgsConstructor
public class DppmsInstallationSyncAdapter implements DataSyncAdapter {
    static final String ADAPTER="DPPMS_INSTALLATION_HISTORY",OBJECT="INSTALLATION_HISTORY";
    private final SyncOwnerRunContext context;
    private final DppmsPublishedProjectScopeApi published;
    private final ProjectMasterMigrationApi projects;
    private final SyncBindingMapper bindings;
    private final InstallationHistoryInsertApi installations;
    @Override public boolean supportsStreaming(){return true;}
    @Override public boolean requiresAllBindings(){return false;}
    @Override public List<String> writeTables(){return List.of("imp_eng_installation","ast_address","ast_device_location","ast_device");}
    @Override public Descriptor descriptor() {
        var fields=new ArrayList<Field>();
        DppmsInstallationRecordMigrationApi.ORIGINAL_COLUMNS.forEach(n->fields.add(new Field(n,n,"STRING",false)));
        fields.add(new Field("originalProjectCode","原项目编码","STRING",true));fields.add(new Field("projectType","原项目类型","STRING",true));
        return new Descriptor(ADAPTER,"DPPMS普通安装历史首次迁入",List.of(new ObjectDescriptor(OBJECT,"原安装记录",fields,
                "IMP","Installation","imp_eng_installation",false)),List.of("RETAIN"),List.of("UPSERT"),false,true);
    }
    @Override public void refreshCaches() {}
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Change> preview(Batch batch){return execute(batch,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Change> apply(Batch batch){return execute(batch,true);}
    private List<Change> execute(Batch batch,boolean apply) {
        var run=context.require(new SyncOwnerRunContext.Request(batch,ADAPTER,apply));
        DppmsInstallationStream.validate(run.definition());
        if(batch.rows().isEmpty())return List.of();
        var previous=new HashMap<String,Binding>();
        for(var binding:batch.bindings())if(!OBJECT.equals(binding.object()) || previous.put(binding.sourceKey(),binding)!=null)fail("BINDING_DUPLICATE");
        var rawRows=new LinkedHashMap<String,Map<String,String>>();var projectKeys=new LinkedHashSet<String>();var sns=new HashSet<String>();
        for(var row:batch.rows()) {
            if(!OBJECT.equals(row.object()) || !row.fields().keySet().containsAll(DppmsInstallationRecordMigrationApi.ORIGINAL_COLUMNS))fail("RAW_COLUMNS_MISSING");
            var raw=new LinkedHashMap<String,String>();
            DppmsInstallationRecordMigrationApi.ORIGINAL_COLUMNS.forEach(n->raw.put(n,text(row.fields().get(n))));
            if(!Objects.equals(row.sourceKey(),raw.get("id")) || rawRows.put(row.sourceKey(),raw)!=null)fail("SOURCE_ID_INVALID");
            var prior=previous.get(row.sourceKey());
            if(prior==null && row.targetId()!=null || prior!=null && (!Objects.equals(row.targetId(),prior.targetId())
                    || !(prior.lastFields().get("originalFields") instanceof Map<?,?> original)
                    || !original.keySet().equals(raw.keySet()) || raw.keySet().stream().anyMatch(n->!Objects.equals(raw.get(n),text(original.get(n))))))fail("BOUND_SOURCE_CHANGED");
            projectKeys.add(raw.get("projectId"));sns.add(raw.get("barcode"));
        }
        if(sns.size()!=1 || sns.contains(null) || sns.iterator().next().isBlank())fail("COMPLETE_DEVICE_HISTORY_REQUIRED");
        var scope=published.resolve(new DppmsPublishedProjectScopeApi.Query(run.tenantId(),run.task().getConnectionId(),List.copyOf(projectKeys)));
        var references=new HashMap<String,DppmsProjectBindingApi.ProjectBinding>();
        for(var project:scope.projects())if(!projectKeys.contains(project.sourceProjectKey()) || references.put(project.sourceProjectKey(),project)!=null)fail("PROJECT_SCOPE_AMBIGUOUS");
        if(!references.keySet().equals(projectKeys))fail("PROJECT_SCOPE_CHANGED");
        var targetIds=references.values().stream().map(DppmsProjectBindingApi.ProjectBinding::projectId).collect(java.util.stream.Collectors.toSet());
        if(targetIds.size()!=1)fail("DEVICE_PROJECT_CONFLICT");
        Long projectId=targetIds.iterator().next();
        for(var row:batch.rows()) {
            var project=references.get(rawRows.get(row.sourceKey()).get("projectId"));
            if(!Objects.equals(project.originalProjectCode(),text(row.fields().get("originalProjectCode")))
                    || !Objects.equals(project.projectType(),text(row.fields().get("projectType"))))fail("PROJECT_IDENTITY_CHANGED");
        }
        var project=references.values().iterator().next();
        var facts=projects.inspectBindings(new ProjectMasterMigrationApi.IdentityInspection(run.tenantId(),run.actorId(),
                List.of(new ProjectMasterMigrationApi.IdentityTarget(projectId,project.projectType(),project.projectCode()))));
        if(facts.size()!=1 || !Objects.equals(facts.getFirst().id(),projectId))fail("PROJECT_UNRESOLVED");
        projects.lockBindings(new ProjectMasterMigrationApi.BindingVerification(run.tenantId(),run.actorId(),facts));
        var devices=bindings.selectIdentitySources(new SyncQueries.IdentitySources(run.tenantId(),"DPPMS",run.task().getConnectionId(),
                DppmsDeviceBootstrapSyncTemplate.ADAPTER,"ast_device_shipment",List.copyOf(sns)).originalLine());
        var deviceIds=new HashSet<Long>();
        for(var device:devices) {
            if(!sns.contains(device.getSourceKey()) || device.getTargetId()==null || device.getTargetId()<=0)fail("DEVICE_BINDING_INVALID");
            deviceIds.add(device.getTargetId());
        }
        if(deviceIds.size()!=1)fail("DEVICE_BINDING_UNRESOLVED");
        long equipmentId=deviceIds.iterator().next();
        var existing=new ArrayList<InstallationHistoryInsertApi.Existing>();var fresh=new ArrayList<InstallationHistoryInsertApi.Row>();
        for(var row:batch.rows()) {
            var prior=previous.get(row.sourceKey());
            if(prior==null)fresh.add(new InstallationHistoryInsertApi.Row(row.sourceKey(),equipmentId,rawRows.get(row.sourceKey())));
            else {
                if(!Objects.equals(text(prior.lastFields().get("projectId")),projectId.toString())
                        || !Objects.equals(text(prior.lastFields().get("equipmentId")),Long.toString(equipmentId)))fail("BOUND_TARGET_CHANGED");
                existing.add(new InstallationHistoryInsertApi.Existing(row.sourceKey(),prior.targetId(),equipmentId,text(prior.lastFields().get("formalFieldsJson"))));
            }
        }
        var results=new LinkedHashMap<String,InstallationHistoryInsertApi.Result>();
        if(!existing.isEmpty())for(var result:installations.verify(new InstallationHistoryInsertApi.Verification(run.tenantId(),run.actorId(),projectId,existing)))
            if(results.put(result.sourceKey(),result)!=null)fail("OWNER_RESULT_DUPLICATE");
        if(!fresh.isEmpty()) {
            var command=new InstallationHistoryInsertApi.Command(run.tenantId(),run.actorId(),projectId,fresh);
            for(var result:apply?installations.insert(command):installations.preview(command))if(results.put(result.sourceKey(),result)!=null)fail("OWNER_RESULT_DUPLICATE");
        }
        if(!results.keySet().equals(rawRows.keySet()))fail("OWNER_RESULT_INCOMPLETE");
        var changes=new ArrayList<Change>();
        for(var row:batch.rows()) {
            var result=results.get(row.sourceKey());var prior=previous.get(row.sourceKey());
            if(prior!=null&&!Objects.equals(prior.targetId(),result.installationId()) || apply&&(result.installationId()==null||result.installationId()<=0))fail("OWNER_TARGET_INVALID");
            var after=new LinkedHashMap<String,Object>();
            String original=rawRows.get(row.sourceKey()).entrySet().stream().map(entry->
                    cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(entry.getKey())+":"+
                    (entry.getValue()==null?"null":cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(entry.getValue())))
                    .collect(java.util.stream.Collectors.joining(",","{","}"));
            after.put("originalFields",cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(original));
            after.put("projectId",projectId);after.put("equipmentId",equipmentId);
            after.put("formalFieldsJson",result.formalFieldsJson());
            changes.add(new Change(OBJECT,row.sourceKey(),prior==null&&!apply?null:result.installationId(),prior==null?"CREATED":"UNCHANGED",
                    prior==null?Map.of():prior.lastFields(),after,"复用正式安装历史和缺失当前位置规则"));
        }
        return List.copyOf(changes);
    }
    private static String text(Object value){return value==null || value instanceof tools.jackson.databind.JsonNode node&&node.isNull()?null:value.toString();}
    private static void fail(String code){throw new IllegalArgumentException("DPPMS_INSTALLATION_"+code);}
}
