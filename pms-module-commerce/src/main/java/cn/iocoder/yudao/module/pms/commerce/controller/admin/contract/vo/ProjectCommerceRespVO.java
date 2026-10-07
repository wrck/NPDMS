package cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo;
import java.util.List;
import java.time.LocalDateTime;
public record ProjectCommerceRespVO(List<ContractRespVO> contracts, List<Order> orders, List<ExecutionOrder> executionOrders) {
    public record Order(Long id, String orderNo, String companyName, LocalDateTime orderCreateTime,
        java.math.BigDecimal orderAmount, String currencyCode, String salesType,
        LocalDateTime customerRequiredTime, String executionNo, String customerCode, String customerName) {}
    public record ExecutionOrder(Long id, String executionNo, String salesRepCode, String salesRepName,
        String marketName, String systemName, String expendName, String industryName,
        String departmentName, String companyName, String customerProjectName, String finalCustomerName,
        String agentName, String projectManagerName, String serviceTypeName, String channelName,
        LocalDateTime submitTime, String sourceSystem, LocalDateTime sourceSyncTime,
        String projectCode, String projectName, java.math.BigDecimal projectAmount,
        String majorProjectLevel, String projectType, String marketCode, String systemCode,
        String expendCode, String industryCode) {}
}
