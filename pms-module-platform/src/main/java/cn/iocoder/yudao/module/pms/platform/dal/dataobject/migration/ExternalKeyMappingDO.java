package cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("plt_external_key_mapping")
@Data
@EqualsAndHashCode(callSuper = true)
public class ExternalKeyMappingDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long batchId;
    private Long sourceRecordId;
    private String resultType;
    private String targetContext;
    private String targetObjectType;
    private String targetTable;
    private Long targetId;
    private String targetRole;
    private Integer targetSequence;
    @TableField(exist = false)
    private String resultKey;
}
