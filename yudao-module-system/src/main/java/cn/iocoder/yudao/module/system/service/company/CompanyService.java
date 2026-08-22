package cn.iocoder.yudao.module.system.service.company;

import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;

import java.util.Collection;

public interface CompanyService {

    CompanyDO getCompany(Long id);

    CompanyDO getCompanyByCode(String code);

    void validateCompanyList(Collection<Long> ids);

}
