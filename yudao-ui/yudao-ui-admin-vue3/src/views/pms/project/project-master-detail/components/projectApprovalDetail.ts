import { inject, type InjectionKey } from 'vue'
import { useRouter } from 'vue-router'

export const projectApprovalDetailKey: InjectionKey<(id: string) => void> = Symbol('projectApprovalDetail')

// 项目详情提供页内抽屉；相同业务组件在独立页面使用时保留原流程详情路由。
export const useProjectApprovalDetail = () => {
  const openDrawer = inject(projectApprovalDetailKey, null)
  const router = useRouter()
  return openDrawer || ((id: string) => router.push({ name: 'BpmProcessInstanceDetail', query: { id } }))
}
