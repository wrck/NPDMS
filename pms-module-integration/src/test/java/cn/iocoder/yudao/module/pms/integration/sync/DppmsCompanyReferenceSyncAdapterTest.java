package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncTaskDO;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsCompanyReferenceSyncAdapterTest {
    private ManagedOrganizationApi owner;
    private DppmsCompanyReferenceSyncAdapter adapter;
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);owner=mock(ManagedOrganizationApi.class);var tasks=mock(SyncTaskService.class);
        when(tasks.required(12L)).thenReturn(new SyncTaskDO().setAdapter("DPPMS_ORGANIZATION").setSourceSystem("DPPMS")
                .setDefinition(JsonUtils.toJsonString(DppmsConsolidatedTemplate.create(new DppmsConsolidatedTemplate.Request("P01",9L)))));
        adapter=new DppmsCompanyReferenceSyncAdapter(owner,tasks);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    private ManagedOrganizationApi.Node company(Long id,String code){return new ManagedOrganizationApi.Node(new CompanyDO().setId(id).setCode(code).setName("公司").setStatus(0),null,null);}
    private Batch batch(Long target,List<Binding> prior){return new Batch("integration:1:12",List.of(new Row("COMPANY_REFERENCE","7",
            Map.of("sourceCompanyId","7","sourceCompanyCode","01"),target)),prior,true,"RETAIN",false,"UPSERT",false);}
    @Test void originalCompanyAliasUsesItsExistingConversionWithoutChangingTheFormalCompany(){
        when(owner.list()).thenReturn(List.of(company(91L,"001"),company(92L,"01")));
        var change=adapter.apply(batch(null,List.of())).getFirst();assertEquals(91L,change.targetId());
        assertEquals("01",change.after().get("sourceCompanyCode"));assertEquals("001",change.after().get("companyCode"));
        verify(owner,never()).apply(any());verify(owner,never()).replaceAll(any());
        assertEquals("UNCHANGED",adapter.apply(batch(91L,List.of(new Binding(change.object(),change.sourceKey(),91L,change.after())))).getFirst().action());
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(92L,List.of())));
    }
    @Test void missingOrAmbiguousFormalCompanyCannotCreateOrChooseOne(){
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(null,List.of())));
        when(owner.list()).thenReturn(List.of(company(91L,"001"),company(92L,"001")));
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(null,List.of())));
        verify(owner,never()).apply(any());
    }
    @Test void organizationPreviewReceiptCanResolveANewCompanyWithoutAnyWrite(){
        var preceding=List.of(new ManagedOrganizationApi.Result("COMPANY:8",null,company(null,"001"),"CREATED"));
        var change=adapter.previewAfterOrganization(new DppmsConsolidation.OrganizationPreviewBatch(batch(null,List.of()),preceding)).getFirst();
        assertNull(change.targetId());assertEquals("REFERENCED",change.action());verify(owner,never()).apply(any());
    }
}
