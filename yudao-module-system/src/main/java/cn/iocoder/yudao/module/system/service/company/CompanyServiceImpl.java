package cn.iocoder.yudao.module.system.service.company;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.module.system.dal.mysql.company.CompanyMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.COMPANY_NOT_ENABLE;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.COMPANY_NOT_FOUND;

@Service
@Validated
public class CompanyServiceImpl implements CompanyService {

    @Resource
    private CompanyMapper companyMapper;

    @Override
    public CompanyDO getCompany(Long id) {
        return companyMapper.selectById(id);
    }

    @Override
    public CompanyDO getCompanyByCode(String code) {
        CompanyDO company = companyMapper.selectByCode(code);
        validateCompany(company);
        return company;
    }

    @Override
    public void validateCompanyList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        Map<Long, CompanyDO> companyMap = convertMap(companyMapper.selectByIds(ids), CompanyDO::getId);
        ids.forEach(id -> validateCompany(companyMap.get(id)));
    }

    private void validateCompany(CompanyDO company) {
        if (company == null) {
            throw exception(COMPANY_NOT_FOUND);
        }
        if (!CommonStatusEnum.ENABLE.getStatus().equals(company.getStatus())) {
            throw exception(COMPANY_NOT_ENABLE, company.getName());
        }
    }

}
