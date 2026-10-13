package cn.iocoder.yudao.module.pms.project.api.migration;

import java.util.List;
import java.util.Map;

/** SQL input to the existing lease-list and product-configuration Owners. */
public interface ProjectAssociatedProductMigrationApi {
    enum Kind {
        LEASE_PRODUCT("pm_project_product_lease_line_from_crm","ProjectLeaseProduct","proj_project_lease_product",
                List.of("projectCode","orderExecNumber","productFirstName","productName","productSubCode","productSubModel","productSubName","num","memo","leaseDuration","dataSource")),
        PRODUCT_CONFIGURATION("pm_project_product_config_level_info_from_crm","ProjectProductConfiguration","proj_project_product_configuration",
                List.of("projectCode","orderExecNumber","itemGroup","itemCode","parentCode","quantity","bomPaths","itemModel","itemDesc","level","dataSource"));
        public final String sourceTable,objectType,targetTable;public final List<String> columns;
        Kind(String sourceTable,String objectType,String targetTable,List<String> columns) {
            this.sourceTable=sourceTable;this.objectType=objectType;this.targetTable=targetTable;this.columns=columns;
        }
    }
    record Source(String sourceKey,Map<String,String> originalFields,Long targetId,String baseline) {}
    record Command(ProjectMigrationIdentityContextApi.Query identity,String originalProjectCode,Kind kind,List<Source> rows) {}
    record Result(String sourceKey,Long targetId,String action,Map<String,Object> fields,String baseline) {}
    List<Result> preview(Command command);
    List<Result> apply(Command command);
}
