package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectLicenseHistoryMapper;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsSecureLicenseSyncTemplate.*;

class DirectLicenseHistoryStreamingTaskTest {
    final SyncOwnerRunContext context=mock(SyncOwnerRunContext.class);
    final DirectLicenseHistoryMapper records=mock(DirectLicenseHistoryMapper.class);
    final DirectLicenseHistoryPersistence owner=mock(DirectLicenseHistoryPersistence.class);
    final DppmsDirectLicenseHistoryTask task=new DppmsDirectLicenseHistoryTask(context,mock(javax.sql.DataSource.class),
            records,mock(cn.iocoder.yudao.module.system.api.permission.PermissionApi.class),mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class),mock(PlatformCommandExecutionApi.class),
            mock(PlatformMigrationEvidenceApi.class),DirectLicenseHistoryCipherTest.KEY,"TEST-V1");
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(task,"owner",owner);
        var approved=java.util.stream.IntStream.rangeClosed(1,401).mapToObj(i->new Approved(""+i,"SN"+i,null,null,null,null,"RAW")).toList();
        var d=DppmsDirectLicenseHistoryTemplate.create(7L).toBuilder().sources(List.of(DppmsDirectLicenseHistoryTemplate.approvedSource(new Request(7L,null,approved)))).build();
        when(context.require(any())).thenReturn(new SyncOwnerRunContext.Context(1L,2L,new SyncTaskDO().setId(37L).setConnectionId(7L),
                new SyncRunDO().setId(370L).setStartedAt(LocalDateTime.of(2020,1,1,0,0)),d));
        when(owner.preview(any())).thenAnswer(call->{var command=call.getArgument(0,DppmsLicenseRecordApi.Command.class);
            return command.licenses().stream().map(row->new DirectLicenseHistoryPersistence.Preview(row.sourceKey(),null,"CREATE",List.of(),List.of())).toList();});
    }
    Row row(String key) {
        var fields=new LinkedHashMap<String,Object>();COLUMNS.forEach(n->fields.put(n,null));
        fields.put("id",key);fields.put("sn","SN"+key);fields.put("status","RAW");fields.put("ciphertext","SYNTHETIC-ENCRYPTED");
        fields.put("keyId","TEST-V1");fields.put("codeMac","a".repeat(64));return new Row(OBJECT,key,fields,null);
    }
    Batch batch(List<Row> rows){return new Batch("integration:1:37",rows,List.of(),true,"RETAIN",false,"UPSERT",false);}
    @Test void oneChunkOfLargeApprovedManifestCanPreviewWithoutDroppingFullScope() {
        new SyncDefinitionValidator(List.of(task)).validate(context.require(null).definition());
        var result=task.preview(batch(List.of(row("201"))));assertEquals(1,result.size());assertEquals("CREATED",result.getFirst().action());
        verify(owner).preview(argThat(c->c.licenses().size()==1 && c.projectId()==null && c.licenses().getFirst().sourceKey().equals("201")));
        verify(owner,never()).apply(any());
    }
    @Test void outOfScopeChangedFactAndOversizedCommandRemainRejected() {
        assertThrows(IllegalArgumentException.class,()->task.preview(batch(List.of(row("402")))));
        var changed=row("201");changed.fields().put("sn","OTHER");assertThrows(IllegalArgumentException.class,()->task.preview(batch(List.of(changed))));
        var oversized=java.util.stream.IntStream.rangeClosed(1,201).mapToObj(i->row(""+i)).toList();
        assertThrows(IllegalArgumentException.class,()->task.preview(batch(oversized)));verifyNoInteractions(owner);
    }
    @Test void emptyCodeIssuePreservesTheCreatedRecordAndItsSourceIdentity() {
        doReturn(List.of(new DirectLicenseHistoryPersistence.Preview("201",null,"CREATE",List.of(),List.of("LICENSE_CODE_EMPTY")))).when(owner).preview(any());
        var changes=task.preview(batch(List.of(row("201"))));assertEquals(1,changes.size());var change=changes.getFirst();
        assertEquals("CREATED",change.action());assertEquals("201",change.sourceKey());
        assertEquals(List.of("LICENSE_CODE_EMPTY"),change.after().get("sourceIssues"));
        assertTrue(change.message().contains("原始空字符串已加密保留"));assertTrue(DppmsDirectLicenseHistoryTask.hasSourceIssue(change));
        verify(owner,never()).apply(any());
    }
    @Test void wholeTableUniqueContractIsAssociatedWhileMissingAndAmbiguousStayNull() {
        var names=List.of("SINGLE","SHARED","MISSING");
        var facts=java.util.stream.IntStream.rangeClosed(1,3).mapToObj(i->new Approved(""+i,"SN"+i,null,names.get(i-1),null,null,"RAW")).toList();
        var run=context.require(null);
        var definition=run.definition().toBuilder().sources(List.of(DppmsDirectLicenseHistoryTemplate.approvedSource(new Request(7L,null,facts)))).build();
        when(context.require(any())).thenReturn(new SyncOwnerRunContext.Context(run.tenantId(),run.actorId(),run.task(),run.run(),definition));
        when(records.selectContractIds(any())).thenAnswer(call->switch(call.getArgument(0,DirectLicenseHistoryMapper.ContractIdentity.class).contractNo()) {
            case "SINGLE"->List.of(501L);case "SHARED"->List.of(502L,503L);default->List.of();
        });
        var rows=java.util.stream.IntStream.rangeClosed(1,3).mapToObj(i->{var row=row(""+i);row.fields().put("contract",names.get(i-1));return row;}).toList();
        assertEquals(3,task.preview(batch(rows)).size());
        verify(owner).preview(argThat(c->c.projectId()==null && c.licenses().get(0).contractId()==501L
                && c.licenses().get(1).contractId()==null && c.licenses().get(2).contractId()==null));
        verify(records,times(3)).selectContractIds(argThat(q->q.tenantId()==1L));verify(owner,never()).apply(any());
    }
}
