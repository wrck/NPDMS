package cn.iocoder.yudao.module.pms.asset.dal.dataobject.location;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("ast_area_department_mapping")
@Data
@EqualsAndHashCode(callSuper = true)
public class AreaDepartmentMappingDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String areaCode;
    private String areaLevel;
    private String mappingType;
    private String departmentCode;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Integer status;
    private Integer version;

}
