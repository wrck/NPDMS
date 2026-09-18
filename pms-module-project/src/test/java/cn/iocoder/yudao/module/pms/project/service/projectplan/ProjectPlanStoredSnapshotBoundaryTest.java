package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_PLAN_CHANGE_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_PLAN_VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectPlanStoredSnapshotBoundaryTest {
    // Reuse the package fixture so authorization and command semantics remain identical to existing tests.
    private final ProjectPlanDraftServiceTest fixture = new ProjectPlanDraftServiceTest();

    @BeforeEach void setUp() { fixture.setup(); }
    @AfterEach void tearDown() { fixture.clear(); }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "[]", "{\"executionSchemaVersion\":null}",
            "{\"executionSchemaVersion\":\"2\"}", "{\"executionSchemaVersion\":2.0}",
            "{\"executionSchemaVersion\":3}", "{\"executionSchemaVersion\":4294967298}"})
    void neitherPreviewNorActivationCanReinterpretAnInvalidEffectiveSnapshot(String json) {
        fixture.effective.setExecutionSnapshot(json);
        for (boolean publishing : new boolean[]{false, true}) {
            var error = assertThrows(ServiceException.class,
                    () -> fixture.service.prepare(9L, 52L, 0, 1L, publishing));
            assertEquals(PROJECT_PLAN_CHANGE_INVALID.getCode(), error.getCode());
        }
        verifyNoInteractions(fixture.compiler, fixture.dependencies, fixture.ruleValidator,
                fixture.executions, fixture.graph, fixture.commands, fixture.deliverables);
        assertEquals(51L, fixture.project.getActivePlanVersionId());
        assertEquals(json, fixture.effective.getExecutionSnapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ID", "TENANT", "PROJECT", "STATUS"})
    void rejectsAnEffectiveRowOutsideTheLockedProjectVersion(String field) {
        switch (field) {
            case "ID" -> fixture.effective.setId(99L);
            case "TENANT" -> fixture.effective.setTenantId(8L);
            case "PROJECT" -> fixture.effective.setProjectId(10L);
            case "STATUS" -> fixture.effective.setStatus("DRAFT");
            default -> throw new AssertionError(field);
        }
        var error = assertThrows(ServiceException.class,
                () -> fixture.service.prepare(9L, 52L, 0, 1L, true));
        assertEquals(PROJECT_PLAN_VERSION_CONFLICT.getCode(), error.getCode());
        verifyNoInteractions(fixture.compiler, fixture.executions, fixture.graph, fixture.commands);
    }
}
