package cn.iocoder.yudao.module.pms.engineering.api.installation;

import java.util.List;
import java.util.Map;

/** Approved historical installation carry into the existing record and location Owners. */
public interface DppmsInstallationRecordMigrationApi {
    List<Result> preview(Command command);
    List<Result> apply(Command command);
    List<String> ORIGINAL_COLUMNS=List.of("id","projectId","barcode","itemCode","itemModel","itemName",
            "receiveName","emsNum","emsCompany","packdate","contractNo","installAddress","chProjectId",
            "chContractNo","transferProjectId","transferContractNo","transferFlag","createTime","createBy","updateTime","updateBy");
    record Command(Long tenantId,Long actorId,Long projectId,String sourceProjectKey,List<Entry> entries) {}
    /** Immutable migration guard, not a business address store. JSON retains explicit original nulls. */
    record Baseline(Long id,String formalFieldsJson,Long currentInstallationId,String currentFieldsJson) {}
    record Entry(String sourceKey,Map<String,String> originalFields,Baseline baseline) {}
    record Result(String sourceKey,Long targetId,String action,Baseline after) {}
}
