package cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.deliveryconfiguration.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration.*;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PM-03: state/version/tenant protection and copy identity; database atomicity is verified separately. */
class DeliveryDefinitionServiceTest {
    DeliveryDefinitionRevisionMapper revisions = mock(DeliveryDefinitionRevisionMapper.class);
    DeliveryDefinitionReferenceMapper references = mock(DeliveryDefinitionReferenceMapper.class);
    DeliveryConfigurationCommands commands = mock(DeliveryConfigurationCommands.class);
    DeliveryDefinitionResolver resolver = mock(DeliveryDefinitionResolver.class);
    DeliveryDefinitionService service = new DeliveryDefinitionService(revisions, references, commands, resolver);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        when(commands.execute(anyString(), anyString(), any(), eq(Long.class), any())).thenAnswer(call -> {
            Supplier<Long> operation = call.getArgument(4); return operation.get();
        });
        when(references.selectReferences(any())).thenReturn(List.of());
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void creationAssignsApplicationIdsBeforeMapperWrites() {
        when(revisions.lockIdentity(any())).thenReturn(List.of());
        when(revisions.insert(any(DeliveryDefinitionRevisionDO.class))).thenAnswer(call -> {
            DeliveryDefinitionRevisionDO row = call.getArgument(0);
            assertNotNull(row.getId()); assertTrue(row.getId() > 0);
            return 1;
        });
        when(references.insert(any(DeliveryDefinitionReferenceDO.class))).thenAnswer(call -> {
            DeliveryDefinitionReferenceDO row = call.getArgument(0);
            assertNotNull(row.getId()); assertTrue(row.getId() > 0);
            assertNotNull(row.getOwnerRevisionId()); return 1;
        });
        var task = new Save(DeliveryDefinitionKind.TASK, "TASK_WITH_REFERENCES", 1,
                JsonUtils.parseTree("{\"name\":\"需求分析\",\"workBinding\":\"work\",\"permissionPolicy\":\"permission\",\"completionRule\":\"completion\"}"),
                List.of(new DeliveryDefinitionReference("work", 1L), new DeliveryDefinitionReference("permission", 2L),
                        new DeliveryDefinitionReference("completion", 3L)));
        assertTrue(service.create(task, "create") > 0);
        verify(references, times(3)).insert(any(DeliveryDefinitionReferenceDO.class));
    }

    @Test void publishedPayloadCannotBeUpdated() {
        var row = revision("PUBLISHED"); existing(row);
        assertThrows(ServiceException.class, () -> service.update(10L, 2, body(), "key"));
        verify(revisions, never()).replaceDraft(any()); verifyNoInteractions(resolver);
    }
    @Test void staleVersionCannotPublishOrDisable() {
        existing(revision("DRAFT"));
        assertThrows(ServiceException.class, () -> service.publish(10L, 1, "key"));
        assertThrows(ServiceException.class, () -> service.disable(10L, 1, "other"));
        verify(revisions, never()).publish(any()); verify(revisions, never()).disable(any());
    }
    @Test void crossTenantExactLookupFailsClosed() {
        var row = revision("PUBLISHED"); row.setTenantId(8L); when(revisions.selectById(10L)).thenReturn(row);
        assertThrows(ServiceException.class, () -> service.get(10L));
        verify(references, never()).selectReferences(any());
    }
    @Test void copyAllocatesNextRevisionWithoutMutatingPublishedSource() {
        var source = revision("PUBLISHED"); existing(source);
        when(revisions.insert(any(DeliveryDefinitionRevisionDO.class))).thenAnswer(call -> {
            DeliveryDefinitionRevisionDO row = call.getArgument(0); row.setId(11L);
            assertEquals("DRAFT", row.getRevisionState()); assertEquals(4L, row.getRevisionNo());
            assertEquals("RULE", row.getDefinitionCode()); assertNull(row.getPublishedAt()); return 1;
        });
        assertEquals(11L, service.copy(10L, 2, "copy"));
        assertEquals("PUBLISHED", source.getRevisionState()); assertEquals(3L, source.getRevisionNo());
        verify(revisions, never()).replaceDraft(any());
    }
    @Test void existingDraftRejectsCopyAndPublishFailureDoesNotWrite() {
        var source = revision("PUBLISHED"); existing(source);
        var draft = revision("DRAFT"); draft.setId(11L);
        when(revisions.lockIdentity(any())).thenReturn(List.of(source, draft));
        assertThrows(ServiceException.class, () -> service.copy(10L, 2, "copy"));
        existing(draft); when(revisions.selectById(11L)).thenReturn(draft);
        doThrow(new IllegalArgumentException("missing Provider")).when(resolver).resolveDefinition(any(), eq(true));
        assertThrows(IllegalArgumentException.class, () -> service.publish(11L, 2, "publish"));
        verify(revisions, never()).publish(any());
    }
    @Test void disableOnlyUpdatesFlagAndHistoricalReadRemainsAvailable() {
        var row = revision("PUBLISHED"); existing(row); when(revisions.disable(any())).thenReturn(1);
        assertEquals(10L, service.disable(10L, 2, "disable"));
        assertNotNull(row.getDisabledAt()); assertEquals(body().payload().toString(), row.getPayload());
        assertEquals("PUBLISHED", service.get(10L).revisionState());
        verify(references, never()).deleteDraftReferences(any());
    }
    private void existing(DeliveryDefinitionRevisionDO row) {
        when(revisions.selectById(row.getId())).thenReturn(row); when(revisions.lockIdentity(any())).thenReturn(List.of(row));
    }
    private DeliveryDefinitionRevisionDO revision(String state) {
        var row = new DeliveryDefinitionRevisionDO(); row.setId(10L); row.setTenantId(7L); row.setVersion(2);
        row.setDefinitionKind("COMPLETION_RULE"); row.setDefinitionCode("RULE"); row.setRevisionNo(3L);
        row.setRevisionState(state); row.setSchemaVersion(1); row.setPayload(body().payload().toString());
        if ("PUBLISHED".equals(state)) row.setPublishedAt(LocalDateTime.of(2026,9,8,0,0)); return row;
    }
    private Save body() { return new Save(DeliveryDefinitionKind.COMPLETION_RULE, "RULE", 1,
            JsonUtils.parseObject("{\"predicate\":\"TASK_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}}", JsonNode.class), List.of()); }
}
