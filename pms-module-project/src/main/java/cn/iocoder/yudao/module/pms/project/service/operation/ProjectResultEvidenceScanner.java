package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.*;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.project.domain.template.ResultEvidencePolicy.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Objects;

/** 每页在同一已提交结果边界下复核；边界变化先补采，不将不同时间的页拼成成功证据。 */
@Service
@RequiredArgsConstructor
public class ProjectResultEvidenceScanner {
    private static final int PAGE_SIZE = 100;
    private final ProjectResultSubscriptionContext contexts;
    private final ProjectResultEvidenceConsistency consistency;
    private final ProjectBusinessResultSources sources;
    private final ResultSubscriptionCandidateMapper candidates;
    private final ResultEvidenceMapper evidence;
    private final PlatformBusinessEventApi outbox;

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void process(ResultEvidenceScanEvent event) {
        var context = contexts.lock(event.target());
        if (context == null) return;
        var row = context.subscription();
        if (event.subscriptionVersion() > row.getVersion()) throw new IllegalArgumentException("EVIDENCE_FUTURE_CHECKPOINT");
        if (event.subscriptionVersion() != row.getVersion() || !"LIVE".equals(row.getPhase())) return;
        var boundary = consistency.lock(row);
        if (boundary.sequence() < row.getProcessedSequence()) throw new IllegalStateException("EVIDENCE_BOUNDARY_CHANGED");
        if (boundary.sequence() != row.getProcessedSequence()) {
            var wakeup = ResultSubscriptionWakeup.forCause(row, "evidence-refresh:" + row.getVersion() + ":" + boundary.sequence());
            outbox.append("ResultSubscription",row.getId().toString(),new BusinessEvent(wakeup.eventId(),ResultSubscriptionWakeup.EVENT_TYPE,JsonUtils.toJsonString(wakeup)));
            return;
        }
        var definition = ResultSubscriptionContract.read(row.getConfiguration());
        var type = ResultSubscriptionContract.type(definition);
        var descriptor = sources.descriptor(type);
        if (descriptor == null) throw new IllegalStateException("RESULT_SOURCE_UNAVAILABLE");
        if ("PINNED_RESULT".equals(definition.policy().acquisition()) && !descriptor.exactLookup()
                || "HISTORICAL_FACT".equals(definition.policy().validity()) && !descriptor.historicalLookup())
            throw new IllegalStateException("EVIDENCE_SOURCE_CAPABILITY_CHANGED");
        var scan = evidence.selectScanForUpdate(new ResultEvidenceMapper.ScanIdentity(row.getTenantId(),row.getProjectId(),row.getId(),row.getVersion()));
        if (scan == null) {
            scan = new ResultEvidenceScanDO(); scan.setId(IdWorker.getId()); scan.setTenantId(row.getTenantId()); scan.setProjectId(row.getProjectId());
            scan.setSubscriptionId(row.getId()); scan.setSubscriptionVersion(row.getVersion()); scan.setThroughSequence(boundary.sequence());
            scan.setAfterCandidateId(0L); scan.setAccumulator(JsonUtils.toJsonString(Accumulator.empty())); scan.setStatus("COLLECTING"); scan.setVersion(0);
            if (evidence.insertScan(scan) != 1) throw new IllegalStateException("EVIDENCE_SCAN_CREATE_CONFLICT");
        }
        requireScan(scan,row);
        if (!"COLLECTING".equals(scan.getStatus())) return;
        var page = candidates.selectPage(new ResultSubscriptionCandidateMapper.Page(row.getTenantId(),row.getProjectId(),row.getId(),scan.getAfterCandidateId(),PAGE_SIZE+1));
        if (page == null || page.size() > PAGE_SIZE+1) throw new IllegalStateException("EVIDENCE_CANDIDATE_PAGE_INVALID");
        long after = scan.getAfterCandidateId();
        var qualifications = new ArrayList<Qualification>();
        for (var candidate : page.subList(0,Math.min(page.size(),PAGE_SIZE))) {
            if (candidate == null || candidate.getId() == null || candidate.getId() <= after
                    || !Objects.equals(row.getTenantId(),candidate.getTenantId()) || !Objects.equals(row.getProjectId(),candidate.getProjectId())
                    || !Objects.equals(row.getId(),candidate.getSubscriptionId()) || candidate.getObservedSequence() == null
                    || (candidate.getObservedSequence() < 0 || candidate.getObservedSequence() > boundary.sequence())) throw new IllegalStateException("EVIDENCE_CANDIDATE_SCOPE_MISMATCH");
            after = candidate.getId();
            var observed = sources.inspect(new Query(row.getTenantId(),row.getProjectId(),type,candidate.getObjectId(),descriptor.exactLookup()?candidate.getResultId():null));
            if (observed != null && observed.result() != null && (!Objects.equals(row.getTenantId(),observed.result().tenantId())
                    || !Objects.equals(row.getProjectId(),observed.result().projectId())))
                throw new IllegalStateException("EVIDENCE_RESULT_SCOPE_MISMATCH");
            var qualification = ResultEvidencePolicy.qualify(definition,row.getBaselineSequence(),boundary.sequence(),
                    new Candidate(candidate.getObjectId(),candidate.getResultId(),candidate.getFormationSequence(),observed));
            if (qualification.eligibility() == Eligibility.UNAVAILABLE) throw new IllegalStateException("EVIDENCE_SOURCE_UNAVAILABLE");
            qualifications.add(qualification);
            var item = new ResultEvidenceItemDO(); item.setId(IdWorker.getId()); item.setTenantId(row.getTenantId()); item.setProjectId(row.getProjectId());
            item.setScanId(scan.getId()); item.setCandidateId(candidate.getId()); item.setObjectId(candidate.getObjectId()); item.setResultId(candidate.getResultId());
            item.setFormationSequence(candidate.getFormationSequence()); item.setEligibility(qualification.eligibility().name()); item.setReason(qualification.reason() == null ? null : qualification.reason().substring(0,Math.min(qualification.reason().length(),128)));
            item.setObservation(JsonUtils.toJsonString(observed));
            if (evidence.insertItem(item) != 1) throw new IllegalStateException("EVIDENCE_ITEM_WRITE_CONFLICT");
        }
        var accumulated = ResultEvidencePolicy.accumulate(definition,JsonUtils.parseObject(scan.getAccumulator(),Accumulator.class),qualifications);
        boolean complete = page.size() <= PAGE_SIZE;
        var decision = ResultEvidencePolicy.decide(definition,accumulated,complete);
        if (evidence.advance(new ResultEvidenceMapper.Progress(row.getTenantId(),row.getProjectId(),scan.getId(),scan.getVersion(),after,
                JsonUtils.toJsonString(accumulated),decision.status().name())) != 1) throw new IllegalStateException("EVIDENCE_SCAN_ADVANCE_CONFLICT");
        if (complete) {
            var evaluated = ResultEvidenceEvaluatedEvent.create(event.target(),scan);
            outbox.append("ResultEvidence",scan.getId().toString(),new BusinessEvent(evaluated.eventId(),ResultEvidenceEvaluatedEvent.EVENT_TYPE,JsonUtils.toJsonString(evaluated)));
        } else {
            var continuation = ResultEvidenceScanEvent.create(row,row.getVersion(),"page:"+after);
            outbox.append("ResultEvidence",scan.getId().toString(),new BusinessEvent(continuation.eventId(),ResultEvidenceScanEvent.EVENT_TYPE,JsonUtils.toJsonString(continuation)));
        }
    }

    static void requireScan(ResultEvidenceScanDO scan, ResultSubscriptionDO row) {
        try { ResultEvidencePolicy.Status.valueOf(scan.getStatus()); }
        catch (RuntimeException invalid) { throw new IllegalStateException("EVIDENCE_SCAN_STATUS_INVALID",invalid); }
        if (!Objects.equals(row.getTenantId(),scan.getTenantId()) || !Objects.equals(row.getProjectId(),scan.getProjectId())
                || !Objects.equals(row.getId(),scan.getSubscriptionId()) || !Objects.equals(row.getVersion(),scan.getSubscriptionVersion())
                || !Objects.equals(row.getProcessedSequence(),scan.getThroughSequence()) || scan.getId() == null || scan.getId() <= 0
                || scan.getAfterCandidateId() == null || scan.getAfterCandidateId() < 0 || scan.getVersion() == null || scan.getVersion() < 0)
            throw new IllegalStateException("EVIDENCE_SCAN_IDENTITY_MISMATCH");
    }
}
