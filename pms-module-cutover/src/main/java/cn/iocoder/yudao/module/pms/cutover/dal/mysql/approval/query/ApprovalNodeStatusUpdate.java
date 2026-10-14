package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;

public record ApprovalNodeStatusUpdate(CutoverApprovalNodeDO node, Integer expectedVersion,
                                       String expectedStatusCode) {
}
