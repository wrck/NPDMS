import { describe, expect, it, beforeEach } from 'vitest'
import {
  projectSchema,
  projectFieldMapping,
  projectRequestValidator,
  validateProjectResponse
} from '@/validators/project'
import {
  registerValidator,
  findRequestValidator,
  findResponseValidator,
  findValidators,
  clearRegistry,
  getRegistrySnapshot
} from '@/validators/registry'

describe('Project validator', () => {
  describe('projectSchema', () => {
    it('通过校验：完整合法项目', () => {
      const result = projectRequestValidator({
        projectCode: 'PMS-2026-0001',
        projectName: '网络设备升级项目',
        projectType: 'NETWORK_DEVICE',
        customerName: '客户A',
        progress: 50
      })
      expect(result.valid).toBe(true)
      expect(result.data?.projectName).toBe('网络设备升级项目')
    })

    it('失败：旧短名字段不再自动映射（projectName/projectType 必填缺失）', () => {
      const result = projectRequestValidator({
        code: 'PMS-2026-0001',
        name: '网络设备升级项目',
        type: 'NETWORK_DEVICE',
        customerName: '客户A',
        managerName: '张三'
      })
      // projectFieldMapping 已置空，短名 code/name/type/managerName 不再被映射到长前缀
      expect(result.valid).toBe(false)
      expect(result.errors).toContainEqual({
        field: 'projectName',
        message: '字段必填'
      })
      expect(result.errors).toContainEqual({
        field: 'projectType',
        message: '字段必填'
      })
    })

    it('失败：必填字段 projectName 缺失', () => {
      const result = projectRequestValidator({
        projectCode: 'PMS-2026-0001',
        projectType: 'NETWORK_DEVICE',
        customerName: '客户A'
      })
      expect(result.valid).toBe(false)
      expect(result.errors).toContainEqual({
        field: 'projectName',
        message: '字段必填'
      })
    })

    it('失败：projectType 缺失（旧短名 type 也未提供）', () => {
      const result = projectRequestValidator({
        projectCode: 'PMS-2026-0001',
        projectName: '项目A',
        customerName: '客户A'
      })
      expect(result.valid).toBe(false)
      expect(result.errors).toContainEqual({
        field: 'projectType',
        message: '字段必填'
      })
    })

    it('失败：customerName 缺失', () => {
      const result = projectRequestValidator({
        projectCode: 'PMS-2026-0001',
        projectName: '项目A',
        projectType: 'NETWORK_DEVICE'
      })
      expect(result.valid).toBe(false)
      expect(result.errors).toContainEqual({
        field: 'customerName',
        message: '字段必填'
      })
    })

    it('失败：progress 超出 0-100 范围', () => {
      const result = projectRequestValidator({
        projectCode: 'PMS-2026-0001',
        projectName: '项目A',
        projectType: 'NETWORK_DEVICE',
        customerName: '客户A',
        progress: 150
      })
      expect(result.valid).toBe(false)
      expect(result.errors.some((e) => e.field === 'progress' && e.message.includes('不能大于 100'))).toBe(true)
    })

    it('失败：projectName 超过 200 字符', () => {
      const result = projectRequestValidator({
        projectCode: 'PMS-2026-0001',
        projectName: 'A'.repeat(201),
        projectType: 'NETWORK_DEVICE',
        customerName: '客户A'
      })
      expect(result.valid).toBe(false)
      expect(result.errors.some((e) => e.field === 'projectName' && e.message.includes('200'))).toBe(true)
    })

    it('通过校验：仅识别长名字段（短名被忽略，不做映射）', () => {
      const result = projectRequestValidator({
        code: '旧code',
        projectCode: 'PMS-2026-0001',
        name: '旧名',
        projectName: '新名',
        type: 'IGNORED',
        projectType: 'NETWORK_DEVICE',
        customerName: '客户A'
      })
      // projectFieldMapping 已置空，短名 code/name/type 不再被映射；仅长名通过校验
      expect(result.valid).toBe(true)
      expect(result.data?.projectCode).toBe('PMS-2026-0001')
      expect(result.data?.projectName).toBe('新名')
      expect(result.data?.projectType).toBe('NETWORK_DEVICE')
    })

    it('normalize 后的 data 仅包含后端字段名（短名被白名单过滤）', () => {
      const result = projectRequestValidator({
        code: '旧code',
        projectCode: 'PMS-2026-0001',
        name: '旧名',
        projectName: '项目A',
        type: 'IGNORED',
        projectType: 'NETWORK_DEVICE',
        customerName: '客户A'
      })
      expect(result.valid).toBe(true)
      const data = result.data as Record<string, unknown>
      // 短名字段不在 schema 中，被白名单过滤掉
      expect('code' in data).toBe(false)
      expect('name' in data).toBe(false)
      expect('type' in data).toBe(false)
      // 长名字段保留
      expect('projectCode' in data).toBe(true)
      expect('projectName' in data).toBe(true)
      expect('projectType' in data).toBe(true)
    })
  })

  describe('validateProjectResponse', () => {
    it('响应数据含后端字段：通过', () => {
      const result = validateProjectResponse({
        id: 1,
        projectCode: 'PMS-2026-0001',
        projectName: '项目A',
        projectType: 'NETWORK_DEVICE',
        customerName: '客户A',
        progress: 30,
        version: 1
      })
      expect(result.valid).toBe(true)
    })
  })

  describe('projectFieldMapping', () => {
    it('为空对象（防御性兜底，不再做短名→长名映射）', () => {
      // 历史 mapping 曾为 { code: 'projectCode', name: 'projectName',
      // type: 'projectType', managerName: 'projectManagerName' }。
      // 现已置空：前端代码已统一使用后端字段名，validator 不再做隐式映射。
      // 保留空对象定义作为防御性兜底，避免未来若有短名意外传入时抛错。
      expect(Object.keys(projectFieldMapping)).toHaveLength(0)
    })
  })
})

