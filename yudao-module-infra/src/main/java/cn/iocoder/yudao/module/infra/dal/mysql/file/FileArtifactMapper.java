package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileArtifactDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileArtifactMapper extends BaseMapperX<FileArtifactDO> {

    default FileArtifactDO selectBySource(String sourceSystem, String sourceArtifactKey) {
        return selectOne(new LambdaQueryWrapperX<FileArtifactDO>()
                .eq(FileArtifactDO::getSourceSystem, sourceSystem)
                .eq(FileArtifactDO::getSourceArtifactKey, sourceArtifactKey));
    }
}