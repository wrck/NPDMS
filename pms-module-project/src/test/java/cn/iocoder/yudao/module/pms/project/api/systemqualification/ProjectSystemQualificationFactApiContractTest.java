package cn.iocoder.yudao.module.pms.project.api.systemqualification;

import cn.iocoder.yudao.module.pms.project.api.systemqualification.dto.ProjectSystemQualificationFact;
import cn.iocoder.yudao.module.pms.project.api.systemqualification.dto.ProjectSystemQualificationLockQuery;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectSystemQualificationFactApiContractTest {

    @Test
    void lockQueryMustNotAcceptUserOrFrozenVersionInputs() {
        assertEquals(List.of("projectId", "requiredLifecycleStatus", "requiredCurrentStage"),
                componentNames(ProjectSystemQualificationLockQuery.class));
        assertEquals(List.of(Long.class, String.class, String.class),
                componentTypes(ProjectSystemQualificationLockQuery.class));
    }

    @Test
    void resultMustExposeOnlyCurrentQualificationVersions() {
        assertEquals(List.of("projectId", "currentManagerUserId", "lifecycleStatus", "currentStage",
                        "currentProjectVersion", "currentParticipantFactVersion", "currentTreeVersion"),
                componentNames(ProjectSystemQualificationFact.class));
        assertEquals(List.of(Long.class, Long.class, String.class, String.class, Integer.class, Long.class,
                        Long.class), componentTypes(ProjectSystemQualificationFact.class));
    }

    @Test
    void apiMustExposeOneSystemLockOperation() {
        assertEquals(List.of("lockCurrentForSystem"), Arrays.stream(ProjectSystemQualificationFactApi.class
                .getDeclaredMethods()).map(method -> method.getName()).sorted().toList());
        Method method = ProjectSystemQualificationFactApi.class.getDeclaredMethods()[0];
        assertEquals(ProjectSystemQualificationFact.class, method.getReturnType());
        assertEquals(List.of(ProjectSystemQualificationLockQuery.class), List.of(method.getParameterTypes()));
    }

    private static List<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static List<Class<?>> componentTypes(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getType).toList();
    }

}
