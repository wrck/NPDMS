package cn.iocoder.yudao.module.pms.bindings.backend;

import cn.iocoder.yudao.module.pms.bindings.dal.dataobject.InlineInstanceDO;
import cn.iocoder.yudao.module.pms.bindings.dal.mysql.InlineInstanceMapper;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.definition.ProcessDefinitionSnapshot;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.BusinessFactPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.BusinessResultFormationPort;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InlineSyncBackendTest {
    @Mock InlineInstanceMapper instances;
    @Mock BusinessFactPort facts;
    @Mock BusinessResultFormationPort results;
    @InjectMocks InlineSyncBackend backend;

    @Test void replayCannotChangeTheObject() {
        when(instances.selectByIdempotencyKey("same-key")).thenReturn(Optional.of(existing()));
        assertEquals("IDEMPOTENCY_DIGEST_CONFLICT", assertThrows(BusinessContractException.class,
                () -> backend.run(command("definition-a", 10L))).getErrorCode());
        verifyNoInteractions(facts, results);
    }

    @Test void replayCannotChangeTheDefinition() {
        when(instances.selectByIdempotencyKey("same-key")).thenReturn(Optional.of(existing()));
        assertEquals("IDEMPOTENCY_DIGEST_CONFLICT", assertThrows(BusinessContractException.class,
                () -> backend.run(command("definition-b", 9L))).getErrorCode());
        verifyNoInteractions(facts, results);
    }

    @Test void sameRequestReplaysTheOriginalResult() {
        when(instances.selectByIdempotencyKey("same-key")).thenReturn(Optional.of(existing()));
        var replay = backend.run(command("definition-a", 9L));
        assertTrue(replay.replay()); assertEquals("result-original", replay.resultId());
        verifyNoInteractions(facts, results);
    }

    private static InlineInstanceDO existing() {
        var row = new InlineInstanceDO(); row.setTenantId(7L);
        row.setDefinitionCode("definition-a"); row.setDefinitionVersion(1);
        row.setOwnerModule("PRJ"); row.setEntityType("project"); row.setEntityId(9L);
        row.setVerdict("SATISFIED"); row.setResultId("result-original");
        return row;
    }

    private static InlineSyncBackend.RunCommand command(String code, Long id) {
        return new InlineSyncBackend.RunCommand(definition(code), new EntityRef(7L, "PRJ", "project", id), "same-key");
    }

    private static ProcessDefinitionSnapshot definition(String code) {
        return new ProcessDefinitionSnapshot(code, 1, "test", "PRJ", "project", "PRJ/project", 1,
                "evaluate", 1, "FIELD_CONDITION", "1", List.of(), "ready");
    }
}
