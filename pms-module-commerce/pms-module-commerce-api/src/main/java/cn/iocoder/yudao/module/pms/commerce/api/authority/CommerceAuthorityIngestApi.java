package cn.iocoder.yudao.module.pms.commerce.api.authority;

import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityBatchCommand;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityBatchResult;

/** INT-01向COM提交本地权威副本的公开接收端；不包含网络连接器。 */
public interface CommerceAuthorityIngestApi {

    CommerceAuthorityBatchResult ingestBatch(CommerceAuthorityBatchCommand command);
}
