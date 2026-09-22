import { describe, expect, it } from 'vitest'
import { emptyDesigner, newQuestion, readDesigner, writeDesigner } from './questionnaireDesigner'
const fixture = () => {
  const model = emptyDesigner()
  const q = newQuestion('overall')
  q.title = '总体评价'
  q.options = [{ code: 'good', label: '满意', score: '100.00' }, { code: 'poor', label: '不满意', score: '0.00' }]
  model.questions.push(q)
  return model
}
describe('questionnaire designer frozen schema', () => {
  it('roundtrips existing scoring configuration without changing question codes or decimal strings', () => {
    const original = writeDesigner(fixture(), 'R1', '80.00')
    expect(writeDesigner(readDesigner(original), 'R1', '80.00')).toBe(original)
    expect(JSON.parse(original).questions[0].options[0].score).toBe('100.00')
  })
  it('calculates multiple-choice maximum as average, not sum', () => {
    const model = fixture()
    Object.assign(model.questions[0], { type: 'MULTIPLE_CHOICE', minSelections: 2, maxSelections: 2 })
    expect(JSON.parse(writeDesigner(model, 'R1', '40')).scoring.scoreMax).toBe('50.00')
  })
  it('calculates weighted maximum exactly and excludes text questions', () => {
    const model = fixture()
    model.strategy = 'WEIGHTED_AVERAGE_V1'
    const second = newQuestion('second')
    Object.assign(second, { title: '第二题', weight: '3', options: [{ code: 'a', label: '选项', score: '80' }] })
    model.questions.push(second, { ...newQuestion('text'), title: '意见', type: 'TEXT' })
    const json = JSON.parse(writeDesigner(model, 'R1', '80'))
    expect(json.scoring.scoreMax).toBe('85.00')
    expect(json.questions[2]).not.toHaveProperty('options')
    expect(json.questions[2]).not.toHaveProperty('weight')
  })
  it('rejects invalid or unrepresentable scores before publication', () => {
    const model = fixture()
    expect(() => writeDesigner(model, 'R1', '101')).toThrow('满分')
    model.questions.push({ ...newQuestion('overall'), title: '重复' })
    expect(() => writeDesigner(model, 'R1', '80')).toThrow('不重复')
    model.questions.pop()
    model.questions[0].type = 'MULTIPLE_CHOICE'
    model.questions[0].minSelections = model.questions[0].maxSelections = 3
    model.questions[0].options.push({ code: 'third', label: '第三项', score: '0' })
    expect(() => writeDesigner(model, 'R1', '0')).toThrow('精确到两位小数')
  })
})
