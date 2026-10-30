package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeLineFactApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeLineFact;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeLineRef;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeDetailMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.commerce.enums.ErrorCodeConstants.COMMERCE_SCOPE_LINE_INVALID;

/**
 * 设备清单行事实校验：清单行 = 合同对应销售订单行在项目内的交付范围分配。
 * 只校验归属与有效性（当前有效范围行 + 未删除明细），不锁定交付范围，不校验数量上限。
 */
@Service
public class DeliveryScopeLineFactService implements DeliveryScopeLineFactApi {

    @Resource
    private DeliveryScopeMapper scopeMapper;

    @Resource
    private DeliveryScopeDetailMapper detailMapper;

    @Override
    public List<DeliveryScopeLineFact> validateSelection(Long projectId, List<DeliveryScopeLineRef> lines) {
        // 空集合筛选返回空结果，不因省略条件扩大查询范围
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        if (projectId == null || projectId <= 0
                || lines.stream().anyMatch(this::invalidRef)
                || lines.stream().map(this::refKey).distinct().count() != lines.size()) {
            throw exception(COMMERCE_SCOPE_LINE_INVALID);
        }
        // 1. 明细拆分行先查明细：明细引用不携带范围行，归属须经由明细行取得
        var detailIds = lines.stream().map(DeliveryScopeLineRef::scopeDetailId).filter(Objects::nonNull).toList();
        Map<Long, DeliveryScopeDetailDO> details = detailIds.isEmpty() ? Map.of()
                : detailMapper.selectList(new LambdaQueryWrapperX<DeliveryScopeDetailDO>()
                        .in(DeliveryScopeDetailDO::getId, detailIds)).stream()
                        .collect(Collectors.toMap(DeliveryScopeDetailDO::getId, Function.identity()));
        if (details.size() != detailIds.size()) {
            throw exception(COMMERCE_SCOPE_LINE_INVALID);
        }
        // 2. 未拆分范围行引用直接指定，明细引用经由明细归属；归属当前项目且当前有效
        var scopeIds = lines.stream().map(DeliveryScopeLineRef::scopeId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        details.values().forEach(detail -> {
            if (detail.getDeliveryScopeId() != null) {
                scopeIds.add(detail.getDeliveryScopeId());
            }
        });
        List<Long> scopeIdList = List.copyOf(scopeIds);
        Map<Long, DeliveryScopeDO> scopes = scopeIdList.isEmpty() ? Map.of()
                : scopeMapper.selectList(new LambdaQueryWrapperX<DeliveryScopeDO>()
                        .in(DeliveryScopeDO::getId, scopeIdList)
                        .eq(DeliveryScopeDO::getProjectId, projectId)
                        .isNull(DeliveryScopeDO::getEffectiveTo)).stream()
                        .collect(Collectors.toMap(DeliveryScopeDO::getId, Function.identity()));
        if (scopes.size() != scopeIdList.size()) {
            throw exception(COMMERCE_SCOPE_LINE_INVALID);
        }
        // 3. 组装服务器快照；未拆分范围行名称取订单行物料描述
        return lines.stream().map(line -> {
            if (line.scopeDetailId() != null) {
                DeliveryScopeDetailDO detail = details.get(line.scopeDetailId());
                DeliveryScopeDO scope = scopes.get(detail.getDeliveryScopeId());
                String productCode = nonblank(detail.getProductCode()) ? detail.getProductCode()
                        : scope.getProductCode();
                return new DeliveryScopeLineFact(detail.getId(), scope.getId(), scope.getOrderNo(),
                        scope.getLineNo(), productCode, detail.getProductName(),
                        detail.getDeviceTypeCode(), detail.getDeviceTypeName(), detail.getAllocatedQty());
            }
            DeliveryScopeDO scope = scopes.get(line.scopeId());
            return new DeliveryScopeLineFact(null, scope.getId(), scope.getOrderNo(), scope.getLineNo(),
                    scope.getProductCode(), scope.getProductDesc(), null, null, scope.getAllocatedQty());
        }).toList();
    }

    /** 引用对必须恰好指定一端，且端值必须为正。 */
    private boolean invalidRef(DeliveryScopeLineRef line) {
        return (line.scopeDetailId() == null) == (line.scopeId() == null)
                || line.scopeDetailId() != null && line.scopeDetailId() <= 0
                || line.scopeId() != null && line.scopeId() <= 0;
    }

    private static boolean nonblank(String value) {
        return value != null && !value.isBlank();
    }

    private String refKey(DeliveryScopeLineRef line) {
        return line.scopeDetailId() != null ? "D" + line.scopeDetailId() : "S" + line.scopeId();
    }
}
