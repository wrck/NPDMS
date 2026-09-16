import { describe, expect, it, beforeEach } from 'vitest'
import {
  defineSchema,
  validate,
  createValidator,
  createValidatorWithMapping,
  applyFieldMapping,
  formatErrors,
  required,
  optional,
  string,
  number,
  maxLen,
  min,
  max,
  range,
  enumOf,
  pattern,
  type Schema
} from '@/validators'

describe('validators 框架', () => {
  describe('基础校验', () => {
    const schema: Schema = defineSchema({
      name: [required(), string(), maxLen(50)],
      age: [optional(), number(), range(0, 150)],
      email: [optional(), string(), pattern(/^[^@]+@[^@]+$/)],
      status: [optional(), string(), enumOf(['A', 'B', 'C'])]
    })

    it('通过校验：所有字段符合规则', () => {
      const result = validate(schema, {
        name: 'Alice',
        age: 30,
        email: 'alice@example.com',
        status: 'A'
      })
      expect(result.valid).toBe(true)
      expect(result.errors).toHaveLength(0)
      expect(result.data).toEqual({
        name: 'Alice',
        age: 30,
        email: 'alice@example.com',
        status: 'A'
      })
    })

    it('失败：必填字段缺失', () => {
      const result = validate(schema, { age: 30 })
      expect(result.valid).toBe(false)
      expect(result.errors).toContainEqual({
        field: 'name',
        message: '字段必填'
      })
    })

    it('失败：字符串长度超限', () => {
      const result = validate(schema, { name: 'a'.repeat(51) })
      expect(result.valid).toBe(false)
      expect(result.errors.some((e) => e.field === 'name' && e.message.includes('不能超过 50'))).toBe(true)
    })

    it('失败：数字超出范围', () => {
      const result = validate(schema, { name: 'Alice', age: 200 })
      expect(result.valid).toBe(false)
      expect(result.errors.some((e) => e.field === 'age' && e.message.includes('不能大于 150'))).toBe(true)
    })

    it('失败：枚举值不匹配', () => {
      const result = validate(schema, { name: 'Alice', status: 'D' })
      expect(result.valid).toBe(false)
      expect(result.errors.some((e) => e.field === 'status' && e.message.includes('A, B, C'))).toBe(true)
    })

    it('失败：正则不匹配', () => {
      const result = validate(schema, { name: 'Alice', email: 'not-an-email' })
      expect(result.valid).toBe(false)
      expect(result.errors.some((e) => e.field === 'email' && e.message.includes('格式不正确'))).toBe(true)
    })

    it('可选字段为空时跳过后续校验', () => {
      const result = validate(schema, { name: 'Alice', age: undefined })
      expect(result.valid).toBe(true)
      expect(result.data?.name).toBe('Alice')
      expect(result.data?.age).toBeUndefined()
    })

    it('非对象输入直接失败', () => {
      const result = validate(schema, 'not an object')
      expect(result.valid).toBe(false)
      expect(result.errors[0].field).toBe('_root')
    })

    it('数组输入直接失败', () => {
      const result = validate(schema, [1, 2, 3])
      expect(result.valid).toBe(false)
      expect(result.errors[0].field).toBe('_root')
    })
  })

  describe('字段映射', () => {
    const mapping = {
      name: 'projectName',
      code: 'projectCode',
      type: 'projectType'
    }

    it('把旧字段名映射到新字段名', () => {
      const result = applyFieldMapping(
        { name: '项目A', code: 'P-001', type: 'NETWORK_DEVICE' },
        mapping
      )
      expect(result).toEqual({
        projectName: '项目A',
        projectCode: 'P-001',
        projectType: 'NETWORK_DEVICE'
      })
    })

    it('新字段名已存在时不覆盖', () => {
      const result = applyFieldMapping(
        { name: '旧名', projectName: '新名' },
        mapping
      )
      expect(result.projectName).toBe('新名')
      expect(result.name).toBe('旧名') // 旧字段保留（不删除，因为新字段已存在）
    })

    it('非对象输入原样返回', () => {
      expect(applyFieldMapping(null, mapping)).toBeNull()
      expect(applyFieldMapping([1, 2], mapping)).toEqual([1, 2])
    })
  })

  describe('createValidatorWithMapping', () => {
    const schema: Schema = defineSchema({
      projectName: [required(), string()],
      projectCode: [required(), string()],
      projectType: [required(), string()]
    })
    const mapping = {
      name: 'projectName',
      code: 'projectCode',
      type: 'projectType'
    }
    const validator = createValidatorWithMapping(schema, mapping)

    it('接受旧字段名并自动映射', () => {
      const result = validator({
        name: '项目A',
        code: 'P-001',
        type: 'NETWORK_DEVICE'
      })
      expect(result.valid).toBe(true)
      expect(result.data).toEqual({
        projectName: '项目A',
        projectCode: 'P-001',
        projectType: 'NETWORK_DEVICE'
      })
    })

    it('接受新字段名（不映射）', () => {
      const result = validator({
        projectName: '项目A',
        projectCode: 'P-001',
        projectType: 'NETWORK_DEVICE'
      })
      expect(result.valid).toBe(true)
    })

    it('混合输入：新字段优先', () => {
      const result = validator({
        name: '旧名',
        projectName: '新名',
        code: 'P-001',
        type: 'NETWORK_DEVICE'
      })
      expect(result.valid).toBe(true)
      expect(result.data?.projectName).toBe('新名')
    })

    it('失败：映射后必填字段仍缺失', () => {
      const result = validator({ name: '项目A' })
      expect(result.valid).toBe(false)
      expect(result.errors).toContainEqual({ field: 'projectCode', message: '字段必填' })
      expect(result.errors).toContainEqual({ field: 'projectType', message: '字段必填' })
    })
  })

  describe('createValidator', () => {
    it('返回的函数复用 schema', () => {
      const schema: Schema = defineSchema({
        name: [required(), string()]
      })
      const validator = createValidator(schema)
      expect(validator({ name: 'Alice' }).valid).toBe(true)
      expect(validator({}).valid).toBe(false)
    })
  })

  describe('formatErrors', () => {
    it('把错误列表拼成单条消息', () => {
      const msg = formatErrors([
        { field: 'name', message: '字段必填' },
        { field: 'age', message: '必须是数字' }
      ])
      expect(msg).toBe('name: 字段必填; age: 必须是数字')
    })

    it('空列表返回空字符串', () => {
      expect(formatErrors([])).toBe('')
    })
  })
})
