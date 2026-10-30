package cn.iocoder.yudao.module.pms.acceptance.api.satisfaction;

import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionResultFact;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionResultFactQuery;

public interface SatisfactionResultFactApi {
    SatisfactionResultFact inspect(SatisfactionResultFactQuery query);
    SatisfactionResultFact lockAndRevalidate(SatisfactionResultFactQuery query);
}
