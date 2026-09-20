package cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan.query;
@lombok.Data
@lombok.EqualsAndHashCode(callSuper = true)
public class StagePlanPageQuery extends cn.iocoder.yudao.framework.common.pojo.PageParam {
    private Long projectId;
    private Integer status;
    private java.util.Set<Long> visibleProjectIds;
}
