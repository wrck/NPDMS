package cn.iocoder.yudao.module.pms.asset.service.migration;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareHistoryExtensionApi;
import cn.iocoder.yudao.module.pms.asset.service.version.SoftwareVersionLegacyExtensionService;
@org.springframework.stereotype.Service @lombok.RequiredArgsConstructor
public class DppmsSoftwareHistoryExtensionService implements DppmsSoftwareHistoryExtensionApi {
    private final SoftwareVersionLegacyExtensionService extensions;
    @Override @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY,rollbackFor=Exception.class)
    public void preview(Initialization command) {
        extensions.preview(new SoftwareVersionLegacyExtensionService.Preview(command.tenantId(),command.actorId(),command.recordId(),command.definitionId(),command.values()));
    }
    @Override @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY,rollbackFor=Exception.class)
    public void initialize(Initialization command) {
        extensions.initialize(command.tenantId(),command.actorId(),command.recordId(),command.definitionId(),command.values());
    }
}
