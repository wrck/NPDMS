package cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverExternalDataDirectory;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverExternalDataPort;
import java.util.List;

public record CutoverExternalCatalogRespVO(List<Definition> interfaces, List<Field> contextFields, List<Field> bulletinFields) {
    public record Field(String key, String label, String valueType, boolean required) {
        static Field from(CutoverExternalDataPort.Field value) {
            return new Field(value.key(), value.label(), value.valueType(), value.required());
        }
    }
    public record Definition(String code, String name, List<Field> parameters, List<Field> responseFields) {
        static Definition from(CutoverExternalDataPort.Definition value) {
            return new Definition(value.code(), value.name(), value.parameters().stream().map(Field::from).toList(),
                    value.responseFields().stream().map(Field::from).toList());
        }
    }
    public static CutoverExternalCatalogRespVO from(CutoverExternalDataDirectory.Catalog value) {
        return new CutoverExternalCatalogRespVO(value.interfaces().stream().map(Definition::from).toList(),
                value.contextFields().stream().map(Field::from).toList(), value.bulletinFields().stream().map(Field::from).toList());
    }
}
