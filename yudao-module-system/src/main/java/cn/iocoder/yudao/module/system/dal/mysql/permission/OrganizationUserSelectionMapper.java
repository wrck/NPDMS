package cn.iocoder.yudao.module.system.dal.mysql.permission;

import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidateRespDTO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.OrganizationUserCandidatePageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrganizationUserSelectionMapper {

    Long selectCount(@Param("query") OrganizationUserCandidatePageQuery query);

    List<OrganizationUserCandidateRespDTO> selectPage(@Param("query") OrganizationUserCandidatePageQuery query);

}
