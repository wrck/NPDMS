package cn.iocoder.yudao.module.pms.platform.dal.mysql.file;

import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileUploadSessionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionCompletionUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionArtifactBindingQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionTerminationUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionValidationUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionStorageBindingUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FileUploadSessionMapper {

    int insert(@Param("row") FileUploadSessionDO row);

    FileUploadSessionDO selectForUpdate(@Param("query") FileUploadSessionLockQuery query);

    FileUploadSessionDO selectArtifactBindingForUpdate(
            @Param("query") FileUploadSessionArtifactBindingQuery query);

    int beginValidationIfInitialized(@Param("query") FileUploadSessionValidationUpdate query);

    int bindStorageReceiptIfInitialized(@Param("query") FileUploadSessionStorageBindingUpdate query);

    int completeIfValidating(@Param("query") FileUploadSessionCompletionUpdate query);

    int terminateIfRetryable(@Param("query") FileUploadSessionTerminationUpdate query);
}
