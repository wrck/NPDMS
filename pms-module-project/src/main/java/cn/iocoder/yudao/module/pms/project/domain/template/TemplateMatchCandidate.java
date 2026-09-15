package cn.iocoder.yudao.module.pms.project.domain.template;

import lombok.Data;

/**
 * 生效模板的最新已发布版本候选。
 */
@Data
public class TemplateMatchCandidate {

    /** 模板ID */
    private Long templateId;
    /** 模板编码 */
    private String code;
    /** 模板名称 */
    private String name;
    /** 匹配优先级（数值小者先命中） */
    private Integer matchPriority;
    /** 最新已发布版本号（F-PM01 表单选择时展示概要） */
    private Integer latestRevisionNo;
    /** 最新已发布版本稳定ID（正式创建按此ID冻结，不在提交时重新选择latest） */
    private Long templateRevisionId;
    /** 当前发布版本的适用条件名称；V2 不再用四维列表示可执行规则。 */
    private String ruleName;
    /** 匹配条件：签约方式（null=不限） */
    private String signingMethod;
    /** 匹配条件：项目类别（null=不限） */
    private String projectCategory;
    /** 匹配条件：实施方式（null=不限） */
    private String implementationMethod;
    /** 匹配条件：重大项目级别（null=不限） */
    private String majorProjectLevel;
}
