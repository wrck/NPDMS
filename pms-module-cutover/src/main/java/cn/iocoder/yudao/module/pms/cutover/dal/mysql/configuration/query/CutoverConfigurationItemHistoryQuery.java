package cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query;

import java.util.List;

public record CutoverConfigurationItemHistoryQuery(String configurationCode, Long excludedRevisionId,
                                                   List<String> stableItemKeys) {
}
