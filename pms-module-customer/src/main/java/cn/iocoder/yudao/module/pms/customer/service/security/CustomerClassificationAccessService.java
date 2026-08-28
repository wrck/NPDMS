package cn.iocoder.yudao.module.pms.customer.service.security;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.classification.CustomerMarketRelationDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.classification.CustomerMarketRelationMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_CLASSIFICATION_INVALID;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_SCOPE_DENIED;

@Service
public class CustomerClassificationAccessService {

    @Resource
    private DeptApi deptApi;
    @Resource
    private CustomerMarketRelationMapper marketRelationMapper;

    public CustomerClassificationSnapshot validate(Long tenantId, CustomerClassificationInput input,
                                                   CustomerVisibleScope scope) {
        if (tenantId == null || input == null || scope == null) {
            throw exception(CUSTOMER_CLASSIFICATION_INVALID);
        }
        var department = deptApi.getDeptByCode(input.departmentCode());
        if (department == null || !CommonStatusEnum.ENABLE.getStatus().equals(department.getStatus())) {
            throw exception(CUSTOMER_CLASSIFICATION_INVALID);
        }
        CustomerMarketRelationDO query = new CustomerMarketRelationDO();
        query.setTenantId(tenantId);
        query.setMarketCode(input.marketCode());
        query.setSystemCode(input.systemCode());
        query.setExpendCode(input.expendCode());
        query.setIndustryCode(input.industryCode());
        CustomerMarketRelationDO relation = marketRelationMapper.selectActive(query);
        if (relation == null) {
            throw exception(CUSTOMER_CLASSIFICATION_INVALID);
        }
        if (!scope.all() && scope.slices().stream().noneMatch(slice -> matches(slice, input))) {
            throw exception(CUSTOMER_SCOPE_DENIED);
        }
        return new CustomerClassificationSnapshot(department.getCode(), department.getName(),
                relation.getMarketCode(), relation.getMarketName(), relation.getSystemCode(), relation.getSystemName(),
                relation.getExpendCode(), relation.getExpendName(), relation.getIndustryCode(), relation.getIndustryName());
    }

    private boolean matches(CustomerScopeSlice slice, CustomerClassificationInput input) {
        return matches(slice.departmentCodes(), input.departmentCode())
                && matches(slice.marketCodes(), input.marketCode())
                && matches(slice.systemCodes(), input.systemCode())
                && matches(slice.expendCodes(), input.expendCode())
                && matches(slice.industryCodes(), input.industryCode());
    }

    private boolean matches(java.util.Set<String> allowed, String value) {
        return allowed.isEmpty() || allowed.contains(value);
    }
}
