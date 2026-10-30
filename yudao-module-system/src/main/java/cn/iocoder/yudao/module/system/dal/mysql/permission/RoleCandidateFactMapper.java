package cn.iocoder.yudao.module.system.dal.mysql.permission;

import cn.iocoder.yudao.module.system.api.permission.RoleCandidateFactApi;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.RoleCandidateFactQuery;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.ActorRoleMembershipQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RoleCandidateFactMapper {
    List<RoleCandidateFactApi.Candidate> selectCandidates(@Param("query") RoleCandidateFactQuery query);
    List<RoleCandidateFactApi.ActorMembership> selectActorMemberships(@Param("query") ActorRoleMembershipQuery query);
}
