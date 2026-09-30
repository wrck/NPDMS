package cn.iocoder.yudao.module.pms.commerce.model;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.AuthorityCandidateDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.AuthorityCandidateMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.CrmExecutionOrderMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelContributor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * B4 批次统一目录声明（P12）：合同、销售订单、交付范围、权威候选、CRM 执行单进入统一目录。
 * DPPMS/CRM 同步权威摄取、授权候选-决策流、范围分配等专业写路径保留本域，
 * 声明不开放通用 create/save 操作；技术 outbox 与订单行/关系/明细等子表不进入目录。
 */
@Component
public class CommerceBusinessModelContributor implements BusinessModelContributor {

    private final ContractMapper contractMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final DeliveryScopeMapper deliveryScopeMapper;
    private final AuthorityCandidateMapper authorityCandidateMapper;
    private final CrmExecutionOrderMapper crmExecutionOrderMapper;

    public CommerceBusinessModelContributor(ContractMapper contractMapper,
                                            SalesOrderMapper salesOrderMapper,
                                            DeliveryScopeMapper deliveryScopeMapper,
                                            AuthorityCandidateMapper authorityCandidateMapper,
                                            CrmExecutionOrderMapper crmExecutionOrderMapper) {
        this.contractMapper = contractMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.deliveryScopeMapper = deliveryScopeMapper;
        this.authorityCandidateMapper = authorityCandidateMapper;
        this.crmExecutionOrderMapper = crmExecutionOrderMapper;
    }

    private static BusinessFieldDescriptor field(String code, String name, EntityField.Type type) {
        return new BusinessFieldDescriptor(code, name, type, false, true, true, null);
    }

    private static BusinessFieldDescriptor required(String code, String name, EntityField.Type type) {
        return new BusinessFieldDescriptor(code, name, type, true, true, true, null);
    }

