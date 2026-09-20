package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PMS 现场培训记录 DO（ACC-01，Demo 6.1）。
 * <p>
 * 对应表 {@code imp_eng_training}。
 * 状态：0 草稿、1 已外发、2 客户已确认、3 已作废。
 * 外发令牌仅存 SHA-256 摘要，原始令牌只在外发时返回一次，不落库。
 */
@TableName("imp_eng_training")
@Data
@EqualsAndHashCode(callSuper = true)
public class TrainingDO extends TenantBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 培训记录编码，项目内唯一
     */
    private String code;
    /**
     * 培训名称
     */
    private String name;
    /**
     * 客户联系人（自动带入用户联系人）
     */
    private String contactName;
    /**
     * 客户联系电话（自动带入）
     */
    private String contactPhone;
    /**
     * 培训类型，逗号分隔：TECHNICAL_PRINCIPLE/PRODUCT_OPS/OTHER
     */
    private String trainingTypes;
    /**
     * 培训时间
     */
    private LocalDate trainingTime;
    /**
     * 培训工程师用户编号（默认当前登录人）
     */
    private Long trainerUserId;
    /**
     * 培训工程师姓名快照
     */
    private String trainerName;
    /**
     * 参训人数
     */
    private Integer traineeCount;
    /**
     * 培训内容
     */
    private String content;
    /**
     * 状态：0 草稿 1 已外发 2 客户已确认 3 已作废
     */
    private Integer status;
    /**
     * 外发令牌 SHA-256 摘要（原始令牌不落库）
     */
    private String signTokenDigest;
    /**
     * 外发令牌有效期
     */
    private LocalDateTime tokenExpiresAt;
    /**
     * 客户评价：培训工程师技术水平及表达能力
     */
    private String skillRating;
    /**
     * 客户评价：培训内容及讲解效果
     */
    private String effectRating;
    /**
     * 客户评价：培训满意度
     */
    private String satisfactionRating;
    /**
     * 客户综合意见
     */
    private String signOpinion;
    /**
     * 客户签字人
     */
    private String signConfirmerName;
    /**
     * 客户确认时间
     */
    private LocalDateTime signTime;
    /**
     * 培训记录表文件URL
     */
    private String fileUrl;
    /**
     * 培训记录表文件名
     */
    private String fileName;
    /**
     * 培训记录表文件大小（字节）
     */
    private Long fileSize;
    /**
     * 培训记录表文件SHA-256校验值
     */
    private String fileChecksum;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
    /**
     * 备注
     */
    private String remark;

}
