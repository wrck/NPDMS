package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class ProjectManualCreationConcurrencyMySqlIntegrationTest extends ProjectManualCreationMySqlTestSupport {

    @Test
    void sameKeyAndDigestCreatesOneProjectAndReplaysOneResult() throws Exception {
        String key = KEY_PREFIX + UUID.randomUUID();
        String digest = sha256("same-request-" + UUID.randomUUID());
        String projectName = DATA_PREFIX + UUID.randomUUID();
        List<Outcome> outcomes = runConcurrently(
                () -> applicationService.create(newCommand(key, digest, projectName), newActor()),
                () -> applicationService.create(newCommand(key, digest, projectName), newActor()));

        assertTrue(outcomes.stream().allMatch(outcome -> outcome.failure() == null));
        ManualProjectCreateResult first = outcomes.get(0).result();
        ManualProjectCreateResult second = outcomes.get(1).result();
        assertEquals(first.id(), second.id());
        assertEquals(first.projectCode(), second.projectCode());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project WHERE id = ?", Long.class, first.id()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_idempotency_record WHERE idempotency_key = ? AND status = 'COMPLETED'",
                Long.class, key));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE aggregate_key = ? AND result_code = 'SUCCESS'",
                Long.class, String.valueOf(first.id())));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_outbox_event WHERE aggregate_key = ? AND event_type = 'ProjectCreated'",
                Long.class, String.valueOf(first.id())));
    }

    @Test
    void sameKeyAndDifferentDigestCreatesOneProjectAndReturnsOneConflict() throws Exception {
        String key = KEY_PREFIX + UUID.randomUUID();
        List<Outcome> outcomes = runConcurrently(
                () -> applicationService.create(newCommand(key, sha256("request-a")), newActor()),
                () -> applicationService.create(newCommand(key, sha256("request-b")), newActor()));

        List<Outcome> successes = outcomes.stream().filter(outcome -> outcome.failure() == null).toList();
        List<Outcome> failures = outcomes.stream().filter(outcome -> outcome.failure() != null).toList();
        assertEquals(1, successes.size());
        assertEquals(1, failures.size());
        ServiceException conflict = assertInstanceOf(ServiceException.class, failures.get(0).failure());
        assertEquals(PMS_IDEMPOTENCY_KEY_CONFLICT.getCode(), conflict.getCode());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_idempotency_record WHERE idempotency_key = ? AND status = 'COMPLETED'",
                Long.class, key));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project WHERE project_name LIKE ?", Long.class, DATA_PREFIX + "%"));
    }

    private List<Outcome> runConcurrently(Callable<ManualProjectCreateResult> first,
                                          Callable<ManualProjectCreateResult> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Outcome> left = executor.submit(awaitStart(ready, start, first));
            Future<Outcome> right = executor.submit(awaitStart(ready, start, second));
            ready.await();
            start.countDown();
            return List.of(left.get(), right.get());
        }
    }

    private Callable<Outcome> awaitStart(CountDownLatch ready, CountDownLatch start,
                                         Callable<ManualProjectCreateResult> operation) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                return new Outcome(operation.call(), null);
            } catch (Throwable failure) {
                return new Outcome(null, failure);
            }
        };
    }

    private record Outcome(ManualProjectCreateResult result, Throwable failure) {
    }
}
