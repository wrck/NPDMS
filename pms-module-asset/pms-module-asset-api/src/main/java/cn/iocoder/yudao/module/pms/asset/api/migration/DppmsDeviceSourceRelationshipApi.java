package cn.iocoder.yudao.module.pms.asset.api.migration;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import java.time.LocalDateTime;
import java.util.List;
/** Contract-scoped main/extra SN facts; never activates a physical assembly or changes ownership. */
public interface DppmsDeviceSourceRelationshipApi {
    List<ImportResult> previewRelationships(Command command);
    List<ImportResult> applyRelationships(Command command);
    List<NativeEntityMigrationRow> importRelationships(Command command);
    PageResult<RelationshipRecord> pageRelationships(Query query);
    List<AuditResult> previewAuditCorrection(AuditRequest request);
    List<AuditResult> applyAuditCorrection(AuditRequest request);
    /** Separate approved correction. Its full-row fingerprint protects every physical field. */
    record AuditRequest(Long tenantId,Long actorId,String owner,List<AuditEntry> entries) {}
    record AuditEntry(Relation source,Long targetId,String expectedBeforeFingerprint) {}
    record AuditResult(String sourceKey,String action,AuditRecord before,AuditRecord after) {}
    record AuditRecord(Long id,Long tenantId,String sourceDeviceSn,String targetDeviceSn,String sourceItem,
            String targetItem,String relationshipType,String contractNo,LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo,String sourceSystem,String sourceKey,String sourceVersion,Integer version,
            String creator,LocalDateTime createTime,String updater,LocalDateTime updateTime,Boolean deleted) {
        public String fingerprint() {
            var values=java.util.Arrays.asList(id,tenantId,sourceDeviceSn,targetDeviceSn,sourceItem,targetItem,
                    relationshipType,contractNo,effectiveFrom,effectiveTo,sourceSystem,sourceKey,sourceVersion,
                    version,creator,createTime,updater,updateTime,deleted);
            var canonical=values.stream().map(v->v==null?null:v instanceof LocalDateTime d
                    ?d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")):v.toString()).toList();
            try {
                return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(
                        cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(canonical)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch(java.security.NoSuchAlgorithmException ex) {throw new IllegalStateException(ex);}
        }
    }
    /** Migration transaction owns source evidence and bindings, not this business entity. */
    record Command(Long tenantId,Long actorId,List<Relation> relations) {}
    record Relation(Long sourceId,String sn1,String item1,String sn2,String item2,String contract,
            String sourceCreateTime,String sourceUpdateTime) {}
    record ImportResult(String sourceKey,String action,RelationshipRecord before,RelationshipRecord after) {}
    record Query(Long tenantId,Long actorId,Long projectId,Long deviceId,String deviceSn,int pageNo,int pageSize) {}
    record RelationshipRecord(Long id,String sourceDeviceSn,String targetDeviceSn,String sourceItem,String targetItem,
            String relationshipType,String contractNo,LocalDateTime effectiveFrom,LocalDateTime effectiveTo,
            String sourceSystem,String sourceKey,String sourceVersion,Integer version,String creator,
            LocalDateTime createTime,String updater,LocalDateTime updateTime) {}
}
