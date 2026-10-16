package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.business;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.ProjectBusinessModel;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("imp_eng_training")
@ProjectBusinessModel(ownerModule="IMP", entityType="training", stableCode="IMP_TRAINING", name="现场培训",
        permissionPrefix="pms:imp-training", nativeEntityType="TRAINING_RECORD")
public class TrainingBusinessDO extends BaseProjectBusinessEntity {
    @BusinessModelField(name="培训编号", writable=false, displayOrder=10) private String code;
    @BusinessModelField(name="培训名称", displayOrder=20) @NotBlank @Size(max=255) private String name;
    @BusinessModelField(name="客户联系人", displayOrder=30) private String contactName;
    @BusinessModelField(name="联系电话", displayOrder=40) private String contactPhone;
    @BusinessModelField(name="培训类型", displayOrder=50) @NotBlank private String trainingTypes;
    @BusinessModelField(name="培训时间", displayOrder=60) @NotNull private LocalDate trainingTime;
    @BusinessModelField(name="培训工程师", displayOrder=70) private Long trainerUserId;
    @BusinessModelField(name="工程师姓名", writable=false, displayOrder=80) private String trainerName;
    @BusinessModelField(name="参训人数", displayOrder=90) private Integer traineeCount;
    @BusinessModelField(name="培训内容", displayOrder=100) private String content;
    @BusinessModelField(name="状态", writable=false, displayOrder=110) private Integer status;
    // Native token hashes and frozen schemas remain stored but never enter the public field contract.
    private String signTokenDigest;
    private LocalDateTime tokenExpiresAt;
    @BusinessModelField(name="工程师评价", writable=false, displayOrder=120) private String skillRating;
    @BusinessModelField(name="培训效果", writable=false, displayOrder=130) private String effectRating;
    @BusinessModelField(name="培训满意度", writable=false, displayOrder=140) private String satisfactionRating;
    @BusinessModelField(name="综合意见", writable=false, displayOrder=150) private String signOpinion;
    @BusinessModelField(name="客户签字人", writable=false, displayOrder=160) private String signConfirmerName;
    @BusinessModelField(name="客户确认时间", writable=false, displayOrder=170) private LocalDateTime signTime;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String fileChecksum;
    @BusinessModelField(name="备注", displayOrder=180) private String remark;
    @BusinessModelField(name="客户确认模板", displayOrder=190) private Long confirmationTemplateId;
    private Long confirmationRevisionId;
    private String confirmationFormRules;
    private String confirmationValues;
    private String signatureImageDataUrl;
    @BusinessModelField(name="打印模板", displayOrder=200) private Long printTemplateId;
    private Long printRevisionId;
    private String printLayoutSnapshot;
}
