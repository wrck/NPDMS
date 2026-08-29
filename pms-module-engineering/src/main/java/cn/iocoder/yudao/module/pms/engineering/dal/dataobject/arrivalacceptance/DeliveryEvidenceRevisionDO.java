package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("imp_delivery_evidence_revision")
@Data
public class DeliveryEvidenceRevisionDO implements Serializable {

    @TableId
    private Long id;
    private Long evidenceId;
    private Integer revisionNo;
    private String fileReferenceId;
    private Integer fileVersionNo;
    private String fileHash;
    private Long sourceRecordId;
    private Long sourceVersion;
    private String creator;
    private LocalDateTime createTime;
    private Long tenantId;
}
