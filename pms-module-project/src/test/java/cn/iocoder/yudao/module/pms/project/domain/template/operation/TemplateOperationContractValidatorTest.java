package cn.iocoder.yudao.module.pms.project.domain.template.operation;

import org.junit.jupiter.api.Test;

class TemplateOperationContractValidatorTest {
    @Test
    void validatesOperationContractsAndPreservesLegacyAbsence() {
        TemplateOperationContractCases.runAll();
    }
}
