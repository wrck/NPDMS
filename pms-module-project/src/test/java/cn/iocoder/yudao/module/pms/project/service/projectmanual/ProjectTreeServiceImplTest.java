package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTreeServiceImplTest {

    @Mock
    private ProjectMasterMapper projectMasterMapper;
    @InjectMocks
    private ProjectTreeServiceImpl service;

    @Test
    void updateChildWeightsUpdatesCompleteValidSet() {
        when(projectMasterMapper.selectById(1L)).thenReturn(project(1L));
        when(projectMasterMapper.selectChildren(1L)).thenReturn(List.of(project(2L), project(3L)));

        service.updateChildWeights(1L, Map.of(2L, new BigDecimal("60.00"), 3L, new BigDecimal("40.00")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProjectMasterDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(projectMasterMapper).updateById(captor.capture());
        assertEquals(List.of(2L, 3L), captor.getValue().stream().map(ProjectMasterDO::getId).toList());
        assertEquals(List.of(new BigDecimal("60.00"), new BigDecimal("40.00")),
                captor.getValue().stream().map(ProjectMasterDO::getAggregationWeight).toList());
        assertEquals(List.of("MANUAL", "MANUAL"),
                captor.getValue().stream().map(ProjectMasterDO::getWeightSource).toList());
    }

    @Test
    void updateChildWeightsRejectsPartialSetBeforeWriting() {
        when(projectMasterMapper.selectById(1L)).thenReturn(project(1L));
        when(projectMasterMapper.selectChildren(1L)).thenReturn(List.of(project(2L), project(3L)));

        assertThrows(ServiceException.class,
                () -> service.updateChildWeights(1L, Map.of(2L, new BigDecimal("100.00"))));

        verify(projectMasterMapper, never()).updateById(any(List.class));
    }

    @Test
    void updateChildWeightsRejectsInvalidSumBeforeWriting() {
        when(projectMasterMapper.selectById(1L)).thenReturn(project(1L));
        when(projectMasterMapper.selectChildren(1L)).thenReturn(List.of(project(2L), project(3L)));

        assertThrows(ServiceException.class,
                () -> service.updateChildWeights(1L,
                        Map.of(2L, new BigDecimal("50.00"), 3L, new BigDecimal("40.00"))));

        verify(projectMasterMapper, never()).updateById(any(List.class));
    }

    private static ProjectMasterDO project(Long id) {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(id);
        return project;
    }
}