describe('validators/registry', () => {
  beforeEach(() => {
    clearRegistry()
  })

  it('注册并按精确 URL 查找', () => {
    const validator = (input: unknown) => ({ valid: true, errors: [], data: input })
    registerValidator({
      method: 'POST',
      urlPattern: '/api/test',
      requestValidator: validator,
      description: 'test'
    })
    const found = findRequestValidator('POST', '/api/test')
    expect(found).toBe(validator)
  })

  it('按正则 URL 查找', () => {
    const validator = (input: unknown) => ({ valid: true, errors: [], data: input })
    registerValidator({
      method: 'GET',
      urlPattern: /^\/api\/test\/\d+$/,
      responseValidator: validator
    })
    expect(findResponseValidator('GET', '/api/test/123')).toBe(validator)
    expect(findResponseValidator('GET', '/api/test/abc')).toBeUndefined()
  })

  it('URL 匹配时剥离 query string', () => {
    const validator = (input: unknown) => ({ valid: true, errors: [], data: input })
    registerValidator({
      method: 'GET',
      urlPattern: '/api/test',
      responseValidator: validator
    })
    expect(findResponseValidator('GET', '/api/test?page=1&size=20')).toBe(validator)
  })

  it('method 大小写不敏感', () => {
    const validator = (input: unknown) => ({ valid: true, errors: [], data: input })
    registerValidator({
      method: 'POST',
      urlPattern: '/api/test',
      requestValidator: validator
    })
    expect(findRequestValidator('post', '/api/test')).toBe(validator)
    expect(findRequestValidator('Post', '/api/test')).toBe(validator)
  })

  it('后注册的优先级高（覆盖）', () => {
    const v1 = (input: unknown) => ({ valid: true, errors: [], data: input })
    const v2 = (input: unknown) => ({ valid: false, errors: [], data: undefined })
    registerValidator({ method: 'POST', urlPattern: '/api/test', requestValidator: v1 })
    registerValidator({ method: 'POST', urlPattern: '/api/test', requestValidator: v2 })
    const found = findRequestValidator('POST', '/api/test')
    expect(found).toBe(v2)
  })

  it('未注册的 URL 返回 undefined', () => {
    expect(findRequestValidator('POST', '/api/not-registered')).toBeUndefined()
  })

  it('findValidators 返回所有匹配条目', () => {
    const v1 = (input: unknown) => ({ valid: true, errors: [], data: input })
    const v2 = (input: unknown) => ({ valid: true, errors: [], data: input })
    registerValidator({ method: 'POST', urlPattern: '/api/test', requestValidator: v1 })
    registerValidator({ method: 'POST', urlPattern: /^\/api\/.*/, responseValidator: v2 })
    const found = findValidators('POST', '/api/test')
    expect(found).toHaveLength(2)
  })

  it('getRegistrySnapshot 返回当前所有注册项', () => {
    registerValidator({
      method: 'POST',
      urlPattern: '/api/test',
      description: 'test entry'
    })
    const snapshot = getRegistrySnapshot()
    expect(snapshot).toHaveLength(1)
    expect(snapshot[0].description).toBe('test entry')
  })
})
