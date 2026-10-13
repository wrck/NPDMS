package cn.iocoder.yudao.module.pms.commerce.api.migration;

import java.util.List;
import java.util.Map;

/** Formal contract collection information; does not register a collection or acceptance action. */
public interface ContractReceivableMigrationApi {
    List<String> ORIGINAL_COLUMNS = List.of("id","contract_num","batch_code","project_name","order_num",
            "client_supplier_code","client_supplier_name","contract_money_amount","delivered_money_amount",
            "collected_money_amount","collected_money_ratio","receivables_money_amount","over_due_money_amount",
            "maketing_department_name","office_name","industry_name","marketing_representative_name",
            "currency_name","create_by","create_time","update_by","update_time","effective_from","effective_to",
            "import_batch_num","contract_create_date","projectCode","marketCode","systemId","industryId",
            "officeCode","expendId","usernamec","latest_ship_date","usernamec2","systemid_o","expendid_o",
            "industry_name_o","dataSource");
    record Entry(String sourceKey, Map<String,String> originalFields, String creator, String updater,
                 Long targetId, String expectedFingerprint) {}
    record Request(Long tenantId, Long actorId, String owner, List<Entry> entries) {}
    record Result(String sourceKey, Long targetId, String action, Map<String,Object> fields, String fingerprint) {}
    List<Result> preview(Request request);
    List<Result> apply(Request request);
}
