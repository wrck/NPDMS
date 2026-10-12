package cn.iocoder.yudao.module.pms.commerce.service.migration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.*;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceDetailImportApi.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.associated.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class DppmsCommerceDetailImportServiceTest {
    DppmsCommerceEntityResolveApi resolver=mock(DppmsCommerceEntityResolveApi.class);
    DppmsCommerceDetailMapper mapper=mock(DppmsCommerceDetailMapper.class);
    SalesOrderMapper orders=mock(SalesOrderMapper.class);
    SalesOrderLineMapper lines=mock(SalesOrderLineMapper.class);
    ShipmentPackageMapper packages=mock(ShipmentPackageMapper.class);
    DppmsExecutionOrganizationReferences organizationReferences=mock(DppmsExecutionOrganizationReferences.class);
    DppmsCommerceDetailImportService service=new DppmsCommerceDetailImportService(resolver,mapper,orders,lines,packages,organizationReferences);
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);
        var company=new cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO();
        company.setId(71L);company.setTenantId(1L);company.setCode("001");company.setName("Official company");
        when(organizationReferences.load()).thenReturn(new DppmsExecutionOrganizationReferences.Directory(
                Map.of("001",new cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.Node(company,null,null)),Map.of()));
        when(mapper.selectPackageContractIdentity(any())).thenReturn(new DppmsCommerceDetailMapper.PackageContractIdentity("DPPMS","CONTRACT-KEY"));}
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    DppmsCommerceEntityResolveApi.Command identity() {
        return new DppmsCommerceEntityResolveApi.Command(1L,1L,100L,"TEST",List.of(),List.of(),List.of(),List.of(),"10");
    }
    NativeEntityMigrationRow contract() {
        return NativeEntityMigrationRow.mapped("contracts","10",new ExternalTargetMapping("COM","Contract","com_contract",20L,"BUSINESS_ENTITY",0));
    }
    @Test void reusesFormalCompanyAndSourceSystemWithoutCreatingOriginalCompanyMirrors() {
        when(resolver.resolve(any())).thenReturn(List.of(contract(),NativeEntityMigrationRow.mapped("orders","30",
                new ExternalTargetMapping("COM","SalesOrder","com_sales_order",40L,"BUSINESS_ENTITY",0))));
        var row=new SalesOrderDO();row.setId(40L);row.setCompanyCode("001");row.setCustomerCode("APPROVED");
        row.setSourceRecordKey("DPPMS|SAP|001|0|ORDER");when(mapper.selectOrderForUpdate(any())).thenReturn(row);
        when(orders.updateById(any(SalesOrderDO.class))).thenReturn(1);
        var source=new DppmsCommerceDetailImportApi.Order("30",null,null,null,null,null,null,null,null,null,null,"01","SAP","30");
        service.importDetails(new Command(identity(),List.of(source),List.of(),List.of(),List.of()));
        assertEquals("001",row.getCompanyCode());assertEquals("APPROVED",row.getCustomerCode());
        assertEquals("SAP",row.getSourceSystem());assertEquals("DPPMS|SAP|001|0|ORDER",row.getSourceRecordKey());
    }
    @Test void nativePromotionRequiresSyncAndNeverCreatesOrMergesContractPlans() {
        when(resolver.resolve(any())).thenReturn(List.of(contract()));
        for(int replay=0;replay<2;replay++) {
            var result=service.importDetails(new Command(identity(),List.of(),List.of(),List.of(),List.of(planSource())));
            var plan=result.stream().filter(r->"plans".equals(r.domain())).findFirst().orElseThrow();
            assertTrue(plan.targets().isEmpty());
            assertEquals("CONTRACT_COLLECTION_PLAN_SYNC_REQUIRED",plan.reasonCode());
        }
        verifyNoInteractions(mapper,orders,lines,packages);
    }
    @Test void conflictingNativeDatesBlockBeforeAnyOtherOrderValueIsChanged() {
        when(resolver.resolve(any())).thenReturn(List.of(contract(),NativeEntityMigrationRow.mapped("orders","30",
                new ExternalTargetMapping("COM","SalesOrder","com_sales_order",40L,"BUSINESS_ENTITY",0))));
        var existing=LocalDateTime.of(2020,1,2,3,4,5);
        var sourceDate=existing.plusNanos(321_000_000);
        var row=new SalesOrderDO();row.setId(40L);row.setCompanyCode("001");row.setSourceSystem("ERP");
        row.setSourceRecordKey("DPPMS|SAP|001|0|ORDER");row.setOrderCreateTime(existing);row.setCustomerRequiredTime(existing.plusDays(1));
        when(mapper.selectOrderForUpdate(any())).thenReturn(row);
        var source=new DppmsCommerceDetailImportApi.Order("30",null,null,sourceDate,sourceDate.plusDays(1),null,null,null,null,null,null,"01","SAP","30",sourceDate.plusHours(1));
        var result=service.importDetails(new Command(identity(),List.of(source),List.of(),List.of(),List.of()));
        var outcome=result.stream().filter(r->"orders".equals(r.domain())).findFirst().orElseThrow();
        assertTrue(outcome.targets().isEmpty());assertEquals("COMMERCE_SOURCE_CONTENT_CHANGED",outcome.reasonCode());
        assertEquals(existing,row.getOrderCreateTime());assertEquals(existing.plusDays(1),row.getCustomerRequiredTime());
        assertNull(row.getSourceUpdatedAt());assertEquals("ERP",row.getSourceSystem());
        verify(orders,never()).updateById(any(SalesOrderDO.class));
    }
    @Test void directFormalOrderDatesPreserveOriginalMillisecondsAndReplayWithoutWriting() {
        when(resolver.resolve(any())).thenReturn(List.of(contract(),NativeEntityMigrationRow.mapped("orders","30",
                new ExternalTargetMapping("COM","SalesOrder","com_sales_order",40L,"BUSINESS_ENTITY",0))));
        var row=new SalesOrderDO();row.setId(40L);row.setCompanyCode("001");row.setSourceSystem("ERP");
        row.setSourceRecordKey("DPPMS|SAP|001|0|ORDER");when(mapper.selectOrderForUpdate(any())).thenReturn(row);
        when(orders.updateById(any(SalesOrderDO.class))).thenReturn(1);
        var date=LocalDateTime.of(2020,1,2,3,4,5,321_000_000);
        var source=new DppmsCommerceDetailImportApi.Order("30",null,null,date,date.plusDays(1),null,null,null,null,null,null,"01","SAP","30",date.plusHours(1));
        var command=new Command(identity(),List.of(source),List.of(),List.of(),List.of());
        service.importDetails(command);service.importDetails(command);
        assertEquals(date,row.getOrderCreateTime());assertEquals(date.plusDays(1),row.getCustomerRequiredTime());
        assertEquals(date.plusHours(1),row.getSourceUpdatedAt());assertEquals("SAP",row.getSourceSystem());
        verify(orders,times(1)).updateById(row);
    }
    @Test void wrongErpOrCompanyIdentityCannotChangeAnOrder() {
        when(resolver.resolve(any())).thenReturn(List.of(contract(),NativeEntityMigrationRow.mapped("orders","30",
                new ExternalTargetMapping("COM","SalesOrder","com_sales_order",40L,"BUSINESS_ENTITY",0))));
        var row=new SalesOrderDO();row.setId(40L);row.setSourceRecordKey("DPPMS|D365|001|0|ORDER");row.setCompanyCode("001");row.setSourceSystem("D365");
        when(mapper.selectOrderForUpdate(any())).thenReturn(row);String before=JsonUtils.toJsonString(row);
        for (var source:List.of(new DppmsCommerceDetailImportApi.Order("30","CONTRACT",null,null,null,null,null,null,null,null,null,"01","SAP","30"),
                new DppmsCommerceDetailImportApi.Order("30","CONTRACT",null,null,null,null,null,null,null,null,null,"02","D365","30"))) {
            var result=service.importDetails(new Command(identity(),List.of(source),List.of(),List.of(),List.of()));
            assertEquals("TARGET_IDENTITY_CONFLICT",result.stream().filter(r->"orders".equals(r.domain())).findFirst().orElseThrow().reasonCode());
            assertEquals(before,JsonUtils.toJsonString(row));
        }
        verify(orders,never()).updateById(any(SalesOrderDO.class));
    }
    @Test void lineReusesFormalSourceTimeAndPreservesEmptyBundleCodeUnitAndOriginalOwnerIdentity() {
        when(resolver.resolve(any())).thenReturn(List.of(contract(),NativeEntityMigrationRow.mapped("order-lines","31",
                new ExternalTargetMapping("COM","SalesOrderLine","com_sales_order_line",41L,"BUSINESS_ENTITY",0))));
        var row=new SalesOrderLineDO();row.setId(41L);row.setOrderId(40L);row.setCompanyCode("001");row.setSourceSystem("ERP");
        row.setSourceRecordKey("DPPMS|D365|01|0|ORDER|1");row.setOrderQty(new BigDecimal("4.000"));
        row.setOpenQty(new BigDecimal("1.000"));row.setDeliveredQty(new BigDecimal("3.000"));row.setQuantityStatus("PENDING_AUTHORITY");
        when(mapper.selectLineForUpdate(any())).thenReturn(row);when(lines.updateById(any(SalesOrderLineDO.class))).thenReturn(1);
        var at=LocalDateTime.of(2020,1,2,3,4,5,123_000_000);
        var source=new DppmsCommerceDetailImportApi.Line("31","PRODUCT","Description",new BigDecimal("4"),new BigDecimal("1"),"",12,"0","PROFIT","EXEC",null,"01","D365","31",at);
        var command=new Command(identity(),List.of(),List.of(source),List.of(),List.of());
        service.importDetails(command);service.importDetails(command);
        assertEquals("",row.getBundleCode());assertEquals("D365",row.getSourceSystem());assertEquals(at,row.getSourceUpdatedAt());
        assertEquals("DPPMS|D365|01|0|ORDER|1",row.getSourceRecordKey());assertEquals(40L,row.getOrderId());
        assertEquals(new BigDecimal("3.000"),row.getDeliveredQty());assertNull(row.getUnitCode());assertEquals("PENDING_AUTHORITY",row.getQuantityStatus());
        verify(lines,times(1)).updateById(row);
    }
    @Test void normalizesProvedSourceCarrierCodeIntoTheExistingCarrierName() {
        when(resolver.resolve(any())).thenReturn(List.of(contract()));
        var row=new ShipmentPackageDO();row.setId(90L);row.setPackageNo("PACK");row.setSourceRecordKey("PACK");row.setCarrierName("2");
        when(mapper.selectPackageForUpdate(any())).thenReturn(List.of(row));
        when(packages.updateById(any(ShipmentPackageDO.class))).thenReturn(1);
        var source=new DppmsCommerceDetailImportApi.Package("PACK","PACK",null,null,null,"RECEIVER","EXPRESS","承运商名称","CONTRACT-KEY","2");
        var result=service.importDetails(new Command(identity(),List.of(),List.of(),List.of(source),List.of()));
        assertFalse(result.stream().filter(r->"shipment-packages".equals(r.domain())).findFirst().orElseThrow().targets().isEmpty());
        assertEquals(source.carrierName(),row.getCarrierName());assertEquals("2",row.getCarrierCode());
    }

    private DppmsCommerceDetailImportApi.Package packageSource() {
        return new DppmsCommerceDetailImportApi.Package("PACK","PACK",null,null,null,"RECEIVER","EXPRESS","Carrier","CONTRACT-KEY","2");
    }
    private ShipmentPackageDO packageTarget(String carrierName) {
        when(resolver.resolve(any())).thenReturn(List.of(contract()));
        var row=new ShipmentPackageDO();row.setId(90L);row.setPackageNo("PACK");row.setSourceRecordKey("PACK");row.setCarrierName(carrierName);
        when(mapper.selectPackageForUpdate(any())).thenReturn(List.of(row));
        return row;
    }
    private NativeEntityMigrationRow importPackage(DppmsCommerceDetailImportApi.Package source) {
        return service.importDetails(new Command(identity(),List.of(),List.of(),List.of(source),List.of())).stream()
                .filter(r->"shipment-packages".equals(r.domain())).findFirst().orElseThrow();
    }
    @Test void independentCarrierNameCannotBeOverwrittenOrEnrichedOnConflict() {
        var row=packageTarget("Current Owner Carrier");
        var result=importPackage(packageSource());
        assertTrue(result.targets().isEmpty());assertEquals("COMMERCE_SOURCE_CONTENT_CHANGED",result.reasonCode());
        assertEquals("Current Owner Carrier",row.getCarrierName());assertNull(row.getCarrierCode());
        assertNull(row.getReceiverName());assertNull(row.getExpressNo());
        verify(packages,never()).updateById(any(ShipmentPackageDO.class));
    }
    @Test void alreadyNormalizedCarrierNameReplaysWithoutWriting() {
        var source=packageSource();var row=packageTarget(source.carrierName());
        row.setReceiverName(source.receiverName());row.setExpressNo(source.expressNo());
        row.setCarrierCode(source.carrierCode());
        var result=importPackage(source);
        assertFalse(result.targets().isEmpty());assertEquals(source.carrierName(),row.getCarrierName());
        assertEquals(source.carrierCode(),row.getCarrierCode());
        verify(mapper).selectPackageForUpdate(argThat(q->q.tenantId()==1L && q.projectId()==100L
                && "TEST".equals(q.contractNo()) && "PACK".equals(q.packageNo())));
        verify(packages,never()).updateById(any(ShipmentPackageDO.class));
    }
    @Test void otherPackageFieldConflictPreventsCarrierNormalizationAndAllEnrichment() {
        var row=packageTarget("2");row.setExpressNo("Current Owner Express");
        var result=importPackage(packageSource());
        assertTrue(result.targets().isEmpty());assertEquals("COMMERCE_SOURCE_CONTENT_CHANGED",result.reasonCode());
        assertEquals("2",row.getCarrierName());assertEquals("Current Owner Express",row.getExpressNo());
        assertNull(row.getCarrierCode());
        assertNull(row.getReceiverName());verify(packages,never()).updateById(any(ShipmentPackageDO.class));
    }
    @Test void emptyCarrierNameCanBeFilledFromTheDecodedSourceName() {
        when(packages.updateById(any(ShipmentPackageDO.class))).thenReturn(1);
        for (String empty:Arrays.asList(null,"","  ")) {
            var row=packageTarget(empty);var source=packageSource();
            assertFalse(importPackage(source).targets().isEmpty());assertEquals(source.carrierName(),row.getCarrierName());
            assertEquals(source.carrierCode(),row.getCarrierCode());
        }
        verify(packages,times(3)).updateById(any(ShipmentPackageDO.class));
    }
    @Test void nonEmptyCarrierNameNeedsTheExplicitMatchingSourceCodeForNormalization() {
        var row=packageTarget("2");var source=packageSource();
        var withoutCode=new DppmsCommerceDetailImportApi.Package(source.sourceKey(),source.packageNo(),source.shipmentTime(),
                source.warrantyStartTime(),source.warrantyEndTime(),source.receiverName(),source.expressNo(),
                source.carrierName(),source.sourceContractKey(),null);
        var result=importPackage(withoutCode);
        assertTrue(result.targets().isEmpty());assertEquals("COMMERCE_SOURCE_CONTENT_CHANGED",result.reasonCode());
        assertEquals("2",row.getCarrierName());assertNull(row.getCarrierCode());
        verify(packages,never()).updateById(any(ShipmentPackageDO.class));
    }
    @Test void carrierNormalizationRetainsTheExistingWriteConflictGuard() {
        packageTarget("2");when(packages.updateById(any(ShipmentPackageDO.class))).thenReturn(0);
        var failure=assertThrows(IllegalStateException.class,()->importPackage(packageSource()));
        assertEquals("DPPMS_NATIVE_PACKAGE_CONCURRENT_CHANGE",failure.getMessage());
    }

    @Test void lockedPackageSourceIdentityMismatchCannotChangeAnyCarrierOrPackageField() {
        var row=packageTarget("2");row.setSourceRecordKey("ANOTHER-SOURCE");
        var result=importPackage(packageSource());
        assertTrue(result.targets().isEmpty());assertEquals("NATIVE_SOURCE_IDENTITY_UNRESOLVED",result.reasonCode());
        assertEquals("2",row.getCarrierName());assertNull(row.getCarrierCode());
        assertNull(row.getReceiverName());assertNull(row.getExpressNo());
        verify(packages,never()).updateById(any(ShipmentPackageDO.class));
    }
    @Test void carrierOnlyCorrectionPreservesExistingDatesAndAllOtherPackageFields() {
        var row=packageTarget("2");var at=LocalDateTime.of(2020,1,2,3,4,5);
        row.setShipmentTime(at);row.setWarrantyStartTime(at);row.setWarrantyEndTime(at.plusYears(1));
        row.setReceiverName("CURRENT RECEIVER");row.setExpressNo("CURRENT EXPRESS");row.setShipmentContractRefId(77L);
        row.setCarrierCode("2");when(packages.updateById(any(ShipmentPackageDO.class))).thenReturn(1);
        var result=importPackage(new DppmsCommerceDetailImportApi.Package("PACK","PACK",null,null,null,null,null,"Carrier",null,"2"));
        assertFalse(result.targets().isEmpty());assertEquals("Carrier",row.getCarrierName());
        assertEquals("2",row.getCarrierCode());assertEquals(at,row.getShipmentTime());assertEquals(at,row.getWarrantyStartTime());
        assertEquals(at.plusYears(1),row.getWarrantyEndTime());assertEquals("CURRENT RECEIVER",row.getReceiverName());
        assertEquals("CURRENT EXPRESS",row.getExpressNo());assertEquals(77L,row.getShipmentContractRefId());
        verifyNoInteractions(orders,lines);
    }

    @Test void existingDifferentRawCarrierCodeCannotBeOverwrittenEvenWhenNameMatchesSourceCode() {
        var row=packageTarget("2");row.setCarrierCode("3");
        var result=importPackage(new DppmsCommerceDetailImportApi.Package("PACK","PACK",null,null,null,null,null,"Carrier",null,"2"));
        assertEquals("COMMERCE_SOURCE_CONTENT_CHANGED",result.reasonCode());assertTrue(result.targets().isEmpty());
        assertEquals("2",row.getCarrierName());assertEquals("3",row.getCarrierCode());
        verify(packages,never()).updateById(any(ShipmentPackageDO.class));
    }
    @Test void lockQueryBindsExactContractWithoutChangingProjectPackagePageScope() throws Exception {
        var configuration=new org.apache.ibatis.session.Configuration();
        String resource="mapper/migration/DppmsCommerceDetailMapper.xml";
        try(var input=getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
        }
        String namespace=DppmsCommerceDetailMapper.class.getName()+".";
        var locked=configuration.getMappedStatement(namespace+"selectPackageForUpdate").getBoundSql(Map.of("query",
                new DppmsCommerceDetailMapper.PackageQuery(1L,100L,"EXACT-CONTRACT","PACK")));
        String sql=locked.getSql().replaceAll("\\s+"," ");
        assertTrue(sql.contains("c.id=sr.contract_id"));assertTrue(sql.contains("c.tenant_id=p.tenant_id"));
        assertTrue(sql.contains("c.deleted=0"));assertTrue(sql.contains("c.contract_no=?"));assertTrue(sql.endsWith("FOR UPDATE"));
        assertTrue(locked.getParameterMappings().stream().anyMatch(p->p.getProperty().equals("query.contractNo")));
        var page=configuration.getMappedStatement(namespace+"selectPackages").getBoundSql(Map.of("query",
                new DppmsCommerceDetailMapper.PageQuery(1L,100L,0,20)));
        assertFalse(page.getParameterMappings().stream().anyMatch(p->p.getProperty().equals("query.contractNo")));
        assertTrue(page.getSql().contains("cr.project_id"));
    }
    @Test void rawContractKeyIsProvedByTheFormalRelationshipAndCannotBeSilentlyAdopted() {
        var row=packageTarget("2");String before=JsonUtils.toJsonString(row);
        for (var reference:Arrays.asList(null,new DppmsCommerceDetailMapper.PackageContractIdentity("DPPMS","ANOTHER"),
                new DppmsCommerceDetailMapper.PackageContractIdentity("CRM","CONTRACT-KEY"))) {
            when(mapper.selectPackageContractIdentity(any())).thenReturn(reference);
            var result=importPackage(packageSource());assertEquals("NATIVE_SOURCE_IDENTITY_UNRESOLVED",result.reasonCode());
            assertEquals(before,JsonUtils.toJsonString(row));
        }
        verify(packages,never()).updateById(any(ShipmentPackageDO.class));
        verify(mapper,times(3)).selectPackageContractIdentity(argThat(query->query.tenantId()==1L && query.id()==90L));
    }
    @Test void actualContractIdentitySqlRejectsDeletedMissingAndCrossTenantRelationships() throws Exception {
        var configuration=new org.apache.ibatis.session.Configuration();String resource="mapper/migration/DppmsCommerceDetailMapper.xml";
        try(var input=getClass().getClassLoader().getResourceAsStream(resource)) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
        }
        try(var db=java.sql.DriverManager.getConnection("jdbc:h2:mem:package_contract_identity;MODE=MySQL")) {
            try(var sql=db.createStatement()) {
                sql.execute("CREATE TABLE com_shipment_package(id BIGINT,tenant_id BIGINT,shipment_contract_ref_id BIGINT,deleted INT)");
                sql.execute("CREATE TABLE com_shipment_contract_reference(id BIGINT,tenant_id BIGINT,source_system VARCHAR(32),source_record_key VARCHAR(128),deleted INT)");
                sql.execute("INSERT INTO com_shipment_contract_reference VALUES(70,1,'DPPMS','CONTRACT-KEY',0),(71,2,'DPPMS','OTHER-TENANT',0),(72,1,'DPPMS','DELETED',1)");
                sql.execute("INSERT INTO com_shipment_package VALUES(90,1,70,0),(91,1,70,1),(92,1,71,0),(93,1,72,0),(94,1,NULL,0),(95,2,71,0)");
            }
            for (long id:List.of(90L,91L,92L,93L,94L,95L)) {
                var parameters=Map.of("query",new DppmsCommerceDetailMapper.IdQuery(1L,id));
                var bound=configuration.getMappedStatement(DppmsCommerceDetailMapper.class.getName()+".selectPackageContractIdentity").getBoundSql(parameters);
                try(var sql=db.prepareStatement(bound.getSql())) {
                    int index=1;for(var mapping:bound.getParameterMappings()) sql.setObject(index++,configuration.newMetaObject(parameters).getValue(mapping.getProperty()));
                    try(var rows=sql.executeQuery()) {
                        assertEquals(id==90L,rows.next(),"package="+id);
                        if(id==90L) { assertEquals("DPPMS",rows.getString(1));assertEquals("CONTRACT-KEY",rows.getString(2));assertFalse(rows.next()); }
                    }
                }
            }
        }
    }

    private Execution execution(Map<String,Object> overrides) {
        var fields=new LinkedHashMap<String,Object>(Map.of("sourceKey","500","sourceSystem","CRM","executionNo","EX",
                "sourceCompanyCode","01","projectName","Original","projectAmount","100.00"));
        fields.putAll(overrides);
        return JsonUtils.parseObject(JsonUtils.toJsonString(fields),Execution.class);
    }
    private CrmExecutionOrderDO executionTarget() {
        var row=new CrmExecutionOrderDO();row.setId(50L);row.setVersion(7L);row.setPrimaryProjectId(100L);
        row.setSourceSystem("CRM");row.setExecutionNo("EX");row.setCompanyCode("001");row.setProjectName("Original");
        row.setProjectAmount(new BigDecimal("100.000000"));
        when(resolver.resolve(any())).thenReturn(List.of(contract(),NativeEntityMigrationRow.mapped("executions","500",
                new ExternalTargetMapping("COM","CrmExecutionOrder","com_crm_execution_order",50L,"BUSINESS_ENTITY",0))));
        when(mapper.selectExecutionForUpdate(any())).thenReturn(row);
        when(mapper.updateExecutionFields(any())).thenReturn(1);
        return row;
    }
    @Test void executionEnrichesFormalCompanyAndOriginalEmptyStringsWithoutMirrors() {
        var target=executionTarget();
        var request=new Command(identity(),List.of(),List.of(),List.of(),List.of(),List.of(execution(Map.of("channelName","","customerProjectName",""))));
        var result=service.importDetails(request);
        assertEquals(71L,target.getCompanyId());assertEquals("Official company",target.getCompanyName());
        assertEquals("",target.getChannelName());assertEquals("",target.getCustomerProjectName());
        assertEquals("001",target.getCompanyCode());assertEquals("Original",target.getProjectName());
        assertEquals(new BigDecimal("100.000000"),target.getProjectAmount());
        assertFalse(result.stream().filter(r->"executions".equals(r.domain())).findFirst().orElseThrow().targets().isEmpty());
        verify(mapper).updateExecutionFields(argThat(c->c.tenantId()==1L&&c.row().getId()==50L&&c.version()==7L&&c.projectId()==100L));
        clearInvocations(mapper);
        service.importDetails(request);
        verify(mapper,never()).updateExecutionFields(any());
        verifyNoInteractions(orders,lines,packages);
    }
    @Test void executionSourceContentDriftCannotBeReportedAsMappedOrFillMetadata() {
        var target=executionTarget();target.setProjectName("Current Owner value");
        var result=service.importDetails(new Command(identity(),List.of(),List.of(),List.of(),List.of(),List.of(execution(Map.of()))));
        var outcome=result.stream().filter(r->"executions".equals(r.domain())).findFirst().orElseThrow();
        assertTrue(outcome.targets().isEmpty());assertEquals("EXECUTION_SOURCE_CONTENT_CHANGED:projectName",outcome.reasonCode());
        assertNull(target.getCompanyId());assertEquals("Current Owner value",target.getProjectName());
        verify(mapper,never()).updateExecutionFields(any());
    }
    @Test void executionFillsMissingOfficialAttributeAndPreservesOtherFacts() {
        var target=executionTarget();target.setProjectName(null);
        var result=service.importDetails(new Command(identity(),List.of(),List.of(),List.of(),List.of(),List.of(execution(Map.of()))));
        assertNull(result.stream().filter(r->"executions".equals(r.domain())).findFirst().orElseThrow().reasonCode());
        assertEquals("Original",target.getProjectName());assertEquals(71L,target.getCompanyId());
        verify(mapper).updateExecutionFields(any());
    }
    @Test void executionSourceIdentityConflictAndVersionConflictCannotRewriteEvidence() {
        var target=executionTarget();target.setCompanyId(999L);
        var result=service.importDetails(new Command(identity(),List.of(),List.of(),List.of(),List.of(),List.of(execution(Map.of()))));
        assertEquals("EXECUTION_ORGANIZATION_REFERENCE_CONFLICT",result.stream().filter(r->"executions".equals(r.domain())).findFirst().orElseThrow().reasonCode());
        assertEquals(999L,target.getCompanyId());verify(mapper,never()).updateExecutionFields(any());
        target.setCompanyId(null);when(mapper.updateExecutionFields(any())).thenReturn(0);
        assertThrows(IllegalStateException.class,()->service.importDetails(new Command(identity(),List.of(),List.of(),List.of(),List.of(),List.of(execution(Map.of())))));
    }
    @Test void executionCannotBypassResolvedProjectAndSourceIdentity() {
        executionTarget();when(mapper.selectExecutionForUpdate(any())).thenReturn(null);
        var result=service.importDetails(new Command(identity(),List.of(),List.of(),List.of(),List.of(),List.of(execution(Map.of()))));
        assertEquals("TARGET_IDENTITY_CONFLICT",result.stream().filter(r->"executions".equals(r.domain())).findFirst().orElseThrow().reasonCode());
        verify(mapper).selectExecutionForUpdate(argThat(q->q.tenantId()==1L&&q.projectId()==100L&&q.id()==50L
                &&"CRM".equals(q.sourceSystem())&&"EX".equals(q.executionNo())));
        verify(mapper,never()).updateExecutionFields(any());
    }

    @Test void executionPageReadsBusinessMetadataWithTenantAndProjectScope() {
        var target=executionTarget();
        target.setChannelName("Channel");target.setCustomerProjectName("Customer Project");
        when(mapper.selectExecutions(any())).thenReturn(List.of(target));when(mapper.countExecutions(any())).thenReturn(1L);
        var page=service.page(new Query(1L,1L,100L,"executions",1,20));
        assertEquals("CRM|EX",page.list().getFirst().sourceKey());
        assertFalse(page.list().getFirst().values().containsKey("legacyRecordId"));
        assertFalse(page.list().getFirst().values().containsKey("sourceCompanyCode"));
        assertEquals("001",page.list().getFirst().values().get("companyCode"));
        assertEquals("Channel",page.list().getFirst().values().get("channelName"));
        assertEquals("Customer Project",page.list().getFirst().values().get("customerProjectName"));
        verify(mapper).selectExecutions(argThat(q->q.tenantId()==1L&&q.projectId()==100L&&q.offset()==0&&q.limit()==20));
        assertThrows(IllegalArgumentException.class,()->service.page(new Query(2L,1L,100L,"executions",1,20)));
        verify(mapper,times(1)).selectExecutions(any());
    }


    private Plan planSource() {
        var at=LocalDateTime.of(2020,1,2,3,4,5);
        return new Plan("301","TEST","BATCH","Payment plan","Acceptance",at,new BigDecimal("30"),at.plusDays(3),
                "Feedback","DPPMS",at,"ORIGINAL",at.plusDays(3),"ORIGINAL",at,null);
    }
    private ContractCollectionPlanDO planTarget() {
        var row=new ContractCollectionPlanDO();row.setId(401L);row.setTenantId(1L);row.setContractId(20L);
        row.setContractNo("TEST");row.setCollectionItemName("终验款");row.setAfterDays(30);row.setDeleted(false);
        row.setCreateTime(LocalDateTime.of(2020,1,1,0,0));row.setUpdateTime(row.getCreateTime());
        return row;
    }
    private Command planCommand(Long tenantId,Long projectId,Plan source) {
        var identity=new DppmsCommerceEntityResolveApi.Command(tenantId,1L,projectId,"TEST",
                List.of(),List.of(),List.of(),List.of(),"10");
        return new Command(identity,List.of(),List.of(),List.of(),List.of(source));
    }
    @Test void unauthorizedOrForeignTenantPlanImportNeverReachesIdentityLockOrBusinessWrites() {
        when(resolver.resolve(any())).thenThrow(new IllegalArgumentException("DPPMS_NATIVE_CONTRACT_NOT_ASSOCIATED"));
        assertEquals("DPPMS_NATIVE_CONTRACT_NOT_ASSOCIATED",assertThrows(IllegalArgumentException.class,
                ()->service.importDetails(planCommand(1L,200L,planSource()))).getMessage());
        assertEquals("DPPMS_NATIVE_TENANT_MISMATCH",assertThrows(IllegalArgumentException.class,
                ()->service.importDetails(planCommand(2L,200L,planSource()))).getMessage());
        verify(resolver,times(1)).resolve(any());verifyNoInteractions(mapper,orders,lines,packages);
    }
    @Test void secondProjectPageUsesOwnerContractScopeViaFormalContractWithoutInventingSourceOrProjectProperties() {
        var row=planTarget();
        when(mapper.selectPlans(any())).thenReturn(List.of(row));when(mapper.countPlans(any())).thenReturn(1L);
        var page=service.page(new Query(1L,1L,200L,"plans",1,20));
        assertEquals(1L,page.total());assertEquals(401L,page.list().getFirst().id());
        assertEquals("终验款",page.list().getFirst().values().get("collectionItemName"));
        assertNull(page.list().getFirst().sourceKey());assertFalse(page.list().getFirst().values().containsKey("projectId"));
        verify(mapper).selectPlans(argThat(q->q.tenantId()==1L&&q.projectId()==200L&&q.offset()==0&&q.limit()==20));
        verify(mapper).countPlans(argThat(q->q.tenantId()==1L&&q.projectId()==200L));
        assertThrows(IllegalArgumentException.class,()->service.page(new Query(2L,1L,200L,"plans",1,20)));
        verify(mapper,times(1)).selectPlans(any());
    }
    @Test void actualPlanPageAndCountSqlShareContractScopeAndRejectInvalidRelations() throws Exception {
        var configuration=new org.apache.ibatis.session.Configuration();
        String resource="mapper/migration/DppmsCommerceDetailMapper.xml";
        try(var input=getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
        }
        try(var db=java.sql.DriverManager.getConnection("jdbc:h2:mem:payment_plan_contract_scope;MODE=MySQL")) {
            try(var sql=db.createStatement()) {
                sql.execute("CREATE TABLE com_contract(id BIGINT,tenant_id BIGINT,deleted INT)");
                sql.execute("CREATE TABLE com_project_contract_relation(contract_id BIGINT,tenant_id BIGINT,project_id BIGINT,deleted INT,status VARCHAR(20),effective_from TIMESTAMP,effective_to TIMESTAMP)");
                sql.execute("CREATE TABLE com_contract_collection_plan(id BIGINT,tenant_id BIGINT,contract_id BIGINT,deleted INT,effective_from TIMESTAMP,effective_to TIMESTAMP)");
                sql.execute("INSERT INTO com_contract VALUES(20,1,0),(21,2,0),(22,1,1)");
                sql.execute("INSERT INTO com_contract_collection_plan VALUES(401,1,20,0,TIMESTAMP '2020-01-01 00:00:00',TIMESTAMP '2020-02-01 00:00:00'),(402,2,21,0,NULL,NULL),(403,1,22,0,NULL,NULL),(404,1,20,1,NULL,NULL)");
                sql.execute("INSERT INTO com_project_contract_relation VALUES(20,1,100,0,'ACTIVE',NULL,NULL),(20,1,200,0,'ACTIVE',NULL,NULL),(20,1,200,0,'ACTIVE',NULL,NULL),(20,1,201,0,'INACTIVE',NULL,NULL),(20,1,202,0,'ACTIVE',NULL,TIMESTAMP '2020-01-01 00:00:00'),(20,1,203,0,'ACTIVE',TIMESTAMP '2099-01-01 00:00:00',NULL),(20,1,204,1,'ACTIVE',NULL,NULL),(20,2,205,0,'ACTIVE',NULL,NULL),(22,1,206,0,'ACTIVE',NULL,NULL),(21,2,207,0,'ACTIVE',NULL,NULL)");
            }
            for(Long projectId:Arrays.asList(100L,200L,201L,202L,203L,204L,205L,206L,207L,999L,null)) {
                var query=new DppmsCommerceDetailMapper.PageQuery(1L,projectId,0,20);
                var ids=planSqlValues(db,configuration,"selectPlans",query);
                var counts=planSqlValues(db,configuration,"countPlans",query);
                boolean associated=Objects.equals(projectId,100L)||Objects.equals(projectId,200L);
                assertEquals(associated?List.of(401L):List.of(),ids,"project="+projectId);
                assertEquals(List.of(associated?1L:0L),counts,"count project="+projectId);
            }
            assertEquals(List.of(402L),planSqlValues(db,configuration,"selectPlans",
                    new DppmsCommerceDetailMapper.PageQuery(2L,207L,0,20)));
        }
    }
    private List<Long> planSqlValues(java.sql.Connection db,org.apache.ibatis.session.Configuration configuration,
                                    String method,DppmsCommerceDetailMapper.PageQuery query) throws Exception {
        var parameters=Map.of("query",query);
        var bound=configuration.getMappedStatement(DppmsCommerceDetailMapper.class.getName()+"."+method).getBoundSql(parameters);
        try(var sql=db.prepareStatement(bound.getSql())) {
            int index=1;
            for(var mapping:bound.getParameterMappings())
                sql.setObject(index++,configuration.newMetaObject(parameters).getValue(mapping.getProperty()));
            var result=new ArrayList<Long>();
            try(var rows=sql.executeQuery()){while(rows.next())result.add(rows.getLong(1));}
            return result;
        }
    }

}
