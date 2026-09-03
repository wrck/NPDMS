package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CredentialGrantDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.EffectiveCredentialGrantQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CredentialGrantMapper extends BaseMapperX<CredentialGrantDO> {

    List<CredentialGrantDO> selectEffective(@Param("query") EffectiveCredentialGrantQuery query);
}
