package cn.iocoder.yudao.module.pms.cutover.api.spare;

import cn.iocoder.yudao.module.pms.cutover.api.spare.dto.SpareExternalReferenceBindingCommand;
import cn.iocoder.yudao.module.pms.cutover.api.spare.dto.SpareExternalReferenceBindingResult;
import cn.iocoder.yudao.module.pms.cutover.api.spare.dto.SpareReferenceBindingOutcome;
import cn.iocoder.yudao.module.pms.cutover.api.spare.dto.SpareStatusCallbackCommand;
import cn.iocoder.yudao.module.pms.cutover.api.spare.dto.SpareStatusCallbackOutcome;
import cn.iocoder.yudao.module.pms.cutover.api.spare.dto.SpareStatusCallbackResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CutoverSpareCallbackApiContractTest {

    @Test
    void exposesTheTwoLockedCallbackOperations() {
        Map<String, Method> methods = Arrays.stream(CutoverSpareCallbackApi.class.getDeclaredMethods())
                .collect(Collectors.toMap(Method::getName, Function.identity()));

        assertThat(methods).containsOnlyKeys("bindExternalReference", "acceptStatus");
        assertSignature(methods.get("bindExternalReference"), SpareExternalReferenceBindingResult.class,
                SpareExternalReferenceBindingCommand.class);
        assertSignature(methods.get("acceptStatus"), SpareStatusCallbackResult.class,
                SpareStatusCallbackCommand.class);
    }

    @Test
    void carriesEveryLockedRecordFieldExactly() {
        assertThat(componentNames(SpareExternalReferenceBindingCommand.class)).containsExactly(
                "eventId", "tenantId", "platformRequestId", "externalSystemCode", "externalRequestId",
                "externalApplicationNo", "occurredAt", "correlationId");
        assertThat(componentNames(SpareExternalReferenceBindingResult.class)).containsExactly(
                "applicationReferenceId", "externalApplicationNo", "integrationStatus", "outcome");
        assertThat(componentNames(SpareStatusCallbackCommand.class)).containsExactly(
                "eventId", "tenantId", "externalSystemCode", "externalApplicationNo", "statusVersion",
                "externalStatusRaw", "statusSnapshot", "externalOccurredAt", "correlationId");
        assertThat(componentNames(SpareStatusCallbackResult.class)).containsExactly(
                "applicationReferenceId", "statusVersion", "outcome");
    }

    @Test
    void preservesWireLongTimeAndReadOnlyStatusSnapshot() {
        long snowflakeId = 9_007_199_254_740_992L;
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 2, 10, 30);
        Map<String, Object> mutableSnapshot = new LinkedHashMap<>();
        mutableSnapshot.put("status", "ACCEPTED");

        SpareExternalReferenceBindingCommand binding = new SpareExternalReferenceBindingCommand(
                "event-bind-1", 1L, "request-1", "SPARE_SYSTEM", "external-request-1",
                "application-1", occurredAt, "correlation-1");
        SpareStatusCallbackCommand status = new SpareStatusCallbackCommand(
                "event-status-1", 1L, "SPARE_SYSTEM", "application-1", snowflakeId,
                "WAITING_DELIVERY", mutableSnapshot, occurredAt, "correlation-2");
        mutableSnapshot.put("status", "MUTATED");

        assertThat(binding.occurredAt()).isEqualTo(occurredAt);
        assertThat(status.statusVersion()).isEqualTo(snowflakeId);
        assertThat(status.statusSnapshot()).containsEntry("status", "ACCEPTED");
        assertThat(status.statusSnapshot()).isUnmodifiable();
    }

    @Test
    void canonicalizesEquivalentStatusObjectsAndDetachesNestedValues() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("z", "last");
        nested.put("a", "first");
        List<Object> stages = new ArrayList<>(List.of("CREATED", "ACCEPTED"));
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("stages", stages);
        first.put("detail", nested);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("detail", Map.of("a", "first", "z", "last"));
        second.put("stages", List.of("CREATED", "ACCEPTED"));

        SpareStatusCallbackCommand firstCommand = statusCommand("event-1", first);
        SpareStatusCallbackCommand secondCommand = statusCommand("event-2", second);
        nested.put("a", "mutated");
        stages.add("MUTATED");

        assertThat(firstCommand.statusSnapshot()).isEqualTo(secondCommand.statusSnapshot());
        assertThat(firstCommand.statusSnapshot().keySet()).containsExactly("detail", "stages");
        assertThat(firstCommand.statusSnapshot().get("detail"))
                .isEqualTo(Map.of("a", "first", "z", "last"));
        assertThat(firstCommand.statusSnapshot().get("stages"))
                .isEqualTo(List.of("CREATED", "ACCEPTED"));
    }

    @Test
    void exposesOnlyTheLockedOutcomesAndPublicErrors() {
        assertThat(SpareReferenceBindingOutcome.values()).containsExactly(
                SpareReferenceBindingOutcome.APPLIED, SpareReferenceBindingOutcome.REPLAYED);
        assertThat(SpareStatusCallbackOutcome.values()).containsExactly(
                SpareStatusCallbackOutcome.APPLIED, SpareStatusCallbackOutcome.REPLAYED,
                SpareStatusCallbackOutcome.IGNORED_OLD_VERSION);
        assertThat(CutoverSpareCallbackException.Code.values()).containsExactly(
                CutoverSpareCallbackException.Code.INVALID_REQUEST,
                CutoverSpareCallbackException.Code.TENANT_CONTEXT_MISMATCH,
                CutoverSpareCallbackException.Code.NOT_VISIBLE_OR_NOT_FOUND,
                CutoverSpareCallbackException.Code.IDEMPOTENCY_CONFLICT,
                CutoverSpareCallbackException.Code.IDEMPOTENCY_IN_PROGRESS,
                CutoverSpareCallbackException.Code.REFERENCE_IDENTITY_CONFLICT,
                CutoverSpareCallbackException.Code.STATUS_VERSION_CONFLICT,
                CutoverSpareCallbackException.Code.OWNER_DATA_CORRUPTED,
                CutoverSpareCallbackException.Code.PROVIDER_UNAVAILABLE);

        assertThat(new SpareExternalReferenceBindingResult(10L, "application-1", "EXTERNAL_REFERENCED",
                SpareReferenceBindingOutcome.APPLIED).integrationStatus()).isEqualTo("EXTERNAL_REFERENCED");
        assertThat(new SpareStatusCallbackResult(10L, 1L, SpareStatusCallbackOutcome.APPLIED).outcome())
                .isEqualTo(SpareStatusCallbackOutcome.APPLIED);
    }

    @Test
    void classifiesInvalidPublicInputWithTheStableApiError() {
        assertThatThrownBy(() -> new SpareExternalReferenceBindingCommand(
                " padded-event ", 1L, "request-1", "SPARE_SYSTEM", "external-request-1",
                "application-1", LocalDateTime.of(2026, 9, 2, 10, 30), "correlation-1"))
                .isInstanceOfSatisfying(CutoverSpareCallbackException.class,
                        error -> assertThat(error.code()).isEqualTo(
                                CutoverSpareCallbackException.Code.INVALID_REQUEST));
    }

    private static String[] componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(component -> component.getName()).toArray(String[]::new);
    }

    private static void assertSignature(Method method, Class<?> returnType, Class<?> parameterType) {
        assertThat(method.getReturnType()).isEqualTo(returnType);
        assertThat(method.getParameterTypes()).containsExactly(parameterType);
    }

    private static SpareStatusCallbackCommand statusCommand(String eventId, Map<String, Object> snapshot) {
        return new SpareStatusCallbackCommand(eventId, 1L, "SPARE_SYSTEM", "application-1", 1L,
                "WAITING_DELIVERY", snapshot, LocalDateTime.of(2026, 9, 2, 10, 30), "correlation-1");
    }
}
