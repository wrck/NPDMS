package cn.iocoder.yudao.module.system.api.migration;

import java.time.LocalDateTime;
import java.util.List;

/** Approved legacy values in the existing global SYSTEM dictionary. */
public interface SystemDictionaryDataMigrationApi {
    record Item(Long id,Integer sort,String label,String value,String dictType,Integer status,String colorType,
                String cssClass,String remark,String creator,LocalDateTime createTime,String updater,
                LocalDateTime updateTime,Boolean deleted) {}
    record Entry(String sourceKey,Long targetId,Item lastApplied,Item incoming) {}
    record Request(Long tenantId,Long actorId,String owner,List<Entry> entries) {}
    record Result(String sourceKey,String action,Item before,Item after) {}
    List<Result> preview(Request request);
    List<Result> apply(Request request);
}
