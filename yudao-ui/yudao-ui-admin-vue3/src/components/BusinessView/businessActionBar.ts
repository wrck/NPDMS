import { inject, type InjectionKey, type Ref } from 'vue'

/**
 * 项目工作区底部吸附操作栏的「业务操作区」挂载点。
 * 业务视图的视图级操作按钮（刷新、保存、提交、打开入口等）经 Teleport 收口到这里；
 * 无提供方的独立页面（如计划进度、需求分析页签）注入不到目标，按钮保持原地渲染。
 */
export const BUSINESS_ACTION_BAR_TARGET: InjectionKey<Ref<HTMLElement | null | undefined>> =
  Symbol('business-action-bar-target')

export const useBusinessActionBar = () => ({
  barTarget: inject(BUSINESS_ACTION_BAR_TARGET, undefined)
})
