package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublishedManageScopeBatchTest {
 private BaseSyncProjectReferences.ReferenceMapper mapper;
 private ProjectScopeApi owner;
 private BaseSyncProjectReferences references;
 @BeforeEach void setup(){
  mapper=mock(BaseSyncProjectReferences.ReferenceMapper.class);owner=mock(ProjectScopeApi.class);
  references=new BaseSyncProjectReferences(mapper,owner);
  when(mapper.selectCurrentProjects(any())).thenAnswer(call->{
   var q=call.<BaseSyncProjectReferences.ProjectIdsQuery>getArgument(0);
   return q.ids().stream().map(PublishedManageScopeBatchTest::identity).toList();
  });
  when(mapper.selectCurrentTreeProjectIds(any())).thenAnswer(call->call.<BaseSyncProjectReferences.ProjectIdsQuery>getArgument(0).ids());
 }
 private static BaseSyncProjectReferences.Identity identity(long id){
  return new BaseSyncProjectReferences.Identity(Long.toString(id),id,1L,0L,"P-"+id,"10",null,null,null,"ACTIVE",false);
 }
 private static BaseSyncPublishedProjects.Member member(long id){
  return new BaseSyncPublishedProjects.Member(Long.toString(id),id,"10","P-"+id,"P-"+id,null);
 }
 private BaseSyncProjectReferences.CurrentManageScope context(Set<Long> ids){
  when(owner.resolveAllCurrent(any())).thenReturn(ids);
  return references.currentManageScope(new BaseSyncProjectReferences.ManageScopeQuery(1,2));
 }
 @Test void ownerManageIsFetchedOnceAndReusedAcrossBatchesWithoutPerProjectCalls(){
  var mutable=new HashSet<>(Set.of(10L,20L));var scope=context(mutable);mutable.clear();
  for(long id:List.of(10L,20L,10L))assertEquals(id,references.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(1,2,List.of(member(id)),false,scope)).get(Long.toString(id)).id());
  verify(owner).resolveAllCurrent(new ProjectAllScopeQuery(1L,2L,ProjectScopeApi.ACTION_MANAGE));
  verify(owner,never()).resolveCurrent(any());
  verify(mapper,times(3)).selectCurrentProjects(any());verify(mapper,times(3)).selectCurrentTreeProjectIds(any());
 }
 @Test void emptyOwnerScopeCannotAuthorizeExistingProject(){
  var scope=context(Set.of());
  assertThrows(AccessDeniedException.class,()->references.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(1,2,List.of(member(10)),false,scope)));
 }
 @Test void noTrackingHistorySupportsBothReferencePathsButStillRequiresManageScope(){
  doReturn(List.of(new BaseSyncProjectReferences.Identity("10",10L,1L,0L,"P-10","10",null,null,null,"NO_TRACKING_CLOSED",false))).when(mapper).selectCurrentProjects(any());
  var allowed=context(Set.of(10L));
  assertEquals("NO_TRACKING_CLOSED",references.resolve(new BaseSyncProjectReferences.Query(1,2,3,List.of("10"),false,allowed)).get("10").lifecycleStatus());
  assertEquals("NO_TRACKING_CLOSED",references.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(1,2,List.of(member(10)),false,allowed)).get("10").lifecycleStatus());
  var denied=context(Set.of());
  assertThrows(AccessDeniedException.class,()->references.resolve(new BaseSyncProjectReferences.Query(1,2,3,List.of("10"),false,denied)));
  assertThrows(AccessDeniedException.class,()->references.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(1,2,List.of(member(10)),false,denied)));
 }
 @Test void runContextCannotCrossTenantOrActor(){
  var scope=context(Set.of(10L));
  assertThrows(AccessDeniedException.class,()->references.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(2,2,List.of(member(10)),false,scope)));
  assertThrows(AccessDeniedException.class,()->references.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(1,3,List.of(member(10)),false,scope)));
  verifyNoInteractions(mapper);
 }
 @Test void currentTreeIsRecheckedForEachBatch(){
  var scope=context(Set.of(10L));
  assertEquals(1,references.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(1,2,List.of(member(10)),false,scope)).size());
  doReturn(List.of()).when(mapper).selectCurrentTreeProjectIds(any());
  assertThrows(AccessDeniedException.class,()->references.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(1,2,List.of(member(10)),false,scope)));
 }
 @Test void currentIdentityCannotChangeAfterRunScopeWasObtained(){
  var scope=context(Set.of(10L));
  doReturn(List.of(new BaseSyncProjectReferences.Identity("10",10L,1L,1L,"RENAMED","10",null,null,null,"ACTIVE",false))).when(mapper).selectCurrentProjects(any());
  assertThrows(IllegalStateException.class,()->references.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(1,2,List.of(member(10)),false,scope)));
 }
 @Test void currentIdentityCannotCrossTenantOrBecomeDeleted(){
  var scope=context(Set.of(10L));
  doReturn(List.of(new BaseSyncProjectReferences.Identity("10",10L,2L,0L,"P-10","10",null,null,null,"ACTIVE",false))).when(mapper).selectCurrentProjects(any());
  assertThrows(IllegalStateException.class,()->references.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(1,2,List.of(member(10)),false,scope)));
  doReturn(List.of(new BaseSyncProjectReferences.Identity("10",10L,1L,0L,"P-10","10",null,null,null,"ACTIVE",true))).when(mapper).selectCurrentProjects(any());
  assertThrows(IllegalStateException.class,()->references.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(1,2,List.of(member(10)),false,scope)));
 }
 @Test void laterRunObtainsFreshOwnerScopeRatherThanPermanentCachedAuthorization(){
  when(owner.resolveAllCurrent(any())).thenReturn(Set.of(10L),Set.of());
  var first=references.currentManageScope(new BaseSyncProjectReferences.ManageScopeQuery(1,2));
  assertEquals(1,references.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(1,2,List.of(member(10)),false,first)).size());
  var second=references.currentManageScope(new BaseSyncProjectReferences.ManageScopeQuery(1,2));
  assertThrows(AccessDeniedException.class,()->references.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(1,2,List.of(member(10)),false,second)));
  verify(owner,times(2)).resolveAllCurrent(any());
 }
 @Test void oldFourArgumentContractRetainsOwnerPerProjectValidation(){
  when(owner.resolveCurrent(any())).thenAnswer(call->{var q=call.<cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery>getArgument(0);return new ProjectScopeResult(q.anchorProjectId(),1L,Set.of(q.anchorProjectId()),Set.of());});
  assertEquals(2,references.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(1,2,List.of(member(10),member(20)),false)).size());
  verify(owner,times(2)).resolveCurrent(any());verify(owner,never()).resolveAllCurrent(any());verify(mapper,never()).selectCurrentTreeProjectIds(any());
 }
 @Test void numericReferencesReuseRunManageScopeButRecheckEveryCurrentTree(){
  var scope=context(Set.of(10L,20L));
  for(long id:List.of(10L,20L,10L))assertEquals(id,references.resolve(new BaseSyncProjectReferences.Query(1,2,3,List.of(Long.toString(id)),false,scope)).get(Long.toString(id)).id());
  verify(owner).resolveAllCurrent(any());verify(owner,never()).resolveCurrent(any());verify(mapper,times(3)).selectCurrentTreeProjectIds(any());
  doReturn(List.of()).when(mapper).selectCurrentTreeProjectIds(any());
  assertThrows(AccessDeniedException.class,()->references.resolve(new BaseSyncProjectReferences.Query(1,2,3,List.of("10"),false,scope)));
 }
 @Test void numericReferenceScopeCannotCrossTenantActorOrAuthorizeOutsideOwnerSet(){
  var scope=context(Set.of(10L));
  assertThrows(AccessDeniedException.class,()->references.resolve(new BaseSyncProjectReferences.Query(2,2,3,List.of("10"),false,scope)));
  assertThrows(AccessDeniedException.class,()->references.resolve(new BaseSyncProjectReferences.Query(1,3,3,List.of("10"),false,scope)));
  verifyNoInteractions(mapper);
  assertThrows(AccessDeniedException.class,()->references.resolve(new BaseSyncProjectReferences.Query(1,2,3,List.of("20"),false,scope)));
 }
 @Test void missingNumericParentRetainsPreviewAndActualRules(){
  var scope=context(Set.of(10L));doReturn(List.of()).when(mapper).selectCurrentProjects(any());
  assertEquals(10,references.resolve(new BaseSyncProjectReferences.Query(1,2,3,List.of("10"),true,scope)).get("10").id());
  assertThrows(IllegalStateException.class,()->references.resolve(new BaseSyncProjectReferences.Query(1,2,3,List.of("10"),false,scope)));
 }
}
