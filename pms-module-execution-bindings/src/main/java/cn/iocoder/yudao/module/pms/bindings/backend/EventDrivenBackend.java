package cn.iocoder.yudao.module.pms.bindings.backend;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.bindings.dal.dataobject.EventCheckpointDO;
import cn.iocoder.yudao.module.pms.bindings.dal.dataobject.EventedInstanceDO;
import cn.iocoder.yudao.module.pms.bindings.dal.mysql.EventCheckpointMapper;
import cn.iocoder.yudao.module.pms.bindings.dal.mysql.EventedInstanceMapper;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.definition.ProcessDefinitionPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.definition.ProcessDefinitionSnapshot;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPollPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventRecord;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.RuleVerdict;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.BusinessFactPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.BusinessResultFormationPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.ResultSemantics;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.subscription.ResultSubscriptionPort;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行后端二：事件驱动。后台轮询统一业务事件，按已发布定义逐个判定并形成结果，
 * 形成依据是事件身份（重复事件在结果层幂等）；消费检查点自持，处理后落库。
 */
@Component
@ConditionalOnProperty(name = "pms.bindings.backends.event-driven.enabled",
        havingValue = "true", matchIfMissing = true)
public class EventDrivenBackend {

    public static final String BACKEND_ID = "bindings-event-driven";
    static final String CONSUMED_EVENT_TYPE = "pms.business.changed";
    static final String FORMED_EVENT_TYPE = "pms.business.formed";
    static final String INVALIDATED_EVENT_TYPE = "pms.business.invalidated";

    private static final Logger log = LoggerFactory.getLogger(EventDrivenBackend.class);

    /** 消费游标：Outbox 行号按类型推进；读取窗口只含游标之后的事件，已检查点事件不再回填窗口。 */
    private final Map<String, Long> pollCursor = new ConcurrentHashMap<>();
    private final BusinessEventPollPort eventPollPort;
    private final ProcessDefinitionPort definitionPort;
    private final EventCheckpointMapper checkpointMapper;
    private final EventedInstanceMapper instanceMapper;
    private final BusinessFactPort factPort;
    private final BusinessResultFormationPort formationPort;
    private final ResultSubscriptionPort subscriptionPort;

    public EventDrivenBackend(BusinessEventPollPort eventPollPort, ProcessDefinitionPort definitionPort,
                              EventCheckpointMapper checkpointMapper, EventedInstanceMapper instanceMapper,
                              BusinessFactPort factPort, BusinessResultFormationPort formationPort,
                              ResultSubscriptionPort subscriptionPort) {
        this.eventPollPort = eventPollPort;
        this.definitionPort = definitionPort;
        this.checkpointMapper = checkpointMapper;
        this.instanceMapper = instanceMapper;
        this.factPort = factPort;
        this.formationPort = formationPort;
        this.subscriptionPort = subscriptionPort;
    }

    @Scheduled(fixedDelayString = "${pms.bindings.backends.event-driven.poll-interval-ms:3000}",
            initialDelayString = "${pms.bindings.backends.event-driven.poll-initial-delay-ms:5000}")
    public void pollAndProcess() {
        // 三类事件分别领取；单类型轮询失败不阻断其他类型。
        for (String eventType : List.of(CONSUMED_EVENT_TYPE, FORMED_EVENT_TYPE, INVALIDATED_EVENT_TYPE)) {
            long cursor = pollCursor.getOrDefault(eventType, 0L);
            List<BusinessEventRecord> events;
            try {
                events = eventPollPort.pollPending(eventType, 50, cursor);
            } catch (Exception ex) {
                log.warn("[event-driven-backend] 事件轮询失败 type={}: {}", eventType, ex.getMessage());
                continue;
            }
            long advanced = cursor;
            for (BusinessEventRecord event : events) {
                try {
                    TenantUtils.execute(event.sourceRef().tenantId(), () -> processOne(event));
                    advanced = event.sequence();
                } catch (Exception ex) {
                    // 单事件失败不落检查点，游标停留在失败事件之前，下轮按幂等重处理；不吞掉其他事件的进度。
                    log.warn("[event-driven-backend] 事件处理失败 event={}: {}", event.eventId(), ex.getMessage());
                    break;
                }
            }
            if (advanced > cursor) {
                pollCursor.put(eventType, advanced);
            }
        }
    }

