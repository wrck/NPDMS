package cn.iocoder.yudao.module.pms.integration.basesync;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.util.*;

/** Source project IDs are target IDs; actual existing parents still require current manage scope. */
@Component @RequiredArgsConstructor
public class BaseSyncProjectReferences {
 private final ReferenceMapper mapper;private final ProjectScopeApi scope;
 public record Query(long tenantId,long actorId,long connectionId,List<String> canonicalKeys,boolean preview){}
 public record Identity(String sourceKey,Long id,Long tenantId,Long version,String projectCode,String projectType,
                        Long companyId,String companyCode,String companyName,String lifecycleStatus,Boolean deleted){}
 public Map<String,Identity> resolve(Query q){
  if(q.tenantId()<=0 || q.actorId()<=0 || q.connectionId()<=0 || q.canonicalKeys()==null || q.canonicalKeys().size()>2000 || q.canonicalKeys().stream().anyMatch(k->k==null || !k.matches("[1-9][0-9]*")))throw new IllegalArgumentException("PROJECT_REFERENCE_QUERY_INVALID");
  if(q.canonicalKeys().isEmpty())return Map.of();
  var result=new HashMap<String,Identity>();var authorized=new HashSet<Long>();
  var rows=mapper.selectCurrentProjects(new ProjectIdsQuery(q.tenantId(),q.canonicalKeys().stream().map(Long::valueOf).toList(),q.preview()));
  for(var row:rows){
   if(!q.canonicalKeys().contains(row.sourceKey()) || row.tenantId()!=q.tenantId() || Boolean.TRUE.equals(row.deleted()) || !Set.of("10","afss","afxx").contains(row.projectType()) || !Set.of("ACTIVE","NORMAL_CLOSED","EXCEPTION_CLOSED").contains(Objects.toString(row.lifecycleStatus(),"")))throw new IllegalArgumentException("PROJECT_FORMAL_IDENTITY_INVALID");
   var old=result.putIfAbsent(row.sourceKey(),row);if(old!=null && !old.equals(row))throw new IllegalArgumentException("PROJECT_FORMAL_IDENTITY_AMBIGUOUS");
   if(authorized.add(row.id())){
    var current=scope.resolveCurrent(new ProjectCurrentScopeQuery(q.tenantId(),q.actorId(),row.id(),ProjectScopeApi.ACTION_MANAGE));
    if(current==null || current.fullProjectIds()==null || !current.fullProjectIds().contains(row.id()))throw new org.springframework.security.access.AccessDeniedException("PROJECT_MANAGE_SCOPE_MISSING:"+row.id());
   }
  }
  var missing=q.canonicalKeys().stream().distinct().filter(k->!result.containsKey(k)).limit(12).toList();
  if(!q.preview()&&!missing.isEmpty())throw new IllegalStateException("FORMAL_PROJECT_PARENT_MISSING:"+String.join(",",missing));
  if(q.preview())for(var key:q.canonicalKeys())if(!result.containsKey(key))result.put(key,new Identity(key,Long.valueOf(key),q.tenantId(),null,null,"10",null,null,null,null,false));
  return Map.copyOf(result);
 }

 public record PublishedQuery(long tenantId,long actorId,List<BaseSyncPublishedProjects.Member> members,boolean preview){}
 public record ProjectIdsQuery(long tenantId,List<Long> ids,boolean preview){}
 public Map<String,Identity> verifyPublished(PublishedQuery q){
  if(q.tenantId()<=0||q.actorId()<=0||q.members()==null||q.members().size()>2000)throw new IllegalArgumentException("PUBLISHED_PROJECT_QUERY_INVALID");
  if(q.members().isEmpty())return Map.of();var byId=new HashMap<Long,Identity>();
  var ids=q.members().stream().map(BaseSyncPublishedProjects.Member::projectId).distinct().toList();
  for(var row:mapper.selectCurrentProjects(new ProjectIdsQuery(q.tenantId(),ids,q.preview()))){if(row.tenantId()!=q.tenantId()||Boolean.TRUE.equals(row.deleted())||!Set.of("ACTIVE","NORMAL_CLOSED","EXCEPTION_CLOSED").contains(Objects.toString(row.lifecycleStatus(),""))||byId.putIfAbsent(row.id(),row)!=null)throw new IllegalStateException("PUBLISHED_PROJECT_CURRENT_IDENTITY_INVALID");
   var current=scope.resolveCurrent(new ProjectCurrentScopeQuery(q.tenantId(),q.actorId(),row.id(),ProjectScopeApi.ACTION_MANAGE));if(current==null||current.fullProjectIds()==null||!current.fullProjectIds().contains(row.id()))throw new org.springframework.security.access.AccessDeniedException("PROJECT_MANAGE_SCOPE_MISSING:"+row.id());
  }
  var result=new HashMap<String,Identity>();for(var member:q.members()){var p=byId.get(member.projectId());
   if(p==null&&q.preview())p=new Identity(member.sourceKey(),member.projectId(),q.tenantId(),null,member.projectCode(),member.projectType(),null,null,null,null,false);
   if(p==null||!Objects.equals(p.projectCode(),member.projectCode())||!Objects.equals(p.projectType(),member.projectType()))throw new IllegalStateException("PUBLISHED_PROJECT_CURRENT_IDENTITY_CHANGED");result.put(member.sourceKey(),p);}return Map.copyOf(result);
 }
 @Mapper public interface ReferenceMapper {
  List<Identity> selectCurrentProjects(@Param("query") ProjectIdsQuery query);
  List<Identity> selectFormal(@Param("query") Query query);
  List<Identity> selectIndependent(@Param("query") Query query);
 }
}
