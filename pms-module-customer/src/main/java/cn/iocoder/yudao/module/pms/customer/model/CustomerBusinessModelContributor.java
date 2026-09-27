package cn.iocoder.yudao.module.pms.customer.model;

import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.CustomerContactMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.servicelevel.CustomerServiceLevelDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.CustomerContactMasterMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.servicelevel.CustomerServiceLevelMapper;
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
 * B4 批次统一目录声明（P12）：客户主数据、联系人、服务等级进入统一目录。
 * CRM 同步 UPSERT、字段所有权互斥、服务等级状态机等专业写路径保留本域，
 * 声明不开放通用 create/save 操作。
 */
@Component
public class CustomerBusinessModelContributor implements BusinessModelContributor {

    private final CustomerMasterMapper customerMasterMapper;
    private final CustomerContactMasterMapper customerContactMasterMapper;
    private final CustomerServiceLevelMapper customerServiceLevelMapper;

    public CustomerBusinessModelContributor(CustomerMasterMapper customerMasterMapper,
                                            CustomerContactMasterMapper customerContactMasterMapper,
                                            CustomerServiceLevelMapper customerServiceLevelMapper) {
        this.customerMasterMapper = customerMasterMapper;
        this.customerContactMasterMapper = customerContactMasterMapper;
        this.customerServiceLevelMapper = customerServiceLevelMapper;
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
        BusinessModelDescriptor customerMaster = new BusinessModelDescriptor("CUS", "customerMaster",
                "CUS_CUSTOMER_MASTER", 1, BusinessModelKind.AGGREGATE_ROOT, "客户主数据",
                "pms:customer:query",
                List.of(required("code", "客户编码", EntityField.Type.TEXT),
                        required("name", "客户名称", EntityField.Type.TEXT),
                        field("shortName", "客户简称", EntityField.Type.TEXT),
                        field("customerLevel", "客户等级", EntityField.Type.TEXT),
                        field("lifecycleStatus", "生命周期状态", EntityField.Type.TEXT),
                        field("sourceType", "来源类型", EntityField.Type.TEXT),
                        field("sourceKey", "来源键", EntityField.Type.TEXT),
                        field("sourceVersion", "来源版本", EntityField.Type.TEXT),
                        field("syncStatus", "同步状态", EntityField.Type.TEXT),
                        field("dataAsOf", "数据截止时间", EntityField.Type.DATETIME),
                        field("reconciliationPending", "对账待处理", EntityField.Type.BOOLEAN),
                        field("contactPhone", "联系电话", EntityField.Type.TEXT),
                        field("contactEmail", "联系邮箱", EntityField.Type.TEXT),
                        field("departmentCode", "部门编码", EntityField.Type.TEXT),
                        field("marketCode", "市场编码", EntityField.Type.TEXT),
                        field("industryCode", "行业编码", EntityField.Type.TEXT),
                        field("address", "地址", EntityField.Type.TEXT),
                        field("remark", "备注", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "cus_customer_master");
        declarations.add(new BusinessModelDeclaration(customerMaster, CustomerMasterDO.class, customerMasterMapper, null));
        BusinessModelDescriptor customerContact = new BusinessModelDescriptor("CUS", "customerContact",
                "CUS_CUSTOMER_CONTACT", 1, BusinessModelKind.AGGREGATE_ROOT, "客户联系人",
                "pms:customer-contact:query",
                List.of(required("name", "联系人姓名", EntityField.Type.TEXT),
                        field("customerId", "客户编号", EntityField.Type.NUMBER),
                        field("department", "部门", EntityField.Type.TEXT),
                        field("title", "职务", EntityField.Type.TEXT),
                        field("mobile", "手机", EntityField.Type.TEXT),
                        field("phone", "电话", EntityField.Type.TEXT),
                        field("email", "邮箱", EntityField.Type.TEXT),
                        field("primaryFlag", "主要联系人", EntityField.Type.BOOLEAN),
                        field("status", "状态", EntityField.Type.NUMBER),
                        field("remark", "备注", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "cus_customer_contact");
        declarations.add(new BusinessModelDeclaration(customerContact, CustomerContactMasterDO.class,
                customerContactMasterMapper, null));
        BusinessModelDescriptor serviceLevel = new BusinessModelDescriptor("CUS", "customerServiceLevel",
                "CUS_CUSTOMER_SERVICE_LEVEL", 1, BusinessModelKind.AGGREGATE_ROOT, "客户服务等级",
                "pms:service-level:query",
                List.of(field("customerId", "客户编号", EntityField.Type.NUMBER),
                        required("level", "服务等级", EntityField.Type.TEXT),
                        field("validFrom", "生效日期", EntityField.Type.DATE),
                        field("validTo", "失效日期", EntityField.Type.DATE),
                        field("status", "状态", EntityField.Type.NUMBER),
                        field("responseTimeHours", "响应时长（小时）", EntityField.Type.NUMBER),
                        field("proactiveService", "主动服务", EntityField.Type.BOOLEAN),
                        field("remark", "备注", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "cus_customer_service_level");
        declarations.add(new BusinessModelDeclaration(serviceLevel, CustomerServiceLevelDO.class,
                customerServiceLevelMapper, null));
        return declarations;
    }
}
