package cn.iocoder.yudao.module.pms.engineering.dal.mysql.training;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.ProjectScopedCodeMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 现场培训记录 Mapper（ACC-01）
 */
@Mapper
public interface TrainingMapper extends ProjectScopedCodeMapper<TrainingDO> {

    TrainingDO selectFileOwnerForUpdate(@org.apache.ibatis.annotations.Param("query") cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.query.TrainingFileOwnerQuery query);

    default PageResult<TrainingDO> selectPage(TrainingPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TrainingDO>()
                .eqIfPresent(TrainingDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(TrainingDO::getCode, reqVO.getCode())
                .likeIfPresent(TrainingDO::getName, reqVO.getName())
                .eqIfPresent(TrainingDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(TrainingDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(TrainingDO::getId));
    }

    default TrainingDO selectByDigest(String signTokenDigest) {
        return selectOne(new LambdaQueryWrapperX<TrainingDO>()
                .eq(TrainingDO::getSignTokenDigest, signTokenDigest));
    }

    default List<TrainingDO> selectListByProject(Long projectId) {
        return selectList(new LambdaQueryWrapperX<TrainingDO>()
                .eq(TrainingDO::getProjectId, projectId)
                .orderByDesc(TrainingDO::getId));
    }

}
