package cn.iocoder.yudao.module.pms.project.service.projectschedule;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectschedule.vo.StageSuggestionRulePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectschedule.vo.StageSuggestionRuleSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectschedule.StageSuggestionRuleDO;

import java.util.Set;

/**
 * 施工计划建议规则维护（Demo 页面9 / Excel 3.1）。
 * <p>
 * 规则决定 3.1 建议最迟完成的倒排解析；保存时校验来源类型白名单与参照阶段条件，
 * 同一阶段+签约方式的重复行拒绝落库（软删除行不参与重复判定）。停用行不参与推算。
 */
public interface StageSuggestionRuleService {

    Set<String> SOURCE_TYPES = Set.of("PMS_IMPORTED", "DURATION_REQUIRE", "STAGE_PLAN");

    PageResult<StageSuggestionRuleDO> getRulePage(StageSuggestionRulePageReqVO pageReqVO);

    StageSuggestionRuleDO getRule(Long id);

    Long createRule(StageSuggestionRuleSaveReqVO saveReqVO);

    void updateRule(StageSuggestionRuleSaveReqVO saveReqVO);

    void deleteRule(Long id);
}
