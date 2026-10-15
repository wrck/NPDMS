package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi.ProjectBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PublishedProjectScopeMapper {
    record Publication(Long tenantId, Long connectionId, Long taskId, Long runId) {}
    record Member(Long tenantId, Long runId, ProjectBinding project) {}
    record Selection(Long tenantId, Long connectionId, Long runId, List<String> sourceProjectKeys) {}
    record Connection(Long tenantId, Long connectionId) {}
    record SourcePage(Long tenantId, Long connectionId, Long runId, String afterKey) {}
    int insertMember(@Param("query") Member member);
    ProjectBinding selectMember(@Param("query") Member member);
    int insertPublication(@Param("query") Publication publication);
    Long selectLatestRun(@Param("query") Connection query);
    List<ProjectBinding> selectMembers(@Param("query") Selection query);
    List<ProjectBinding> selectSourcePage(@Param("query") SourcePage query);
}
