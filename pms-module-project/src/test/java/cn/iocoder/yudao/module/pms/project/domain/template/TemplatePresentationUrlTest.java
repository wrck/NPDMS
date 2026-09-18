package cn.iocoder.yudao.module.pms.project.domain.template;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplatePresentationUrlTest {
    @Test
    void validatesPathsAndIndependentlyEncodesQueryValues() {
        assertEquals(63, TemplatePresentationUrlScenarios.run());
    }
}
