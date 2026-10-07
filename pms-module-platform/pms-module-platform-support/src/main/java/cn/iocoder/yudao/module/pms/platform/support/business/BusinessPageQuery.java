package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.Data;

/** Default business query. A business may subclass it for its own XML query parameters. */
@Data
public class BusinessPageQuery {
    @Min(1) private int pageNo = 1;
    @Min(1) @Max(200) private int pageSize = 20;
    private List<BusinessFieldFilter> filters = List.of();
}
