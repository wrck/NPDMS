package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 项目模板内容快照 DTO（JSON 序列化存储）
 */
@Data
public class TemplateSnapshot implements Serializable {

    private Integer schemaVersion = 1;
    private List<PhaseDef> phases;
    private List<TaskDef> tasks;
    private List<TeamRoleDef> teamRoles;

    @Data
    public static class PhaseDef implements Serializable {
        /** 阶段编码，模板内唯一（稳定键） */
        private String phaseCode;
        private String phaseName;
        private Integer sortOrder;
        private String entryCriteria;
        private String exitCriteria;
    }

    @Data
    public static class TaskDef implements Serializable {
        /** 任务编码，模板内唯一（稳定键） */
        private String taskCode;
        private String taskName;
        /** 父任务编码，null=顶层 */
        private String parentTaskCode;
        /** 所属阶段编码 */
        private String phaseCode;
        /** 优先级：0低 1中 2高 */
        private Integer priority;
        private Integer sortOrder;
        private BigDecimal estimatedHours;
        private String description;
    }

    @Data
    public static class TeamRoleDef implements Serializable {
        private String roleCode;
        private String roleName;
        private Integer requiredCount;
    }
}
