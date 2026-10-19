package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.*;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Component;

/** Raw batches and immutable base observations; historical extension attributes are deferred. */
@Component @RequiredArgsConstructor
public class P35SoftwareHistorySyncTask extends AbstractSyncTask<P35SoftwareHistorySyncTask.Source,P35SoftwareHistorySyncTask.Parameters> {
 private final TargetMapper mapper;private final BaseSyncPublishedProjects publications;private final BaseSyncProjectReferences projects;
 public static final class Parameters {private BaseSyncPublishedProjects.Snapshot publication;private SyncSource.Session source;private final Map<Long,PlannedBatch> batches=new HashMap<>();}
 public record PlannedBatch(Long id,Long projectId){}
 @Override protected Parameters createParameters(SyncContext<?> c){return new Parameters();}
 @Override public String taskCode(){return "BASE_P35";}
 @Override public String taskName(){return "软件批次及版本历史";}
 @Override public void work(SyncContext<Parameters> c){if(c.preview())c.runState().warning("SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED");
  c.parameters().publication=publications.load(new BaseSyncPublishedProjects.Query(c.tenantId(),c.connectionId(),c.maxRows(),c.source(),c.preview()));
  c.runState().warning("P35_EXTENSION_ATTRIBUTES_DEFERRED:conp_bak,conp_change,boot_bak,boot_change,cpld_bak,cpld_change,pcb_bak,pcb_change");
  try(var source=c.source().open(c.connectionId())){c.parameters().source=source;syncData(c.forData("SoftwareBatch",false,true));syncData(c.forData("SoftwareObservation",false,true));}finally{c.parameters().source=null;}
 }
 public record ReadQuery(int limit,List<String> projectKeys){}
 @Override protected void syncDataQueryByRowHandler(SyncContext<Parameters> c,ResultHandler<Source> handler){var keys=new ArrayList<>(c.parameters().publication.members().keySet());Collections.sort(keys);for(int i=0;i<keys.size();i+=500)syncDataQueryBySql(c.parameters().source.sqlSession(),new MappedCall<>(c,sourceMapperNamespace()+".select"+c.dataName(),new ReadQuery(c.maxRows()+1,keys.subList(i,Math.min(i+500,keys.size())))),handler);}
 @Override protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){
  var c=b.context();boolean batch=c.dataName().equals("SoftwareBatch");var selected=new LinkedHashMap<String,BaseSyncPublishedProjects.Member>();
  for(var s:b.rows()){if(s.sourceId==null||s.sourceId<=0||s.originalProjectId==null)fail("SOURCE_IDENTITY_INVALID");var member=c.parameters().publication.members().get(s.originalProjectId.toString());if(member==null)fail("SOURCE_OUTSIDE_PUBLISHED_SCOPE");selected.put(member.sourceKey(),member);}
  var refs=projects.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(c.tenantId(),c.actorId(),List.copyOf(selected.values()),c.preview()));var keys=b.rows().stream().map(s->s.sourceId.toString()).toList();var bindings=c.identities().findByKeys(new SyncIdentityStore.Query(batch?"SOFTWARE_BATCH":"SOFTWARE_OBSERVATION",keys));
  var ids=bindings.values().stream().map(SyncIdentityStore.Identity::targetId).toList();var q=new TargetQuery(c.tenantId(),ids,c.preview());var batches=new HashMap<Long,BatchRow>();var observations=new HashMap<Long,ObservationRow>();if(!ids.isEmpty()){if(batch)mapper.selectBatches(q).forEach(r->batches.put(r.id,r));else mapper.selectObservations(q).forEach(r->observations.put(r.id,r));}
  for(var s:b.rows()){
   if(s.createTime==null||s.createTime.getNano()%1000000!=0||s.updateTime!=null&&s.updateTime.getNano()%1000000!=0)fail("ORIGINAL_AUDIT_REQUIRED_OR_PRECISION_INVALID");var p=refs.get(s.originalProjectId.toString());var binding=bindings.get(s.sourceId.toString());s.action=SyncWriteAction.INSERT;
   if(batch){
    if(s.latest!=null&&!Set.of(0,1).contains(s.latest))fail("LATEST_FLAG_INVALID");var row=new BatchRow();row.tenantId=c.tenantId();row.projectId=p.id();row.changeVersion=s.changeVersion;row.changeRemark=s.changeRemark;row.latest=s.latest;row.creator=s.resolvedCreator;row.updater=s.resolvedUpdater;row.createTime=s.createTime;row.updateTime=s.updateTime==null?s.createTime:s.updateTime;row.deleted=false;validateBatch(row);
    var old=binding==null?null:batches.get(binding.targetId());if(binding!=null){if(old==null||Boolean.TRUE.equals(old.deleted)||!binding.fingerprint().equals(batchFingerprint(old)))fail("EXISTING_BATCH_PROTECTED");row.id=old.id;if(!batchFingerprint(row).equals(batchFingerprint(old)))fail("BATCH_HISTORY_IMMUTABLE");s.action=SyncWriteAction.UNCHANGED;}else row.id=c.preview()?null:IdWorker.getId();s.batch=row;if(c.preview()&&(c.parameters().batches.putIfAbsent(s.sourceId,new PlannedBatch(row.id,row.projectId))!=null||c.parameters().batches.size()>c.maxRows()))fail("SOURCE_BATCH_DUPLICATE_OR_CAPACITY");
   }else{
    var row=new ObservationRow();row.tenantId=c.tenantId();row.projectId=p.id();row.deviceSn=s.barCode;row.contractNo=s.contractNo;row.productCode=s.itemCode;row.conpVersion=s.conp;row.conpType=s.conpType;row.conpSeries=s.conpSeries;row.conpMark=s.conpMark;row.bootVersion=s.boot;row.cpldVersion=s.cpld;row.pcbVersion=s.pcb;row.executeTime=s.executeTime;row.dataState=s.datastate;row.customInfo=s.customInfo;row.recordedAt=s.createTime;row.dataSource="DPPMS";row.creator=s.resolvedCreator;row.updater=s.resolvedUpdater;row.createTime=s.createTime;row.updateTime=s.updateTime==null?s.createTime:s.updateTime;row.deleted=false;
    if(s.logId!=null){var parent=c.parameters().batches.get(s.logId);if(parent==null||!Objects.equals(parent.projectId,p.id()))fail("ORIGINAL_BATCH_NOT_IN_SAME_PUBLISHED_PROJECT");row.changeBatchId=parent.id;}
    validateObservation(row);if(row.customInfo!=null)BaseSyncJson.canonicalJson(row.customInfo);var old=binding==null?null:observations.get(binding.targetId());
    if(binding!=null){if(old==null||Boolean.TRUE.equals(old.deleted))fail("BOUND_OBSERVATION_MISSING");row.id=old.id;if(!binding.fingerprint().equals(observationFingerprint(old)))fail("EXISTING_OBSERVATION_PROTECTED");if(!observationFingerprint(row).equals(observationFingerprint(old)))fail("OBSERVATION_HISTORY_IMMUTABLE");s.action=SyncWriteAction.UNCHANGED;}
    else row.id=c.preview()?null:IdWorker.getId();
    s.observation=row;
   }
  }
 }
 static void validateBatch(BatchRow r){checkTextLength(r.changeVersion,10);checkTextLength(r.changeRemark,255);checkTextLength(r.creator,64);checkTextLength(r.updater,64);}
