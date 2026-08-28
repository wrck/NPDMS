package cn.iocoder.yudao.module.pms.platform.service.dynamicform;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_FIELD_KEY_DUPLICATE;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_SCHEMA_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicFormSchemaServiceTest {

    private final DynamicFormSchemaService service = new DynamicFormSchemaService();

    @Test
    void discoversNestedFieldsInDocumentOrderWithoutRewritingUnknownConfiguration() {
        JsonNode conf = JsonUtils.parseTree("""
                {"submitBtn":true,"unknown":{"parseFunc":"return value","url":"https://example.test"}}
                """);
        JsonNode rules = JsonUtils.parseTree("""
                [
                  {"type":"input","field":"name","on":{"change":"function(){}"}},
                  {"type":"row","children":[
                    {"type":"PmsFileArtifact","field":"drawings","props":{"api":"/files"}},
                    {"type":"unknown-widget","field":"customValue","iframe":"https://example.test/frame"}
                  ]}
                ]
                """);
        String originalConf = conf.toString();
        String originalRules = rules.toString();

        var fields = service.validateAndDescribe(conf, rules, DynamicFormSchemaService.ENGINE_CODE,
                DynamicFormSchemaService.DESIGNER_VERSION, DynamicFormSchemaService.RENDERER_VERSION);

        assertEquals(List.of("name", "customValue"), fields.ordinaryFieldKeys());
        assertEquals(List.of("drawings"), fields.fileFieldKeys());
        assertEquals(originalConf, conf.toString());
        assertEquals(originalRules, rules.toString());
    }

    @Test
    void rejectsDuplicateFieldKeys() {
        ServiceException duplicate = assertThrows(ServiceException.class, () -> validate("""
                [{"type":"input","field":"same"},{"type":"input","field":"same"}]
                """));
        assertEquals(DYNAMIC_FORM_FIELD_KEY_DUPLICATE.getCode(), duplicate.getCode());
    }

    @Test
    void ignoresBlankLayoutFieldButRequiresControlledFileField() {
        var fields = validate("""
                [{"type":"row","field":"","children":[{"type":"input","field":"name"}]}]
                """);
        assertEquals(List.of("name"), fields.ordinaryFieldKeys());
        assertEquals(List.of(), fields.fileFieldKeys());

        ServiceException blank = assertThrows(ServiceException.class, () -> validate(
                "[{\"type\":\"PmsFileArtifact\",\"field\":\"\"}]"));
        assertEquals(DYNAMIC_FORM_SCHEMA_INVALID.getCode(), blank.getCode());
    }

    @Test
    void rejectsInvalidControlledFilePurposeKeys() {
        ServiceException slash = assertThrows(ServiceException.class,
                () -> validate("[{\"type\":\"PmsFileArtifact\",\"field\":\"site/photo\"}]"));
        assertEquals(DYNAMIC_FORM_SCHEMA_INVALID.getCode(), slash.getCode());

        String tooLong = "a".repeat(DynamicFormSchemaService.MAX_FILE_FIELD_KEY_LENGTH + 1);
        assertThrows(ServiceException.class,
                () -> validate("[{\"type\":\"PmsFileArtifact\",\"field\":\"" + tooLong + "\"}]"));
    }

    @Test
    void requiresPinnedEngineAndRootShapes() {
        assertThrows(ServiceException.class, () -> service.parseAndValidate(
                "not-json", "[]", DynamicFormSchemaService.ENGINE_CODE,
                DynamicFormSchemaService.DESIGNER_VERSION, DynamicFormSchemaService.RENDERER_VERSION));
        assertThrows(ServiceException.class, () -> service.validateAndDescribe(
                JsonUtils.parseTree("[]"), JsonUtils.parseTree("[]"), DynamicFormSchemaService.ENGINE_CODE,
                DynamicFormSchemaService.DESIGNER_VERSION, DynamicFormSchemaService.RENDERER_VERSION));
        assertThrows(ServiceException.class, () -> service.validateAndDescribe(
                JsonUtils.parseTree("{}"), JsonUtils.parseTree("{}"), DynamicFormSchemaService.ENGINE_CODE,
                DynamicFormSchemaService.DESIGNER_VERSION, DynamicFormSchemaService.RENDERER_VERSION));
        assertThrows(ServiceException.class, () -> service.validateAndDescribe(
                JsonUtils.parseTree("{}"), JsonUtils.parseTree("[]"), "OTHER_ENGINE",
                DynamicFormSchemaService.DESIGNER_VERSION, DynamicFormSchemaService.RENDERER_VERSION));
        assertThrows(ServiceException.class, () -> service.validateAndDescribe(
                JsonUtils.parseTree("{}"), JsonUtils.parseTree("[]"), DynamicFormSchemaService.ENGINE_CODE,
                "3.3.0", DynamicFormSchemaService.RENDERER_VERSION));
        assertThrows(ServiceException.class, () -> service.validateAndDescribe(
                JsonUtils.parseTree("{}"), JsonUtils.parseTree("[]"), DynamicFormSchemaService.ENGINE_CODE,
                DynamicFormSchemaService.DESIGNER_VERSION, "3.1.0"));
    }

    private DynamicFormSchemaService.SchemaFields validate(String rules) {
        return service.validateAndDescribe(JsonUtils.parseTree("{}"), JsonUtils.parseTree(rules),
                DynamicFormSchemaService.ENGINE_CODE, DynamicFormSchemaService.DESIGNER_VERSION,
                DynamicFormSchemaService.RENDERER_VERSION);
    }
}
