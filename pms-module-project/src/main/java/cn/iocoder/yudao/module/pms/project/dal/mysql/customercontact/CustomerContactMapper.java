package cn.iocoder.yudao.module.pms.project.dal.mysql.customercontact;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.customercontact.vo.CustomerContactPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.customercontact.CustomerContactDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 客户联系人 Mapper
 */
@Mapper
public interface CustomerContactMapper extends BaseMapperX<CustomerContactDO> {

    default PageResult<CustomerContactDO> selectPage(CustomerContactPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CustomerContactDO>()
                .eqIfPresent(CustomerContactDO::getCustomerId, reqVO.getCustomerId())
                .likeIfPresent(CustomerContactDO::getName, reqVO.getName())
                .eqIfPresent(CustomerContactDO::getPrimaryFlag, reqVO.getPrimaryFlag())
                .eqIfPresent(CustomerContactDO::getStatus, reqVO.getStatus())
                .orderByDesc(CustomerContactDO::getId));
    }

    default List<CustomerContactDO> selectListByCustomerId(Long customerId) {
        return selectList(new LambdaQueryWrapperX<CustomerContactDO>()
                .eq(CustomerContactDO::getCustomerId, customerId)
                .orderByAsc(CustomerContactDO::getId));
    }

    default CustomerContactDO selectActivePrimaryByCustomerId(Long customerId) {
        return selectOne(new LambdaQueryWrapperX<CustomerContactDO>()
                .eq(CustomerContactDO::getCustomerId, customerId)
                .eq(CustomerContactDO::getPrimaryFlag, Boolean.TRUE)
                .eq(CustomerContactDO::getStatus, 0));
    }

}
