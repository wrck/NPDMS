import { expect, it } from 'vitest'
import { commonPlannedAddress, plannedAddressCandidates } from './plannedInstallationAddress'
const plan = { solutionId: '9007199254740993', solutionVersion: 3, solutionName: '批准方案', addresses: [
  { productCode: 'A', location: '机房一' }, { productCode: 'B', location: '机房一' },
  { productCode: 'C', location: '机房二' }, { productCode: 'D', location: '机房一' }, { productCode: 'D', location: '机房二' }
] }
it('uses exact product codes and only a common address for the complete selected batch', () => {
  expect(commonPlannedAddress(plan, [' A ', 'B', 'A'])).toBe('机房一')
  expect(commonPlannedAddress(plan, ['A', 'C'])).toBeUndefined()
  expect(commonPlannedAddress(plan, ['A', 'MISSING'])).toBeUndefined()
  expect(commonPlannedAddress(plan, ['AA'])).toBeUndefined()
})
it('does not pick an arbitrary location from ambiguous or missing product identities', () => {
  expect(commonPlannedAddress(plan, ['D'])).toBeUndefined()
  expect(commonPlannedAddress(plan, ['A', undefined])).toBeUndefined()
  expect(commonPlannedAddress(plan, [])).toBeUndefined()
})
it('offers a single global address regardless of product identity', () => {
  const single = { ...plan, addresses: [{ productCode: 'OTHER', location: '机房一' }, { productCode: 'A', location: ' 机房一 ' }] }
  expect(plannedAddressCandidates(single, [undefined])).toEqual(['机房一'])
})
it('offers all exact-code matches and intersects multiple selected codes', () => {
  expect(plannedAddressCandidates(plan, ['D'])).toEqual(['机房一', '机房二'])
  expect(plannedAddressCandidates(plan, ['A', 'D'])).toEqual(['机房一'])
  expect(plannedAddressCandidates(plan, ['A', 'C'])).toEqual([])
})
