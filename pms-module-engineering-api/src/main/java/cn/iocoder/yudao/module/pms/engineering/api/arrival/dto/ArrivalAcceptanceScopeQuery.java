package cn.iocoder.yudao.module.pms.engineering.api.arrival.dto;

import java.util.List;
import java.util.Set;

/** 到货事实查询及重验查询共有的业务范围，内部编排直接复用原查询对象。 */
public interface ArrivalAcceptanceScopeQuery {
    Long tenantId();
    Long projectId();
    Set<Long> deviceIds();
    List<ArrivalQuantityScopeFact> quantityScopes();
}
