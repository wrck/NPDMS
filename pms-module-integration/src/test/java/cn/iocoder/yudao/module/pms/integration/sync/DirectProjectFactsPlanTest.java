package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.project.api.migration.ProjectFactsMigrationApi.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import org.junit.jupiter.api.*;
import cn.iocoder.yudao.module.pms.project.api.migration.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectProjectFactsPlanTest {
    ProjectMigrationIdentityContextApi projects=mock(ProjectMigrationIdentityContextApi.class);
    DirectProjectPartyMapper parties=mock(DirectProjectPartyMapper.class);EntityExtensionApi extensions=mock(EntityExtensionApi.class);
    javax.sql.DataSource dataSource=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:p19-plan-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
    org.springframework.transaction.support.TransactionTemplate tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
    DirectProjectFactsPersistence service=new DirectProjectFactsPersistence(dataSource,projects,parties,extensions);
    ProjectMigrationIdentityContextApi.Query identity=new ProjectMigrationIdentityContextApi.Query(1L,7L,19L,3L,"8",9L);
    @BeforeEach void setup(){tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);when(parties.selectMigrationSourceForUpdate(any())).thenReturn(List.of());}
    static Map<String,String> party(){
        var f=new LinkedHashMap<String,String>();ProjectFactsMigrationApi.PARTY_COLUMNS.forEach(name->f.put(name,null));
        f.put("id","11");f.put("projectId","8");f.put("partyRole","10");f.put("partyCode","A");f.put("partyName","服务渠道");
        f.put("createTime","2023-01-01T12:34:56.123");return f;
    }
    Command command(Entry entry){return new Command(identity,21L,List.of(entry));}
    @Test void carriesServiceChannelAndNullActorsThroughExistingColumns() {
        when(parties.insertMigrationParty(any())).thenReturn(1);
        var result=apply(command(new Entry("pm_project_related_party","11",party(),null,null,null,null))).getFirst();
        assertEquals("CREATED",result.action());assertEquals("10",result.fields().get("partyRole"));
        assertEquals(result.fields().get("createTime"),result.fields().get("updateTime"));assertEquals(20,result.fields().size());
        assertTrue(((tools.jackson.databind.JsonNode)result.fields().get("creator")).isNull());
        verify(projects).lockAndRevalidate(identity);
    }
    @Test void refusesUnreviewedExistingParty() {
        var old=new DirectProjectPartyRow();old.setId(13L);old.setProjectId(19L);old.setDeleted(false);
        when(parties.selectMigrationSourceForUpdate(any())).thenReturn(List.of(old));
        assertThrows(IllegalArgumentException.class,()->apply(command(new Entry("pm_project_related_party","11",party(),null,null,null,null))));
        verify(parties,never()).updateMigrationParty(any());
    }
    @Test void projectBindingFailurePreventsAllWrites() {
        doThrow(new IllegalArgumentException("changed")).when(projects).lockAndRevalidate(identity);
        assertThrows(IllegalArgumentException.class,()->apply(command(new Entry("pm_project_related_party","11",party(),null,null,null,null))));
        verifyNoInteractions(parties,extensions);
    }
    static Map<String,String> states(){
        var f=new LinkedHashMap<String,String>();ProjectFactsMigrationApi.STATE_COLUMNS.forEach(name->f.put(name,null));
        f.put("projectId","8");f.put("shipmentState","-1");f.put("shipmentTime","2023-01-01T01:02:03");
        f.put("executionState","50");f.put("executionStateTime","2023-02-01T01:02:03");
        f.put("closeProcessState","100");f.put("projectPlanState","48");return f;
    }
    @Test void savesOnlyApprovedStatusFieldsAndKeepsOtherFormalAttributes() {
        when(extensions.read(any(),any())).thenReturn(new EntityExtensionApi.Values(21L,Map.of("existing_attribute","keep"),1));
        var result=apply(command(new Entry("pm_project_state","8",states(),null,null,null,null))).getFirst();
        var captor=org.mockito.ArgumentCaptor.forClass(EntityExtensionApi.Save.class);verify(extensions).save(captor.capture());
        assertEquals("keep",captor.getValue().fields().get("existing_attribute"));assertEquals(5,captor.getValue().fields().size());
        assertFalse(captor.getValue().fields().containsKey("closeProcessState"));assertEquals(19L,result.targetId());
    }
    @Test void laterManualStatusChangeIsProtected() {
        when(extensions.read(any(),any())).thenReturn(new EntityExtensionApi.Values(21L,Map.of("execution_status","90"),2));
        assertThrows(IllegalArgumentException.class,()->apply(command(new Entry("pm_project_state","8",states(),null,null,19L,"old"))));
        verify(extensions,never()).save(any());
    }
    List<Result> apply(Command command){return tx.execute(status->service.apply(command));}
}
