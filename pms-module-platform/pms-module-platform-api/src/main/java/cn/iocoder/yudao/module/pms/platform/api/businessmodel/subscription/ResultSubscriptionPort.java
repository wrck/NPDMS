package cn.iocoder.yudao.module.pms.platform.api.businessmodel.subscription;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.ResultSelectionPolicy;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;

import java.util.List;
import java.util.Set;

/**
 * 独立结果订阅能力：订阅建立、存量补扫采纳与判断结果归本能力所有；
 * 执行实现只负责调度、等待与恢复。补扫采纳与正式判断共用同一选择语义。
 */
public interface ResultSubscriptionPort {

    /** 建立订阅：轮次形成基线取建立时该结果类型当前最大形成序号（不含）。 */
    ResultSubscriptionRecord create(CreateCommand command, EntityActor actor);

    /**
     * 存量补扫：按选择策略枚举既有结果并重新判断；已 SATISFIED 的订阅不自动重开、
     * 不重复写采纳证据；结果失效不回滚历史判断，只记录影响。
     */
    ResultSubscriptionDecision backfill(long subscriptionId, long throughSequence, EntityActor actor);

    ResultSubscriptionRecord get(long subscriptionId, EntityActor actor);

    /** 按结果类型列出尚未满足（仍在收集）的订阅：结果形成事件的补扫消费入口。 */
    List<ResultSubscriptionRecord> listCollecting(String resultType, String ownerModule,
                                                  String entityType, EntityActor actor);

    ResultSubscriptionDecision lastDecision(long subscriptionId, EntityActor actor);

    record CreateCommand(String subscriptionCode, String subscriberKind, String subscriberNodeKey,
                         String resultType, String ownerModule, String entityType,
                         ResultSelectionPolicy policy, long roundNo, String projectStableRef,
                         String planVersion, String ruleVersion) {
    }

    record ResultSubscriptionRecord(long id, String subscriptionCode, String subscriberKind,
                                    String subscriberNodeKey, String resultType, String ownerModule,
                                    String entityType, ResultSelectionPolicy policy, long roundNo,
                                    long formationBaseline, String status) {
    }

    record ResultSubscriptionDecision(DecisionStatus status, long examined, long eligible,
                                      Set<Long> missingObjects, String basis,
                                      List<String> adoptedResultRefs) {
    }

    /** 与既有证据判定状态同名同义：未知/不完整不冒充满足。 */
    enum DecisionStatus { COLLECTING, SATISFIED, WAITING, AMBIGUOUS, UNAVAILABLE }
}
