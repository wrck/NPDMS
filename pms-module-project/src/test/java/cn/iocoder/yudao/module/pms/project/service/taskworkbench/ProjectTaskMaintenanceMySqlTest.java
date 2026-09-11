package cn.iocoder.yudao.module.pms.project.service.taskworkbench;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskMaintenanceMapper.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.*;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="skipITs",matches="false")
@SpringBootTest(classes=TaskWorkbenchMySqlTestApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
class ProjectTaskMaintenanceMySqlTest extends TaskWorkbenchMySqlTestSupport {
    @Resource ProjectTaskMaintenanceMapper maintenance;
    @Resource ProjectTaskRuntimeMapper runtime;
    @BeforeEach void setup() {
        assertEquals("npdms_test",jdbcTemplate.queryForObject("SELECT DATABASE()",String.class));
        TenantContextHolder.setTenantId(0L); createFixture(3);
    }
    @AfterEach void clearRoles() {
        jdbcTemplate.update("DELETE FROM proj_project_task_responsible WHERE tenant_id=0 AND project_id=?",projectId);
        TenantContextHolder.clear();
    }
    private TaskResponsibleRow responsible(long id, long taskId, long userId) {
        var row = new TaskResponsibleRow(); row.setId(id); row.setProjectId(projectId); row.setProjectTaskId(taskId);
        row.setUserId(userId); row.setTenantId(0L); row.setAssignedBy(9L); row.setEffectiveFrom(LocalDateTime.now()); row.setReason("独立测试职责"); return row;
    }
    @Test void responsibleHistoryAndTaskVersionRollbackTogether() {
        long taskId=taskIds.getFirst(); var first=responsible(projectId+100,taskId,11);
        assertEquals(1,maintenance.insertResponsible(first));
        assertThrows(RuntimeException.class,()->maintenance.insertResponsible(responsible(projectId+101,taskId,12)));
        assertThrows(IllegalStateException.class,()->transactionTemplate.executeWithoutResult(tx->{
            var current=maintenance.selectCurrentForUpdate(new TaskQuery(0L,taskId));
            maintenance.closeResponsible(new CloseResponsible(0L,current.getId(),current.getVersion(),LocalDateTime.now(),"9"));
            maintenance.insertResponsible(responsible(projectId+102,taskId,12));
            runtime.incrementTaskVersionIfMatch(new TaskVersionUpdate(0L,taskId,0,"9"));
            throw new IllegalStateException("injected save failure");
        }));
        var history=maintenance.selectHistory(new HistoryQuery(0L,taskId,0,20));
        assertEquals(1,history.size()); assertNull(history.getFirst().getEffectiveTo()); assertEquals(11L,history.getFirst().getUserId());
        assertEquals(0,jdbcTemplate.queryForObject("SELECT version FROM proj_project_task WHERE id=?",Integer.class,taskId));
        transactionTemplate.executeWithoutResult(tx->{
            var current=maintenance.selectCurrentForUpdate(new TaskQuery(0L,taskId));
            maintenance.closeResponsible(new CloseResponsible(0L,current.getId(),current.getVersion(),LocalDateTime.now(),"9"));
            maintenance.insertResponsible(responsible(projectId+103,taskId,12));
        });
        assertEquals(2,maintenance.selectHistory(new HistoryQuery(0L,taskId,0,20)).size());
    }
    @Test void longRichTextAndPlainHistoryAreDistinctAndTenantGuarded() {
        long taskId=taskIds.getFirst();
        jdbcTemplate.update("UPDATE proj_project_task SET description=? WHERE id=?","原文本 <标签>",taskId);
        assertEquals("PLAIN",maintenance.selectDescription(new TaskQuery(0L,taskId)).descriptionFormat());
        String html=TaskRichText.clean("<p><strong>"+"任务说明".repeat(200)+"</strong><script>alert(1)</script></p>");
        assertEquals(1,maintenance.updateDescription(new DescriptionUpdate(0L,taskId,0,"9",html,"HTML")));
        var saved=maintenance.selectDescription(new TaskQuery(0L,taskId));
        assertEquals(html,saved.description()); assertEquals("HTML",saved.descriptionFormat());
        assertFalse(saved.description().contains("<script"));
        assertNull(maintenance.selectDescription(new TaskQuery(1L,taskId)));
        assertEquals(0,maintenance.updateDescription(new DescriptionUpdate(1L,taskId,1,"9","wrong","HTML")));
    }
    @Test void ownerGetsOwnTaskAndAncestorVisibilityButDoesNotBecomeExecutor() {
        long parent=taskIds.get(0), child=taskIds.get(2);
        runtime.insertNewTaskPaths(new NewTaskTreePathInsert(0L,projectId,parent,null,"9"));
        jdbcTemplate.update("UPDATE proj_project_task SET parent_task_id=?,root_task_id=?,tree_depth=1,status='PENDING_START' WHERE id=?",parent,parent,child);
        runtime.insertNewTaskPaths(new NewTaskTreePathInsert(0L,projectId,child,parent,"9"));
        maintenance.insertResponsible(responsible(projectId+100,child,11));
        jdbcTemplate.update("INSERT INTO proj_project_task_assignment(id,project_task_id,assignee_user_id,effective_from,assigned_by,reason,tenant_id) VALUES(?,?,12,NOW(),9,'执行测试',0)",projectId+101,child);
        assertEquals(List.of(child),runtime.selectFullTaskIds(new TaskVisibilityQuery(0L,projectId,11L,false)));
        assertEquals(Set.of(parent,child),new HashSet<>(runtime.selectVisibleTaskIds(new TaskVisibilityQuery(0L,projectId,11L,false))));
        var result=runtime.selectTree(ProjectTaskTreeQuery.builder().tenantId(0L).projectIds(Set.of(projectId))
                .visibilityQuery(new TaskVisibilityQuery(0L,projectId,11L,false)).mode(ProjectTaskTreeQuery.Mode.LOCATE)
                .status("PENDING_START").responsibleUserId(11L).executorUserId(12L).pageSize(20).build());
        assertEquals(1,result.size()); assertEquals(child,result.getFirst().getId());
        maintenance.insertResponsible(responsible(projectId+102,parent,12));
        assertEquals(List.of(),runtime.selectTree(ProjectTaskTreeQuery.builder().tenantId(0L).projectIds(Set.of(projectId))
                .visibilityQuery(new TaskVisibilityQuery(0L,projectId,11L,false)).mode(ProjectTaskTreeQuery.Mode.LOCATE)
                .responsibleUserId(12L).pageSize(20).build()));
        assertEquals(12L,maintenance.selectExecutorHistory(new HistoryQuery(0L,child,0,1)).getFirst().getUserId());
    }
}
