package cn.iocoder.yudao.module.pms.acceptance.service.archivedocument;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.ArchiveDocumentMapper;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryBusinessObjectEvidenceProvider;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;
@Component @RequiredArgsConstructor
public class ArchiveDocumentDeliveryEvidenceProvider implements DeliveryBusinessObjectEvidenceProvider {
    private final ArchiveDocumentMapper mapper;
    public boolean supports(String type) { return "archiveDocument".equals(type); }
    public void validateCurrent(Long tenantId,Long projectId,String objectId,Long revision) {
        var row=mapper.selectById(Long.valueOf(objectId));
        if(row==null || !Objects.equals(tenantId,row.getTenantId()) || !Objects.equals(projectId,row.getProjectId())
                || revision!=null || !Integer.valueOf(2).equals(row.getStatus()))
            throw new BusinessContractException("DELIVERY_BUSINESS_OBJECT_INVALID","Native business result is not confirmed/archived or its identity differs");
    }
    public Identity identity(Long tenantId,Long projectId,String objectId,Long revision) {
        validateCurrent(tenantId,projectId,objectId,revision);
        return new Identity("ACC","archiveDocument",Long.valueOf(objectId),"ACC_ARCHIVE_DOCUMENT","archiveDocument",objectId,null);
    }
}
