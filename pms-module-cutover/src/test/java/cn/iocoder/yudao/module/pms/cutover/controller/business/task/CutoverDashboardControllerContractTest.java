package cn.iocoder.yudao.module.pms.cutover.controller.business.task;

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
    void exposesStatisticsOnTheCanonicalTaskResource() throws Exception {
        assertThat(AnnotatedElementUtils.hasAnnotation(CutoverTaskController.class, RestController.class))
                .isTrue();
        assertThat(AnnotatedElementUtils.hasAnnotation(CutoverTaskController.class, Component.class))
                .isTrue();
        Method method = CutoverTaskController.class.getMethod("statistics");
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/statistics");
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@ss.hasPermission('pms:cutover-task:query')");
        assertThat(Arrays.stream(cn.iocoder.yudao.module.pms.cutover.controller.business.task.vo.CutoverTaskStatisticsRespVO.class.getRecordComponents())
                .map(component -> component.getName()).toList()).containsExactly(
                "todoCount", "archivedCount", "approvingCount", "rejectedPendingModificationCount", "generatedAt");
        assertThat(Arrays.stream(CutoverDashboardErrorData.class.getRecordComponents())
                .map(component -> component.getName()).toList()).containsExactly(
                "category", "reasonCode", "recoveryAction", "ownerContext");
    }
}
