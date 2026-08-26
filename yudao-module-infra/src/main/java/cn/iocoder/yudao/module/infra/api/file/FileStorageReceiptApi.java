package cn.iocoder.yudao.module.infra.api.file;

import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageAccessReceipt;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageReceipt;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageStoreCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public interface FileStorageReceiptApi {

    FileStorageReceipt store(@Valid @NotNull FileStorageStoreCommand command);

    FileStorageReceipt inspect(@NotBlank String storageOperationId);

    FileStorageAccessReceipt presignGet(@NotNull Long infraFileId,
                                        @NotNull @Positive Integer expirationSeconds);

    void delete(@NotBlank String storageOperationId);

}
