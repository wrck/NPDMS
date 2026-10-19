package cn.iocoder.yudao.module.pms.project.api.migration;
import java.util.*;
/** Historical carry only. Current authorization remains the separately approved existing Owner command. */
public interface ProjectMemberSourceHistoryApi {
    List<String> COLUMNS=List.of("id","projectId","projectType","memberRole","memberCode","memberName","phoneNum","email","fromFlag","createTime","createBy","updateTime","updateBy","effectiveFrom","effectiveTo");
    record Entry(String sourceKey,Map<String,String> originalFields,Long targetId,String formalFieldsJson) {}
    record Command(Long tenantId,Long actorId,Long projectId,List<Entry> entries) {}
    record Result(String sourceKey,Long targetId,String targetTable,String action,String formalFieldsJson) {}
    List<Result> preview(Command command);
    List<Result> apply(Command command);
}
