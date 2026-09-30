package cn.iocoder.yudao.module.pms.commerce.service.contract;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 老系统 query-project-bycontractno 取值规则：
 * 项目名称 = IFNULL(IF(订单salesType='01', 订单项目名, 执行单项目名), 订单项目名)；
 * 主订单 = 下单时间最早者（多订单并列取单号最小）；主执行单 = 提交时间最新者。
 */
public final class CreationSourceResolver {

    public record Primary(SalesOrderDO order, CrmExecutionOrderDO execution, String projectName,
                   String customerCode, String customerName, String companyCode, String companyName,
                   LocalDateTime orderCreateTime, String customerProjectName, String majorProjectLevel,
                   String projectType, String marketCode, String marketName, String systemCode,
                   String systemName, String expendCode, String expendName, String industryCode,
                   String industryName, Long executionOrderId, String executionNo) {
    }

    private CreationSourceResolver() {
    }

    public static Primary resolve(List<SalesOrderDO> orders, List<CrmExecutionOrderDO> executions) {
        SalesOrderDO order = orders.stream()
                .min(Comparator.comparing(SalesOrderDO::getOrderCreateTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SalesOrderDO::getOrderNo,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        CrmExecutionOrderDO execution = executions.stream()
                .max(Comparator.comparing(CrmExecutionOrderDO::getSubmitTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        String projectName = null;
        if (order != null && "01".equals(order.getSalesType())) {
            projectName = order.getSourceProjectName();
        } else if (execution != null) {
            projectName = execution.getProjectName();
        }
        if (projectName == null && order != null) {
            projectName = order.getSourceProjectName();
        }
        return new Primary(order, execution, projectName,
                order == null ? null : order.getCustomerCode(),
                order == null ? null : order.getCustomerName(),
                order == null ? null : order.getCompanyCode(),
                order == null ? null : order.getCompanyName(),
                order == null ? null : order.getOrderCreateTime(),
                execution == null ? null : execution.getCustomerProjectName(),
                execution == null ? null : execution.getMajorProjectLevel(),
                execution == null ? null : execution.getProjectType(),
                execution == null ? null : execution.getMarketCode(),
                execution == null ? null : execution.getMarketName(),
                execution == null ? null : execution.getSystemCode(),
                execution == null ? null : execution.getSystemName(),
                execution == null ? null : execution.getExpendCode(),
                execution == null ? null : execution.getExpendName(),
                execution == null ? null : execution.getIndustryCode(),
                execution == null ? null : execution.getIndustryName(),
                execution == null ? null : execution.getId(),
                execution == null ? null : execution.getExecutionNo());
    }
}
