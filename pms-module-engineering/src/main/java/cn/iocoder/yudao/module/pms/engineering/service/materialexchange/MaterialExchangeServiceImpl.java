package cn.iocoder.yudao.module.pms.engineering.service.materialexchange;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.MaterialExchangeApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.MaterialExchangePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.MaterialExchangeSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.MaterialExchangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange.MaterialExchangeMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.List;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.MaterialExchangeSerialVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.MaterialExchangeSerialDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange.MaterialExchangeSerialMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange.query.MaterialExchangeSerialQuery;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 物料换货协同 Service 实现（FR-ENG-003）。
 * <p>
 * 单据状态流转：0 草稿 → 1 已提交 → 2 审批中 → 3 已通过 / 4 已驳回 / 5 已撤回 / 6 已终止。
 * CRM 推送状态：PENDING → SENT → RECEIVED，仅 PENDING 可推送。
 * 换货单号全局唯一；草稿/已驳回状态可编辑或删除。
 */
@Service
@Validated
@Slf4j
public class MaterialExchangeServiceImpl implements MaterialExchangeService {

    /**
     * 状态：0 草稿
     */
    public static final int STATUS_DRAFT = 0;
    /**
     * 状态：1 已提交
     */
    public static final int STATUS_SUBMITTED = 1;
    /**
     * 状态：2 审批中
     */
    public static final int STATUS_APPROVING = 2;
    /**
     * 状态：3 已通过
     */
    public static final int STATUS_PASSED = 3;
    /**
     * 状态：4 已驳回
     */
    public static final int STATUS_REJECTED = 4;
    /**
     * 状态：5 已撤回
     */
    public static final int STATUS_WITHDRAWN = 5;
    /**
     * 状态：6 已终止
     */
    public static final int STATUS_TERMINATED = 6;

    /**
     * 审批动作：通过
     */
    public static final String ACTION_PASS = "PASS";
    /**
     * 审批动作：驳回
     */
    public static final String ACTION_REJECT = "REJECT";
    /**
     * 审批动作：退回（退回到草稿）
     */
    public static final String ACTION_RETURN = "RETURN";
    /**
     * 审批动作：转签
     */
    public static final String ACTION_TRANSFER = "TRANSFER";
    /**
     * 审批动作：会签
     */
    public static final String ACTION_COUNTERSIGN = "COUNTERSIGN";

    /**
     * CRM 推送状态：待推送
     */
    public static final String CRM_PUSH_PENDING = "PENDING";
    /**
     * CRM 推送状态：已推送
     */
    public static final String CRM_PUSH_SENT = "SENT";
    /**
     * CRM 推送状态：已接收
     */
    public static final String CRM_PUSH_RECEIVED = "RECEIVED";

    @Resource
    private MaterialExchangeMapper materialExchangeMapper;

