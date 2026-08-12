package cn.iocoder.yudao.module.pms.service.dal.mysql.srvreport;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvreport.vo.SrvReportPageReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvreport.SrvReportDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SrvReportMapper extends BaseMapperX<SrvReportDO> {

    default SrvReportDO selectByTaskIdAndCode(Long taskId, String code) {
        return selectOne(new LambdaQueryWrapperX<SrvReportDO>()
                .eq(SrvReportDO::getTaskId, taskId)
                .eq(SrvReportDO::getCode, code));
    }

    default PageResult<SrvReportDO> selectPage(SrvReportPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SrvReportDO>()
                .eqIfPresent(SrvReportDO::getTaskId, reqVO.getTaskId())
                .likeIfPresent(SrvReportDO::getCode, reqVO.getCode())
                .eqIfPresent(SrvReportDO::getReportType, reqVO.getReportType())
                .eqIfPresent(SrvReportDO::getStatus, reqVO.getStatus())
                .eqIfPresent(SrvReportDO::getGeneratedBy, reqVO.getGeneratedBy())
                .betweenIfPresent(SrvReportDO::getGeneratedTime, reqVO.getGeneratedTime())
                .orderByDesc(SrvReportDO::getId));
    }

}
