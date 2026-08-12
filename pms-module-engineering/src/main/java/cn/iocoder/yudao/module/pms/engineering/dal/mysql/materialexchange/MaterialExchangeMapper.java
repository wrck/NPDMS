package cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.MaterialExchangePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.MaterialExchangeDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MaterialExchangeMapper extends BaseMapperX<MaterialExchangeDO> {

    default PageResult<MaterialExchangeDO> selectPage(MaterialExchangePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MaterialExchangeDO>()
                .eqIfPresent(MaterialExchangeDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(MaterialExchangeDO::getCode, reqVO.getCode())
                .likeIfPresent(MaterialExchangeDO::getName, reqVO.getName())
                .eqIfPresent(MaterialExchangeDO::getExchangeType, reqVO.getExchangeType())
                .eqIfPresent(MaterialExchangeDO::getEquipmentId, reqVO.getEquipmentId())
                .eqIfPresent(MaterialExchangeDO::getCrmPushStatus, reqVO.getCrmPushStatus())
                .eqIfPresent(MaterialExchangeDO::getStatus, reqVO.getStatus())
                .eqIfPresent(MaterialExchangeDO::getApplicantUserId, reqVO.getApplicantUserId())
                .betweenIfPresent(MaterialExchangeDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(MaterialExchangeDO::getId));
    }

    /**
     * 按单号查询，用于全局唯一性校验
     */
    default MaterialExchangeDO selectByCode(String code) {
        return selectOne(MaterialExchangeDO::getCode, code);
    }

}
