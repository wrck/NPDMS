package cn.iocoder.yudao.module.pms.commerce.service.contract;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 老系统 query-project-bycontractno 取值规则回归：
 * 项目名称 = IFNULL(IF(订单salesType='01', 订单项目名, 执行单项目名), 订单项目名)；
 * 主订单 = 下单时间最早者（并列取单号最小）；主执行单 = 提交时间最新者；CRM权威值取自主执行单。
 */
class CreationSourceResolverTest {

    @Test
    void directSignOrderWinsProjectNameOverExecution() {
        SalesOrderDO order = order("O-2", "01", "订单项目名", LocalDateTime.of(2026, 9, 1, 10, 0));
        CrmExecutionOrderDO execution = execution("EX-1", "执行单项目名", LocalDateTime.of(2026, 9, 2, 10, 0));

        CreationSourceResolver.Primary primary = CreationSourceResolver.resolve(List.of(order), List.of(execution));

        assertEquals("订单项目名", primary.projectName());
        assertSame(order, primary.order());
        assertSame(execution, primary.execution());
    }

    @Test
    void nonDirectSignFallsBackToExecutionProjectNameThenOrderName() {
        SalesOrderDO order = order("O-1", "02", "订单项目名", LocalDateTime.of(2026, 9, 1, 10, 0));
        CrmExecutionOrderDO execution = execution("EX-1", "执行单项目名", LocalDateTime.of(2026, 9, 2, 10, 0));
        assertEquals("执行单项目名", CreationSourceResolver.resolve(List.of(order), List.of(execution)).projectName());

        assertEquals("订单项目名",
                CreationSourceResolver.resolve(List.of(order), List.of()).projectName(),
                "无执行单时回退订单项目名，不返回空值");
    }

    @Test
    void primaryOrderIsEarliestCreateTimeWithOrderNoTieBreak() {
        SalesOrderDO later = order("O-1", "01", "晚创建", LocalDateTime.of(2026, 9, 3, 10, 0));
        SalesOrderDO tieHighNo = order("O-2", "01", "同刻单号大", LocalDateTime.of(2026, 9, 1, 10, 0));
        SalesOrderDO tieLowNo = order("O-0", "01", "同刻单号小", LocalDateTime.of(2026, 9, 1, 10, 0));

        CreationSourceResolver.Primary primary = CreationSourceResolver.resolve(
                List.of(later, tieHighNo, tieLowNo), List.of());

        assertSame(tieLowNo, primary.order(), "同刻并列时取单号最小者");
        assertEquals("同刻单号小", primary.projectName());
    }

    @Test
    void primaryExecutionIsLatestSubmitAndCarriesCrmAuthoritativeValues() {
        CrmExecutionOrderDO old = execution("EX-1", "旧提交", LocalDateTime.of(2026, 9, 1, 10, 0));
        CrmExecutionOrderDO latest = execution("EX-2", "最新提交", LocalDateTime.of(2026, 9, 5, 10, 0));
        latest.setCustomerProjectName("客户项目名称");
        latest.setMajorProjectLevel("A");
        latest.setProjectType("ENGINEERING");
        latest.setMarketCode("M1");
        latest.setMarketName("市场一");
        latest.setSystemCode("SYS1");
        latest.setSystemName("系统一");
        latest.setExpendCode("E1");
        latest.setExpendName("拓展一");
        latest.setIndustryCode("I1");
        latest.setIndustryName("行业一");
        SalesOrderDO order = order("O-1", "02", "订单项目名", LocalDateTime.of(2026, 9, 1, 9, 0));
        order.setCustomerCode("CU-1");
        order.setCustomerName("客户一");
        order.setCompanyCode("C01");
        order.setCompanyName("公司一");

        CreationSourceResolver.Primary primary = CreationSourceResolver.resolve(List.of(order), List.of(old, latest));

        assertSame(latest, primary.execution());
        assertEquals("EX-2", primary.executionNo());
        assertEquals(latest.getId(), primary.executionOrderId());
        assertEquals("客户项目名称", primary.customerProjectName());
        assertEquals("A", primary.majorProjectLevel());
        assertEquals("ENGINEERING", primary.projectType());
        assertEquals("M1", primary.marketCode());
        assertEquals("市场一", primary.marketName());
        assertEquals("SYS1", primary.systemCode());
        assertEquals("E1", primary.expendCode());
        assertEquals("I1", primary.industryCode());
        assertEquals("CU-1", primary.customerCode());
        assertEquals("客户一", primary.customerName());
        assertEquals("C01", primary.companyCode());
        assertEquals("公司一", primary.companyName());
        assertEquals(order.getOrderCreateTime(), primary.orderCreateTime());
    }

    @Test
    void emptyChainResolvesToAllNulls() {
        CreationSourceResolver.Primary primary = CreationSourceResolver.resolve(List.of(), List.of());

        assertNull(primary.order());
        assertNull(primary.execution());
        assertNull(primary.projectName());
        assertNull(primary.executionOrderId());
    }

    private SalesOrderDO order(String orderNo, String salesType, String projectName, LocalDateTime createTime) {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(201L);
        order.setOrderNo(orderNo);
        order.setSalesType(salesType);
        order.setSourceProjectName(projectName);
        order.setOrderCreateTime(createTime);
        order.setOrderAmount(BigDecimal.TEN);
        return order;
    }

    private CrmExecutionOrderDO execution(String executionNo, String projectName, LocalDateTime submitTime) {
        CrmExecutionOrderDO execution = new CrmExecutionOrderDO();
        execution.setId(301L);
        execution.setExecutionNo(executionNo);
        execution.setProjectName(projectName);
        execution.setSubmitTime(submitTime);
        return execution;
    }
}
