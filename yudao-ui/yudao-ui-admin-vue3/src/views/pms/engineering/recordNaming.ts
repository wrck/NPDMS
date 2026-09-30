import dayjs from 'dayjs'

/**
 * 需求分析/实施方案记录名称规范：项目编码_项目名称_业务类型_(版本|年月日时分秒)。
 * 缺失的段直接跳过；无版本标签时以生成时刻的年月日时分秒收尾保证可区分。
 */
export const buildRecordName = (
  input: {
    projectCode?: string | null
    projectName?: string | null
    typeLabel?: string | null
    version?: string | number | null
  },
  now: Date = new Date()
): string =>
  [input.projectCode, input.projectName, input.typeLabel]
    .map(segment => String(segment ?? '').trim())
    .filter(Boolean)
    .concat(String(input.version ?? '').trim() || dayjs(now).format('YYYYMMDDHHmmss'))
    .join('_')
