package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.*;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectScopedCodes;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

/** Complete scoped history is needed to choose the latest installation by original time, never source ID. */
@Component @RequiredArgsConstructor
public class P33InstallationSyncTask extends AbstractSyncTask<P33InstallationSyncTask.Source,P33InstallationSyncTask.Parameters> {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(P33InstallationSyncTask.class);
 private final TargetMapper mapper;private final BaseSyncPublishedProjects publications;private final BaseSyncProjectReferences projects;
 private final cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDeviceProjects projectAssignments;
 public static final class Parameters {private BaseSyncPublishedProjects.Snapshot publication;private BaseSyncProjectReferences.CurrentManageScope manageScope;private int installLocationCapacity;private long filteredBlankAddresses;}
 @Override protected Parameters createParameters(SyncContext<?> c){return new Parameters();}
 @Override public String taskCode(){return "BASE_P33";}
 @Override public String taskName(){return "设备安装历史";}
 @Override public void work(SyncContext<Parameters> c){if(c.preview())c.runState().warning("SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED");c.parameters().publication=publications.load(new BaseSyncPublishedProjects.Query(c.tenantId(),c.connectionId(),c.maxRows(),c.source(),c.preview()));c.parameters().manageScope=projects.currentManageScope(new BaseSyncProjectReferences.ManageScopeQuery(c.tenantId(),c.actorId()));syncData(c.forData("Installation",false,false));projectAssignments.refresh(c);if(c.parameters().filteredBlankAddresses>0)c.runState().warning("P33_BLANK_ADDRESS_FILTERED_AFTER_READ:"+c.parameters().filteredBlankAddresses);}
 @Override protected void syncDataBefore(SyncContext<Parameters> c){super.syncDataBefore(c);c.parameters().installLocationCapacity=mapper.installLocationCapacity();if(c.parameters().installLocationCapacity<1)fail("INSTALL_LOCATION_COLUMN_MISSING");}
 public record ReadQuery(int limit,List<String> projectKeys){}
 @Override protected List<Source> syncDataQuery(SyncContext<Parameters> c){
  var rows=new ArrayList<Source>();var keys=new ArrayList<>(c.parameters().publication.members().keySet());Collections.sort(keys);long rawRows=0;
  try(var source=c.source().open(c.connectionId())){for(int i=0;i<keys.size();i+=500){
   List<Source> page=syncDataQueryBySql(source.sqlSession(),new MappedCall<>(c,sourceMapperNamespace()+".selectInstallation",new ReadQuery(c.maxRows()+1,keys.subList(i,Math.min(i+500,keys.size())))));
   rawRows+=page.size();if(rawRows>c.maxRows())fail("SOURCE_CAPACITY");
   // MySQL TRIM handles ordinary spaces; use the same blank rule as validation for Unicode whitespace.
   for(var row:page){if(row.installAddress==null||row.installAddress.isBlank()){c.parameters().filteredBlankAddresses++;continue;}rows.add(row);}
  }}return rows;
 }
 @Override protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){
  var c=b.context();var selected=new LinkedHashMap<String,BaseSyncPublishedProjects.Member>();var sourceIds=new HashSet<Long>();
  long pendingLocations=0,pendingTransfers=0;
  for(var s:b.rows()){s.previewReferencePending=false;validate(s);if(!sourceIds.add(s.sourceId))fail("SOURCE_ID_DUPLICATE");var member=c.parameters().publication.members().get(s.originalProjectId.toString());if(member==null)fail("SOURCE_OUTSIDE_PUBLISHED_SCOPE");if(!Boolean.TRUE.equals(s.transferPending))selected.put(member.sourceKey(),member);
   int characters=s.installAddress.codePointCount(0,s.installAddress.length());
   if(characters>c.parameters().installLocationCapacity){if(!c.preview())fail("INSTALL_LOCATION_CAPACITY_REQUIRED:"+characters+">"+c.parameters().installLocationCapacity);s.previewReferencePending=true;pendingLocations++;log.info("P33_INSTALL_LOCATION_CAPACITY_PENDING sourceId={}, originalProjectId={}, characters={}, capacity={}",s.sourceId,s.originalProjectId,characters,c.parameters().installLocationCapacity);}
   if(Boolean.TRUE.equals(s.transferPending)){s.previewReferencePending=true;pendingTransfers++;log.info("P33_TRANSFER_DEFERRED sourceId={}, originalProjectId={}",s.sourceId,s.originalProjectId);}
  }
  var refs=new HashMap<String,BaseSyncProjectReferences.Identity>();var members=new ArrayList<>(selected.values());for(int i=0;i<members.size();i+=1000){try{refs.putAll(projects.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(c.tenantId(),c.actorId(),members.subList(i,Math.min(i+1000,members.size())),c.preview(),c.parameters().manageScope)));}catch(ServiceException error){throw new IllegalStateException("P33_PROJECT_REFERENCE_REJECTED:code="+error.getCode(),error);}}
  // Preserve the raw request key while MySQL applies the formal SN column's collation.
  var serials=b.rows().stream().map(s->s.barcode).distinct().toList();var equipment=new HashMap<String,Device>();var equipmentIds=new HashSet<Long>();
  for(int i=0;i<serials.size();i+=500)for(var d:mapper.selectDevices(new DeviceQuery(c.tenantId(),serials.subList(i,Math.min(i+500,serials.size())),c.preview()))){if(equipment.putIfAbsent(d.requestedSn,d)!=null)fail("DEVICE_SERIAL_AMBIGUOUS");equipmentIds.add(d.id);}
  long pendingDevices=0;var matchedProjects=new HashSet<Long>();for(var s:b.rows()){var d=equipment.get(s.barcode);s.projectId=Boolean.TRUE.equals(s.transferPending)?c.parameters().publication.members().get(s.originalProjectId.toString()).projectId():refs.get(s.originalProjectId.toString()).id();
   if(d==null){s.previewReferencePending=true;s.equipmentId=null;s.target=null;s.action=null;pendingDevices++;log.info("P33_FORMAL_DEVICE_PENDING sourceId={}, originalProjectId={}, sn={}",s.sourceId,s.originalProjectId,s.barcode);continue;}
   s.equipmentId=d.id;if(!s.previewReferencePending)matchedProjects.add(s.projectId);}
  var current=new HashMap<Long,Row>();var currentByDevice=new HashMap<Long,Row>();var codes=new HashMap<Long,List<String>>();var projectIds=new ArrayList<>(matchedProjects);var deviceIds=new ArrayList<>(equipmentIds);
  for(int i=0;i<Math.max(projectIds.size(),deviceIds.size());i+=500){var ps=i<projectIds.size()?projectIds.subList(i,Math.min(i+500,projectIds.size())):List.<Long>of();var ds=i<deviceIds.size()?deviceIds.subList(i,Math.min(i+500,deviceIds.size())):List.<Long>of();for(var row:mapper.selectExisting(new TargetQuery(c.tenantId(),ps,ds,c.maxRows()+1,c.preview()))){if(current.putIfAbsent(row.id,row)!=null)continue;if(current.size()>c.maxRows())fail("FORMAL_HISTORY_CAPACITY");codes.computeIfAbsent(row.projectId,k->new ArrayList<>()).add(row.code);if(row.currentEquipmentId!=null&&currentByDevice.putIfAbsent(row.equipmentId,row)!=null)fail("CURRENT_INSTALLATION_AMBIGUOUS");}}
  var existingTimes=new HashSet<InstallationTime>();for(var row:current.values())existingTimes.add(new InstallationTime(row.projectId,row.equipmentId,row.createTime));
  var bindings=new HashMap<String,SyncIdentityStore.Identity>();var keys=b.rows().stream().map(s->s.sourceId.toString()).toList();for(int i=0;i<keys.size();i+=500)bindings.putAll(c.identities().findByKeys(new SyncIdentityStore.Query("INSTALLATION_HISTORY",keys.subList(i,Math.min(i+500,keys.size())))));
  var latest=new HashMap<Long,Source>();var tiedLatestTimes=new HashSet<Long>();
  for(var s:b.rows())if(!s.previewReferencePending&&!bindings.containsKey(s.sourceId.toString())){
   var previousLatest=latest.get(s.equipmentId);
   if(previousLatest==null||previousLatest.createTime.isBefore(s.createTime)){latest.put(s.equipmentId,s);tiedLatestTimes.remove(s.equipmentId);}
   else if(previousLatest.createTime.equals(s.createTime))tiedLatestTimes.add(s.equipmentId);
  }
  var ordered=new ArrayList<Source>();latest.values().stream().sorted(Comparator.comparing(s->s.sourceId)).forEach(ordered::add);for(var s:b.rows())if(latest.get(s.equipmentId)!=s)ordered.add(s);
  for(var s:ordered){var binding=bindings.get(s.sourceId.toString());if(s.previewReferencePending){if(binding!=null)fail("EXISTING_INSTALLATION_PROTECTED");continue;}s.action=SyncWriteAction.UNCHANGED;
   if(binding!=null){var old=current.get(binding.targetId());if(old==null||Boolean.TRUE.equals(old.deleted)||!Objects.equals(old.projectId,s.projectId)||!Objects.equals(old.equipmentId,s.equipmentId)||!binding.fingerprint().equals(fingerprint(old,s)))fail("EXISTING_INSTALLATION_PROTECTED");s.target=old;continue;}
   var previous=currentByDevice.get(s.equipmentId);var newest=latest.get(s.equipmentId);var end=previous==null?newest.createTime:previous.effectiveFrom;
   // Equal original times do not identify a unique current installation. Carry every raw history.
   boolean isCurrent=previous==null&&newest==s&&!tiedLatestTimes.contains(s.equipmentId);
   if(previous!=null&&(end==null||!s.createTime.isBefore(end)))fail("EQUAL_OR_NEWER_TIME_REQUIRES_ADJUDICATION");
   if(existingTimes.contains(new InstallationTime(s.projectId,s.equipmentId,s.createTime)))fail("ORIGINAL_INSTALLATION_CANNOT_ADOPT");
   var row=new Row();row.tenantId=c.tenantId();row.projectId=s.projectId;row.equipmentId=s.equipmentId;row.code=ProjectScopedCodes.next(refs.get(s.originalProjectId.toString()).projectCode(),"AZ",codes.computeIfAbsent(s.projectId,k->new ArrayList<>()));codes.get(s.projectId).add(row.code);checkTextLength(row.code,64);row.id=c.preview()?null:IdWorker.getId();row.installLocation=s.installAddress;row.locationResolutionStatus="UNRESOLVED";row.locationSnapshot=s.locationSnapshot;checkTextCapacity(row.locationSnapshot);row.status=2;row.version=0;row.deleted=false;row.installTime=s.createTime;row.effectiveFrom=s.createTime;row.effectiveTo=isCurrent?null:end;row.creator=s.resolvedCreator;row.updater=s.resolvedUpdater;row.createTime=s.createTime;row.updateTime=s.updateTime==null?s.createTime:s.updateTime;row.currentEquipmentId=isCurrent?row.equipmentId:null;s.target=row;s.action=SyncWriteAction.INSERT;
  }
  b.rows().clear();b.rows().addAll(ordered);
  if(pendingLocations>0)c.runState().warning("P33_INSTALL_LOCATION_CAPACITY_PENDING:"+pendingLocations+":"+c.parameters().installLocationCapacity);if(pendingTransfers>0)c.runState().warning("P33_TRANSFER_DEFERRED:"+pendingTransfers);
  if(pendingDevices>0)c.runState().warning("P33_FORMAL_DEVICE_PENDING:"+pendingDevices);
  if(!tiedLatestTimes.isEmpty())c.runState().warning("P33_LATEST_ORIGINAL_TIME_TIED:"+tiedLatestTimes.size());
 }
 static void validate(Source s){if(s.sourceId==null||s.sourceId<=0||s.originalProjectId==null||s.barcode==null||s.barcode.isBlank()||s.installAddress==null||s.installAddress.isBlank()||s.createTime==null)fail("SOURCE_BASELINE_INVALID");if(s.createTime.getNano()!=0||s.updateTime!=null&&s.updateTime.getNano()!=0)fail("ORIGINAL_AUDIT_PRECISION_INVALID");}
 static String fingerprint(Row r,Source s){return SyncFingerprint.of(Arrays.asList(r.id,r.projectId,r.code,r.equipmentId,r.installLocation,r.addressId,r.addressVersion,r.siteId,r.siteVersion,r.siteLocationId,r.siteLocationVersion,r.locationResolutionStatus,BaseSyncJson.canonicalJson(r.addressSnapshot),BaseSyncJson.canonicalJson(r.locationSnapshot),r.effectiveFrom,r.effectiveTo,r.currentEquipmentId,r.installTime,r.installerUserId,r.environmentCheck,r.specCheck,r.photoUrl,r.result,r.status,r.remark,r.version,r.creator,r.createTime,r.updater,r.updateTime,r.deleted,r.tenantId,s.sourceId,s.originalProjectId,s.barcode,s.itemCode,s.itemModel,s.itemName,s.receiveName,s.emsNum,s.emsCompany,s.packdate,s.contractNo,s.installAddress,s.chProjectId,s.chContractNo,s.transferProjectId,s.transferContractNo,s.transferFlag,s.createTime,s.sourceCreator,s.updateTime,s.sourceUpdater));}
 static void fail(String code){throw new IllegalArgumentException("P33_"+code);}
 @Override protected SyncCounts syncDataCount(SyncBatch<Source,Parameters> b){var known=SyncCounts.fromActions(b.rows().stream().filter(s->!s.previewReferencePending).toList(),Source::getAction);return new SyncCounts(b.rows().size(),known.inserted(),known.updated(),known.unchanged(),known.deleted());}
 @Override protected void syncDataInsert(SyncBatch<Source,Parameters> b){
  var c=b.context();var fresh=b.rows().stream().filter(s->s.action==SyncWriteAction.INSERT).toList();
  for(int i=0;i<fresh.size();i+=c.batchSize())super.syncDataInsert(new SyncBatch<>(c,fresh.subList(i,Math.min(i+c.batchSize(),fresh.size()))));
 }
 @Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){var saves=b.rows().stream().filter(s->s.action==SyncWriteAction.INSERT).map(s->new SyncIdentityStore.Save("INSTALLATION_HISTORY",s.sourceId.toString(),s.target.id,fingerprint(s.target,s),null)).toList();b.context().identities().saveAll(saves);}
 @lombok.Data public static class Source implements SyncReferences.SourceAudit {private Long sourceId,originalProjectId,projectId,equipmentId;private String barcode,itemCode,itemModel,itemName,receiveName,emsNum,emsCompany,contractNo,installAddress,locationSnapshot,chProjectId,chContractNo,transferProjectId,transferContractNo,transferFlag;private Boolean transferPending;private LocalDateTime createTime,updateTime;private String packdate;private String sourceCreator,sourceUpdater,resolvedCreator,resolvedUpdater;private Row target;private SyncWriteAction action;private boolean previewReferencePending;public boolean usesAuditCatalogue(){return true;}public boolean requiresAuditReferences(){return true;}public boolean requiresResolvedAuditReferences(){return true;}}
 @lombok.Data public static class Row{private Long id;private Long projectId;private String code;private Long equipmentId;private String installLocation;private Long addressId;private Integer addressVersion;private Long siteId;private Integer siteVersion;private Long siteLocationId;private Integer siteLocationVersion;private String locationResolutionStatus;private String addressSnapshot;private String locationSnapshot;private LocalDateTime effectiveFrom;private LocalDateTime effectiveTo;private Long currentEquipmentId;private LocalDateTime installTime;private Long installerUserId;private String environmentCheck;private String specCheck;private String photoUrl;private String result;private Integer status;private String remark;private Integer version;private String creator;private LocalDateTime createTime;private String updater;private LocalDateTime updateTime;private Boolean deleted;private Long tenantId;}
 @lombok.Data public static class Device{private Long id;private String sn,requestedSn;}
 public record DeviceQuery(long tenantId,List<String> serials,boolean preview){}
 public record TargetQuery(long tenantId,List<Long> projectIds,List<Long> equipmentIds,int limit,boolean preview){}
 private record InstallationTime(Long projectId,Long equipmentId,LocalDateTime createTime){}
 public interface SourceMapper{List<Source> selectInstallation(ReadQuery query);}
 @Mapper public interface TargetMapper{@com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine="true",dataPermission="true") int installLocationCapacity();List<Device> selectDevices(@Param("query") DeviceQuery query);List<Row> selectExisting(@Param("query") TargetQuery query);int insertInstallation(SyncBatch<Source,Parameters> batch);}
}
