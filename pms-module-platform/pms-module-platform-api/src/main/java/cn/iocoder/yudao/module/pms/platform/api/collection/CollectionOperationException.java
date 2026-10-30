package cn.iocoder.yudao.module.pms.platform.api.collection;

/** A safe, actionable collection rejection; never carries a credential or an external response body. */
public final class CollectionOperationException extends IllegalStateException {
    public CollectionOperationException(String safeMessage) { super(safeMessage); }
}
