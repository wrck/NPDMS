package cn.iocoder.yudao.module.pms.acceptance.service.archivedocument;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.archivedocument.ArchiveDocumentDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.ArchiveDocumentMapper;
import cn.iocoder.yudao.module.pms.acceptance.service.AcceptanceRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationRequest;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import org.springframework.stereotype.Service;
import java.util.Map;
@Service @BusinessEntityService(ownerModule="ACC", entityType="archiveDocument")
public class ArchiveDocumentBusinessService extends ExtensibleBusinessApplicationService {
    private final ArchiveDocumentMapper mapper;
    private final AcceptanceRecordCodeGenerator codes;
    private final ArchiveDocumentService lifecycle;
    public ArchiveDocumentBusinessService(DefaultBusinessApplicationService defaults, ArchiveDocumentMapper mapper,
            AcceptanceRecordCodeGenerator codes, ArchiveDocumentService lifecycle) {
        super(defaults); this.mapper=mapper; this.codes=codes; this.lifecycle=lifecycle;
    }
    @Override protected void initializeEntity(BusinessOperationRequest request, BaseBusinessEntity entity) {
        var document=(ArchiveDocumentDO) entity;
        document.setCode(codes.next(document.getProjectId(), AcceptanceRecordCodeGenerator.ARCHIVE_DOCUMENT, mapper));
        document.setStatus(0);
    }
    @Override protected void validateBusinessOperation(BusinessOperationRequest request, Map<String,Object> current) {
        if (request.targetRef()!=null && java.util.Set.of("save","delete").contains(request.operationCode())
                && !Integer.valueOf(0).equals(current.get("status")))
            throw new BusinessContractException("ARCHIVE_DOCUMENT_STATE_INVALID", "仅草稿允许修改或删除");
    }
    @Override protected void validateProposedValues(BusinessOperationRequest request, Map<String,Object> values) {
        lifecycle.validateUnifiedPointer(request.targetRef()==null?null:request.targetRef().entity().entityId(), (String) values.get("documentUrl"));
    }
    @Override protected Map<String,Object> customOperationChanges(BusinessOperationRequest request, Map<String,Object> current) { return Map.of(); }
    @Override protected boolean performCustomOperation(BusinessOperationRequest request, Map<String,Object> current) {
        Long id=request.targetRef().entity().entityId();
        switch(request.operationCode()) {
            case "submit" -> lifecycle.submitArchiveDocument(id);
            case "archive" -> lifecycle.archiveArchiveDocument(id);
            default -> throw new BusinessContractException("OPERATION_NOT_DECLARED", "归档文档未开放该命令");
        }
        return true;
    }
}
