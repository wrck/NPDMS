package cn.iocoder.yudao.module.pms.acceptance.service.archivedocument;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.archivedocument.vo.ArchiveDocumentPageReqVO;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.archivedocument.vo.ArchiveDocumentSaveReqVO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.archivedocument.ArchiveDocumentDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.ArchiveDocumentMapper;
import cn.iocoder.yudao.module.pms.acceptance.service.AcceptanceRecordCodeGenerator;
import jakarta.annotation.Resource;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.query.ArchiveDocumentDeliveryLockQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.iocoder.yudao.module.pms.acceptance.enums.ErrorCodeConstants.ACC_ARCHIVE_DOCUMENT_VERSION_CONFLICT;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.acceptance.enums.ErrorCodeConstants.ACC_ARCHIVE_DOCUMENT_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.acceptance.enums.ErrorCodeConstants.ACC_ARCHIVE_DOCUMENT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.acceptance.enums.ErrorCodeConstants.ACC_ARCHIVE_DOCUMENT_STATUS_INVALID;

/**
 * 交付资料归档 Service 实现类
 * <p>
 * 状态机：0草稿 → 1待归档 → 2已归档
 * 归档后版本不可覆盖：已归档（status=2）的文档不允许更新或删除
 */
@Service
@Validated
public class ArchiveDocumentServiceImpl implements ArchiveDocumentService {

    /**
     * 状态：0草稿
     */
    private static final int STATUS_DRAFT = 0;
    /**
     * 状态：1待归档
     */
    private static final int STATUS_PENDING_ARCHIVE = 1;
    /**
     * 状态：2已归档
     */
    private static final int STATUS_ARCHIVED = 2;

    @Resource
    private ArchiveDocumentMapper archiveDocumentMapper;
    @Resource
    private AcceptanceRecordCodeGenerator recordCodeGenerator;
    @Resource
    private cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi deliveryMaterials;
    @Resource
    private cn.iocoder.yudao.module.pms.acceptance.service.acceptance.NativeAcceptanceDeliveryAccess nativeAccess;

