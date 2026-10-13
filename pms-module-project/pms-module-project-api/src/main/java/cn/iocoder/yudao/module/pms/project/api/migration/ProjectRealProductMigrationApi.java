package cn.iocoder.yudao.module.pms.project.api.migration;

import java.util.List;
import java.util.Map;

/** Direct source input for the existing formal additional product-list Owner. */
public interface ProjectRealProductMigrationApi {
    List<String> ORIGINAL_COLUMNS=List.of("id","projectId","contractNo","projectCode","orderExecNumber",
            "productFirstName","productName","productSubCode","productSubModel","productSubName","num","memo");
    List<String> UPSTREAM_COLUMNS=List.of("projectCode","orderExecNumber","productFirstName","productName",
            "productSubCode","productSubModel","productSubName","num","memo","dataSource");
    record Source(String sourceKey,Map<String,String> originalFields,Map<String,String> upstreamFields,
                  int upstreamMatchCount,Long targetId,String baseline) {}
    record Command(ProjectMigrationIdentityContextApi.Query identity,List<Source> rows) {}
    record Result(String sourceKey,Long targetId,String action,Map<String,Object> fields,String baseline) {}
    List<Result> preview(Command command);
    List<Result> apply(Command command);
}
