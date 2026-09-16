package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 低代码灰度发布策略（批次5-T4，借鉴华为 AppCube 灰度发布 / OutSystems LifeTime）。
 *
 * <p>发布审批通过后，可选择创建灰度发布策略：按比例（grayPercentage 0-100）或租户白名单
 * 渐进生效。灰度期间仅命中用户/租户可见新版本，其余用户仍用旧版本。
 * 可随时调整比例、全量发布（FULL）或回滚（ROLLED_BACK）。</p>
 *
 * <p>状态流转：
 * <ul>
 *   <li>GRAYING: 灰度进行中（0 < percentage < 100）</li>
 *   <li>FULL: 已全量发布（percentage = 100）</li>
 *   <li>ROLLED_BACK: 已回滚（灰度中止，恢复旧版本）</li>
 * </ul></p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("pms_lowcode_gray_release")
public class LowCodeGrayRelease {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置类型: FORM/LIST/ENTITY/MICROFLOW/CONNECTOR/RULE */
    private String configType;

    private Long configId;

    private String configCode;

    /** 灰度版本号（对应 LowCodePublishRecord.version） */
    private Integer version;

    /** 关联发布记录 ID */
    private Long publishRecordId;

    /** 灰度比例（0-100，0=未灰度，100=全量） */
    private Integer grayPercentage;

    /** 租户白名单（JSON 数组，如 ["tenant1","tenant2"]，命中白名单的租户直接可见新版本） */
    private String tenantWhitelist;

    /** 状态: GRAYING / FULL / ROLLED_BACK */
    private String status;

    /** 灰度开始时间 */
    private LocalDateTime grayStartedAt;

    /** 全量发布时间 */
    private LocalDateTime fullReleasedAt;

    /** 回滚时间 */
    private LocalDateTime rolledBackAt;

    /** 创建人 */
    private String createBy;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
