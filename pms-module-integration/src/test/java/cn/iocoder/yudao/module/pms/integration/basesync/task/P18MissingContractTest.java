package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Full execute with in-memory SQL/identity state; no database, DDL or task Run. */
class P18MissingContractTest {
 private P18ProjectContractSyncTask.TargetMapper mapper;
 private SyncIdentityStore identities;
 private SqlSession session;
 private P18ProjectContractSyncTask task;
 private final Map<String,SyncIdentityStore.Identity> bindings=new HashMap<>();
 private final Map<Long,P18ProjectContractSyncTask.Row> targets=new LinkedHashMap<>();
 private final Map<Long,String> projectCompanies=new HashMap<>();
 private final Map<CompanyContract,Long> contracts=new HashMap<>();
 private final List<RawInsert> inserts=new ArrayList<>();
 private final List<String> events=new ArrayList<>();
 private List<P18ProjectContractSyncTask.Source> sources=List.of();
 private SyncContext<Void> lastContext;
 private final LocalDateTime at=LocalDateTime.of(2020,1,1,0,0);
 private record CompanyContract(String company,String contractNo){}
 private record SourceFixture(long id,String sourceCompany,String contractNo){}
 private record RawInsert(Long targetId,String contractNo,Long contractId){}
 @BeforeEach void setup(){
  mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);
  identities=mock(SyncIdentityStore.class);session=mock(SqlSession.class);
  task=spy(new P18ProjectContractSyncTask(mapper));
  doAnswer(call->sources).when(task).syncDataQuery(any());
  when(identities.findByKeys(any())).thenAnswer(call->{
   var query=call.<SyncIdentityStore.Query>getArgument(0);var result=new HashMap<String,SyncIdentityStore.Identity>();
   for(var key:query.sourceKeys()){var old=bindings.get(query.object()+":"+key);if(old!=null)result.put(key,old);}return result;
  });
  doAnswer(call->{
   for(var value:call.<List<SyncIdentityStore.Save>>getArgument(0)){
    String key=value.object()+":"+value.sourceKey();var old=bindings.get(key);
    if(value.previousFingerprint()!=null){assertNotNull(old);assertEquals(old.fingerprint(),value.previousFingerprint());}
    else assertNull(old,"An already owned binding must use its previous fingerprint");
    bindings.put(key,new SyncIdentityStore.Identity(value.sourceKey(),value.targetId(),value.fingerprint()));
   }return null;
  }).when(identities).saveAll(any());
  when(mapper.selectRelations(any())).thenAnswer(call->{
   var query=call.<P18ProjectContractSyncTask.RelationsQuery>getArgument(0);assertEquals(1,query.tenantId());
   return targets.values().stream().filter(row->query.keys().contains(new P18ProjectContractSyncTask.RelationKey(row.getProjectId(),row.getContractNo()))).map(this::copy).toList();
  });
  when(session.insert(anyString(),any())).thenAnswer(call->{
   assertEquals(P18ProjectContractSyncTask.TargetMapper.class.getName()+".insertProjectContract",call.getArgument(0));
   var batch=call.<SyncBatch<P18ProjectContractSyncTask.Source,P18ProjectContractSyncTask.Parameters>>getArgument(1);events.add("insert");
   for(var source:batch.rows()){
    var row=source.getTarget();assertNotNull(row.getId());assertNull(row.getContractId(),"Raw insert must precede reference association");assertEquals(source.getContractNo(),row.getContractNo());
    inserts.add(new RawInsert(row.getId(),row.getContractNo(),row.getContractId()));assertNull(targets.put(row.getId(),copy(row)));
   }return batch.rows().size();
  });
  when(mapper.fillContractReferences(any())).thenAnswer(call->{
   var query=call.<P18ProjectContractSyncTask.ContractReferencesQuery>getArgument(0);assertEquals(1,query.tenantId());events.add("fill");int filled=0;
   var sourceCompanies=new HashMap<Long,String>();
   for(var group:query.sourceCompanies()){
    assertNotNull(group.companyCode());
    for(var id:group.targetIds()){assertTrue(query.targetIds().contains(id));assertNull(sourceCompanies.put(id,group.companyCode()));}
   }
   for(var id:query.targetIds()){
    var row=targets.get(id);assertNotNull(row,"Fill may only receive existing verified targets");
    String sourceCompany=sourceCompanies.get(id);String formalCompany=projectCompanies.get(row.getProjectId());
    Long contractId=sourceCompany!=null&&sourceCompany.equals(formalCompany)?contracts.get(new CompanyContract(sourceCompany,row.getContractNo())):null;
    if(row.getContractId()==null&&contractId!=null){row.setContractId(contractId);filled++;}
   }return filled;
  });
  when(mapper.selectContractReferences(any())).thenAnswer(call->{
   var query=call.<P18ProjectContractSyncTask.ContractReferencesQuery>getArgument(0);events.add("readback");
   return query.targetIds().stream().map(id->new P18ProjectContractSyncTask.ContractReference(id,targets.get(id).getContractId())).toList();
  });
 }
 private P18ProjectContractSyncTask.Row copy(P18ProjectContractSyncTask.Row value){var result=new P18ProjectContractSyncTask.Row();org.springframework.beans.BeanUtils.copyProperties(value,result);return result;}
 private P18ProjectContractSyncTask.Source source(SourceFixture value){
  projectCompanies.put(value.id(),value.sourceCompany()==null?null:cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter.fromSource(value.sourceCompany()));
  var row=new P18ProjectContractSyncTask.Source().setContractSourceId(value.id()).setMemberId(value.id()).setOriginalProjectId(value.id()).setOriginalProjectCode("P-"+value.id()).setOriginalCompanyCode(value.sourceCompany()).setContractNo(value.contractNo()).setContractCreateTime(at).setContractUpdateTime(at).setCreatedAt(at).setUpdatedAt(at).setCreatorCode("").setUpdaterCode("").setResolvedCreator("").setResolvedUpdater("").setFormalStatus("HISTORICAL");row.setCanonical(row);return row;
 }
 private record Execution(boolean preview,int batchSize,List<P18ProjectContractSyncTask.Source> rows){}
 private SyncCounts execute(Execution request){
  sources=request.rows();lastContext=new SyncContext<>(1,2,3,request.preview(),request.batchSize(),10000,at,mock(SyncSource.class),identities,mock(SyncReferences.class),"Task",session,false,false);
  return task.execute(lastContext);
 }
 private void assertBound(P18ProjectContractSyncTask.Source row){
  assertNotNull(row.getTarget());assertNotNull(row.getTarget().getId());assertEquals(row.getContractNo(),row.getTarget().getContractNo());
  assertEquals(row.getTarget().getId(),bindings.get("RELATION_BUSINESS:"+row.businessKey()).targetId());assertEquals(row.getTarget().getId(),bindings.get("RELATION_ORIGINAL:"+row.sourceKey()).targetId());
 }
 @Test void rawRowsPreserveFullContractNumberAndNullReferenceBeforeSinglePostfill(){
  contracts.put(new CompanyContract("001"," C/Exact-01 "),100L);
  var hit=source(new SourceFixture(1,"01"," C/Exact-01 "));var missing=source(new SourceFixture(2,"01","MISSING"));var noCompany=source(new SourceFixture(3,null,"UNKNOWN-COMPANY"));var secondCompany=source(new SourceFixture(4,"02","RAW-COMPANY-002"));
  projectCompanies.put(3L,"001");contracts.put(new CompanyContract("001","UNKNOWN-COMPANY"),101L);
  assertEquals(new SyncCounts(4,4,1,0,0),execute(new Execution(false,2,List.of(hit,missing,noCompany,secondCompany))));
  assertEquals(List.of(" C/Exact-01 ","MISSING","UNKNOWN-COMPANY","RAW-COMPANY-002"),inserts.stream().map(RawInsert::contractNo).toList());assertTrue(inserts.stream().allMatch(row->row.contractId()==null));
  for(var row:List.of(hit,missing,noCompany,secondCompany))assertBound(row);
  assertEquals(100L,hit.getTarget().getContractId());assertNull(missing.getTarget().getContractId());assertNull(noCompany.getTarget().getContractId());assertEquals(8,bindings.size());
  assertTrue(lastContext.runState().warnings().contains("P18_CONTRACT_REFERENCE_PENDING:3"));
 }
 @Test void laterExactMatchUpdatesOnlyReferenceAndBusinessFingerprintOnSameTarget(){
  var row=source(new SourceFixture(1,"01","LATER"));assertEquals(new SyncCounts(1,1,0,0,0),execute(new Execution(false,2,List.of(row))));
  long id=row.getTarget().getId();var before=copy(targets.get(id));var original=bindings.get("RELATION_ORIGINAL:"+row.sourceKey());String businessBefore=bindings.get("RELATION_BUSINESS:"+row.businessKey()).fingerprint();
  contracts.put(new CompanyContract("001","LATER"),100L);
  assertEquals(new SyncCounts(1,0,1,1,0),execute(new Execution(false,2,List.of(row))));assertEquals(id,row.getTarget().getId());assertEquals(100L,row.getTarget().getContractId());
  var expected=copy(before);expected.setContractId(100L);assertEquals(expected,targets.get(id));assertEquals(original,bindings.get("RELATION_ORIGINAL:"+row.sourceKey()));assertNotEquals(businessBefore,bindings.get("RELATION_BUSINESS:"+row.businessKey()).fingerprint());
  clearInvocations(identities);
  assertEquals(new SyncCounts(1,0,0,1,0),execute(new Execution(false,2,List.of(row))));assertEquals(1,inserts.size());assertEquals(1,targets.size());assertEquals(2,bindings.size());
  verify(identities,never()).saveAll(any());verify(identities,never()).save(any());
 }
 @Test void anotherCompanyAndCaseVariantDoNotSupplyAnExactReference(){
  contracts.put(new CompanyContract("002","SAME"),100L);contracts.put(new CompanyContract("001","same"),101L);
  var row=source(new SourceFixture(1,"01","SAME"));var mismatch=source(new SourceFixture(2,"01","SAME"));projectCompanies.put(2L,"002");
  assertEquals(new SyncCounts(2,2,0,0,0),execute(new Execution(false,2,List.of(row,mismatch))));assertBound(row);assertBound(mismatch);assertNull(row.getTarget().getContractId());assertNull(mismatch.getTarget().getContractId());
  var captor=org.mockito.ArgumentCaptor.forClass(P18ProjectContractSyncTask.ContractReferencesQuery.class);verify(mapper).fillContractReferences(captor.capture());assertEquals(1,captor.getValue().tenantId());assertEquals(List.of(row.getTarget().getId(),mismatch.getTarget().getId()),captor.getValue().targetIds());assertEquals(List.of(new P18ProjectContractSyncTask.CompanyRelationIds("001",List.of(row.getTarget().getId(),mismatch.getTarget().getId()))),captor.getValue().sourceCompanies());
 }
 @Test void changedTargetSourceOrUnownedExistingTargetStillCannotBeAdopted(){
  var row=source(new SourceFixture(1,"01","RAW"));execute(new Execution(false,2,List.of(row)));long id=row.getTarget().getId();
  targets.get(id).setCreator("tampered");assertEquals("P18_EXISTING_RELATION_PROTECTED",assertThrows(IllegalArgumentException.class,()->execute(new Execution(false,2,List.of(row)))).getMessage());targets.get(id).setCreator("");
  row.setContractNo("CHANGED");assertEquals("P18_ORIGINAL_RELATION_CHANGED",assertThrows(IllegalArgumentException.class,()->execute(new Execution(false,2,List.of(row)))).getMessage());row.setContractNo("RAW");
  bindings.remove("RELATION_BUSINESS:"+row.businessKey());assertEquals("P18_EXISTING_RELATION_PROTECTED",assertThrows(IllegalArgumentException.class,()->execute(new Execution(false,2,List.of(row)))).getMessage());assertEquals(1,inserts.size());
 }
 @Test void postfillReadbackCannotInjectAnotherTarget(){
  doReturn(List.of(new P18ProjectContractSyncTask.ContractReference(999L,100L))).when(mapper).selectContractReferences(any());
  var row=source(new SourceFixture(1,"01","RAW"));var failure=assertThrows(IllegalArgumentException.class,()->execute(new Execution(false,2,List.of(row))));assertEquals("P18_POSTFILL_RELATION_IDENTITY_INVALID",failure.getMessage());assertTrue(bindings.isEmpty());
 }
 @Test void everyRawInsertChunkCompletesBeforeOneFillAndOneReadback(){
  var rows=new ArrayList<P18ProjectContractSyncTask.Source>();for(int i=1;i<=5;i++)rows.add(source(new SourceFixture(i,"01","RAW-"+i)));
  assertEquals(new SyncCounts(5,5,0,0,0),execute(new Execution(false,2,rows)));assertEquals(List.of("insert","insert","insert","fill","readback"),events);
  verify(mapper).fillContractReferences(any());verify(mapper).selectContractReferences(any());assertEquals(10,bindings.size());
 }
 @Test void previewPlansRawRowsButNeverInsertsFillsOrSavesBindings(){
  var row=source(new SourceFixture(1,"01","RAW"));assertEquals(new SyncCounts(1,1,0,0,0),execute(new Execution(true,2,List.of(row))));assertNull(row.getTarget().getId());assertNull(row.getTarget().getContractId());assertEquals("RAW",row.getTarget().getContractNo());
  verify(session,never()).insert(anyString(),any());verify(mapper,never()).fillContractReferences(any());verify(mapper,never()).selectContractReferences(any());verify(identities,never()).saveAll(any());assertTrue(targets.isEmpty());assertTrue(bindings.isEmpty());
 }
 @Test void twoHundredUnmatchedRowsStillPersistWithOnlyAggregatedMissingWarning(){
  var rows=new ArrayList<P18ProjectContractSyncTask.Source>();for(int i=1;i<=200;i++)rows.add(source(new SourceFixture(i,"01","MISSING-"+i)));
  assertEquals(new SyncCounts(200,200,0,0,0),execute(new Execution(false,50,rows)));assertEquals(200,targets.size());assertEquals(400,bindings.size());assertEquals(List.of("P18_CONTRACT_REFERENCE_PENDING:200"),lastContext.runState().warnings());
  verify(mapper).fillContractReferences(any());verify(mapper).selectContractReferences(any());
 }
 @Test void unmatchedSourceBridgesAreCountedWithoutBlockingMatchedRows(){
  var external=mock(SyncSource.Session.class);var source=mock(SyncSource.class);var raw=mock(P18ProjectContractSyncTask.SourceMapper.class);
  when(source.open(3)).thenReturn(external);when(external.mapper(P18ProjectContractSyncTask.SourceMapper.class)).thenReturn(raw);
  var valid=new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setContractNo("RAW").setProjectGroupCode("VALID").setContractCreateTime(at).setContractUpdateTime(at).setContractCreateBy("").setContractUpdateBy("");
  when(raw.selectContracts(any())).thenReturn(List.of(valid,
   new P18ProjectContractSyncTask.Source().setProjectGroupCode("NO-GROUP"),
   new P18ProjectContractSyncTask.Source().setProjectGroupCode("EMPTY"),
   new P18ProjectContractSyncTask.Source().setProjectGroupCode("NO-PROJECT")));
  when(raw.selectGroups(any())).thenReturn(List.of(
   new P18ProjectContractSyncTask.Source().setGroupProjectGroupCode("VALID").setProjectType("10"),
   new P18ProjectContractSyncTask.Source().setGroupProjectGroupCode("EMPTY").setProjectType("10"),
   new P18ProjectContractSyncTask.Source().setGroupProjectGroupCode("NO-PROJECT").setProjectType("10")));
  when(raw.selectMembers(any())).thenReturn(List.of(
   new P18ProjectContractSyncTask.Source().setMemberId(1L).setMemberProjectGroupCode("VALID").setMemberProjectCode("P-1"),
   new P18ProjectContractSyncTask.Source().setMemberId(2L).setMemberProjectGroupCode("NO-PROJECT").setMemberProjectCode("MISSING")));
  when(raw.selectProjects(any())).thenReturn(List.of(new P18ProjectContractSyncTask.Source().setOriginalProjectId(1L).setOriginalProjectCode("P-1").setOriginalCompanyCode("01")));
  doCallRealMethod().when(task).syncDataQuery(any());
  lastContext=new SyncContext<>(1,2,3,false,2,10000,at,source,identities,mock(SyncReferences.class),"Task",session,false,false);
  assertEquals(new SyncCounts(1,1,0,0,0),task.execute(lastContext));
  assertEquals(1,targets.size());assertEquals(2,bindings.size());
  assertTrue(lastContext.runState().warnings().containsAll(List.of("P18_BRIDGE_CONTRACT_GROUP_MISSING:1","P18_BRIDGE_CONTRACT_MEMBERS_MISSING:1","P18_BRIDGE_CONTRACT_MEMBER_PROJECT_MISSING:1")));
  assertEquals(3,lastContext.runState().warnings().stream().filter(warning->warning.startsWith("P18_BRIDGE_")).count());
  verify(external).close();
 }
}