    @Override
    public Long createArchiveDocument(ArchiveDocumentSaveReqVO createReqVO) {
        validateUnifiedPointer(null,createReqVO.getDocumentUrl());
        // 插入；编码由系统按项目编码自动生成
        if(createReqVO.getStatus()!=null && createReqVO.getStatus()!=STATUS_DRAFT)throw exception(ACC_ARCHIVE_DOCUMENT_STATUS_INVALID);
        ArchiveDocumentDO entity = BeanUtils.toBean(createReqVO, ArchiveDocumentDO.class);
        entity.setCode(recordCodeGenerator.next(createReqVO.getProjectId(),
                AcceptanceRecordCodeGenerator.ARCHIVE_DOCUMENT, archiveDocumentMapper));
        if (entity.getStatus() == null) {
            entity.setStatus(STATUS_DRAFT);
        }
        if (entity.getDocumentType() == null) {
            entity.setDocumentType("ACCEPTANCE");
        }
        archiveDocumentMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArchiveDocument(ArchiveDocumentSaveReqVO updateReqVO) {
        ArchiveDocumentDO existing = validateExists(updateReqVO.getId());
        // 归档后版本不可覆盖：已归档状态不允许修改（编码由系统生成不可改）
        if (Objects.equals(existing.getStatus(), STATUS_ARCHIVED)) {
            throw exception(ACC_ARCHIVE_DOCUMENT_STATUS_INVALID);
        }
        // 仅草稿态允许修改核心字段
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_ARCHIVE_DOCUMENT_STATUS_INVALID);
        }
        if(!Objects.equals(existing.getProjectId(),updateReqVO.getProjectId())
                && !deliveryMaterials.listByEntity("ACC","archiveDocument",existing.getId()).isEmpty())
            throw new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException("DELIVERY_FILE_OWNER_INVALID","Registered delivery sources cannot be moved to another project");
        validateUnifiedPointer(existing.getId(),updateReqVO.getDocumentUrl());
        ArchiveDocumentDO updateObj = BeanUtils.toBean(updateReqVO, ArchiveDocumentDO.class);
        // 保持状态不被前端覆盖
        updateObj.setStatus(existing.getStatus());
        if (updateObj.getVersion() == null || archiveDocumentMapper.updateById(updateObj) != 1) {
            throw exception(ACC_ARCHIVE_DOCUMENT_VERSION_CONFLICT);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArchiveDocument(Long id) {
        ArchiveDocumentDO existing = validateExists(id);
        // 归档后版本不可覆盖：已归档状态不允许删除
        if (Objects.equals(existing.getStatus(), STATUS_ARCHIVED)) {
            throw exception(ACC_ARCHIVE_DOCUMENT_STATUS_INVALID);
        }
        // 仅草稿状态允许删除
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_ARCHIVE_DOCUMENT_STATUS_INVALID);
        }
        archiveDocumentMapper.deleteById(id);
    }

    @Override
    public PageResult<ArchiveDocumentDO> getArchiveDocumentPage(ArchiveDocumentPageReqVO pageReqVO) {
        return archiveDocumentMapper.selectPage(pageReqVO);
    }

    @Override
    public ArchiveDocumentDO getArchiveDocument(Long id) {
        return archiveDocumentMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitArchiveDocument(Long id) {
        ArchiveDocumentDO entity = validateExists(id);
        nativeAccess.requireCommand(TenantContextHolder.getRequiredTenantId(),
                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(),
                "archiveDocument",entity.getProjectId(),"submit");
        if (!Objects.equals(entity.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_ARCHIVE_DOCUMENT_STATUS_INVALID);
        }
        updateStatus(entity, STATUS_PENDING_ARCHIVE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveArchiveDocument(Long id) {
        ArchiveDocumentDO entity = validateExists(id);
        nativeAccess.requireCommand(TenantContextHolder.getRequiredTenantId(),
                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(),
                "archiveDocument",entity.getProjectId(),"audit");
        if (!Objects.equals(entity.getStatus(), STATUS_PENDING_ARCHIVE)) {
            throw exception(ACC_ARCHIVE_DOCUMENT_STATUS_INVALID);
        }
        validateUnifiedPointer(id,entity.getDocumentUrl());
        // 归档后版本不可覆盖：置为已归档后，后续 update/delete 将被拒绝
        ArchiveDocumentDO updateObj = new ArchiveDocumentDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_ARCHIVED);
        updateObj.setArchiveTime(LocalDateTime.now());
        updateObj.setVersion(entity.getVersion());
        if (updateObj.getVersion() == null || archiveDocumentMapper.updateById(updateObj) != 1) {
            throw exception(ACC_ARCHIVE_DOCUMENT_VERSION_CONFLICT);
        }
        deliveryMaterials.registerBusinessResultMaterial("ACC","archiveDocument",id,"ARCHIVE_DOCUMENT",
                "archiveDocument",String.valueOf(id),null,entity.getName(),entity.getProjectId());
    }

    private void updateStatus(ArchiveDocumentDO entity, int status) {
        ArchiveDocumentDO updateObj = new ArchiveDocumentDO();
        updateObj.setId(entity.getId());
        updateObj.setVersion(entity.getVersion());
        updateObj.setStatus(status);
        if (updateObj.getVersion() == null || archiveDocumentMapper.updateById(updateObj) != 1) {
            throw exception(ACC_ARCHIVE_DOCUMENT_VERSION_CONFLICT);
        }
    }

    private ArchiveDocumentDO validateExists(Long id) {
        if (id == null) {
            throw exception(ACC_ARCHIVE_DOCUMENT_NOT_EXISTS);
        }
        ArchiveDocumentDO entity = archiveDocumentMapper.selectDeliveryOwnerForUpdate(new ArchiveDocumentDeliveryLockQuery(TenantContextHolder.getRequiredTenantId(),id));
        if (entity == null) {
            throw exception(ACC_ARCHIVE_DOCUMENT_NOT_EXISTS);
        }
        return entity;
    }


    @Override public void validateUnifiedPointer(Long id,String url) {
        if(url==null || !url.startsWith("/api/v1/pms/archive-documents/"))return; // legacy addresses remain readable.
        var match=java.util.regex.Pattern.compile("^/api/v1/pms/archive-documents/(\\d+)/files/(\\d+)$").matcher(url);
        if(!match.matches() || !String.valueOf(id).equals(match.group(1)))throw new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException("DELIVERY_FILE_OWNER_INVALID","Current document belongs to another native root");
        Long materialId=Long.valueOf(match.group(2));
        boolean valid=deliveryMaterials.listByEntityAndType("ACC","archiveDocument",id,"ARCHIVE_DOCUMENT").stream()
                .anyMatch(m->materialId.equals(m.id()) && "FILE".equals(m.materialKind()) && "ACTIVE".equals(m.status()));
        if(!valid)throw new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException("DELIVERY_FILE_UNAVAILABLE","Current document material is unavailable or withdrawn");
    }
}
