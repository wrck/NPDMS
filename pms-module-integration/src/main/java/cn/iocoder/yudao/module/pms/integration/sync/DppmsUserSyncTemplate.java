package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/** Reads every original account/profile column except the two explicitly excluded credential columns. */
public final class DppmsUserSyncTemplate {
    private DppmsUserSyncTemplate() {}
    static final Map<String, List<String>> COLUMNS = Map.of(
            "t_user", List.of("user_id", "user_name", "create_by", "create_time", "update_by", "update_time", "status",
                    "needChangePwd", "loginErrorCount", "isSysUser", "userCustom1", "userCustom2", "userCustom3", "userCustom4", "userCustom5"),
            "t_user_info", List.of("id", "workNo", "realName", "eName", "compID", "depID", "jobID", "reportTo", "wfreportTo",
                    "empStatus", "jobStatus", "empType", "sex", "birthday", "email", "mobile", "telphone", "avatar", "remark",
                    "state", "user_id", "custom1", "custom2", "custom3", "custom4", "custom5"),
            "fnd_user_info", List.of("id", "username", "email", "dpNo", "realName", "roleIds", "isemail", "status",
                    "defaultPage", "pwdoverdue", "createBy", "createTime", "updateBy", "updateTime", "effectiveFrom", "effectiveTo", "customInfo"));
    static final List<String> ACCOUNT_REFERENCES = List.of("migrationLoginCount", "migrationLoginEmployeeId",
            "migrationLoginWorkNo", "migrationLoginName", "migrationEmployeeId", "migrationEmployeeWorkNo", "migrationEmployeeName");
    static final List<String> EMPLOYMENT_REFERENCES = List.of("migrationCompanyCode", "migrationDepartmentCode",
            "migrationPositionCode", "migrationVerifiedEmployeeId");
    public static SyncDefinition create(Long connectionId) {
        return SyncDefinition.builder().adapter("DPPMS_USERS").sourceSystem("DPPMS").connectionId(connectionId)
                .mode("ONCE").loadingMode("UPSERT").missingPolicy("RETAIN")
                .cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN").overlapSeconds(0)
                .retryCount(0).retryIntervalSeconds(60).autoPaging(false).maxRows(10000).maxBytes(64L * 1024 * 1024)
                .sources(List.of(source("t_user"), source("t_user_info"), source("fnd_user_info"))).build();
    }
    private static SyncDefinition.Source source(String table) {
        List<String> columns = COLUMNS.get(table);
        String key = "t_user".equals(table) ? "user_id" : "id";
        List<String> fields = new ArrayList<>(columns);
        fields.addAll("t_user_info".equals(table) ? EMPLOYMENT_REFERENCES : ACCOUNT_REFERENCES);
        String sql = "SELECT " + String.join(",", columns.stream().map(column -> "u." + column).toList());
        if ("t_user_info".equals(table)) {
            // t_company.id and ehr_company.compID are distinct identities; never reuse the latter here.
            sql += ",c.compCode AS migrationCompanyCode,d.depCode AS migrationDepartmentCode,j.jobCode AS migrationPositionCode,"
                    + "e.empID AS migrationVerifiedEmployeeId FROM t_user_info u "
                    + "LEFT JOIN t_company c ON c.id=u.compID LEFT JOIN ehr_department d ON d.depID=u.depID "
                    + "LEFT JOIN ehr_job j ON j.jobID=u.jobID LEFT JOIN t_user a ON a.user_id=u.user_id "
                    + "LEFT JOIN ehr_employee e ON e.empID=a.userCustom4 AND CAST(e.workNo AS BINARY)=CAST(u.workNo AS BINARY) AND u.workNo<>''";
        } else {
            String username = "t_user".equals(table) ? "user_name" : "username";
            sql += ",l.loginCount AS migrationLoginCount,l.empID AS migrationLoginEmployeeId,l.workNo AS migrationLoginWorkNo,"
                    + "l.name AS migrationLoginName,e.empID AS migrationEmployeeId,e.workNo AS migrationEmployeeWorkNo,e.name AS migrationEmployeeName "
                    + "FROM " + table + " u LEFT JOIN (SELECT account,COUNT(*) AS loginCount,MIN(empID) AS empID,MIN(workNo) AS workNo,MIN(name) AS name "
                    + "FROM ehr_login GROUP BY account) l ON CAST(l.account AS BINARY)=CAST(u." + username + " AS BINARY)"
                    + " LEFT JOIN ehr_employee e ON e.empID=l.empID";
        }
        return SyncDefinition.Source.builder().object(table).sourceObject(table).readMode("SQL")
                .sql(sql).parameters(Map.of())
                .columns(List.of()).filters(List.of()).sourceKey(key)
                // Profile rows have no modification time. All dependent identities are reconciled together.
                .updatedAt(null).mappings(fields.stream().map(column -> SyncDefinition.Mapping.builder()
                        .target(column).source(column).conversion("DIRECT").build()).toList()).build();
    }
}
