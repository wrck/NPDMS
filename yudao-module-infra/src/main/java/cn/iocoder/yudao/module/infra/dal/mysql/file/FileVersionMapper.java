package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileVersionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileVersionMapper extends BaseMapperX<FileVersionDO> {

    default FileVersionDO selectByArtifactAndDigest(Long artifactId, String contentSha256) {
        return selectOne(new LambdaQueryWrapperX<FileVersionDO>()
                .eq(FileVersionDO::getArtifactId, artifactId)
                .eq(FileVersionDO::getContentSha256, contentSha256));
    }
}