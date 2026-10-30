package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChange.Channel;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Query;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultInventorySource.InventoryQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.ResultSubscriptionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultSubscriptionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultSubscriptionMapper.Checkpoint;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.project.domain.template.ResultSubscriptionCheckpoint.Phase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

/** 每次只处理一页；候选、位置与后继唤醒同事务，某个订阅失败不回滚其他目标。 */
@Service
@RequiredArgsConstructor
public class ProjectResultSubscriptionWorker {
    private static final int PAGE_SIZE = 100;
    private final ProjectResultSubscriptionContext contexts;
    private final ProjectBusinessResultSources sources;
    private final ProjectBusinessResultJournal journal;
    private final ProjectResultSubscriptionCandidates candidates;
    private final ResultSubscriptionMapper subscriptions;
    private final PlatformBusinessEventApi outbox;

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void process(ResultSubscriptionWakeup event) {
        var context = contexts.lock(event);
        if (context == null) return;
        var row = context.subscription();
        var definition = ResultSubscriptionContract.read(row.getConfiguration());
        var checkpoint = checkpoint(row);
        if (checkpoint.phase() == Phase.LIVE) {
            var through = boundary(row);
            if (through.sequence() == checkpoint.processedSequence()) return;
            save(row, checkpoint.beginChanges(through.sequence()));
            return;
        }
        if (checkpoint.phase() == Phase.INVENTORY) {
            if ("PINNED_RESULT".equals(definition.policy().acquisition())) {
                var observed = sources.inspect(new Query(row.getTenantId(), row.getProjectId(), ResultSubscriptionContract.type(definition),
                        null, definition.policy().pinnedResultId()));
                candidates.inventory(row, List.of(observed));
                save(row, checkpoint.inventoryComplete(boundary(row).sequence()));
            } else {
                var query = new InventoryQuery(row.getTenantId(), row.getProjectId(), ResultSubscriptionContract.type(definition),
                        "HISTORICAL_FACT".equals(definition.policy().validity()),
                        "OBJECTS".equals(definition.scope().mode()) ? definition.scope().objectIds() : null,
                        checkpoint.inventoryCursor(), PAGE_SIZE);
                var page = sources.inventory(query);
                candidates.inventory(row, page.observations());
                var next = page.observations().isEmpty() ? checkpoint : checkpoint.inventoryPage(page.nextCursor());
                save(row, page.complete() ? next.inventoryComplete(boundary(row).sequence()) : next);
            }
            return;
        }
        var channel = new Channel(row.getChannelId(), row.getTenantId(), row.getProjectId(), ResultSubscriptionContract.type(definition));
        var through = new ProjectBusinessResultJournal.Boundary(channel, checkpoint.throughSequence());
        var page = journal.read(through, checkpoint.processedSequence(), PAGE_SIZE);
        if (!through.equals(page.through()) || page.complete() != (page.nextSequence() == through.sequence()))
            throw new IllegalStateException("SUBSCRIPTION_CHANGE_PAGE_INVALID");
        for (var change : page.changes()) candidates.change(row, change);
        save(row, checkpoint.changesPage(page.nextSequence()));
    }

    private ProjectBusinessResultJournal.Boundary boundary(ResultSubscriptionDO row) {
        var type = ResultSubscriptionContract.type(ResultSubscriptionContract.read(row.getConfiguration()));
        var boundary = journal.capture(row.getTenantId(), row.getProjectId(), type);
        if (boundary == null || !Objects.equals(row.getChannelId(), boundary.channel().id())
                || !Objects.equals(row.getTenantId(), boundary.channel().tenantId())
                || !Objects.equals(row.getProjectId(), boundary.channel().projectId()) || !type.equals(boundary.channel().type()))
            throw new IllegalStateException("SUBSCRIPTION_CHANNEL_CHANGED");
        return boundary;
    }

    static ResultSubscriptionCheckpoint checkpoint(ResultSubscriptionDO row) {
        if (row.getBaselineSequence() == null || row.getProcessedSequence() == null || row.getVersion() == null || row.getVersion() < 0)
            throw new IllegalStateException("SUBSCRIPTION_CHECKPOINT_INVALID");
        return new ResultSubscriptionCheckpoint(Phase.valueOf(row.getPhase()), row.getBaselineSequence(), row.getInventoryCursor(),
                row.getProcessedSequence(), row.getThroughSequence());
    }

    private void save(ResultSubscriptionDO row, ResultSubscriptionCheckpoint next) {
        if (subscriptions.checkpoint(new Checkpoint(row.getTenantId(), row.getProjectId(), row.getId(), row.getVersion(),
                next.phase().name(), next.inventoryCursor(), next.processedSequence(), next.throughSequence())) != 1)
            throw new IllegalStateException("SUBSCRIPTION_CHECKPOINT_CONFLICT");
        if (next.phase() == Phase.LIVE) {
            var event = ResultEvidenceScanEvent.create(row, Math.addExact(row.getVersion(), 1), "checkpoint");
            outbox.append("ResultSubscription", row.getId().toString(),
                    new BusinessEvent(event.eventId(), ResultEvidenceScanEvent.EVENT_TYPE, JsonUtils.toJsonString(event)));
        } else {
            var event = ResultSubscriptionWakeup.forCause(row, "checkpoint:" + Math.addExact(row.getVersion(), 1));
            outbox.append("ResultSubscription", row.getId().toString(),
                    new BusinessEvent(event.eventId(), ResultSubscriptionWakeup.EVENT_TYPE, JsonUtils.toJsonString(event)));
        }
    }
}
