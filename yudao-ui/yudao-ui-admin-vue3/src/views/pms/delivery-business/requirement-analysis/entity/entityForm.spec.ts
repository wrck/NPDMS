import { describe, expect, it } from 'vitest'
import { businessPatch, formValues } from './entityForm'
import type { View } from '@/api/pms/engineering/requirement-analysis/entity'

describe('requirement entity business field mapping', () => {
  it('keeps structured business values in entity fields and custom values in extensions', () => {
    const detail = {
      values: { transmissionCurrentOptions: ['IPv6'], businessDeviceDetails: [{ deviceName: '设备甲' }],
        trafficConcurrency: '100', customEnabled: false, customCount: 0 },
      fieldCatalog: ['transmissionCurrentOptions', 'businessDeviceDetails', 'trafficConcurrency']
        .map(code => ({ code, type: 'TEXT', required: false })),
      extensionValueVersion: 2, extensionDefinitionRevisionId: 8,
      form: { binding: { fieldBindings: { TRANSMISSION_CURRENT_OPTIONS: 'transmissionCurrentOptions',
        BUSINESS_DEVICE_DETAILS: 'businessDeviceDetails', TRAFFIC_CONCURRENCY: 'trafficConcurrency',
        CUSTOM_ENABLED: 'customEnabled', CUSTOM_COUNT: 'customCount' } },
        fields: [{ fieldKey: 'TRANSMISSION_CURRENT_OPTIONS', componentType: 'checkbox' },
          { fieldKey: 'BUSINESS_DEVICE_DETAILS', componentType: 'group' }] }
    } as unknown as View
    expect(formValues(detail)).toMatchObject({ TRANSMISSION_CURRENT_OPTIONS: ['IPv6'], CUSTOM_ENABLED: false, CUSTOM_COUNT: 0 })
    const patch = businessPatch(detail, { TRANSMISSION_CURRENT_OPTIONS: [], BUSINESS_DEVICE_DETAILS: [],
      TRAFFIC_CONCURRENCY: null, CUSTOM_ENABLED: true })
    expect(patch.values).toEqual({ transmissionCurrentOptions: [], businessDeviceDetails: [], trafficConcurrency: null })
    expect(patch.extensionValues).toEqual({ customEnabled: true, customCount: 0 })
    expect(detail.values.trafficConcurrency).toBe('100')
  })
})
