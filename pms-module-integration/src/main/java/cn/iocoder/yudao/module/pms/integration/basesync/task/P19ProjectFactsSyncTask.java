package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncProjectReferences;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Component;

/** Direct project parties; historical extension attributes are deferred by the source-owner decision. */
@Component @RequiredArgsConstructor
public class P19ProjectFactsSyncTask extends AbstractSyncTask<P19ProjectFactsSyncTask.Source,Void> {
 private final TargetMapper mapper;private final BaseSyncProjectReferences projects;
 @Override public String taskCode(){return "BASE_P19";}
 @Override public String taskName(){return "项目相关方及历史状态属性";}
 @Override public void work(SyncContext<Void> c){if(c.preview())c.runState().warning("SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED");c.runState().warning("P19_EXTENSION_ATTRIBUTES_DEFERRED:shipment_status,shipment_status_updated_at,execution_status,execution_status_updated_at");syncData(c.forData("ProjectParty",false,true));}
 @Override protected void syncDataInsertBefore(SyncBatch<Source,Void> b){
  var c=b.context();var keys=b.rows().stream().map(s->s.sourceId.toString()).toList();var bindings=c.identities().findByKeys(new SyncIdentityStore.Query("PROJECT_PARTY",keys));
  var refs=projects.resolve(new BaseSyncProjectReferences.Query(c.tenantId(),c.actorId(),c.connectionId(),b.rows().stream().map(s->s.canonicalProjectId.toString()).distinct().toList(),c.preview()));
  var current=new HashMap<String,PartyRow>();
  for(var old:mapper.selectParties(new PartiesQuery(c.tenantId(),keys,c.preview())))if(!keys.contains(old.sourceRecordKey)||current.putIfAbsent(old.sourceRecordKey,old)!=null)fail("PARTY_SOURCE_AMBIGUOUS");
  for(var s:b.rows()){
   if(s.sourceId==null || s.sourceId<=0 || !"10".equals(s.projectType))fail("SOURCE_IDENTITY_INVALID");var p=refs.get(s.canonicalProjectId.toString());if(!s.projectType.equals(p.projectType()))fail("PROJECT_TYPE_CHANGED");
   if(p.version()!=null&&!Objects.equals(p.projectCode(),s.originalProjectCode))fail("PROJECT_CODE_DIFFERS_FROM_SOURCE");
   var binding=bindings.get(s.sourceId.toString());s.previous=binding==null?null:binding.fingerprint();s.projectId=p.id();s.projectVersion=p.version();s.action=SyncWriteAction.UNCHANGED;
   if(!Set.of("10","20","30").contains(Objects.toString(s.partyRole,"")))fail("PARTY_ROLE_UNMAPPED");
   if(s.createTime==null || s.effectiveFrom!=null&&s.effectiveTo!=null&&s.effectiveTo.isBefore(s.effectiveFrom))fail("ORIGINAL_AUDIT_OR_INTERVAL_MISSING");
   checkTextLength(s.partyCode,128);checkTextLength(s.partyName,1024);
   var old=current.get(s.sourceId.toString());if(old!=null&&(binding==null||binding.targetId()!=old.id||Boolean.TRUE.equals(old.deleted)||!binding.fingerprint().equals(fingerprint(old))))fail("EXISTING_PARTY_PROTECTED");if(old==null&&binding!=null)fail("BOUND_PARTY_MISSING");
   var row=new PartyRow();if(old!=null)org.springframework.beans.BeanUtils.copyProperties(old,row);
   row.tenantId=c.tenantId();row.projectId=p.id();row.partyRole=s.partyRole;row.partyCode=s.partyCode;row.partyName=s.partyName;row.effectiveFrom=s.effectiveFrom;row.effectiveTo=s.effectiveTo;
   row.creator=s.resolvedCreator;row.updater=s.resolvedUpdater;row.createTime=s.createTime;row.updateTime=s.updateTime==null?s.createTime:s.updateTime;row.sourceSystem="DPPMS";row.sourceTable="pm_project_related_party";row.sourceRecordKey=s.sourceId.toString();
   if(old==null){row.id=c.preview()?null:IdWorker.getId();row.status="ACTIVE";row.version=0;row.deleted=false;s.action=SyncWriteAction.INSERT;}
   else if(!fingerprint(old).equals(fingerprint(row))){row.version=Math.addExact(old.version,1);s.expectedVersion=old.version;s.action=SyncWriteAction.UPDATE;}
   s.target=row;
  }
 }
 static String fingerprint(PartyRow r){return SyncFingerprint.of(Arrays.asList(r.id,r.tenantId,r.projectId,r.partyRole,r.partyCode,r.partyName,r.contactName,r.phone,r.sourceSystem,r.sourceTable,r.sourceRecordKey,r.effectiveFrom,r.effectiveTo,r.status,r.version,r.creator,r.createTime,r.updater,r.updateTime,r.deleted));}
 static void fail(String code){throw new IllegalArgumentException("P19_"+code);}
 @Override protected SyncCounts syncDataCount(SyncBatch<Source,Void> b){return SyncCounts.fromActions(b.rows(),Source::getAction);}
 @Override protected void syncDataInsert(SyncBatch<Source,Void> b){super.syncDataInsert(b.matching(s->s.action==SyncWriteAction.INSERT));super.syncDataUpdate(b.matching(s->s.action==SyncWriteAction.UPDATE));}
 @Override protected void syncDataInsertAfter(SyncBatch<Source,Void> b){b.context().identities().saveAll(b.rows().stream().filter(s->s.action!=SyncWriteAction.UNCHANGED).map(s->new SyncIdentityStore.Save("PROJECT_PARTY",s.sourceId.toString(),s.target.id,fingerprint(s.target),s.previous)).toList());}
 @lombok.Data public static class Source implements SyncReferences.SourceAudit {
  private Long sourceId,originalProjectId,canonicalProjectId,projectId,projectVersion;private String originalProjectCode,projectType,partyRole,partyCode,partyName,sourceCreator,sourceUpdater,resolvedCreator,resolvedUpdater,previous;private LocalDateTime createTime,updateTime,effectiveFrom,effectiveTo;private PartyRow target;private SyncWriteAction action;private Integer expectedVersion;
  public boolean usesAuditCatalogue(){return true;}public boolean requiresAuditReferences(){return true;}
 }
 @lombok.Data public static class PartyRow {private Long id;private Long tenantId;private Long projectId;private String partyRole;private String partyCode;private String partyName;private String contactName;private String phone;private String sourceSystem;private String sourceTable;private String sourceRecordKey;private LocalDateTime effectiveFrom;private LocalDateTime effectiveTo;private String status;private Integer version;private String creator;private LocalDateTime createTime;private String updater;private LocalDateTime updateTime;private Boolean deleted;}
 public record PartiesQuery(long tenantId,List<String> keys,boolean preview){}
 public interface SourceMapper {void selectProjectParty(SourceQuery<Void> query,ResultHandler<? super Source> handler);}
 @Mapper public interface TargetMapper {List<PartyRow> selectParties(@Param("query") PartiesQuery query);int insertProjectParty(SyncBatch<Source,Void> batch);int updateProjectParty(RowParameter<Source,Void> row);}
}
