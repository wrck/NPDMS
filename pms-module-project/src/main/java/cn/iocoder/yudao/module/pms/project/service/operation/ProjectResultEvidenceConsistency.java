package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.ResultSubscriptionDO;
import cn.iocoder.yudao.module.pms.project.domain.template.ResultSubscriptionContract;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** 仅对原事务覆盖已审计的本地Owner使用提交通道作屏障；远程单次查询不能声明强一致。 */
@Service
@RequiredArgsConstructor
public class ProjectResultEvidenceConsistency {
    private final ProjectBusinessResultSources sources;
    private final ProjectBusinessResultJournal journal;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public ProjectBusinessResultJournal.Boundary lock(ResultSubscriptionDO subscription) {
        var type = ResultSubscriptionContract.type(ResultSubscriptionContract.read(subscription.getConfiguration()));
        if (!sources.commitBarrierSupported(type)) throw new IllegalStateException("RESULT_COMMIT_BARRIER_UNAVAILABLE");
        var boundary = journal.capture(subscription.getTenantId(), subscription.getProjectId(), type);
        if (boundary == null || !Objects.equals(subscription.getChannelId(), boundary.channel().id())
                || !Objects.equals(subscription.getTenantId(), boundary.channel().tenantId())
                || !Objects.equals(subscription.getProjectId(), boundary.channel().projectId()) || !type.equals(boundary.channel().type()))
            throw new IllegalStateException("EVIDENCE_CHANNEL_MISMATCH");
        return boundary;
    }
}
