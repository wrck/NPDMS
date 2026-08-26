package cn.iocoder.yudao.module.pms.platform.dal.mysql.file;

import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileUploadSessionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionCompletionUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionValidationUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FileUploadSessionMapper {

    int insert(@Param("row") FileUploadSessionDO row);

    FileUploadSessionDO selectForUpdate(@Param("query") FileUploadSessionLockQuery query);

    int beginValidationIfInitialized(@Param("query") FileUploadSessionValidationUpdate query);

    int completeIfValidating(@Param("query") FileUploadSessionCompletionUpdate query);
}
