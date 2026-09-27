package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.deliverable;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

import java.time.LocalDateTime;

/**
 * 阶段交付件归集 DO
 */
@TableName("imp_eng_deliverable")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeliverableDO extends BaseBusinessEntity {

    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 阶段编号
     */
    private Long phaseId;
    /**
     * 交付件编码，项目内唯一
     */
    private String code;
    /**
     * 交付件名称
     */
    private String name;
    /**
     * 类型 DAILY 日报 / RECEIPT 签收单 / SERVICE 服务单 / COMPLETION 完工证明 / TEST 测试记录 / CONFIG 配置档案
     */
    private String deliverableType;
    /**
     * 来源业务类型
     */
    private String sourceType;
    /**
     * 来源业务编号
     */
    private Long sourceId;
    /**
     * 文件地址
     */
    private String fileUrl;
    /**
     * 文件大小
     */
    private Long fileSize;
    /**
     * 文件校验值
     */
    private String fileChecksum;
    /**
     * 0待归集 1已归集 2已作废
     */
    private Integer status;
    /**
     * 归集时间
     */
    private LocalDateTime archivedTime;
    /**
     * 归集人
     */
    private Long archivedBy;
    /**
     * 备注
     */
    private String remark;

}
