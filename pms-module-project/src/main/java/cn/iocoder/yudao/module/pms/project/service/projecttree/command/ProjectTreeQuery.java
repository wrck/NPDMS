package cn.iocoder.yudao.module.pms.project.service.projecttree.command;

public record ProjectTreeQuery(Long anchorProjectId, QueryType queryType, String businessLevelCode,
                               Integer pageSize, String cursor) {
    public enum QueryType { CHILDREN, DESCENDANTS, ANCESTORS, BUSINESS_LEVEL, LOCATE }
}
