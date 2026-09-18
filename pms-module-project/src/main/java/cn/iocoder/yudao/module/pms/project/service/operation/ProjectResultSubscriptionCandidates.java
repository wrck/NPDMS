package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChange;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultSubscriptionCandidateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultSubscriptionCandidateMapper.*;
import cn.iocoder.yudao.module.pms.project.domain.template.ResultSubscriptionContract;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionConfiguration.Subscription;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

/** 一页候选与检查点使用同一事务；候选并不授予节点完成资格。 */
@Service
@RequiredArgsConstructor
public class ProjectResultSubscriptionCandidates {
    private final ResultSubscriptionCandidateMapper mapper;
    private final ProjectBusinessResultSources sources;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void inventory(ResultSubscriptionDO subscription, List<Observation> observations) {
        var definition = ResultSubscriptionContract.read(subscription.getConfiguration());
        for (var observation : observations) {
            requireAvailableSource(observation);
            if (observation.result() != null && matches(subscription, definition, observation.result()))
                merge(subscription, observation, subscription.getBaselineSequence(), null);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void change(ResultSubscriptionDO subscription, BusinessResultChange change) {
        var definition = ResultSubscriptionContract.read(subscription.getConfiguration());
        var type = ResultSubscriptionContract.type(definition);
        if (!Objects.equals(subscription.getTenantId(), change.channel().tenantId())
                || !Objects.equals(subscription.getProjectId(), change.channel().projectId())
                || !Objects.equals(subscription.getChannelId(), change.channel().id()) || !type.equals(change.channel().type()))
            throw new IllegalStateException("SUBSCRIPTION_CHANGE_SCOPE_MISMATCH");
        var observation = change.observation();
        requireAvailableSource(observation);
        if (observation.result() != null) {
            if (matches(subscription, definition, observation.result()))
                merge(subscription, observation, change.sequence(), change.formation() ? change.sequence() : null);
        } else {
            // 缺失结果仍可能使已有证据失效；来源负责解释原对象/修订身份，不用事件ID伪造结果。
            var query = sources.changeQuery(type, change.source());
            if (!Objects.equals(subscription.getTenantId(), query.tenantId())
                    || !Objects.equals(subscription.getProjectId(), query.projectId()) || !type.equals(query.type()))
                throw new IllegalStateException("SUBSCRIPTION_CHANGE_SCOPE_MISMATCH");
            mapper.invalidate(new Invalidation(subscription.getTenantId(), subscription.getProjectId(), subscription.getId(),
                    query.objectId(), query.resultId(), change.sequence(), JsonUtils.toJsonString(observation)));
        }
    }

    private void merge(ResultSubscriptionDO subscription, Observation observation, long sequence, Long formation) {
        var result = observation.result();
        var identity = new Identity(subscription.getTenantId(), subscription.getProjectId(), subscription.getId(), result.objectId(), result.resultId());
        var previous = mapper.selectIdentityForUpdate(identity);
        if (previous == null) {
            var row = new ResultSubscriptionCandidateDO();
            row.setId(IdWorker.getId()); row.setTenantId(subscription.getTenantId()); row.setProjectId(subscription.getProjectId());
            row.setSubscriptionId(subscription.getId()); row.setObjectId(result.objectId()); row.setResultId(result.resultId());
            row.setFormationSequence(formation); row.setObservedSequence(sequence); row.setObservation(JsonUtils.toJsonString(observation));
            if (mapper.insert(row) != 1) throw new IllegalStateException("SUBSCRIPTION_CANDIDATE_WRITE_CONFLICT");
            return;
        }
        if (!Objects.equals(previous.getTenantId(), subscription.getTenantId())
                || !Objects.equals(previous.getProjectId(), subscription.getProjectId())
                || !Objects.equals(previous.getSubscriptionId(), subscription.getId())
                || !Objects.equals(previous.getObjectId(), result.objectId()) || !Objects.equals(previous.getResultId(), result.resultId())
                || previous.getObservedSequence() == null || previous.getObservedSequence() < 0)
            throw new IllegalStateException("SUBSCRIPTION_CANDIDATE_IDENTITY_MISMATCH");
        if (formation != null && previous.getFormationSequence() != null && !formation.equals(previous.getFormationSequence()))
            throw new IllegalStateException("SUBSCRIPTION_FORMATION_CHANGED");
        if (sequence < previous.getObservedSequence()) return;
        if (mapper.observe(new ObservationUpdate(subscription.getTenantId(), subscription.getProjectId(), subscription.getId(), previous.getId(),
                sequence, formation, JsonUtils.toJsonString(observation))) != 1)
            throw new IllegalStateException("SUBSCRIPTION_CANDIDATE_WRITE_CONFLICT");
    }

    private boolean matches(ResultSubscriptionDO subscription, Subscription definition, Result result) {
        if (!Objects.equals(subscription.getTenantId(), result.tenantId()) || !Objects.equals(subscription.getProjectId(), result.projectId())
                || !ResultSubscriptionContract.type(definition).equals(result.type()))
            throw new IllegalStateException("SUBSCRIPTION_RESULT_SCOPE_MISMATCH");
        return ("PROJECT".equals(definition.scope().mode()) || definition.scope().objectIds().contains(result.objectId()))
                && (!"PINNED_RESULT".equals(definition.policy().acquisition()) || definition.policy().pinnedResultId().equals(result.resultId()));
    }
    private void requireAvailableSource(Observation observation) {
        if (observation == null || observation.status() == Status.UNAVAILABLE)
            throw new IllegalStateException("SUBSCRIPTION_RESULT_SOURCE_UNAVAILABLE");
    }
}
