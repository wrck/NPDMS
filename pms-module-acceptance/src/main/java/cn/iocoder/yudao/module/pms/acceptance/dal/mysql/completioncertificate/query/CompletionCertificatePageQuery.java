package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class CompletionCertificatePageQuery extends PageParam {

    private Long tenantId;
    private Set<Long> visibleProjectIds;
    private Long projectId;
    private String code;
    private String name;
    private Long customerId;
    private Integer status;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate[] completionDate;

}
