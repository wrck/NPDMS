package cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

/** 项目异常治理动作历史稳定分页查询。 */
public record ProjectGovernanceHistoryPageQuery(
        Long tenantId,
        Long projectId,
        PageParam pageParam) {

    private static final int MAX_PAGE_SIZE = 200;

    public ProjectGovernanceHistoryPageQuery {
        if (pageParam == null || pageParam.getPageNo() == null || pageParam.getPageNo() < 1) {
            throw new IllegalArgumentException("pageNo must be at least 1");
        }
        if (pageParam.getPageSize() == null || pageParam.getPageSize() < 1
                || pageParam.getPageSize() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        pageParam = copyOf(pageParam);
    }

    @Override
    public PageParam pageParam() {
        return copyOf(pageParam);
    }

    public long offset() {
        return (long) (pageParam.getPageNo() - 1) * pageParam.getPageSize();
    }

    public int limit() {
        return pageParam.getPageSize();
    }

    private static PageParam copyOf(PageParam source) {
        PageParam copy = new PageParam();
        copy.setPageNo(source.getPageNo());
        copy.setPageSize(source.getPageSize());
        return copy;
    }
}
