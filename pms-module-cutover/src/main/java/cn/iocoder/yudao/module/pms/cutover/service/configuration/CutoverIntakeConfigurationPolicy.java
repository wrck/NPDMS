package cn.iocoder.yudao.module.pms.cutover.service.configuration;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/** 新任务只能消费生效配置中冻结且当前仍启用的字典值。历史实例按冻结标签回看。 */
@Service
@RequiredArgsConstructor
public class CutoverIntakeConfigurationPolicy {
    private final CutoverConfigurationRevisionMapper revisions;
    private final DictDataApi dictionaries;
    private final CutoverConfigurationMetadataStore metadata;

    public List<Options> available(Long tenant) {
        LocalDateTime now = LocalDateTime.now();
        return revisions.selectEffectivePublishedList(new cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverEffectiveConfigurationListQuery(tenant, now)).stream()
                .map(this::options).filter(o -> !o.cutoverTypes().isEmpty()).toList();
    }

    public Options options(CutoverConfigurationRevisionDO revision) {
        return new Options(revision.getId(), revision.getConfigurationCode(), revision.getConfigurationName(),
                revision.getRevisionNo(), values(revision, "CUTOVER_TYPE"), values(revision, "NETWORK_MODE"),
                values(revision, "DEVICE_TYPE"));
    }

    public void validate(CutoverConfigurationRevisionDO revision, String cutoverType, String networkMode) {
        validateValue(revision, "CUTOVER_TYPE", cutoverType);
        if (networkMode != null) validateValue(revision, "NETWORK_MODE", networkMode);
    }

    private void validateValue(CutoverConfigurationRevisionDO revision, String dimension, String value) {
        String dictionary = dictionary(revision, dimension);
        if (values(revision, dimension).stream().noneMatch(v -> v.value().equals(value))) {
            throw new IllegalArgumentException("所选割接类型或组网模式不在已发布配置范围内");
        }
        dictionaries.validateDictDataList(dictionary, List.of(value));
    }

    private String dictionary(CutoverConfigurationRevisionDO revision, String dimension) {
        return metadata.dimensions(revision.getId()).stream()
                .filter(row -> dimension.equals(row.getDimensionCode()) && Boolean.TRUE.equals(row.getEnabledFlag()))
                .map(row -> row.getValueSource()).filter(source -> source.startsWith("DICT:") && source.length() > 5)
                .map(source -> source.substring(5)).findFirst().orElse("");
    }

    private List<Option> values(CutoverConfigurationRevisionDO revision, String dimension) {
        String dictionary = dictionary(revision, dimension);
        return metadata.dictionaryValues(revision.getId()).stream().filter(row -> dictionary.equals(row.getDictType()))
                .filter(row -> row.getLabel() != null).map(row -> new Option(row.getValueCode(), row.getLabel())).toList();
    }

    public record Option(String value, String label) { }
    public record Options(Long revisionId, String code, String name, Integer revisionNo,
                          List<Option> cutoverTypes, List<Option> networkModes, List<Option> deviceTypes) { }
}
