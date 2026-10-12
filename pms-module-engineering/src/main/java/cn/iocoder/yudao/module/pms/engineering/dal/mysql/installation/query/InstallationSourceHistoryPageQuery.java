package cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query;
import java.util.Set;
public record InstallationSourceHistoryPageQuery(Long tenantId,Set<Long> visibleProjectIds,
        Long projectId,Long equipmentId,int pageNo,int pageSize) {}
