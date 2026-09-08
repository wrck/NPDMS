import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/config/axios'
import * as api from './index'
vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
beforeEach(() => vi.clearAllMocks())
describe('PM-03 registration public API', () => {
  it('preserves decimal Snowflake IDs in every path and selection Body without numeric coercion', () => {
    const id = '2099999999999999999'
    const revisionId = '2099999999999999998'
    api.getBusinessView(id)
    expect(request.get).toHaveBeenLastCalledWith({ url: `/api/v1/pms/business-views/${id}` })
    const body = {
      entityType: 'DYNAMIC_FORM_INSTANCE',
      viewKey: 'FORM',
      componentKey: 'PLATFORM_DYNAMIC_FORM',
      componentVersion: '1',
      dynamicFormRevisionId: revisionId
    }
    api.createBusinessView(body, 'create')
    expect(request.post).toHaveBeenLastCalledWith(expect.objectContaining({ data: body }))
    api.updateBusinessView(id, 1, body, 'update')
    expect(request.put).toHaveBeenLastCalledWith(
      expect.objectContaining({ url: `/api/v1/pms/business-views/${id}`, data: body })
    )
    for (const [action, command] of [
      ['copy', api.copyBusinessView],
      ['publish', api.publishBusinessView],
      ['disable', api.disableBusinessView]
    ] as const) {
      command(id, 1, action)
      expect(request.post).toHaveBeenLastCalledWith(
        expect.objectContaining({ url: `/api/v1/pms/business-views/${id}/actions/${action}` })
      )
    }
    api.validateBusinessView(id)
    expect(request.post).toHaveBeenLastCalledWith({
      url: `/api/v1/pms/business-views/${id}/actions/validate`
    })
    expect(() => api.getBusinessView(2099999999999999999)).toThrow('无损')
  })
  it('only sends selected Body fields, excluding Owner/provider/schema and object references', () => {
    const body = {
      entityType: 'DYNAMIC_FORM_INSTANCE',
      viewKey: 'FIELD_RECORD',
      componentKey: 'PLATFORM_DYNAMIC_FORM',
      componentVersion: '1',
      dynamicFormRevisionId: 20,
      ownerContext: 'FAKE',
      contextSchema: { instanceId: 99 },
      commandProviderKey: 'FAKE',
      allowedActions: ['ALL']
    }
    api.createBusinessView(body, 'create-key')
    expect(request.post).toHaveBeenCalledWith({
      url: '/api/v1/pms/business-views',
      data: {
        entityType: body.entityType,
        viewKey: body.viewKey,
        componentKey: body.componentKey,
        componentVersion: '1',
        dynamicFormRevisionId: 20
      },
      headers: { 'Idempotency-Key': 'create-key' }
    })
    api.updateBusinessView(8, 3, body, 'update-key')
    expect(request.put).toHaveBeenCalledWith({
      url: '/api/v1/pms/business-views/8',
      data: {
        entityType: body.entityType,
        viewKey: body.viewKey,
        componentKey: body.componentKey,
        componentVersion: '1',
        dynamicFormRevisionId: 20
      },
      headers: { 'If-Match': '3', 'Idempotency-Key': 'update-key' }
    })
  })
  it('copy/publish/disable have empty Bodies and exact CAS/idempotency headers; validate is readonly', () => {
    for (const [name, command] of [
      ['copy', api.copyBusinessView],
      ['publish', api.publishBusinessView],
      ['disable', api.disableBusinessView]
    ] as const) {
      command(8, 4, `${name}-key`)
      expect(request.post).toHaveBeenLastCalledWith({
        url: `/api/v1/pms/business-views/8/actions/${name}`,
        headers: { 'If-Match': '4', 'Idempotency-Key': `${name}-key` }
      })
    }
    api.validateBusinessView(8)
    expect(request.post).toHaveBeenLastCalledWith({
      url: '/api/v1/pms/business-views/8/actions/validate'
    })
  })
  it('uses the exact list/detail/catalog routes and does not invent delete or history endpoints', () => {
    api.getBusinessViewPage({
      pageNo: 1,
      pageSize: 10,
      entityType: 'DYNAMIC_FORM_INSTANCE',
      viewSource: 'DYNAMIC_FORM'
    })
    expect(request.get).toHaveBeenLastCalledWith({
      url: '/api/v1/pms/business-views',
      params: {
        pageNo: 1,
        pageSize: 10,
        entityType: 'DYNAMIC_FORM_INSTANCE',
        viewSource: 'DYNAMIC_FORM'
      }
    })
    api.getBusinessView(8)
    expect(request.get).toHaveBeenLastCalledWith({ url: '/api/v1/pms/business-views/8' })
    api.getBusinessViewComponents()
    expect(request.get).toHaveBeenLastCalledWith({ url: '/api/v1/pms/business-views/components' })
  })
})
