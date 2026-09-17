package cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity;

import lombok.Data;
import java.time.LocalDateTime;

/** 仅供显式承接读取的旧表投影；与旧 BriefingDO 无继承或运行时依赖。 */
@Data
public class BriefingEntityImportSource {
    private Long id;
    private String code;
    private Long projectId;
    private String name;
    private String briefingType;
    private Long templateId;
    private String templateSnapshot;
    private String sourceSnapshot;
    private String content;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String fileChecksum;
    private Integer status;
    private Integer version;
    private LocalDateTime generateTime;
    private LocalDateTime publishTime;
    private Long approverUserId;
    private String approveOpinion;
    private LocalDateTime approveTime;
    private Long creatorUserId;
    private String remark;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
    private Boolean deleted;
    private Long tenantId;
}
