package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ProjectRuleCreationFactsTest {
    @Test void suppliedFactsKeepUnknownNullBooleanAndTextDistinct() throws Exception {
        cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateMatchPreviewReqVO request;
        try (var input = getClass().getResourceAsStream("/project-template/creation-match-facts.json")) {
            assertNotNull(input);
            request = JsonUtils.parseObject(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
                    cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateMatchPreviewReqVO.class);
        }
        var facts = new cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields(key -> null).suppliedCreationFacts(request.getFacts()).values();
        assertFalse(facts.containsKey("project.businessType"));
        assertTrue(facts.get("project.customerCode").available()); assertNull(facts.get("project.customerCode").value());
        assertEquals(false, facts.get("project.isChild").value());
        assertEquals("现场工勘", facts.get("project.projectName").value());
    }
    @Test void invalidTypesRemainUnknownAndRuntimeFieldsCannotEnterPreview() {
        var facts = new cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields(key -> null).suppliedCreationFacts(Map.of(
                "project.isChild", json("\"false\""), "project.projectName", json("{}"))).values();
        assertFalse(facts.get("project.isChild").available()); assertFalse(facts.get("project.projectName").available());
        assertThrows(IllegalArgumentException.class, () -> new cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields(key -> null).suppliedCreationFacts(Map.of("project.lifecycleStatus", json("\"ACTIVE\""))));
    }
    @Test void ownerFactsKeepStableBindingsWhilePreviewRestrictsCreationEligibility() {
        var facts = new cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields(key -> null).creationFacts(new ProjectMasterDO()).values();
        assertEquals(ProjectRuleFields.readableCodes().size(), facts.size());
        assertTrue(facts.values().stream().allMatch(fact -> fact.available()));
        assertNull(facts.get("project.lifecycleStatus").value()); assertNull(facts.get("project.projectEndDate").value());
    }
    private JsonNode json(String text) { return JsonUtils.parseObject(text, JsonNode.class); }
}