    private void processOne(BusinessEventRecord event) {
        if (checkpointMapper.selectByEventId(event.eventId()).isPresent()) {
            return;
        }
        switch (event.kind()) {
            case CHANGED -> evaluateDefinitions(event);
            case FORMED -> backfillSubscriptions(event);
            case INVALIDATED -> {
                // 失效影响由平台结果失效同事务记录；事件消费不重执行任何领域命令，不重开订阅。
            }
        }
        markProcessed(event);
    }

    private void evaluateDefinitions(BusinessEventRecord event) {
        EntityRef ref = event.sourceRef();
        List<ProcessDefinitionSnapshot> definitions = definitionPort.listPublished(ref.ownerModule(),
                ref.entityType());
        for (ProcessDefinitionSnapshot definition : definitions) {
            evaluateAgainst(definition, ref, event);
        }
    }

    /** 结果形成事件驱动订阅补扫：轮次上界取形成序号；旧轮次唤醒的是当前轮补扫，不改投其他轮次。 */
    private void backfillSubscriptions(BusinessEventRecord event) {
        EntityRef ref = event.sourceRef();
        String resultType = String.valueOf(event.payload().get("resultType"));
        long throughSequence = asLong(event.payload().get("resultSequence"));
        for (ResultSubscriptionPort.ResultSubscriptionRecord subscription : subscriptionPort
                .listCollecting(resultType, ref.ownerModule(), ref.entityType(), null)) {
            try {
                subscriptionPort.backfill(subscription.id(), throughSequence, null);
            } catch (Exception ex) {
                // 单订阅失败不阻断其他订阅；事件未落检查点，下轮按幂等重处理。
                throw ex;
            }
        }
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private void evaluateAgainst(ProcessDefinitionSnapshot definition, EntityRef ref,
                                 BusinessEventRecord event) {
        if (instanceMapper.selectByEventAndDefinition(event.eventId(), definition.definitionCode()).isPresent()) {
            return;
        }
        RuleVerdict verdict = RuleSemanticEvaluator.evaluate(definition, ref, factPort,
                "bindings:event-driven");
        String resultId = null;
        if (Boolean.TRUE.equals(verdict.satisfied())) {
            // 形成依据是事件身份：同一事件重复投递不会第二次形成结果。
            resultId = formationPort.form(definition.resultType(), ref, ResultSemantics.NEW_RESULT,
                    event.eventId(), BACKEND_ID).resultId();
        }
        EventedInstanceDO row = new EventedInstanceDO();
        row.setDefinitionCode(definition.definitionCode());
        row.setDefinitionVersion(definition.definitionVersion());
        row.setOwnerModule(ref.ownerModule());
        row.setEntityType(ref.entityType());
        row.setEntityId(ref.entityId());
        row.setEventId(event.eventId());
        row.setVerdict(InlineSyncBackend.verdictText(verdict));
        row.setResultId(resultId);
        try {
            instanceMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            // 同事件同定义的并发处理以唯一键裁决，视为已处理。
        }
    }

    public List<EventedInstanceDO> listInstances(String definitionCode) {
        return instanceMapper.selectByDefinition(definitionCode);
    }

    private void markProcessed(BusinessEventRecord event) {
        try {
            EventCheckpointDO checkpoint = new EventCheckpointDO();
            checkpoint.setEventId(event.eventId());
            checkpoint.setProcessedAt(LocalDateTime.now());
            checkpointMapper.insert(checkpoint);
        } catch (DuplicateKeyException ex) {
            // 并发轮询下检查点唯一键裁决，忽略。
        }
    }
}
