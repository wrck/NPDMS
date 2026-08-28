package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionBatchCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionBatchDTO;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionTaskCreateItem;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionBatchDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionBatchMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionTaskServiceTest {

    @Mock CollectionBatchMapper batchMapper;
    @Mock CollectionTaskMapper taskMapper;
    @Mock PlatformCommandExecutionApi commandExecutionApi;

    private CollectionTaskService service;

    @BeforeEach
    void setUp() {
        service = new CollectionTaskService(batchMapper, taskMapper, commandExecutionApi);
    }

    @Test
    void validBatchCreatesOneBatchAndOneTaskPerDevice() {
        executeOperationsImmediately();
        when(batchMapper.insert(any(CollectionBatchDO.class))).thenAnswer(invocation -> {
            invocation.<CollectionBatchDO>getArgument(0).setId(7001L);
            return 1;
        });
        when(taskMapper.insert(any(CollectionTaskDO.class))).thenReturn(1);

        CollectionBatchDTO created = service.createBatch(command(List.of(item("device-1", "task-key-1"),
                item("device-2", "task-key-2"))));

        assertEquals(7001L, created.id());
        assertEquals(2, created.tasks().size());
        assertEquals(List.of("device-1", "device-2"),
                created.tasks().stream().map(task -> task.deviceId()).toList());
        verify(batchMapper).insert(any(CollectionBatchDO.class));
        verify(taskMapper, org.mockito.Mockito.times(2)).insert(any(CollectionTaskDO.class));
    }

    @Test
    void invalidDeviceRejectsWholeBatchBeforePersistence() {
        CollectionTaskCreateItem invalid = new CollectionTaskCreateItem(
                "", "Device", "10.0.0.1", 22, "SSH", "template-1", "v1", "a".repeat(64),
                "SAVED_CREDENTIAL", 9L, 10L, "task-key-1", "IMP", "ConfigurationCollectionResult", "result-1");

        assertThrows(IllegalArgumentException.class, () -> service.createBatch(command(List.of(invalid))));

        verify(batchMapper, never()).insert(any(CollectionBatchDO.class));
        verify(taskMapper, never()).insert(any(CollectionTaskDO.class));
    }

    @SuppressWarnings("unchecked")
    private void executeOperationsImmediately() {
        when(commandExecutionApi.execute(any(), anyString(), eq(CollectionBatchDTO.class), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<CollectionBatchDTO> operation = invocation.getArgument(3);
                    return new PlatformCommandExecutionApi.ExecutionResult<>(
                            PlatformCommandExecutionApi.Decision.NEW, operation.get());
                });
    }

    private CollectionBatchCreateCommand command(List<CollectionTaskCreateItem> tasks) {
        return new CollectionBatchCreateCommand(
                0L, 7L, "batch-key-1", "b".repeat(64), "IMP", "ConfigurationCollectionResult",
                "result-1", "project-1", "BUSINESS_CONSUMPTION", tasks);
    }

    private CollectionTaskCreateItem item(String deviceId, String taskKey) {
        return new CollectionTaskCreateItem(
                deviceId, "Device " + deviceId, "10.0.0.1", 22, "SSH", "template-1", "v1",
                "a".repeat(64), "SAVED_CREDENTIAL", 9L, 10L, taskKey,
                "IMP", "ConfigurationCollectionResult", "result-1");
    }
}
