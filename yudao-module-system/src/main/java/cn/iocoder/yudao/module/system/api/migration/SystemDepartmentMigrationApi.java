package cn.iocoder.yudao.module.system.api.migration;

import java.time.LocalDateTime;
import java.util.List;

/** Existing directory rows are referenced; only explicitly selected missing departments are created. */
public interface SystemDepartmentMigrationApi {
    record Department(Long id,String code,String name,Long parentId,Integer sort,Long leaderUserId,
                      String phone,String email,Integer status,Integer version,String creator,
                      LocalDateTime createTime,String updater,LocalDateTime updateTime) {}
    record Entry(String object,String sourceKey,String code,String name,String parentCode,Integer status,
                 LocalDateTime createTime,LocalDateTime updateTime,Long targetId,Department lastApplied,
                 boolean referenceOnly,String creator,String updater,Long leaderUserId) {
        public Entry(String object,String sourceKey,String code,String name,String parentCode,Integer status,
                     LocalDateTime createTime,LocalDateTime updateTime,Long targetId,Department lastApplied,boolean referenceOnly) {
            this(object,sourceKey,code,name,parentCode,status,createTime,updateTime,targetId,lastApplied,referenceOnly,null,null,null);
        }
    }
    record Request(Long tenantId,String owner,List<Entry> entries) {}
    record Result(String object,String sourceKey,Department before,Department after,String action) {}
    List<Department> getCatalogue();
    List<Result> preview(Request request);
    List<Result> apply(Request request);
    void refreshCaches();
}
