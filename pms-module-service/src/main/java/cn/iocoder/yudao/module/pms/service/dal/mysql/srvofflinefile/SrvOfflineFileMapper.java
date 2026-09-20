package cn.iocoder.yudao.module.pms.service.dal.mysql.srvofflinefile;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvofflinefile.vo.SrvOfflineFilePageReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvofflinefile.SrvOfflineFileRetiredDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@Deprecated
public interface SrvOfflineFileMapper extends BaseMapperX<SrvOfflineFileRetiredDO> {

    default SrvOfflineFileRetiredDO selectByTaskIdAndCodeRetired(Long taskId, String code) {
        return selectOne(new LambdaQueryWrapperX<SrvOfflineFileRetiredDO>()
                .eq(SrvOfflineFileRetiredDO::getTaskId, taskId)
                .eq(SrvOfflineFileRetiredDO::getCode, code));
    }

    default PageResult<SrvOfflineFileRetiredDO> selectPageRetired(SrvOfflineFilePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SrvOfflineFileRetiredDO>()
                .eqIfPresent(SrvOfflineFileRetiredDO::getTaskId, reqVO.getTaskId())
                .likeIfPresent(SrvOfflineFileRetiredDO::getCode, reqVO.getCode())
                .eqIfPresent(SrvOfflineFileRetiredDO::getParseStatus, reqVO.getParseStatus())
                .eqIfPresent(SrvOfflineFileRetiredDO::getParsedBy, reqVO.getParsedBy())
                .betweenIfPresent(SrvOfflineFileRetiredDO::getParsedTime, reqVO.getParsedTime())
                .orderByDesc(SrvOfflineFileRetiredDO::getId));
    }

}
