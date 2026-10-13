package cn.iocoder.yudao.module.pms.project.api.migration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** INT owns source identities and complete original columns; PROJ owns formal project initialization. */
public interface ProjectMasterMigrationApi {
    List<String> ORIGINAL_COLUMNS = List.of("projectId", "projectType", "projectCode", "projectName", "projectState",
            "isback", "column001", "column002", "column003", "column004", "column005", "column006", "column007",
            "column008", "column009", "column010", "column011", "column012", "columno12_readonly", "column013",
            "column014", "customerProjectName", "salesType", "majorProjectLevel", "compId", "createTime", "createBy",
            "updateTime", "updateBy", "effectiveFrom", "effectiveTo", "disabled", "projectStartTime",
            "projectRefreshTime", "projectCloseTime", "customInfo", "customConfig");
    record Facts(Long id, Long tenantId, Long version, String projectCode, String projectName,
                 Long companyId, String companyCode, String companyName,
                 String projectType, String projectCategory, String signingMethod, String implementationMode,
                 String customerProjectName, String salesType, String majorProjectLevel,
                 String marketName, String systemName, String expendName, String industryName,
                 String notTrackReason, LocalDateTime projectStartTime, LocalDateTime projectRefreshTime,
                 LocalDateTime projectCloseTime, LocalDateTime orderCreateTime, String customInfo,
                 Boolean disabled, LocalDateTime effectiveFrom, LocalDateTime effectiveTo,
                 String creator, LocalDateTime createTime, String updater, LocalDateTime updateTime,
                 String lifecycleStatus, String currentStage, LocalDateTime closedAt) {}
    /** originalFields is the bounded 37-column original header, not a business payload destination. */
    record CodeCollisionProject(Long projectId,String projectType) {}
    record CodeAllocation(String originalProjectCode,List<CodeCollisionProject> orderedProjects,String projectCode) {}
    /** Explicit restoration of a reviewed, previously withdrawn migration root; no ordinary takeover. */
    record Restoration(String expectedFingerprint) {}
    record Entry(String sourceKey, Long targetId, Long expectedProjectVersion,
                 Map<String, String> originalFields, String companyCode, String departmentCode,
                 String creator, String updater, Facts lastApplied,CodeAllocation codeAllocation,Restoration restoration) {
        public Entry(String sourceKey,Long targetId,Long expectedProjectVersion,Map<String,String> originalFields,
                     String companyCode,String departmentCode,String creator,String updater,Facts lastApplied,CodeAllocation codeAllocation) {
            this(sourceKey,targetId,expectedProjectVersion,originalFields,companyCode,departmentCode,creator,updater,lastApplied,codeAllocation,null);
        }
        public Entry(String sourceKey,Long targetId,Long expectedProjectVersion,Map<String,String> originalFields,
                     String companyCode,String departmentCode,String creator,String updater,Facts lastApplied) {
            this(sourceKey,targetId,expectedProjectVersion,originalFields,companyCode,departmentCode,creator,updater,lastApplied,null,null);
        }
    }
    record Request(Long tenantId, Long actorId, String owner, List<Entry> entries, boolean adoptExisting) {}
    record Result(String sourceKey, String action, Facts before, Facts after, String message) {}
    List<Result> preview(Request request);
    /** Must join the INT transaction that also saves the binding and migration run evidence. */
    List<Result> apply(Request request);
    record BindingVerification(Long tenantId,Long actorId,List<Facts> projects) {}
    record IdentityTarget(Long projectId,String projectType,String projectCode) {}
    record IdentityInspection(Long tenantId,Long actorId,List<IdentityTarget> projects) {}
    List<Facts> inspectBindings(IdentityInspection request);
    void verifyBindings(BindingVerification request);
    /** Holds the formal project locks until the enclosing INT binding transaction completes. */
    void lockBindings(BindingVerification request);
}
