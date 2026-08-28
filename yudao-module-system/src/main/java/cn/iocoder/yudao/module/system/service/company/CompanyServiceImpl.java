package cn.iocoder.yudao.module.system.service.company;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.controller.admin.company.vo.CompanyPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.company.vo.CompanySaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.module.system.dal.mysql.company.CompanyMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

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

    @Override
    public PageResult<CompanyDO> getCompanyPage(CompanyPageReqVO reqVO) {
        return companyMapper.selectPage(reqVO);
    }

    @Override
    public List<CompanyDO> getEnabledCompanyList() {
        return companyMapper.selectEnabledList();
    }

    @Override
    public Long createCompany(CompanySaveReqVO reqVO) {
        validateStatus(reqVO.getStatus());
        validateCodeUnique(null, reqVO.getCode());
        CompanyDO company = BeanUtils.toBean(reqVO, CompanyDO.class);
        company.setVersion(0);
        companyMapper.insert(company);
        return company.getId();
    }

    @Override
    public void updateCompany(CompanySaveReqVO reqVO) {
        validateStatus(reqVO.getStatus());
        CompanyDO existing = companyMapper.selectById(reqVO.getId());
        if (existing == null) {
            throw exception(COMPANY_NOT_FOUND);
        }
        if (!Objects.equals(existing.getVersion(), reqVO.getExpectedVersion())) {
            throw exception(COMPANY_VERSION_CONFLICT);
        }
        validateCodeUnique(existing.getId(), reqVO.getCode());
        CompanyDO update = BeanUtils.toBean(reqVO, CompanyDO.class);
        if (companyMapper.updateByIdAndVersion(update, existing.getVersion()) == 0) {
            throw exception(COMPANY_VERSION_CONFLICT);
        }
    }

    private void validateCodeUnique(Long id, String code) {
        CompanyDO company = companyMapper.selectByCode(code);
        if (company != null && !Objects.equals(company.getId(), id)) {
            throw exception(COMPANY_CODE_DUPLICATE);
        }
    }

    private void validateStatus(Integer status) {
        if (!CommonStatusEnum.isEnable(status) && !CommonStatusEnum.isDisable(status)) {
            throw exception(COMPANY_STATUS_INVALID);
        }
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
