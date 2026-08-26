package cn.iocoder.yudao.module.pms.platform.dal.mysql.file;

import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionCursorQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionLockQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FileVersionMapper {

    int insert(@Param("row") FileVersionDO row);

    FileVersionDO selectForUpdate(@Param("query") FileVersionLockQuery query);

    List<FileVersionDO> selectCursor(@Param("query") FileVersionCursorQuery query);
}
