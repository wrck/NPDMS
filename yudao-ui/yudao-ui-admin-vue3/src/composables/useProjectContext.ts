// =============================================================================
// useProjectContext - 项目上下文 provide/inject
// -----------------------------------------------------------------------------
// 在 DefaultLayout 根节点通过 `provideProjectContext()` 提供项目上下文，
// 子组件（项目树侧栏、工作区、阶段面板等）通过 `useProjectContext()` 注入。
// 上下文持有当前选中项目、当前阶段 ID、以及共享的项目列表 ref。
// =============================================================================
import { inject, provide, ref, readonly, type InjectionKey, type Ref, type DeepReadonly } from 'vue'
import type { ProjectTreeNode } from '@/api/project'

/** 当前选中项目信息（从 ProjectTreeNode / Project 提取的最小集） */
export interface CurrentProjectInfo {
  id: number
  projectCode: string
  projectName: string
  status?: string
  templateId?: number | null
  currentPhaseId?: number | null
}

/** 项目上下文契约 */
export interface ProjectContext {
  /** 当前选中的项目（只读视图） */
  currentProject: DeepReadonly<Ref<CurrentProjectInfo | null>>
  /** 当前阶段 ID（只读视图） */
  currentPhase: DeepReadonly<Ref<number | null>>
  /** 项目列表（侧栏树数据缓存，可写） */
  projectList: Ref<ProjectTreeNode[]>
  /** 设置当前项目；同时同步 currentPhase */
  setProject: (project: CurrentProjectInfo) => void
  /** 清空当前项目与阶段 */
  clearProject: () => void
}

/** InjectionKey，唯一保证 provide/inject 类型对齐 */
export const ProjectContextKey: InjectionKey<ProjectContext> = Symbol('projectContext')

/**
 * 在根组件（DefaultLayout）调用一次，提供项目上下文。
 * 返回原始 ctx，便于 provider 自身读写。
 */
export function provideProjectContext(): ProjectContext {
  const currentProject = ref<CurrentProjectInfo | null>(null)
  const currentPhase = ref<number | null>(null)
  const projectList = ref<ProjectTreeNode[]>([])

  const setProject = (project: CurrentProjectInfo) => {
    currentProject.value = project
    currentPhase.value = project.currentPhaseId ?? null
  }

  const clearProject = () => {
    currentProject.value = null
    currentPhase.value = null
  }

  const ctx: ProjectContext = {
    currentProject: readonly(currentProject),
    currentPhase: readonly(currentPhase),
    projectList,
    setProject,
    clearProject
  }

  provide(ProjectContextKey, ctx)
  return ctx
}

/**
 * 在子组件中调用，注入项目上下文。
 * 若未找到 provider，返回空上下文（no-op），避免子组件崩溃。
 */
export function useProjectContext(): ProjectContext {
  const ctx = inject<ProjectContext>(ProjectContextKey)
  if (!ctx) {
    // 返回 no-op 空上下文：值永远为 null/[]，setProject/clearProject 不做任何事
    const emptyProject = ref<CurrentProjectInfo | null>(null)
    const emptyPhase = ref<number | null>(null)
    const emptyList = ref<ProjectTreeNode[]>([])
    return {
      currentProject: readonly(emptyProject),
      currentPhase: readonly(emptyPhase),
      projectList: emptyList,
      setProject: () => {},
      clearProject: () => {}
    }
  }
  return ctx
}
