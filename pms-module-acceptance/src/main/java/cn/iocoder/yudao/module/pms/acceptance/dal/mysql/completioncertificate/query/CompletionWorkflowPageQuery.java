package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Set;
@Data @EqualsAndHashCode(callSuper=true)
public class CompletionWorkflowPageQuery extends PageParam {
    private Long tenantId;
    private Set<Long> visibleProjectIds;
    private Long projectId;
    private String name;
    private Integer status;
    private boolean pairedOnly;
    public int getOffset(){return (getPageNo()-1)*getPageSize();}
}
