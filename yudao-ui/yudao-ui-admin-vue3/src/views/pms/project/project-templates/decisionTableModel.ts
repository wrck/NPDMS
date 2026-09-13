import type { DecisionTableDefinition } from '@/api/pms/project/project-templates/rules'

export const newDecisionTable = (): DecisionTableDefinition => ({
  key: `table_${crypto.randomUUID()}`,
  name: '规则决策表',
  decisionKey: 'decision',
  inputFields: { inputValue: '' },
  xml: `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="definitions" name="规则决策表" namespace="pms" expressionLanguage="juel">
  <decision id="decision" name="规则决策表">
    <decisionTable id="decisionTable" hitPolicy="UNIQUE">
      <input id="input" label="输入字段"><inputExpression id="inputExpression" typeRef="string" expressionLanguage="juel"><text>inputValue</text></inputExpression></input>
      <output id="output" name="result" label="结果" typeRef="boolean" />
      <rule id="rule"><inputEntry id="inputEntry"><text>-</text></inputEntry><outputEntry id="outputEntry" expressionLanguage="juel"><text>false</text></outputEntry></rule>
    </decisionTable>
  </decision>
</definitions>`
})
