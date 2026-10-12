package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareLegacyEventWithdrawalApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceSoftwareLegacyEventWithdrawalDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.*;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DppmsSoftwareLegacyEventWithdrawalService implements DppmsSoftwareLegacyEventWithdrawalApi {
    private final DeviceNetworkVersionEventMapper events;
    private final DeviceSoftwareVersionRecordMapper records;
    private final DeviceSoftwareChangeBatchMapper batches;
    private final DppmsAssetSourceHistoryApi history;
    private final ProjectDeviceAssignmentGuardApi projects;

    @Override
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Baseline> preview(DppmsAssetSourceHistoryApi.Command source,List<Identity> identities) {
        var queries=validate(source,identities);
        var result=new ArrayList<Baseline>();
        for (int i=0;i<identities.size();i++) {
            var row=events.selectLegacyWithdrawalForUpdate(queries.get(i));
            validateEvent(row,queries.get(i));
            result.add(new Baseline(identities.get(i),JsonUtils.toJsonString(row)));
        }
        return List.copyOf(result);
    }

    @Override
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public void withdraw(Withdrawal command) {
        if(command==null || command.baselines()==null) fail("BASELINE_REQUIRED");
        var expected=command.baselines();
        if(expected.stream().anyMatch(Objects::isNull)) fail("BASELINE_INVALID");
        var actual=preview(command.source(),expected.stream().map(Baseline::identity).toList());
        // Lock and compare every original column before the first deletion.
        for(int i=0;i<actual.size();i++) {
            if(expected.get(i).fieldsJson()==null || !Objects.equals(
                    JsonUtils.parseTree(expected.get(i).fieldsJson()),JsonUtils.parseTree(actual.get(i).fieldsJson())))
                fail("EVENT_CHANGED");
        }
        var sources=new HashMap<Long,DppmsAssetSourceHistoryApi.SoftwareObservation>();
        command.source().observations().forEach(row->sources.put(row.sourceId(),row));
        for(var row:expected) {
            var original=sources.get(row.identity().sourceId());
            if(events.withdrawLegacyEvent(query(command.source(),row.identity(),original))!=1) fail("WITHDRAWAL_FAILED");
        }
    }

    @Override
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public void verifyWithdrawn(DppmsAssetSourceHistoryApi.Command source,List<Identity> identities) {
        for(var query:validate(source,identities)) {
            if(events.selectLegacyWithdrawalForUpdate(query)!=null || events.selectSourceIdentity(
                    new DeviceNetworkVersionSourceQuery(query.tenantId(),"DPPMS",query.sourceDeviceKey(),query.sourceEventKey()))!=null)
                fail("EVENT_REAPPEARED");
        }
    }

    private List<DeviceSoftwareLegacyEventWithdrawalQuery> validate(DppmsAssetSourceHistoryApi.Command source,List<Identity> identities) {
        if(source==null || !Objects.equals(source.tenantId(),TenantContextHolder.getRequiredTenantId())
                || source.actorId()==null || source.actorId()<=0 || source.projectId()==null
                || source.observations()==null || source.batches()==null || source.factories()==null || !source.factories().isEmpty()
                || identities==null || identities.isEmpty()) fail("COMMAND_INVALID");
        var access=projects.validate(new ProjectDeviceAssignmentGuardQuery(source.tenantId(),source.projectId(),source.actorId()));
        if(access==null || !access.assignable()) fail("PROJECT_FORBIDDEN");
        var originals=new HashMap<Long,DppmsAssetSourceHistoryApi.SoftwareObservation>();
        for(var row:source.observations()) {
            if(row.sourceId()==null || row.mappedRecordId()==null || originals.put(row.sourceId(),row)!=null
                    || records.selectForUpdate(new DeviceSoftwareVersionRecordLockQuery(source.tenantId(),row.mappedRecordId()))==null)
                fail("FORMAL_CARRY_REQUIRED");
        }
        for(var row:source.batches()) {
            if(row.mappedRecordId()==null || batches.selectRecordForUpdate(new DeviceSoftwareBatchIdentityQuery(
                    source.tenantId(),source.projectId(),row.mappedRecordId()))==null) fail("FORMAL_BATCH_REQUIRED");
        }
        // Reuse strict 31-column, batch-audit and eight-extension replay validation; no new IDs are allowed above.
        history.importSourceHistory(source);
        var ids=new HashSet<Long>();var keys=new HashSet<Long>();var result=new ArrayList<DeviceSoftwareLegacyEventWithdrawalQuery>();
        for(var identity:identities) {
            if(identity==null || identity.eventId()==null || identity.eventId()<=0 || !ids.add(identity.eventId())
                    || !keys.add(identity.sourceId())) fail("IDENTITY_INVALID");
            var original=originals.get(identity.sourceId());
            if(original==null || !Objects.equals(original.mappedRecordId(),identity.recordId())
                    || original.barCode()==null || original.barCode().isBlank()) fail("IDENTITY_NOT_CARRIED");
            result.add(query(source,identity,original));
        }
        return List.copyOf(result);
    }

    private static DeviceSoftwareLegacyEventWithdrawalQuery query(DppmsAssetSourceHistoryApi.Command source,Identity identity,
            DppmsAssetSourceHistoryApi.SoftwareObservation original) {
        return new DeviceSoftwareLegacyEventWithdrawalQuery(source.tenantId(),identity.eventId(),
                "pm_project_soft_version:"+identity.sourceId(),original.barCode());
    }
    private static void validateEvent(DeviceSoftwareLegacyEventWithdrawalDO row,DeviceSoftwareLegacyEventWithdrawalQuery query) {
        if(row==null || !Objects.equals(row.getTenantId(),query.tenantId()) || !Objects.equals(row.getId(),query.eventId())
                || !"DPPMS".equals(row.getSourceSystem()) || !Objects.equals(row.getSourceEventKey(),query.sourceEventKey())
                || !Objects.equals(row.getSourceDeviceKey(),query.sourceDeviceKey()) || Boolean.TRUE.equals(row.getDeleted()))
            fail("EVENT_IDENTITY_CHANGED");
    }
    private static void fail(String reason) {throw new IllegalArgumentException("DPPMS_SOFTWARE_EVENT_WITHDRAWAL_"+reason);}
}
