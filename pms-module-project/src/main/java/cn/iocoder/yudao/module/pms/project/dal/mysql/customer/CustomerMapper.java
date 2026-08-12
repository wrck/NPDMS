package cn.iocoder.yudao.module.pms.project.dal.mysql.customer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.customer.vo.CustomerPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.customer.CustomerDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PMS 客户 Mapper
 */
@Mapper
public interface CustomerMapper extends BaseMapperX<CustomerDO> {

    default PageResult<CustomerDO> selectPage(CustomerPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CustomerDO>()
                .likeIfPresent(CustomerDO::getCode, reqVO.getCode())
                .likeIfPresent(CustomerDO::getName, reqVO.getName())
                .eqIfPresent(CustomerDO::getStatus, reqVO.getStatus())
                .orderByDesc(CustomerDO::getId));
    }

    default CustomerDO selectByCode(String code) {
        return selectOne(CustomerDO::getCode, code);
    }

}
