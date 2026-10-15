package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.AssetProductOfficialMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static cn.iocoder.yudao.module.pms.asset.api.migration.ProductCatalogMigrationApi.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductCatalogMigrationApiImplTest {
    final AssetProductOfficialMapper products=mock(AssetProductOfficialMapper.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final ProductCatalogMigrationApiImpl service=new ProductCatalogMigrationApiImpl(products,permissions);
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    void allowed() { when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(true); }
    AssetProductOfficialDO existing() {
        var row=new AssetProductOfficialDO();row.setId(10L);row.setTenantId(1L);row.setProductCode("CODE");row.setVersion(5L);
        row.setProductName("CRM name");row.setProductModel("CRM model");row.setProductDesc("CRM desc");row.setTechnicalSpec("CRM spec");
        row.setStatus("INACTIVE");row.setSourceSystem("CRM");row.setSourceKey("crm-id");row.setCreator("99");return row;
    }
    Label label(String name) { return new Label("CODE",name,null); }
    Request request(List<Entry> entries) { return new Request(1L,7L,"run",entries); }
    Entry entry(Label value) { return new Entry(value.productCode(),value,null,null); }
    void target(AssetProductOfficialDO row) { when(products.selectByProductCodeForUpdate(any())).thenReturn(row); }
    @Test void crmFieldsRemainUntouchedAndHistoricalLabelsDoNotTouchExtensions() {
        allowed();var row=existing();target(row);
        row.setUpdater("88");row.setUpdateTime(java.time.LocalDateTime.of(2020,1,1,0,0));
        String beforeJson=JsonUtils.toJsonString(row);
        var result=service.apply(request(List.of(entry(label(" original name "))))).getFirst();
        assertEquals("UNCHANGED",result.action());assertEquals(10L,result.targetId());
        assertEquals("CRM name",row.getProductName());assertEquals("CRM model",row.getProductModel());
        assertEquals("CRM desc",row.getProductDesc());assertEquals("CRM spec",row.getTechnicalSpec());
        assertEquals("INACTIVE",row.getStatus());assertEquals("CRM",row.getSourceSystem());assertEquals("99",row.getCreator());
        assertEquals(beforeJson,JsonUtils.toJsonString(row));
        verify(products,never()).insert(any(AssetProductOfficialDO.class));verify(products,never()).updateById(any(AssetProductOfficialDO.class));

    }
    @Test void historicalSnapshotLabelsAndChangedSourceDoNotRewriteCrmFields() {
        allowed();target(existing());
        var replay=service.apply(request(List.of(new Entry("CODE",label("first"),10L,new Snapshot(10L,"CODE",List.of(label("first"))))))).getFirst();
        assertEquals("UNCHANGED",replay.action());
        var changed=service.apply(request(List.of(new Entry("CODE",label("second"),10L,replay.after())))).getFirst();
        assertEquals(List.of(new Label("CODE","CRM name","CRM model")),changed.after().labels());
    }
    @Test void missingProductIsActiveAndUsesApprovedCodeIdentityWithoutInventingASourceTime() {
        allowed();when(products.insert(any(AssetProductOfficialDO.class))).thenReturn(1);
        service.apply(request(List.of(entry(new Label(" CODE "," Original "," Model ")))));
        var capture=ArgumentCaptor.forClass(AssetProductOfficialDO.class);verify(products).insert(capture.capture());var row=capture.getValue();
        assertEquals("CODE",row.getProductCode());assertEquals(" Original ",row.getProductName());assertEquals(" Model ",row.getProductModel());
        assertEquals("ACTIVE",row.getStatus());assertEquals("DPPMS",row.getSourceSystem());assertEquals(" CODE ",row.getSourceKey());
        assertNull(row.getSourceUpdatedAt());assertNull(row.getSourceVersion());assertNotNull(row.getSyncedAt());

    }
    @Test void allPlansAreValidatedBeforeAnyWriteSoALaterManualChangeDoesNotApplyEarlierRows() {
        allowed();var row=existing();
        when(products.selectByProductCodeForUpdate(any())).thenAnswer(invocation-> {
            var query=(cn.iocoder.yudao.module.pms.asset.dal.mysql.product.query.ProductCatalogIdentityQuery)invocation.getArgument(0);
            return "CODE".equals(query.productCode())?row:null;
        });
        var earlier=entry(new Label("AAA","new",null));
        var conflict=new Entry("CODE",label("changed"),10L,new Snapshot(11L,"CODE",List.of(label("deleted manually"))));
        assertThrows(IllegalStateException.class,()->service.apply(request(List.of(earlier,conflict))));
        verify(products,never()).insert(any(AssetProductOfficialDO.class));
    }
    @Test void deletedCrossTenantAndCaseConflictingIdentitiesCannotBeRevivedOrAdopted() {
        allowed();var row=existing();row.setDeleted(true);when(products.selectByProductCodeForUpdate(any())).thenReturn(row);
        assertThrows(IllegalStateException.class,()->service.apply(request(List.of(entry(label("x"))))));
        row.setDeleted(false);row.setTenantId(2L);
        assertThrows(IllegalStateException.class,()->service.apply(request(List.of(entry(label("x"))))));
        row.setTenantId(1L);row.setProductCode("code");
        assertThrows(IllegalStateException.class,()->service.apply(request(List.of(entry(label("x"))))));

    }
    @Test void mismatchingLabelsForANewConvertedCodeWaitForDecisionInsteadOfPickingAName() {
        allowed();assertThrows(IllegalStateException.class,()->service.apply(request(List.of(entry(label("one")),entry(new Label(" CODE ","two",null))))));
        verify(products,never()).insert(any(AssetProductOfficialDO.class));
    }
    @Test void forbiddenOrForeignTenantRequestsFailBeforeBusinessReads() {
        assertThrows(IllegalArgumentException.class,()->service.apply(request(List.of(entry(label("x"))))));
        verifyNoInteractions(products);TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class,()->service.apply(request(List.of(entry(label("x"))))));
        verifyNoInteractions(products);
    }
    @Test void previewComputesFormalTargetsAndPreservationWithoutWrites() {
        allowed();target(existing());
        assertEquals(10L,service.preview(request(List.of(entry(label("x"))))).getFirst().targetId());
        verify(products,never()).insert(any(AssetProductOfficialDO.class));
        verify(products,never()).updateById(any(AssetProductOfficialDO.class));
    }
    @Test void emptyCodesAndTargetIdDriftCannotExpandReadsOrWrite() {
        allowed();
        assertThrows(IllegalArgumentException.class,()->service.apply(request(List.of(entry(new Label(" ",null,null))))));
        assertThrows(IllegalArgumentException.class,()->service.resolve(new ResolveRequest(1L,7L,"run",List.of(new Label("",null,null)))));
        assertTrue(service.apply(request(List.of())).isEmpty());verifyNoInteractions(products);
        when(products.selectByProductCodeForUpdate(any())).thenReturn(existing());
        assertThrows(IllegalStateException.class,()->service.apply(request(List.of(new Entry("CODE",label("old"),11L,null)))));
        verify(products,never()).insert(any(AssetProductOfficialDO.class));verify(products,never()).updateById(any(AssetProductOfficialDO.class));
    }
    @Test void resolvingUsesCodeRegardlessOfHistoricalLabelsAndNeverWrites() {
        allowed();target(existing());
        var result=service.resolve(new ResolveRequest(1L,7L,"verify",List.of(label("Original"))));
        assertEquals(List.of(new Snapshot(10L,"CODE",List.of(new Label("CODE","CRM name","CRM model")))),result);
        assertEquals(result,service.resolve(new ResolveRequest(1L,7L,"verify",List.of(label("Other")))));
        verify(products,never()).insert(any(AssetProductOfficialDO.class));
    }
    @Test void formalDirectoryDoesNotRequireExtensionsButMissingDirectoryFails() {
        allowed();when(products.selectByProductCodeForUpdate(any())).thenReturn(existing());
        assertEquals(10L,service.resolve(new ResolveRequest(1L,7L,"verify",List.of(label(null)))).getFirst().productId());
        when(products.selectByProductCodeForUpdate(any())).thenReturn(null);
        assertThrows(IllegalStateException.class,()->service.resolve(new ResolveRequest(1L,7L,"verify",List.of(label(null)))));

    }
    @Test void resolvingPreservesTenantPermissionsAndDoesNotReviveDeletedProducts() {
        assertThrows(IllegalArgumentException.class,()->service.resolve(new ResolveRequest(1L,7L,"verify",List.of(label("Original")))));
        verifyNoInteractions(products);
        allowed();var row=existing();row.setDeleted(true);when(products.selectByProductCodeForUpdate(any())).thenReturn(row);
        assertThrows(IllegalStateException.class,()->service.resolve(new ResolveRequest(1L,7L,"verify",List.of(label("Original")))));

        TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class,()->service.resolve(new ResolveRequest(1L,7L,"verify",List.of(label("Original")))));
    }
}
