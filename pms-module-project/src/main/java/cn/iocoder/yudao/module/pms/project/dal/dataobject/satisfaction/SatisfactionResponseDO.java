package cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("acc_satisfaction_response")
@Data
public class SatisfactionResponseDO {
    @TableId private Long id;
    private Long tenantId;
    private Long questionnaireId;
    private Integer responseNo;
    private String requestId;
    private String submitChannel;
    private String customerContactRef;
    private Long assistedByUserId;
    private String answerSnapshot;
    private LocalDateTime submittedAt;
    private String creator;
    private LocalDateTime createTime;
}
