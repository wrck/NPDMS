package cn.iocoder.yudao.module.pms.project.api.deliveryscope;

import cn.iocoder.yudao.module.pms.project.api.deliveryscope.dto.ProjectDeliveryScopeQualificationFact;
import cn.iocoder.yudao.module.pms.project.api.deliveryscope.dto.ProjectDeliveryScopeQualificationQuery;
import cn.iocoder.yudao.module.pms.project.api.deliveryscope.dto.ProjectDeliveryScopeQualificationRevalidationQuery;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectDeliveryScopeQualificationFactApiContractTest {

    private static cn.hutool.json.JSONObject machineContract;

    @BeforeAll
    static void loadMachineContract() throws IOException {
        Path moduleDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path repositoryDirectory = Files.exists(moduleDirectory.resolve("specs"))
                ? moduleDirectory : moduleDirectory.resolve("..").normalize();
        machineContract = JSONUtil.parseObj(Files.readString(repositoryDirectory.resolve(
                "specs/features/F-COM-001-project-qualification-contract.json"), StandardCharsets.UTF_8));
    }

    @Test
    void exposesOnlyInspectAndLockAndRevalidate() {
        assertEquals(List.of("inspect", "lockAndRevalidate"), Arrays.stream(
                        ProjectDeliveryScopeQualificationFactApi.class.getDeclaredMethods())
                .map(Method::getName).sorted().toList());
    }

    @Test
    void freezesEveryComparableQualificationAxis() {
        assertEquals(List.of("actorId", "expectedCurrentStage", "expectedLifecycleStatus",
                        "expectedParticipantFactVersion", "expectedProjectVersion", "expectedRootProjectId",
                        "expectedTreeVersion", "projectId", "tenantId"),
                Arrays.stream(ProjectDeliveryScopeQualificationRevalidationQuery.class.getRecordComponents())
                        .map(component -> component.getName()).sorted().toList());
        assertEquals(List.of("currentManagerUserId", "currentStage", "lifecycleStatus",
                        "participantFactVersion", "projectId", "projectVersion", "rootProjectId", "tenantId",
                        "treeVersion"),
                Arrays.stream(ProjectDeliveryScopeQualificationFact.class.getRecordComponents())
                        .map(component -> component.getName()).sorted().toList());
    }

    @Test
    void keepsStablePublicFailureClasses() {
        assertEquals(List.of("DATA_SCOPE_DENIED", "FACT_STALE", "INVALID_REQUEST", "OWNER_DATA_CORRUPTED",
                        "PROVIDER_UNAVAILABLE", "SUBJECT_NOT_ELIGIBLE", "TENANT_CONTEXT_MISMATCH"),
                Arrays.stream(ProjectDeliveryScopeQualificationFactException.Code.values())
                        .map(Enum::name).sorted().toList());
    }

    @Test
    void inspectUsesTrustedTenantProjectAndActorOnly() {
        assertEquals(List.of("actorId", "projectId", "tenantId"),
                Arrays.stream(ProjectDeliveryScopeQualificationQuery.class.getRecordComponents())
                        .map(component -> component.getName()).sorted().toList());
    }

    @Test
    void rejectsInvalidCallerInputWithStableCode() {
        ProjectDeliveryScopeQualificationFactException inspectFailure = assertThrows(
                ProjectDeliveryScopeQualificationFactException.class,
                () -> new ProjectDeliveryScopeQualificationQuery(1L, 2L, null));
        assertEquals(ProjectDeliveryScopeQualificationFactException.Code.INVALID_REQUEST,
                inspectFailure.getCode());
        ProjectDeliveryScopeQualificationFactException lockFailure = assertThrows(
                ProjectDeliveryScopeQualificationFactException.class,
                () -> new ProjectDeliveryScopeQualificationRevalidationQuery(
                        1L, 2L, 2L, 3L, null, "S5", 1, 1L, 1L));
        assertEquals(ProjectDeliveryScopeQualificationFactException.Code.INVALID_REQUEST,
                lockFailure.getCode());
    }

    @Test
    void rejectsBrokenOwnerOutputWithStableCode() {
        ProjectDeliveryScopeQualificationFactException failure = assertThrows(
                ProjectDeliveryScopeQualificationFactException.class,
                () -> new ProjectDeliveryScopeQualificationFact(
                        1L, 2L, 2L, 3L, "ACTIVE", "S7", 1, 1L, 1L));
        assertEquals(ProjectDeliveryScopeQualificationFactException.Code.OWNER_DATA_CORRUPTED,
                failure.getCode());
    }

    @Test
    void rejectsImpossibleLifecycleStageAndNonPositiveTreeVersion() {
        ProjectDeliveryScopeQualificationFactException invalidInput = assertThrows(
                ProjectDeliveryScopeQualificationFactException.class,
                () -> new ProjectDeliveryScopeQualificationRevalidationQuery(
                        1L, 2L, 2L, 3L, "NORMAL_CLOSED", "S0", 1, 1L, 1L));
        assertEquals(ProjectDeliveryScopeQualificationFactException.Code.INVALID_REQUEST,
                invalidInput.getCode());
        ProjectDeliveryScopeQualificationFactException invalidExpectedTree = assertThrows(
                ProjectDeliveryScopeQualificationFactException.class,
                () -> new ProjectDeliveryScopeQualificationRevalidationQuery(
                        1L, 2L, 2L, 3L, "ACTIVE", "S4", 1, 1L, 0L));
        assertEquals(ProjectDeliveryScopeQualificationFactException.Code.INVALID_REQUEST,
                invalidExpectedTree.getCode());
        ProjectDeliveryScopeQualificationFactException brokenOutput = assertThrows(
                ProjectDeliveryScopeQualificationFactException.class,
                () -> new ProjectDeliveryScopeQualificationFact(
                        1L, 2L, 2L, 3L, "NORMAL_CLOSED", "S0", 1, 1L, 1L));
        assertEquals(ProjectDeliveryScopeQualificationFactException.Code.OWNER_DATA_CORRUPTED,
                brokenOutput.getCode());
        ProjectDeliveryScopeQualificationFactException brokenTree = assertThrows(
                ProjectDeliveryScopeQualificationFactException.class,
                () -> new ProjectDeliveryScopeQualificationFact(
                        1L, 2L, 2L, 3L, "ACTIVE", "S4", 1, 1L, 0L));
        assertEquals(ProjectDeliveryScopeQualificationFactException.Code.OWNER_DATA_CORRUPTED,
                brokenTree.getCode());
    }

    @Test
    void locksDirectManagerEntitlementWithoutAuthorizationGrant() {
        assertEquals(List.of("root project row", "target project row", "current active tree version"),
                machineContract.getJSONObject("operations").getJSONObject("lockAndRevalidate")
                        .getJSONArray("lockOrder").toList(String.class));
        String scope = machineContract.getJSONObject("qualification").getStr("scope");
        assertTrue(scope.contains("locked target project current manager"));
        assertTrue(scope.contains("no descendant scope or AuthorizationGrantApi/listEffective"));
    }

    @Test
    void locksFailureDecisionOrder() {
        assertEquals(List.of(
                        "malformed public query or expected fact => INVALID_REQUEST",
                        "explicit tenant differs from trusted runtime tenant => TENANT_CONTEXT_MISMATCH",
                        "inspect target missing or not visible in the direct-target contract => DATA_SCOPE_DENIED",
                        "inspect actor differs from the locked current manager => SUBJECT_NOT_ELIGIBLE",
                        "lockAndRevalidate any mismatch against a structurally valid frozen fact, including manager, lifecycle, stage, project, participant, tree or root identity change => FACT_STALE",
                        "impossible or corrupted locked Owner facts => OWNER_DATA_CORRUPTED",
                        "transaction or database Provider unavailable => PROVIDER_UNAVAILABLE"),
                machineContract.getJSONArray("errorDecisionOrder").toList(String.class));
    }
}
