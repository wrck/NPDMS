package cn.iocoder.yudao.module.pms.platform.dal.mysql.file;

import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileArtifactActivationUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileArtifactLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileArtifactLifecycleUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FileArtifactMapper {

    int insert(@Param("row") FileArtifactDO row);

    FileArtifactDO selectOne(@Param("query") FileArtifactLockQuery query);

    FileArtifactDO selectForUpdate(@Param("query") FileArtifactLockQuery query);

    int activateDraftIfMatch(@Param("query") FileArtifactActivationUpdate query);

    int updateLifecycleIfMatch(@Param("query") FileArtifactLifecycleUpdate query);
}
