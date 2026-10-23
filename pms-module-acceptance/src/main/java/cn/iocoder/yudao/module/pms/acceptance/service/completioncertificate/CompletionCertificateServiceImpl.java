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
import cn.iocoder.yudao.module.pms.acceptance.service.acceptance.NativeAcceptanceProjectAccess;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionCertificatePageQuery;
import jakarta.annotation.Resource;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionCertificateDeliveryLockQuery;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import static cn.iocoder.yudao.module.pms.acceptance.enums.ErrorCodeConstants.ACC_COMPLETION_CERTIFICATE_VERSION_CONFLICT;
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
    @Resource
    private PlatformDeliveryMaterialApi deliveryMaterials;
    @Resource
    private cn.iocoder.yudao.module.pms.acceptance.service.acceptance.NativeAcceptanceDeliveryAccess nativeAccess;
    @Resource
    private NativeAcceptanceProjectAccess projectAccess;
    @Resource private cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.CompletionSatisfactionBundleMapper jointBundles;
    @Resource private cn.iocoder.yudao.module.pms.engineering.api.arrival.SignedArrivalDateApi arrivalDates;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCompletionCertificate(CompletionCertificateSaveReqVO createReqVO) {
        requireCustomerSignatureFromLink(createReqVO);
        projectAccess.requireWrite(createReqVO.getProjectId());
        // 插入；编码由系统按项目编码自动生成
        if(createReqVO.getStatus()!=null && createReqVO.getStatus()!=STATUS_DRAFT)throw exception(ACC_COMPLETION_CERTIFICATE_STATUS_INVALID);
        CompletionCertificateDO entity = BeanUtils.toBean(createReqVO, CompletionCertificateDO.class);
        entity.setManualArrivalDate(manualArrivalDate(createReqVO));
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
        requireCustomerSignatureFromLink(updateReqVO);
        CompletionCertificateDO updateObj = BeanUtils.toBean(updateReqVO, CompletionCertificateDO.class);
        projectAccess.requireWrite(updateReqVO.getProjectId());
        // 保持状态不被前端覆盖
        updateObj.setStatus(existing.getStatus());
        if (updateObj.getVersion() == null || completionCertificateMapper.updateById(updateObj) != 1) {
            throw exception(ACC_COMPLETION_CERTIFICATE_VERSION_CONFLICT);
        }
        if (completionCertificateMapper.updateManualArrivalDate(new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionManualArrivalUpdate(
                TenantContextHolder.getRequiredTenantId(), existing.getId(), updateObj.getVersion(),
                manualArrivalDate(updateReqVO))) != 1) {
            throw exception(ACC_COMPLETION_CERTIFICATE_VERSION_CONFLICT);
        }
        // 设备明细整存整取：按新列表重建
        completionCertificateDeviceMapper.deleteByCertificateId(existing.getId());
        insertCertificateDevices(existing.getId(), updateReqVO.getDevices());
    }

    private java.time.LocalDate manualArrivalDate(CompletionCertificateSaveReqVO request) {
        if (!"是".equals(request.getItemArrival())) return null;
        var automatic = arrivalDates.latest(new cn.iocoder.yudao.module.pms.engineering.api.arrival.SignedArrivalDateApi.Query(
                TenantContextHolder.getRequiredTenantId(), request.getProjectId()));
        return automatic == null ? request.getArrivalDate() : null;
    }

    private void requireCustomerSignatureFromLink(CompletionCertificateSaveReqVO request) {
        if (request.getCustomerSignUrl() != null && !request.getCustomerSignUrl().isBlank()
                || request.getCustomerSignDate() != null)
            throw new IllegalArgumentException("客户签字及签字日期只能通过推送链接提交");
        request.setCustomerSignUrl(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCompletionCertificate(Long id) {
        CompletionCertificateDO existing = validateExists(id);
        // 仅草稿可删除，已外发及后续历史必须保留。
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_COMPLETION_CERTIFICATE_STATUS_INVALID);
        }
        completionCertificateDeviceMapper.deleteByCertificateId(id);
        completionCertificateMapper.deleteById(id);
    }

    @Override
    public PageResult<CompletionCertificateDO> getCompletionCertificatePage(CompletionCertificatePageReqVO pageReqVO) {
        var query = BeanUtils.toBean(pageReqVO, CompletionCertificatePageQuery.class);
        query.setTenantId(TenantContextHolder.getRequiredTenantId());
        query.setVisibleProjectIds(projectAccess.readableProjects());
        return completionCertificateMapper.selectPage(query);
    }

    @Override
    public CompletionCertificateDO getCompletionCertificate(Long id) {
        var row = completionCertificateMapper.selectById(id);
        if (row != null) projectAccess.requireRead(row.getProjectId());
        return row;
    }

    @Override
    public List<CompletionCertificateDeviceDO> getCompletionCertificateDevices(Long certificateId) {
        if (getCompletionCertificate(certificateId) == null) {
            throw exception(ACC_COMPLETION_CERTIFICATE_NOT_EXISTS);
        }
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
    @Transactional(rollbackFor = Exception.class)
    public void submitCompletionCertificate(Long id) {
        CompletionCertificateDO entity = validateExists(id);
        nativeAccess.requireCommand(TenantContextHolder.getRequiredTenantId(),
                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(),
                "completionCertificate",entity.getProjectId(),"submit");
        if (!Objects.equals(entity.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_COMPLETION_CERTIFICATE_STATUS_INVALID);
        }
        updateStatus(entity, STATUS_PENDING_CUSTOMER_CONFIRM);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void customerConfirm(Long id) {
        CompletionCertificateDO entity = validateExists(id);
        if(entity.getSatisfactionTemplateId()!=null || jointBundles.selectByCertificate(new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.CompletionBundleCertificateQuery(
                TenantContextHolder.getRequiredTenantId(),id))!=null)
            throw new IllegalStateException("成套完工证明必须由客户一次签字统一提交");
        nativeAccess.requireCommand(TenantContextHolder.getRequiredTenantId(),
                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(),
                "completionCertificate",entity.getProjectId(),"audit");
        if (!Objects.equals(entity.getStatus(), STATUS_PENDING_CUSTOMER_CONFIRM)) {
            throw exception(ACC_COMPLETION_CERTIFICATE_STATUS_INVALID);
        }
        CompletionCertificateDO updateObj = new CompletionCertificateDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_CUSTOMER_CONFIRMED);
        updateObj.setCustomerConfirmTime(LocalDateTime.now());
        updateObj.setVersion(entity.getVersion());
        if (updateObj.getVersion() == null || completionCertificateMapper.updateById(updateObj) != 1) {
            throw exception(ACC_COMPLETION_CERTIFICATE_VERSION_CONFLICT);
        }
        deliveryMaterials.registerBusinessResultMaterial("ACC", "completionCertificate", id,
                "COMPLETION_CERTIFICATE", "completionCertificate", String.valueOf(id), null,
                entity.getName(), entity.getProjectId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectCompletionCertificate(Long id) {
        CompletionCertificateDO entity = validateExists(id);
        nativeAccess.requireCommand(TenantContextHolder.getRequiredTenantId(),
                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(),
                "completionCertificate",entity.getProjectId(),"audit");
        if (!Objects.equals(entity.getStatus(), STATUS_PENDING_CUSTOMER_CONFIRM)) {
            throw exception(ACC_COMPLETION_CERTIFICATE_STATUS_INVALID);
        }
        updateStatus(entity, STATUS_REJECTED);
        var bundle = jointBundles.selectByCertificate(new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.CompletionBundleCertificateQuery(
                TenantContextHolder.getRequiredTenantId(), id));
        if (bundle != null) jointBundles.revokeActiveGrants(new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.CompletionGrantRevocation(
                TenantContextHolder.getRequiredTenantId(), bundle.getId(),
                String.valueOf(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveCompletionCertificate(Long id) {
        CompletionCertificateDO entity = validateExists(id);
        nativeAccess.requireCommand(TenantContextHolder.getRequiredTenantId(),
                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(),
                "completionCertificate",entity.getProjectId(),"audit");
        if (!Objects.equals(entity.getStatus(), STATUS_CUSTOMER_CONFIRMED)) {
            throw exception(ACC_COMPLETION_CERTIFICATE_STATUS_INVALID);
        }
        CompletionCertificateDO updateObj = new CompletionCertificateDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_ARCHIVED);
        updateObj.setArchiveTime(LocalDateTime.now());
        updateObj.setVersion(entity.getVersion());
        if (updateObj.getVersion() == null || completionCertificateMapper.updateById(updateObj) != 1) {
            throw exception(ACC_COMPLETION_CERTIFICATE_VERSION_CONFLICT);
        }
        deliveryMaterials.registerBusinessResultMaterial("ACC", "completionCertificate", id,
                "COMPLETION_CERTIFICATE", "completionCertificate", String.valueOf(id), null,
                entity.getName(), entity.getProjectId());
    }

    private void updateStatus(CompletionCertificateDO entity, int status) {
        CompletionCertificateDO updateObj = new CompletionCertificateDO();
        updateObj.setId(entity.getId());
        updateObj.setVersion(entity.getVersion());
        updateObj.setStatus(status);
        if (updateObj.getVersion() == null || completionCertificateMapper.updateById(updateObj) != 1) {
            throw exception(ACC_COMPLETION_CERTIFICATE_VERSION_CONFLICT);
        }
    }

    private CompletionCertificateDO validateExists(Long id) {
        if (id == null) {
            throw exception(ACC_COMPLETION_CERTIFICATE_NOT_EXISTS);
        }
        CompletionCertificateDO entity = completionCertificateMapper.selectDeliveryOwnerForUpdate(new CompletionCertificateDeliveryLockQuery(TenantContextHolder.getRequiredTenantId(),id));
        if (entity == null) {
            throw exception(ACC_COMPLETION_CERTIFICATE_NOT_EXISTS);
        }
        projectAccess.requireWrite(entity.getProjectId());
        return entity;
    }


}
