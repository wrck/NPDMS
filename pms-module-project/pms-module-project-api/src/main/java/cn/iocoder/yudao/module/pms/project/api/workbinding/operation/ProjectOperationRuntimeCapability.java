package cn.iocoder.yudao.module.pms.project.api.workbinding.operation;

/** Implemented by the actual controlled command adapter only after its runtime path is installed. */
public interface ProjectOperationRuntimeCapability {
    boolean supports(String operationCode, int operationVersion);
}
