package cn.iocoder.yudao.module.pms.project.api.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectPartyDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class ProjectFactsMigrationService implements ProjectFactsMigrationApi {
    private final ProjectMigrationIdentityContextApi projects;
    private final ProjectPartyMapper parties;
    private final EntityExtensionApi extensions;
    static final List<String> PARTY_FIELDS=List.of("id","tenantId","projectId","partyRole","partyCode","partyName",
            "contactName","phone","sourceSystem","sourceTable","sourceRecordKey","effectiveFrom","effectiveTo","status",
            "version","creator","createTime","updater","updateTime","deleted");
    public static final String PARTY="pm_project_related_party", STATE="pm_project_state";
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Result> preview(Command command){return execute(command,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> apply(Command command){return execute(command,true);}
    private List<Result> execute(Command command,boolean apply) {
        if(command==null || command.identity()==null || command.entries()==null)fail("输入无效");
        if(apply)projects.lockAndRevalidate(command.identity());else projects.resolve(command.identity());
        var results=new ArrayList<Result>();var keys=new HashSet<String>();
        for(var entry:command.entries()) {
            if(entry==null || !keys.add(entry.object()+":"+entry.sourceKey()) || entry.originalFields()==null
                    || !Objects.equals(entry.originalFields().get("projectId"),command.identity().sourceProjectKey()))fail("项目或来源身份不同");
            if(PARTY.equals(entry.object()))results.add(party(command,entry,apply));
            else if(STATE.equals(entry.object()))results.add(state(command,entry,apply));
            else fail("不支持的业务主体");
        }
        return List.copyOf(results);
    }
    private Result party(Command command,Entry entry,boolean apply) {
        var identity=command.identity();var f=entry.originalFields();
        if(!f.keySet().equals(new HashSet<>(PARTY_COLUMNS)) || !Objects.equals(entry.sourceKey(),f.get("id"))
                || !entry.sourceKey().matches("[1-9][0-9]*") || !Set.of("10","20","30").contains(f.get("partyRole")))fail("相关方字段、角色或原键无效");
        var found=parties.selectMigrationSourceForUpdate(new ProjectPartyMapper.MigrationSourceQuery(identity.tenantId(),PARTY,entry.sourceKey()));
        if(found.size()>1)fail("相关方来源身份多义");
        var old=found.isEmpty()?null:found.getFirst();
        if(entry.targetId()!=null && (old==null || !Objects.equals(entry.targetId(),old.getId()))
                || old!=null && (!Objects.equals(old.getProjectId(),identity.projectId()) || Boolean.TRUE.equals(old.getDeleted())
                || entry.expectedFingerprint()==null || !entry.expectedFingerprint().equals(fingerprint(fields(old)))))fail("相关方已变化或缺少已核对前值");
        if(old==null && entry.expectedFingerprint()!=null)fail("相关方绑定目标不存在");
        var row=new ProjectPartyDO();row.setTenantId(identity.tenantId());row.setProjectId(identity.projectId());
        row.setPartyRole(f.get("partyRole"));row.setPartyCode(f.get("partyCode"));row.setPartyName(f.get("partyName"));
        row.setEffectiveFrom(date(f.get("effectiveFrom")));row.setEffectiveTo(date(f.get("effectiveTo")));
        row.setCreator(entry.creator());row.setCreateTime(date(f.get("createTime")));row.setUpdater(entry.updater());
        row.setUpdateTime(f.get("updateTime")==null?row.getCreateTime():date(f.get("updateTime")));
        row.setSourceSystem("DPPMS");row.setSourceTable(PARTY);row.setSourceRecordKey(entry.sourceKey());
        row.setDeleted(false);row.setStatus(old==null?"ACTIVE":old.getStatus());row.setVersion(old==null?0:old.getVersion());
        if(row.getCreateTime()==null || row.getUpdateTime()==null)fail("相关方历史日期缺失");
        if(old!=null){row.setId(old.getId());row.setContactName(old.getContactName());row.setPhone(old.getPhone());}
        String action=old==null?"CREATED":fields(old).equals(fields(row))?"UNCHANGED":"UPDATED";
        if(!"UNCHANGED".equals(action) && old!=null)row.setVersion(old.getVersion()+1);
        if(apply && !"UNCHANGED".equals(action)) {
            if(old==null)row.setId(IdWorker.getId());
            var write=new ProjectPartyMapper.MigrationWrite(row,entry.creator(),entry.updater());
            if((old==null?parties.insertMigrationParty(write):parties.updateMigrationParty(write))!=1)fail("相关方写入冲突");
            row.setCreator(write.creator());row.setUpdater(write.updater());
        }
        var values=fields(row);return new Result(PARTY,entry.sourceKey(),row.getId(),action,values,fingerprint(values));
    }
    private Result state(Command command,Entry entry,boolean apply) {
        var identity=command.identity();var f=entry.originalFields();
        if(!f.keySet().equals(new HashSet<>(STATE_COLUMNS)) || !Objects.equals(entry.sourceKey(),f.get("projectId"))
                || command.extensionDefinitionId()==null || entry.targetId()!=null && !Objects.equals(entry.targetId(),identity.projectId()))fail("状态原字段或扩展定义缺失");
        var target=EntityDataRef.current(new EntityRef(identity.tenantId(),"PRJ","project",identity.projectId()));
        var actor=new EntityActor(identity.tenantId(),identity.actorId(),"dppms-project-facts");
        var current=extensions.read(target,actor);
        var saved=new LinkedHashMap<String,Object>(current.fields());
        var before=stateFields(saved);
        if(entry.expectedFingerprint()!=null && !entry.expectedFingerprint().equals(fingerprint(before)))fail("状态扩展已被业务人员修改");
        if(entry.expectedFingerprint()==null && before.values().stream().anyMatch(v->!(v instanceof tools.jackson.databind.JsonNode n && n.isNull())))fail("既有状态扩展未批准接管");
        var after=new LinkedHashMap<String,Object>();
        after.put("shipment_status",nullable(f.get("shipmentState")));
        after.put("shipment_status_updated_at",nullable(f.get("shipmentTime")));
        after.put("execution_status",nullable(f.get("executionState")));
        after.put("execution_status_updated_at",nullable(f.get("executionStateTime")));
        if(current.definitionRevisionId()!=null && !Objects.equals(current.definitionRevisionId(),command.extensionDefinitionId()))fail("已有扩展定义不同，先合并正式定义");
        String action=before.equals(after)?"UNCHANGED":current.version()==0?"CREATED":"UPDATED";
        if(apply && !"UNCHANGED".equals(action)) {
            saved.putAll(after);
            saved.replaceAll((name,value)->value instanceof tools.jackson.databind.JsonNode n && n.isNull()?null:value);
            extensions.save(new EntityExtensionApi.Save(target,actor,identity.expectedProjectVersion(),current.version(),
                    command.extensionDefinitionId(),saved));
        }
        // Plan/approval cache fields deliberately do not enter lifecycle, task or approval completion facts.
        return new Result(STATE,entry.sourceKey(),identity.projectId(),action,Collections.unmodifiableMap(after),fingerprint(after));
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public Long configureDefinition(DefinitionRequest request) {
        if(request==null || !Objects.equals(request.tenantId(),TenantContextHolder.getRequiredTenantId()) || request.actorId()==null)fail("扩展定义租户或操作者无效");
        var actor=new EntityActor(request.tenantId(),request.actorId(),"project-status-definition");
        var fields=new ArrayList<EntityExtensionApi.Definition>();
        if(request.existingRevisionId()!=null)fields.addAll(extensions.definition(request.existingRevisionId(),
                new EntityRef(request.tenantId(),"PRJ","project",1L),actor).fields());
        var required=List.of(new EntityExtensionApi.Definition("shipment_status","发货状态",EntityField.Type.TEXT,false,20,List.of()),
                new EntityExtensionApi.Definition("shipment_status_updated_at","发货状态更新时间",EntityField.Type.DATETIME,false,null,List.of()),
                new EntityExtensionApi.Definition("execution_status","执行状态",EntityField.Type.TEXT,false,20,List.of()),
                new EntityExtensionApi.Definition("execution_status_updated_at","执行状态更新时间",EntityField.Type.DATETIME,false,null,List.of()));
        for(var field:required) {
            var existing=fields.stream().filter(f->f.code().equals(field.code())).toList();
            if(!existing.isEmpty() && !existing.getFirst().equals(field))fail("同名正式属性定义不同");
            if(existing.isEmpty())fields.add(field);
        }
        if(request.existingRevisionId()!=null && fields.size()==extensions.definition(request.existingRevisionId(),
                new EntityRef(request.tenantId(),"PRJ","project",1L),actor).fields().size())return request.existingRevisionId();
        return extensions.publishDefinition(request.tenantId(),"PRJ","project",fields,actor).id();
    }
    static Map<String,Object> stateFields(Map<String,Object> fields) {
        var result=new LinkedHashMap<String,Object>();
        for(String name:List.of("shipment_status","shipment_status_updated_at","execution_status","execution_status_updated_at"))
            result.put(name,nullable(fields.get(name)));return result;
    }
    static Map<String,Object> fields(ProjectPartyDO row) {
        var bean=new BeanWrapperImpl(row);var result=new LinkedHashMap<String,Object>();
        for(String name:PARTY_FIELDS){Object value=bean.getPropertyValue(name);result.put(name,nullable(value instanceof LocalDateTime time?time.toString():value));}
        return Collections.unmodifiableMap(result);
    }
    static Object nullable(Object value){return value==null?tools.jackson.databind.node.NullNode.getInstance():value;}
    static LocalDateTime date(String value){return value==null?null:LocalDateTime.parse(value.replace(' ','T'));}
    static String fingerprint(Map<String,Object> values) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JsonUtils.toJsonString(values).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static void fail(String message){throw new IllegalArgumentException("DPPMS项目资料："+message);}
}
