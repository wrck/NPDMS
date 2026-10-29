/** 独立路由的业务条件；不是持久授权，服务端逐请求重新校验。 */
export const OFFICE_READONLY_PATH = '/pms/project/office-readonly'
export function officeProjectReadContext(location?: Pick<Location, 'pathname' | 'search'>) {
  location ||= typeof window !== 'undefined' ? window.location : undefined
  if (!location) return undefined
  if (!location.pathname.endsWith(OFFICE_READONLY_PATH)) return undefined
  const query = new URLSearchParams(location.search)
  return { projectId: query.get('projectId') || '', projectName: query.get('projectName') || '', contractNo: query.get('contractNo') || '' }
}

export const officeReadonlyRequestAllowed = (method: string, url: string, responseType?: string) =>
  method.toUpperCase() === 'GET' && !['blob', 'arraybuffer'].includes(responseType || '') &&
  !/download|export|print|ticket|presign|\/content(?:\/|$)|\/file\/.*?\/get/i.test(url)

export function applyOfficeReadonlyRequest(config: { url?: string; method?: string; responseType?: string; headers: Record<string, any> }) {
  const context = officeProjectReadContext()
  if (!context || /\/system\/auth\//.test(config.url || '')) return
  if (!officeReadonlyRequestAllowed(config.method || 'GET', config.url || '', config.responseType)) {
    throw new Error('当前为办事处项目只读查看，不能办理或下载')
  }
  config.headers['X-Pms-Office-Project'] = context.projectId
  config.headers['X-Pms-Office-Name'] = encodeURIComponent(context.projectName)
  config.headers['X-Pms-Office-Contract'] = encodeURIComponent(context.contractNo)
}

/** 旧业务面板的只读交互边界；服务端另行禁止所有写入和下载。导航、查询、查看及分页仍可使用。 */
export function officeReadonlyControlAllowed(element: Element) {
  if (element.matches('.rail-item, .flow-item, .station, .rail-more') || element.closest('.mobile-stage-options, .el-tabs__header, .el-pagination, .el-select, .el-date-picker, [data-readonly-navigation]')) return true
  if (element.closest('a[href], [download]')) return false
  const text = (element.getAttribute('aria-label') || element.textContent || '').trim()
  return /^(查看|详情|明细|历史|查询|搜索|筛选|刷新|重置|展开|收起|返回|关闭|取消|重新装载|加载更多|阶段状态查看|选择任务)/.test(text) &&
    !/(下载|导出|打印|提交|办理|编辑|删除|新建|创建|保存|重排|发起|执行)/.test(text)
}
