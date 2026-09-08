package cn.iocoder.yudao.module.pms.platform.dal.mysql.businessview.query;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Getter;
import java.util.Set;
/** PM-03: server-resolved Owner configuration scope, empty scope returns nothing. */
@Getter
public final class BusinessViewPageQuery extends PageParam {
    private final Long tenantId;
    private final String entityType;
    private final String viewSource;
    private final Set<String> ownerContexts;
    public long getOffset() { return ((long) getPageNo() - 1) * getPageSize(); }
    public BusinessViewPageQuery(Long tenantId, String entityType, String viewSource,
                                 Set<String> ownerContexts, int pageNo, int pageSize) {
        this.tenantId = tenantId;
        this.entityType = entityType;
        this.viewSource = viewSource;
        this.ownerContexts = Set.copyOf(ownerContexts);
        setPageNo(pageNo);
        setPageSize(pageSize);
    }
}
