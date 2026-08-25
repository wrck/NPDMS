package cn.iocoder.yudao.module.system.service.company;

import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.controller.admin.company.vo.CompanyPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.company.vo.CompanySaveReqVO;

import java.util.Collection;
import java.util.List;

public interface CompanyService {

    CompanyDO getCompany(Long id);

    CompanyDO getCompanyByCode(String code);

    void validateCompanyList(Collection<Long> ids);

    PageResult<CompanyDO> getCompanyPage(CompanyPageReqVO reqVO);

    List<CompanyDO> getEnabledCompanyList();

    Long createCompany(CompanySaveReqVO reqVO);

    void updateCompany(CompanySaveReqVO reqVO);

}
