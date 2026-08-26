package cn.iocoder.yudao.module.pms.platform.dal.mysql.file;

import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.ExactFileReferenceQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceCursorQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceReplaceVersionUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceStateUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileArtifactReferenceQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FileReferenceMapper {

    int insert(@Param("row") FileReferenceDO row);

    FileReferenceDO selectExact(@Param("query") ExactFileReferenceQuery query);

    FileReferenceDO selectForUpdate(@Param("query") FileReferenceLockQuery query);

    List<FileReferenceDO> selectCursor(@Param("query") FileReferenceCursorQuery query);

    int replaceVersionIfMatch(@Param("query") FileReferenceReplaceVersionUpdate query);

    int updateStateIfMatch(@Param("query") FileReferenceStateUpdate query);

    List<FileReferenceDO> selectByArtifactForUpdate(@Param("query") FileArtifactReferenceQuery query);
}
