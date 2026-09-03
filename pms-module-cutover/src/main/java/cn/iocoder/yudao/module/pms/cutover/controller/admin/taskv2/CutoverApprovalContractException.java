package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

public final class CutoverApprovalContractException extends RuntimeException {
    private final int httpStatus;
    private final int errorCode;
    private final ErrorData errorData;

    public CutoverApprovalContractException(int httpStatus, int errorCode, String message, ErrorData errorData) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.errorData = errorData;
    }

    public int httpStatus() { return httpStatus; }
    public int errorCode() { return errorCode; }
    public ErrorData errorData() { return errorData; }

    public record ErrorData(String category, String reasonCode, String recoveryAction, String ownerContext,
                            Integer currentApprovalVersion, Integer currentTaskVersion) { }
}
