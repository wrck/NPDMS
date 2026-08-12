package cn.iocoder.yudao.module.pms.service.dal.mysql.srvissue;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssuePageReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvissue.SrvIssueDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SrvIssueMapper extends BaseMapperX<SrvIssueDO> {

    default SrvIssueDO selectByTaskIdAndCode(Long taskId, String code) {
        return selectOne(new LambdaQueryWrapperX<SrvIssueDO>()
                .eq(SrvIssueDO::getTaskId, taskId)
                .eq(SrvIssueDO::getCode, code));
    }

    default List<SrvIssueDO> selectListByTaskId(Long taskId) {
        return selectList(new LambdaQueryWrapperX<SrvIssueDO>()
                .eq(SrvIssueDO::getTaskId, taskId)
                .orderByDesc(SrvIssueDO::getId));
    }

    default PageResult<SrvIssueDO> selectPage(SrvIssuePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SrvIssueDO>()
                .eqIfPresent(SrvIssueDO::getTaskId, reqVO.getTaskId())
                .likeIfPresent(SrvIssueDO::getCode, reqVO.getCode())
                .likeIfPresent(SrvIssueDO::getName, reqVO.getName())
                .eqIfPresent(SrvIssueDO::getSeverity, reqVO.getSeverity())
                .eqIfPresent(SrvIssueDO::getOwnerUserId, reqVO.getOwnerUserId())
                .eqIfPresent(SrvIssueDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(SrvIssueDO::getDeadline, reqVO.getDeadline())
                .orderByDesc(SrvIssueDO::getId));
    }

}
