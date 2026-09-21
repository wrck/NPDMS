package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.module.infra.api.file.FileStorageReceiptApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageReceipt;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageStoreCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Durable storage receipt survives a business transaction rollback and is reused on callback retry. */
@Service
@RequiredArgsConstructor
public class CollectionLogStorageService {
    private final FileStorageReceiptApi storage;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public FileStorageReceipt store(String operation, byte[] bytes, String name, String mediaType) {
        var receipt = storage.inspect(operation);
        return receipt == null ? storage.store(new FileStorageStoreCommand(operation, bytes, name, mediaType)) : receipt;
    }
}
