package cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;

public record InstallationHistoricalCarryUpdate(InstallationDO record, Long expectedVersion) {}
