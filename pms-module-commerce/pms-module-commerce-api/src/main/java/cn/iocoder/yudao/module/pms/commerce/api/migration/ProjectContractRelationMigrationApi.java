package cn.iocoder.yudao.module.pms.commerce.api.migration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** INT owns source bindings; COM owns authorized formal project/contract relationships. */
public interface ProjectContractRelationMigrationApi {
    /** Explicitly approved source audit identities; these strings create no user or authorization. */
    Set<String> UNLINKED_AUDIT_CODES=Set.of("sys","异常数据处理");
    record Relation(Long id, Long tenantId, Long projectId, Long contractId, String relationRole,
                    String sourceSystem, String sourceTable, String sourceRecordKey,
                    LocalDateTime effectiveFrom, LocalDateTime effectiveTo, String status, Integer version,
                    String creator, LocalDateTime createTime, String updater, LocalDateTime updateTime, Boolean deleted) {}
    /** reviewedBefore is a complete, separately adjudicated existing row, never an audit-ignore option. */
    record Entry(String sourceKey, Long targetId, Relation lastApplied, Relation reviewedBefore,
                 Relation incoming, String sourceProjectKey, String sourceProjectCode,
                 Long expectedProjectVersion, String contractNo,Long sourceConnectionId) {
        public Entry(String sourceKey,Long targetId,Relation lastApplied,Relation reviewedBefore,Relation incoming,
                     String sourceProjectKey,String sourceProjectCode,Long expectedProjectVersion,String contractNo) {
            this(sourceKey,targetId,lastApplied,reviewedBefore,incoming,sourceProjectKey,sourceProjectCode,expectedProjectVersion,contractNo,null);
        }
    }
    record Request(Long tenantId, Long actorId, String owner, List<Entry> entries, boolean adoptExisting) {}
    record Result(String sourceKey, String action, Relation before, Relation after) {}
    record Reference(String sourceKey,Long projectId,String projectType,String projectCode,String contractNo) {}
    record ReferenceQuery(Long tenantId,Long actorId,String owner,List<Reference> references) {}
    record ResolvedReference(String sourceKey,Long projectId,Long projectVersion,Long contractId) {}
    /** Reads existing formal identities; no business creation, grants, or migration-binding writes. */
    List<ResolvedReference> resolveReferences(ReferenceQuery query);
    List<Result> preview(Request request);
    /** Shares the transaction with INT bindings and immutable run evidence. */
    List<Result> apply(Request request);
}
