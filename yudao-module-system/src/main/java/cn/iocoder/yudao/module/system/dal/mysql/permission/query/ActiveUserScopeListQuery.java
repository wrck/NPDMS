package cn.iocoder.yudao.module.system.dal.mysql.permission.query;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class ActiveUserScopeListQuery {

    Long userId;
    LocalDateTime currentTime;
    Integer enabledStatus;

}
