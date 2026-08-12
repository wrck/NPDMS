package cn.iocoder.yudao.module.pms.engineering.dal.mysql.announcement;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcement.vo.AnnouncementPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.announcement.AnnouncementDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnnouncementMapper extends BaseMapperX<AnnouncementDO> {

    default PageResult<AnnouncementDO> selectPage(AnnouncementPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AnnouncementDO>()
                .likeIfPresent(AnnouncementDO::getCode, reqVO.getCode())
                .likeIfPresent(AnnouncementDO::getTitle, reqVO.getTitle())
                .eqIfPresent(AnnouncementDO::getAnnouncementType, reqVO.getAnnouncementType())
                .likeIfPresent(AnnouncementDO::getProductModel, reqVO.getProductModel())
                .eqIfPresent(AnnouncementDO::getSeverity, reqVO.getSeverity())
                .eqIfPresent(AnnouncementDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(AnnouncementDO::getPublishDate, reqVO.getPublishDateRange())
                .betweenIfPresent(AnnouncementDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(AnnouncementDO::getId));
    }

    /**
     * 按编号查询，用于全局唯一性校验
     */
    default AnnouncementDO selectByCode(String code) {
        return selectOne(AnnouncementDO::getCode, code);
    }

}
