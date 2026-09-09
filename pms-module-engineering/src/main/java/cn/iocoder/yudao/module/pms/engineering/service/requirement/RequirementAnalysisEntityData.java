package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;

import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_CONTENT_INVALID;

/** Ordinary PRE-04 data belongs to the business entity, not the form runtime context. */
public final class RequirementAnalysisEntityData {
    private RequirementAnalysisEntityData() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> values(PreparationDO entity) {
        if (entity == null || entity.getEntityValueJson() == null) {
            throw exception(REQUIREMENT_ANALYSIS_CONTENT_INVALID);
        }
        try {
            Map<String, Object> values = JsonUtils.parseObject(entity.getEntityValueJson(), Map.class);
            if (values == null) throw exception(REQUIREMENT_ANALYSIS_CONTENT_INVALID);
            return new LinkedHashMap<>(values);
        } catch (RuntimeException invalid) {
            throw exception(REQUIREMENT_ANALYSIS_CONTENT_INVALID);
        }
    }
}
