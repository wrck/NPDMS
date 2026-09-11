import { describe, expect, it } from 'vitest'
import { blankPredicate, decodeDecisionRows, encodeDecisionRows } from './ruleDecisionModel'

describe('PM-03 rule decision normal form', () => {
  it('round-trips one predicate without changing the wire rule', () => {
    const rule = { predicate: 'TASK', parameters: { refCode: 'TASK_REVIEW' } }
    const rows = decodeDecisionRows(rule)
    expect(rows).toEqual([{ conditions: [rule] }])
    expect(encodeDecisionRows(rows!)).toEqual(rule)
  })

  it('maps decision rows to ANY of row-level ALL groups', () => {
    const rows = [
      {
        conditions: [
          { predicate: 'TASK', parameters: { refCode: 'TASK_A' } },
          { predicate: 'STATE', parameters: { refCode: 'S2_COMPLETED' } }
        ]
      },
      { conditions: [{ predicate: 'APPROVAL', parameters: { refCode: 'APPROVAL_B' } }] }
    ]

    const encoded = encodeDecisionRows(rows)
    expect(encoded).toEqual({
      operator: 'ANY',
      rules: [
        {
          operator: 'ALL',
          rules: [
            { predicate: 'TASK', parameters: { refCode: 'TASK_A' } },
            { predicate: 'STATE', parameters: { refCode: 'S2_COMPLETED' } }
          ]
        },
        { predicate: 'APPROVAL', parameters: { refCode: 'APPROVAL_B' } }
      ]
    })
    expect(decodeDecisionRows(encoded)).toEqual(rows)
  })

  it('keeps non-tabular nested rules tree-only instead of flattening semantics', () => {
    const nested = {
      operator: 'ALL',
      rules: [
        { predicate: 'TASK', parameters: { refCode: 'TASK_A' } },
        {
          operator: 'ANY',
          rules: [
            { predicate: 'STATE', parameters: { refCode: 'S2_COMPLETED' } },
            { predicate: 'STATE', parameters: { refCode: 'S3_COMPLETED' } }
          ]
        }
      ]
    }
    const baseline = structuredClone(nested)
    expect(decodeDecisionRows(nested)).toBeUndefined()
    expect(nested).toEqual(baseline)
  })

  it('uses an explicit blank predicate when all decision rows are removed', () => {
    expect(encodeDecisionRows([])).toEqual(blankPredicate())
  })
})