    @Override
    public List<BusinessModelDeclaration> declarations() {
        List<BusinessModelDeclaration> declarations = new ArrayList<>();
        BusinessModelDescriptor contract = new BusinessModelDescriptor("COM", "contract",
                "COM_CONTRACT", 1, BusinessModelKind.AGGREGATE_ROOT, "合同",
                "pms:commerce:contract:query",
                List.of(required("contractNo", "合同编号", EntityField.Type.TEXT),
                        required("contractName", "合同名称", EntityField.Type.TEXT),
                        field("companyCode", "公司编码", EntityField.Type.TEXT),
                        field("companyName", "公司名称", EntityField.Type.TEXT),
                        field("contractType", "合同类型", EntityField.Type.TEXT),
                        field("customerCode", "客户编码", EntityField.Type.TEXT),
                        field("customerName", "客户名称", EntityField.Type.TEXT),
                        field("contractAmount", "合同金额", EntityField.Type.NUMBER),
                        field("currencyCode", "币种", EntityField.Type.TEXT),
                        field("authorityStatus", "权威状态", EntityField.Type.TEXT),
                        field("sourceLifecycleStatus", "来源生命周期状态", EntityField.Type.TEXT),
                        field("sourceSyncTime", "来源同步时间", EntityField.Type.DATETIME),
                        field("status", "状态", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "com_contract");
        declarations.add(new BusinessModelDeclaration(contract, ContractDO.class, contractMapper, null));
        BusinessModelDescriptor salesOrder = new BusinessModelDescriptor("COM", "salesOrder",
                "COM_SALES_ORDER", 1, BusinessModelKind.AGGREGATE_ROOT, "销售订单",
                "pms:commerce:contract:query",
                List.of(required("orderNo", "订单编号", EntityField.Type.TEXT),
                        field("sourceSystem", "来源系统", EntityField.Type.TEXT),
                        field("sourceRecordKey", "来源键", EntityField.Type.TEXT),
                        field("sourceVersion", "来源版本", EntityField.Type.TEXT),
                        field("companyCode", "公司编码", EntityField.Type.TEXT),
                        field("orderType", "订单类型", EntityField.Type.TEXT),
                        field("salesType", "销售类型", EntityField.Type.TEXT),
                        field("customerCode", "客户编码", EntityField.Type.TEXT),
                        field("customerName", "客户名称", EntityField.Type.TEXT),
                        field("sourceProjectName", "来源项目名称", EntityField.Type.TEXT),
                        field("orderAmount", "订单金额", EntityField.Type.NUMBER),
                        field("currencyCode", "币种", EntityField.Type.TEXT),
                        field("authorityStatus", "权威状态", EntityField.Type.TEXT),
                        field("sourceLifecycleStatus", "来源生命周期状态", EntityField.Type.TEXT),
                        field("orderCreateTime", "订单创建时间", EntityField.Type.DATETIME),
                        field("customerRequiredTime", "客户要求时间", EntityField.Type.DATETIME),
                        field("status", "状态", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "com_sales_order");
        declarations.add(new BusinessModelDeclaration(salesOrder, SalesOrderDO.class, salesOrderMapper, null));
        BusinessModelDescriptor deliveryScope = new BusinessModelDescriptor("COM", "deliveryScope",
                "COM_DELIVERY_SCOPE", 1, BusinessModelKind.AGGREGATE_ROOT, "交付范围",
                "pms:commerce:scope:query",
                List.of(field("projectId", "项目编号", EntityField.Type.NUMBER),
                        field("projectCode", "项目编码", EntityField.Type.TEXT),
                        field("projectName", "项目名称", EntityField.Type.TEXT),
                        field("orderNo", "订单编号", EntityField.Type.TEXT),
                        field("lineNo", "订单行号", EntityField.Type.TEXT),
                        field("productCode", "产品编码/物料编码", EntityField.Type.TEXT),
                        field("productDesc", "产品描述/物料描述", EntityField.Type.TEXT),
                        field("allocatedQty", "分配数量", EntityField.Type.NUMBER),
                        field("scopeStatus", "范围状态", EntityField.Type.TEXT),
                        field("allocationVersion", "分配版本", EntityField.Type.NUMBER),
                        field("allocationSource", "分配来源", EntityField.Type.TEXT),
                        field("changeReason", "变更原因", EntityField.Type.TEXT),
                        field("departmentCode", "部门编码", EntityField.Type.TEXT),
                        field("effectiveFrom", "生效时间", EntityField.Type.DATETIME),
                        field("effectiveTo", "失效时间", EntityField.Type.DATETIME),
                        field("status", "状态", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "com_delivery_scope");
        declarations.add(new BusinessModelDeclaration(deliveryScope, DeliveryScopeDO.class, deliveryScopeMapper, null));
        BusinessModelDescriptor authorityCandidate = new BusinessModelDescriptor("COM", "authorityCandidate",
                "COM_AUTHORITY_CANDIDATE", 1, BusinessModelKind.AGGREGATE_ROOT, "权威候选",
                "pms:commerce:authority:reconcile",
                List.of(required("objectType", "对象类型", EntityField.Type.TEXT),
                        field("candidateSourceSystem", "候选来源系统", EntityField.Type.TEXT),
                        field("candidateSourceKey", "候选来源键", EntityField.Type.TEXT),
                        field("candidateVersion", "候选版本", EntityField.Type.TEXT),
                        field("candidateStatus", "候选状态", EntityField.Type.TEXT),
                        field("evidenceReference", "证据引用", EntityField.Type.TEXT),
                        field("matchedOwnerType", "匹配归属类型", EntityField.Type.TEXT),
                        field("matchedOwnerId", "匹配归属编号", EntityField.Type.NUMBER),
                        field("decisionReason", "决策原因", EntityField.Type.TEXT),
                        field("submittedAt", "提交时间", EntityField.Type.DATETIME),
                        field("decidedAt", "决策时间", EntityField.Type.DATETIME)),
                List.of(), List.of(), List.of(), "com_authority_candidate");
        declarations.add(new BusinessModelDeclaration(authorityCandidate, AuthorityCandidateDO.class,
                authorityCandidateMapper, null));
        BusinessModelDescriptor executionOrder = new BusinessModelDescriptor("COM", "crmExecutionOrder",
                "COM_CRM_EXECUTION_ORDER", 1, BusinessModelKind.AGGREGATE_ROOT, "CRM 执行单",
                "pms:commerce:contract:query",
                List.of(required("executionNo", "执行单编号", EntityField.Type.TEXT),
                        field("sourceSystem", "来源系统", EntityField.Type.TEXT),
                        field("projectCode", "项目编码", EntityField.Type.TEXT),
                        field("projectName", "项目名称", EntityField.Type.TEXT),
                        field("salesRepCode", "销售代表编码", EntityField.Type.TEXT),
                        field("marketCode", "市场部编码", EntityField.Type.TEXT),
                        field("systemCode", "系统部编码", EntityField.Type.TEXT),
                        field("expendCode", "拓展部编码", EntityField.Type.TEXT),
                        field("industryCode", "行业编码", EntityField.Type.TEXT),
                        field("serviceTypeName", "服务类型", EntityField.Type.TEXT),
                        field("engineeringFee", "工程费", EntityField.Type.NUMBER),
                        field("companyCode", "公司编码", EntityField.Type.TEXT),
                        field("projectType", "项目类型", EntityField.Type.TEXT),
                        field("projectAmount", "项目金额", EntityField.Type.NUMBER),
                        field("requiredInDate", "要求进场日期", EntityField.Type.DATE),
                        field("submitTime", "提交时间", EntityField.Type.DATETIME),
                        field("status", "状态", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "com_crm_execution_order");
        declarations.add(new BusinessModelDeclaration(executionOrder, CrmExecutionOrderDO.class,
                crmExecutionOrderMapper, null));
        return declarations;
    }
}
