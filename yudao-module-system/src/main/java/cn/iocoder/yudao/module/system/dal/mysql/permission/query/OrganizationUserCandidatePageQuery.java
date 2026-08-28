package cn.iocoder.yudao.module.system.dal.mysql.permission.query;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class OrganizationUserCandidatePageQuery {

    Long companyId;
    Long departmentId;
    String departmentCode;
    String keyword;
    Integer enabledStatus;
    LocalDateTime currentTime;
    Integer offset;
    Integer limit;

}
