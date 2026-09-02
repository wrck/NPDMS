package cn.iocoder.yudao.module.pms.asset.service.producttype;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeDO;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.ImportAssetProductTypeCommand;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_PRODUCT_TYPE_CODE_CONFLICT;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_PRODUCT_TYPE_CROSS_TENANT_REFERENCE;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_PRODUCT_TYPE_SOURCE_CONFLICT;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_PRODUCT_TYPE_SOURCE_STALE;

public class AssetProductTypeImportRejectedException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String rejectionCode;
    private final ImportAssetProductTypeCommand command;
    private final String currentProductTypeCode;
    private final String currentSourceSystem;
    private final String currentSourceKey;
    private final String currentSourceVersion;
    private final LocalDateTime observedSourceUpdatedAt;
    private final boolean conflict;

    private AssetProductTypeImportRejectedException(ErrorCode errorCode, String rejectionCode,
                                                    ImportAssetProductTypeCommand command,
                                                    String currentProductTypeCode,
                                                    String currentSourceSystem,
                                                    String currentSourceKey,
                                                    String currentSourceVersion,
                                                    LocalDateTime observedSourceUpdatedAt,
                                                    boolean conflict) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
        this.rejectionCode = rejectionCode;
        this.command = command;
        this.currentProductTypeCode = currentProductTypeCode;
        this.currentSourceSystem = currentSourceSystem;
        this.currentSourceKey = currentSourceKey;
        this.currentSourceVersion = currentSourceVersion;
        this.observedSourceUpdatedAt = observedSourceUpdatedAt;
        this.conflict = conflict;
    }

    public static AssetProductTypeImportRejectedException stale(ImportAssetProductTypeCommand command,
                                                                 LocalDateTime observedSourceUpdatedAt) {
        return new AssetProductTypeImportRejectedException(AST_PRODUCT_TYPE_SOURCE_STALE, "STALE_SOURCE",
                command, null, null, null, null, observedSourceUpdatedAt, false);
    }

    public static AssetProductTypeImportRejectedException sourceConflict(ImportAssetProductTypeCommand command,
                                                                          String currentProductTypeCode,
                                                                          LocalDateTime observedSourceUpdatedAt) {
        return new AssetProductTypeImportRejectedException(AST_PRODUCT_TYPE_SOURCE_CONFLICT, "SOURCE_CONFLICT",
                command, currentProductTypeCode, command.sourceSystem(), command.sourceKey(), null,
                observedSourceUpdatedAt, true);
    }

    public static AssetProductTypeImportRejectedException codeConflict(ImportAssetProductTypeCommand command,
                                                                        String currentSourceSystem,
                                                                        String currentSourceKey,
                                                                        String currentSourceVersion,
                                                                        LocalDateTime observedSourceUpdatedAt) {
        return new AssetProductTypeImportRejectedException(AST_PRODUCT_TYPE_CODE_CONFLICT,
                "PRODUCT_TYPE_CODE_CONFLICT", command, command.productTypeCode(), currentSourceSystem,
                currentSourceKey, currentSourceVersion, observedSourceUpdatedAt, true);
    }

    public static AssetProductTypeImportRejectedException sourceKeyReserved(
            ImportAssetProductTypeCommand command, LocalDateTime observedSourceUpdatedAt) {
        return new AssetProductTypeImportRejectedException(AST_PRODUCT_TYPE_SOURCE_CONFLICT,
                "SOURCE_KEY_RESERVED", command, null, command.sourceSystem(), command.sourceKey(), null,
                observedSourceUpdatedAt, false);
    }

    public static AssetProductTypeImportRejectedException productTypeCodeReserved(
            ImportAssetProductTypeCommand command, AssetProductTypeDO productType) {
        return new AssetProductTypeImportRejectedException(AST_PRODUCT_TYPE_CODE_CONFLICT,
                "PRODUCT_TYPE_CODE_RESERVED", command, command.productTypeCode(), productType.getSourceSystem(),
                productType.getSourceKey(), productType.getSourceVersion(), productType.getSourceUpdatedAt(), false);
    }

    public static AssetProductTypeImportRejectedException crossTenant(ImportAssetProductTypeCommand command) {
        return new AssetProductTypeImportRejectedException(AST_PRODUCT_TYPE_CROSS_TENANT_REFERENCE,
                "CROSS_TENANT_REFERENCE", command, null, null, null, null, null, false);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String rejectionCode() {
        return rejectionCode;
    }

    public ImportAssetProductTypeCommand command() {
        return command;
    }

    public String currentProductTypeCode() {
        return currentProductTypeCode;
    }

    public String currentSourceSystem() {
        return currentSourceSystem;
    }

    public String currentSourceKey() {
        return currentSourceKey;
    }

    public String currentSourceVersion() {
        return currentSourceVersion;
    }

    public LocalDateTime observedSourceUpdatedAt() {
        return observedSourceUpdatedAt;
    }

    public boolean conflict() {
        return conflict;
    }
}
