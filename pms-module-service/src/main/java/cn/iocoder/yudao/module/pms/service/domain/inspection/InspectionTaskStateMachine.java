package cn.iocoder.yudao.module.pms.service.domain.inspection;

import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException;
import java.util.Set;

/** INS-01/02/05/06/07 的领域流转；命令完成条件由同事务内的 Owner 事实提供。 */
public final class InspectionTaskStateMachine {
    public enum Action { START_OFFLINE, START_ONLINE, PRECHECK_PASSED, EXECUTION_COMPLETE,
        REPORT_READY, ANNOTATIONS_COMPLETE, UPDATE_ANNOTATIONS, ISSUES_CLOSED, REQUEST_ARCHIVE, ARCHIVE, CANCEL }

    public record Transition(String current, Action action, boolean executionComplete,
                             boolean reportAvailable, boolean annotationsComplete, long openIssues,
                             boolean archiveRequested, boolean customerReferenceCreated) { }

    private InspectionTaskStateMachine() { }

    public static String next(Transition command) {
        String current = command.current();
        if (Set.of("ARCHIVED", "CANCELLED").contains(current)) reject("终态巡检任务只读");
        return switch (command.action()) {
            case START_OFFLINE, START_ONLINE -> {
                if (!Set.of("PREPARING", "PRECHECK_PENDING", "EXECUTING", "REPORT_PENDING").contains(current))
                    reject("当前阶段不能新建执行尝试");
                yield command.action() == Action.START_OFFLINE ? "EXECUTING" : "PRECHECK_PENDING";
            }
            case PRECHECK_PASSED -> { requireCurrent(current, "PRECHECK_PENDING"); yield "EXECUTING"; }
            case EXECUTION_COMPLETE -> {
                requireCurrent(current, "EXECUTING");
                if (!command.executionComplete()) reject("仍有设备或检测项未形成完整结果");
                yield "REPORT_PENDING";
            }
            case REPORT_READY -> {
                if (!Set.of("REPORT_PENDING", "ANNOTATION_PENDING").contains(current)) reject("当前阶段不能生成报告");
                if (!command.reportAvailable() || !command.executionComplete()) reject("报告文件或输入结果不可用");
                yield "ANNOTATION_PENDING";
            }
            case ANNOTATIONS_COMPLETE -> {
                requireCurrent(current, "ANNOTATION_PENDING");
                if (!command.reportAvailable() || !command.annotationsComplete()) reject("报告或问题标注未完成");
                yield command.openIssues() == 0 ? "CLOSED" : "TRACKING";
            }
            case ISSUES_CLOSED -> {
                requireCurrent(current, "TRACKING");
                requireClosure(command);
                yield "CLOSED";
            }
            case UPDATE_ANNOTATIONS -> {
                if (!Set.of("TRACKING", "CLOSED").contains(current)) reject("当前阶段不能修订已确认标注");
                if (!command.reportAvailable() || !command.annotationsComplete()) reject("报告或问题标注不完整");
                yield command.openIssues() == 0 ? "CLOSED" : "TRACKING";
            }
            case REQUEST_ARCHIVE -> {
                requireCurrent(current, "CLOSED"); requireClosure(command); yield "CLOSED";
            }
            case ARCHIVE -> {
                requireCurrent(current, "CLOSED"); requireClosure(command);
                if (!command.archiveRequested() || !command.customerReferenceCreated()) reject("归档申请或客户资产引用未完成");
                yield "ARCHIVED";
            }
            case CANCEL -> "CANCELLED";
        };
    }

    private static void requireClosure(Transition value) {
        if (!value.reportAvailable() || !value.annotationsComplete() || value.openIssues() != 0)
            reject("报告、标注或跟踪闭环条件未满足");
    }
    private static void requireCurrent(String actual, String expected) {
        if (!expected.equals(actual)) reject("巡检阶段已变化，请刷新后重试");
    }
    private static void reject(String message) {
        throw new InspectionException(InspectionException.Reason.STATE_CONFLICT, message);
    }
}
