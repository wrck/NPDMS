/** One SYSTEM user selection provider. Policies retain each existing endpoint permission and scope. */
import * as UserApi from './index'
export type SystemUserId = string | number
export interface SystemUserPageQuery extends PageParam {
  username?: string
  nickname?: string
  mobile?: string
  status?: number
  deptId?: SystemUserId
  roleId?: SystemUserId
  createTime?: string[]
}
export type SystemUserSelectionUser = Omit<Partial<UserApi.UserVO>, 'id' | 'deptId'> & {
  id: SystemUserId
  deptId?: SystemUserId
}
export const sameSystemUserId = (a: unknown, b: unknown) =>
  a != null && b != null && String(a) === String(b)
export interface ScopedUserSelectionSource {
  key: string
  search: (query: {
    keyword: string
    pageNo: number
    pageSize: number
  }) => Promise<{ list: SystemUserSelectionUser[]; total: number }>
  resolve?: (ids: SystemUserId[]) => Promise<SystemUserSelectionUser[]>
}
export const systemUserSelection = {
  // Some workflows already receive an authorized candidate list from their owner.
  // Reuse exactly that list; do not query or supplement it from the global directory.
  fromCandidates: (
    key: string,
    values: readonly SystemUserSelectionUser[]
  ): ScopedUserSelectionSource => ({
    key: `${key}:${values.map((user) => String(user.id)).join(',')}`,
    search: async (query) => {
      const keyword = query.keyword.trim().toLowerCase()
      const matched = values.filter(
        (user) =>
          !keyword ||
          [user.nickname, user.username, user.deptName, user.email].some((value) =>
            value?.toLowerCase().includes(keyword)
          )
      )
      return {
        list: matched.slice((query.pageNo - 1) * query.pageSize, query.pageNo * query.pageSize),
        total: matched.length
      }
    },
    resolve: async (ids) => values.filter((user) => ids.some((id) => sameSystemUserId(id, user.id)))
  }),
  // Published form schemas may declare custom scoped endpoints. Preserve their exact contract;
  // never fall back to the enabled-user directory when a scoped request fails.
  schemaRequest: async (input: { url: string; method: string; data?: unknown }) => {
    if (input.method === 'GET' && input.url === '/system/user/simple-list')
      return UserApi.getSimpleUserList()
    const { default: request } = await import('@/config/axios')
    if (input.method === 'POST') return request.post({ url: input.url, data: input.data })
    return request.get({ url: input.url })
  },
  scoped: (
    source: ScopedUserSelectionSource,
    query: { keyword: string; pageNo: number; pageSize: number }
  ) => source.search(query),
  scopedResolve: (source: ScopedUserSelectionSource, ids: SystemUserId[]) =>
    ids.length && source.resolve ? source.resolve(ids) : Promise.resolve([]),
  // Business dropdown: enabled, sparse candidates under the original simple-list contract.
  active: () => UserApi.getSimpleUserList(),
  // Management search/resolve: existing system:user:query permission is still checked by SYSTEM.
  page: (query: SystemUserPageQuery) => UserApi.getUserPage(query),
  resolve: (ids: SystemUserId[]): Promise<SystemUserSelectionUser[]> => UserApi.getUserList(ids)
}
