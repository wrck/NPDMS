package cn.iocoder.yudao.module.pms.integration.basesync;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.util.*;

/** Run-consistent full type10 scope from this independent suite only; no original publications, tasks or bindings. */
@Component @RequiredArgsConstructor
public class BaseSyncPublishedProjects {
 private final ReferenceMapper mapper;
 public record Query(long tenantId,long connectionId,int maxRows,cn.iocoder.yudao.module.pms.integration.api.basesync.SyncSource source,boolean preview){
  public Query(long tenantId,long connectionId,int maxRows){this(tenantId,connectionId,maxRows,null,false);}
 }
 public record Member(String sourceKey,Long projectId,String projectType,String originalProjectCode,String projectCode,String fingerprint){}
 public record Completed(long runId,long readCount){}
 public record Page(long tenantId,long connectionId,long publicationId,String afterKey){}
 public record Snapshot(long publicationId,Map<String,Member> members,Map<Long,Member> canonical){public Snapshot{members=Map.copyOf(members);canonical=Map.copyOf(canonical);}}
 public Snapshot load(Query q){
  if(q.tenantId()<=0||q.connectionId()<=0||q.maxRows()<1)throw new IllegalArgumentException("PUBLISHED_PROJECT_SCOPE_QUERY_INVALID");
  if(q.preview())return preview(q);
  if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
    || !Objects.equals(org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel(),java.sql.Connection.TRANSACTION_REPEATABLE_READ))throw new IllegalStateException("INDEPENDENT_SCOPE_REQUIRES_REPEATABLE_READ_TRANSACTION");
  var pin=mapper.selectCompleted(q);
  if(pin==null||pin.runId()<=0)throw new IllegalStateException("COMPLETE_FORMAL_BASE_P16_SCOPE_REQUIRED");
  long masters=mapper.countMasters(new Page(q.tenantId(),q.connectionId(),pin.runId(),""));
  long expected=pin.readCount()-masters;
  if(masters<0||expected<0||masters>0&&expected<masters||expected>q.maxRows())throw new IllegalStateException("INDEPENDENT_SCOPE_RECEIPT_INVALID");
  var members=new HashMap<String,Member>();var canonical=new HashMap<Long,Member>();String after="";
  while(true){var page=mapper.selectPage(new Page(q.tenantId(),q.connectionId(),pin.runId(),after));if(page.isEmpty())break;
   for(var row:page){if(row.sourceKey()==null||!row.sourceKey().matches("[1-9][0-9]{0,18}")||row.projectId()==null||row.projectId()<=0||row.projectCode()==null||row.originalProjectCode()==null||!"10".equals(row.projectType())||members.putIfAbsent(row.sourceKey(),row)!=null||members.size()>q.maxRows())throw new IllegalStateException("PUBLISHED_SCOPE_IDENTITY_INVALID_OR_TOO_LARGE");
    var old=canonical.get(row.projectId());if(old!=null&&(!old.projectCode().equals(row.projectCode())||!old.projectType().equals(row.projectType())||!old.originalProjectCode().equals(row.originalProjectCode())))throw new IllegalStateException("PUBLISHED_SCOPE_PROJECT_IDENTITY_AMBIGUOUS");
    if(old==null||Long.parseLong(row.sourceKey())>Long.parseLong(old.sourceKey()))canonical.put(row.projectId(),row);
   }var next=page.getLast().sourceKey();if(next.compareTo(after)<=0)throw new IllegalStateException("PUBLISHED_SCOPE_CURSOR_DID_NOT_ADVANCE");after=next;
  }
  if(canonical.size()!=masters)throw new IllegalStateException("INDEPENDENT_SCOPE_MASTER_COUNT_DIFFERS");
  for(var row:members.values()){var master=canonical.get(row.projectId());if(!Objects.equals(row.fingerprint(),cn.iocoder.yudao.module.pms.integration.api.basesync.SyncFingerprint.of(Arrays.asList(Long.parseLong(row.sourceKey()),Long.parseLong(master.sourceKey()),row.projectType(),row.originalProjectCode(),row.projectId()))))throw new IllegalStateException("INDEPENDENT_SCOPE_SOURCE_OR_PROJECT_CHANGED");}
  if(members.size()!=expected)throw new IllegalStateException("INDEPENDENT_SCOPE_INCOMPLETE:"+members.size()+":"+expected);
  return new Snapshot(pin.runId(),members,canonical);
 }
 private Snapshot preview(Query q){
  if(q.source()==null)throw new IllegalArgumentException("PREVIEW_SOURCE_REQUIRED");
  var members=new HashMap<String,Member>();var canonical=new HashMap<Long,Member>();
  try(var source=q.source().open(q.connectionId())){
   for(var row:source.mapper(SourceMapper.class).selectPreviewScope(new ReadQuery(Math.addExact(q.maxRows(),1)))){
    if(row.sourceKey()==null||!row.sourceKey().matches("[1-9][0-9]{0,18}")||row.projectId()==null||row.projectId()<=0||!"10".equals(row.projectType())||row.projectCode()==null||row.originalProjectCode()==null)throw new IllegalStateException("SOURCE_PROJECT_SCOPE_INVALID");
    var member=new Member(row.sourceKey(),row.projectId(),row.projectType(),row.originalProjectCode(),row.projectCode(),cn.iocoder.yudao.module.pms.integration.api.basesync.SyncFingerprint.of(Arrays.asList(Long.parseLong(row.sourceKey()),row.projectId(),row.projectType(),row.originalProjectCode(),row.projectId())));
    if(members.putIfAbsent(member.sourceKey(),member)!=null||members.size()>q.maxRows())throw new IllegalStateException("SOURCE_PROJECT_SCOPE_DUPLICATE_OR_CAPACITY");
    var previous=canonical.get(member.projectId());if(previous!=null&&(!previous.projectCode().equals(member.projectCode())||!previous.projectType().equals(member.projectType())))throw new IllegalStateException("SOURCE_PROJECT_SCOPE_AMBIGUOUS");
    if(previous==null||Long.parseLong(member.sourceKey())>Long.parseLong(previous.sourceKey()))canonical.put(member.projectId(),member);
   }
  }
  // Zero identifies a read-only source snapshot, never a fabricated formal completion receipt.
  return new Snapshot(0,members,canonical);
 }
 public record ReadQuery(int limit){}
 public interface SourceMapper {List<Member> selectPreviewScope(ReadQuery query);}
 @Mapper public interface ReferenceMapper {
  Completed selectCompleted(@Param("query") Query query);
  long countMasters(@Param("query") Page query);
  List<Member> selectPage(@Param("query") Page query);
 }
}
