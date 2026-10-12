package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsContractHistoryImportApi.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.history.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.history.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.history.query.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DppmsContractHistoryImportServiceTest {
    ProjectScopeApi scopes=mock(ProjectScopeApi.class);
    ProjectContractHistoryMapper relations=mock(ProjectContractHistoryMapper.class);
    ContractReceivableMapper receivables=mock(ContractReceivableMapper.class);
    DppmsContractHistoryImportService service=new DppmsContractHistoryImportService(scopes,relations,receivables);
    Map<String,ProjectContractHistoryDO> relationRows=new LinkedHashMap<>();
    Map<String,ContractReceivableDO> headRows=new LinkedHashMap<>();
    String hash="a".repeat(64);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(100L,1L,Set.of(100L),Set.of()));
        when(relations.selectExactCurrentContractIds(any())).thenReturn(List.of(20L));
        when(relations.selectSourceRevisionForUpdate(any())).thenAnswer(i->{var q=(ContractHistoryRevisionQuery)i.getArgument(0);return relationRows.get(q.projectId()+":"+q.sourceRowId()+":"+q.sourceRevisionHash());});
        when(receivables.selectSourceRevisionForUpdate(any())).thenAnswer(i->{var q=(ContractReceivableRevisionQuery)i.getArgument(0);return headRows.get(q.sourceRowId()+":"+q.sourceRevisionHash());});
        when(relations.insert(any(ProjectContractHistoryDO.class))).thenAnswer(i->{var r=(ProjectContractHistoryDO)i.getArgument(0);relationRows.put(r.getProjectId()+":"+r.getSourceRowId()+":"+r.getSourceRevisionHash(),r);return 1;});
        when(receivables.insert(any(ContractReceivableDO.class))).thenAnswer(i->{var r=(ContractReceivableDO)i.getArgument(0);headRows.put(r.getSourceRowId()+":"+r.getSourceRevisionHash(),r);return 1;});
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    Relation relation(String checksum) {
        return Relation.builder().sourceKey("7").sourceChecksum(checksum).sourceProjectGroupId(10L)
                .contractNo("C").projectGroupCode(" Original Group ").sourceCreatedBy(" Original User ")
                .sourceUpdatedAt(LocalDateTime.of(2020,1,2,3,4)).build();
    }
    Receivable head(String checksum,Double ratio,String customer) {
        return Receivable.builder().sourceKey("8").sourceChecksum(checksum).contractNo("C")
                .contractAmount(new BigDecimal("100.00")).deliveredAmount(BigDecimal.ZERO).collectedAmount(BigDecimal.ZERO)
                .sourceCollectedMoneyRatio(ratio).customerName(customer).systemSourceKey(123).industryCode(456).expendSourceKey(789)
                .sourceCreatedBy(" Raw Person ").sourceUpdatedAt(LocalDateTime.of(2020,1,2,3,4)).build();
    }
    Command command(String checksum,Receivable head){return new Command(1L,1L,100L,"ORIGINAL_PROJECT",List.of(relation(checksum)),List.of(head));}
    @Test void preservesNullableOriginalDoubleAuditAndExplicitNullDivisionKeysWithoutChangingAuthority() {
        var result=service.importHistory(command(hash,head(hash,0.123456789," Raw Customer ")));
        assertEquals(2,result.size());assertTrue(result.stream().allMatch(r->!r.targets().isEmpty()));
        var row=headRows.values().iterator().next();assertEquals(0.123456789,row.getSourceCollectedMoneyRatio());
        assertNull(row.getCollectedRatio());assertEquals(" Raw Customer ",row.getCustomerName());
        assertEquals(" Raw Person ",row.getSourceCreatedBy());assertNull(row.getSourceCreatedAt());assertNull(row.getSourceDataSource());
        assertEquals("123",row.getSystemSourceKey());assertEquals("456",row.getIndustryCode());assertEquals("789",row.getExpendSourceKey());
        assertNull(row.getCompanyCode());assertNull(row.getContractId());assertEquals("PENDING_COMPANY",row.getMappingStatus());
        var division=JsonUtils.parseObject(row.getOriginalDivisionValues(),JsonNode.class);
        for(var field:List.of("systemSourceKey","expendSourceKey","industryName")){assertTrue(division.has(field));assertTrue(division.get(field).isNull());}
        var member=relationRows.values().iterator().next();assertEquals(20L,member.getContractId());
        assertEquals(" Original Group ",member.getProjectGroupCode());assertEquals("ORIGINAL_PROJECT",member.getSourceProjectKey());
    }
    @Test void replayKeepsIdsAndNewRevisionAppendsWhileOldBusinessHistoryStaysUnchanged() {
        var first=service.importHistory(command(hash,head(hash,0.5,"First")));
        var replay=service.importHistory(command(hash,head(hash,0.5,"First")));
        assertEquals(first,replay);assertEquals(1,headRows.size());assertEquals(1,relationRows.size());
        var next="b".repeat(64);service.importHistory(command(next,head(next,0.75,"Later")));
        assertEquals(2,headRows.size());assertEquals(2,relationRows.size());
        assertEquals("First",headRows.get("8:"+hash).getCustomerName());
        assertEquals(new BigDecimal("0.500000"),headRows.get("8:"+hash).getCollectedRatio());
        verify(receivables,times(2)).insert(any(ContractReceivableDO.class));verify(receivables,never()).updateById(any(ContractReceivableDO.class));
        verify(relations,never()).updateById(any(ProjectContractHistoryDO.class));
    }
    @Test void sameRevisionCannotDropOrReplaceAnyPreviouslyRetainedRawValue() {
        service.importHistory(command(hash,head(hash,0.5," First ")));
        var error=assertThrows(IllegalArgumentException.class,()->service.importHistory(command(hash,head(hash,0.5,null))));
        assertTrue(error.getMessage().contains("IMMUTABLE_CONFLICT:customerName"));assertEquals(" First ",headRows.get("8:"+hash).getCustomerName());
        assertEquals(1,headRows.size());verify(receivables,times(1)).insert(any(ContractReceivableDO.class));
    }
    @Test void missingOrAmbiguousCurrentContractStillPreservesHistoricalMembershipWithoutActivatingIt() {
        when(relations.selectExactCurrentContractIds(any())).thenReturn(List.of(20L,21L));
        service.importHistory(command(hash,head(hash,null,null)));assertNull(relationRows.values().iterator().next().getContractId());
        assertEquals(1,relationRows.size());assertEquals(1,headRows.size());
    }
    @Test void sourceHeadOutsideTheExactSourceContractChainRejectsBeforeAnyInsert() {
        var outside=Receivable.builder().sourceKey("8").sourceChecksum(hash).contractNo("OTHER")
                .contractAmount(BigDecimal.ZERO).deliveredAmount(BigDecimal.ZERO).collectedAmount(BigDecimal.ZERO).build();
        assertThrows(IllegalArgumentException.class,()->service.importHistory(command(hash,outside)));
        verifyNoInteractions(receivables);verify(relations,never()).insert(any(ProjectContractHistoryDO.class));
    }
    @Test void tenantMismatchAndPlaceholderOnlyProjectScopeCannotReadOrWriteHistory() {
        TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->service.importHistory(command(hash,head(hash,null,null))));
        TenantContextHolder.setTenantId(1L);when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(100L,1L,Set.of(),Set.of(100L)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.importHistory(command(hash,head(hash,null,null))));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.page(new Query(1L,1L,100L,"contracts",1,20)));
        verifyNoInteractions(receivables,relations);
    }
    @Test void deletedSourceRevisionCannotBeRestoredByReplay() {
        service.importHistory(command(hash,head(hash,0.5,"First")));headRows.get("8:"+hash).setDeleted(true);
        assertThrows(IllegalArgumentException.class,()->service.importHistory(command(hash,head(hash,0.5,"First"))));
        assertEquals(1,headRows.size());assertTrue(headRows.get("8:"+hash).getDeleted());
    }
    @Test void pageUsesAuthorizedProjectAndIncludesExplicitNullOriginalProperties() {
        service.importHistory(command(hash,head(hash,null,null)));
        when(receivables.selectProjectPage(any())).thenReturn(new ArrayList<>(headRows.values()));when(receivables.countProject(any())).thenReturn(1L);
        var page=service.page(new Query(1L,1L,100L,"contract-receivable-history",2,20));
        assertEquals(1,page.total());assertEquals("8",page.rows().getFirst().sourceKey());
        assertTrue(page.rows().getFirst().values().containsKey("sourceDataSource"));assertNull(page.rows().getFirst().values().get("sourceDataSource"));
        verify(receivables).selectProjectPage(argThat(q->q.tenantId()==1L&&q.projectId()==100L&&q.offset()==20&&q.limit()==20));
        verify(scopes).resolveCurrent(argThat(q->ProjectScopeApi.ACTION_VIEW.equals(q.actionCode())));
    }
}
