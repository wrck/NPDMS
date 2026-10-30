package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import java.util.Set;

/**
 * PMS business dropdowns using the ordinary enabled-user directory scope.
 * Not a project membership or role qualification API. Email is searchable but never returned.
 */
public interface BusinessUserSelectionApi {
    PageResult<Option> page(Query query);

    record Query(int pageNo, int pageSize, String keyword, Set<Long> userIds) {
        public Query(int pageNo, int pageSize, String keyword) {
            this(pageNo, pageSize, keyword, null);
        }
        public Query {
            userIds = userIds == null ? null : Set.copyOf(userIds);
        }
    }
    record Option(Long id, String nickname, Long deptId, String deptName) { }
}
