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
    void patchCompleteAndCreateDraftExposeIndependentPltAndSolCasHeaders() throws Exception {
        Method patch = PreparationController.class.getMethod("patchRequirementForm", Long.class, String.class,
                String.class, RequirementAnalysisFormPatchReqVO.class);
        Method complete = PreparationController.class.getMethod("completeRequirementAnalysis", Long.class,
                String.class, String.class, String.class, RequirementAnalysisActionReqVO.class);
        Method createDraft = PreparationController.class.getMethod("createRequirementAnalysisRevision", Long.class,
                String.class, String.class, String.class, RequirementAnalysisActionReqVO.class);

        assertEquals(Set.of("If-Match", "X-SOL-If-Match"), casHeaders(patch));
        assertEquals(Set.of("If-Match", "X-SOL-If-Match", "Idempotency-Key"), headers(complete));
        assertEquals(Set.of("If-Match", "X-SOL-If-Match", "Idempotency-Key"), headers(createDraft));
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
