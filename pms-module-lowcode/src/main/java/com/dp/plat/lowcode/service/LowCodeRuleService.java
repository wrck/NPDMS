package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.entity.LowCodeConfigVersion;
import com.dp.plat.lowcode.entity.LowCodeRule;

import java.util.List;
import java.util.Map;

/**
 * 低代码规则服务。
 */
public interface LowCodeRuleService extends IService<LowCodeRule> {

    /**
     * 执行规则。
     *
     * @param code  规则编码
     * @param facts 输入事实
     * @return 执行结果（含 actions 或 result）
     */
    Map<String, Object> execute(String code, Map<String, Object> facts);

    /**
     * 发布规则并生成版本快照。
     *
     * @param ruleId 规则 ID
     * @return 生成的版本快照
     */
    LowCodeConfigVersion publishWithVersion(Long ruleId);

    /**
     * 查询规则版本历史。
     *
     * @param ruleId 规则 ID
     * @return 版本快照列表（按版本号倒序）
     */
    List<LowCodeConfigVersion> listRuleVersions(Long ruleId);

    /**
     * 回滚规则到指定版本。
     *
     * @param ruleId        规则 ID
     * @param targetVersion 目标版本号
     */
    void rollbackRule(Long ruleId, Integer targetVersion);
}
