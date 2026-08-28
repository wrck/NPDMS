package cn.iocoder.yudao.module.system.api.company;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.company.dto.CompanyRespDTO;
import cn.iocoder.yudao.module.system.service.company.CompanyService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class CompanyApiImpl implements CompanyApi {

    @Resource
    private CompanyService companyService;

    @Override
    public CompanyRespDTO getCompany(Long id) {
        return BeanUtils.toBean(companyService.getCompany(id), CompanyRespDTO.class);
    }

    @Override
    public CompanyRespDTO getCompanyByCode(String code) {
        return BeanUtils.toBean(companyService.getCompanyByCode(code), CompanyRespDTO.class);
    }

    @Override
    public void validateCompanyList(Collection<Long> ids) {
        companyService.validateCompanyList(ids);
    }

}
