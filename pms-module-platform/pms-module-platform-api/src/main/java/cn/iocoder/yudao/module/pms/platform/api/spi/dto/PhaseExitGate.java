package cn.iocoder.yudao.module.pms.platform.api.spi.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 阶段退出条件（迁移自源工程 pms-common com.dp.plat.common.dto.PhaseExitGate，
 * 结构化 JSON，4 类条件）
 * 关联设计文档：§3.2
 */
@Data
public class PhaseExitGate implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 必需交付件 */
    private List<RequiredDeliverable> requiredDeliverables;

    /** 必需任务 */
    private List<RequiredTask> requiredTasks;

    /** 必需里程碑 */
    private List<RequiredMilestone> requiredMilestones;

    /** 必需审批 */
    private List<RequiredApproval> requiredApprovals;

    @Data
    public static class RequiredDeliverable implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 交付件标识。模板态为前端生成的字符串引用（无数据库实例）；
         * 项目态为数据库 Long 主键。统一用 String 承载，避免 Jackson 反序列化
         * 字符串 ID 到 Long 字段时抛类型错误。项目态校验时由调用方安全转为 Long。
         */
        private String deliverableId;
        private String deliverableName;
        private String requiredStatus; // PUBLISHED/REFERENCED/ARCHIVED
    }

    @Data
    public static class RequiredTask implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 阶段标识。模板态为 phaseCode 字符串引用；项目态为数据库 Long 主键。
         * 统一用 String 承载，避免类型错误。
         */
        private String phaseId;
        private Boolean allCompleted;
    }

    @Data
    public static class RequiredMilestone implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 里程碑标识。模板态为前端生成的字符串引用；项目态为数据库 Long 主键。
         * 统一用 String 承载，避免类型错误。
         */
        private String milestoneId;
        private Boolean mustReached;
    }

    @Data
    public static class RequiredApproval implements Serializable {
        private static final long serialVersionUID = 1L;
        private String approvalType; // PHASE_EXIT 等
        private Boolean mustApproved;
    }
}