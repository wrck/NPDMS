package cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo;

import cn.iocoder.yudao.module.pms.commerce.controller.admin.order.vo.SalesOrderRespVO;

import java.time.LocalDateTime;
import java.util.List;

public record ContractDetailRespVO(
        ContractRespVO contract,
        List<SalesOrderRespVO> relatedOrders,
        List<ProjectRelationRespVO> projectRelations,
        String sourceSystem,
        String sourceVersion,
        LocalDateTime sourceSyncTime,
        LocalDateTime sourceUpdatedAt) {

    public record ProjectRelationRespVO(Long id, Long projectId, String relationRole,
                                        String status, Integer version) {
    }
}
