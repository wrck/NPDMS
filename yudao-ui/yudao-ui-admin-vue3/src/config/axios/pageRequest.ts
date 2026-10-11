// 通用分页组件“全部”使用真实条数；服务端每次读取保持 PageParam 的 200 条上限。
export const getPagedResult = async (option: any, fetch: (option: any) => Promise<any>) => {
  const params = option.params
  const size = Number(params?.pageSize)
  const pageNo = Number(params?.pageNo)
  if (!Number.isSafeInteger(size) || size <= 200 || !Number.isSafeInteger(pageNo) || pageNo < 1) {
    return fetch(option)
  }
  const offset = (pageNo - 1) * size
  const firstPage = Math.floor(offset / 200) + 1
  const read = (page: number) => fetch({ ...option, params: { ...params, pageNo: page, pageSize: 200 } })
  const first = await read(firstPage)
  if (!Array.isArray(first?.list) || !Number.isFinite(Number(first.total))) {
    throw new Error('分页接口未返回 list 和 total，无法显示全部')
  }
  const total = Number(first.total)
  const required = Math.min(size, Math.max(total - offset, 0))
  const list = first.list.slice(offset % 200, offset % 200 + required)
  for (let page = firstPage + 1; list.length < required; page++) {
    const next = await read(page)
    if (!Array.isArray(next?.list) || !next.list.length) throw new Error('分页结果不完整，请重新查询')
    list.push(...next.list.slice(0, required - list.length))
  }
  return { ...first, list, total }
}
