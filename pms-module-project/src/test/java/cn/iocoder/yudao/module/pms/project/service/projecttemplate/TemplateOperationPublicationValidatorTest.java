package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemplateOperationPublicationValidatorTest {
    @Test void serializedAbsentContractsDoNotBlockAnotherNodesDeployedOperation() {
        var registry = mock(ProjectBusinessOperationRegistry.class);
        when(registry.runtimeAvailable("CUT.CUTOVER_APPROVAL.CREATE", 1)).thenReturn(true);
        var validator = new TemplateOperationPublicationValidator(registry);
        var document = JsonUtils.parseObject("""
                {"tasks":[
                  {"nodeKey":"task:legacy","workBinding":{"type":"PAGE","operationContract":null}},
                  {"nodeKey":"task:cutover","workBinding":{"operationContract":{"version":1,"operations":[
                    {"operationCode":"CUT.CUTOVER_APPROVAL.CREATE","operationVersion":1,"pre":{"mode":"NONE"},"post":{"mode":"NONE"}}
                  ]}}}
                ],"stages":[{"nodeKey":"stage:legacy","workBinding":{"operationContract":null}}]}
                """, TemplateDesignerDocument.class);
        assertTrue(validator.validate(document).isEmpty());
        verify(registry).runtimeAvailable("CUT.CUTOVER_APPROVAL.CREATE", 1);
        document.getTasks().getFirst().getWorkBinding().setOperationContract(JsonUtils.parseTree("{}"));
        assertTrue(validator.validate(document).stream().anyMatch(issue -> "OPERATION_CONTRACT_INVALID".equals(issue.code())));
        document.getTasks().getFirst().getWorkBinding().setOperationContract(null);
        when(registry.runtimeAvailable("CUT.CUTOVER_APPROVAL.CREATE", 1)).thenReturn(false);
        assertTrue(validator.validate(document).stream().anyMatch(issue -> "OPERATION_RUNTIME_NOT_INSTALLED".equals(issue.code())));
    }
}
