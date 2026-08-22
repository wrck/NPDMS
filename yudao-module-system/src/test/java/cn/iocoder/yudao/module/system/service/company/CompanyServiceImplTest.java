package cn.iocoder.yudao.module.system.service.company;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.module.system.dal.mysql.company.CompanyMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.COMPANY_NOT_ENABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(CompanyServiceImpl.class)
class CompanyServiceImplTest extends BaseDbUnitTest {

    @Resource
    private CompanyServiceImpl companyService;
    @Resource
    private CompanyMapper companyMapper;

    @Test
    void getCompanyByCode_returnsEnabledCompany() {
        CompanyDO company = company("COMPANY-A", CommonStatusEnum.ENABLE.getStatus());
        companyMapper.insert(company);

        CompanyDO result = companyService.getCompanyByCode("COMPANY-A");

        assertEquals(company.getId(), result.getId());
        assertEquals("COMPANY-A", result.getCode());
        companyService.validateCompanyList(List.of(company.getId()));
    }

    @Test
    void getCompanyByCode_rejectsDisabledCompany() {
        companyMapper.insert(company("DISABLED", CommonStatusEnum.DISABLE.getStatus()));

        assertServiceException(() -> companyService.getCompanyByCode("DISABLED"), COMPANY_NOT_ENABLE,
                "DISABLED 公司");
    }

    private static CompanyDO company(String code, Integer status) {
        return new CompanyDO()
                .setCode(code)
                .setName(code + " 公司")
                .setStatus(status)
                .setVersion(0);
    }

}
