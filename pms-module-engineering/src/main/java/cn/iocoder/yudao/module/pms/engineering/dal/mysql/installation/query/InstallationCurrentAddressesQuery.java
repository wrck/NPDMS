package cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query;
import java.util.List;
public record InstallationCurrentAddressesQuery(Long tenantId, Long projectId, List<Long> equipmentIds) { }
