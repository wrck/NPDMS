package cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionSourceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SolutionSourceMapper extends BaseMapperX<SolutionSourceDO> {

    default List<SolutionSourceDO> selectListBySolutionId(Long solutionId) {
        return selectList(new LambdaQueryWrapperX<SolutionSourceDO>()
                .eq(SolutionSourceDO::getSolutionId, solutionId)
                .orderByAsc(SolutionSourceDO::getId));
    }

    default SolutionSourceDO selectBySolutionAndSource(Long solutionId, String sourceType, Long sourceId) {
        return selectOne(new LambdaQueryWrapperX<SolutionSourceDO>()
                .eq(SolutionSourceDO::getSolutionId, solutionId)
                .eq(SolutionSourceDO::getSourceType, sourceType)
                .eq(SolutionSourceDO::getSourceId, sourceId));
    }

    default int deleteBySolutionId(Long solutionId) {
        return delete(new LambdaQueryWrapperX<SolutionSourceDO>()
                .eq(SolutionSourceDO::getSolutionId, solutionId));
    }

}
