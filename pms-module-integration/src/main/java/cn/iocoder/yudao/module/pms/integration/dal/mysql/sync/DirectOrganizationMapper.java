package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** P01-only direct writes, explicitly authorized separately from the original organization task. */
@Mapper
public interface DirectOrganizationMapper {
    record Scope(long tenantId) {}
    record Leaders(long tenantId, Set<Long> ids) {}
    record Node(Long id, String type, String code, String name, Long parentId, Integer sort,
                Long leaderUserId, String phone, String email, Integer status, Integer version,
                String creator, LocalDateTime createTime, String updater, LocalDateTime updateTime) {}
    record Write(long tenantId, Node node) {}
    record LeaderWrite(long tenantId, Node before, Node after) {}
    List<Node> selectCompanies(@Param("query") Scope query);
    List<Node> lockCompanies(@Param("query") Scope query);
    List<Node> selectDepartments(@Param("query") Scope query);
    List<Node> lockDepartments(@Param("query") Scope query);
    List<Long> selectLeaders(@Param("query") Leaders query);
    int insertCompany(@Param("write") Write write);
    int insertDepartment(@Param("write") Write write);
    int completeLeader(@Param("write") LeaderWrite write);
}
