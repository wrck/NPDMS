package cn.iocoder.yudao.module.pms.engineering.api.arrival;

import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFact;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFactQuery;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFactRevalidationQuery;

/** IMP到货签收项目事实的公开只读契约。 */
public interface ArrivalAcceptanceFactApi {

    ArrivalAcceptanceFact inspect(ArrivalAcceptanceFactQuery query);

    ArrivalAcceptanceFact lockAndRevalidate(ArrivalAcceptanceFactRevalidationQuery query);
}
