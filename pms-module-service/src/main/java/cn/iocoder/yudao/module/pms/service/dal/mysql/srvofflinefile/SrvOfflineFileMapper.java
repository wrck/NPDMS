package cn.iocoder.yudao.module.pms.service.dal.mysql.srvofflinefile;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvofflinefile.vo.SrvOfflineFilePageReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvofflinefile.SrvOfflineFileDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SrvOfflineFileMapper extends BaseMapperX<SrvOfflineFileDO> {

    default SrvOfflineFileDO selectByTaskIdAndCode(Long taskId, String code) {
        return selectOne(new LambdaQueryWrapperX<SrvOfflineFileDO>()
                .eq(SrvOfflineFileDO::getTaskId, taskId)
                .eq(SrvOfflineFileDO::getCode, code));
    }

    default PageResult<SrvOfflineFileDO> selectPage(SrvOfflineFilePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SrvOfflineFileDO>()
                .eqIfPresent(SrvOfflineFileDO::getTaskId, reqVO.getTaskId())
                .likeIfPresent(SrvOfflineFileDO::getCode, reqVO.getCode())
                .eqIfPresent(SrvOfflineFileDO::getParseStatus, reqVO.getParseStatus())
                .eqIfPresent(SrvOfflineFileDO::getParsedBy, reqVO.getParsedBy())
                .betweenIfPresent(SrvOfflineFileDO::getParsedTime, reqVO.getParsedTime())
                .orderByDesc(SrvOfflineFileDO::getId));
    }

}
