package cn.iocoder.yudao.module.pms.cutover.service.approval.notification;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNotificationDO;

import java.time.LocalDateTime;
import java.util.List;

/** Creates CUT-owned external delivery requests without invoking a cross-module provider. */
public final class CutoverExternalNotificationRequestFactory {
    private static final List<String> CHANNELS = List.of("SMS", "EMAIL", "DINGTALK");

    public List<CutoverApprovalNotificationDO> createForActivatedNode(CutoverApprovalInstanceDO instance,
                                                                       CutoverApprovalNodeDO node,
                                                                       int committedNodeVersion,
                                                                       long actorId,
                                                                       LocalDateTime now) {
        if (instance == null || node == null || instance.getTenantId() == null || instance.getTenantId() <= 0
                || instance.getId() == null || instance.getId() <= 0 || node.getId() == null || node.getId() <= 0
                || node.getNodeNo() == null || node.getNodeNo() <= 0 || node.getCurrentApproverUserId() == null
                || node.getCurrentApproverUserId() <= 0 || committedNodeVersion < 0 || actorId <= 0 || now == null) {
            throw new IllegalArgumentException("外部提醒请求来源事实不完整");
        }
        return CHANNELS.stream().map(channel -> create(instance, node, committedNodeVersion, actorId, now, channel))
                .toList();
    }

    private CutoverApprovalNotificationDO create(CutoverApprovalInstanceDO instance,
                                                   CutoverApprovalNodeDO node,
                                                   int committedNodeVersion,
                                                   long actorId,
                                                   LocalDateTime now,
                                                   String channel) {
        CutoverApprovalNotificationDO row = new CutoverApprovalNotificationDO();
        row.setTenantId(instance.getTenantId());
        row.setApprovalInstanceId(instance.getId());
        row.setApprovalNodeId(node.getId());
        row.setRecipientUserId(node.getCurrentApproverUserId());
        row.setDeliveryKey("CUT_APPROVAL_EXT:" + instance.getId() + ":" + node.getNodeNo() + ":"
                + committedNodeVersion + ":" + channel);
        row.setTemplateCode("CUT_APPROVAL_PENDING_V2");
        row.setChannelCode(channel);
        row.setStatusCode("PENDING");
        row.setRetryCount(0);
        row.setNextRetryAt(null);
        row.setMessageId(null);
        row.setProviderReferenceId(null);
        row.setLastErrorCode(null);
        row.setLastAttemptAt(null);
        row.setSentAt(null);
        row.setVersion(0);
        row.setCreator(String.valueOf(actorId));
        row.setUpdater(String.valueOf(actorId));
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }
}
