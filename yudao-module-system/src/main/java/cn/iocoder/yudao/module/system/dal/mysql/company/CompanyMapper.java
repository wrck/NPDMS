package cn.iocoder.yudao.module.system.dal.mysql.company;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.controller.admin.company.vo.CompanyPageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CompanyMapper extends BaseMapperX<CompanyDO> {

    default CompanyDO selectByCode(String code) {
        return selectOne(CompanyDO::getCode, code);
    }

    default PageResult<CompanyDO> selectPage(CompanyPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CompanyDO>()
                .likeIfPresent(CompanyDO::getCode, reqVO.getCode())
                .likeIfPresent(CompanyDO::getName, reqVO.getName())
                .eqIfPresent(CompanyDO::getStatus, reqVO.getStatus())
                .orderByAsc(CompanyDO::getCode));
    }

    default List<CompanyDO> selectEnabledList() {
        return selectList(new LambdaQueryWrapperX<CompanyDO>()
                .eq(CompanyDO::getStatus, 0)
                .orderByAsc(CompanyDO::getCode));
    }

    @Update("UPDATE system_company SET code = #{company.code}, name = #{company.name}, "
            + "status = #{company.status}, version = version + 1 "
            + "WHERE id = #{company.id} AND version = #{expectedVersion} AND deleted = FALSE")
    int updateByIdAndVersion(@Param("company") CompanyDO company,
                             @Param("expectedVersion") Integer expectedVersion);

}
