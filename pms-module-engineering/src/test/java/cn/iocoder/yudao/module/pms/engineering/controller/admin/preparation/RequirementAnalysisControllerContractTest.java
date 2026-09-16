package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation;

import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisActionReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisFormPatchReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PatchMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RequirementAnalysisControllerContractTest {

    @Test
    void newRevisionCommandsExposeBusinessCasAndIdempotencyHeaders() throws Exception {
        var controller = cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement.RequirementAnalysisEntityController.class;
        var patchType = cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisEntityCommands.Patch.class;
        var actionType = cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisEntityCommands.Action.class;
        for (Method method : new Method[]{
                controller.getMethod("save", Long.class, Long.class, int.class, String.class, patchType),
                controller.getMethod("complete", Long.class, Long.class, int.class, String.class, actionType),
                controller.getMethod("copy", Long.class, Long.class, int.class, String.class, actionType)}) {
            assertEquals(Set.of("If-Match", "Idempotency-Key"), headers(method));
        }
    }

    @Test
    void cancelledPre04SectionPatchRouteIsNotReachable() {
        boolean legacyRoute = Arrays.stream(PreparationController.class.getMethods())
                .map(method -> method.getAnnotation(PatchMapping.class))
                .filter(java.util.Objects::nonNull)
                .anyMatch(mapping -> Arrays.asList(mapping.params()).contains("type=PRE_04"));

        assertFalse(legacyRoute);
    }

    private Set<String> casHeaders(Method method) {
        return headers(method).stream().filter(name -> name.contains("Match")).collect(Collectors.toSet());
    }

    private Set<String> headers(Method method) {
        return Arrays.stream(method.getParameters()).map(Parameter::getAnnotations)
                .flatMap(Arrays::stream).filter(RequestHeader.class::isInstance)
                .map(RequestHeader.class::cast).map(RequestHeader::value).collect(Collectors.toSet());
    }
}
