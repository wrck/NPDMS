package cn.iocoder.yudao.module.pms.project.service.acceptance.application;

import java.util.List;

/** PROJ可同步调用的ACC内部应用接口。 */
public interface ProjectDeliverableInitializationApplicationService {

    DeliverableInitializationResult initialize(InitializeProjectDeliverablesCommand command);

    List<DeliverableView> getByProjectId(Long projectId);

    /** Locks the Owner roots; preview and apply share the same retirement eligibility. Requires caller transaction. */
    DeliverablePlanState inspectPlanDefinitions(Long projectId);

    record DeliverablePlanState(List<DeliverableView> definitions, java.util.Set<Long> retirableIds) { }

    /** Authorized project-plan command holds the project lock and supplies the previewed instance versions. */
    void applyPlanChanges(ApplyDeliverablePlanChanges command);

    record ApplyDeliverablePlanChanges(Long projectId, Long actorId, List<DeliverablePlanChange> changes) { }

    /** Null id creates a new definition; null definition retires an unhandled instance. */
    record DeliverablePlanChange(Long id, Integer expectedVersion, DeliverableDefinition definition) { }

    record InitializeProjectDeliverablesCommand(
            Long projectId,
            Long templateRevisionId,
            List<DeliverableDefinition> definitions) {
    }

    record DeliverableDefinition(
            String deliverableCode,
            String name,
            String stageCode,
            String taskCode,
            boolean required,
            Long sourceDefinitionId) {
    }

    record DeliverableInitializationResult(int expectedCount, int insertedCount) {
    }

    record DeliverableView(
            Long id,
            Long projectId,
            String deliverableCode,
            String name,
            String stageCode,
            String taskCode,
            Boolean required,
            Long sourceDefinitionId,
            String status,
            Integer version) {
    }
}
