package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanStepDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverSupportArrangementDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanStepMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverSupportArrangementMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.CreateCutoverPlanDraftCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.SaveCutoverPlanDraftCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanRules;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.CutoverPlanCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverTaskRules;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverPlanApplicationServiceTest {

    @Test
    void createsStandardDraftFromFrozenOwnerFacts() {
        Fixture fixture = fixture("A");
        CutoverPlanCommandResult result = fixture.service.createDraft(command("ONLINE_TEMPLATE_STANDARD", null, null));

        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(fixture.inserted.get().getEditModeCode()).isEqualTo("ONLINE_TEMPLATE_STANDARD");
        assertThat(JsonUtils.parseObject(fixture.inserted.get().getContentSnapshot(), tools.jackson.databind.JsonNode.class)
                .path("overview").path("deviceSummary")).hasSize(1);
        assertThat(fixture.inserted.get().getOwnershipConfirmed()).isNull();
        assertThat(fixture.platform.facts).singleElement().satisfies(fact ->
                assertThat(fact.correlationId()).isEqualTo("corr-create"));
        verifyNoInteractions(fixture.filePort);
    }

    @Test
    void createsSimpleDDraftWithoutChecklistOrFileOwner() {
        Fixture fixture = fixture("D");
        fixture.service.createDraft(command("ONLINE_TEMPLATE_SIMPLE_D", null, null));

        assertThat(fixture.inserted.get().getChecklistId()).isNull();
        assertThat(JsonUtils.parseObject(fixture.inserted.get().getContentSnapshot(), tools.jackson.databind.JsonNode.class)
                .path("editMode").asText()).isEqualTo("ONLINE_TEMPLATE_SIMPLE_D");
        verifyNoInteractions(fixture.filePort);
    }

    @Test
    void createsUploadDraftWithLockedFileFact() {
        Fixture fixture = fixture("A");
        CutoverPlanFilePort.FileFact file = fileFact();
        when(fixture.filePort.inspect(1L, 8L, 70L, file.handle())).thenReturn(file);
        when(fixture.filePort.lockAndRevalidate(1L, 8L, 70L, file.handle())).thenReturn(file);

        fixture.service.createDraft(command("FULL_FILE_UPLOAD", file, true));

        assertThat(fixture.inserted.get().getContentSnapshot()).isNull();
        assertThat(fixture.inserted.get().getFileArtifactId()).isEqualTo(901L);
        assertThat(fixture.inserted.get().getOwnershipConfirmed()).isTrue();
        verify(fixture.filePort).inspect(1L, 8L, 70L, file.handle());
        verify(fixture.filePort).lockAndRevalidate(1L, 8L, 70L, file.handle());
        InOrder order = inOrder(fixture.projectPort, fixture.sourcePort, fixture.filePort,
                fixture.taskMapper, fixture.planMapper);
        order.verify(fixture.projectPort).lockAndRevalidate(8L, 70L, "ACTION_EDIT", 30L);
        order.verify(fixture.sourcePort).lockAndRevalidate(eq(1L), eq(8L), any());
        order.verify(fixture.filePort).lockAndRevalidate(1L, 8L, 70L, file.handle());
        order.verify(fixture.taskMapper).selectForUpdate(any());
        order.verify(fixture.planMapper).selectCurrentForUpdate(any());
    }

    @Test
    void savesSimpleDDraftWithAtomicChildReplacement() {
        Fixture fixture = fixture("D");
        fixture.service.createDraft(command("ONLINE_TEMPLATE_SIMPLE_D", null, null));
        CutoverPlanRevisionDO plan = fixture.inserted.get();
        when(fixture.planMapper.selectCurrent(any())).thenReturn(plan);
        when(fixture.planMapper.selectCurrentForUpdate(any())).thenReturn(plan);
        when(fixture.planMapper.replaceDraftIfMatch(any())).thenReturn(1);
        List<CutoverPlanStepDO> savedSteps = new ArrayList<>();
        when(fixture.stepMapper.insert(any(CutoverPlanStepDO.class)))
                .thenAnswer(invocation -> { savedSteps.add(invocation.getArgument(0)); return 1; });
        tools.jackson.databind.node.ObjectNode body = JsonUtils.getObjectMapper().createObjectNode();
        body.put("editMode", "ONLINE_TEMPLATE_SIMPLE_D");
        tools.jackson.databind.node.ArrayNode steps = body.putArray("steps");
        tools.jackson.databind.node.ObjectNode operation = steps.addObject();
        operation.put("sectionCode", "OPERATION"); operation.put("stepNo", 1); operation.put("content", "执行割接");
        tools.jackson.databind.node.ObjectNode rollback = steps.addObject();
        rollback.put("sectionCode", "ROLLBACK"); rollback.put("stepNo", 1); rollback.put("content", "执行回退");

        CutoverPlanCommandResult result = fixture.service.saveDraft(new SaveCutoverPlanDraftCommand(
                1L, 8L, 50L, 4, 0, 30L, body, "save-key-1", "corr-save"));

        assertThat(result.planVersion()).isEqualTo(1);
        assertThat(savedSteps).extracting(CutoverPlanStepDO::getSectionCode)
                .containsExactly("OPERATION", "ROLLBACK");
        verify(fixture.stepMapper).deleteDraftRows(any());
        verify(fixture.supportMapper).deleteDraftRows(any());
        assertThat(fixture.platform.facts.getLast().correlationId()).isEqualTo("corr-save");
    }

    @Test
    void saveUploadLocksFileBeforeTaskAndPlanRows() {
        Fixture fixture = fixture("A");
        CutoverPlanFilePort.FileFact file = fileFact();
        when(fixture.filePort.inspect(1L, 8L, 70L, file.handle())).thenReturn(file);
        when(fixture.filePort.lockAndRevalidate(1L, 8L, 70L, file.handle())).thenReturn(file);
        fixture.service.createDraft(command("FULL_FILE_UPLOAD", file, true));
        CutoverPlanRevisionDO plan = fixture.inserted.get();
        clearInvocations(fixture.projectPort, fixture.sourcePort, fixture.filePort,
                fixture.taskMapper, fixture.planMapper);
        when(fixture.planMapper.selectCurrent(any())).thenReturn(plan);
        when(fixture.planMapper.selectCurrentForUpdate(any())).thenReturn(plan);
        when(fixture.planMapper.replaceDraftIfMatch(any())).thenReturn(1);

        fixture.service.saveDraft(new SaveCutoverPlanDraftCommand(1L, 8L, 50L, 4, 0, 30L,
                uploadContent(file), "upload-save", "corr-upload-save"));

        InOrder order = inOrder(fixture.projectPort, fixture.sourcePort, fixture.filePort,
                fixture.taskMapper, fixture.planMapper);
        order.verify(fixture.projectPort).lockAndRevalidate(8L, 70L, "ACTION_EDIT", 30L);
        order.verify(fixture.sourcePort).lockAndRevalidate(eq(1L), eq(8L), any());
        order.verify(fixture.filePort).lockAndRevalidate(1L, 8L, 70L, file.handle());
        order.verify(fixture.taskMapper).selectForUpdate(any());
        order.verify(fixture.planMapper).selectCurrentForUpdate(any());
    }

    @Test
    void saveReplayAcceptsEquivalentJsonObjectKeyOrder() {
        Fixture fixture = fixture("D");
        fixture.service.createDraft(command("ONLINE_TEMPLATE_SIMPLE_D", null, null));
        CutoverPlanRevisionDO plan = fixture.inserted.get();
        when(fixture.planMapper.selectCurrent(any())).thenReturn(plan);
        when(fixture.planMapper.selectCurrentForUpdate(any())).thenReturn(plan);
        when(fixture.planMapper.replaceDraftIfMatch(any())).thenReturn(1);
        when(fixture.stepMapper.insert(any(CutoverPlanStepDO.class))).thenReturn(1);
        tools.jackson.databind.JsonNode first = simpleContent(false);
        tools.jackson.databind.JsonNode reordered = simpleContent(true);

        CutoverPlanCommandResult saved = fixture.service.saveDraft(new SaveCutoverPlanDraftCommand(
                1L, 8L, 50L, 4, 0, 30L, first, "same-save", "corr-a"));
        CutoverPlanCommandResult replayed = fixture.service.saveDraft(new SaveCutoverPlanDraftCommand(
                1L, 8L, 50L, 4, 0, 30L, reordered, "same-save", "corr-b"));

        assertThat(replayed.replayed()).isTrue();
        assertThat(replayed.planVersion()).isEqualTo(saved.planVersion());
        verify(fixture.planMapper, times(1)).replaceDraftIfMatch(any());
    }

    @Test
    void preservesExistingSupportIdentityByRoleWhenClientOmitsId() {
        Fixture fixture = fixture("A");
        fixture.service.createDraft(command("ONLINE_TEMPLATE_STANDARD", null, null));
        CutoverPlanRevisionDO plan = fixture.inserted.get();
        when(fixture.planMapper.selectCurrent(any())).thenReturn(plan);
        when(fixture.planMapper.selectCurrentForUpdate(any())).thenReturn(plan);
        when(fixture.planMapper.replaceDraftIfMatch(any())).thenReturn(1);
        when(fixture.stepMapper.insert(any(CutoverPlanStepDO.class))).thenReturn(1);
        CutoverSupportArrangementDO existing = new CutoverSupportArrangementDO();
        existing.setId(91L); existing.setRoleCode("CUSTOMER");
        when(fixture.supportMapper.selectListByPlanForUpdate(any())).thenReturn(List.of(existing));
        AtomicReference<CutoverSupportArrangementDO> saved = new AtomicReference<>();
        when(fixture.supportMapper.insert(any(CutoverSupportArrangementDO.class)))
                .thenAnswer(invocation -> { saved.set(invocation.getArgument(0)); return 1; });

        fixture.service.saveDraft(new SaveCutoverPlanDraftCommand(1L, 8L, 50L, 4, 0, 30L,
                standardContent(null), "save-support", "corr-support"));

        assertThat(saved.get().getId()).isEqualTo(91L);
        assertThat(saved.get().getRoleCode()).isEqualTo("CUSTOMER");
    }

    private static Fixture fixture(String grade) {
        CutoverTaskMapper taskMapper = mock(CutoverTaskMapper.class);
        CutoverPlanRevisionMapper planMapper = mock(CutoverPlanRevisionMapper.class);
        CutoverPlanStepMapper stepMapper = mock(CutoverPlanStepMapper.class);
        CutoverSupportArrangementMapper supportMapper = mock(CutoverSupportArrangementMapper.class);
        CutoverProjectScopePort project = mock(CutoverProjectScopePort.class);
        CutoverPlanFilePort file = mock(CutoverPlanFilePort.class);
        CutoverTaskDO task = task();
        when(taskMapper.selectById(50L)).thenReturn(task);
        when(taskMapper.selectForUpdate(any())).thenReturn(task);
        when(project.inspect(8L, 70L, "ACTION_EDIT")).thenReturn(new CutoverProjectScopePort.ProjectScopeFact(70L, 30L, true));
        when(project.lockAndRevalidate(8L, 70L, "ACTION_EDIT", 30L))
                .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(70L, 30L, true));
        CutoverPlanSourcePort.SourceFacts facts = facts(grade);
        CutoverPlanSourcePort source = mock(CutoverPlanSourcePort.class);
        when(source.inspect(1L, 8L, 50L)).thenReturn(facts);
        when(source.lockAndRevalidate(eq(1L), eq(8L), any())).thenReturn(facts);
        when(planMapper.selectCurrentForUpdate(any())).thenReturn(null);
        when(planMapper.selectMaxRevisionNo(any())).thenReturn(0);
        AtomicReference<CutoverPlanRevisionDO> inserted = new AtomicReference<>();
        when(planMapper.insert(any(CutoverPlanRevisionDO.class)))
                .thenAnswer(invocation -> { inserted.set(invocation.getArgument(0)); return 1; });
        DirectPlatform platform = new DirectPlatform();
        CutoverPlanApplicationService service = new CutoverPlanApplicationService(taskMapper, planMapper, stepMapper,
                supportMapper, project, source, file, new CutoverPlanContentCodec(), platform,
                Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC));
        return new Fixture(service, file, source, project, taskMapper, inserted, platform,
                planMapper, stepMapper, supportMapper);
    }

    private static CutoverTaskDO task() {
        CutoverTaskDO task = new CutoverTaskDO(); task.setId(50L); task.setTenantId(1L); task.setProjectId(70L);
        task.setOwnerUserId(8L); task.setTaskOrigin(CutoverTaskRules.ORIGIN_NEW_PLATFORM);
        task.setCurrentStage(CutoverTaskRules.STAGE_P4); task.setTaskStatus(CutoverTaskRules.STATUS_PLAN_DRAFTING);
        task.setVersion(4); return task;
    }

    private static CutoverPlanSourcePort.SourceFacts facts(String grade) {
        List<CutoverPlanSourcePort.RiskFactSnapshot> risks = "D".equals(grade) ? List.of() : List.of(
                new CutoverPlanSourcePort.RiskFactSnapshot(501L, "risk-1", 2, "风险一", "FAILED", "端口检查失败"));
        List<String> sections = "D".equals(grade) ? CutoverPlanRules.SIMPLE_SECTIONS
                : CutoverPlanRules.STANDARD_SECTIONS;
        List<CutoverPlanSourcePort.TemplateSectionSnapshot> templates = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) templates.add(new CutoverPlanSourcePort.TemplateSectionSnapshot(
                sections.get(i), sections.get(i), i + 1, List.of("NETWORK_CUTOVER"), List.of(grade), true));
        CutoverPlanSourcePort.SourceSnapshot snapshot = new CutoverPlanSourcePort.SourceSnapshot(1, 50L, 4,
                100L, 2, grade, "D".equals(grade) ? null : 200L, "D".equals(grade) ? null : 3,
                70L, 6, 30L, List.of(new CutoverPlanSourcePort.DeviceSnapshot(301L, "SN-1", 9L,
                "ROUTER", "type-v1")), 401L, "CFG-1", 1, templates, risks);
        return new CutoverPlanSourcePort.SourceFacts(snapshot, risks);
    }

    private static CutoverPlanFilePort.FileFact fileFact() {
        return new CutoverPlanFilePort.FileFact(901L, 2, "cut-plan.pdf",
                new CutoverPlanFilePort.FileFactVersion(3, 4, 5), 6L, "a".repeat(64));
    }

    private static CreateCutoverPlanDraftCommand command(String mode, CutoverPlanFilePort.FileFact file, Boolean ownership) {
        return new CreateCutoverPlanDraftCommand(1L, 8L, 50L, 4, 30L, mode, file, ownership,
                "create-key-1", "corr-create");
    }

    private static tools.jackson.databind.node.ObjectNode simpleContent(boolean reverseKeys) {
        tools.jackson.databind.node.ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        tools.jackson.databind.node.ArrayNode steps = JsonUtils.getObjectMapper().createArrayNode();
        tools.jackson.databind.node.ObjectNode operation = JsonUtils.getObjectMapper().createObjectNode();
        if (reverseKeys) {
            operation.put("content", "执行割接"); operation.put("stepNo", 1); operation.put("sectionCode", "OPERATION");
        } else {
            operation.put("sectionCode", "OPERATION"); operation.put("stepNo", 1); operation.put("content", "执行割接");
        }
        steps.add(operation);
        if (reverseKeys) { root.set("steps", steps); root.put("editMode", "ONLINE_TEMPLATE_SIMPLE_D"); }
        else { root.put("editMode", "ONLINE_TEMPLATE_SIMPLE_D"); root.set("steps", steps); }
        return root;
    }

    private static tools.jackson.databind.node.ObjectNode standardContent(Long arrangementId) {
        tools.jackson.databind.node.ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        root.put("editMode", "ONLINE_TEMPLATE_STANDARD");
        tools.jackson.databind.node.ObjectNode overview = root.putObject("overview");
        overview.put("projectDescription", ""); overview.putArray("scheduleTable");
        overview.putNull("preTopologyFile"); overview.putNull("postTopologyFile");
        tools.jackson.databind.node.ObjectNode device = overview.putArray("deviceSummary").addObject();
        device.put("deviceId", 301L); device.put("serialNumber", "SN-1");
        device.put("projectAssignmentVersion", 9L); device.put("deviceTypeCode", "ROUTER");
        device.put("deviceTypeSourceVersion", "type-v1"); overview.putNull("networkConfigurationFile");
        root.putArray("steps").addObject().put("sectionCode", "OPERATION").put("stepNo", 1).put("content", "执行割接");
        root.putArray("riskMitigations");
        tools.jackson.databind.node.ObjectNode support = root.putArray("supportArrangements").addObject();
        if (arrangementId == null) support.putNull("arrangementId"); else support.put("arrangementId", arrangementId);
        support.put("roleCode", "CUSTOMER"); support.put("personName", "客户经理");
        support.put("dutyDescription", "现场确认"); support.put("phone", "13800000000");
        support.put("arrivalTime", 1_788_192_000_000L); return root;
    }

    private static tools.jackson.databind.node.ObjectNode uploadContent(CutoverPlanFilePort.FileFact fact) {
        tools.jackson.databind.node.ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        root.put("editMode", "FULL_FILE_UPLOAD");
        tools.jackson.databind.node.ObjectNode file = root.putObject("fileArtifactFact");
        file.put("artifactId", fact.artifactId()); file.put("versionNo", fact.versionNo());
        file.put("referenceKey", fact.referenceKey());
        file.set("fileFactVersion", JsonUtils.getObjectMapper().valueToTree(fact.fileFactVersion()));
        file.put("scopeVersion", fact.scopeVersion()); file.put("sha256", fact.sha256());
        root.put("ownershipConfirmed", true); return root;
    }

    private static class Fixture {
        final CutoverPlanApplicationService service; final CutoverPlanFilePort filePort;
        final CutoverPlanSourcePort sourcePort; final CutoverProjectScopePort projectPort;
        final CutoverTaskMapper taskMapper;
        final AtomicReference<CutoverPlanRevisionDO> inserted; final DirectPlatform platform;
        final CutoverPlanRevisionMapper planMapper; final CutoverPlanStepMapper stepMapper;
        final CutoverSupportArrangementMapper supportMapper;
        Fixture(CutoverPlanApplicationService service, CutoverPlanFilePort filePort,
                CutoverPlanSourcePort sourcePort, CutoverProjectScopePort projectPort,
                CutoverTaskMapper taskMapper,
                AtomicReference<CutoverPlanRevisionDO> inserted, DirectPlatform platform,
                CutoverPlanRevisionMapper planMapper, CutoverPlanStepMapper stepMapper,
                CutoverSupportArrangementMapper supportMapper) {
            this.service = service; this.filePort = filePort; this.sourcePort = sourcePort;
            this.projectPort = projectPort; this.taskMapper = taskMapper;
            this.inserted = inserted; this.platform = platform;
            this.planMapper = planMapper; this.stepMapper = stepMapper; this.supportMapper = supportMapper;
        }
    }

    private static final class DirectPlatform implements PlatformCommandExecutionApi {
        private final List<SuccessFacts> facts = new ArrayList<>();
        private final Map<String, Cached> completed = new HashMap<>();
        @Override public <T> ExecutionResult<T> execute(IdempotencyScope scope, String digest, Class<T> type,
                                                        Supplier<T> operation, Function<T, SuccessFacts> factsFactory) {
            String key = scope.scopeCode() + ':' + scope.key(); Cached cached = completed.get(key);
            if (cached != null) return cached.digest.equals(digest)
                    ? new ExecutionResult<>(Decision.REPLAY_COMPLETED, type.cast(cached.value))
                    : new ExecutionResult<>(Decision.CONFLICT, null);
            T value = operation.get(); facts.add(factsFactory.apply(value)); completed.put(key, new Cached(digest, value));
            return new ExecutionResult<>(Decision.NEW, value);
        }
        private record Cached(String digest, Object value) {}
    }
}
