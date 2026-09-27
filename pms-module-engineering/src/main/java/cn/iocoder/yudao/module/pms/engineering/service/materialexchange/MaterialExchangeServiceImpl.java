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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice;
import cn.iocoder.yudao.module.pms.asset.api.product.AssetProductOfficialApi;
import cn.iocoder.yudao.module.pms.asset.api.product.ProductOfficialSnapshot;
import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeLineFactApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeLineFact;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeLineRef;
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
    @Resource
    private DeliveryScopeLineFactApi scopeLineFactApi;
    @Resource
    private AssetProductOfficialApi assetProductOfficialApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMaterialExchange(MaterialExchangeSaveReqVO createReqVO) {
        List<ExchangeLine> lines = resolveAndValidateLines(createReqVO, null);
        // 1. 校验单号全局唯一
        validateCodeUnique(createReqVO.getCode(), null);
        // 3. 转换并写入，初始单据状态为草稿、CRM 推送状态为待推送
        MaterialExchangeDO entity = BeanUtils.toBean(createReqVO, MaterialExchangeDO.class);
        // 4. 主表产品编码 = 勾选清单行物料编码去重拼接（换货产品不影响）；名称/型号/原订单号随分流保存退出申报
        entity.setProductCode(joinedProductCode(lines));
        entity.setProductName(null);
        entity.setProductModel(null);
        entity.setOriginalOrderNo(null);
        entity.setStatus(STATUS_DRAFT);
        entity.setCrmPushStatus(CRM_PUSH_PENDING);
        if (entity.getVersion() == null) {
            entity.setVersion(0L);
        }
        materialExchangeMapper.insert(entity);
        saveSerials(entity.getId(), lines, serialQuantities(createReqVO),
                resolveExchangeProductSnapshots(lines));
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
        List<ExchangeLine> lines = resolveAndValidateLines(updateReqVO, existing);
        // 5. 更新（乐观锁由 MyBatis-Plus @Version 自动处理）；产品编码重算拼接，名称/型号/原订单号随分流保存退出申报
        MaterialExchangeDO update = BeanUtils.toBean(updateReqVO, MaterialExchangeDO.class);
        update.setProductCode(joinedProductCode(lines));
        update.setProductName(null);
        update.setProductModel(null);
        update.setOriginalOrderNo(null);
        update.setVersion(existing.getVersion());
        if (materialExchangeMapper.updateById(update) != 1) {
            throw exception(MATERIAL_EXCH_VERSION_NOT_MATCH);
        }
        // 旧客户端未发送明细时保留已保存快照；显式编辑才替换当前草稿明细。
        if (updateReqVO.getSerials() != null || serialMapper.selectByExchange(
                new MaterialExchangeSerialQuery(existing.getId())).isEmpty()) {
            serialMapper.deleteByExchange(new MaterialExchangeSerialQuery(existing.getId()));
            saveSerials(existing.getId(), lines, serialQuantities(updateReqVO),
                    resolveExchangeProductSnapshots(lines));
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

    /**
     * 设备行解析与校验：设备清单行 = 合同对应销售订单行的交付范围分配（范围明细拆分行或未拆分范围行），
     * 通过 commerce 只读事实 API 校验归属；旧序列号行（仅 equipmentId）继续走设备选择校验，不重写旧快照。
     * 主表数量保持与各设备行换货数量之和一致。
     */
    private List<ExchangeLine> resolveAndValidateLines(MaterialExchangeSaveReqVO request,
                                                       MaterialExchangeDO existing) {
        List<ExchangeLine> lines;
        if (request.getSerials() != null) {
            lines = resolveExplicitLines(request.getSerials());
        } else if (existing != null) {
            lines = resolveSavedLines(existing);
        } else {
            lines = request.getDeviceId() == null ? List.of()
                    : List.of(new ExchangeLine(null, request.getDeviceId(), null));
        }
        // 1. 清单行与旧设备行分流校验归属；空集合筛选返回空结果
        var scopeRefs = lines.stream().map(line -> line.scopeRef).filter(Objects::nonNull).toList();
        Map<String, DeliveryScopeLineFact> factByRef = scopeLineFactApi
                .validateSelection(request.getProjectId(), scopeRefs).stream()
                .collect(Collectors.toMap(MaterialExchangeServiceImpl::factRefKey, Function.identity()));
        var legacyIds = lines.stream().map(line -> line.legacyDeviceId).filter(Objects::nonNull).toList();
        Map<Long, SelectedProjectDevice> deviceById = legacyIds.isEmpty() ? Map.of()
                : deviceSelectionApi.validateSelection(request.getProjectId(), legacyIds).stream()
                .collect(Collectors.toMap(SelectedProjectDevice::equipmentId, Function.identity()));
        for (ExchangeLine line : lines) {
            line.scopeFact = line.scopeRef == null ? null : factByRef.get(refKey(line.scopeRef));
            line.device = line.legacyDeviceId == null ? null : deviceById.get(line.legacyDeviceId);
        }
        // 2. 主表数量 = 各设备行换货数量之和；旧客户端未传明细时数量仍按设备台数
        if (request.getSerials() != null && !lines.isEmpty()
                && (request.getQuantity() == null
                || request.getQuantity().compareTo(serialQuantityTotal(request)) != 0)) {
            throw exception(MATERIAL_EXCH_SERIAL_QUANTITY_INVALID);
        }
        // 3. 主表原设备编号：清单行无单设备语义，仅旧行保留设备编号
        request.setDeviceId(lines.stream().map(line -> line.legacyDeviceId)
                .filter(Objects::nonNull).findFirst().orElse(null));
        return lines;
    }

    /** 显式编辑行：清单行引用与旧设备行分流；两者都缺失的行视为无效清单行。 */
    private List<ExchangeLine> resolveExplicitLines(List<MaterialExchangeSerialVO> serials) {
        List<ExchangeLine> lines = new ArrayList<>();
        for (MaterialExchangeSerialVO row : serials) {
            if (row.getScopeDetailId() != null) {
                lines.add(new ExchangeLine(DeliveryScopeLineRef.ofDetail(row.getScopeDetailId()), null,
                        row.getProductId()));
            } else if (row.getScopeId() != null) {
                lines.add(new ExchangeLine(DeliveryScopeLineRef.ofScope(row.getScopeId()), null,
                        row.getProductId()));
            } else if (row.getDeviceId() != null) {
                lines.add(new ExchangeLine(null, row.getDeviceId(), null));
            } else {
                throw exception(MATERIAL_EXCH_SCOPE_LINE_INVALID);
            }
        }
        return lines;
    }

    /** 已保存快照行：旧客户端未发送明细时按已保存行校验；无行时回退主表单设备入口。 */
    private List<ExchangeLine> resolveSavedLines(MaterialExchangeDO existing) {
        List<ExchangeLine> lines = new ArrayList<>();
        for (MaterialExchangeSerialDO row : serialMapper.selectByExchange(
                new MaterialExchangeSerialQuery(existing.getId()))) {
            if (row.getScopeDetailId() != null) {
                lines.add(new ExchangeLine(DeliveryScopeLineRef.ofDetail(row.getScopeDetailId()), null,
                        row.getProductId()));
            } else if (row.getScopeId() != null) {
                lines.add(new ExchangeLine(DeliveryScopeLineRef.ofScope(row.getScopeId()), null,
                        row.getProductId()));
            } else if (row.getDeviceId() != null) {
                lines.add(new ExchangeLine(null, row.getDeviceId(), null));
            }
        }
        if (lines.isEmpty() && existing.getDeviceId() != null) {
            lines.add(new ExchangeLine(null, existing.getDeviceId(), null));
        }
        return lines;
    }

    /** 换货产品引用校验与快照解析：填写行按产品信息取 ACTIVE 快照，无效或停用拒绝；空集合返回空结果。 */
    private Map<Long, ProductOfficialSnapshot> resolveExchangeProductSnapshots(List<ExchangeLine> lines) {
        List<Long> productIds = lines.stream().map(line -> line.exchangeProductId)
                .filter(Objects::nonNull).distinct().toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return assetProductOfficialApi.getActiveProductSnapshots(productIds).stream()
                .collect(Collectors.toMap(ProductOfficialSnapshot::id, Function.identity()));
    }

    /** 行解析结果：清单行（范围事实）与旧序列号行（设备事实）分流；显式行与已保存快照共用。 */
    private static final class ExchangeLine {
        private DeliveryScopeLineRef scopeRef;
        private Long legacyDeviceId;
        private DeliveryScopeLineFact scopeFact;
        private SelectedProjectDevice device;
        private Long exchangeProductId;

        private ExchangeLine(DeliveryScopeLineRef scopeRef, Long legacyDeviceId, Long exchangeProductId) {
            this.scopeRef = scopeRef;
            this.legacyDeviceId = legacyDeviceId;
            this.exchangeProductId = exchangeProductId;
        }

        private String refKey() {
            return scopeRef != null ? MaterialExchangeServiceImpl.refKey(scopeRef) : "V" + legacyDeviceId;
        }
    }

    private static String refKey(DeliveryScopeLineRef scopeRef) {
        return scopeRef.scopeDetailId() != null ? "D" + scopeRef.scopeDetailId() : "S" + scopeRef.scopeId();
    }

    /** 范围行事实的引用键，与行解析键同口径。 */
    private static String factRefKey(DeliveryScopeLineFact fact) {
        return fact.scopeDetailId() != null ? "D" + fact.scopeDetailId() : "S" + fact.scopeId();
    }

    /** 明细行换货数量按行引用汇总；缺省按 1 台处理。 */
    private Map<String, BigDecimal> serialQuantities(MaterialExchangeSaveReqVO request) {
        if (request.getSerials() == null) {
            return Map.of();
        }
        return request.getSerials().stream().collect(Collectors.toMap(this::lineRefKey,
                serial -> serial.getQuantity() == null ? BigDecimal.ONE : serial.getQuantity(),
                (first, second) -> first));
    }

    private String lineRefKey(MaterialExchangeSerialVO serial) {
        if (serial.getScopeDetailId() != null) {
            return "D" + serial.getScopeDetailId();
        }
        return serial.getScopeId() != null ? "S" + serial.getScopeId() : "V" + serial.getDeviceId();
    }

    private BigDecimal serialQuantityTotal(MaterialExchangeSaveReqVO request) {
        return serialQuantities(request).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 主表产品编码：勾选清单行物料编码去重、半角逗号拼接；无清单行（仅旧序列号行）返回 null。 */
    private String joinedProductCode(List<ExchangeLine> lines) {
        String joined = lines.stream().map(line -> line.scopeFact)
                .filter(Objects::nonNull)
                .map(DeliveryScopeLineFact::itemCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
        return joined.isBlank() ? null : joined;
    }

    /** 持久化设备行服务器快照；显式行顺序与客户端一致，数量按行引用回填；换货产品快照按引用由服务端写入。 */
    private void saveSerials(Long exchangeId, List<ExchangeLine> lines, Map<String, BigDecimal> quantities,
                             Map<Long, ProductOfficialSnapshot> productSnapshots) {
        for (ExchangeLine line : lines) {
            MaterialExchangeSerialDO row = new MaterialExchangeSerialDO();
            row.setExchangeId(exchangeId);
            row.setQuantity(quantities.getOrDefault(line.refKey(), BigDecimal.ONE));
            if (line.scopeRef != null) {
                DeliveryScopeLineFact fact = line.scopeFact;
                row.setScopeDetailId(fact.scopeDetailId());
                row.setScopeId(fact.scopeId());
                row.setOrderNo(fact.orderNo());
                row.setLineNo(fact.lineNo());
                row.setItemCode(fact.itemCode());
                row.setProductName(fact.productName());
                row.setProductCode(fact.productCode());
                row.setDeviceTypeCode(fact.deviceTypeCode());
                row.setDeviceTypeName(fact.deviceTypeName());
            } else {
                SelectedProjectDevice device = line.device;
                row.setDeviceId(device.equipmentId());
                row.setSn(device.sn());
                row.setProductName(device.name());
                row.setProductCode(device.productCode());
                row.setProductModel(device.productModel());
                row.setContractNo(device.contractNo());
            }
            if (line.exchangeProductId != null) {
                ProductOfficialSnapshot snapshot = productSnapshots.get(line.exchangeProductId);
                if (snapshot == null) {
                    throw exception(MATERIAL_EXCH_PRODUCT_INVALID);
                }
                row.setProductId(snapshot.id());
                row.setProductName(snapshot.productName());
                row.setProductCode(snapshot.productCode());
                row.setProductModel(snapshot.productModel());
            } else {
                row.setProductId(null);
            }
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
        // 3. 提交前重校验设备行归属：清单行走范围事实，旧行走设备选择
        var scopeRefs = new ArrayList<DeliveryScopeLineRef>();
        var legacyIds = new ArrayList<Long>();
        for (MaterialExchangeSerialDO row : serialMapper.selectByExchange(new MaterialExchangeSerialQuery(id))) {
            if (row.getScopeDetailId() != null) {
                scopeRefs.add(DeliveryScopeLineRef.ofDetail(row.getScopeDetailId()));
            } else if (row.getScopeId() != null) {
                scopeRefs.add(DeliveryScopeLineRef.ofScope(row.getScopeId()));
            } else if (row.getDeviceId() != null) {
                legacyIds.add(row.getDeviceId());
            }
        }
        if (scopeRefs.isEmpty() && legacyIds.isEmpty() && entity.getDeviceId() != null) {
            legacyIds.add(entity.getDeviceId());
        }
        scopeLineFactApi.validateSelection(entity.getProjectId(), scopeRefs);
        if (!legacyIds.isEmpty()) {
            deviceSelectionApi.validateSelection(entity.getProjectId(), legacyIds);
        }
        // 4. 更新状态
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
        if (version == null || !Objects.equals(entity.getVersion(), version.longValue())) {
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
