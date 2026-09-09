package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateGateReferenceOrderTest {
    private TemplateDefinitionContent.GateRef ref(String type, String code) {
        var value = new TemplateDefinitionContent.GateRef();
        value.setRefType(type);
        value.setRefCode(code);
        return value;
    }

    @Test
    void databaseIndexOrderDoesNotChangeGateMeaning() {
        var task = ref("TASK", "T1");
        var milestone = ref("MILESTONE", "M1");
        var deliverable = ref("DELIVERABLE", "D1");
        assertTrue(TemplateDefinitionReferenceAssembler.sameGateReferences(
                List.of(deliverable, milestone, task), List.of(task, milestone, deliverable)));
    }

    @Test
    void missingDuplicateOrChangedReferencesStillFail() {
        var task = ref("TASK", "T1");
        var milestone = ref("MILESTONE", "M1");
        assertFalse(TemplateDefinitionReferenceAssembler.sameGateReferences(List.of(task), List.of(task, milestone)));
        assertFalse(TemplateDefinitionReferenceAssembler.sameGateReferences(List.of(task, task), List.of(task, milestone)));
        assertFalse(TemplateDefinitionReferenceAssembler.sameGateReferences(List.of(ref("TASK", "OTHER")), List.of(task)));
        var historical = ref("TASK", "T1");
        historical.setRefVersion("legacy");
        assertFalse(TemplateDefinitionReferenceAssembler.sameGateReferences(List.of(historical), List.of(task)));
    }
}
