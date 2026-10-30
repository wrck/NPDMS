package cn.iocoder.yudao.module.pms.project.dal.mysql.projectschedule.query;

import lombok.Data;

/**
 * 参与推算的有效建议规则列表查询（场景：3.1 推算按签约方式取有效规则）。
 */
@Data
public class StageSuggestionRuleListQuery {

    /** 签约方式；返回该方式的精确行与全部变体行（signing_method 为空）。 */
    private String signingMethod;
}
