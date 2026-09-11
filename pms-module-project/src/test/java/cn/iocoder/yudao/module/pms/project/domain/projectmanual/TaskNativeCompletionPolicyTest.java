package cn.iocoder.yudao.module.pms.project.domain.projectmanual;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskNativeCompletionPolicyTest {

    @Test
    void acceptsOnlyTheFrozenDonePolicy() {
        assertDoesNotThrow(() -> TaskNativeCompletionPolicy.validate(
                "TASK_NATIVE", "TASK_NATIVE_STATUS", "{\"requiredStatus\":\"DONE\"}"));
    }

    @Test
    void rejectsWrongBindingRuleStatusAndExtraParameters() {
        assertThrows(IllegalArgumentException.class, () -> TaskNativeCompletionPolicy.validate(
                "BUSINESS_OBJECT", "TASK_NATIVE_STATUS", "{\"requiredStatus\":\"DONE\"}"));
        assertThrows(IllegalArgumentException.class, () -> TaskNativeCompletionPolicy.validate(
                "TASK_NATIVE", "ALL", "{\"requiredStatus\":\"DONE\"}"));
        assertThrows(IllegalArgumentException.class, () -> TaskNativeCompletionPolicy.validate(
                "TASK_NATIVE", "TASK_NATIVE_STATUS", "{\"requiredStatus\":\"COMPLETED\"}"));
        assertThrows(IllegalArgumentException.class, () -> TaskNativeCompletionPolicy.validate(
                "TASK_NATIVE", "TASK_NATIVE_STATUS", "{\"requiredStatus\":\"DONE\",\"fallback\":true}"));
    }

    @Test
    void rejectsMissingOrMalformedSnapshot() {
        assertThrows(IllegalArgumentException.class, () -> TaskNativeCompletionPolicy.validateRule(
                "TASK_NATIVE_STATUS", null));
        assertThrows(IllegalArgumentException.class, () -> TaskNativeCompletionPolicy.validateRule(
                "TASK_NATIVE_STATUS", "[]"));
        assertThrows(IllegalArgumentException.class, () -> TaskNativeCompletionPolicy.validateRule(
                "TASK_NATIVE_STATUS", "not-json"));
    }
}
