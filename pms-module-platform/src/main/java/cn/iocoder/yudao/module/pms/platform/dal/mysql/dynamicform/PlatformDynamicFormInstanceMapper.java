package cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform;

import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.PlatformDynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormInstanceLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormInstancePageQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormInstanceOwnerQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormInstanceRowQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormInstanceValueUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlatformDynamicFormInstanceMapper {

    int insert(@Param("row") PlatformDynamicFormInstanceDO row);

    List<PlatformDynamicFormInstanceDO> selectPage(@Param("query") DynamicFormInstancePageQuery query);

    long selectCountPage(@Param("query") DynamicFormInstancePageQuery query);

    PlatformDynamicFormInstanceDO selectByRow(@Param("query") DynamicFormInstanceRowQuery query);

    PlatformDynamicFormInstanceDO selectForUpdate(@Param("query") DynamicFormInstanceLockQuery query);

    PlatformDynamicFormInstanceDO selectByOwner(@Param("query") DynamicFormInstanceOwnerQuery query);

    int updateValueIfMatch(@Param("update") DynamicFormInstanceValueUpdate update);
}
