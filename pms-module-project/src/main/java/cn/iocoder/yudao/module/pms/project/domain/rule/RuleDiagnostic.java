package cn.iocoder.yudao.module.pms.project.domain.rule;

/** Input identity and failure only; never a raw business value or an exception payload. */
public record RuleDiagnostic(String path, String component, String code, String inputKey) { }
