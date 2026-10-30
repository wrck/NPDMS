package cn.iocoder.yudao.module.pms.platform.dal.mysql.result;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.result.PlatformBusinessResultDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PlatformBusinessResultMapper extends BaseMapperX<PlatformBusinessResultDO> {

    default Optional<PlatformBusinessResultDO> selectByBasis(String resultType, String ownerModule,
                                                             String entityType, Long entityId, String formationBasis) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapperX<PlatformBusinessResultDO>()
                .eq(PlatformBusinessResultDO::getResultType, resultType)
                .eq(PlatformBusinessResultDO::getOwnerModule, ownerModule)
                .eq(PlatformBusinessResultDO::getEntityType, entityType)
                .eq(PlatformBusinessResultDO::getEntityId, entityId)
                .eq(PlatformBusinessResultDO::getFormationBasis, formationBasis)));
    }

    default Optional<PlatformBusinessResultDO> selectCurrentValid(String resultType, String ownerModule,
                                                                  String entityType, Long entityId) {
        return selectList(new LambdaQueryWrapperX<PlatformBusinessResultDO>()
                .eq(PlatformBusinessResultDO::getResultType, resultType)
                .eq(PlatformBusinessResultDO::getOwnerModule, ownerModule)
                .eq(PlatformBusinessResultDO::getEntityType, entityType)
                .eq(PlatformBusinessResultDO::getEntityId, entityId)
                .eq(PlatformBusinessResultDO::getValid, true)
                .orderByDesc(PlatformBusinessResultDO::getFormedAt))
                .stream().findFirst();
    }

    default List<PlatformBusinessResultDO> selectInventory(String resultType, String ownerModule,
                                                           String entityType, Long entityId,
                                                           String resultId, boolean onlyValid) {
        return selectList(new LambdaQueryWrapperX<PlatformBusinessResultDO>()
                .eq(PlatformBusinessResultDO::getResultType, resultType)
                .eqIfPresent(PlatformBusinessResultDO::getOwnerModule, ownerModule)
                .eqIfPresent(PlatformBusinessResultDO::getEntityType, entityType)
                .eqIfPresent(PlatformBusinessResultDO::getEntityId, entityId)
                .eqIfPresent(PlatformBusinessResultDO::getResultId, resultId)
                .eq(onlyValid, PlatformBusinessResultDO::getValid, true)
                .orderByDesc(PlatformBusinessResultDO::getId));
    }
}
