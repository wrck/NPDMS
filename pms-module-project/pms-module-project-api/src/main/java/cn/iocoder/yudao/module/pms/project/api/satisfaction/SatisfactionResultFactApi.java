package cn.iocoder.yudao.module.pms.project.api.satisfaction;

import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionResultFact;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionResultFactQuery;

public interface SatisfactionResultFactApi {
    SatisfactionResultFact inspect(SatisfactionResultFactQuery query);
    SatisfactionResultFact lockAndRevalidate(SatisfactionResultFactQuery query);
}
