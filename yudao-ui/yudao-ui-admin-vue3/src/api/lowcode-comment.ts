import { del, get, post } from '@/utils/request'

export interface LowCodeComment {
  id?: number
  configType: string
  configId: number
  userId: number
  userName?: string
  content: string
  mentions?: string
  parentId?: number
  createTime?: string
}

/** 评论线程树节点（按 parent_id 构建的多级回复树） */
export interface CommentTreeNode {
  /** 当前评论 */
  comment: LowCodeComment
  /** 子回复列表 */
  replies: CommentTreeNode[]
}

export function getComments(configType: string, configId: number) {
  return get<LowCodeComment[]>('/api/lowcode/comment', { configType, configId })
}

/** 查询线程化评论（按 parent_id 构建树） */
export function getThreadedComments(configType: string, configId: number) {
  return get<CommentTreeNode[]>('/api/lowcode/comment/threaded', { configType, configId })
}

export function addComment(data: LowCodeComment) {
  return post<LowCodeComment>('/api/lowcode/comment', data)
}

export function deleteComment(id: number) {
  return del(`/api/lowcode/comment/${id}`)
}
