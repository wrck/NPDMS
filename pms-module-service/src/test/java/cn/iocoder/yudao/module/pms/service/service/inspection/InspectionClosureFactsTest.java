package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class InspectionClosureFactsTest {
    @Test void authenticZeroRuleUmcReportNeedsNoInventedAnnotationsButMissingInputsStayIncomplete() {
        var reports=mock(InspectionReportService.class);var links=mock(InspectionReportResultMapper.class);
        var results=mock(InspectionDetectionResultMapper.class);var annotations=mock(InspectionAnnotationMapper.class);
        var issues=mock(ServiceIssueMapper.class);var facts=new InspectionClosureFacts(reports,links,results,annotations,issues);
        var task=new InspectionTaskDO();task.setId(10L);task.setTenantId(7L);task.setCurrentReportId(70L);
        when(links.selectForTask(any())).thenReturn(List.of());when(results.selectForTask(any())).thenReturn(List.of());
        when(annotations.selectForTask(any())).thenReturn(List.of());when(issues.selectForTaskForUpdate(any())).thenReturn(List.of());
        when(reports.available(any())).thenReturn(true);when(reports.hasEmptyUmcResults(any())).thenReturn(true);
        var query=new InspectionClosureFacts.Query(null,task);
        assertTrue(facts.current(query).annotationsComplete());
        when(reports.hasEmptyUmcResults(any())).thenReturn(false);
        assertFalse(facts.current(query).annotationsComplete());
        when(reports.hasEmptyUmcResults(any())).thenReturn(true);when(reports.available(any())).thenReturn(false);
        assertFalse(facts.current(query).annotationsComplete());
    }
}
