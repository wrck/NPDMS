package cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution;

/** 中性过程定义引用：稳定编码与冻结版本，不携带某引擎表达式或旧运行时对象。 */
public record ProcessDefinitionRef(String definitionCode, int definitionVersion) {
}
