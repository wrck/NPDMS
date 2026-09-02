package cn.iocoder.yudao.module.pms.cutover.controller.admin.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CutoverDashboardControllerContractTest {

    @Test
    void keepsCandidateOutsideProductionRegistrationWithExactRestRecords() throws Exception {
        assertThat(AnnotatedElementUtils.hasAnnotation(CutoverDashboardController.class, RestController.class))
                .isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(CutoverDashboardController.class, Component.class))
                .isFalse();
        Method method = CutoverDashboardController.class.getMethod("kpis");
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/kpis");
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@ss.hasPermission('pms:cutover-task:query')");
        assertThat(Arrays.stream(CutoverDashboardKpiData.class.getRecordComponents())
                .map(component -> component.getName()).toList()).containsExactly(
                "todoCount", "archivedCount", "approvingCount", "rejectedPendingModificationCount", "generatedAt");
        assertThat(Arrays.stream(CutoverDashboardErrorData.class.getRecordComponents())
                .map(component -> component.getName()).toList()).containsExactly(
                "category", "reasonCode", "recoveryAction", "ownerContext");
    }
}
