package cn.iocoder.yudao.module.pms.engineering.dal.mysql.doctemplate;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.doctemplate.DocTemplateVersionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DocTemplateVersionMapper extends BaseMapperX<DocTemplateVersionDO> {

    /**
     * 按模板ID+版本标签查询，用于唯一性校验
     */
    default DocTemplateVersionDO selectByTemplateIdAndVersionLabel(Long templateId, String versionLabel) {
        return selectOne(DocTemplateVersionDO::getTemplateId, templateId,
                DocTemplateVersionDO::getVersionLabel, versionLabel);
    }

    /**
     * 按模板ID查询全部版本（按ID倒序）
     */
    default List<DocTemplateVersionDO> selectListByTemplateId(Long templateId) {
        return selectList(new LambdaQueryWrapperX<DocTemplateVersionDO>()
                .eq(DocTemplateVersionDO::getTemplateId, templateId)
                .orderByDesc(DocTemplateVersionDO::getId));
    }

    /**
     * 查询模板的已发布版本（取最新一条）
     */
    default DocTemplateVersionDO selectPublishedVersion(Long templateId) {
        return selectOne(new LambdaQueryWrapperX<DocTemplateVersionDO>()
                .eq(DocTemplateVersionDO::getTemplateId, templateId)
                .eq(DocTemplateVersionDO::getPublished, 1)
                .orderByDesc(DocTemplateVersionDO::getId)
                .last("LIMIT 1"));
    }

}
