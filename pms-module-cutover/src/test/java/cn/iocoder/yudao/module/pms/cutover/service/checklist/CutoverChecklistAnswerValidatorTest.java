package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CutoverChecklistAnswerValidatorTest {
    @Test void emptyDraftIsAllowedButCannotPassRequiredSubmission() {
        var item = item("TEXT", "{}");
        assertDoesNotThrow(() -> CutoverChecklistAnswerValidator.validate(item, "{\"value\":\"\"}", false));
        assertThrows(CutoverChecklistException.class, () -> CutoverChecklistAnswerValidator.validate(item, "{\"value\":\"\"}", true));
    }

    @Test void tableRejectsPlainTextAndChecksRequiredColumns() {
        var item = item("TABLE", "{\"columns\":[{\"code\":\"device\",\"label\":\"设备\",\"required\":true}]}");
        assertThrows(CutoverChecklistException.class, () -> CutoverChecklistAnswerValidator.validate(item, "{\"value\":\"随便填写\"}", false));
        assertThrows(CutoverChecklistException.class, () -> CutoverChecklistAnswerValidator.validate(item, "{\"value\":{\"rows\":[{}]}}", true));
        assertDoesNotThrow(() -> CutoverChecklistAnswerValidator.validate(item, "{\"value\":{\"rows\":[{\"device\":\"SN-01\"}]}}", true));
    }

    @Test void conditionalSubtableOnlyRequiredWhenItsConditionApplies() {
        var item = item("TABLE", """
                {"fields":[{"code":"enabled","type":"BOOLEAN","required":true},
                {"code":"details","required":true,"visibleWhen":{"field":"enabled","equals":true},
                  "columns":[{"code":"port","required":true}]}]}
                """);
        assertDoesNotThrow(() -> CutoverChecklistAnswerValidator.validate(item, "{\"value\":{\"enabled\":false}}", true));
        assertThrows(CutoverChecklistException.class, () -> CutoverChecklistAnswerValidator.validate(item, "{\"value\":{\"enabled\":true}}", true));
        assertDoesNotThrow(() -> CutoverChecklistAnswerValidator.validate(item,
                "{\"value\":{\"enabled\":true,\"details\":[{\"port\":\"GE1/0/1\"}]}}", true));
    }

    @Test void rejectsAnswersOutsidePublishedOptions() {
        var item = item("SELECT", "{\"options\":[{\"label\":\"正常\",\"value\":\"NORMAL\"}, {\"label\":\"异常\",\"value\":\"ABNORMAL\"}]}");
        assertThrows(CutoverChecklistException.class, () -> CutoverChecklistAnswerValidator.validate(item, "{\"value\":\"GUESS\"}", true));
        assertDoesNotThrow(() -> CutoverChecklistAnswerValidator.validate(item, "{\"value\":\"NORMAL\"}", true));
    }

    private static CutoverChecklistItemDO item(String format, String schema) {
        var item = new CutoverChecklistItemDO();
        item.setItemName("检查项"); item.setRequiredFlag(true); item.setInterfaceFormatCode(format); item.setInterfaceSchemaSnapshot(schema);
        return item;
    }
}
