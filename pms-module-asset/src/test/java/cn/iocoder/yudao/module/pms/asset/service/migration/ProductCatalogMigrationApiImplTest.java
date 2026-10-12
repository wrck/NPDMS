package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.AssetProductOfficialMapper;
import cn.iocoder.yudao.module.pms.asset.service.product.OfficialProductCatalogLabels;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
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
    final EntityExtensionApi extensions=mock(EntityExtensionApi.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final ProductCatalogMigrationApiImpl service=new ProductCatalogMigrationApiImpl(products,extensions,permissions);
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    void allowed() {
        when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(true);
        when(extensions.definition(eq(9L),any(),any())).thenReturn(new EntityExtensionApi.DefinitionRevision(9L,1L,"AST","ProductOfficial",1,
                List.of(OfficialProductCatalogLabels.definition())));
    }
    AssetProductOfficialDO existing() {
        var row=new AssetProductOfficialDO();row.setId(10L);row.setTenantId(1L);row.setProductCode("CODE");row.setVersion(5L);
        row.setProductName("CRM name");row.setProductModel("CRM model");row.setProductDesc("CRM desc");row.setTechnicalSpec("CRM spec");
        row.setStatus("INACTIVE");row.setSourceSystem("CRM");row.setSourceKey("crm-id");row.setCreator("99");return row;
    }
    Label label(String name) { return new Label("CODE",name,null); }
    Request request(List<Entry> entries) { return new Request(1L,7L,9L,"run",entries); }
    Entry entry(Label value) { return new Entry(value.productCode(),value,null,null); }
    void target(AssetProductOfficialDO row,List<Label> labels) {
        when(products.selectByProductCodeForUpdate(any())).thenReturn(row);
        when(extensions.read(any(),any())).thenReturn(new EntityExtensionApi.Values(9L,
                Map.of(OfficialProductCatalogLabels.FIELD,OfficialProductCatalogLabels.encode(labels)),2));
    }
    @Test void crmFieldsRemainUntouchedAndOriginalNullableLabelsGoToTheFormalExtension() {
        allowed();var row=existing();target(row,List.of());
        var result=service.apply(request(List.of(entry(label(" original name "))))).getFirst();
        assertEquals("UPDATE",result.action());assertEquals(10L,result.targetId());
        assertEquals("CRM name",row.getProductName());assertEquals("CRM model",row.getProductModel());
        assertEquals("CRM desc",row.getProductDesc());assertEquals("CRM spec",row.getTechnicalSpec());
        assertEquals("INACTIVE",row.getStatus());assertEquals("CRM",row.getSourceSystem());assertEquals("99",row.getCreator());
        verify(products,never()).insert(any(AssetProductOfficialDO.class));verify(products,never()).updateById(any(AssetProductOfficialDO.class));
        var capture=ArgumentCaptor.forClass(EntityExtensionApi.Save.class);verify(extensions).save(capture.capture());
        var save=capture.getValue();assertEquals(10L,save.target().entity().entityId());assertEquals(5L,save.expectedEntityVersion());
        assertEquals(2,save.expectedValueVersion());assertEquals(7L,save.actor().userId());
        @SuppressWarnings("unchecked") Map<String,Object> decoded=JsonUtils.parseObject(JsonUtils.toJsonString(save.fields()),Map.class);
        assertEquals(List.of(label(" original name ")),OfficialProductCatalogLabels.decode(decoded,"CODE"));
        assertTrue(((Map<?,?>)((List<?>)decoded.get(OfficialProductCatalogLabels.FIELD)).getFirst()).containsKey("productModel"));
    }
    @Test void replayDoesNotRewriteAndSourceChangeAppendsRatherThanErasingThePreviousLabel() {
        allowed();target(existing(),List.of(label("first")));
        var replay=service.apply(request(List.of(new Entry("CODE",label("first"),10L,new Snapshot(10L,"CODE",List.of(label("first"))))))).getFirst();
        assertEquals("UNCHANGED",replay.action());verify(extensions,never()).save(any());
        var changed=service.apply(request(List.of(new Entry("CODE",label("second"),10L,replay.after())))).getFirst();
        assertEquals(List.of(label("first"),label("second")),changed.after().labels());verify(extensions).save(any());
    }
    @Test void missingProductIsActiveAndUsesApprovedCodeIdentityWithoutInventingASourceTime() {
        allowed();when(products.insert(any(AssetProductOfficialDO.class))).thenReturn(1);
        service.apply(request(List.of(entry(new Label(" CODE "," Original "," Model ")))));
        var capture=ArgumentCaptor.forClass(AssetProductOfficialDO.class);verify(products).insert(capture.capture());var row=capture.getValue();
        assertEquals("CODE",row.getProductCode());assertEquals(" Original ",row.getProductName());assertEquals(" Model ",row.getProductModel());
        assertEquals("ACTIVE",row.getStatus());assertEquals("DPPMS",row.getSourceSystem());assertEquals(" CODE ",row.getSourceKey());
        assertNull(row.getSourceUpdatedAt());assertNull(row.getSourceVersion());assertNotNull(row.getSyncedAt());
        verify(extensions).save(argThat(save->save.target().entity().entityId().equals(row.getId())));
    }
    @Test void allPlansAreValidatedBeforeAnyWriteSoALaterManualChangeDoesNotApplyEarlierRows() {
        allowed();var row=existing();
        when(products.selectByProductCodeForUpdate(any())).thenAnswer(invocation-> {
            var query=(cn.iocoder.yudao.module.pms.asset.dal.mysql.product.query.ProductCatalogIdentityQuery)invocation.getArgument(0);
            return "CODE".equals(query.productCode())?row:null;
        });
        when(extensions.read(any(),any())).thenReturn(new EntityExtensionApi.Values(9L,Map.of(),1));
        var earlier=entry(new Label("AAA","new",null));
        var conflict=new Entry("CODE",label("changed"),10L,new Snapshot(10L,"CODE",List.of(label("deleted manually"))));
        assertThrows(IllegalStateException.class,()->service.apply(request(List.of(earlier,conflict))));
        verify(products,never()).insert(any(AssetProductOfficialDO.class));verify(extensions,never()).save(any());
    }
    @Test void deletedCrossTenantAndCaseConflictingIdentitiesCannotBeRevivedOrAdopted() {
        allowed();var row=existing();row.setDeleted(true);when(products.selectByProductCodeForUpdate(any())).thenReturn(row);
        assertThrows(IllegalStateException.class,()->service.apply(request(List.of(entry(label("x"))))));
        row.setDeleted(false);row.setTenantId(2L);
        assertThrows(IllegalStateException.class,()->service.apply(request(List.of(entry(label("x"))))));
        row.setTenantId(1L);row.setProductCode("code");
        assertThrows(IllegalStateException.class,()->service.apply(request(List.of(entry(label("x"))))));
        verify(extensions,never()).read(any(),any());verify(extensions,never()).save(any());
    }
    @Test void mismatchingLabelsForANewConvertedCodeWaitForDecisionInsteadOfPickingAName() {
        allowed();assertThrows(IllegalStateException.class,()->service.apply(request(List.of(entry(label("one")),entry(new Label(" CODE ","two",null))))));
        verify(products,never()).insert(any(AssetProductOfficialDO.class));verify(extensions,never()).save(any());
    }
    @Test void forbiddenOrForeignTenantRequestsFailBeforeBusinessReads() {
        assertThrows(IllegalArgumentException.class,()->service.apply(request(List.of(entry(label("x"))))));
        verifyNoInteractions(products,extensions);TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class,()->service.apply(request(List.of(entry(label("x"))))));
        verifyNoInteractions(products,extensions);
    }
    @Test void previewComputesFormalTargetsAndPreservationWithoutWrites() {
        allowed();target(existing(),List.of());
        assertEquals(10L,service.preview(request(List.of(entry(label("x"))))).getFirst().targetId());
        verify(products,never()).insert(any(AssetProductOfficialDO.class));verify(extensions,never()).save(any());
    }
    @Test void resolvingFormalCarryRequiresTheFullOriginalLabelAndNeverWrites() {
        allowed();target(existing(),List.of(label("Original")));
        var result=service.resolve(new ResolveRequest(1L,7L,"verify",List.of(label("Original"))));
        assertEquals(List.of(new Snapshot(10L,"CODE",List.of(label("Original")))),result);
        assertThrows(IllegalStateException.class,()->service.resolve(new ResolveRequest(1L,7L,"verify",List.of(label("Other")))));
        verify(products,never()).insert(any(AssetProductOfficialDO.class));verify(extensions,never()).save(any());
    }
    @Test void formalDirectoryWithoutDeclaredLabelsDoesNotCountAsCompleteCarry() {
        allowed();when(products.selectByProductCodeForUpdate(any())).thenReturn(existing());
        when(extensions.read(any(),any())).thenReturn(new EntityExtensionApi.Values(null,Map.of(),0));
        assertThrows(IllegalStateException.class,()->service.resolve(new ResolveRequest(1L,7L,"verify",List.of(label(null)))));
        verify(extensions,never()).save(any());
    }
    @Test void resolvingPreservesTenantPermissionsAndDoesNotReviveDeletedProducts() {
        assertThrows(IllegalArgumentException.class,()->service.resolve(new ResolveRequest(1L,7L,"verify",List.of(label("Original")))));
        verifyNoInteractions(products,extensions);
        allowed();var row=existing();row.setDeleted(true);when(products.selectByProductCodeForUpdate(any())).thenReturn(row);
        assertThrows(IllegalStateException.class,()->service.resolve(new ResolveRequest(1L,7L,"verify",List.of(label("Original")))));
        verify(extensions,never()).read(any(),any());
        TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class,()->service.resolve(new ResolveRequest(1L,7L,"verify",List.of(label("Original")))));
    }
}
