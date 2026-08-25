package cn.iocoder.yudao.module.pms.project.service.acceptance.application;

import java.util.List;

/** PROJ可同步调用的ACC内部应用接口。 */
public interface ProjectDeliverableInitializationApplicationService {

    DeliverableInitializationResult initialize(InitializeProjectDeliverablesCommand command);

    List<DeliverableView> getByProjectId(Long projectId);

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
