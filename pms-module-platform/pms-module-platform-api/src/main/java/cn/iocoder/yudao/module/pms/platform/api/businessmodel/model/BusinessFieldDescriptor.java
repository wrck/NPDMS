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
        String dictionaryRef,
        int displayOrder,
        boolean listVisible,
        boolean searchable,
        boolean sortable) {
    public BusinessFieldDescriptor(String code,String name,EntityField.Type type,boolean required,
            boolean readable,boolean writable,String dictionaryRef) {
        this(code,name,type,required,readable,writable,dictionaryRef,0,readable,
                readable && scalar(type),readable && scalar(type));
    }
    public static boolean scalar(EntityField.Type type) {
        return type != EntityField.Type.TEXT_LIST && type != EntityField.Type.OBJECT_LIST;
    }
}
