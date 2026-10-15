package cn.iocoder.yudao.module.pms.commerce.api.migration;

import java.util.List;

/** Commerce-owned snapshot of already carried ERP orders; does not run an upstream sync. */
public interface DppmsLocalContractSourceApi {
    List<Contract> read(Query query);
    record Query(Long tenantId, Long actorId, List<String> contractNumbers, long maxBytes) {}
    record Contract(String companyCode, String contractNo, String customerCode, String customerName,
                    String migrationIssue) {}
}
