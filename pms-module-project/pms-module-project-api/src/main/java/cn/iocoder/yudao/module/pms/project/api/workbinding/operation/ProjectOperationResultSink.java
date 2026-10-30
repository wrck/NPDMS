package cn.iocoder.yudao.module.pms.project.api.workbinding.operation;

/** S5 supplies a durable result producer; absence keeps new operation contracts non-runnable. */
public interface ProjectOperationResultSink {
    void append(String operationCode, int operationVersion, ProjectOperationCommand command,
                ProjectOperationResult result, Long tenantId, Long actorId, String correlationId);
}
