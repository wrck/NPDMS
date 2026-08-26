package cn.iocoder.yudao.module.pms.customer.service.history;

import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerLifecycleStatus;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerFieldHistoryDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerFieldHistoryMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.query.CustomerHistoryListQuery;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.UpdateCustomerCommand;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class CustomerHistoryService {

    @Resource
    private CustomerFieldHistoryMapper historyMapper;

    public List<CustomerFieldHistoryDO> list(Long tenantId, Long customerId) {
        if (tenantId == null || customerId == null) {
            throw new IllegalArgumentException("客户历史查询不完整");
        }
        return historyMapper.selectListByCustomer(
                new CustomerHistoryListQuery(tenantId, customerId));
    }

    public void appendCreate(CustomerMasterDO customer, Long operatorId, String operationId) {
        append(customer, "code", null, customer.getCode(), "CUS", operatorId, operationId);
        append(customer, "name", null, customer.getName(), owner(customer), operatorId, operationId);
        if (customer.getShortName() != null) {
            append(customer, "shortName", null, customer.getShortName(), owner(customer), operatorId, operationId);
        }
        if (customer.getRemark() != null) {
            append(customer, "remark", null, customer.getRemark(), "CUS", operatorId, operationId);
        }
    }

    public void appendUpdate(CustomerMasterDO before, UpdateCustomerCommand command,
                             Long operatorId, String operationId) {
        if (command.changedFields().contains("name")) {
            append(before, "name", before.getName(), command.name(), "CRM", operatorId, operationId);
        }
        if (command.changedFields().contains("shortName")) {
            append(before, "shortName", before.getShortName(), command.shortName(), "CRM", operatorId, operationId);
        }
        if (command.changedFields().contains("remark")) {
            append(before, "remark", before.getRemark(), command.remark(), "CUS", operatorId, operationId);
        }
        if (command.changedFields().contains("classification")) {
            append(before, "departmentCode", before.getDepartmentCode(), command.departmentCode(),
                    owner(before), operatorId, operationId);
            append(before, "marketCode", before.getMarketCode(), command.marketCode(),
                    owner(before), operatorId, operationId);
            append(before, "systemCode", before.getSystemCode(), command.systemCode(),
                    owner(before), operatorId, operationId);
            append(before, "expendCode", before.getExpendCode(), command.expendCode(),
                    owner(before), operatorId, operationId);
            append(before, "industryCode", before.getIndustryCode(), command.industryCode(),
                    owner(before), operatorId, operationId);
        }
    }

    public void appendLifecycle(CustomerMasterDO before, CustomerLifecycleStatus targetStatus,
                                String reason, Long operatorId, String operationId) {
        append(before, "lifecycleStatus", before.getLifecycleStatus(), targetStatus.name(),
                "CUS", operatorId, operationId);
        append(before, "lifecycleReason", null, reason, "CUS", operatorId, operationId);
    }

    private void append(CustomerMasterDO customer, String fieldName, String beforeValue,
                        String afterValue, String fieldOwner, Long operatorId, String operationId) {
        CustomerFieldHistoryDO history = new CustomerFieldHistoryDO();
        history.setTenantId(customer.getTenantId());
        history.setCustomerId(customer.getId());
        history.setFieldName(fieldName);
        history.setFieldOwner(fieldOwner);
        history.setBeforeValueDigest(digest(beforeValue));
        history.setAfterValueDigest(digest(afterValue));
        history.setSourceType(customer.getSourceType());
        history.setOperationId(operationId);
        history.setOperatorId(operatorId);
        history.setOccurredAt(LocalDateTime.now());
        historyMapper.insert(history);
    }

    private String owner(CustomerMasterDO customer) {
        return "CRM_SYNC".equals(customer.getSourceType()) ? "CRM" : "CUS";
    }

    private String digest(String value) {
        if (value == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }
}
