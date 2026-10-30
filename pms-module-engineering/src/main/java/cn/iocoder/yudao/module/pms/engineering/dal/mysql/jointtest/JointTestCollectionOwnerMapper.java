package cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.JointTestDO;
import org.apache.ibatis.annotations.*;
@Mapper public interface JointTestCollectionOwnerMapper extends BaseMapperX<JointTestDO> {
    JointTestDO lockById(@Param("id") Long id);
}
