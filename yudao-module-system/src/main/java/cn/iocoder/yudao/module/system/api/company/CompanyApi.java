package cn.iocoder.yudao.module.system.api.company;

import cn.iocoder.yudao.module.system.api.company.dto.CompanyRespDTO;

import java.util.Collection;

public interface CompanyApi {

    CompanyRespDTO getCompany(Long id);

    CompanyRespDTO getCompanyByCode(String code);

    void validateCompanyList(Collection<Long> ids);

}
