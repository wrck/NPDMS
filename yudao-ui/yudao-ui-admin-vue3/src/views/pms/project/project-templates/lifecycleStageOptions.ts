import { DICT_TYPE, getDictOptions } from '@/utils/dict'

export interface LifecycleStageOption {
  code: string
  name: string
}

/** 模板阶段只允许标准生命周期 S0～S6（编译器同规则）；维护期等字典扩展项不进入模板设计。 */
const TEMPLATE_LIFECYCLE_PATTERN = /^S[0-6]$/

/** 标准生命周期阶段选项：取值与名称来自数据字典 pms_project_lifecycle_stage。 */
export const lifecycleStageOptions = (): LifecycleStageOption[] =>
  getDictOptions(DICT_TYPE.PMS_PROJECT_LIFECYCLE_STAGE)
    .filter((item) => TEMPLATE_LIFECYCLE_PATTERN.test(String(item.value)))
    .map((item) => ({ code: String(item.value), name: item.label }))
