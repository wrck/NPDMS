package cn.iocoder.yudao.module.pms.project.service.projectschedule;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectschedule.vo.StageSuggestionRulePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectschedule.vo.StageSuggestionRuleSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectschedule.StageSuggestionRuleDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectschedule.StageSuggestionRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.STAGE_SUGGESTION_RULE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.STAGE_SUGGESTION_RULE_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.STAGE_SUGGESTION_RULE_NOT_EXISTS;

@Service
@Validated
@RequiredArgsConstructor
public class StageSuggestionRuleServiceImpl implements StageSuggestionRuleService {

    private final StageSuggestionRuleMapper ruleMapper;

    @Override
    public PageResult<StageSuggestionRuleDO> getRulePage(StageSuggestionRulePageReqVO pageReqVO) {
        return ruleMapper.selectPage(pageReqVO, new LambdaQueryWrapperX<StageSuggestionRuleDO>()
                .eqIfPresent(StageSuggestionRuleDO::getStageCode, pageReqVO.getStageCode())
                .eqIfPresent(StageSuggestionRuleDO::getSigningMethod, pageReqVO.getSigningMethod())
                .eqIfPresent(StageSuggestionRuleDO::getSourceType, pageReqVO.getSourceType())
                .orderByAsc(StageSuggestionRuleDO::getStageCode).orderByAsc(StageSuggestionRuleDO::getId));
    }

    @Override
    public StageSuggestionRuleDO getRule(Long id) {
        StageSuggestionRuleDO rule = ruleMapper.selectById(id);
        if (rule == null) throw exception(STAGE_SUGGESTION_RULE_NOT_EXISTS);
        return rule;
    }

    @Override
    public Long createRule(StageSuggestionRuleSaveReqVO saveReqVO) {
        validateSave(saveReqVO);
        requireNoDuplicate(saveReqVO.getStageCode(), saveReqVO.getSigningMethod(), null);
        StageSuggestionRuleDO rule = new StageSuggestionRuleDO();
        applySave(rule, saveReqVO);
        ruleMapper.insert(rule);
        return rule.getId();
    }

    @Override
    public void updateRule(StageSuggestionRuleSaveReqVO saveReqVO) {
        if (saveReqVO.getId() == null || ruleMapper.selectById(saveReqVO.getId()) == null)
            throw exception(STAGE_SUGGESTION_RULE_NOT_EXISTS);
        validateSave(saveReqVO);
        requireNoDuplicate(saveReqVO.getStageCode(), saveReqVO.getSigningMethod(), saveReqVO.getId());
        StageSuggestionRuleDO rule = new StageSuggestionRuleDO();
        applySave(rule, saveReqVO);
        ruleMapper.updateById(rule);
    }

    @Override
    public void deleteRule(Long id) {
        if (ruleMapper.selectById(id) == null) throw exception(STAGE_SUGGESTION_RULE_NOT_EXISTS);
        ruleMapper.deleteById(id);
    }

    /** 来源类型白名单与参照阶段条件；启用状态缺省视为启用。 */
    private void validateSave(StageSuggestionRuleSaveReqVO saveReqVO) {
        if (!SOURCE_TYPES.contains(saveReqVO.getSourceType()))
            throw exception(STAGE_SUGGESTION_RULE_INVALID, "建议来源须为 " + String.join("/", SOURCE_TYPES));
        boolean referenceFilled = saveReqVO.getReferenceStageCode() != null && !saveReqVO.getReferenceStageCode().isBlank();
        if ("STAGE_PLAN".equals(saveReqVO.getSourceType()) && !referenceFilled)
            throw exception(STAGE_SUGGESTION_RULE_INVALID, "阶段参照规则必须填写参照阶段编码");
        if (!"STAGE_PLAN".equals(saveReqVO.getSourceType()) && referenceFilled)
            throw exception(STAGE_SUGGESTION_RULE_INVALID, "仅阶段参照规则可填写参照阶段编码");
    }

    /** 同一阶段+签约方式唯一；空签约方式行与任意精确变体行并存（精确行优先）。 */
    private void requireNoDuplicate(String stageCode, String signingMethod, Long excludeId) {
        var wrapper = new LambdaQueryWrapperX<StageSuggestionRuleDO>()
                .eq(StageSuggestionRuleDO::getStageCode, stageCode);
        if (signingMethod == null) wrapper.isNull(StageSuggestionRuleDO::getSigningMethod);
        else wrapper.eq(StageSuggestionRuleDO::getSigningMethod, signingMethod);
        if (excludeId != null) wrapper.ne(StageSuggestionRuleDO::getId, excludeId);
        Long count = ruleMapper.selectCount(wrapper);
        if (count != null && count > 0) throw exception(STAGE_SUGGESTION_RULE_DUPLICATE);
    }

    private void applySave(StageSuggestionRuleDO rule, StageSuggestionRuleSaveReqVO saveReqVO) {
        rule.setId(saveReqVO.getId());
        rule.setStageCode(saveReqVO.getStageCode());
        rule.setSigningMethod(saveReqVO.getSigningMethod());
        rule.setSourceType(saveReqVO.getSourceType());
        rule.setReferenceStageCode(saveReqVO.getReferenceStageCode());
        rule.setOffsetMonths(saveReqVO.getOffsetMonths());
        rule.setOffsetDays(saveReqVO.getOffsetDays());
        rule.setRemark(saveReqVO.getRemark());
        rule.setEnabled(saveReqVO.getEnabled() == null || saveReqVO.getEnabled());
    }
}
