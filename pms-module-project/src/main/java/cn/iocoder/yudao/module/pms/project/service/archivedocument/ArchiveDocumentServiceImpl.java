package cn.iocoder.yudao.module.pms.project.service.archivedocument;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.archivedocument.vo.ArchiveDocumentPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.archivedocument.vo.ArchiveDocumentSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.archivedocument.ArchiveDocumentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.archivedocument.ArchiveDocumentMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_ARCHIVE_DOCUMENT_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_ARCHIVE_DOCUMENT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_ARCHIVE_DOCUMENT_STATUS_INVALID;

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

    @Override
    public Long createArchiveDocument(ArchiveDocumentSaveReqVO createReqVO) {
        // 校验项目内编码唯一
        validateCodeUnique(null, createReqVO.getProjectId(), createReqVO.getCode());
        // 插入
        ArchiveDocumentDO entity = BeanUtils.toBean(createReqVO, ArchiveDocumentDO.class);
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
        // 校验项目内编码唯一
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getProjectId(), updateReqVO.getCode());
        // 归档后版本不可覆盖：已归档状态不允许修改
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

    private void validateCodeUnique(Long id, Long projectId, String code) {
        if (projectId == null || code == null) {
            return;
        }
        ArchiveDocumentDO existing = archiveDocumentMapper.selectByProjectIdAndCode(projectId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(ACC_ARCHIVE_DOCUMENT_CODE_DUPLICATE, code);
        }
    }

}
