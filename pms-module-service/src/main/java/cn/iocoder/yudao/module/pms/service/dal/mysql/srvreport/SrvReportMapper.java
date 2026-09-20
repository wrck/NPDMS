package cn.iocoder.yudao.module.pms.service.dal.mysql.srvreport;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvreport.vo.SrvReportPageReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvreport.SrvReportRetiredDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@Deprecated
public interface SrvReportMapper extends BaseMapperX<SrvReportRetiredDO> {

    default SrvReportRetiredDO selectByTaskIdAndCodeRetired(Long taskId, String code) {
        return selectOne(new LambdaQueryWrapperX<SrvReportRetiredDO>()
                .eq(SrvReportRetiredDO::getTaskId, taskId)
                .eq(SrvReportRetiredDO::getCode, code));
    }

    default PageResult<SrvReportRetiredDO> selectPageRetired(SrvReportPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SrvReportRetiredDO>()
                .eqIfPresent(SrvReportRetiredDO::getTaskId, reqVO.getTaskId())
                .likeIfPresent(SrvReportRetiredDO::getCode, reqVO.getCode())
                .eqIfPresent(SrvReportRetiredDO::getReportType, reqVO.getReportType())
                .eqIfPresent(SrvReportRetiredDO::getStatus, reqVO.getStatus())
                .eqIfPresent(SrvReportRetiredDO::getGeneratedBy, reqVO.getGeneratedBy())
                .betweenIfPresent(SrvReportRetiredDO::getGeneratedTime, reqVO.getGeneratedTime())
                .orderByDesc(SrvReportRetiredDO::getId));
    }

}
