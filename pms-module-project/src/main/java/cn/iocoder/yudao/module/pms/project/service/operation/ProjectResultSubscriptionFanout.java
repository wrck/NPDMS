package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChange;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultSubscriptionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultSubscriptionMapper.ChannelPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** 仅按结果通道分发，操作绑定和页面均不是接收前提；后来安装的订阅由自身库存扫描补采。 */
@Service
@RequiredArgsConstructor
public class ProjectResultSubscriptionFanout {
    private static final int PAGE_SIZE = 100;
    private final ProjectBusinessResultJournal journal;
    private final ResultSubscriptionMapper subscriptions;
    private final PlatformBusinessEventApi outbox;

    @Transactional(rollbackFor = Exception.class)
    public void accept(BusinessResultChange source, long after) {
        if (source == null || after < 0 || !Objects.equals(source.channel().tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("SUBSCRIPTION_FANOUT_SCOPE_INVALID");
        var channel = source.channel();
        var recorded = journal.read(new ProjectBusinessResultJournal.Boundary(channel, source.sequence()), source.sequence()-1, 1);
        if (recorded.changes().size() != 1 || !JsonUtils.parseTree(JsonUtils.toJsonString(source))
                .equals(JsonUtils.parseTree(JsonUtils.toJsonString(recorded.changes().getFirst()))))
            throw new IllegalArgumentException("SUBSCRIPTION_SOURCE_EVENT_MISMATCH");
        var page = subscriptions.selectChannelPage(new ChannelPage(channel.tenantId(), channel.projectId(), channel.id(), after, PAGE_SIZE + 1));
        if (page == null || page.size() > PAGE_SIZE + 1) throw new IllegalStateException("SUBSCRIPTION_RECIPIENT_PAGE_INVALID");
        long previous = after;
        for (var row : page) {
            if (row == null || row.getId() == null || row.getId() <= previous
                    || !Objects.equals(channel.tenantId(), row.getTenantId()) || !Objects.equals(channel.projectId(), row.getProjectId())
                    || !Objects.equals(channel.id(), row.getChannelId())) throw new IllegalStateException("SUBSCRIPTION_RECIPIENT_PAGE_INVALID");
            previous = row.getId();
        }
        for (var row : page.subList(0, Math.min(PAGE_SIZE, page.size()))) {
            var wakeup = ResultSubscriptionWakeup.forCause(row, "change:" + source.eventId());
            outbox.append("ResultSubscription", row.getId().toString(),
                    new BusinessEvent(wakeup.eventId(), ResultSubscriptionWakeup.EVENT_TYPE, JsonUtils.toJsonString(wakeup)));
        }
        if (page.size() > PAGE_SIZE) {
            var continuation = ResultSubscriptionFanoutEvent.create(source, page.get(PAGE_SIZE-1).getId());
            outbox.append("BusinessResultChannel", channel.id().toString(),
                    new BusinessEvent(continuation.eventId(), ResultSubscriptionFanoutEvent.EVENT_TYPE, JsonUtils.toJsonString(continuation)));
        }
    }
}
