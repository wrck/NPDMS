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

const resolvePmsUserOptions = async (ids: SystemUserId[]) => {
  if (!ids.length) return []
  const result = await searchPmsUserOptions({ keyword: '', pageNo: 1, pageSize: 100, userIds: ids.join(',') })
  return result.list
}

export const pmsUserSelectionSource = (key: string): ScopedUserSelectionSource => ({
  key,
  search: searchPmsUserOptions,
  resolve: resolvePmsUserOptions
})
