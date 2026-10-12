package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.customer.api.contact.FinalCustomerContactMigrationApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.Function;
import static cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectFinalCustomerContactImportServiceTest {
    final ProjectLegacyEntityMigrationService sources=mock(ProjectLegacyEntityMigrationService.class);
    final ProjectMasterMapper projects=mock(ProjectMasterMapper.class);
    final FinalCustomerContactMigrationApi contacts=mock(FinalCustomerContactMigrationApi.class);
    final SystemUserMigrationApi identities=mock(SystemUserMigrationApi.class);
    final ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    final PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    final ProjectFinalCustomerContactImportService service=new ProjectFinalCustomerContactImportService(sources,projects,contacts,identities,evidence,commands);
    final ProjectAccessActor actor=new ProjectAccessActor(1L,1L);
    final ProjectLegacyEntityMigrationService.Request sourceRequest=new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64));
    final ProjectFinalCustomerContactImportService.Request request=new ProjectFinalCustomerContactImportService.Request(sourceRequest,12L,99L,"b".repeat(64));
    final Map<String,String> raw=new TreeMap<>();
    ProjectLegacyRecordDO original;SuccessFacts facts;
    @BeforeEach void setup(){
        for(String field:List.of("id","projectId","projectType","memberRole","memberCode","memberName","phoneNum","email","fromFlag","createTime","createBy","updateTime","updateBy","effectiveFrom","effectiveTo"))raw.put(field,null);
        raw.put("id","256075");raw.put("projectId","51922");raw.put("projectType","10");raw.put("memberRole","60");raw.put("memberCode","");
        raw.put("memberName","董颖");raw.put("phoneNum","13500802833");raw.put("email","");raw.put("fromFlag","2");
        raw.put("createTime","2023-04-14 14:55:02");raw.put("effectiveFrom","2023-04-14 14:55:02");raw.put("createBy","z01010");
        original=new ProjectLegacyRecordDO();original.setSourceKey("256075");original.setDomainCode("members");original.setSourceTable("pm_project_member");payload();
        var source=new ProjectLegacySourceDO();source.setSourceProjectKey("51922");source.setTenantId(1L);
        var snapshot=new ProjectLegacySnapshotDO();snapshot.setId(20L);
        when(sources.loadCorrectionSource(100L,sourceRequest,actor)).thenReturn(new ProjectLegacyEntityMigrationService.Loaded(source,snapshot,Map.of("members",List.of(original))));
        var project=new ProjectMasterDO();project.setId(100L);project.setTenantId(1L);project.setVersion(12L);
        when(projects.selectByIdForUpdate(100L)).thenReturn(project);
        when(identities.getHistoricalIdentitiesByUsername(1L,Set.of("z01010"))).thenReturn(List.of(new SystemUserMigrationApi.HistoricalIdentity(40L,"z01010")));
        when(contacts.preview(any())).thenReturn(new FinalCustomerContactMigrationApi.Preview(new FinalCustomerContactMigrationApi.FinalCustomer(99L,100L,"实际最终用户","51922"),"b".repeat(64),0));
        when(contacts.carry(any())).thenReturn(new FinalCustomerContactMigrationApi.Result(List.of(new FinalCustomerContactMigrationApi.Target("256075",500L,"c".repeat(64)))));
        when(commands.execute(any(),anyString(),eq(ProjectFinalCustomerContactImportService.Report.class),any(),any())).thenAnswer(inv->{
            var report=inv.<Supplier<ProjectFinalCustomerContactImportService.Report>>getArgument(3).get();
            facts=inv.<Function<ProjectFinalCustomerContactImportService.Report,SuccessFacts>>getArgument(4).apply(report);
            return new ExecutionResult<>(Decision.NEW,report);
        });
    }
    void payload(){
        original.setPayloadJson(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(raw));
        var canonical=new TreeMap<String,Object>();canonical.put("domain",original.getDomainCode());canonical.put("key",original.getSourceKey());
        canonical.put("parentDomain",original.getParentDomain());canonical.put("parentKey",original.getParentSourceKey());canonical.put("sourceUpdatedAt",original.getSourceUpdatedAt());canonical.put("values",new TreeMap<>(raw));
        original.setChecksum(DigestUtil.sha256Hex(JsonUtils.toJsonString(canonical)));
    }
    @Test void previewResolvesOnlyExactFinalPartyAndAuditAccountWithoutBusinessWrites(){
        var preview=service.preview(100L,sourceRequest,actor);assertEquals(1,preview.sourceContactCount());assertEquals(99L,preview.owner().finalCustomer().id());
        verify(contacts,never()).carry(any());verifyNoInteractions(commands,evidence);
    }
    @Test void migrationUsesCusOwnerAndOriginalNullValuesWithoutProjectOrTaskChanges(){
        var result=service.migrate(100L,request,actor);assertEquals("NEW",result.decision());
        verify(contacts).carry(argThat(command->{var source=command.contacts().getFirst();return "40".equals(source.creator()) && source.updater()==null && "".equals(source.email()) && "".equals(source.contactCode());}));
        verify(evidence).reconcile(any(),any(),eq("members"),eq(List.of(original)),anyMap(),eq("FINAL_CUSTOMER_CONTACT_V1"));
        assertNull(facts.eventType());assertTrue(facts.businessEvents().isEmpty());verifyNoInteractions(projects);
    }
    @Test void missingAuditAccountBlocksBeforeAnyBusinessWrite(){
        when(identities.getHistoricalIdentitiesByUsername(anyLong(),anySet())).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));verifyNoInteractions(contacts,commands,evidence);
    }
    @Test void changedImmutableSourceCannotBeImported(){
        original.setPayloadJson(original.getPayloadJson().replace("董颖","改名"));
        assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));verifyNoInteractions(contacts,commands,evidence);
    }
    @Test void emptyOrNonContactDomainCannotGenerateContacts(){
        raw.put("memberRole","30");payload();
        assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));verify(contacts,never()).carry(any());
    }
    @Test void ownerCannotReturnTargetsForAnotherSource(){
        when(contacts.carry(any())).thenReturn(new FinalCustomerContactMigrationApi.Result(List.of(new FinalCustomerContactMigrationApi.Target("other",500L,"c".repeat(64)))));
        assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));verifyNoInteractions(evidence);
    }
    @Test void replayChecksOriginalBindingAndNeverInsertsAgain(){
        var report=service.migrate(100L,request,actor).report();
        when(commands.execute(any(),anyString(),eq(ProjectFinalCustomerContactImportService.Report.class),any(),any())).thenReturn(new ExecutionResult<>(Decision.REPLAY_COMPLETED,report));
        when(evidence.statuses(eq(1L),eq("51922"),eq(20L),anyList())).thenReturn(Map.of());
        assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));verify(contacts,times(1)).carry(any());verify(contacts,times(2)).verify(any(),eq(report.result()));
    }
    @Test void idempotencyConflictCannotFallThroughIntoBusinessWrites(){
        when(commands.execute(any(),anyString(),eq(ProjectFinalCustomerContactImportService.Report.class),any(),any())).thenReturn(new ExecutionResult<>(Decision.CONFLICT,null));
        assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));verify(contacts,never()).carry(any());verifyNoInteractions(evidence);
    }
}
