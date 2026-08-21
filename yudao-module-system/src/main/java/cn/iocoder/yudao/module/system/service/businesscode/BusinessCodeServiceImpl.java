package cn.iocoder.yudao.module.system.service.businesscode;

import cn.iocoder.yudao.module.system.api.businesscode.dto.BusinessCodeAllocation;
import cn.iocoder.yudao.module.system.dal.dataobject.businesscode.BusinessCodeRuleDO;
import cn.iocoder.yudao.module.system.dal.mysql.businesscode.BusinessCodeRuleMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.BUSINESS_CODE_RULE_UNAVAILABLE;

@Service
public class BusinessCodeServiceImpl implements BusinessCodeService {

    @Resource
    private BusinessCodeRuleMapper businessCodeRuleMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public BusinessCodeAllocation allocate(long tenantId, String ruleCode) {
        List<BusinessCodeRuleDO> rules = businessCodeRuleMapper.selectActiveForUpdate(tenantId, ruleCode);
        if (rules.size() != 1) {
            throw exception(BUSINESS_CODE_RULE_UNAVAILABLE);
        }
        BusinessCodeRuleDO rule = rules.get(0);
        long sequence = rule.getNextValue();
        long nextValue = Math.addExact(sequence, 1);
        if (businessCodeRuleMapper.advance(rule.getId(), sequence, nextValue) != 1) {
            throw exception(BUSINESS_CODE_RULE_UNAVAILABLE);
        }
        String number = String.format(Locale.ROOT, "%0" + rule.getPaddingWidth() + "d", sequence);
        return new BusinessCodeAllocation(rule.getPrefix() + number, rule.getRuleVersion());
    }
}
