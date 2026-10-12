package cn.iocoder.yudao.module.pms.asset.service.product;

import cn.iocoder.yudao.module.pms.asset.api.migration.ProductCatalogMigrationApi.Label;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.*;

/** Explicit business name/model properties; neither arbitrary source payloads nor technical IDs. */
public final class OfficialProductCatalogLabels {
    private OfficialProductCatalogLabels() {}
    public static final String FIELD = "dppms_catalog_labels";
    public static final String LABEL = "DPPMS目录名称及型号";
    public static EntityExtensionApi.Definition definition() {
        return new EntityExtensionApi.Definition(FIELD, LABEL, EntityField.Type.OBJECT_LIST, false, null, List.of());
    }
    public static List<Label> decode(Map<String, Object> values, String productCode) {
        Object raw = values.get(FIELD);
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> list)) throw new IllegalStateException("PRODUCT_CATALOG_LABELS_INVALID");
        var labels = new ArrayList<Label>();
        for (var item : list) {
            if (!(item instanceof Map<?, ?> fields) || !fields.keySet().equals(Set.of("productCode", "productName", "productModel")))
                throw new IllegalStateException("PRODUCT_CATALOG_LABELS_INVALID");
            var label = new Label(text(fields.get("productCode")), text(fields.get("productName")), text(fields.get("productModel")));
            if (label.productCode() == null || !productCode.equals(label.productCode().trim()) || labels.contains(label))
                throw new IllegalStateException("PRODUCT_CATALOG_LABELS_IDENTITY_CONFLICT");
            labels.add(label);
        }
        return List.copyOf(labels);
    }
    public static List<Map<String, Object>> encode(List<Label> labels) {
        return labels.stream().map(label -> {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("productCode", label.productCode());
            // Platform NON_NULL serialization must not erase an explicit unknown source attribute.
            fields.put("productName", label.productName() == null ? JsonNodeFactory.instance.nullNode() : label.productName());
            fields.put("productModel", label.productModel() == null ? JsonNodeFactory.instance.nullNode() : label.productModel());
            return fields;
        }).toList();
    }
    private static String text(Object value) {
        if (value == null || value instanceof tools.jackson.databind.JsonNode node && node.isNull()) return null;
        if (!(value instanceof String text)) throw new IllegalStateException("PRODUCT_CATALOG_LABELS_INVALID");
        return text;
    }
}
