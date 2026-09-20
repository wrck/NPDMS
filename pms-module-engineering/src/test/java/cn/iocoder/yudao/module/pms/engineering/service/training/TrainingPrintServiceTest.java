package cn.iocoder.yudao.module.pms.engineering.service.training;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class TrainingPrintServiceTest {
    private final DynamicFormBusinessInstanceApi api = mock(DynamicFormBusinessInstanceApi.class);
    private final TrainingPrintService service = new TrainingPrintService();
    @BeforeEach void setUp() { TenantContextHolder.setTenantId(7L); ReflectionTestUtils.setField(service, "templateApi", api); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void capturesValidatedPublishedRevisionForTrustedTenant() {
        var fact = fact("{\"trainingPrint\":" + JsonUtils.toJsonString(TrainingPrintLayout.defaults()) + "}");
        when(api.inspectCurrentRevisionForUsage(any())).thenAnswer(call -> {
            DynamicFormCurrentRevisionQuery query = call.getArgument(0);
            assertEquals(7L, query.tenantId()); assertEquals(100L, query.templateId());
            assertEquals(TrainingPrintTemplatePolicy.KEY, query.providerKey());
            return fact;
        });
        when(api.lockAndRevalidateRevisionForUsage(any())).thenReturn(fact);
        TrainingDO row = new TrainingDO(); service.capture(row, 100L);
        assertEquals(101L, row.getPrintRevisionId()); assertEquals(100L, row.getPrintTemplateId());
        assertEquals(TrainingPrintLayout.defaults(), TrainingPrintLayout.parse(row.getPrintLayoutSnapshot()));
        verify(api).lockAndRevalidateRevisionForUsage(any());
    }
    @Test void failedRevalidationDoesNotChangeSnapshot() {
        when(api.inspectCurrentRevisionForUsage(any())).thenReturn(fact("{}"));
        when(api.lockAndRevalidateRevisionForUsage(any())).thenThrow(new IllegalStateException("disabled or stale"));
        TrainingDO row = new TrainingDO(); row.setPrintLayoutSnapshot("original");
        assertThrows(IllegalStateException.class, () -> service.capture(row, 100L));
        assertEquals("original", row.getPrintLayoutSnapshot()); assertNull(row.getPrintTemplateId());
    }
    @Test void rejectsPublishedTemplateWithoutPrintSettings() {
        var fact = fact("{}");
        when(api.inspectCurrentRevisionForUsage(any())).thenReturn(fact);
        when(api.lockAndRevalidateRevisionForUsage(any())).thenReturn(fact);
        TrainingDO row = new TrainingDO();
        assertThrows(RuntimeException.class, () -> service.capture(row, 100L));
        assertNull(row.getPrintRevisionId());
    }
    private DynamicFormRevisionFact fact(String conf) {
        return fact(conf, "[]");
    }
    @Test void capturesNativeLayoutAndNestedSignatureWithoutChangingConfiguration() {
        String rules = """
                [{"type":"input","field":"name"},{"type":"input","field":"content"},
                 {"type":"input","field":"trainingTime"},
                 {"type":"row","children":[{"type":"signaturePad","field":"signatureImageDataUrl"}]}]
                """;
        var fact = fact("{\"form\":{\"labelWidth\":\"160px\"}}", rules);
        when(api.inspectCurrentRevisionForUsage(any())).thenReturn(fact);
        when(api.lockAndRevalidateRevisionForUsage(any())).thenReturn(fact);
        var record = new TrainingDO();
        service.capture(record, 100L);
        var snapshot = JsonUtils.parseTree(record.getPrintLayoutSnapshot());
        assertEquals("FORM_CREATE_ELEMENT_PLUS", snapshot.path("engine").asText());
        assertEquals(JsonUtils.parseTree(rules), snapshot.path("formRulesJson"));
        assertEquals("160px", snapshot.path("formConfJson").path("form").path("labelWidth").asText());
    }
    private DynamicFormRevisionFact fact(String conf, String rules) {
        return new DynamicFormRevisionFact(7L, TrainingPrintTemplatePolicy.KEY, 100L, 101L, 1, 1,
                TrainingPrintTemplatePolicy.USAGE, null, "FORM_CREATE_ELEMENT_PLUS", "3.4.0", "3.2.38", conf, rules, List.of(), null);
    }
}
