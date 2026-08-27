import {
  decodeConf,
  decodeFields,
  encodeConf,
  encodeFields,
  setConfAndFields
} from '@/utils/formCreate'
import type { JsonObject } from '@/api/pms/platform/dynamic-form'
import { sameJsonValue } from './dynamicFormRuntime'

export const decodeDynamicForm = (formConfJson: JsonObject, formRulesJson: JsonObject[]) => ({
  option: decodeConf(JSON.stringify(formConfJson)) as unknown as JsonObject,
  rule: decodeFields(formRulesJson.map((rule) => JSON.stringify(rule))) as unknown as JsonObject[]
})

export const encodeDynamicForm = (designer: object) => ({
  formConfJson: JSON.parse(encodeConf(designer)) as JsonObject,
  formRulesJson: encodeFields(designer).map((rule) => JSON.parse(rule) as JsonObject)
})

export const restoreDynamicFormDesigner = (
  designer: object,
  formConfJson: JsonObject,
  formRulesJson: JsonObject[]
) =>
  setConfAndFields(
    designer,
    JSON.stringify(formConfJson),
    formRulesJson.map((rule) => JSON.stringify(rule))
  )

export const sameDynamicFormPayload = (
  current: { formConfJson: JsonObject; formRulesJson: JsonObject[] },
  intended: { formConfJson: JsonObject; formRulesJson: JsonObject[] }
) =>
  sameJsonValue(current.formConfJson, intended.formConfJson) &&
  sameJsonValue(current.formRulesJson, intended.formRulesJson)
