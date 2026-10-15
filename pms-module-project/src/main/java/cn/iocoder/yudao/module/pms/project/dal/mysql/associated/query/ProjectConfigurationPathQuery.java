package cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query;
import java.util.List;
public record ProjectConfigurationPathQuery(Long tenantId,String sourceProjectCode,List<PathIdentity> paths) {
    public ProjectConfigurationPathQuery {paths=List.copyOf(paths);}
    public record PathIdentity(String projectCode,String executionNo,Integer itemGroup,String bomPath) {}
}
