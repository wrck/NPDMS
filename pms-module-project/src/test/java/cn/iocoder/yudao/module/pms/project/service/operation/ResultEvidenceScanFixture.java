package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 本地H2使用生产XML及Spring事务；Native Owner、计划与结果日志采用明确外围替身。 */
final class ResultEvidenceScanFixture implements AutoCloseable {
    final ResultSubscriptionRecoveryFixture recovery = new ResultSubscriptionRecoveryFixture();
    final Map<String,Observation> observations = new HashMap<>();
    final ResultEvidenceMapper evidence;
    final ProjectResultEvidenceScanner scanner;
    ResultEvidenceScanFixture() throws Exception {
        recovery.jdbc.execute("CREATE TABLE proj_result_evidence_scan(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,subscription_id BIGINT,subscription_version INT,through_sequence BIGINT,after_candidate_id BIGINT,accumulator CLOB,status VARCHAR(24),version INT,UNIQUE(tenant_id,subscription_id,subscription_version))");
        recovery.jdbc.execute("CREATE TABLE proj_result_evidence_item(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,scan_id BIGINT,candidate_id BIGINT,object_id VARCHAR(128),result_id VARCHAR(128),formation_sequence BIGINT,eligibility VARCHAR(24),reason VARCHAR(128),observation CLOB,UNIQUE(tenant_id,scan_id,candidate_id))");
        var config = new Configuration(new Environment("evidence",new SpringManagedTransactionFactory(),recovery.jdbc.getDataSource()));
        config.setMapUnderscoreToCamelCase(true);
        String xml="mapper/businessresult/ResultEvidenceMapper.xml";
        try(var input=getClass().getClassLoader().getResourceAsStream(xml)) { assertNotNull(input);new XMLMapperBuilder(input,config,xml,config.getSqlFragments()).parse(); }
        evidence=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config)).getMapper(ResultEvidenceMapper.class);
        when(recovery.sources.commitBarrierSupported(ResultSubscriptionRecoveryFixture.TYPE)).thenReturn(true);
        when(recovery.sources.descriptor(ResultSubscriptionRecoveryFixture.TYPE)).thenReturn(new Descriptor(ResultSubscriptionRecoveryFixture.TYPE,true,true,true));
        when(recovery.sources.inspect(any())).thenAnswer(call -> {
            Query query=call.getArgument(0);return observations.getOrDefault(query.resultId()==null?query.objectId():query.resultId(),Observation.absent(Status.NOT_FOUND,"RESULT_NOT_FOUND"));
        });
        scanner=recovery.proxy(new ProjectResultEvidenceScanner(recovery.contexts,new ProjectResultEvidenceConsistency(recovery.sources,recovery.journal),recovery.sources,recovery.candidates,evidence,recovery.outbox));
        @SuppressWarnings("unchecked") ObjectProvider<ProjectResultEvidenceScanner> provider=mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(scanner);ReflectionTestUtils.setField(recovery.delivery,"evidence",provider);
        recovery.tick();recovery.tick();
    }
    void seed(int number,String object,String result,Long formation,Validity validity) {
        var value=recovery.result(object,result,validity);observations.put(result,value);
        var row=new ResultSubscriptionCandidateDO();row.setId(1000L+number);row.setTenantId(1L);row.setProjectId(3L);row.setSubscriptionId(501L);
        row.setObjectId(object);row.setResultId(result);row.setFormationSequence(formation);row.setObservedSequence(recovery.row().getProcessedSequence());row.setObservation(JsonUtils.toJsonString(value));
        assertEquals(1,recovery.candidates.insert(row));
    }
    void policy(String acquisition,String validity,String selection,List<String> objects) {
        var subscription=(ObjectNode) recovery.snapshot.getStages().getFirst().getExecution().get("subscriptions").get(0);
        var policy=(ObjectNode)subscription.get("policy");policy.put("acquisition",acquisition);policy.put("validity",validity);policy.put("selection",selection);
        if(acquisition.equals("PINNED_RESULT"))policy.put("pinnedResultId","r1");else policy.remove("pinnedResultId");
        var scope=(ObjectNode)subscription.get("scope");scope.put("mode",objects==null?"PROJECT":"OBJECTS");
        if(objects!=null){var ids=scope.putArray("objectIds");objects.forEach(ids::add);}else scope.remove("objectIds");
        recovery.plan.setExecutionSnapshot(JsonUtils.toJsonString(recovery.snapshot));
        recovery.jdbc.update("UPDATE proj_result_subscription SET configuration=? WHERE id=501",subscription.toString());
    }
    void tick() { scanner.process(ResultEvidenceScanEvent.create(recovery.row(),recovery.row().getVersion(),"test")); }
    ResultEvidenceScanDO scan() { var row=recovery.row();return evidence.selectScanForUpdate(new ResultEvidenceMapper.ScanIdentity(1L,3L,501L,row.getVersion())); }
    int items() {return recovery.jdbc.queryForObject("SELECT COUNT(*) FROM proj_result_evidence_item",Integer.class);}
    int events(String type) {return recovery.jdbc.queryForObject("SELECT COUNT(*) FROM recovery_test_outbox WHERE event_type=?",Integer.class,type);}
    void advanceEpoch(long through) {
        recovery.committed=through;recovery.jdbc.update("UPDATE proj_result_subscription SET processed_sequence=?,version=version+1 WHERE id=501",through);
    }
    public void close() {recovery.close();}
}
