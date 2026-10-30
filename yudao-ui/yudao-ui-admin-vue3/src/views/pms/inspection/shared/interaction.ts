/** 响应丢失或同键处理中必须重放原内容，不能据此另起一次业务命令。 */
export const inspectionFailure = (error: unknown) => {
  const response = (error as { response?: { status?: number; data?: { msg?: string; data?: { reasonCode?: string } } } })?.response
  const reason = response?.data?.data?.reasonCode
  const unknown = reason === 'IN_PROGRESS' || error !== 'error' &&
    (response?.status == null || response.status >= 500 || response.status === 408)
  return {
    unknown,
    refresh: ['VERSION_CONFLICT', 'STATE_CONFLICT', 'PRECHECK_REQUIRED', 'FORBIDDEN', 'NOT_FOUND'].includes(reason || '') ||
      response?.status === 403 || response?.status === 404,
    message: response?.data?.msg || (unknown ? '暂时无法确认操作结果，请重试原请求。' : '操作未成功，请核对填写内容和当前权限。')
  }
}
