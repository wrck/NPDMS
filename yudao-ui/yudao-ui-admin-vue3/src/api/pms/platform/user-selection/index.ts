import request from '@/config/axios'
import type { ScopedUserSelectionSource, SystemUserSelectionUser, SystemUserId } from '@/api/system/user/selection'

// Same enabled-user directory scope as simple-list; email is matched by SYSTEM and never returned.
export const searchPmsUserOptions = (params: {
  keyword: string
  pageNo: number
  pageSize: number
  userIds?: string
}): Promise<{ list: SystemUserSelectionUser[]; total: number }> =>
  request.get({ url: '/api/v1/pms/user-selections', params })

export const resolvePmsUserOptions = async (ids: SystemUserId[]) => {
  if (!ids.length) return []
  const result: SystemUserSelectionUser[] = []
  for (let offset = 0; offset < ids.length; offset += 100) {
    const page = await searchPmsUserOptions({ keyword: '', pageNo: 1, pageSize: 100, userIds: ids.slice(offset, offset + 100).join(',') })
    result.push(...page.list)
  }
  return result
}

export const pmsUserSelectionSource = (key: string): ScopedUserSelectionSource => ({
  key,
  search: searchPmsUserOptions,
  resolve: resolvePmsUserOptions
})
