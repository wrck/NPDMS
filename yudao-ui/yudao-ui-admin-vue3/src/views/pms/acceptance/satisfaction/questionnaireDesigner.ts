export interface DesignerQuestion {
  code: string
  title: string
  type: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'RATING' | 'TEXT'
  required: boolean
  options: { code: string; label: string; score: string }[]
  weight: string
  minSelections: number
  maxSelections: number
  minLength: number
  maxLength: number
}
export interface DesignerModel {
  questions: DesignerQuestion[]
  strategy: 'SUM_V1' | 'WEIGHTED_AVERAGE_V1'
  precision: number
  roundingMode: 'HALF_UP' | 'HALF_EVEN' | 'DOWN'
}
export const emptyDesigner = (): DesignerModel => ({ questions: [], strategy: 'SUM_V1', precision: 2, roundingMode: 'HALF_UP' })
export const newQuestion = (code: string): DesignerQuestion => ({
  code, title: '', type: 'RATING', required: true, options: [], weight: '1.00',
  minSelections: 1, maxSelections: 1, minLength: 0, maxLength: 1000
})
export const readDesigner = (json: string): DesignerModel => {
  const root = JSON.parse(json)
  if (root.schemaVersion !== 1 || !Array.isArray(root.questions) || !root.scoring) throw new Error('问卷版本不可编辑')
  return {
    questions: root.questions.map((q: DesignerQuestion) => ({ ...newQuestion(q.code), ...q })),
    strategy: root.scoring.strategy, precision: root.scoring.precision, roundingMode: root.scoring.roundingMode
  }
}
const cents = (value: string, label: string): bigint => {
  if (!/^\d{1,5}(\.\d{1,2})?$/.test(String(value))) throw new Error(`${label}须为非负数，最多两位小数、五位整数`)
  const [whole, fraction = ''] = String(value).split('.')
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'))
}
const decimal = (value: bigint) => `${value / 100n}.${String(value % 100n).padStart(2, '0')}`
const integerRange = (min: number, max: number, floor: number, label: string) => {
  if (!Number.isInteger(min) || !Number.isInteger(max) || min < floor || max < min) throw new Error(`${label}范围无效`)
}
// Exact fractions preserve the backend's maximum-score contract; rounding a maximum would make publication invalid.
export const writeDesigner = (model: DesignerModel, ruleVersion: string, threshold: string): string => {
  if (!ruleVersion.trim()) throw new Error('请填写规则版本')
  if (!['SUM_V1', 'WEIGHTED_AVERAGE_V1'].includes(model.strategy) ||
    !['HALF_UP', 'HALF_EVEN', 'DOWN'].includes(model.roundingMode) ||
    !Number.isInteger(model.precision) || model.precision < 0 || model.precision > 2) throw new Error('评分规则无效')
  const codes = new Set<string>()
  let numerator = 0n, denominator = 1n, weightSum = 0n, scored = 0
  const questions = model.questions.map(q => {
    if (!q.code.trim() || codes.has(q.code) || !q.title.trim()) throw new Error('请填写不重复的题目编码和题目标题')
    codes.add(q.code)
    const base = { code: q.code, title: q.title, type: q.type, required: q.required }
    if (q.type === 'TEXT') {
      integerRange(q.minLength, q.maxLength, 0, '字数')
      return { ...base, minLength: q.minLength, maxLength: q.maxLength }
    }
    if (!['RATING', 'SINGLE_CHOICE', 'MULTIPLE_CHOICE'].includes(q.type)) throw new Error('题型无效')
    if (!q.options.length) throw new Error(`请为“${q.title}”添加选项`)
    const optionCodes = new Set<string>()
    const options = q.options.map(o => {
      if (!o.code.trim() || optionCodes.has(o.code) || !o.label.trim()) throw new Error('请填写不重复的选项编码和选项内容')
      optionCodes.add(o.code)
      return { code: o.code, label: o.label, score: decimal(cents(o.score, '选项分值')) }
    })
    let count = 1
    if (q.type === 'MULTIPLE_CHOICE') {
      integerRange(q.minSelections, q.maxSelections, 1, '选择数量')
      if (q.maxSelections > options.length) throw new Error('最多选择数量不能超过选项数')
      count = q.minSelections
    }
    const maximum = options.map(o => cents(o.score, '分值')).sort((a, b) => a > b ? -1 : a < b ? 1 : 0)
      .slice(0, count).reduce((sum, n) => sum + n, 0n)
    const weight = model.strategy === 'WEIGHTED_AVERAGE_V1' ? cents(q.weight, '权重') : 1n
    if (weight <= 0n) throw new Error('权重必须大于0')
    numerator = numerator * BigInt(count) + maximum * weight * denominator
    denominator *= BigInt(count)
    weightSum += weight
    scored++
    return { ...base, options,
      ...(q.type === 'MULTIPLE_CHOICE' ? { minSelections: q.minSelections, maxSelections: q.maxSelections } : {}),
      ...(model.strategy === 'WEIGHTED_AVERAGE_V1' ? { weight: decimal(weight) } : {}) }
  })
  if (!scored) throw new Error('至少添加一道评分题')
  if (model.strategy === 'WEIGHTED_AVERAGE_V1') denominator *= weightSum
  if (numerator % denominator !== 0n) throw new Error('当前满分无法精确到两位小数，请调整选项分值、最少选择数量或权重')
  const maximum = numerator / denominator
  cents(decimal(maximum), '满分')
  const target = cents(threshold, '达标阈值')
  if (target > maximum) throw new Error(`达标阈值不能超过满分 ${decimal(maximum)}`)
  return JSON.stringify({ schemaVersion: 1, questions, scoring: { ruleVersion, strategy: model.strategy,
    scoreMin: '0.00', scoreMax: decimal(maximum), precision: model.precision,
    roundingMode: model.roundingMode, threshold: decimal(target) } })
}
