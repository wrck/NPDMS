package cn.iocoder.yudao.module.pms.project.dal.mysql.servicelevel;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.servicelevel.vo.CustomerServiceLevelPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.servicelevel.CustomerServiceLevelDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 客户服务等级 Mapper
 */
@Mapper
public interface CustomerServiceLevelMapper extends BaseMapperX<CustomerServiceLevelDO> {

    default PageResult<CustomerServiceLevelDO> selectPage(CustomerServiceLevelPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CustomerServiceLevelDO>()
                .eqIfPresent(CustomerServiceLevelDO::getCustomerId, reqVO.getCustomerId())
                .eqIfPresent(CustomerServiceLevelDO::getLevel, reqVO.getLevel())
                .eqIfPresent(CustomerServiceLevelDO::getStatus, reqVO.getStatus())
                .orderByDesc(CustomerServiceLevelDO::getId));
    }

    default List<CustomerServiceLevelDO> selectListByCustomerId(Long customerId) {
        return selectList(new LambdaQueryWrapperX<CustomerServiceLevelDO>()
                .eq(CustomerServiceLevelDO::getCustomerId, customerId)
                .orderByDesc(CustomerServiceLevelDO::getId));
    }

}
