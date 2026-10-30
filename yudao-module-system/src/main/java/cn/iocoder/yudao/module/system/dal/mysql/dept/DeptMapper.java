package cn.iocoder.yudao.module.system.dal.mysql.dept;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface DeptMapper extends BaseMapperX<DeptDO> {

    default List<DeptDO> selectList(DeptListReqVO reqVO) {
        return selectList(new LambdaQueryWrapperX<DeptDO>()
                .likeIfPresent(DeptDO::getCode, reqVO.getCode())
                .likeIfPresent(DeptDO::getName, reqVO.getName())
                .eqIfPresent(DeptDO::getStatus, reqVO.getStatus()));
    }

    default DeptDO selectByParentIdAndName(Long parentId, String name) {
        return selectOne(DeptDO::getParentId, parentId, DeptDO::getName, name);
    }

    default DeptDO selectByCode(String code) {
        return selectOne(DeptDO::getCode, code);
    }

    default Long selectCountByParentId(Long parentId) {
        return selectCount(DeptDO::getParentId, parentId);
    }

    default List<DeptDO> selectListByParentId(Collection<Long> parentIds) {
        return selectList(DeptDO::getParentId, parentIds);
    }

    default List<DeptDO> selectListByLeaderUserId(Long id) {
        return selectList(DeptDO::getLeaderUserId, id);
    }


    default List<DeptDO> selectProjectMemberDepartments(
            cn.iocoder.yudao.module.system.dal.mysql.dept.query.ProjectMemberDepartmentsQuery query) {
        if (query == null || query.tenantId() == null || query.departmentIds() == null || query.departmentIds().isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<DeptDO>()
                .eq(DeptDO::getTenantId, query.tenantId())
                .in(DeptDO::getId, query.departmentIds())
                .likeIfPresent(DeptDO::getName, query.keyword())
                .select(DeptDO::getId, DeptDO::getName));
    }
}
