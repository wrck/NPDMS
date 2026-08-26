package cn.iocoder.yudao.module.pms.customer.service.outbox;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CustomerOutboxPayloadFactory {

    public String customerUpdated(CustomerMasterDO customer, String action) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerId", customer.getId());
        payload.put("action", action);
        payload.put("sourceType", customer.getSourceType());
        payload.put("version", customer.getVersion());
        payload.put("occurredAt", LocalDateTime.now());
        return JsonUtils.toJsonString(payload);
    }
}
