package cn.iocoder.yudao.module.pms.engineering.service.delivery;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionMapper;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SolutionDeliveryEvidenceProviderTest {
    private final SolutionMapper mapper=mock(SolutionMapper.class);
    private final SolutionDeliveryEvidenceProvider provider=new SolutionDeliveryEvidenceProvider(mapper);
    private SolutionDO approved() {
        var row=new SolutionDO();row.setId(5L);row.setTenantId(1L);row.setProjectId(7L);
        row.setStatus(3);row.setBaselineVersion(2);when(mapper.selectById(5L)).thenReturn(row);return row;
    }
    @Test void rejectsApprovedSolutionFromDifferentProject() {
        approved();assertThrows(BusinessContractException.class,()->provider.validateCurrent(1L,8L,"5",2L));
    }
    @Test void acceptsExactProjectAndBaseline() {
        approved();assertDoesNotThrow(()->provider.validateCurrent(1L,7L,"5",2L));
    }
    @Test void rejectsDifferentBaseline() {
        approved();assertThrows(BusinessContractException.class,()->provider.validateCurrent(1L,7L,"5",3L));
    }
    @Test void rejectsDifferentTenant() {
        approved();assertThrows(BusinessContractException.class,()->provider.validateCurrent(2L,7L,"5",2L));
    }
}
