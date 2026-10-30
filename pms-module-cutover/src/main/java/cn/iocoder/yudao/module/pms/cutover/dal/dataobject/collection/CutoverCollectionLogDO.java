package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.collection;

import com.baomidou.mybatisplus.annotation.TableField;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 不可变的采集回传证据，晚到结果也不改写已提交清单或归档闭环。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cut_collection_log")
public class CutoverCollectionLogDO extends BaseBusinessEntity {
    private Long taskId;
    @TableField(value = "entry_code")
    private String entry;
    private Long objectId;
    private Long executionId;
    private String platformTaskId;
    private Long resultVersion;
    private Long fileVersionId;
    private Long deviceId;
    @TableField(exist = false)
    private String resultSnapshot;


    /** 回执声明的业务上下文；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String sourceContextCode;
    /** 回执声明的业务对象类型；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String sourceObjectTypeCode;
    /** 回执项目；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long projectId;
    /** 原发起人；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long actorUserId;
    /** 已使用协议；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String protocolCode;
    /** 实际外部执行状态；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String externalStatusCode;
    /** 实际失败分类 */
    private String failureCategoryCode;
    /** 按PLT契约可保存的脱敏命令文本；不得含凭据 */
    private String commandText;
    /** 本次命令模板名称 */
    private String templateName;
}
