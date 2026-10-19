package cn.iocoder.yudao.module.pms.project.service.risk;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.project.controller.admin.risk.vo.ProjectRiskSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.risk.ProjectRiskDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.risk.ProjectRiskMapper;
import cn.iocoder.yudao.module.pms.project.domain.risk.RiskStatusRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectRiskServiceTest {
    @Mock private ProjectMasterMapper projectMapper;
    @Mock private ProjectRiskMapper projectRiskMapper;
    @InjectMocks private ProjectRiskServiceImpl service;

    @Test
    void createsRiskForNewDomainProjectAndInitializesIdentification() {
        when(projectMapper.selectById(101L)).thenReturn(new ProjectMasterDO());
        when(projectRiskMapper.insert(any(ProjectRiskDO.class))).thenAnswer(invocation -> {
            invocation.<ProjectRiskDO>getArgument(0).setId(201L);
            return 1;
        });
        assertEquals(201L, service.createRiskRetired(request()));
        ArgumentCaptor<ProjectRiskDO> saved = ArgumentCaptor.forClass(ProjectRiskDO.class);
        verify(projectRiskMapper).insert(saved.capture());
        assertEquals(101L, saved.getValue().getProjectId());
        assertEquals(RiskStatusRules.IDENTIFIED, saved.getValue().getStatus());
        assertNotNull(saved.getValue().getIdentifiedAt());
    }

    @Test
    void missingProjectReturnsBusinessErrorWithoutWritingRisk() {
        ServiceException error = assertThrows(ServiceException.class, () -> service.createRiskRetired(request()));
        assertEquals(PROJECT_RISK_PROJECT_NOT_EXISTS.getCode(), error.getCode());
        verifyNoInteractions(projectRiskMapper);
    }

    @Test
    void invalidRiskLevelStillFailsBeforeInsert() {
        when(projectMapper.selectById(101L)).thenReturn(new ProjectMasterDO());
        ProjectRiskSaveReqVO request = request();
        request.setRiskLevel("UNKNOWN");
        assertThrows(ServiceException.class, () -> service.createRiskRetired(request));
        verifyNoInteractions(projectRiskMapper);
    }

    @Test
    void changingProjectOrSkippingRiskStateStillFails() {
        ProjectRiskDO existing = new ProjectRiskDO();
        existing.setId(201L);
        existing.setProjectId(101L);
        existing.setStatus(RiskStatusRules.IDENTIFIED);
        when(projectRiskMapper.selectById(201L)).thenReturn(existing);
        ProjectRiskSaveReqVO request = request();
        request.setId(201L);
        request.setProjectId(102L);
        assertThrows(ServiceException.class, () -> service.updateRiskRetired(request));
        request.setProjectId(101L);
        request.setStatus(RiskStatusRules.CLOSED);
        ServiceException error = assertThrows(ServiceException.class, () -> service.updateRiskRetired(request));
        assertEquals(PROJECT_RISK_STATUS_TRANSITION_INVALID.getCode(), error.getCode());
        verify(projectRiskMapper, never()).updateById(any(ProjectRiskDO.class));
    }

    private ProjectRiskSaveReqVO request() {
        ProjectRiskSaveReqVO request = new ProjectRiskSaveReqVO();
        request.setProjectId(101L);
        request.setTitle("新域项目风险");
        request.setRiskLevel("MEDIUM");
        return request;
    }
}
