package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOperationInput;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.acceptancereport.vo.AcceptanceReportDraftReqVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TemplateOperationInputTest {
    record Form(Map<String, Object> values, int version) { }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "1", "true", "{\"tenantId\":null}", "{\"execution\":null}",
            "{\"@class\":\"java.lang.Runtime\"}", "{\"className\":\"java.lang.Runtime\"}",
            "{\"version\":2.5}", "{\"version\":null}", "{\"userId\":1}", "{\"unknown\":true}"})
    void rejectsUntrustedIdentityTypesAndUnknownFields(String input) {
        var error = assertThrows(IllegalArgumentException.class,
                () -> ProjectOperationInput.read(JsonUtils.getObjectMapper(), JsonUtils.parseTree(input), Form.class));
        assertEquals("BUSINESS_INPUT_INVALID", error.getMessage());
        assertNull(error.getCause());
    }

    @Test
    void dynamicBusinessMapIsPreservedWithoutChangingTheOriginalOrSharedMapper() {
        JsonNode input = JsonUtils.parseTree("{\"values\":{\"tenantId\":\"legitimate field\",\"@class\":\"text\",\"items\":[1,true,null]},\"version\":2}");
        String before = JsonUtils.toJsonString(input);
        boolean setting = JsonUtils.getObjectMapper().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        Form parsed = ProjectOperationInput.read(JsonUtils.getObjectMapper(), input, Form.class);
        assertEquals("legitimate field", parsed.values().get("tenantId"));
        assertEquals("text", parsed.values().get("@class"));
        assertEquals(2, parsed.version());
        parsed.values().put("changed", true);
        assertEquals(before, JsonUtils.toJsonString(input));
        assertEquals(setting, JsonUtils.getObjectMapper().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        assertDoesNotThrow(() -> JsonUtils.parseObject("{\"notAField\":1}", AcceptanceReportDraftReqVO.class));
    }

    @Test
    void usesOriginalRequestDateConfigurationAndOnlyRemovesDeclaredTransportFieldsFromACopy() {
        var request = new AcceptanceReportDraftReqVO();
        request.setAcceptanceTime(LocalDateTime.of(2026, 9, 17, 12, 30));
        request.setConclusionCode("PASS"); request.setExpectedReportVersionNo(2);
        var input = (tools.jackson.databind.node.ObjectNode) JsonUtils.parseTree(JsonUtils.toJsonString(request));
        input.put("reportVersionId", "9007199254740993");
        String before = JsonUtils.toJsonString(input);
        var parsed = ProjectOperationInput.read(JsonUtils.getObjectMapper(), input, AcceptanceReportDraftReqVO.class, Set.of("reportVersionId"));
        assertEquals(request, parsed);
        assertEquals(before, JsonUtils.toJsonString(input));
        assertThrows(IllegalArgumentException.class, () -> ProjectOperationInput.read(JsonUtils.getObjectMapper(), input, AcceptanceReportDraftReqVO.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "true", "[]", "{}"})
    void reasonCannotBeCoercedIntoText(String value) {
        assertThrows(IllegalArgumentException.class,
                () -> ProjectOperationInput.optionalText(JsonUtils.parseTree("{\"reason\":" + value + "}"), "reason"));
    }

    @Test
    void missingAndNullOptionalTextRemainAbsentAndTransportOnlyCommandsRejectExtraFields() {
        assertNull(ProjectOperationInput.optionalText(JsonUtils.parseTree("{}"), "reason"));
        assertNull(ProjectOperationInput.optionalText(JsonUtils.parseTree("{\"reason\":null}"), "reason"));
        assertEquals("", ProjectOperationInput.optionalText(JsonUtils.parseTree("{\"reason\":\"\"}"), "reason"));
        assertThrows(IllegalArgumentException.class, () -> ProjectOperationInput.fields(JsonUtils.parseTree("{\"ignored\":1}"), Set.of()));
    }
}
