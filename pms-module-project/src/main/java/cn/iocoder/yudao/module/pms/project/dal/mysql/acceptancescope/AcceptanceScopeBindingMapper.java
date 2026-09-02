package cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancescope;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancescope.AcceptanceScopeBindingDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancescope.query.AcceptanceScopeBindingIdentityQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancescope.query.AcceptanceScopeCurrentQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AcceptanceScopeBindingMapper extends BaseMapperX<AcceptanceScopeBindingDO> {

    AcceptanceScopeBindingDO selectByIdentityForUpdate(
            @Param("query") AcceptanceScopeBindingIdentityQuery query);

    List<AcceptanceScopeBindingDO> selectCurrentByScopeForUpdate(
            @Param("query") AcceptanceScopeCurrentQuery query);
}
