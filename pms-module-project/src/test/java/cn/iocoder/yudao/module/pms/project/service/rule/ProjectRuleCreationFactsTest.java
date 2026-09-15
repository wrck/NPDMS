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
        var facts = ProjectRuleFields.suppliedCreationFacts(request.getFacts()).values();
        assertFalse(facts.containsKey("project.businessType"));
        assertTrue(facts.get("project.customerCode").available()); assertNull(facts.get("project.customerCode").value());
        assertEquals(false, facts.get("project.isChild").value());
        assertEquals("现场工勘", facts.get("project.projectName").value());
    }
    @Test void invalidTypesRemainUnknownAndRuntimeFieldsCannotEnterPreview() {
        var facts = ProjectRuleFields.suppliedCreationFacts(Map.of(
                "project.isChild", json("\"false\""), "project.projectName", json("{}"))).values();
        assertFalse(facts.get("project.isChild").available()); assertFalse(facts.get("project.projectName").available());
        assertThrows(IllegalArgumentException.class, () -> ProjectRuleFields.suppliedCreationFacts(Map.of("project.lifecycleStatus", json("\"ACTIVE\""))));
    }
    @Test void ownerFactsContainAllAndOnlyCreationFieldsWithKnownEmptyValues() {
        var facts = ProjectRuleFields.creationFacts(new ProjectMasterDO()).values();
        assertEquals(ProjectRuleFields.catalog().stream().filter(ProjectRuleFields.Field::availableAtCreation).count(), facts.size());
        assertTrue(facts.values().stream().allMatch(fact -> fact.available()));
        assertFalse(facts.containsKey("project.lifecycleStatus")); assertFalse(facts.containsKey("project.projectEndDate"));
    }
    private JsonNode json(String text) { return JsonUtils.parseObject(text, JsonNode.class); }
}