    @Resource
    private MaterialExchangeSerialMapper serialMapper;
    @Resource
    private ProjectDeviceSelectionApi deviceSelectionApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMaterialExchange(MaterialExchangeSaveReqVO createReqVO) {
        List<SelectedProjectDevice> serials = validateSelection(createReqVO, null);
        // 1. 校验单号全局唯一
        validateCodeUnique(createReqVO.getCode(), null);
        // 3. 转换并写入，初始单据状态为草稿、CRM 推送状态为待推送
        MaterialExchangeDO entity = BeanUtils.toBean(createReqVO, MaterialExchangeDO.class);
        entity.setStatus(STATUS_DRAFT);
        entity.setCrmPushStatus(CRM_PUSH_PENDING);
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        materialExchangeMapper.insert(entity);
        saveSerials(entity.getId(), serials);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMaterialExchange(MaterialExchangeSaveReqVO updateReqVO) {
        // 1. 校验存在
        MaterialExchangeDO existing = lockMaterialExchange(updateReqVO.getId());
        // 2. 状态校验：仅 0 草稿 / 4 已驳回 可编辑
        validateStatus(existing, STATUS_DRAFT, STATUS_REJECTED);
        // 3. 乐观锁版本校验
        validateVersion(existing, updateReqVO.getVersion());
        // 4. 单号不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(MATERIAL_EXCH_CODE_DUPLICATE, updateReqVO.getCode());
        }
        if (!Objects.equals(existing.getProjectId(), updateReqVO.getProjectId())) {
            throw exception(MATERIAL_EXCH_PROJECT_NOT_EXISTS);
        }
        List<SelectedProjectDevice> serials = validateSelection(updateReqVO, existing);
        // 5. 更新（乐观锁由 MyBatis-Plus @Version 自动处理）
        MaterialExchangeDO update = BeanUtils.toBean(updateReqVO, MaterialExchangeDO.class);
        update.setVersion(existing.getVersion());
        if (materialExchangeMapper.updateById(update) != 1) {
            throw exception(MATERIAL_EXCH_VERSION_NOT_MATCH);
        }
        // 旧客户端未发送明细时保留已保存快照；显式编辑才替换当前草稿明细。
        if (updateReqVO.getSerials() != null || serialMapper.selectByExchange(
                new MaterialExchangeSerialQuery(existing.getId())).isEmpty()) {
            serialMapper.deleteByExchange(new MaterialExchangeSerialQuery(existing.getId()));
            saveSerials(existing.getId(), serials);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMaterialExchange(Long id) {
        // 1. 校验存在
        MaterialExchangeDO existing = lockMaterialExchange(id);
        // 2. 状态校验：仅 0 草稿 / 4 已驳回 可删除
        validateStatus(existing, STATUS_DRAFT, STATUS_REJECTED);
        // 3. 删除
        materialExchangeMapper.deleteById(id);
        serialMapper.deleteByExchange(new MaterialExchangeSerialQuery(id));
    }

    @Override
    public MaterialExchangeDO getMaterialExchange(Long id) {
        return materialExchangeMapper.selectById(id);
    }

    @Override
    public List<MaterialExchangeSerialVO> getSerials(Long id) {
        validateMaterialExchangeExists(id);
        return BeanUtils.toBean(serialMapper.selectByExchange(new MaterialExchangeSerialQuery(id)),
                MaterialExchangeSerialVO.class);
    }

    private List<SelectedProjectDevice> validateSelection(MaterialExchangeSaveReqVO request,
                                                           MaterialExchangeDO existing) {
        List<Long> ids;
        if (request.getSerials() != null) {
            ids = request.getSerials().stream().map(MaterialExchangeSerialVO::getEquipmentId).toList();
        } else if (existing != null) {
            ids = serialMapper.selectByExchange(new MaterialExchangeSerialQuery(existing.getId())).stream()
                    .map(MaterialExchangeSerialDO::getEquipmentId).toList();
            if (ids.isEmpty() && request.getEquipmentId() != null) ids = List.of(request.getEquipmentId());
        } else {
            ids = request.getEquipmentId() == null ? List.of() : List.of(request.getEquipmentId());
        }
        var devices = deviceSelectionApi.validateSelection(request.getProjectId(), ids);
        if (request.getSerials() != null && !ids.isEmpty()
                && (request.getQuantity() == null || request.getQuantity().compareTo(
                        java.math.BigDecimal.valueOf(ids.size())) != 0)) {
            throw exception(MATERIAL_EXCH_SERIAL_QUANTITY_INVALID);
        }
        // 兼容旧单设备入口；新界面以序列号子表为准。
        request.setEquipmentId(devices.isEmpty() ? null : devices.getFirst().equipmentId());
        return devices;
    }

    private void saveSerials(Long exchangeId, List<SelectedProjectDevice> devices) {
        for (SelectedProjectDevice device : devices) {
            MaterialExchangeSerialDO row = new MaterialExchangeSerialDO();
            row.setExchangeId(exchangeId);
            row.setEquipmentId(device.equipmentId());
            row.setSn(device.sn());
            row.setName(device.name());
            row.setProductCode(device.productCode());
            row.setProductModel(device.productModel());
            row.setContractNo(device.contractNo());
            serialMapper.insert(row);
        }
    }

    @Override
    public MaterialExchangeDO validateMaterialExchangeExists(Long id) {
        MaterialExchangeDO entity = materialExchangeMapper.selectById(id);
        if (entity == null) {
            throw exception(MATERIAL_EXCH_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<MaterialExchangeDO> getMaterialExchangePage(MaterialExchangePageReqVO pageReqVO) {
        return materialExchangeMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitMaterialExchange(Long id) {
        // 1. 校验存在
        MaterialExchangeDO entity = lockMaterialExchange(id);
        // 2. 状态校验：0 草稿 / 4 已驳回 → 1 已提交
        validateStatus(entity, STATUS_DRAFT, STATUS_REJECTED);
        var ids = serialMapper.selectByExchange(new MaterialExchangeSerialQuery(id)).stream()
                .map(MaterialExchangeSerialDO::getEquipmentId).toList();
        if (ids.isEmpty() && entity.getEquipmentId() != null) ids = List.of(entity.getEquipmentId());
        deviceSelectionApi.validateSelection(entity.getProjectId(), ids);
        // 3. 更新状态
        updateStatus(entity, STATUS_SUBMITTED, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveMaterialExchange(MaterialExchangeApproveReqVO reqVO) {
        // 1. 校验存在
        MaterialExchangeDO entity = validateMaterialExchangeExists(reqVO.getId());
        // 2. 状态校验：1 已提交 / 2 审批中 可审批
        validateStatus(entity, STATUS_SUBMITTED, STATUS_APPROVING);
        // 3. 根据审批动作决定目标状态
        int newStatus = resolveApproveStatus(reqVO.getApproveAction());
        // 4. 更新状态、审批人、审批时间、审批意见与审批动作
        updateStatus(entity, newStatus, reqVO.getApproverUserId(), reqVO.getApproveOpinion(), reqVO.getApproveAction());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawMaterialExchange(Long id) {
        // 1. 校验存在
        MaterialExchangeDO entity = validateMaterialExchangeExists(id);
        // 2. 状态校验：1 已提交 / 2 审批中 → 5 已撤回
        validateStatus(entity, STATUS_SUBMITTED, STATUS_APPROVING);
        // 3. 更新状态
        updateStatus(entity, STATUS_WITHDRAWN, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminateMaterialExchange(Long id) {
        // 1. 校验存在
        MaterialExchangeDO entity = validateMaterialExchangeExists(id);
        // 2. 状态校验：非 3 已通过 / 非 6 已终止 可终止
        if (Objects.equals(entity.getStatus(), STATUS_PASSED)
                || Objects.equals(entity.getStatus(), STATUS_TERMINATED)) {
            throw exception(MATERIAL_EXCH_STATUS_INVALID);
        }
        // 3. 更新状态
        updateStatus(entity, STATUS_TERMINATED, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pushToCrm(Long id, String crmOrderNo) {
        // 1. 校验存在
        MaterialExchangeDO entity = validateMaterialExchangeExists(id);
        // 2. CRM 推送状态校验：仅 PENDING 可推送
        if (!Objects.equals(entity.getCrmPushStatus(), CRM_PUSH_PENDING)) {
            throw exception(MATERIAL_EXCH_CRM_ALREADY_PUSHED);
        }
        // External integration is intentionally reserved, not simulated. A supplied
        // order number is not CRM evidence and must not advance the local record.
        throw exception(MATERIAL_EXCH_CRM_NOT_CONNECTED);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 根据审批动作解析目标状态：
     * PASS → 3 已通过，REJECT → 4 已驳回，RETURN → 0 草稿，TRANSFER / COUNTERSIGN → 2 审批中（保持）
     */
    private int resolveApproveStatus(String action) {
        switch (action) {
            case ACTION_PASS:
                return STATUS_PASSED;
            case ACTION_REJECT:
                return STATUS_REJECTED;
            case ACTION_RETURN:
                return STATUS_DRAFT;
            case ACTION_TRANSFER:
            case ACTION_COUNTERSIGN:
                return STATUS_APPROVING;
            default:
                throw exception(MATERIAL_EXCH_STATUS_INVALID);
        }
    }

    /**
     * 更新状态并写入审批信息。
     * <p>
     * version 交由 {@code OptimisticLockerInnerInterceptor} 处理：updateById 自动
     * WHERE version=DB 当前值并 SET version+1。此处不得手动 {@code setVersion(+1)}，
     * 否则 WHERE 版本超前一位，UPDATE 恒为 0 行静默失败。
     */
    private void updateStatus(MaterialExchangeDO entity, int newStatus,
                              Long approverUserId, String approveOpinion, String approveAction) {
        entity.setStatus(newStatus);
        // 审批类操作（PASS / REJECT / RETURN / TRANSFER / COUNTERSIGN）记录审批信息
        if (approverUserId != null) {
            entity.setApproverUserId(approverUserId);
        }
        if (approveOpinion != null) {
            entity.setApproveOpinion(approveOpinion);
        }
        if (approveAction != null) {
            entity.setApproveAction(approveAction);
        }
        // 审批动作产生终态或退回时，记录审批时间
        if (newStatus == STATUS_PASSED || newStatus == STATUS_REJECTED || newStatus == STATUS_DRAFT) {
            entity.setApproveTime(LocalDateTime.now());
        }
        if (materialExchangeMapper.updateById(entity) != 1) {
            throw exception(MATERIAL_EXCH_VERSION_NOT_MATCH);
        }
    }

    private void validateCodeUnique(String code, Long excludeId) {
        if (StringUtils.isBlank(code)) {
            return;
        }
        MaterialExchangeDO existing = materialExchangeMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (excludeId == null || !Objects.equals(existing.getId(), excludeId)) {
            throw exception(MATERIAL_EXCH_CODE_DUPLICATE, code);
        }
    }

    private MaterialExchangeDO lockMaterialExchange(Long id) {
        MaterialExchangeDO entity = materialExchangeMapper.selectByIdForUpdate(id);
        if (entity == null) throw exception(MATERIAL_EXCH_NOT_EXISTS);
        return entity;
    }

    private void validateVersion(MaterialExchangeDO entity, Integer version) {
        if (version == null || !Objects.equals(entity.getVersion(), version)) {
            throw exception(MATERIAL_EXCH_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(MaterialExchangeDO entity, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(entity.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(MATERIAL_EXCH_STATUS_INVALID);
    }
}
