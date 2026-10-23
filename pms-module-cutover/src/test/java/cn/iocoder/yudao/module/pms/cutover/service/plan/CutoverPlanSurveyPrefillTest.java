package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistView;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.SaveCutoverPlanDraftCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class CutoverPlanSurveyPrefillTest {
    private final CutoverChecklistApplicationService checklists = mock(CutoverChecklistApplicationService.class);
    private final FileArtifactApi files = mock(FileArtifactApi.class);
    private final CutoverPlanSurveyPrefill prefill = new CutoverPlanSurveyPrefill(checklists, files);
    private final String file = "{\"artifactId\":901,\"versionNo\":2,\"referenceKey\":\"source\",\"scopeVersion\":30,\"sha256\":\"" + "a".repeat(64)
            + "\",\"fileFactVersion\":{\"artifactVersion\":1,\"referenceVersion\":2,\"availabilityVersion\":3}}";

    @Test void bindsExactTopologyVersionAndOnlyFillsEmptyFieldsWithoutChangingTheSource() {
        when(checklists.getView(any())).thenReturn(survey(80L, "SUBMITTED"));
        when(files.attachExistingVersions(any())).thenAnswer(invocation -> {
            var command = (AttachExistingFileVersionsCommand) invocation.getArgument(0);
            var item = command.items().getFirst();
            assertThat(item.source().objectId()).isEqualTo("81");
            assertThat(item.source().objectType()).isEqualTo("CUTOVER_CHECKLIST_ITEM");
            assertThat(item.source().artifactId()).isEqualTo(901L);
            assertThat(item.source().versionNo()).isEqualTo(2);
            assertThat(item.target().objectId()).isEqualTo("50");
            assertThat(item.target().objectType()).isEqualTo("CUTOVER_PLAN");
            return List.of(fact(item.target().referenceKey()));
        });
        var query = query();
        var before = query.content().rootSnapshot().deepCopy();
        var result = prefill.fill(query);
        assertThat(result.rootSnapshot().path("overview").path("projectDescription").asText()).isEqualTo("割接背景：替换设备\n涉及业务：办公网络");
        var topology = result.rootSnapshot().path("overview").path("preTopologyFile");
        assertThat(topology.path("artifactId").asLong()).isEqualTo(901L);
        assertThat(topology.path("versionNo").asInt()).isEqualTo(2);
        assertThat(topology.path("referenceKey").asText()).isNotEqualTo("source");
        assertThat(result.rootSnapshot().path("overview").path("postTopologyFile").isNull()).isTrue();
        assertThat(result.rootSnapshot().path("overview").path("networkConfigurationFile").isNull()).isTrue();
        assertThat(query.content().rootSnapshot()).isEqualTo(before);
        verify(checklists).getView(argThat(read -> read.taskId().equals(50L) && read.checklistId().equals(80L)));
    }

    @Test void preservesExistingTextAndFileAndDoesNotBindAnythingWhenThereIsNoSourceFile() {
        var query = query();
        var overview = (tools.jackson.databind.node.ObjectNode) query.content().rootSnapshot().path("overview");
        overview.put("projectDescription", "已填概述"); overview.set("preTopologyFile", JsonUtils.parseTree(file));
        when(checklists.getView(any())).thenReturn(survey(80L, "SUBMITTED"));
        var result = prefill.fill(query);
        assertThat(result.rootSnapshot().path("overview")).isEqualTo(overview);
        verifyNoInteractions(files);
        when(checklists.getView(any())).thenReturn(new CutoverChecklistView(50L, "P4", 4L, 30L, 80L, 3L, 1L,
                "SUBMITTED", "hash", "{}", "{}", "[]", List.of()));
        prefill.fill(query());
        verifyNoInteractions(files);
    }

    @Test void rejectsWrongPredecessorAndPropagatesAttachmentFailureWithoutReturningAnUnboundFile() {
        when(checklists.getView(any())).thenReturn(survey(999L, "SUBMITTED"));
        assertThatThrownBy(() -> prefill.fill(query())).isInstanceOf(CutoverPlanApplicationException.class);
        verifyNoInteractions(files);
        when(checklists.getView(any())).thenReturn(survey(80L, "SUBMITTED"));
        when(files.attachExistingVersions(any())).thenThrow(new SecurityException("not permitted"));
        var query = query();
        assertThatThrownBy(() -> prefill.fill(query)).isInstanceOf(SecurityException.class);
        assertThat(query.content().rootSnapshot().path("overview").path("preTopologyFile").isNull()).isTrue();
    }

    @Test void unavailableOrInvalidatedSurveyDoesNotBlockSavingUserDraftOrSupplyNewFiles() {
        var query = query();
        when(checklists.getView(any())).thenReturn(survey(80L, "INVALIDATED"));
        assertThat(prefill.fill(query)).isSameAs(query.content());
        when(checklists.getView(any())).thenThrow(new cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException(
                cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistException.Code.NOT_FOUND, "unavailable"));
        assertThat(prefill.fill(query)).isSameAs(query.content());
        verifyNoInteractions(files);
    }

    private FileArtifactVersionFact fact(String key) {
        return new FileArtifactVersionFact(901L, 2, key, "CUTOVER_CHECKLIST_MANUAL_EVIDENCE", "拓扑.png", 100L, "image/png",
                "a".repeat(64), "AVAILABLE", "ACTIVE", new FileFactVersion(1, 0, 3), 30L);
    }
    private CutoverChecklistView survey(Long id, String status) {
        return new CutoverChecklistView(50L, "P4", 4L, 30L, id, 3L, 1L, status, "hash", "{}", "{}", "[]", List.of(
                item("SURVEY_CURRENT_TOPOLOGY", "MANUAL", "{\"file\":" + file + "}"),
                item("SURVEY_CUTOVER_BACKGROUND", "DIRECT", "{\"value\":{\"backgroundDescription\":\"替换设备\"}}"),
                item("SURVEY_BUSINESS_SUMMARY", "DIRECT", "{\"value\":\"办公网络\"}")));
    }
    private CutoverChecklistView.Item item(String key, String mode, String answer) {
        return new CutoverChecklistView.Item(81L, key, "BUSINESS_SURVEY", key, "", "FILE", "{}", mode, true, "SYSTEM_MATCHED", true, 1,
                CutoverChecklistView.CurrentResult.builder().resultSourceCode(mode).answerSnapshot(answer).build());
    }
    private CutoverPlanSurveyPrefill.Query query() {
        var source = new CutoverPlanSourcePort.SourceSnapshot(1, 50L, 4L, 60L, 2L, "A", 80L, 3L, 70L, 6, 30L,
                List.of(new CutoverPlanSourcePort.DeviceSnapshot(90L, "SN-1", 1L, null, null)), 401L, "CFG", 1,
                List.of(new CutoverPlanSourcePort.TemplateSectionSnapshot("OPERATION", "操作", 1, List.of("NETWORK_CUTOVER"), List.of("A"), true)), List.of());
        var task = new CutoverTaskDO(); task.setId(50L);
        var plan = new CutoverPlanRevisionDO(); plan.setId(100L);
        var content = new CutoverPlanContentCodec.DecodedContent(JsonUtils.parseTree("{\"editMode\":\"ONLINE_TEMPLATE_STANDARD\",\"overview\":{\"projectDescription\":\"\",\"preTopologyFile\":null,\"postTopologyFile\":null,\"networkConfigurationFile\":null}}"), List.of(), List.of(), null, false);
        return new CutoverPlanSurveyPrefill.Query(new SaveCutoverPlanDraftCommand(1L, 8L, 50L, 4L, 2L, content.rootSnapshot(), "test", "correlation"), task, plan,
                new CutoverPlanSourcePort.SourceFacts(source, List.of()), content);
    }
}
