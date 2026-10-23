package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Existing connection-local binding TEMP fixture, unchanged unique keys, production batch XML. */
class BindingBatchMySqlTest extends RemainingTaskMySqlSupport {
 BaseSyncMetadataMapper mapper;
 @BeforeEach void metadataMapper()throws Exception{
  var resource="mapper/basesync/BaseSyncMetadataMapper.xml";
  try(var input=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(input,session.getConfiguration(),resource,session.getConfiguration().getSqlFragments()).parse();}
  mapper=session.getMapper(BaseSyncMetadataMapper.class);
 }
 BaseSyncMetadataMapper.BindingWrite row(long id){return new BaseSyncMetadataMapper.BindingWrite(id,1,2,9,"MEMBER_HISTORY","source"+id,id,"a".repeat(64),null,"actor");}
 @Test void nativeMultiValuesInsertPersistsFieldsAndProtectedUpdateKeepsWhere(){
  assertEquals(2,transaction.execute(status->mapper.insertBindings(new BaseSyncMetadataMapper.BindingBatch(List.of(row(1),row(2))))).intValue());
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM int_base_sync_binding WHERE tenant_id=1 AND task_id=2 AND object_key='MEMBER_HISTORY' AND last_run_id=9 AND creator='actor' AND updater='actor'",Integer.class));
  var changed=new BaseSyncMetadataMapper.BindingWrite(3,1,2,10,"MEMBER_HISTORY","source1",1,"b".repeat(64),"a".repeat(64),"next");
  assertEquals(1,transaction.execute(status->mapper.updateBinding(changed)).intValue());assertEquals(0,transaction.execute(status->mapper.updateBinding(changed)).intValue());
 }
 @Test void duplicateSourceUniqueKeyRejectsBatchAndRollsBackEarlierBatch(){
  assertThrows(RuntimeException.class,()->transaction.execute(status->{
   mapper.insertBindings(new BaseSyncMetadataMapper.BindingBatch(List.of(row(1),row(2))));
   var duplicate=new BaseSyncMetadataMapper.BindingWrite(4,1,2,9,"MEMBER_HISTORY","source3",4,"a".repeat(64),null,"actor");
   mapper.insertBindings(new BaseSyncMetadataMapper.BindingBatch(List.of(row(3),duplicate)));return null;
  }));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM int_base_sync_binding",Integer.class));
 }
}
