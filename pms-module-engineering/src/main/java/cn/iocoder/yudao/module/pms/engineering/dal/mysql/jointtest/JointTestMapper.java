package cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo.JointTestPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.JointTestDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PMS 业务联调 Mapper（FR-ENG-024）。
 */
@Mapper
public interface JointTestMapper extends BaseMapperX<JointTestDO> {

    default PageResult<JointTestDO> selectPage(JointTestPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<JointTestDO>()
                .eqIfPresent(JointTestDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(JointTestDO::getCode, reqVO.getCode())
                .likeIfPresent(JointTestDO::getTestCase, reqVO.getTestCase())
                .eqIfPresent(JointTestDO::getStatus, reqVO.getStatus())
                .eqIfPresent(JointTestDO::getEquipmentId, reqVO.getEquipmentId())
                .eqIfPresent(JointTestDO::getTesterUserId, reqVO.getTesterUserId())
                .orderByDesc(JointTestDO::getId));
    }

    default JointTestDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(JointTestDO::getProjectId, projectId, JointTestDO::getCode, code);
    }
}