static void validateObservation(ObservationRow r){checkTextLength(r.deviceSn,100);checkTextLength(r.conpVersion,255);checkTextLength(r.conpType,100);checkTextLength(r.conpSeries,100);checkTextLength(r.conpMark,255);checkTextLength(r.bootVersion,255);checkTextLength(r.cpldVersion,255);checkTextLength(r.pcbVersion,255);checkTextLength(r.versionDesc,1000);checkTextLength(r.dataSource,32);checkTextLength(r.requestKey,180);checkTextLength(r.contractNo,100);checkTextLength(r.productCode,25);checkTextLength(r.creator,64);checkTextLength(r.updater,64);}

 static String batchFingerprint(BatchRow r){return SyncFingerprint.of(Arrays.asList(r.id,r.tenantId,r.projectId,r.changeVersion,r.changeRemark,r.latest,r.creator,r.createTime,r.updater,r.updateTime,r.deleted));}
 static String observationFingerprint(ObservationRow r){var facts=new ArrayList<Object>(Arrays.asList(r.id,r.tenantId,r.deviceId,r.deviceSn,r.projectId,r.previousRecordId,r.conpVersion,r.conpType,r.conpSeries,r.conpMark,r.bootVersion,r.cpldVersion,r.pcbVersion,r.customized,r.releaseDate,r.versionDesc,r.recordedAt,r.dataSource,r.parsedFactId,r.requestKey,r.changeBatchId,r.contractNo,r.productCode,r.executeTime,r.dataState,BaseSyncJson.canonicalJson(r.customInfo),r.creator,r.createTime,r.updater,r.updateTime,r.deleted));return SyncFingerprint.of(facts);}
 static void fail(String code){throw new IllegalArgumentException("P35_"+code);}
 @Override protected SyncCounts syncDataCount(SyncBatch<Source,Parameters> b){return SyncCounts.fromActions(b.rows(),Source::getAction);}
 @Override protected void syncDataInsert(SyncBatch<Source,Parameters> b){var fresh=b.matching(s->s.action==SyncWriteAction.INSERT);super.syncDataInsert(fresh);}
 @Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){var saves=new ArrayList<SyncIdentityStore.Save>();for(var s:b.rows()){if(s.batch!=null){if(b.context().parameters().batches.putIfAbsent(s.sourceId,new PlannedBatch(s.batch.id,s.batch.projectId))!=null||b.context().parameters().batches.size()>b.context().maxRows())fail("SOURCE_BATCH_DUPLICATE_OR_CAPACITY");if(s.action==SyncWriteAction.INSERT)saves.add(new SyncIdentityStore.Save("SOFTWARE_BATCH",s.sourceId.toString(),s.batch.id,batchFingerprint(s.batch),null));}else if(s.action==SyncWriteAction.INSERT)saves.add(new SyncIdentityStore.Save("SOFTWARE_OBSERVATION",s.sourceId.toString(),s.observation.id,observationFingerprint(s.observation),null));}b.context().identities().saveAll(saves);}

 @lombok.Data public static class Source implements SyncReferences.SourceAudit {private Long sourceId,originalProjectId,logId;private String changeVersion,changeRemark,contractNo,itemCode,barCode,conp,conpType,conpSeries,conpMark,cpld,boot,pcb,customInfo,sourceCreator,sourceUpdater,resolvedCreator,resolvedUpdater;private Integer latest,datastate;private LocalDateTime createTime,updateTime;private java.time.LocalDate executeTime;private BatchRow batch;private ObservationRow observation;private SyncWriteAction action;public boolean usesAuditCatalogue(){return true;}public boolean requiresAuditReferences(){return true;}public boolean requiresResolvedAuditReferences(){return true;}}
 @lombok.Data public static class BatchRow{private Long id;private Long tenantId;private Long projectId;private String changeVersion;private String changeRemark;private Integer latest;private String creator;private LocalDateTime createTime;private String updater;private LocalDateTime updateTime;private Boolean deleted;}
