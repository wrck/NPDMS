package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class AcceptancePageQuery extends PageParam {

    private Long tenantId;
    private Set<Long> visibleProjectIds;
    private Long projectId;
    private String code;
    private String name;
    private String acceptanceType;
    private Integer status;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate[] acceptanceDate;

}
