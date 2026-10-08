package cn.iocoder.yudao.module.pms.commerce.service.contract;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.List;

/**
 * 老系统 query-project-bycontractno 取值规则：
 * 项目名称 = IFNULL(IF(订单salesType='01', 订单项目名, 执行单项目名), 订单项目名)；
 * 仅沿所选订单的 executionNo 取值；多个订单须明确选择，跨来源同号不得任选。
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
        return resolve(orders, executions, null);
    }

    public static Primary resolve(List<SalesOrderDO> orders, List<CrmExecutionOrderDO> executions,
                                  Long salesOrderId) {
        for (SalesOrderDO candidate : orders) executionFor(candidate, executions);
        SalesOrderDO order;
        if (salesOrderId != null) {
            order = orders.stream().filter(value -> salesOrderId.equals(value.getId())).findFirst()
                    .orElseThrow(() -> invalidParamException("所选销售订单不属于该合同的有效订单"));
        } else {
            order = orders.size() == 1 ? orders.getFirst() : null;
        }
        CrmExecutionOrderDO execution = executionFor(order, executions);
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
    /** The order stores no CRM source-system key; duplicate numbers are ambiguous, not interchangeable. */
    public static CrmExecutionOrderDO executionFor(SalesOrderDO order, List<CrmExecutionOrderDO> executions) {
        if (order == null || order.getExecutionNo() == null || order.getExecutionNo().isBlank()) return null;
        List<CrmExecutionOrderDO> related = executions.stream()
                .filter(value -> Objects.equals(order.getExecutionNo(), value.getExecutionNo())).toList();
        if (related.size() > 1) throw invalidParamException("执行单号在多个来源中存在歧义，请先核对同步关系");
        if (related.isEmpty()) return null;
        CrmExecutionOrderDO execution = related.getFirst();
        requireCompany(order.getCompanyCode(), order.getCompanyId(), execution.getCompanyCode(), execution.getCompanyId());
        if (execution.getSourceSystem() == null || execution.getSourceSystem().isBlank()
                || order.getTenantId() != null && execution.getTenantId() != null
                    && !order.getTenantId().equals(execution.getTenantId()))
            throw invalidParamException("执行单来源身份不完整或租户不一致，请核对同步关系");
        return execution;
    }

    public static void validateContractOrders(cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO contract,
                                               List<SalesOrderDO> orders) {
        for (var order : orders) {
            requireCompany(contract.getCompanyCode(), contract.getCompanyId(), order.getCompanyCode(), order.getCompanyId());
            if (contract.getTenantId() != null && order.getTenantId() != null
                    && !contract.getTenantId().equals(order.getTenantId()))
                throw invalidParamException("合同与订单租户不一致");
        }
    }
    private static void requireCompany(String expectedCode, Long expectedId, String actualCode, Long actualId) {
        if (expectedCode == null || expectedCode.isBlank() || actualCode == null || actualCode.isBlank()
                || !expectedCode.equals(actualCode)
                || expectedId != null && actualId != null && !expectedId.equals(actualId))
            throw invalidParamException("合同、订单与执行单公司身份不一致或缺失，请核对同步关系");
    }

    public static String fingerprint(ContractAccessService.CreationSourceDetail detail) {
        // Stable ordering makes a database plan change irrelevant to the source evidence digest.
        var facts = java.util.List.of(detail.contract(),
                detail.orders().stream().sorted(java.util.Comparator.comparing(SalesOrderDO::getId)).toList(),
                detail.executionOrders().stream().sorted(java.util.Comparator.comparing(CrmExecutionOrderDO::getId)).toList(),
                detail.orderContractRelations().stream().sorted(java.util.Comparator.comparing(
                        cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO::getId)).toList());
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(facts));
    }

}
