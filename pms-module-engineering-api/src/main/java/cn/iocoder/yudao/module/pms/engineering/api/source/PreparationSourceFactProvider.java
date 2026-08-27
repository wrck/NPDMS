package cn.iocoder.yudao.module.pms.engineering.api.source;

import cn.iocoder.yudao.module.pms.engineering.api.source.dto.PreparationSourceFact;
import cn.iocoder.yudao.module.pms.engineering.api.source.dto.PreparationSourceFactQuery;
import cn.iocoder.yudao.module.pms.engineering.api.source.dto.PreparationSourceFactRevalidationQuery;

/** INT-05等权威来源面向PRE-02提供的窄只读事实契约。 */
public interface PreparationSourceFactProvider {

    String sourceTypeCode();

    PreparationSourceFact inspect(PreparationSourceFactQuery query);

    PreparationSourceFact lockAndRevalidate(PreparationSourceFactRevalidationQuery query);
}