@lombok.Data public static class ObservationRow{private Long id;private Long tenantId;private Long deviceId;private String deviceSn;private Long projectId;private Long previousRecordId;private String conpVersion;private String conpType;private String conpSeries;private String conpMark;private String bootVersion;private String cpldVersion;private String pcbVersion;private Boolean customized;private java.time.LocalDate releaseDate;private String versionDesc;private LocalDateTime recordedAt;private String dataSource;private Long parsedFactId;private String requestKey;private Long changeBatchId;private String contractNo;private String productCode;private java.time.LocalDate executeTime;private Integer dataState;private String customInfo;private String creator;private LocalDateTime createTime;private String updater;private LocalDateTime updateTime;private Boolean deleted;}

 public record TargetQuery(long tenantId,List<Long> ids,boolean preview){}
 public interface SourceMapper{void selectSoftwareBatch(ReadQuery query,ResultHandler<? super Source> handler);void selectSoftwareObservation(ReadQuery query,ResultHandler<? super Source> handler);}
 @Mapper public interface TargetMapper{List<BatchRow> selectBatches(@Param("query") TargetQuery query);List<ObservationRow> selectObservations(@Param("query") TargetQuery query);int insertSoftwareBatch(SyncBatch<Source,Parameters> batch);int insertSoftwareObservation(SyncBatch<Source,Parameters> batch);}
}
