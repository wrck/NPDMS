package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOperationResult;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.TemplateOperationCompilation;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler;
import cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContractValidator.Checkpoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectOperationResultFieldsTest {
    @Test void resultFieldsArePostOnlyAndNeverExposeArbitraryResponseData() {
        var result = new ProjectOperationResult("SOL", "SITE_SURVEY", "9007199254740993", null, 1, "fact", "SURVEY_CONFIRMED",
                JsonUtils.parseTree("{\"privateValue\":\"hidden\"}"), false);
        assertEquals("9007199254740993", ProjectOperationResultFields.read(result, "transactionResult.objectId").value());
        assertTrue(ProjectOperationResultFields.read(result, "transactionResult.revisionId").available());
        assertNull(ProjectOperationResultFields.read(result, "transactionResult.revisionId").value());
        assertFalse(ProjectOperationResultFields.read(null, "transactionResult.resultCode").available());
        assertFalse(ProjectOperationResultFields.read(result, "transactionResult.response.privateValue").available());
        var program = new ProjectRuleCompiler().compile(JsonUtils.parseTree("{\"predicate\":\"FIELD\",\"parameters\":{\"fieldCode\":\"transactionResult.resultCode\",\"operator\":\"=\",\"valueType\":\"TEXT\",\"value\":\"SURVEY_CONFIRMED\"}}"));
        assertTrue(TemplateOperationCompilation.supports(program, Checkpoint.POST));
        assertFalse(TemplateOperationCompilation.supports(program, Checkpoint.PRE));
    }
}
