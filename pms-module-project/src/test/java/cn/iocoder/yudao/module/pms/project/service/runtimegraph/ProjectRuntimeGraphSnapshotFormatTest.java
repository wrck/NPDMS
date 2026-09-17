package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectStageExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectRuntimeGraphSnapshotFormatTest {

    private final ProjectRuntimeGraphMapper graph = mock(ProjectRuntimeGraphMapper.class);
    private final ProjectStageExecutionContractMapper contracts = mock(ProjectStageExecutionContractMapper.class);
    private final ProjectRuntimeGraphFreezer freezer = new ProjectRuntimeGraphFreezer(graph, contracts);

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, 1, 3, Integer.MAX_VALUE})
    void rejectsUnknownTypedVersionBeforeAnyWrite(Integer version) {
        TemplateExecutionSnapshot snapshot = validSnapshot();
        snapshot.setExecutionSchemaVersion(version);

        assertThrows(IllegalArgumentException.class,
                () -> freezer.freeze(1L, 2L, 3L, snapshot, List.of(), LocalDateTime.of(2026, 9, 17, 0, 0)));

        verifyNoInteractions(graph, contracts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"executionSchemaVersion\":null}",
            "{\"executionSchemaVersion\":\"2\"}", "{\"executionSchemaVersion\":2.0}",
            "{\"executionSchemaVersion\":3}", "{\"executionSchemaVersion\":4294967298}"})
    void compatibilityProjectionCannotHideMissingOrUnknownVersion(String json) {
        TemplateDefinitionContent content = new TemplateDefinitionContent();
        content.setExecutionSnapshot(JsonUtils.parseObject(json, JsonNode.class));

        assertThrows(IllegalArgumentException.class,
                () -> freezer.freeze(1L, 2L, 3L, content, List.of(), LocalDateTime.of(2026, 9, 17, 0, 0)));

        verifyNoInteractions(graph, contracts);
    }

    @Test
    void keepsSupportedTypedAndSerializedProjectionValid() {
        TemplateExecutionSnapshot snapshot = validSnapshot();
        TemplateDefinitionContent content = snapshot.toRuntimeContent();

        assertDoesNotThrow(() -> freezer.validate(snapshot));
        assertDoesNotThrow(() -> freezer.validate(content));
        verifyNoInteractions(graph, contracts);
    }

    private TemplateExecutionSnapshot validSnapshot() {
        TemplateExecutionSnapshot snapshot = new TemplateExecutionSnapshot();
        snapshot.setCompilerVersion("historical-compiler");
        var stage = new TemplateExecutionSnapshot.StageContract();
        stage.setNodeKey("stage-a");
        stage.setCode("A");
        stage.setCompletionRule(JsonUtils.parseObject("{}", JsonNode.class));
        snapshot.getStages().add(stage);
        return snapshot;
    }
}
