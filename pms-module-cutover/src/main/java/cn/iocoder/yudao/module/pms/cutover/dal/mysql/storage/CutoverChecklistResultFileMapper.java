package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverChecklistResultFileDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverChecklistResultFileListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 统一结果文件引用：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverChecklistResultFileMapper extends BaseMapperX<CutoverChecklistResultFileDO> {

    default List<CutoverChecklistResultFileDO> selectListByParents(CutoverChecklistResultFileListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistResultIds() == null || query.checklistResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverChecklistResultFileDO>()
                .eq(CutoverChecklistResultFileDO::getTenantId, query.tenantId())
                .in(CutoverChecklistResultFileDO::getChecklistResultId, query.checklistResultIds())
                .orderByAsc(CutoverChecklistResultFileDO::getFileNo)
                .orderByAsc(CutoverChecklistResultFileDO::getId));
    }
}
