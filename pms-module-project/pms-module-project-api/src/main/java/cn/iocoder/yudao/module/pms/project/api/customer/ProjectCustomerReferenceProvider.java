package cn.iocoder.yudao.module.pms.project.api.customer;

/** 客户更正专用引用检查；每个模块只查询自己的业务记录，不产生写入或迁移。 */
public interface ProjectCustomerReferenceProvider {
    enum Source {
        CUSTOMER("项目联系人"), ASSET("设备归属"), COMMERCE("合同及实施范围"),
        ENGINEERING("工程业务记录"), PROJECT("交付件、验收及业务关联"),
        CUTOVER("割接记录"), SERVICE("服务任务"), PLATFORM("采集任务及凭据授权");
        private final String label;
        Source(String label) { this.label = label; }
        public String label() { return label; }
    }
    record Query(Long tenantId, Long projectId) { }
    Source source();
    /** 包含历史引用；自动初始化且没有业务内容的实例不计入。 */
    long countReferences(Query query);
}
