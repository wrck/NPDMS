package cn.iocoder.yudao.module.pms.workflow.listener;

import cn.iocoder.yudao.module.pms.workflow.spi.OaTodoPort;
import cn.iocoder.yudao.module.pms.workflow.spi.dto.OaTodoCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flowable {@link TaskListener} that mirrors user-task lifecycle events to
 * the 致远 OA todo system（迁移自源工程 pms-workflow OaTaskListener）.
 *
 * <p>On task {@code create} a todo is pushed to OA for the task assignee; on
 * task {@code complete} the corresponding OA todo is closed. The listener is
 * registered in the BPMN via {@code delegateExpression="${oaTaskListener}"}.</p>
 *
 * <p><b>跨模块边界（依赖倒置）</b>：源工程直接依赖 pms-integration 的
 * {@code OaIntegrationService}，目标体系下改为注入本模块声明的
 * {@link OaTodoPort} 扩展点，由集成侧实现。若集成模块未加载（bean 不存在），
 * Spring 注入失败会导致流程启动报错——集成侧实现见
 * {@code pms-module-integration} 的 OaTodoPortAdapter。</p>
 *
 * <p><b>事务隔离（Task 19.4）</b>：{@link #notify(DelegateTask)} 方法标注
 * {@code @Transactional(propagation = REQUIRES_NEW)}，确保 OA 集成（含
 * {@code IntegrationLog} 写入）在独立事务中执行，与 Flowable 工作流主流程
 * 事务隔离：</p>
 *
 * <ul>
 *   <li>OA 调用成功 → IntegrationLog 记录 SUCCESS，新事务提交，主流程继续</li>
 *   <li>OA 调用失败 → IntegrationLog 记录 FAILED，新事务提交（日志需保留），
 *       异常被 try-catch 吞掉，主流程不受影响</li>
 *   <li>主流程后续回滚 → 不影响已提交的 OA 集成日志（REQUIRES_NEW 已提交）</li>
 * </ul>
 *
 * <p><b>代理生效前提</b>：Flowable 通过 {@code delegateExpression} 从 Spring
 * 容器获取本 Bean，获取的是 Spring 代理对象（CGLIB），因此 {@code @Transactional}
 * 注解能正常生效。同类内部方法调用不会触发代理，但本类仅 {@code notify} 一个
 * 入口方法，不存在内部调用链。</p>
 *
 * <p><b>异常策略</b>：所有异常（{@code IntegrationException} / HTTP 错误 /
 * token 获取失败）均在 {@code notify} 内 catch，仅记录 WARN 日志，绝不向
 * Flowable 引擎上抛。OA 集成是 best-effort，瞬时故障不应阻塞工作流。</p>
 */
@Slf4j
@Component("oaTaskListener")
@RequiredArgsConstructor
public class OaTaskListener implements TaskListener {

    private final OaTodoPort oaTodoPort;

    /**
     * 处理 Flowable 任务事件，在独立事务中同步 OA 待办。
     *
     * <p>使用 {@code REQUIRES_NEW} 传播级别：暂停当前 Flowable 事务，开启
     * 新事务执行 OA 集成。无论 OA 调用成功或失败，新事务都会提交（确保
     * IntegrationLog 持久化），然后恢复原事务。异常被 catch 吞掉，不向
     * Flowable 引擎传播。</p>
     *
     * @param delegateTask Flowable 委托任务实例
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(DelegateTask delegateTask) {
        String eventName = delegateTask.getEventName();
        try {
            if ("create".equals(eventName)) {
                OaTodoCommand command = OaTodoCommand.builder()
                        .title(delegateTask.getName())
                        .content("待办任务：" + delegateTask.getName())
                        .handlerUserId(delegateTask.getAssignee())
                        .processInstanceId(delegateTask.getProcessInstanceId())
                        .businessKey(resolveBusinessKey(delegateTask))
                        .processUrl(resolveProcessUrl(delegateTask))
                        .businessType(delegateTask.getProcessDefinitionId())
                        .build();
                oaTodoPort.pushTodo(command);
            } else if ("complete".equals(eventName)) {
                // Use the Flowable task id as the OA business key.
                oaTodoPort.completeTodo(delegateTask.getId());
            }
        } catch (Exception e) {
            // Swallow: OA integration must not affect the workflow transaction.
            // REQUIRES_NEW 事务已提交（IntegrationLog 的 FAILED 记录已持久化），
            // 此处仅记录告警，不向 Flowable 引擎抛出异常。
            log.warn("OA task listener '{}' failed for task {} (workflow unaffected): {}",
                    eventName, delegateTask.getId(), e.getMessage());
        }
    }

    /**
     * Resolve the business key from the process variables if available.
     */
    private String resolveBusinessKey(DelegateTask delegateTask) {
        try {
            Object value = delegateTask.getVariable("businessKey");
            return value == null ? delegateTask.getId() : value.toString();
        } catch (Exception e) {
            return delegateTask.getId();
        }
    }

    /**
     * Resolve a process detail URL from process variables if available.
     */
    private String resolveProcessUrl(DelegateTask delegateTask) {
        try {
            Object value = delegateTask.getVariable("processUrl");
            return value == null ? null : value.toString();
        } catch (Exception e) {
            return null;
        }
    }
}