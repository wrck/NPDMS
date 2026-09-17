package cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.entity.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 复制旧审核参数；PASS 通过，REJECT 退回草稿。 */
@Data
public class BriefingEntityApproveReqVO {
    @NotNull
    private Long id;
    @NotBlank
    @Size(max = 32)
    private String approveAction;
    private Long approverUserId;
    @Size(max = 500)
    private String approveOpinion;
    private Integer version;
}
