package cn.iocoder.yudao.module.pms.service.service.srvrule;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvrule.vo.SrvRulePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvrule.vo.SrvRuleSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvrule.SrvRuleRetiredDO;
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
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Service
@Validated
@Deprecated
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
    public Long createSrvRuleRetired(SrvRuleSaveReqVO createReqVO) {
        // 校验编码唯一
        validateCodeUnique(null, createReqVO.getCode());
        // 插入
        SrvRuleRetiredDO rule = BeanUtils.toBean(createReqVO, SrvRuleRetiredDO.class);
        if (rule.getStatus() == null) {
            rule.setStatus(STATUS_DRAFT);
        }
        srvRuleMapper.insert(rule);
        return rule.getId();
    }

    @Override
    public void updateSrvRuleRetired(SrvRuleSaveReqVO updateReqVO) {
        SrvRuleRetiredDO existing = validateSrvRuleExists(updateReqVO.getId());
        // 校验编码唯一
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getCode());
        // 已发布或已停用的规则不允许修改核心字段
        SrvRuleRetiredDO updateObj = BeanUtils.toBean(updateReqVO, SrvRuleRetiredDO.class);
        // 保持状态不被前端覆盖
        updateObj.setStatus(existing.getStatus());
        srvRuleMapper.updateById(updateObj);
    }

    @Override
    public void deleteSrvRuleRetired(Long id) {
        validateSrvRuleExists(id);
        srvRuleMapper.deleteById(id);
    }

    @Override
    public PageResult<SrvRuleRetiredDO> getSrvRulePageRetired(SrvRulePageReqVO pageReqVO) {
        return srvRuleMapper.selectPageRetired(pageReqVO);
    }

    @Override
    public SrvRuleRetiredDO getSrvRuleRetired(Long id) {
        return srvRuleMapper.selectById(id);
    }

    @Override
    public void publishSrvRuleRetired(Long id) {
        SrvRuleRetiredDO rule = validateSrvRuleExists(id);
        if (!Integer.valueOf(STATUS_DRAFT).equals(rule.getStatus())) {
            throw exception(SRV_RULE_STATUS_INVALID);
        }
        SrvRuleRetiredDO updateObj = new SrvRuleRetiredDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_PUBLISHED);
        updateObj.setEffectiveTime(LocalDateTime.now());
        srvRuleMapper.updateById(updateObj);
    }

    @Override
    public void disableSrvRuleRetired(Long id) {
        SrvRuleRetiredDO rule = validateSrvRuleExists(id);
        if (!Integer.valueOf(STATUS_PUBLISHED).equals(rule.getStatus())) {
            throw exception(SRV_RULE_STATUS_INVALID);
        }
        SrvRuleRetiredDO updateObj = new SrvRuleRetiredDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_DISABLED);
        srvRuleMapper.updateById(updateObj);
    }

    private SrvRuleRetiredDO validateSrvRuleExists(Long id) {
        if (id == null) {
            throw exception(SRV_RULE_NOT_EXISTS);
        }
        SrvRuleRetiredDO rule = srvRuleMapper.selectById(id);
        if (rule == null) {
            throw exception(SRV_RULE_NOT_EXISTS);
        }
        return rule;
    }

    private void validateCodeUnique(Long id, String code) {
        if (code == null) {
            return;
        }
        SrvRuleRetiredDO existing = srvRuleMapper.selectByCodeRetired(code);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(SRV_RULE_CODE_DUPLICATE, code);
        }
    }

}
