package cn.iocoder.yudao.module.pms.engineering.dal.mysql.authorization;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.authorization.vo.AuthorizationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.authorization.AuthorizationDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthorizationMapper extends BaseMapperX<AuthorizationDO> {

    default PageResult<AuthorizationDO> selectPage(AuthorizationPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AuthorizationDO>()
                .eqIfPresent(AuthorizationDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(AuthorizationDO::getCode, reqVO.getCode())
                .likeIfPresent(AuthorizationDO::getName, reqVO.getName())
                .eqIfPresent(AuthorizationDO::getAuthorizationType, reqVO.getAuthorizationType())
                .eqIfPresent(AuthorizationDO::getStatus, reqVO.getStatus())
                .eqIfPresent(AuthorizationDO::getDeviceId, reqVO.getDeviceId())
                .likeIfPresent(AuthorizationDO::getDeviceSerial, reqVO.getDeviceSerial())
                .eqIfPresent(AuthorizationDO::getSubmitUserId, reqVO.getSubmitUserId())
                .eqIfPresent(AuthorizationDO::getApproverUserId, reqVO.getApproverUserId())
                .betweenIfPresent(AuthorizationDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(AuthorizationDO::getId));
    }

    /**
     * 按编号查询，用于全局唯一性校验
     */
    default AuthorizationDO selectByCode(String code) {
        return selectOne(AuthorizationDO::getCode, code);
    }

    /**
     * 按项目ID查询数量
     */
    default Long selectCountByProjectId(Long projectId) {
        return selectCount(AuthorizationDO::getProjectId, projectId);
    }

}
