package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyDetailMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyDetailQuery;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityFieldMapping;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/** Typed child rows are saved under the parent survey's optimistic-lock transaction. */
@Service
@RequiredArgsConstructor
public class SiteSurveyDetails {
    private static final EntityFieldMapping<SiteSurveyEntityDO> FIELDS = new EntityFieldMapping<>(SiteSurveyEntityDO.class);
    private final SiteSurveyDetailMapper mapper;

    public void load(SiteSurveyEntityDO survey) {
        var query = new SiteSurveyDetailQuery(survey.getTenantId(), survey.getId());
        var conditions = mapper.selectConditions(query).stream().collect(Collectors.groupingBy(SiteSurveyConditionDO::getConditionType,
                LinkedHashMap::new, Collectors.mapping(SiteSurveyConditionDO::getConditionCode, Collectors.toList())));
        Map<String, Object> values = new LinkedHashMap<>();
        FIELDS.fields().stream().filter(field -> field.type() == EntityField.Type.TEXT_LIST)
                .forEach(field -> values.put(field.code(), conditions.getOrDefault(field.code(), List.of())));
        FIELDS.write(survey, values);
        survey.setSelectedMaterials(mapper.selectMaterials(query));
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void save(SiteSurveyEntityDO survey, Long actorId) {
        var query = new SiteSurveyDetailQuery(survey.getTenantId(), survey.getId());
        var oldMaterials = mapper.selectMaterials(query).stream().collect(Collectors.toMap(SiteSurveyMaterialDO::getSn, row -> row));
        mapper.deleteConditions(query);
        mapper.deleteMaterials(query);
        var values = FIELDS.read(survey);
        FIELDS.fields().stream().filter(field -> field.type() == EntityField.Type.TEXT_LIST).forEach(field -> {
            var choices = (List<?>) values.get(field.code());
            if (choices == null) return;
            for (int index = 0; index < choices.size(); index++) {
                var row = new SiteSurveyConditionDO();
                row.setId(IdWorker.getId());
                row.setTenantId(survey.getTenantId());
                row.setSurveyId(survey.getId());
                row.setConditionType(field.code());
                row.setConditionCode((String) choices.get(index));
                row.setSortOrder(index);
                row.setCreator(actorId.toString());
                row.setUpdater(row.getCreator());
                mapper.insertCondition(row);
            }
        });
        var materials = survey.getSelectedMaterials();
        if (materials == null) return;
        for (int index = 0; index < materials.size(); index++) {
            var row = materials.get(index);
            var previous = oldMaterials.get(row.getSn());
            row.setId(previous == null ? IdWorker.getId() : previous.getId());
            row.setTenantId(survey.getTenantId());
            row.setSurveyId(survey.getId());
            row.setProjectId(survey.getProjectId());
            row.setSortOrder(index);
            row.setCreator(previous == null ? actorId.toString() : previous.getCreator());
            row.setUpdater(actorId.toString());
            row.setCreateTime(previous == null ? java.time.LocalDateTime.now() : previous.getCreateTime());
            row.setUpdateTime(java.time.LocalDateTime.now());
            mapper.insertMaterial(row);
        }
    }
}
