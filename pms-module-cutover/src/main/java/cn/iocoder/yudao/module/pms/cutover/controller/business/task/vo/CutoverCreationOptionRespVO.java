package cn.iocoder.yudao.module.pms.cutover.controller.business.task.vo;

import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverIntakeConfigurationPolicy;
import java.util.List;

/** 当前配置允许的创建选项，HTTP 合同不暴露配置服务内部类型。 */
public record CutoverCreationOptionRespVO(Long revisionId, String code, String name, Integer revisionNo,
        List<Option> cutoverTypes, List<Option> networkModes, List<Option> deviceTypes) {
    public record Option(String value, String label) {
        static Option from(CutoverIntakeConfigurationPolicy.Option value) {
            return new Option(value.value(), value.label());
        }
    }
    public static CutoverCreationOptionRespVO from(CutoverIntakeConfigurationPolicy.Options value) {
        return new CutoverCreationOptionRespVO(value.revisionId(), value.code(), value.name(), value.revisionNo(),
                value.cutoverTypes().stream().map(Option::from).toList(),
                value.networkModes().stream().map(Option::from).toList(),
                value.deviceTypes().stream().map(Option::from).toList());
    }
}
