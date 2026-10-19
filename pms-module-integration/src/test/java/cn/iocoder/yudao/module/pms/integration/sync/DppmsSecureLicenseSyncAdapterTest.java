package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceEntityResolveApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsSecureLicenseSyncTemplate.*;
class DppmsSecureLicenseSyncAdapterTest {
    SyncOwnerRunContext context=mock(SyncOwnerRunContext.class);DppmsLicenseRecordApi owner=mock(DppmsLicenseRecordApi.class);
    DppmsCommerceEntityResolveApi contracts=mock(DppmsCommerceEntityResolveApi.class);DppmsSecureLicenseSyncAdapter adapter=new DppmsSecureLicenseSyncAdapter(context,owner,contracts);
    SyncDefinition definition=create(new Request(7L,10L,List.of(new Approved("201","SN",null,"CONTRACT",null,null,"RAW"))));
    @BeforeEach void setup(){
        when(context.require(any())).thenReturn(new SyncOwnerRunContext.Context(1L,2L,new SyncTaskDO().setId(37L).setConnectionId(7L),new SyncRunDO().setId(370L).setStartedAt(LocalDateTime.of(2020,1,1,0,0)),definition));
        when(contracts.resolveLicenseContracts(any())).thenReturn(List.of(new DppmsCommerceEntityResolveApi.LicenseContract("CONTRACT",50L)));
        when(owner.preview(any())).thenReturn(List.of(new DppmsLicenseRecordApi.Preview("201",null,"CREATE",List.of())));
        when(owner.apply(any())).thenReturn(List.of(new DppmsLicenseRecordApi.Target("201",80L)));
    }
    Row row(Long target){var fields=new LinkedHashMap<String,Object>();COLUMNS.forEach(n->fields.put(n,null));fields.put("id","201");fields.put("sn","SN");fields.put("contract","CONTRACT");fields.put("status","RAW");fields.put("ciphertext","ENCRYPTED");fields.put("keyId","TEST-V1");fields.put("codeMac","a".repeat(64));return new Row(OBJECT,"201",fields,target);}
    Batch batch(Row row,List<Binding> bindings){return new Batch("integration:1:37",List.of(row),bindings,true,"RETAIN",false,"UPSERT",false);}
    @Test void controlledTemplateCannotBecomeGenericPlaintextSqlAndNullFactsRemainFrozen(){
        new SyncDefinitionValidator(List.of(adapter)).validate(JsonUtils.parseObject(JsonUtils.toJsonString(definition),SyncDefinition.class));
        assertNull(definition.sources().getFirst().sql());assertNull(definition.sources().getFirst().publishedProjectMatch());
        assertNull(adapter.preview(batch(row(null),List.of())).getFirst().targetId());verify(owner,never()).apply(any());
        var result=adapter.apply(batch(row(null),List.of())).getFirst();assertEquals(80L,result.targetId());
        verify(owner).apply(argThat(c->c.projectId()==10L && c.snapshotId()==370L && c.licenses().getFirst().contractId()==50L && "ENCRYPTED".equals(c.licenses().getFirst().envelope().ciphertext())));
        assertFalse(JsonUtils.toJsonString(result.after()).contains("ciphertext"));assertFalse(JsonUtils.toJsonString(result.after()).contains("licenseCode"));
    }
    @Test void immutableOriginalAndCodeMacChangesCannotReachOwner(){
        var result=adapter.apply(batch(row(null),List.of())).getFirst();var binding=new Binding(OBJECT,"201",80L,JsonUtils.parseMap(JsonUtils.toJsonString(result.after())));clearInvocations(owner);
        var changed=row(80L);changed.fields().put("codeMac","b".repeat(64));assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(changed,List.of(binding))));
        var other=row(80L);other.fields().put("sn","OTHER");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(other,List.of(binding))));verifyNoInteractions(owner);
    }
    @Test void unapprovedIdsAndMissingScopeCannotImportOrInferAllLicenses(){
        var extra=new Row(OBJECT,"202",row(null).fields(),null);assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(extra,List.of())));
        assertThrows(IllegalArgumentException.class,()->adapter.apply(new Batch("integration:1:37",List.of(),List.of(),true,"RETAIN",false,"UPSERT",false)));verifyNoInteractions(owner);
    }
    @Test void pendingAssociationsRemainExplicitAndAreNotInventedAsMatches(){
        when(contracts.resolveLicenseContracts(any())).thenReturn(List.of(new DppmsCommerceEntityResolveApi.LicenseContract("CONTRACT",null)));
        when(owner.preview(any())).thenReturn(List.of(new DppmsLicenseRecordApi.Preview("201",null,"CREATE",List.of("DEVICE_NOT_MATCHED","CONTRACT_NOT_UNIQUELY_MATCHED"))));
        var result=adapter.apply(batch(row(null),List.of())).getFirst();assertEquals(List.of("DEVICE_NOT_MATCHED","CONTRACT_NOT_UNIQUELY_MATCHED"),result.after().get("pendingAssociations"));
        assertTrue(result.message().contains("关联仍待处理"));verify(owner).apply(argThat(c->c.licenses().getFirst().contractId()==null));
    }
}
