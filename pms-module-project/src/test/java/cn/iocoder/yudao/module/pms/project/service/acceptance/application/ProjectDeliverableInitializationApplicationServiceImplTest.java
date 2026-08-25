package cn.iocoder.yudao.module.pms.project.service.acceptance.application;

import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AccProjectDeliverableMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectDeliverableInitializationApplicationServiceImplTest {

    @Mock
    private AccProjectDeliverableMapper mapper;

    @InjectMocks
    private ProjectDeliverableInitializationApplicationServiceImpl service;

    @Test
    void implementationRequiresExistingTransaction() throws Exception {
        Method method = ProjectDeliverableInitializationApplicationServiceImpl.class.getMethod(
                "initialize", ProjectDeliverableInitializationApplicationService.InitializeProjectDeliverablesCommand.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertEquals(Propagation.MANDATORY, transactional.propagation());
    }

    @Test
    void failedBatchInsertIsRejected() {
        when(mapper.insertBatch(anyCollection())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.initialize(commandWithTwoDeliverables()));
    }

    @Test
    void partialInitializationIsRejected() {
        when(mapper.insertBatch(anyCollection())).thenReturn(true);
        when(mapper.selectCountByProjectId(100L)).thenReturn(1L);

        assertThrows(IllegalStateException.class, () -> service.initialize(commandWithTwoDeliverables()));
    }

    private ProjectDeliverableInitializationApplicationService.InitializeProjectDeliverablesCommand
    commandWithTwoDeliverables() {
        var first = new ProjectDeliverableInitializationApplicationService.DeliverableDefinition(
                "D-001", "项目计划", "S0", "T-001", true, 11L);
        var second = new ProjectDeliverableInitializationApplicationService.DeliverableDefinition(
                "D-002", "启动纪要", "S0", null, true, 12L);
        return new ProjectDeliverableInitializationApplicationService.InitializeProjectDeliverablesCommand(
                100L, 200L, List.of(first, second));
    }
}
