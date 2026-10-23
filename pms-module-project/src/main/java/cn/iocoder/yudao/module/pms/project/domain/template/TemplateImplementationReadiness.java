package cn.iocoder.yudao.module.pms.project.domain.template;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 新版本显式列举IMP实体前置；缺省配置保留全部历史要求。 */
public record TemplateImplementationReadiness(Map<String, String> sourceTaskCodes) {
    public static final Set<String> SOURCES = Set.of("EXE_01", "EXE_02", "EXE_03", "EXE_04");
    public TemplateImplementationReadiness {
        if (sourceTaskCodes == null || !SOURCES.containsAll(sourceTaskCodes.keySet())
                || sourceTaskCodes.values().stream().anyMatch(code -> code == null || code.isBlank()))
            throw new IllegalArgumentException("实施前置来源和任务码无效");
        sourceTaskCodes = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(sourceTaskCodes));
    }

    public Set<String> requiredSources(List<TemplateExecutionSnapshot.TaskContract> tasks) {
        validate(tasks);
        var required = tasks.stream().filter(task -> !Boolean.FALSE.equals(task.getRequired()))
                .map(TemplateExecutionSnapshot.TaskContract::getCode).collect(Collectors.toSet());
        return sourceTaskCodes.entrySet().stream().filter(entry -> required.contains(entry.getValue()))
                .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }

    public void validate(List<TemplateExecutionSnapshot.TaskContract> tasks) {
        for (var entry : sourceTaskCodes.entrySet()) {
            var found = tasks.stream().filter(task -> entry.getValue().equals(task.getCode())).toList();
            if (found.size() != 1 || !supports(entry.getKey(), found.getFirst().getBinding()))
                throw new IllegalArgumentException("实施前置" + entry.getKey() + "必须关联对应IMP实体任务，不能引用文件上传任务");
        }
    }

    private static boolean supports(String source, TemplateExecutionSnapshot.BindingContract binding) {
        if (binding == null) return false;
        String type = switch (source) {
            case "EXE_01" -> "ARRIVAL";
            case "EXE_02" -> "INSTALLATION";
            case "EXE_03" -> "CONFIGURATION";
            case "EXE_04" -> "JOINT_TEST";
            default -> throw new IllegalArgumentException("实施前置来源无效");
        };
        if (Set.of("BUSINESS_OBJECT", "BUSINESS_COMPONENT").contains(String.valueOf(binding.getType())))
            return "IMP".equals(binding.getTargetContextCode()) && type.equals(binding.getTargetObjectType());
        String route = switch (source) {
            case "EXE_01" -> "/pms/engineering/execution/imp-arrival";
            case "EXE_02" -> "/pms/engineering/execution/imp-installation";
            case "EXE_03" -> "/pms/engineering/execution/imp-configuration";
            default -> "/pms/engineering/execution/imp-joint-test";
        };
        return "PAGE".equals(binding.getType()) && binding.getParameters() != null
                && route.equals(binding.getParameters().path("routePath").asText(null));
    }
}
