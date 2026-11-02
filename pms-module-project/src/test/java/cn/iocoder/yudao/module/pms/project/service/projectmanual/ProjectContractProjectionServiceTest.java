package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectContractQueryApi;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.projection.ProjectContractProjectionRow;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectContractProjectionUpdate;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectContractProjectionServiceTest {
    private final ProjectMasterMapper mapper = mock(ProjectMasterMapper.class);
    private final ProjectContractQueryApi contracts = mock(ProjectContractQueryApi.class);
    private final ProjectContractProjectionService service = new ProjectContractProjectionService(mapper, contracts);

    @Test
    void updatesCurrentNumbersAndClearsExpiredButPreservesNeverRelatedManualValues() {
        var rows = List.of(row(1L, "OLD"), row(2L, "MANUAL"), row(3L, "EXPIRED"), row(4L, "B,A"));
        when(contracts.getProjectContractFacts(any())).thenReturn(List.of(
                fact(1L,true,"B"), fact(1L,true,"A"), fact(1L,true,"A"),
                fact(3L,false,"EXPIRED"), fact(4L,true,"A"), fact(4L,true,"B")));
        when(mapper.updateContractProjectionIfMatch(any())).thenReturn(1);
        var result = service.reconcilePage(1L, LocalDateTime.now(), rows);
        assertEquals(3, result.updated());
        assertEquals(0, result.failed());
        verify(mapper).updateContractProjectionIfMatch(new ProjectContractProjectionUpdate(1L,1L,1L,"OLD","A,B"));
        verify(mapper).updateContractProjectionIfMatch(new ProjectContractProjectionUpdate(1L,3L,1L,"EXPIRED",null));
        verify(mapper).updateContractProjectionIfMatch(new ProjectContractProjectionUpdate(1L,4L,1L,"B,A","A,B"));
        verify(mapper, times(3)).updateContractProjectionIfMatch(any());
    }

    @Test
    void invalidActiveNumberAndConcurrentUpdateDoNotOverwriteAndDoNotBlockOtherProjects() {
        when(contracts.getProjectContractFacts(any())).thenReturn(List.of(fact(1L,true,null), fact(2L,true,"A"), fact(3L,true,"B")));
        when(mapper.updateContractProjectionIfMatch(any())).thenReturn(0,1);
        var result = service.reconcilePage(1L, LocalDateTime.now(), List.of(row(1L,"OLD"), row(2L,"OLD"), row(3L,"OLD")));
        assertEquals(1,result.updated()); assertEquals(2,result.failed());
        verify(mapper, never()).updateContractProjectionIfMatch(new ProjectContractProjectionUpdate(1L,1L,1L,"OLD",null));
    }

    @Test
    void doesNotTruncateContractListLongerThanPreviousColumnLimit() {
        String a = "A".repeat(130), b = "B".repeat(130);
        when(contracts.getProjectContractFacts(any())).thenReturn(List.of(fact(1L,true,b), fact(1L,true,a)));
        when(mapper.updateContractProjectionIfMatch(any())).thenReturn(1);
        var result = service.reconcilePage(1L, LocalDateTime.now(), List.of(row(1L,"OLD")));
        assertEquals(1,result.updated());
        verify(mapper).updateContractProjectionIfMatch(new ProjectContractProjectionUpdate(1L,1L,1L,"OLD",a + "," + b));
    }

    private static ProjectContractProjectionRow row(Long id, String no) {
        var row = new ProjectContractProjectionRow(); row.setId(id); row.setVersion(1L); row.setContractNo(no); return row;
    }
    private static ProjectContractQueryApi.ProjectContractFact fact(Long id, boolean current, String no) {
        return new ProjectContractQueryApi.ProjectContractFact(id,current,no);
    }
}
