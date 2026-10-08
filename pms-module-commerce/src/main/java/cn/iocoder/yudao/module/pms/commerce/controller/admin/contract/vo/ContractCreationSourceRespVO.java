package cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractAccessService;
import cn.iocoder.yudao.module.pms.commerce.service.contract.CreationSourceResolver;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 合同主档单入口的项目创建取值预览。
 * resolved 建议值沿用老系统 query-project-bycontractno 规则：
 * 项目名称 = IFNULL(IF(订单salesType='01', 订单项目名, 执行单项目名), 订单项目名)；
 * 客户/下单公司/下单时间来自所选订单；CRM字段仅来自该订单关联的执行单。
 */
public record ContractCreationSourceRespVO(Contract contract, List<Order> orders,
                                           List<ExecutionOrder> executionOrders,
                                           List<String> lineExecutionNos, Resolved resolved, String sourceFingerprint) {

    public static ContractCreationSourceRespVO from(ContractAccessService.CreationSourceDetail detail,
                                                    boolean sensitiveReadable) {
        return from(detail, sensitiveReadable, null);
    }

    public static ContractCreationSourceRespVO from(ContractAccessService.CreationSourceDetail detail,
                                                    boolean sensitiveReadable, Long salesOrderId) {
        ContractDO contract = detail.contract();
        CreationSourceResolver.validateContractOrders(contract, detail.orders());
        var primary = CreationSourceResolver.resolve(detail.orders(), detail.executionOrders(), salesOrderId);
        var order = primary.order();
        var execution = primary.execution();
        return new ContractCreationSourceRespVO(
                new Contract(contract.getId(), contract.getContractNo(), contract.getContractName(),
                        sensitiveReadable ? contract.getContractAmount() : null, sensitiveReadable ? contract.getCurrencyCode() : null,
                        contract.getCompanyCode(), contract.getCompanyName(),
                        sensitiveReadable ? contract.getCustomerCode() : null,
                        sensitiveReadable ? contract.getCustomerName() : null),
                detail.orders().stream().map(o -> new Order(o.getId(), o.getOrderNo(), o.getSalesType(),
                        sensitiveReadable ? o.getOrderAmount() : null, sensitiveReadable ? o.getCurrencyCode() : null,
                        o.getOrderCreateTime(), o.getCustomerRequiredTime(),
                        sensitiveReadable ? o.getCustomerCode() : null,
                        sensitiveReadable ? o.getCustomerName() : null, o.getExecutionNo())).toList(),
                detail.orders().stream().map(o -> CreationSourceResolver.executionFor(o, detail.executionOrders()))
                        .filter(java.util.Objects::nonNull).distinct().map(e -> new ExecutionOrder(e.getId(), e.getExecutionNo(),
                        e.getProjectCode(), e.getProjectName(), e.getCustomerProjectName(),
                        e.getMajorProjectLevel(), e.getProjectType(), sensitiveReadable ? e.getProjectAmount() : null,
                        e.getMarketCode(), e.getMarketName(), e.getSystemCode(), e.getSystemName(),
                        e.getExpendCode(), e.getExpendName(), e.getIndustryCode(), e.getIndustryName(),
                        e.getDepartmentCode(), e.getDepartmentName(), e.getCompanyCode(), e.getCompanyName(),
                        e.getSalesRepCode(), e.getSalesRepName(), e.getSubmitTime())).toList(),
                detail.lineExecutionNos(),
                new Resolved(order == null ? null : order.getId(), primary.projectName(),
                        !sensitiveReadable ? null : primary.customerCode(),
                        !sensitiveReadable ? null : primary.customerName(),
                        primary.companyCode(), primary.companyName(), primary.orderCreateTime(),
                        primary.customerProjectName(), primary.majorProjectLevel(), primary.projectType(),
                        primary.marketCode(), primary.marketName(), primary.systemCode(), primary.systemName(),
                        primary.expendCode(), primary.expendName(), primary.industryCode(), primary.industryName(),
                        primary.executionOrderId(), primary.executionNo()), CreationSourceResolver.fingerprint(detail));
    }

    public record Contract(Long id, String contractNo, String contractName, BigDecimal contractAmount,
                           String currencyCode, String companyCode, String companyName,
                           String customerCode, String customerName) {
    }

    public record Order(Long id, String orderNo, String salesType, BigDecimal orderAmount,
                        String currencyCode, LocalDateTime orderCreateTime, LocalDateTime customerRequiredTime,
                        String customerCode, String customerName, String executionNo) {
    }

    public record ExecutionOrder(Long id, String executionNo, String projectCode, String projectName,
                                 String customerProjectName, String majorProjectLevel, String projectType,
                                 BigDecimal projectAmount, String marketCode, String marketName,
                                 String systemCode, String systemName, String expendCode, String expendName,
                                 String industryCode, String industryName, String departmentCode,
                                 String departmentName, String companyCode, String companyName,
                                 String salesRepCode, String salesRepName, LocalDateTime submitTime) {
    }

    public record Resolved(Long salesOrderId, String projectName, String customerCode, String customerName,
                           String companyCode, String companyName, LocalDateTime orderCreateTime,
                           String customerProjectName, String majorProjectLevel, String projectType,
                           String marketCode, String marketName, String systemCode, String systemName,
                           String expendCode, String expendName, String industryCode, String industryName,
                           Long executionOrderId, String executionNo) {
    }
}
