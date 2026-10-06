import { ElMessage, ElMessageBox } from 'element-plus'
export const useMessage = () => ({ success: ElMessage.success, warning: ElMessage.warning, error: ElMessage.error, prompt: ElMessageBox.prompt })
