package cn.iocoder.yudao.module.pms.integration.sync.generic;

import cn.iocoder.yudao.module.pms.integration.sync.SyncDefinition;
import java.util.Map;
import java.util.Objects;

/** One approved formatting transition; never grants permission to change the customer identity. */
final class DppmsCustomerCodeTransition {
    record Input(SyncDefinition definition, GenericTargetStep step, Map<String,Object> original,
                 Map<String,Object> stored, Map<String,Object> incoming) {}
    static boolean allowed(Input input) {
        if(!"DPPMS".equals(input.definition().sourceSystem()) || !"sales-orders-full-sync".equals(input.definition().taskKey())
                || !"com_sales_order".equals(input.step().table())
                || input.step().mappings().stream().noneMatch(m->"customer_code".equals(m.target())
                    && "migrationCustomerCode".equals(m.source()) && "STRING".equals(m.conversion())))return false;
        Object old=input.stored().get("customer_code");
        if(!(old instanceof String code) || !Objects.equals(old,input.original().get("customerCode"))
                || !code.matches("[A-Za-z][A-Za-z0-9]*"))return false;
        String content=code.substring(1).replaceFirst("^0+","");
        if(content.length()>5)return false;
        String normalized=code.substring(0,1)+"0".repeat(5-content.length())+content;
        return !normalized.equals(code) && normalized.equals(input.incoming().get("customer_code"));
    }
    private DppmsCustomerCodeTransition() {}
}
