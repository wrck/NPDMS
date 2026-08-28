package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

public record DynamicFormOwnerKey(String ownerContext, String objectType, String objectId)
        implements Comparable<DynamicFormOwnerKey> {
    public DynamicFormProviderKey providerKey() {
        return new DynamicFormProviderKey(ownerContext, objectType);
    }

    @Override
    public int compareTo(DynamicFormOwnerKey other) {
        int provider = providerKey().compareTo(other.providerKey());
        return provider != 0 ? provider : objectId.compareTo(other.objectId);
    }
}
