package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.time.LocalDate;
public record ProjectEndDateUpdate(Long tenantId, Long projectId, Integer expectedVersion,
                                  LocalDate endDate, String updater) {
}
