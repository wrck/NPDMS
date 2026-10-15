package cn.iocoder.yudao.module.pms.asset.api.migration;

import java.util.List;

/** Whole-source factory history; separate from project-scoped history and the current MES baseline. */
public interface DppmsFactoryHistoryMigrationApi {
    List<Result> preview(Command command);
    List<Result> apply(Command command);
    record Command(Long tenantId,Long actorId,List<Entry> entries) {}
    record Values(String serialNumber,String conp,String cpld,String boot,String pcb) {}
    record Entry(String sourceKey,Values values,Long mappedRecordId) {}
    record Result(String sourceKey,Long targetId,String action,Values values) {}
}
