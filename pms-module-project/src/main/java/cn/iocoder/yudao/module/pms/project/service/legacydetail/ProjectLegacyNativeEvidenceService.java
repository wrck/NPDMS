package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi.Baseline;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/** Uses the PLT Owner contract; no migration ledger SQL belongs to PROJ. */
@Service
@RequiredArgsConstructor
public class ProjectLegacyNativeEvidenceService {
    private final PlatformMigrationEvidenceApi evidence;
    public record Payload(Long legacyRecordId, Long snapshotId, String retentionReason,
                          Map<String, String> values, Baseline scopeBaseline,
                          cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.Baseline installationBaseline, String realProductBaseline, String associatedListBaseline) {
        public Payload(Long legacyRecordId,Long snapshotId,String retentionReason,Map<String,String> values,Baseline scopeBaseline,
                cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.Baseline installationBaseline,String realProductBaseline) {
            this(legacyRecordId,snapshotId,retentionReason,values,scopeBaseline,installationBaseline,realProductBaseline,null);
        }
        public Payload(Long legacyRecordId,Long snapshotId,String retentionReason,Map<String,String> values,Baseline scopeBaseline,
                cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.Baseline installationBaseline) {
            this(legacyRecordId,snapshotId,retentionReason,values,scopeBaseline,installationBaseline,null);
        }
        public Payload(Long legacyRecordId,Long snapshotId,String retentionReason,Map<String,String> values,Baseline scopeBaseline) {
            this(legacyRecordId,snapshotId,retentionReason,values,scopeBaseline,null);
        }
        public Payload(Long legacyRecordId, Long snapshotId, String retentionReason, Map<String,String> values) {
            this(legacyRecordId,snapshotId,retentionReason,values,null);
        }
    }
    public record RowStatus(String classification, String reasonCode, String reason,
                            List<ExternalTargetMapping> targets, Baseline scopeBaseline,
                            cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.Baseline installationBaseline, String realProductBaseline, String associatedListBaseline) {
        public RowStatus(String classification,String reasonCode,String reason,List<ExternalTargetMapping> targets,Baseline scopeBaseline,
                cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.Baseline installationBaseline,String realProductBaseline) {
            this(classification,reasonCode,reason,targets,scopeBaseline,installationBaseline,realProductBaseline,null);
        }
        public RowStatus(String classification,String reasonCode,String reason,List<ExternalTargetMapping> targets,Baseline scopeBaseline,
                cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.Baseline installationBaseline) {
            this(classification,reasonCode,reason,targets,scopeBaseline,installationBaseline,null);
        }
        public RowStatus(String classification,String reasonCode,String reason,List<ExternalTargetMapping> targets,Baseline scopeBaseline) {
            this(classification,reasonCode,reason,targets,scopeBaseline,null);
        }
        public RowStatus(String classification,String reasonCode,String reason,List<ExternalTargetMapping> targets) {
            this(classification,reasonCode,reason,targets,null);
        }
    }

    public void reconcile(ProjectLegacySourceDO source, ProjectLegacySnapshotDO snapshot, String domain,
                          List<ProjectLegacyRecordDO> records, Map<String, NativeEntityMigrationRow> outcomes) {
        reconcile(source,snapshot,domain,records,outcomes,"V4");
    }

    void reconcile(ProjectLegacySourceDO source, ProjectLegacySnapshotDO snapshot, String domain,
                   List<ProjectLegacyRecordDO> records, Map<String, NativeEntityMigrationRow> outcomes, String protocol) {
        reconcile(source,snapshot,domain,records,outcomes,protocol,Map.of());
    }

