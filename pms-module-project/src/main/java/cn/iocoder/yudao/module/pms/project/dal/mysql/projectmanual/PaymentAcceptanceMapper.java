package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.PaymentAcceptanceTarget;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.PaymentAcceptanceQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.PaymentAcceptanceUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PaymentAcceptanceMapper {
    record Binding(Long id, Long tenantId, String nodeType, Long nodeId, String sourceOwner, String sourceKey) {}
    List<PaymentAcceptanceTarget> selectTargets(@Param("query") PaymentAcceptanceQuery query);
    int bind(@Param("query") Binding query);
    int updateAcceptance(@Param("query") PaymentAcceptanceUpdate query);
}
