package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.controller.admin.sync.DirectOrganizationTemplateController;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrganizationDualTrackEntryTest {
    @Test void originalAndDirectEntriesReturnIndependentDefinitionsWithoutSavingTasks() {
        var connections = mock(SyncConnectionService.class);
        var controller = new DirectOrganizationTemplateController(connections);
        var original = controller.originalTemplate(18L).getData();
        String frozen = JsonUtils.toJsonString(original);
        var direct = controller.template(18L).getData();
        assertEquals("DPPMS_ORGANIZATION",original.adapter());
        assertEquals("DPPMS_ORGANIZATION_DIRECT",direct.adapter());
        assertNotEquals(SyncDefinitionValidator.taskIdentity(original),SyncDefinitionValidator.taskIdentity(direct));
        assertEquals(original.connectionId(),direct.connectionId());
        assertEquals(original.sources().stream().map(SyncDefinition.Source::object).toList(),direct.sources().stream().map(SyncDefinition.Source::object).toList());
        direct.sources().getFirst().sql("changed only in new task draft");
        assertEquals(frozen,JsonUtils.toJsonString(original));
        assertEquals(frozen,JsonUtils.toJsonString(controller.originalTemplate(18L).getData()));
        assertEquals("EHR_ORGANIZATION",EhrSyncTemplate.create(18L).adapter());
        verify(connections,times(3)).required(18L);
        verifyNoMoreInteractions(connections);
    }
    @Test void bothEntriesRejectAnUnavailableConnectionBeforeProducingConfiguration() {
        var connections = mock(SyncConnectionService.class);
        when(connections.required(18L)).thenThrow(new IllegalArgumentException("连接不属于当前租户"));
        var controller = new DirectOrganizationTemplateController(connections);
        assertThrows(IllegalArgumentException.class,() -> controller.originalTemplate(18L));
        assertThrows(IllegalArgumentException.class,() -> controller.template(18L));
    }
}
