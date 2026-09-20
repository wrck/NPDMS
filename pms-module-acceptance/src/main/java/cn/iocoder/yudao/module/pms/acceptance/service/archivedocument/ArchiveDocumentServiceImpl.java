package cn.iocoder.yudao.module.pms.acceptance.service.archivedocument;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.archivedocument.vo.ArchiveDocumentPageReqVO;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.archivedocument.vo.ArchiveDocumentSaveReqVO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.archivedocument.ArchiveDocumentDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.ArchiveDocumentMapper;
import cn.iocoder.yudao.module.pms.acceptance.service.AcceptanceRecordCodeGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
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

    @Override
    public Long createArchiveDocument(ArchiveDocumentSaveReqVO createReqVO) {
        // 插入；编码由系统按项目编码自动生成
        ArchiveDocumentDO entity = BeanUtils.toBean(createReqVO, ArchiveDocumentDO.class);
        entity.setCode(recordCodeGenerator.next(createReqVO.getProjectId(),
                AcceptanceRecordCodeGenerator.ARCHIVE_DOCUMENT, archiveDocumentMapper,
                ArchiveDocumentDO::getProjectId, ArchiveDocumentDO::getCode));
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
        ArchiveDocumentDO updateObj = BeanUtils.toBean(updateReqVO, ArchiveDocumentDO.class);
        // 保持状态不被前端覆盖
        updateObj.setStatus(existing.getStatus());
        archiveDocumentMapper.updateById(updateObj);
    }

    @Override
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
    public void submitArchiveDocument(Long id) {
        ArchiveDocumentDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_ARCHIVE_DOCUMENT_STATUS_INVALID);
        }
        updateStatus(id, STATUS_PENDING_ARCHIVE);
    }

    @Override
    public void archiveArchiveDocument(Long id) {
        ArchiveDocumentDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_PENDING_ARCHIVE)) {
            throw exception(ACC_ARCHIVE_DOCUMENT_STATUS_INVALID);
        }
        // 归档后版本不可覆盖：置为已归档后，后续 update/delete 将被拒绝
        ArchiveDocumentDO updateObj = new ArchiveDocumentDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_ARCHIVED);
        updateObj.setArchiveTime(LocalDateTime.now());
        archiveDocumentMapper.updateById(updateObj);
    }

    private void updateStatus(Long id, int status) {
        ArchiveDocumentDO updateObj = new ArchiveDocumentDO();
        updateObj.setId(id);
        updateObj.setStatus(status);
        archiveDocumentMapper.updateById(updateObj);
    }

    private ArchiveDocumentDO validateExists(Long id) {
        if (id == null) {
            throw exception(ACC_ARCHIVE_DOCUMENT_NOT_EXISTS);
        }
        ArchiveDocumentDO entity = archiveDocumentMapper.selectById(id);
        if (entity == null) {
            throw exception(ACC_ARCHIVE_DOCUMENT_NOT_EXISTS);
        }
        return entity;
    }

}
