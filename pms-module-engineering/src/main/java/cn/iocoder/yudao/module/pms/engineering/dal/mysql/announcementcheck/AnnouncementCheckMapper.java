package cn.iocoder.yudao.module.pms.engineering.dal.mysql.announcementcheck;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcementcheck.vo.AnnouncementCheckPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.announcementcheck.AnnouncementCheckDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnnouncementCheckMapper extends BaseMapperX<AnnouncementCheckDO> {

    default PageResult<AnnouncementCheckDO> selectPage(AnnouncementCheckPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AnnouncementCheckDO>()
                .eqIfPresent(AnnouncementCheckDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(AnnouncementCheckDO::getAnnouncementId, reqVO.getAnnouncementId())
                .likeIfPresent(AnnouncementCheckDO::getCode, reqVO.getCode())
                .likeIfPresent(AnnouncementCheckDO::getDeviceSerial, reqVO.getDeviceSerial())
                .likeIfPresent(AnnouncementCheckDO::getDeviceModel, reqVO.getDeviceModel())
                .eqIfPresent(AnnouncementCheckDO::getMatchResult, reqVO.getMatchResult())
                .eqIfPresent(AnnouncementCheckDO::getEomStatus, reqVO.getEomStatus())
                .eqIfPresent(AnnouncementCheckDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(AnnouncementCheckDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(AnnouncementCheckDO::getId));
    }

    /**
     * 按编号查询，用于全局唯一性校验
     */
    default AnnouncementCheckDO selectByCode(String code) {
        return selectOne(AnnouncementCheckDO::getCode, code);
    }

    /**
     * 按项目ID查询数量
     */
    default Long selectCountByProjectId(Long projectId) {
        return selectCount(AnnouncementCheckDO::getProjectId, projectId);
    }

}
