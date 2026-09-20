package cn.iocoder.yudao.module.pms.service.dal.mysql.srvissue;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssuePageReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvissue.SrvIssueRetiredDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
@Deprecated
public interface SrvIssueMapper extends BaseMapperX<SrvIssueRetiredDO> {

    default SrvIssueRetiredDO selectByTaskIdAndCodeRetired(Long taskId, String code) {
        return selectOne(new LambdaQueryWrapperX<SrvIssueRetiredDO>()
                .eq(SrvIssueRetiredDO::getTaskId, taskId)
                .eq(SrvIssueRetiredDO::getCode, code));
    }

    default List<SrvIssueRetiredDO> selectListByTaskIdRetired(Long taskId) {
        return selectList(new LambdaQueryWrapperX<SrvIssueRetiredDO>()
                .eq(SrvIssueRetiredDO::getTaskId, taskId)
                .orderByDesc(SrvIssueRetiredDO::getId));
    }

    default PageResult<SrvIssueRetiredDO> selectPageRetired(SrvIssuePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SrvIssueRetiredDO>()
                .eqIfPresent(SrvIssueRetiredDO::getTaskId, reqVO.getTaskId())
                .likeIfPresent(SrvIssueRetiredDO::getCode, reqVO.getCode())
                .likeIfPresent(SrvIssueRetiredDO::getName, reqVO.getName())
                .eqIfPresent(SrvIssueRetiredDO::getSeverity, reqVO.getSeverity())
                .eqIfPresent(SrvIssueRetiredDO::getOwnerUserId, reqVO.getOwnerUserId())
                .eqIfPresent(SrvIssueRetiredDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(SrvIssueRetiredDO::getDeadline, reqVO.getDeadline())
                .orderByDesc(SrvIssueRetiredDO::getId));
    }

}
