package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class InspectionTaskPageQuery extends PageParam {
    private Long tenantId;
    private Long actorId;
    private Set<Long> visibleProjectIds;
    private Set<Long> managedProjectIds;
    private String keyword;
    private Long projectId;
    private String mode;
    private String statusCode;
    private WorkbenchSection workbenchSection;
    public enum WorkbenchSection { PENDING, CREATED }
    /** BaseEntity.creator 保存字符串用户ID，绑定字符串避免数字转换及连接排序规则影响。 */
    public String getActorCreator() { return actorId == null ? null : actorId.toString(); }
    public int getOffset() { return (getPageNo() - 1) * getPageSize(); }
}
