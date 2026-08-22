package cn.iocoder.yudao.module.pms.asset.dal.dataobject.location;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("ast_address")
@Data
@EqualsAndHashCode(callSuper = true)
public class AddressDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String countryCode;
    private String countryName;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String districtCode;
    private String districtName;
    private String detailAddress;
    private String fullAddress;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String normalizedAddress;
    private String addressFingerprint;
    private Integer status;
    private Integer version;

}
