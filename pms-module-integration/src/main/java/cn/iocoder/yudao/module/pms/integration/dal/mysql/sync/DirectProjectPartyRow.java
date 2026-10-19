package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proj_project_party")
public class DirectProjectPartyRow extends TenantBaseDO {
    private Long id;
    private Long projectId;
    private String partyRole;
    private String partyCode;
    private String partyName;
    private String contactName;
    private String phone;
    private String sourceSystem;
    private String sourceTable;
    private String sourceRecordKey;
    private Integer version;
    private String status;
    private java.time.LocalDateTime effectiveFrom;
    private java.time.LocalDateTime effectiveTo;
}
