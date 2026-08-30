package cn.iocoder.yudao.module.pms.project.api.deliveryscope;

import cn.iocoder.yudao.module.pms.project.api.deliveryscope.dto.ProjectDeliveryScopeQualificationFact;
import cn.iocoder.yudao.module.pms.project.api.deliveryscope.dto.ProjectDeliveryScopeQualificationQuery;
import cn.iocoder.yudao.module.pms.project.api.deliveryscope.dto.ProjectDeliveryScopeQualificationRevalidationQuery;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectDeliveryScopeQualificationFactApiContractTest {

    @Test
    void exposesOnlyInspectAndLockAndRevalidate() {
        assertEquals(List.of("inspect", "lockAndRevalidate"), Arrays.stream(
                        ProjectDeliveryScopeQualificationFactApi.class.getDeclaredMethods())
                .map(Method::getName).sorted().toList());
    }

    @Test
    void freezesEveryComparableQualificationAxis() {
        assertEquals(List.of("actorId", "expectedCurrentStage", "expectedLifecycleStatus",
                        "expectedParticipantFactVersion", "expectedProjectVersion", "expectedTreeVersion",
                        "projectId", "tenantId"),
                Arrays.stream(ProjectDeliveryScopeQualificationRevalidationQuery.class.getRecordComponents())
                        .map(component -> component.getName()).sorted().toList());
        assertEquals(List.of("currentManagerUserId", "currentStage", "lifecycleStatus",
                        "participantFactVersion", "projectId", "projectVersion", "tenantId", "treeVersion"),
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
                        1L, 2L, 3L, null, "S5", 1, 1L, 1L));
        assertEquals(ProjectDeliveryScopeQualificationFactException.Code.INVALID_REQUEST,
                lockFailure.getCode());
    }

    @Test
    void rejectsBrokenOwnerOutputWithStableCode() {
        ProjectDeliveryScopeQualificationFactException failure = assertThrows(
                ProjectDeliveryScopeQualificationFactException.class,
                () -> new ProjectDeliveryScopeQualificationFact(
                        1L, 2L, 3L, "ACTIVE", "S7", 1, 1L, 1L));
        assertEquals(ProjectDeliveryScopeQualificationFactException.Code.OWNER_DATA_CORRUPTED,
                failure.getCode());
    }
}
