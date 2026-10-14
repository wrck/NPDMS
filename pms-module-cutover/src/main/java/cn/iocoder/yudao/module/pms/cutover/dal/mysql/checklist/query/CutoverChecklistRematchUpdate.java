package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;

public record CutoverChecklistRematchUpdate(CutoverChecklistDO checklist, Long expectedVersion) {
}
