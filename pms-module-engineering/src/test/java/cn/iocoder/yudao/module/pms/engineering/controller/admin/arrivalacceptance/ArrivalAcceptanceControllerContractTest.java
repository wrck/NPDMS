package cn.iocoder.yudao.module.pms.engineering.controller.admin.arrivalacceptance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.jackson.config.YudaoJacksonAutoConfiguration;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrivalacceptance.vo.ArrivalAcceptanceReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrivalacceptance.vo.ArrivalAcceptanceRespVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalAcceptanceDO;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.ArrivalAcceptanceApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.ArrivalAcceptanceCommandService;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.ArrivalAcceptanceContractException;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.ArrivalAcceptanceQueryService;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.ArrivalAcceptanceViews;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseBody;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ArrivalAcceptanceControllerContractTest {

    @Test
    void exposesEightLockedRoutesAndFivePermissionKeysWithoutProductionRegistration() {
        RequestMapping root = ArrivalAcceptanceController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/api/v1/pms/arrival-acceptances");
        assertThat(AnnotatedElementUtils.hasAnnotation(ArrivalAcceptanceController.class, RestController.class))
                .isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(ArrivalAcceptanceController.class, Component.class))
                .isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(ArrivalAcceptanceController.class, ResponseBody.class))
                .isTrue();

        Map<String, Endpoint> endpoints = new LinkedHashMap<>();
        for (Method method : ArrivalAcceptanceController.class.getDeclaredMethods()) {
            Endpoint endpoint = endpoint(method);
            if (endpoint != null) endpoints.put(method.getName(), endpoint);
        }
        assertThat(endpoints).containsExactlyInAnyOrderEntriesOf(Map.of(
                "list", new Endpoint("GET", "", "pms:arrival-acceptance:query"),
                "create", new Endpoint("POST", "", "pms:arrival-acceptance:create"),
                "detail", new Endpoint("GET", "/{id}", "pms:arrival-acceptance:query"),
                "patch", new Endpoint("PATCH", "/{id}", "pms:arrival-acceptance:edit-own-draft"),
                "submit", new Endpoint("POST", "/{id}/actions/submit", "pms:arrival-acceptance:edit-own-draft"),
                "confirm", new Endpoint("POST", "/{id}/actions/confirm", "pms:arrival-acceptance:confirm"),
                "raiseDifference", new Endpoint("POST", "/{id}/actions/raise-difference",
                        "pms:arrival-acceptance:resolve-difference"),
                "resolveDifference", new Endpoint("POST", "/{id}/actions/resolve-difference",
                        "pms:arrival-acceptance:resolve-difference")));
    }

    @Test
    void createMapsTrustedContextToApplicationCommandAndReturnsTypedCommandData() {
        ArrivalAcceptanceApplicationService application = mock(ArrivalAcceptanceApplicationService.class);
        ArrivalAcceptanceCommandService command = mock(ArrivalAcceptanceCommandService.class);
        ArrivalAcceptanceQueryService query = mock(ArrivalAcceptanceQueryService.class);
        ArrivalAcceptanceRequestContext context = mock(ArrivalAcceptanceRequestContext.class);
        var access = access();
        when(context.current()).thenReturn(new ArrivalAcceptanceRequestContext.TrustedContext(
                9L, 88L, "corr-create", access));
        ArrivalAcceptanceDO root = new ArrivalAcceptanceDO();
        root.setId(101L);
        when(application.createDraft(any())).thenReturn(root);
        when(query.detail(any())).thenReturn(detailView(101L));

        var controller = new ArrivalAcceptanceController(application, command, query, context);
        var result = controller.create("idem-create", JsonUtils.parseObject("""
                {"projectId":20,"batchCode":"ARR-001","logisticsNo":"LOG-001",
                 "arrivedAt":"2026-08-30T10:00:00","signerName":"张三",
                 "expectedDeliveryScopeVersion":7}
                """, JsonNode.class));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).extracting(ArrivalAcceptanceRespVO.Command::id,
                ArrivalAcceptanceRespVO.Command::projectId,
                ArrivalAcceptanceRespVO.Command::status,
                ArrivalAcceptanceRespVO.Command::allowedActions)
                .containsExactly(101L, 20L, "DRAFT", List.of("EDIT_DRAFT", "SUBMIT"));
        ArgumentCaptor<ArrivalAcceptanceApplicationService.CreateDraftCommand> captor =
                ArgumentCaptor.forClass(ArrivalAcceptanceApplicationService.CreateDraftCommand.class);
        verify(application).createDraft(captor.capture());
        assertThat(captor.getValue()).extracting(
                ArrivalAcceptanceApplicationService.CreateDraftCommand::tenantId,
                ArrivalAcceptanceApplicationService.CreateDraftCommand::actorUserId,
                ArrivalAcceptanceApplicationService.CreateDraftCommand::idempotencyKey,
                ArrivalAcceptanceApplicationService.CreateDraftCommand::correlationId)
                .containsExactly(9L, 88L, "idem-create", "corr-create");
    }

    @Test
    void standaloneHttpAssemblyExecutesPositiveListContract() throws Exception {
        ArrivalAcceptanceApplicationService application = mock(ArrivalAcceptanceApplicationService.class);
        ArrivalAcceptanceCommandService command = mock(ArrivalAcceptanceCommandService.class);
        ArrivalAcceptanceQueryService query = mock(ArrivalAcceptanceQueryService.class);
        ArrivalAcceptanceRequestContext context = mock(ArrivalAcceptanceRequestContext.class);
        when(context.current()).thenReturn(new ArrivalAcceptanceRequestContext.TrustedContext(
                9L, 88L, "corr-query", access()));
        when(query.page(any())).thenReturn(new cn.iocoder.yudao.framework.common.pojo.PageResult<>(
                List.of(new ArrivalAcceptanceViews.ArrivalListItem(101L, 20L, "ARR-001", "LOG-001",
                        LocalDateTime.of(2026, 8, 30, 10, 0), "张三", "DRAFT", null, 0,
                        List.of("EDIT_DRAFT"), LocalDateTime.of(2026, 8, 30, 9, 0))), 1L));
        var controller = new TestArrivalAcceptanceController(application, command, query, context);

        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/v1/pms/arrival-acceptances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].batchCode").value("ARR-001"));
    }

    @Test
    void realHttpBoundaryMapsValidationPermissionAndMachineFailures() throws Exception {
        ArrivalAcceptanceApplicationService application = mock(ArrivalAcceptanceApplicationService.class);
        ArrivalAcceptanceCommandService command = mock(ArrivalAcceptanceCommandService.class);
        ArrivalAcceptanceQueryService query = mock(ArrivalAcceptanceQueryService.class);
        ArrivalAcceptanceRequestContext context = mock(ArrivalAcceptanceRequestContext.class);
        when(context.current()).thenReturn(new ArrivalAcceptanceRequestContext.TrustedContext(
                9L, 88L, "corr-http", access()));
        when(application.createDraft(any())).thenThrow(
                ArrivalAcceptanceContractException.simple("IDEMPOTENCY_CONFLICT",
                        "IDEMPOTENCY_PAYLOAD_CONFLICT", "conflict"),
                ArrivalAcceptanceContractException.simple("EVIDENCE_INVALID",
                        "EVIDENCE_MISSING", "evidence missing"),
                ArrivalAcceptanceContractException.owner("OWNER_PROVIDER_UNAVAILABLE",
                        "COM_PROVIDER_UNAVAILABLE", "COM", "commerce unavailable"));
        when(query.detail(any())).thenThrow(new ArrivalAcceptanceQueryService.NotVisibleException());
        var mvc = MockMvcBuilders.standaloneSetup(
                new TestArrivalAcceptanceController(application, command, query, context)).build();
        String create = """
                {"projectId":20,"batchCode":"ARR-001","logisticsNo":"LOG-001",
                 "arrivedAt":"2026-08-30T10:00:00","signerName":"张三",
                 "expectedDeliveryScopeVersion":7}
                """;

        mvc.perform(post("/api/v1/pms/arrival-acceptances").contentType(MediaType.APPLICATION_JSON)
                        .content(create)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(post("/api/v1/pms/arrival-acceptances").header("Idempotency-Key", "idem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(create.replace(",\"signerName\":\"张三\"", "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(post("/api/v1/pms/arrival-acceptances").header("Idempotency-Key", "idem")
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data").doesNotExist());

        mvc.perform(post("/api/v1/pms/arrival-acceptances").header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.category").value("IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.data.reasonCode").value("IDEMPOTENCY_PAYLOAD_CONFLICT"));
        mvc.perform(post("/api/v1/pms/arrival-acceptances").header("Idempotency-Key", "idem-2")
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.category").value("EVIDENCE_INVALID"));
        mvc.perform(post("/api/v1/pms/arrival-acceptances").header("Idempotency-Key", "idem-3")
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.data.ownerContext").value("COM"));
        mvc.perform(get("/api/v1/pms/arrival-acceptances/101"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(1011004011));

        ArrivalAcceptanceRequestContext denied = mock(ArrivalAcceptanceRequestContext.class);
        when(denied.current()).thenThrow(new AccessDeniedException("denied"));
        var deniedMvc = MockMvcBuilders.standaloneSetup(
                new TestArrivalAcceptanceController(application, command, query, denied)).build();
        deniedMvc.perform(get("/api/v1/pms/arrival-acceptances"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void serializesSnowflakeIdsAndAbsentEvidenceWithLockedWireShape() throws Exception {
        long snowflake = 9_007_199_254_740_992L;
        ArrivalAcceptanceRespVO.Detail detail = new ArrivalAcceptanceRespVO.Detail(
                snowflake, 20L, "ARR-001", "LOG-001", LocalDateTime.of(2026, 8, 30, 10, 0),
                "张三", "DRAFT", 7L, new ArrivalAcceptanceRespVO.ScopeWatermark(7L, List.of()),
                null, null, null, null, null, null, null, null, null, 0,
                List.of("EDIT_DRAFT"), List.of(), List.of(), null);

        var module = new YudaoJacksonAutoConfiguration().timestampSupportModuleBean();
        JsonMapper mapper = JsonMapper.builder().addModule(module).build();
        JsonNode json = mapper.readTree(mapper.writeValueAsString(detail));
        assertThat(json.get("id").isString()).isTrue();
        assertThat(json.get("id").asText()).isEqualTo("9007199254740992");
        assertThat(json.get("evidence").isNull()).isTrue();
        assertThat(json.propertyStream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder(
                "id", "projectId", "batchCode", "logisticsNo", "arrivedAt", "signerName", "status",
                "deliveryScopeVersion", "scopeWatermark", "evidenceId", "evidenceRevision",
                "projectFactVersion", "predecessorAcceptanceId", "successorReason", "submittedBy",
                "submittedAt", "confirmedBy", "confirmedAt", "version", "allowedActions",
                "currentLines", "differences", "evidence");
    }

    @Test
    void deserializesResolveRequestByLockedDiscriminator() {
        String json = """
                {"resolutionType":"SUPPLEMENT","differenceId":31,"expectedDifferenceRevision":2,
                 "expectedDifferenceVersion":4,
                 "supplementScope":{"scopeType":"DEVICE","deviceId":99},
                 "reason":"补签完成",
                 "evidenceRevision":{"artifactId":5,"referenceKey":"ref-5","versionNo":1,
                   "scopeVersion":3,"fileFactVersion":{"artifactVersion":1,"referenceVersion":2,
                   "availabilityVersion":3},"hash":"hash-5"}}
                """;

        ArrivalAcceptanceReqVO.Resolution resolution = JsonUtils.parseObject(
                json, ArrivalAcceptanceReqVO.Resolution.class);
        assertThat(resolution).isInstanceOf(ArrivalAcceptanceReqVO.Supplement.class);
        ArrivalAcceptanceReqVO.Supplement supplement = (ArrivalAcceptanceReqVO.Supplement) resolution;
        assertThat(supplement.supplementScope()).isInstanceOf(ArrivalAcceptanceReqVO.DeviceScope.class);
        assertThat(((ArrivalAcceptanceReqVO.DeviceScope) supplement.supplementScope()).deviceId()).isEqualTo(99L);
    }

    @Test
    void rejectsUnknownClientFieldsEvenWhenSharedMapperIgnoresUnknownProperties() {
        String json = """
                {"projectId":20,"batchCode":"ARR-001","logisticsNo":"LOG-001",
                 "arrivedAt":"2026-08-30T10:00:00","signerName":"张三",
                 "expectedDeliveryScopeVersion":7,"status":"CONFIRMED"}
                """;

        JsonMapper lenientMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> lenientMapper.readValue(
                        json, ArrivalAcceptanceReqVO.Create.class))
                .isInstanceOf(RuntimeException.class);

        String nestedJson = """
                {"artifactId":5,"referenceKey":"ref-5","versionNo":1,"scopeVersion":3,
                 "fileFactVersion":{"artifactVersion":1,"referenceVersion":2,
                   "availabilityVersion":3,"serverOwnedVersion":4},"hash":"hash-5"}
                """;
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> lenientMapper.readValue(
                        nestedJson, ArrivalAcceptanceReqVO.FileRevision.class))
                .isInstanceOf(RuntimeException.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ArrivalAcceptanceRequestCodec.raise(
                        lenientMapper.readTree("""
                                {"arrivalLineId":31,"expectedLineVersion":2,"differenceTypeCode":"SHORTAGE",
                                 "scopeSnapshot":{"scopeType":"DEVICE"},"reason":"缺件","riskDescription":"延期",
                                 "evidenceRevision":{"artifactId":5,"referenceKey":"ref-5","versionNo":1,
                                  "scopeVersion":3,"fileFactVersion":{"artifactVersion":1,"referenceVersion":2,
                                  "availabilityVersion":3},"hash":"hash-5"}}
                                """)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Task 12 前仅在测试中激活候选 Controller，避免提前进入生产组件扫描。 */
    @RestController
    @RequestMapping("/api/v1/pms/arrival-acceptances")
    static final class TestArrivalAcceptanceController extends ArrivalAcceptanceController {

        TestArrivalAcceptanceController(ArrivalAcceptanceApplicationService applicationService,
                                        ArrivalAcceptanceCommandService commandService,
                                        ArrivalAcceptanceQueryService queryService,
                                        ArrivalAcceptanceRequestContext requestContext) {
            super(applicationService, commandService, queryService, requestContext);
        }
    }

    private static Endpoint endpoint(Method method) {
        String verb;
        String path;
        if (method.isAnnotationPresent(GetMapping.class)) {
            verb = "GET";
            path = first(method.getAnnotation(GetMapping.class).value());
        } else if (method.isAnnotationPresent(PostMapping.class)) {
            verb = "POST";
            path = first(method.getAnnotation(PostMapping.class).value());
        } else if (method.isAnnotationPresent(PatchMapping.class)) {
            verb = "PATCH";
            path = first(method.getAnnotation(PatchMapping.class).value());
        } else {
            return null;
        }
        PreAuthorize authorize = method.getAnnotation(PreAuthorize.class);
        String permission = Arrays.stream(new String[]{
                        "pms:arrival-acceptance:query", "pms:arrival-acceptance:create",
                        "pms:arrival-acceptance:edit-own-draft", "pms:arrival-acceptance:confirm",
                        "pms:arrival-acceptance:resolve-difference"})
                .filter(authorize.value()::contains).findFirst().orElseThrow();
        return new Endpoint(verb, path, permission);
    }

    private static String first(String[] paths) {
        return paths.length == 0 ? "" : paths[0];
    }

    private static ArrivalAcceptanceViews.AccessContext access() {
        return new ArrivalAcceptanceViews.AccessContext(88L,
                Set.of("pms:arrival-acceptance:create", "pms:arrival-acceptance:edit-own-draft"),
                Set.of(20L), Set.of(20L), Set.of(20L), Set.of(20L));
    }

    private static ArrivalAcceptanceViews.ArrivalDetail detailView(Long id) {
        return new ArrivalAcceptanceViews.ArrivalDetail(id, 20L, "ARR-001", "LOG-001",
                LocalDateTime.of(2026, 8, 30, 10, 0), "张三", "DRAFT", 7L,
                new ArrivalAcceptanceViews.ScopeWatermarkData(7L, List.of()),
                null, null, null, null, null, null, null, null, null, 0,
                List.of("EDIT_DRAFT", "SUBMIT"), List.of(), List.of(), null);
    }

    private record Endpoint(String method, String path, String permission) {
    }
}