    void reconcile(ProjectLegacySourceDO source, ProjectLegacySnapshotDO snapshot, String domain,
                   List<ProjectLegacyRecordDO> records, Map<String, NativeEntityMigrationRow> outcomes, String protocol,
                   Map<String,Baseline> scopeBaselines) {
        reconcile(source,snapshot,domain,records,outcomes,protocol,scopeBaselines,Map.of());
    }
    void reconcile(ProjectLegacySourceDO source, ProjectLegacySnapshotDO snapshot, String domain,
                   List<ProjectLegacyRecordDO> records, Map<String, NativeEntityMigrationRow> outcomes, String protocol,
                   Map<String,Baseline> scopeBaselines,
                   Map<String,cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.Baseline> installationBaselines) {
        reconcile(source,snapshot,domain,records,outcomes,protocol,scopeBaselines,installationBaselines,Map.of());
    }
    void reconcileRealProducts(ProjectLegacySourceDO source,ProjectLegacySnapshotDO snapshot,List<ProjectLegacyRecordDO> records,
            Map<String,NativeEntityMigrationRow> outcomes,Map<String,String> baselines) {
        reconcile(source,snapshot,"real-products",records,outcomes,"REAL_PRODUCT_V1",Map.of(),Map.of(),baselines);
    }
    void reconcileAssociatedList(ProjectLegacySourceDO source,ProjectLegacySnapshotDO snapshot,String domain,List<ProjectLegacyRecordDO> records,
            Map<String,NativeEntityMigrationRow> outcomes,Map<String,String> baselines) {
        String protocol=switch(domain) {
            case "lease-products" -> "LEASE_PRODUCT_V1";
            case "configured-products" -> "PRODUCT_CONFIGURATION_V1";
            default -> throw new IllegalArgumentException("DPPMS_ASSOCIATED_LIST_DOMAIN_INVALID");
        };
        reconcile(source,snapshot,domain,records,outcomes,protocol,Map.of(),Map.of(),Map.of(),baselines);
    }
    private void reconcile(ProjectLegacySourceDO source,ProjectLegacySnapshotDO snapshot,String domain,List<ProjectLegacyRecordDO> records,
            Map<String,NativeEntityMigrationRow> outcomes,String protocol,Map<String,Baseline> scopeBaselines,
            Map<String,cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.Baseline> installationBaselines,
            Map<String,String> realBaselines) {
        reconcile(source,snapshot,domain,records,outcomes,protocol,scopeBaselines,installationBaselines,realBaselines,Map.of());
    }
    private void reconcile(ProjectLegacySourceDO source,ProjectLegacySnapshotDO snapshot,String domain,List<ProjectLegacyRecordDO> records,
            Map<String,NativeEntityMigrationRow> outcomes,String protocol,Map<String,Baseline> scopeBaselines,
            Map<String,cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.Baseline> installationBaselines,
            Map<String,String> realBaselines,Map<String,String> associatedBaselines) {
        if (!"V4".equals(protocol) && !("COLLECTION_PLAN_V1".equals(protocol) && "plans".equals(domain))
                && !("PROJECT_HEADER_V1".equals(protocol) && "project".equals(domain))
                && !("ORDER_FIELDS_V1".equals(protocol) && Set.of("orders","order-lines").contains(domain))
                && !(Set.of("PRODUCT_SCOPE_V1","PRODUCT_SCOPE_V2","PRODUCT_SCOPE_V3","PRODUCT_SCOPE_V4").contains(protocol) && "products".equals(domain))
                && !("EXECUTION_FIELDS_V1".equals(protocol) && "executions".equals(domain))
                && !(Set.of("INSTALLATION_RECORD_V1","INSTALLATION_RECORD_V2").contains(protocol) && "installations".equals(domain))
                && !("REAL_PRODUCT_V1".equals(protocol) && "real-products".equals(domain))
                && !("LEASE_PRODUCT_V1".equals(protocol) && "lease-products".equals(domain))
                && !("PRODUCT_CONFIGURATION_V1".equals(protocol) && "configured-products".equals(domain))
                && !("SOFTWARE_HISTORY_V1".equals(protocol) && "software-versions".equals(domain))
                && !("SOFTWARE_BATCH_V1".equals(protocol) && "software-changes".equals(domain))
                && !("FACTORY_RECORD_V1".equals(protocol) && "factory-versions".equals(domain))
                && !("MEMBER_HISTORY_V1".equals(protocol) && "members".equals(domain))
                && !("FINAL_CUSTOMER_CONTACT_V1".equals(protocol) && "members".equals(domain))
                && !("LICENSE_RECORD_V1".equals(protocol) && "licenses".equals(domain)))
            throw new IllegalArgumentException("DPPMS_NATIVE_PROTOCOL_INVALID");
        if (records.isEmpty() || "maintenance".equals(domain)) return;
        String key = "DPPMS-NATIVE-" + protocol + ":" + snapshot.getId() + ":" + domain;
        String purpose = "NATIVE_" + protocol + "_" + snapshot.getId() + "_" + domain;
        if("LICENSE_RECORD_V1".equals(protocol)) {
            String partition=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(snapshot.getId(),records.stream().map(ProjectLegacyRecordDO::getSourceKey).toList())));
            key="DPPMS-NATIVE-LICENSE:"+partition;
            purpose="NATIVE_LICENSE_"+partition.substring(0,40);
        }
        String table = records.getFirst().getSourceTable();
        String sha = DigestUtil.sha256Hex(JsonUtils.toJsonString(records.stream()
                .map(r -> List.of(r.getSourceKey(), r.getChecksum())).toList()));
        var batch = evidence.createImportBatch(new CreateImportBatchCommand(source.getTenantId(), "PROJ", purpose,
                key, "DPPMS", table, "DPPMS_NATIVE_" + protocol, records.size(), sha, snapshot.getSourceReadAt(),
                null, null, key + ":CREATE", key));
        var commands = new ArrayList<AppendMigrationSourceRecordCommand>();
        for (var r : records) {
            var outcome = outcomes.get(identity(domain, r.getSourceKey()));
            if (outcome == null) throw new IllegalStateException("DPPMS_NATIVE_OUTCOME_MISSING");
            var values = JsonUtils.parseObject(r.getPayloadJson(), new TypeReference<Map<String, String>>() {});
            Baseline baseline=scopeBaselines.get(r.getSourceKey());
            if(Set.of("PRODUCT_SCOPE_V2","PRODUCT_SCOPE_V3","PRODUCT_SCOPE_V4").contains(protocol) && (baseline==null || baseline.id()==null
                    || baseline.createTime()==null || baseline.updateTime()==null || outcome.targets().size()!=1
                    || !Objects.equals(outcome.targets().getFirst().targetId(),baseline.id())
                    || !"com_delivery_scope".equals(outcome.targets().getFirst().targetTable())))
                throw new IllegalArgumentException("DPPMS_PRODUCT_SCOPE_AUDIT_EVIDENCE_INVALID");
            var installationBaseline=installationBaselines.get(r.getSourceKey());
            if(Set.of("INSTALLATION_RECORD_V1","INSTALLATION_RECORD_V2").contains(protocol)
                    && (installationBaseline==null || installationBaseline.id()==null || installationBaseline.formalFieldsJson()==null
                    || outcome.targets().size()!=1 || !Objects.equals(outcome.targets().getFirst().targetId(),installationBaseline.id())
                    || !"imp_eng_installation".equals(outcome.targets().getFirst().targetTable())))
                throw new IllegalArgumentException("DPPMS_INSTALLATION_RECORD_EVIDENCE_INVALID");
            String realBaseline=realBaselines.get(r.getSourceKey());
            if("REAL_PRODUCT_V1".equals(protocol) && (realBaseline==null || outcome.targets().size()!=1
                    || !"proj_project_real_product".equals(outcome.targets().getFirst().targetTable())))
                throw new IllegalArgumentException("DPPMS_REAL_PRODUCT_BASELINE_INVALID");
            String associatedBaseline=associatedBaselines.get(r.getSourceKey());
            if(Set.of("LEASE_PRODUCT_V1","PRODUCT_CONFIGURATION_V1").contains(protocol) && (associatedBaseline==null
                    || outcome.targets().size()!=1 || !Objects.equals("lease-products".equals(domain)?"proj_project_lease_product":"proj_project_product_configuration",outcome.targets().getFirst().targetTable())))
                throw new IllegalArgumentException("DPPMS_ASSOCIATED_LIST_BASELINE_INVALID");
            // sourceChecksum is the immutable source row checksum; added metadata is explicitly separate.
            commands.add(new AppendMigrationSourceRecordCommand(source.getTenantId(), batch.batchId(), "DPPMS", table,
                    sourcePk(source.getSourceProjectKey(),snapshot.getId(),r.getSourceKey()), source.getSourceProjectKey() + ":" + domain + ":" + r.getSourceKey(),
                    Set.of("MEMBER_HISTORY_V1","FINAL_CUSTOMER_CONTACT_V1").contains(protocol)?"{\"legacyRecordId\":"+r.getId()+",\"snapshotId\":"+snapshot.getId()+",\"values\":"+r.getPayloadJson()+"}"
                            :associatedBaseline!=null?associatedListPayload(r,snapshot.getId(),outcome.reasonCode(),associatedBaseline)
                            :realBaseline!=null?realProductPayload(r,snapshot.getId(),outcome.reasonCode(),realBaseline)
                            :installationBaseline==null?JsonUtils.toJsonString(new Payload(r.getId(), snapshot.getId(), outcome.reasonCode(), values,baseline))
                            : installationPayload(r,snapshot.getId(),outcome.reasonCode(),installationBaseline),
                    r.getChecksum(), snapshot.getSourceReadAt(), key));
        }
        var sourceFacts = new ArrayList<MigrationSourceRecordFact>();
        for (int offset = 0; offset < commands.size(); offset += 200) {
            sourceFacts.addAll(evidence.appendSourceRecords(new AppendMigrationSourceRecordsCommand(
                    commands.subList(offset, Math.min(commands.size(), offset + 200)))));
        }
        evidence.markStagedReady(new MarkStagedReadyCommand(source.getTenantId(), batch.batchId(), batch.version(),
                ImportStagingDecision.READY, (long) records.size(), "DPPMS_NATIVE_" + protocol, sha, null, key + ":READY", key));
        var claim = evidence.claimStagedBatch(new ClaimStagedBatchCommand(source.getTenantId(), "PROJ", purpose,
                List.of("DPPMS"), List.of(table), key));
        if (!claim.claimed() || !Objects.equals(claim.batch().batchId(), batch.batchId())) {
            throw new IllegalStateException("DPPMS_NATIVE_BATCH_CLAIM_FAILED");
        }
        List<AppendExternalMappingCommand> mappings = new ArrayList<>();
        Map<String, String> keysByReference = new HashMap<>();
        records.forEach(r -> keysByReference.put(sourcePk(source.getSourceProjectKey(),snapshot.getId(),r.getSourceKey()),r.getSourceKey()));
        long mapped = 0;
        for (var fact : sourceFacts) {
            var outcome = outcomes.get(identity(domain, keysByReference.get(fact.sourcePk())));
            boolean hasTargets = !outcome.targets().isEmpty();
            if (hasTargets) mapped++;
            mappings.add(new AppendExternalMappingCommand(source.getTenantId(), batch.batchId(), fact.sourceRecordId(),
                    hasTargets ? SourceReconciliationType.MAPPED : SourceReconciliationType.RETAINED,
                    outcome.targets(), key + ":ROW:" + fact.sourceRecordId(), key));
        }
        for (int offset = 0; offset < mappings.size(); offset += 200) {
            evidence.appendExternalMappings(new AppendExternalMappingsCommand(
                    mappings.subList(offset, Math.min(mappings.size(), offset + 200)), key + ":MAP:" + offset, key));
        }
        evidence.completeReconciliation(new CompleteReconciliationCommand(source.getTenantId(), batch.batchId(),
                claim.batch().version(), records.size(), mapped, 0, records.size() - mapped,
                "DPPMS_NATIVE_" + protocol, key + ":COMPLETE", key));
    }

    public Map<String, RowStatus> statuses(Long tenantId, String sourceProjectKey, Long snapshotId, List<ProjectLegacyRecordDO> records) {
        var finalContacts=records.stream().filter(row->"members".equals(row.getDomainCode())
                && "60".equals(JsonUtils.parseObject(row.getPayloadJson(),new TypeReference<Map<String,String>>(){}).get("memberRole"))).toList();
        if(!finalContacts.isEmpty()) {
            var ordinary=records.stream().filter(row->!finalContacts.contains(row)).toList();
            var result=new HashMap<>(statuses(tenantId,sourceProjectKey,snapshotId,ordinary,null));
            result.putAll(statuses(tenantId,sourceProjectKey,snapshotId,finalContacts,"FINAL_CUSTOMER_CONTACT_V1"));return result;
        }
        return statuses(tenantId,sourceProjectKey,snapshotId,records,null);
    }
    Map<String,RowStatus> legacySoftwareEventStatuses(Long tenantId,String sourceProjectKey,Long snapshotId,List<ProjectLegacyRecordDO> records) {
        if(records.stream().anyMatch(row->!"software-versions".equals(row.getDomainCode())))
            throw new IllegalArgumentException("DPPMS_SOFTWARE_EVENT_DOMAIN_INVALID");
        return statuses(tenantId,sourceProjectKey,snapshotId,records,"V3");
    }
    private Map<String, RowStatus> statuses(Long tenantId,String sourceProjectKey,Long snapshotId,List<ProjectLegacyRecordDO> records,String exactProtocol) {
        Map<String, RowStatus> result = new HashMap<>();
        if (records.isEmpty()) return result;
        if (records.stream().allMatch(r -> "maintenance".equals(r.getDomainCode()))) {
            String reason="USER_DEFERRED_MAINTENANCE_RECORD";
            records.forEach(r -> result.put(r.getSourceKey(),new RowStatus("RETAINED",reason,LegacyNativeReasons.text(reason),List.of())));
            return result;
        }
        var items = new ArrayList<MigrationPreviewItem>();
        for (int offset = 0; offset < records.size(); offset += 1000) {
            var part = records.subList(offset, Math.min(records.size(), offset + 1000));
            String domain=records.getFirst().getDomainCode();
            var requests=part.stream().map(r -> new MigrationPreviewRecordRequest(
                    sourcePk(sourceProjectKey,snapshotId,r.getSourceKey()),r.getChecksum())).toList();
            String protocol=exactProtocol!=null?exactProtocol:switch(domain) {
                case "members" -> "MEMBER_HISTORY_V1";
                case "software-versions" -> "SOFTWARE_HISTORY_V1";
                case "software-changes" -> "SOFTWARE_BATCH_V1";
                case "licenses" -> "LICENSE_RECORD_V1";
                case "real-products" -> "REAL_PRODUCT_V1";
                case "lease-products" -> "LEASE_PRODUCT_V1";
                case "configured-products" -> "PRODUCT_CONFIGURATION_V1";
                case "plans" -> "COLLECTION_PLAN_V1";
                case "project" -> "PROJECT_HEADER_V1";
                case "orders", "order-lines" -> "ORDER_FIELDS_V1";
                case "products" -> "PRODUCT_SCOPE_V4";
                case "executions" -> "EXECUTION_FIELDS_V1";
                case "factory-versions" -> "FACTORY_RECORD_V1";
                case "installations" -> "INSTALLATION_RECORD_V2";
                default -> "V4";
            };
            var current="licenses".equals(domain)?evidence.preview(new MigrationPreviewCommand(tenantId,"DPPMS",records.getFirst().getSourceTable(),requests)).items():evidence.previewBatch(new MigrationBatchPreviewCommand(tenantId,"PROJ",
                    "NATIVE_"+protocol+"_"+snapshotId+"_"+domain,"DPPMS-NATIVE-"+protocol+":"+snapshotId+":"+domain,
                    "DPPMS",records.getFirst().getSourceTable(),requests)).items();
            if(Set.of("products","installations").contains(domain)) for(String prior:"products".equals(domain)?List.of("PRODUCT_SCOPE_V3","PRODUCT_SCOPE_V2","PRODUCT_SCOPE_V1"):List.of("INSTALLATION_RECORD_V1")) {
                var missing=current.stream().filter(i->i.latestBatchId()==null).map(MigrationPreviewItem::sourcePk).toList();
                if(missing.isEmpty())break;
                var older=evidence.previewBatch(new MigrationBatchPreviewCommand(tenantId,"PROJ",
                        "NATIVE_"+prior+"_"+snapshotId+"_"+domain,"DPPMS-NATIVE-"+prior+":"+snapshotId+":"+domain,
                        "DPPMS",records.getFirst().getSourceTable(),requests.stream().filter(r->missing.contains(r.sourcePk())).toList())).items();
                var previous=new HashMap<String,MigrationPreviewItem>();older.forEach(i->previous.put(i.sourcePk(),i));
                current=current.stream().map(i->i.latestBatchId()==null?previous.getOrDefault(i.sourcePk(),i):i).toList();
            }
            // Keep prior V3, V2 and V1 evidence available for their immutable snapshots.
            for (int version:exactProtocol!=null?List.<Integer>of():Set.of("plans","project","orders","order-lines","products","executions","factory-versions","installations","real-products","software-versions","software-changes").contains(domain)?List.of(4,3,2,1):List.of(3,2,1)) {
                var missing=current.stream().filter(i->i.latestBatchId()==null).map(MigrationPreviewItem::sourcePk).toList();
                if (missing.isEmpty()) break;
                String priorPurpose=version==1?"NATIVE_":"NATIVE_V"+version+"_";
                String priorKey=version==1?"DPPMS-NATIVE:":"DPPMS-NATIVE-V"+version+":";
                var older=evidence.previewBatch(new MigrationBatchPreviewCommand(tenantId,"PROJ",
                        priorPurpose+snapshotId+"_"+domain,priorKey+snapshotId+":"+domain,
                        "DPPMS",records.getFirst().getSourceTable(),
                        requests.stream().filter(r->missing.contains(r.sourcePk())).toList())).items();
                var previous=new HashMap<String,MigrationPreviewItem>();older.forEach(i->previous.put(i.sourcePk(),i));
                current=current.stream().map(i->i.latestBatchId()==null?previous.getOrDefault(i.sourcePk(),i):i).toList();
            }
            items.addAll(current);
        }
        Map<Long, Payload> payloads = new HashMap<>();
        for (Long batchId : items.stream().map(MigrationPreviewItem::latestBatchId).filter(Objects::nonNull).distinct().toList()) {
            Long after = null;
            do {
                var page = evidence.pageSourceRecords(new MigrationSourceRecordPageQuery(tenantId, batchId, after, 500));
                for (var fact : page.records()) {
                    // Other consumers may use this source identity; only recognize our typed snapshot provenance.
                    var payload = JsonUtils.parseObject(fact.sourcePayloadJson(), Map.class);
                    if (payload != null && payload.containsKey("legacyRecordId") && payload.containsKey("snapshotId")) {
                        payloads.put(fact.sourceRecordId(), readPayload(payload));
                    }
                }
                after = page.nextAfterSourceRecordId();
            } while (after != null);
        }
        var originals = new HashMap<String, Long>();
        Map<String,String> originalKeys = new HashMap<>();
        records.forEach(r -> {
            String pk=sourcePk(sourceProjectKey,snapshotId,r.getSourceKey()); originals.put(pk,r.getId()); originalKeys.put(pk,r.getSourceKey());
        });
        for (var item : items) {
            var payload = payloads.get(item.latestSourceRecordId());
            boolean belongs = payload != null && Objects.equals(payload.snapshotId(), snapshotId)
                    && Objects.equals(payload.legacyRecordId(), originals.get(item.sourcePk()));
            var classification = belongs ? item.classification().name() : "NOT_MIGRATED";
            String reason = belongs ? payload.retentionReason() : null;
            var targets = belongs ? item.targets().stream().filter(t -> "MAPPED".equals(t.resultType()))
                    .map(t -> new ExternalTargetMapping(t.targetContext(), t.targetObjectType(), t.targetTable(),
                            t.targetId(), t.targetRole(), t.targetSequence())).toList() : List.<ExternalTargetMapping>of();
            result.put(originalKeys.get(item.sourcePk()), new RowStatus(classification, reason, LegacyNativeReasons.text(reason), targets,
                    belongs?payload.scopeBaseline():null,belongs?payload.installationBaseline():null,belongs?payload.realProductBaseline():null,belongs?payload.associatedListBaseline():null));
        }
        return result;
    }
    private static String associatedListPayload(ProjectLegacyRecordDO record,Long snapshotId,String reason,String baseline) {
        return "{\"legacyRecordId\":"+record.getId()+",\"snapshotId\":"+snapshotId
                +",\"retentionReason\":"+JsonUtils.toJsonString(reason)+",\"values\":"+record.getPayloadJson()
                +",\"associatedListBaseline\":"+JsonUtils.toJsonString(baseline)+"}";
    }
    private static String realProductPayload(ProjectLegacyRecordDO record,Long snapshotId,String reason,String baseline) {
        return "{\"legacyRecordId\":"+record.getId()+",\"snapshotId\":"+snapshotId
                +",\"retentionReason\":"+JsonUtils.toJsonString(reason)+",\"values\":"+record.getPayloadJson()
                +",\"realProductBaseline\":"+JsonUtils.toJsonString(baseline)+"}";
    }
    static String installationPayload(ProjectLegacyRecordDO record,Long snapshotId,String reason,
            cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.Baseline baseline) {
        // The immutable raw JSON explicitly retains all original NULL columns.
        return "{\"legacyRecordId\":"+record.getId()+",\"snapshotId\":"+snapshotId
                +",\"retentionReason\":"+JsonUtils.toJsonString(reason)+",\"values\":"+record.getPayloadJson()
                +",\"installationBaseline\":"+JsonUtils.toJsonString(baseline)+"}";
    }
    private static Payload readPayload(Map<?,?> raw) {
        // V2 staging used ISO dates; the platform's LocalDateTime reader only accepts epoch milliseconds.
        // Decode immutable audit metadata explicitly so a string cannot silently become epoch zero.
        var withoutAudit=new LinkedHashMap<>(raw);
        Object audit=withoutAudit.remove("scopeBaseline");
        var payload=JsonUtils.parseObject(JsonUtils.toJsonString(withoutAudit),Payload.class);
        if(audit==null)return payload;
        if(!(audit instanceof Map<?,?> fields))throw new IllegalArgumentException("DPPMS_PRODUCT_SCOPE_AUDIT_EVIDENCE_INVALID");
        Long id=fields.get("id")==null?null:Long.valueOf(fields.get("id").toString());
        var baseline=new Baseline(id,(String)fields.get("creator"),auditTime(fields.get("createTime")),
                (String)fields.get("updater"),auditTime(fields.get("updateTime")));
        return new Payload(payload.legacyRecordId(),payload.snapshotId(),payload.retentionReason(),payload.values(),baseline,payload.installationBaseline(),payload.realProductBaseline(),payload.associatedListBaseline());
    }
    private static LocalDateTime auditTime(Object value) {
        if(value==null)return null;
        if(value instanceof Long || value instanceof Integer)
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(((Number)value).longValue()),ZoneId.systemDefault());
        if(value instanceof String text) {
            try{return LocalDateTime.parse(text);}
            catch(java.time.format.DateTimeParseException invalid) {
                throw new IllegalArgumentException("DPPMS_PRODUCT_SCOPE_AUDIT_DATE_INVALID");
            }
        }
        throw new IllegalArgumentException("DPPMS_PRODUCT_SCOPE_AUDIT_DATE_INVALID");
    }
    static String identity(String domain, String key) { return domain + ":" + key; }
    // Evidence belongs to one project and immutable snapshot. Later snapshots and shared questionnaire
    // definitions must preserve the earlier reference's reconciliation status.
    static String sourcePk(String projectKey,Long snapshotId,String sourceKey) {
        String key=projectKey+":"+snapshotId+":"+sourceKey;
        return key.length()<=128 ? key : "REF:"+DigestUtil.sha256Hex(key);
    }
}
