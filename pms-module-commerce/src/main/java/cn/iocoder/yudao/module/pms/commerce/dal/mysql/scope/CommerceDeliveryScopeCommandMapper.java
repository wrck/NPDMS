package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeProjectVersionDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.OrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.CommerceDeliveryScopeCommandQuery.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CommerceDeliveryScopeCommandMapper {

    int insertProjectVersionIfAbsent(ProjectVersionSeed query);

    DeliveryScopeProjectVersionDO selectProjectVersionForUpdate(ProjectLock query);

    List<OrderLineDO> selectOrderLinesForUpdate(OrderLinesLock query);

    List<DeliveryScopeDO> selectCurrentScopesForUpdate(CurrentScopesLock query);

    List<DeliveryScopeDetailDO> selectScopeDetailsForUpdate(ScopeDetailsLock query);

    List<AllocationVersionFact> selectMaxAllocationVersions(AllocationVersionQuery query);

    int endScope(EndScope query);

    int endDetails(EndDetails query);

    int advanceProjectVersion(AdvanceProjectVersion query);
}
