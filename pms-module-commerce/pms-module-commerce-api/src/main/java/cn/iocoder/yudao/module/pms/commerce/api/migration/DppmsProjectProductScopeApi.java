package cn.iocoder.yudao.module.pms.commerce.api.migration;

import java.time.LocalDateTime;
import java.util.*;

/** Historical project products become formal scopes without consuming current allocation capacity. */
public interface DppmsProjectProductScopeApi {
    List<Result> preview(Command command);
    List<Result> apply(Command command);
    Page page(Query query);
    record Query(Long tenantId,Long actorId,Long projectId,int pageNo,int pageSize) {}
    record Page(List<Result> list,long total) {}
    List<String> ORIGINAL_COLUMNS=List.of("id","projectId","contractNo","itemCode","itemName","projectQuantity",
            "orderQuantity","deliverQuantity","openQuantity","orderNumber","lineNum");
    record Command(Long tenantId,Long actorId,Long projectId,Long expectedProjectVersion,String sourceProjectKey,List<Entry> entries,
                   Long sourceConnectionId) {
        public Command(Long tenantId,Long actorId,Long projectId,Long expectedProjectVersion,String sourceProjectKey,List<Entry> entries) {
            this(tenantId,actorId,projectId,expectedProjectVersion,sourceProjectKey,entries,null);
        }
    }
    /** Existing formally carried IDs and migration audit are retained during the approved entity consolidation. */
    record Baseline(Long id,String creator,LocalDateTime createTime,String updater,LocalDateTime updateTime,String formalFieldsJson) {
        public Baseline(Long id,String creator,LocalDateTime createTime,String updater,LocalDateTime updateTime) {
            this(id,creator,createTime,updater,updateTime,null);
        }
    }
    record Entry(String sourceKey,Map<String,String> originalFields,Baseline baseline) {}
    record Result(String sourceKey,Long targetId,String action,Map<String,Object> fields,String message) {
        public Result(String sourceKey,Long targetId,String action,Map<String,Object> fields) {
            this(sourceKey,targetId,action,fields,null);
        }
    }
}
