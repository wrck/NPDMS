/**
 * yudao useMessage 适配层。
 *
 * <p>对 ElMessage / ElMessageBox / ElNotification 的薄封装，
 * 提供 yudao 原生页面所需的 {@code success / error / warning / info /
 * confirm / delConfirm / exportConfirm / prompt} 等方法。</p>
 *
 * <p>与 yudao 原版的差异：去掉了 vue-i18n 依赖，文案直接用中文常量。</p>
 */
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'

export interface UseMessage {
  /** 消息提示（成功/错误/警告/信息） */
  success(message: string): void
  error(message: string): void
  warning(message: string): void
  info(message: string): void
  /** 关闭所有消息 */
  closeAll(): void
  /** 通知（成功/错误/警告/信息） */
  notifySuccess(title: string, message?: string): void
  notifyError(title: string, message?: string): void
  notifyWarning(title: string, message?: string): void
  notifyInfo(title: string, message?: string): void
  /** 确认对话框 */
  confirm(content: string, title?: string, options?: Record<string, unknown>): Promise<void>
  /** 删除确认对话框 */
  delConfirm(content?: string, title?: string): Promise<void>
  /** 导出确认对话框 */
  exportConfirm(content?: string, title?: string): Promise<void>
  /** 输入对话框 */
  prompt(content: string, title?: string, options?: Record<string, unknown>): Promise<string>
  /** 警告对话框 */
  alert(content: string, title?: string, options?: Record<string, unknown>): Promise<void>
}

export function useMessage(): UseMessage {
  return {
    success(message: string): void {
      ElMessage.success(message)
    },
    error(message: string): void {
      ElMessage.error(message)
    },
    warning(message: string): void {
      ElMessage.warning(message)
    },
    info(message: string): void {
      ElMessage.info(message)
    },
    closeAll(): void {
      ElMessage.closeAll()
    },
    notifySuccess(title: string, message?: string): void {
      ElNotification.success({ title, message })
    },
    notifyError(title: string, message?: string): void {
      ElNotification.error({ title, message })
    },
    notifyWarning(title: string, message?: string): void {
      ElNotification.warning({ title, message })
    },
    notifyInfo(title: string, message?: string): void {
      ElNotification.info({ title, message })
    },
    confirm(
      content: string,
      title: string = '提示',
      options: Record<string, unknown> = {}
    ): Promise<void> {
      return ElMessageBox.confirm(content, title, {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        ...options
      }).then(() => undefined)
    },
    delConfirm(
      content: string = '是否删除该数据？删除后不可恢复！',
      title: string = '删除确认'
    ): Promise<void> {
      return ElMessageBox.confirm(content, title, {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => undefined)
    },
    exportConfirm(
      content: string = '是否确认导出数据？',
      title: string = '导出确认'
    ): Promise<void> {
      return ElMessageBox.confirm(content, title, {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => undefined)
    },
    prompt(
      content: string,
      title: string = '提示',
      options: Record<string, unknown> = {}
    ): Promise<string> {
      return ElMessageBox.prompt(content, title, {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        ...options
      }).then((res) => res.value)
    },
    alert(
      content: string,
      title: string = '提示',
      options: Record<string, unknown> = {}
    ): Promise<void> {
      return ElMessageBox.alert(content, title, {
        confirmButtonText: '确定',
        ...options
      }).then(() => undefined)
    }
  }
}
