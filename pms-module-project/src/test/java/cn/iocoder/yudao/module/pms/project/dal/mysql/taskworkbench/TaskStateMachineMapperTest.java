package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateMachineRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateTransitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachinePublishUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachinePublishedQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateTransitionQuery;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = TaskWorkbenchMySqlTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TaskStateMachineMapperTest extends TaskWorkbenchMySqlTestSupport {

    @Resource
    private TaskStateMachineMapper mapper;

    @BeforeEach
    void setUp() {
        createFixture(1);
    }

    @Test
    void shouldReadPublishedDefinitionAndRequireFrozenTransition() {
        TaskStateMachineDefinition definition = mapper.selectPublished(publishedQuery(0L));
        assertEquals(publishedRevisionId, definition.revision().getId());
        assertEquals(8, definition.transitions().size());
        definition.validateForPublish();

        TaskStateTransitionDO transition = mapper.requireTransition(new TaskStateTransitionQuery(
                0L, publishedRevisionId, "PENDING_START", "START"));
        assertEquals("IN_PROGRESS", transition.getToStatusCode());
        assertThrows(IllegalArgumentException.class, () -> mapper.requireTransition(
                new TaskStateTransitionQuery(0L, publishedRevisionId, "PENDING_START", "UNKNOWN")));
        assertNull(mapper.selectPublished(publishedQuery(999_999L)));
    }

    @Test
    void shouldRejectMissingCoreUnknownRoleAndUnknownCondition() {
        TaskStateMachineDefinition published = mapper.selectPublished(publishedQuery(0L));

        List<TaskStateTransitionDO> missingCore = new ArrayList<>(published.transitions());
        missingCore.removeFirst();
        assertThrows(IllegalArgumentException.class,
                () -> new TaskStateMachineDefinition(published.revision(), missingCore).validateForPublish());

        List<TaskStateTransitionDO> changedCoreMapping = copyTransitions(published.transitions());
        changedCoreMapping.stream().filter(transition -> "ASSIGN".equals(transition.getActionCode()))
                .findFirst().orElseThrow().setStandardStatusMapping("CLOSED");
        assertThrows(IllegalArgumentException.class,
                () -> new TaskStateMachineDefinition(
                        published.revision(), changedCoreMapping).validateForPublish());

        List<TaskStateTransitionDO> unknownRole = copyTransitions(published.transitions());
        unknownRole.getFirst().setAllowedRoleCode("UNKNOWN_ROLE");
        assertThrows(IllegalArgumentException.class,
                () -> new TaskStateMachineDefinition(published.revision(), unknownRole).validateForPublish());

        List<TaskStateTransitionDO> unknownCondition = copyTransitions(published.transitions());
        unknownCondition.getFirst().setEntryCondition("{\"schemaVersion\":1,\"unknown\":true}");
        assertThrows(IllegalArgumentException.class,
                () -> new TaskStateMachineDefinition(published.revision(), unknownCondition).validateForPublish());
    }

    @Test
    void shouldPublishValidDraftOnceUnderConcurrency() throws Exception {
        TaskStateMachineRevisionDO draft = createDraftFromPublished();
        TaskStateMachinePublishUpdate update = new TaskStateMachinePublishUpdate(
                0L, draft.getId(), 0, 9001L, LocalDateTime.now(), "fproj007-task3-test");

        List<Integer> results;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactionTemplate.execute(status -> mapper.publishIfValid(update)));
            var second = executor.submit(() -> transactionTemplate.execute(status -> mapper.publishIfValid(update)));
            results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }

        assertEquals(1, results.stream().mapToInt(Integer::intValue).sum());
        assertTrue(results.contains(0));
        assertEquals("PUBLISHED", jdbcTemplate.queryForObject(
                "SELECT status FROM proj_task_state_machine_revision WHERE id=?", String.class, draft.getId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT version FROM proj_task_state_machine_revision WHERE id=?", Integer.class, draft.getId()));
        assertEquals(publishedRevisionId, jdbcTemplate.queryForObject(
                "SELECT state_machine_revision_id FROM proj_project_task WHERE id=?",
                Long.class, taskIds.getFirst()));
    }

    private TaskStateMachineRevisionDO createDraftFromPublished() {
        TaskStateMachineDefinition published = mapper.selectPublished(publishedQuery(0L));
        Integer nextRevisionNo = jdbcTemplate.queryForObject(
                "SELECT MAX(revision_no) + 1 FROM proj_task_state_machine_revision WHERE tenant_id=0",
                Integer.class);
        TaskStateMachineRevisionDO draft = new TaskStateMachineRevisionDO();
        draft.setTenantId(0L);
        draft.setRevisionNo(nextRevisionNo);
        draft.setStatus("DRAFT");
        draft.setEffectiveFrom(LocalDateTime.now());
        draft.setVersion(0);
        draft.setId(IdUtil.getSnowflakeNextId());
        mapper.insertDraft(draft);
        createdRevisionIds.add(draft.getId());
        for (TaskStateTransitionDO transition : published.transitions()) {
            TaskStateTransitionDO copy = copyTransitions(List.of(transition)).getFirst();
            copy.setId(IdUtil.getSnowflakeNextId());
            copy.setTenantId(0L);
            copy.setRevisionId(draft.getId());
            copy.setVersion(0);
            copy.setCreator("test");
            copy.setUpdater("test");
            assertEquals(1, mapper.insertTransition(copy));
        }
        return draft;
    }

    private TaskStateMachinePublishedQuery publishedQuery(long tenantId) {
        return TaskStateMachinePublishedQuery.builder()
                .tenantId(tenantId).effectiveAt(LocalDateTime.now()).build();
    }

    private static List<TaskStateTransitionDO> copyTransitions(List<TaskStateTransitionDO> source) {
        return source.stream().map(transition -> {
            TaskStateTransitionDO copy = new TaskStateTransitionDO();
            copy.setFromStatusCode(transition.getFromStatusCode());
            copy.setActionCode(transition.getActionCode());
            copy.setToStatusCode(transition.getToStatusCode());
            copy.setStandardStatusMapping(transition.getStandardStatusMapping());
            copy.setAllowedRoleCode(transition.getAllowedRoleCode());
            copy.setEntryCondition(transition.getEntryCondition());
            copy.setExitCondition(transition.getExitCondition());
            return copy;
        }).toList();
    }
}
