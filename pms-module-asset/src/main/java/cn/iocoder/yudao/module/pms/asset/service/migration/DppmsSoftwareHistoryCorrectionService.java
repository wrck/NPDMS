package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi.*;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareHistoryCorrectionApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** One-time, all-column guarded transfer. Ordinary synchronization cannot invoke the correction path. */
@Service @RequiredArgsConstructor
public class DppmsSoftwareHistoryCorrectionService implements DppmsSoftwareHistoryCorrectionApi {
    private final DeviceSoftwareObservationMapper legacyRecords;
    private final DeviceSoftwareChangeBatchMapper batches;
    private final DeviceSoftwareVersionRecordMapper records;
    private final DppmsAssetSourceHistoryService history;
    private final ProjectDeviceAssignmentGuardApi projectGuard;

    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Baseline> preview(Command source) {
        authorize(source);
        // Completed transfers are immutable. Reject before touching the retired V4 schema.
        for (var row:source.observations()) {
            if (row.mappedRecordId()==null) fail("RECORD_BINDING_INVALID");
            if (records.selectForUpdate(new DeviceSoftwareVersionRecordLockQuery(source.tenantId(),row.mappedRecordId()))!=null)
                fail("TARGET_ALREADY_EXISTS");
        }
        var result=new ArrayList<Baseline>();
        Set<Long> ids=new HashSet<>();
        for (var row:source.batches()) {
            if (row.mappedRecordId()==null || !ids.add(row.mappedRecordId())) fail("BATCH_BINDING_INVALID");
            var old=batches.selectLegacyForUpdate(new DeviceSoftwareLegacyRecordQuery(source.tenantId(),source.projectId(),row.mappedRecordId()));
            validateBatchSource(source,row,old);
            result.add(new Baseline("software-changes",row.sourceId(),old.getId(),JsonUtils.toJsonString(old)));
        }
        ids.clear();
        for (var row:source.observations()) {
            if (row.mappedRecordId()==null || !ids.add(row.mappedRecordId())) fail("RECORD_BINDING_INVALID");
            var old=legacyRecords.selectLegacyForUpdate(new DeviceSoftwareLegacyRecordQuery(source.tenantId(),source.projectId(),row.mappedRecordId()));
            validateRecordSource(source,row,old);
            result.add(new Baseline("software-versions",row.sourceId(),old.getId(),JsonUtils.toJsonString(old)));
        }
        return List.copyOf(result);
    }

    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<NativeEntityMigrationRow> correct(Correction command) {
        var actual=preview(command.source());
        if (command.baselines()==null || command.baselines().size()!=actual.size()) fail("BASELINE_INCOMPLETE");
        Map<String,Baseline> expected=new HashMap<>();
        for (var row:command.baselines()) {
            if (row==null || row.fieldsJson()==null || expected.put(row.domain()+":"+row.sourceId(),row)!=null)
                fail("BASELINE_INVALID");
        }
        // Check every existing field before the first mutation. NULL, empty strings and JSON null remain distinct.
        for (var row:actual) {
            var before=expected.get(row.domain()+":"+row.sourceId());
            if (before==null || !Objects.equals(before.targetId(),row.targetId())
                    || !Objects.equals(JsonUtils.parseTree(before.fieldsJson()),JsonUtils.parseTree(row.fieldsJson())))
                fail("TARGET_CHANGED");
        }
        var source=command.source();var result=new ArrayList<NativeEntityMigrationRow>();
        Map<Long,Long> batchIds=new HashMap<>();
        for (var row:source.batches()) {
            result.add(history.importCorrectedBatch(source,row));batchIds.put(row.sourceId(),row.mappedRecordId());
        }
        for (var row:source.observations()) {
            var batchId=row.logId()==null?null:batchIds.get(row.logId());
            if (row.logId()!=null && batchId==null) fail("BATCH_BINDING_MISSING");
            result.add(history.importCorrectedObservation(source,row.withMapping(row.mappedRecordId(),batchId,row.extensionDefinitionId())));
        }
        return List.copyOf(result);
    }

    private void authorize(Command source) {
        if (source==null || !Objects.equals(source.tenantId(),TenantContextHolder.getRequiredTenantId())
                || source.actorId()==null || source.actorId()<=0 || source.projectId()==null
                || source.sourceProjectKey()==null || source.sourceReadAt()==null || source.observations()==null
                || source.batches()==null || source.factories()==null || !source.factories().isEmpty()) fail("COMMAND_INVALID");
        var guard=projectGuard.validate(new ProjectDeviceAssignmentGuardQuery(source.tenantId(),source.projectId(),source.actorId()));
        if (guard==null || !guard.assignable()) fail("PROJECT_FORBIDDEN");
    }

    private void validateRecordSource(Command command,SoftwareObservation source,DeviceSoftwareObservationDO old) {
        if (old==null || !"DPPMS".equals(old.getSourceSystem()) || Boolean.TRUE.equals(old.getDeleted())
                || !Objects.equals(command.sourceProjectKey(),old.getSourceProjectKey())
                || !Objects.equals(source.sourceId(),old.getSourceId()) || !Objects.equals(source.sourceProjectId(),old.getSourceProjectId())
                || !Objects.equals(old.getSourceKey(),String.valueOf(source.sourceId())) || old.getSourceChecksum()==null)
            fail("SOURCE_IDENTITY_INVALID");
        var raw=new SoftwareObservation(old.getSourceId(),old.getSourceProjectId(),old.getLogId(),old.getContractNo(),old.getItemCode(),old.getBarCode(),
                old.getConp(),old.getConpType(),old.getConpSeries(),old.getConpMark(),old.getConpBak(),old.getConpChange(),
                old.getCpld(),old.getCpldBak(),old.getCpldChange(),old.getBoot(),old.getBootBak(),old.getBootChange(),old.getPcb(),old.getPcbBak(),old.getPcbChange(),
                old.getExecuteTime(),old.getDatastate(),old.getSourceCreatedAt(),old.getSourceCreatedBy(),old.getSourceUpdatedAt(),old.getSourceUpdatedBy(),source.customInfo());
        if (!Objects.equals(raw,source.withMapping(null,null,null))
                || !jsonSame(source.customInfo(),old.getCustomInfo())) fail("ORIGINAL_FIELDS_CHANGED");
    }
    private void validateBatchSource(Command command,SoftwareBatch source,DeviceSoftwareLegacyBatchDO old) {
        if (old==null || !"DPPMS".equals(old.getSourceSystem()) || Boolean.TRUE.equals(old.getDeleted())
                || !Objects.equals(command.sourceProjectKey(),old.getSourceProjectKey())
                || !Objects.equals(old.getSourceKey(),String.valueOf(source.sourceId())) || old.getSourceChecksum()==null)
            fail("BATCH_SOURCE_IDENTITY_INVALID");
        var raw=new SoftwareBatch(old.getSourceId(),old.getSourceProjectId(),old.getChangeVersion(),old.getChangeRemark(),old.getLatest(),
                old.getSourceCreatedBy(),old.getSourceCreatedAt(),old.getSourceUpdatedBy(),old.getSourceUpdatedAt(),source.mappedRecordId());
        if (!Objects.equals(raw,source)) fail("BATCH_ORIGINAL_FIELDS_CHANGED");
    }
    private static boolean jsonSame(String a,String b) {
        return Objects.equals(a,b) || a!=null && b!=null && Objects.equals(JsonUtils.parseTree(a),JsonUtils.parseTree(b));
    }
    private static void fail(String reason) { throw new IllegalArgumentException("DPPMS_SOFTWARE_CORRECTION_"+reason); }
}
