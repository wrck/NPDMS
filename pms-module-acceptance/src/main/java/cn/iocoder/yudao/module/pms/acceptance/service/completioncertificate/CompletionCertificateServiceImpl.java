package cn.iocoder.yudao.module.pms.acceptance.service.completioncertificate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.completioncertificate.vo.CompletionCertificateDeviceSaveReqVO;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.completioncertificate.vo.CompletionCertificatePageReqVO;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.completioncertificate.vo.CompletionCertificateSaveReqVO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionCertificateDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionCertificateDeviceDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.CompletionCertificateDeviceMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.CompletionCertificateMapper;
import cn.iocoder.yudao.module.pms.acceptance.service.AcceptanceRecordCodeGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.acceptance.enums.ErrorCodeConstants.ACC_COMPLETION_CERTIFICATE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.acceptance.enums.ErrorCodeConstants.ACC_COMPLETION_CERTIFICATE_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.acceptance.enums.ErrorCodeConstants.ACC_COMPLETION_CERTIFICATE_STATUS_INVALID;

/**
 * 电子完工证明 Service 实现类
 * <p>
 * 状态机：0草稿 → 1待客户确认 → 2客户已确认 → 3已归档 / 4已驳回
 * 【待确认：法律效力口径】电子完工证明的法律效力以公司法务口径为准，本实现仅承载流程数据。
 */
@Service
@Validated
public class CompletionCertificateServiceImpl implements CompletionCertificateService {

    /**
     * 状态：0草稿
     */
    private static final int STATUS_DRAFT = 0;
    /**
     * 状态：1待客户确认
     */
    private static final int STATUS_PENDING_CUSTOMER_CONFIRM = 1;
    /**
     * 状态：2客户已确认
     */
    private static final int STATUS_CUSTOMER_CONFIRMED = 2;
    /**
     * 状态：3已归档
     */
    private static final int STATUS_ARCHIVED = 3;
    /**
     * 状态：4已驳回
     */
    private static final int STATUS_REJECTED = 4;

    @Resource
    private CompletionCertificateMapper completionCertificateMapper;
    @Resource
    private CompletionCertificateDeviceMapper completionCertificateDeviceMapper;
    @Resource
    private AcceptanceRecordCodeGenerator recordCodeGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCompletionCertificate(CompletionCertificateSaveReqVO createReqVO) {
        // 插入；编码由系统按项目编码自动生成
        CompletionCertificateDO entity = BeanUtils.toBean(createReqVO, CompletionCertificateDO.class);
        entity.setCode(recordCodeGenerator.next(createReqVO.getProjectId(),
                AcceptanceRecordCodeGenerator.COMPLETION_CERTIFICATE, completionCertificateMapper));
        if (entity.getStatus() == null) {
            entity.setStatus(STATUS_DRAFT);
        }
        completionCertificateMapper.insert(entity);
        insertCertificateDevices(entity.getId(), createReqVO.getDevices());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCompletionCertificate(CompletionCertificateSaveReqVO updateReqVO) {
        CompletionCertificateDO existing = validateExists(updateReqVO.getId());
        // 仅草稿态允许修改核心字段（编码由系统生成不可改）
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_COMPLETION_CERTIFICATE_STATUS_INVALID);
        }
        CompletionCertificateDO updateObj = BeanUtils.toBean(updateReqVO, CompletionCertificateDO.class);
        // 保持状态不被前端覆盖
        updateObj.setStatus(existing.getStatus());
        completionCertificateMapper.updateById(updateObj);
        // 设备明细整存整取：按新列表重建
        completionCertificateDeviceMapper.deleteByCertificateId(existing.getId());
        insertCertificateDevices(existing.getId(), updateReqVO.getDevices());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCompletionCertificate(Long id) {
        CompletionCertificateDO existing = validateExists(id);
        // 仅草稿或已驳回状态允许删除
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)
                && !Objects.equals(existing.getStatus(), STATUS_REJECTED)) {
            throw exception(ACC_COMPLETION_CERTIFICATE_STATUS_INVALID);
        }
        completionCertificateDeviceMapper.deleteByCertificateId(id);
        completionCertificateMapper.deleteById(id);
    }

    @Override
    public PageResult<CompletionCertificateDO> getCompletionCertificatePage(CompletionCertificatePageReqVO pageReqVO) {
        return completionCertificateMapper.selectPage(pageReqVO);
    }

    @Override
    public CompletionCertificateDO getCompletionCertificate(Long id) {
        return completionCertificateMapper.selectById(id);
    }

    @Override
    public List<CompletionCertificateDeviceDO> getCompletionCertificateDevices(Long certificateId) {
        return completionCertificateDeviceMapper.selectListByCertificateId(certificateId);
    }

    private void insertCertificateDevices(Long certificateId, List<CompletionCertificateDeviceSaveReqVO> devices) {
        if (devices == null || devices.isEmpty()) {
            return;
        }
        List<CompletionCertificateDeviceDO> deviceEntities = new ArrayList<>(devices.size());
        for (int i = 0; i < devices.size(); i++) {
            CompletionCertificateDeviceSaveReqVO deviceReqVO = devices.get(i);
            CompletionCertificateDeviceDO device = BeanUtils.toBean(deviceReqVO, CompletionCertificateDeviceDO.class);
            device.setCertificateId(certificateId);
            device.setSort(i);
            deviceEntities.add(device);
        }
        completionCertificateDeviceMapper.insertBatch(deviceEntities);
    }

    @Override
    public void submitCompletionCertificate(Long id) {
        CompletionCertificateDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_COMPLETION_CERTIFICATE_STATUS_INVALID);
        }
        updateStatus(id, STATUS_PENDING_CUSTOMER_CONFIRM);
    }

    @Override
    public void customerConfirm(Long id) {
        CompletionCertificateDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_PENDING_CUSTOMER_CONFIRM)) {
            throw exception(ACC_COMPLETION_CERTIFICATE_STATUS_INVALID);
        }
        CompletionCertificateDO updateObj = new CompletionCertificateDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_CUSTOMER_CONFIRMED);
        updateObj.setCustomerConfirmTime(LocalDateTime.now());
        completionCertificateMapper.updateById(updateObj);
    }

    @Override
    public void rejectCompletionCertificate(Long id) {
        CompletionCertificateDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_PENDING_CUSTOMER_CONFIRM)) {
            throw exception(ACC_COMPLETION_CERTIFICATE_STATUS_INVALID);
        }
        updateStatus(id, STATUS_REJECTED);
    }

    @Override
    public void archiveCompletionCertificate(Long id) {
        CompletionCertificateDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_CUSTOMER_CONFIRMED)) {
            throw exception(ACC_COMPLETION_CERTIFICATE_STATUS_INVALID);
        }
        CompletionCertificateDO updateObj = new CompletionCertificateDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_ARCHIVED);
        updateObj.setArchiveTime(LocalDateTime.now());
        completionCertificateMapper.updateById(updateObj);
    }

    private void updateStatus(Long id, int status) {
        CompletionCertificateDO updateObj = new CompletionCertificateDO();
        updateObj.setId(id);
        updateObj.setStatus(status);
        completionCertificateMapper.updateById(updateObj);
    }

    private CompletionCertificateDO validateExists(Long id) {
        if (id == null) {
            throw exception(ACC_COMPLETION_CERTIFICATE_NOT_EXISTS);
        }
        CompletionCertificateDO entity = completionCertificateMapper.selectById(id);
        if (entity == null) {
            throw exception(ACC_COMPLETION_CERTIFICATE_NOT_EXISTS);
        }
        return entity;
    }


}
