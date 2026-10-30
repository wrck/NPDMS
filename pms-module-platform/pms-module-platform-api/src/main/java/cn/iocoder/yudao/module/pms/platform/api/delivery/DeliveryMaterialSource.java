package cn.iocoder.yudao.module.pms.platform.api.delivery;

/** 交付材料来源语义值域：模板配置、提交校验与配置界面选择器共用此权威定义，不在调用方复制清单。 */
public enum DeliveryMaterialSource {

    UPLOAD("上传文件"),
    BUSINESS_RESULT("关联业务成果");

    private final String label;

    DeliveryMaterialSource(String label) {
        this.label = label;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }

    public record Descriptor(String code, String label) {
    }
}
