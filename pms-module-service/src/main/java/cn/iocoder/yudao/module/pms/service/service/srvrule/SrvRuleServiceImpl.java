package cn.iocoder.yudao.module.pms.service.service.srvrule;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvrule.vo.SrvRulePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvrule.vo.SrvRuleSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvrule.SrvRuleDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.srvrule.SrvRuleMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_RULE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_RULE_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_RULE_STATUS_INVALID;

/**
 * 巡检规则 Service 实现类
 */
@Service
@Validated
public class SrvRuleServiceImpl implements SrvRuleService {

    /**
     * 规则状态：0草稿
     */
    private static final int STATUS_DRAFT = 0;
    /**
     * 规则状态：1已发布
     */
    private static final int STATUS_PUBLISHED = 1;
    /**
     * 规则状态：2已停用
     */
    private static final int STATUS_DISABLED = 2;

    @Resource
    private SrvRuleMapper srvRuleMapper;

    @Override
    public Long createSrvRule(SrvRuleSaveReqVO createReqVO) {
        // 校验编码唯一
        validateCodeUnique(null, createReqVO.getCode());
        // 插入
        SrvRuleDO rule = BeanUtils.toBean(createReqVO, SrvRuleDO.class);
        if (rule.getStatus() == null) {
            rule.setStatus(STATUS_DRAFT);
        }
        srvRuleMapper.insert(rule);
        return rule.getId();
    }

    @Override
    public void updateSrvRule(SrvRuleSaveReqVO updateReqVO) {
        SrvRuleDO existing = validateSrvRuleExists(updateReqVO.getId());
        // 校验编码唯一
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getCode());
        // 已发布或已停用的规则不允许修改核心字段
        SrvRuleDO updateObj = BeanUtils.toBean(updateReqVO, SrvRuleDO.class);
        // 保持状态不被前端覆盖
        updateObj.setStatus(existing.getStatus());
        srvRuleMapper.updateById(updateObj);
    }

    @Override
    public void deleteSrvRule(Long id) {
        validateSrvRuleExists(id);
        srvRuleMapper.deleteById(id);
    }

    @Override
    public PageResult<SrvRuleDO> getSrvRulePage(SrvRulePageReqVO pageReqVO) {
        return srvRuleMapper.selectPage(pageReqVO);
    }

    @Override
    public SrvRuleDO getSrvRule(Long id) {
        return srvRuleMapper.selectById(id);
    }

    @Override
    public void publishSrvRule(Long id) {
        SrvRuleDO rule = validateSrvRuleExists(id);
        if (!Integer.valueOf(STATUS_DRAFT).equals(rule.getStatus())) {
            throw exception(SRV_RULE_STATUS_INVALID);
        }
        SrvRuleDO updateObj = new SrvRuleDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_PUBLISHED);
        updateObj.setEffectiveTime(LocalDateTime.now());
        srvRuleMapper.updateById(updateObj);
    }

    @Override
    public void disableSrvRule(Long id) {
        SrvRuleDO rule = validateSrvRuleExists(id);
        if (!Integer.valueOf(STATUS_PUBLISHED).equals(rule.getStatus())) {
            throw exception(SRV_RULE_STATUS_INVALID);
        }
        SrvRuleDO updateObj = new SrvRuleDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_DISABLED);
        srvRuleMapper.updateById(updateObj);
    }

    private SrvRuleDO validateSrvRuleExists(Long id) {
        if (id == null) {
            throw exception(SRV_RULE_NOT_EXISTS);
        }
        SrvRuleDO rule = srvRuleMapper.selectById(id);
        if (rule == null) {
            throw exception(SRV_RULE_NOT_EXISTS);
        }
        return rule;
    }

    private void validateCodeUnique(Long id, String code) {
        if (code == null) {
            return;
        }
        SrvRuleDO existing = srvRuleMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(SRV_RULE_CODE_DUPLICATE, code);
        }
    }

}
