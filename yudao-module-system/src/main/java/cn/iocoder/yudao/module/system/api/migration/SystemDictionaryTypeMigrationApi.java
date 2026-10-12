package cn.iocoder.yudao.module.system.api.migration;

import java.time.LocalDateTime;
import java.util.List;

/** Migration of dictionary definitions into the existing global SYSTEM model. */
public interface SystemDictionaryTypeMigrationApi {
    record Type(Long id,String name,String type,Integer status,String remark,String creator,LocalDateTime createTime,
                String updater,LocalDateTime updateTime,Boolean deleted,LocalDateTime deletedTime) {}
    record Entry(String sourceKey,Long targetId,Type lastApplied,Type incoming) {}
    record Request(Long tenantId,Long actorId,String owner,List<Entry> entries) {}
    record Result(String sourceKey,String action,Type before,Type after) {}
    List<Result> preview(Request request);
    List<Result> apply(Request request);
}
