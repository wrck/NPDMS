package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Supporting quota/post checks; token values remain in memory for the existing revocation API. */
@Mapper
public interface DirectAccountAuxiliaryMapper {
    record UserScope(Long tenantId, Long userId) {}
    record PostRemoval(UserScope scope, Set<Long> postIds) {}
    record PostWrite(UserScope scope, Long id, Long postId, String actor, LocalDateTime time) {}
    Integer selectAccountLimit(@Param("query") DirectAccountQueries.UserMigrationTenantQuery query);
    List<Long> selectPosts(@Param("query") UserScope query);
    int deletePosts(@Param("write") PostRemoval write);
    int insertPost(@Param("write") PostWrite write);
    List<String> selectAccessTokens(@Param("query") UserScope query);
}
