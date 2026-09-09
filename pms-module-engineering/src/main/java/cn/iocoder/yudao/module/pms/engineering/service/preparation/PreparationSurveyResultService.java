package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationSurveyResultDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationSurveyResultMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationItemRowQuery;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.PreparationSurveyResult;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.PreparationSurveyResultRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_VERSION_NOT_MATCH;

/** PRE-02: persistence is only called under the owning preparation/item/form transaction and CAS. */
@Service
@RequiredArgsConstructor
public class PreparationSurveyResultService {
    private final PreparationSurveyResultMapper mapper;

    public PreparationSurveyResult get(Long tenantId, Long preparationId, Long itemId) {
        PreparationSurveyResultDO row = mapper.selectByItem(new PreparationItemRowQuery(tenantId, preparationId, itemId));
        return row == null ? null : BeanUtils.toBean(row, PreparationSurveyResult.class);
    }

    public void save(Long tenantId, Long preparationId, Long itemId, String itemCode,
                     PreparationSurveyResult patch, Long actorId) {
        PreparationSurveyResultRules.validatePatch(itemCode, patch);
        PreparationSurveyResultDO existing = mapper.selectByItem(new PreparationItemRowQuery(tenantId, preparationId, itemId));
        PreparationSurveyResultDO row = existing == null ? new PreparationSurveyResultDO()
                : BeanUtils.toBean(existing, PreparationSurveyResultDO.class);
        for (String field : patch.getSubmittedFields()) {
            switch (field) {
                case "powerSupply" -> row.setPowerSupply(patch.getPowerSupply());
                case "powerEnvironment" -> row.setPowerEnvironment(patch.getPowerEnvironment());
                case "networkPort" -> row.setNetworkPort(patch.getNetworkPort());
                case "fiber" -> row.setFiber(patch.getFiber());
                case "cabinet" -> row.setCabinet(patch.getCabinet());
                case "networkCable" -> row.setNetworkCable(patch.getNetworkCable());
                case "opticalModule" -> row.setOpticalModule(patch.getOpticalModule());
                case "cabinetAvailable" -> row.setCabinetAvailable(patch.getCabinetAvailable());
                case "networkCableAvailable" -> row.setNetworkCableAvailable(patch.getNetworkCableAvailable());
                case "opticalModuleAvailable" -> row.setOpticalModuleAvailable(patch.getOpticalModuleAvailable());
                case "originalOpticalModule" -> row.setOriginalOpticalModule(patch.getOriginalOpticalModule());
                default -> throw new IllegalStateException("UNVALIDATED_SURVEY_FIELD");
            }
        }
        row.setTenantId(tenantId); row.setPreparationId(preparationId); row.setItemId(itemId);
        row.setUpdater(String.valueOf(actorId));
        if (existing == null) row.setCreator(String.valueOf(actorId));
        if ((existing == null ? mapper.insert(row) : mapper.update(row)) != 1) {
            throw exception(PREPARATION_VERSION_NOT_MATCH);
        }
    }

    public void copy(Long tenantId, Long oldPreparationId, Long oldItemId,
                     Long newPreparationId, Long newItemId, Long actorId) {
        PreparationSurveyResultDO old = mapper.selectByItem(new PreparationItemRowQuery(tenantId, oldPreparationId, oldItemId));
        if (old == null) return;
        PreparationSurveyResultDO copied = BeanUtils.toBean(old, PreparationSurveyResultDO.class);
        copied.setPreparationId(newPreparationId); copied.setItemId(newItemId); copied.setTenantId(tenantId);
        copied.setCreator(String.valueOf(actorId)); copied.setUpdater(String.valueOf(actorId));
        if (mapper.insert(copied) != 1) throw exception(PREPARATION_VERSION_NOT_MATCH);
    }
}
