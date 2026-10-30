package cn.iocoder.yudao.module.pms.platform.api.businessmodel.model;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;

/**
 * 目录中主动开放的业务字段。继承得到的密码、内部标识或基础控制字段不出现在目录，
 * 不代表可被前端读取或修改。
 */
public record BusinessFieldDescriptor(
        String code,
        String name,
        EntityField.Type type,
        boolean required,
        boolean readable,
        boolean writable,
        String dictionaryRef) {
}
