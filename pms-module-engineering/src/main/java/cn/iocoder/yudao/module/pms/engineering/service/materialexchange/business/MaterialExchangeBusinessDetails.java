package cn.iocoder.yudao.module.pms.engineering.service.materialexchange.business;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.business.MaterialExchangeBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange.MaterialExchangeSerialMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange.query.MaterialExchangeSerialQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice;
import cn.iocoder.yudao.module.pms.asset.api.product.*;
import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeLineFactApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/** Copied native line rules over the existing detail table; the default parent owns the transaction. */
@Component @RequiredArgsConstructor
public class MaterialExchangeBusinessDetails {
    private final MaterialExchangeSerialMapper serialMapper;
    private final ProjectDeviceSelectionApi deviceSelectionApi;
    private final DeliveryScopeLineFactApi scopeLineFactApi;
    private final AssetProductOfficialApi assetProductOfficialApi;

    public void load(MaterialExchangeBusinessDO row) {
        row.setSerials(BeanUtils.toBean(serialMapper.selectByExchange(new MaterialExchangeSerialQuery(row.getId())),MaterialExchangeSerialVO.class));
    }
    public void prepare(MaterialExchangeBusinessDO before,MaterialExchangeBusinessDO row,Map<String,Object> business,jakarta.validation.Validator validator) {
        if(!Set.of("serials").containsAll(business.keySet()))throw new BusinessContractException("FIELD_NOT_OPEN","换货明细命令无效");
        var request=BeanUtils.toBean(row,MaterialExchangeSaveReqVO.class);
        request.setSerials(null);
        if(business.containsKey("serials")) {
            if(!(business.get("serials") instanceof List<?>))throw exception(MATERIAL_EXCH_SCOPE_LINE_INVALID);
            request.setSerials(JsonUtils.parseArray(JsonUtils.toJsonString(business.get("serials")),MaterialExchangeSerialVO.class));
            if(request.getSerials().stream().anyMatch(Objects::isNull))throw exception(MATERIAL_EXCH_SCOPE_LINE_INVALID);
            if(request.getSerials().stream().map(this::lineRefKey).distinct().count()!=request.getSerials().size())throw exception(MATERIAL_EXCH_SCOPE_LINE_INVALID);
            request.setQuantity(serialQuantityTotal(request));
        } else if(before==null)request.setQuantity(BigDecimal.ZERO);
        if(!validator.validate(request).isEmpty())throw new BusinessContractException("ENTITY_CONSTRAINT_INVALID","换货数量和申请内容无效");
        var lines=resolveAndValidateLines(request,before==null?null:BeanUtils.toBean(before,MaterialExchangeDO.class));
        row.setQuantity(request.getQuantity());row.setDeviceId(request.getDeviceId());row.setProductCode(joinedProductCode(lines));
        row.setProductName(null);row.setProductModel(null);row.setOriginalOrderNo(null);
        if(before==null || business.containsKey("serials"))row.setPendingSerialsJson(JsonUtils.toJsonString(resolveSerials(row.getId(),lines,serialQuantities(request),resolveExchangeProductSnapshots(lines))));
    }
    public void save(MaterialExchangeBusinessDO row) {
        if(row.getPendingSerialsJson()==null)return;
        serialMapper.deleteByExchange(new MaterialExchangeSerialQuery(row.getId()));
        for(var detail:JsonUtils.parseArray(row.getPendingSerialsJson(),MaterialExchangeSerialDO.class)) {
            detail.setExchangeId(row.getId());detail.setTenantId(row.getTenantId());
            if(serialMapper.insert(detail)!=1)throw exception(MATERIAL_EXCH_VERSION_NOT_MATCH);
        }
    }
    public void delete(MaterialExchangeBusinessDO row) {serialMapper.deleteByExchange(new MaterialExchangeSerialQuery(row.getId()));}
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
                .collect(Collectors.toMap(MaterialExchangeBusinessDetails::factRefKey, Function.identity()));
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
            return scopeRef != null ? MaterialExchangeBusinessDetails.refKey(scopeRef) : "V" + legacyDeviceId;
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
                .map(DeliveryScopeLineFact::productCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
        return joined.isBlank() ? null : joined;
    }

    /** 持久化设备行服务器快照；显式行顺序与客户端一致，数量按行引用回填；换货产品快照按引用由服务端写入。 */
    private List<MaterialExchangeSerialDO> resolveSerials(Long exchangeId, List<ExchangeLine> lines, Map<String, BigDecimal> quantities,
                             Map<Long, ProductOfficialSnapshot> productSnapshots) {
        var result = new ArrayList<MaterialExchangeSerialDO>();
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
                row.setItemCode(fact.productCode());
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
            result.add(row);
        }
        return result;
    }

}
