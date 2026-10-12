package cn.iocoder.yudao.module.pms.commerce.api.migration;

import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import java.util.List;

/** Resolve only exact business/source identities already held by COM; never creates authority or allocations. */
public interface DppmsCommerceEntityResolveApi {
    List<NativeEntityMigrationRow> resolve(Command command);
    /** Locks and verifies existing lines; never substitutes a guessed ERP line number. */
    List<ShipmentLine> lockShipmentLines(Command command, List<ShipmentLine> lines);
    /** Missing or ambiguous contract identities remain unassociated; never guess a company. */
    List<LicenseContract> resolveLicenseContracts(LicenseContracts query);
    record LicenseContracts(Long tenantId, Long actorId, Long projectId, List<String> contractNos) {}
    record LicenseContract(String contractNo, Long contractId) {}
    record ShipmentLine(String sourceKey, Long id, String sourceSystem, String companyCode,
                        String orderNo, String lineNo, String inventoryTransactionId) {}
    record Command(Long tenantId, Long actorId, Long projectId, String contractNo,
                   List<Order> orders, List<Line> lines, List<Execution> executions, List<Package> packages,
                   String contractSourceKey) {}
    /** companyCode is the original DPPMS code (01..04); the Owner converts it before exact identity lookup. */
    record Order(String sourceKey, String source, String companyCode, String type, String number) {}
    /** companyCode and type retain the original DPPMS company and lineType for exact parent identity. */
    record Line(String sourceKey, String source, String companyCode, String type, String number, String lineNo) {}
    record Execution(String sourceKey, String source, String number) {}
    record Package(String sourceKey, String packageNo) {}
}
