package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

public record DynamicFormProviderKey(String ownerContext, String objectType)
        implements Comparable<DynamicFormProviderKey> {
    @Override
    public int compareTo(DynamicFormProviderKey other) {
        int context = ownerContext.compareTo(other.ownerContext);
        return context != 0 ? context : objectType.compareTo(other.objectType);
    }
}
