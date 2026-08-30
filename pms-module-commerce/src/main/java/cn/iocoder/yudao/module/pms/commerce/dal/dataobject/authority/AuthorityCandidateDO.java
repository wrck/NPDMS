package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("com_authority_candidate")
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthorityCandidateDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String objectType;
    private String candidateSourceSystem;
    private String candidateSourceKey;
    private String candidateVersion;
    private String candidatePayload;
    private String evidenceReference;
    private String candidateStatus;
    private String matchedOwnerTable;
    private Long matchedOwnerId;
    private String matchedOwnerSourceVersion;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private Long decidedBy;
    private LocalDateTime decidedAt;
    @Version
    private Integer version;
}
