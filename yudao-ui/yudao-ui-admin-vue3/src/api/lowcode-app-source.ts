import axios from 'axios'
import { get, TOKEN_KEY } from '@/utils/request'
import { triggerBlobDownload } from '@/api/excel'

/**
 * 低代码应用源码导出 API（批次5-T10 前端）。
 *
 * <p>对应后端 {@code LowCodeAppSourceExportController}：预览清单 / 导出 ZIP /
 * 查询可导出应用列表。借鉴网易轻舟源码导出 — 无黑盒引擎，将低代码应用配置
 * 打包为可独立部署的源码 ZIP（JSON + DDL + POM + README）。</p>
 */

/** 应用源码导出清单（与后端 AppSourceManifest DTO 对齐） */
export interface AppSourceManifest {
  /** 导出格式版本 */
  manifestVersion?: string
  /** 应用标识（bizType 作为应用分组键） */
  appCode?: string
  /** 应用名称 */
  appName?: string
  /** 描述 */
  description?: string
  /** 导出时间 */
  exportTime?: string
  /** 导出人 */
  exportBy?: string
  /** 源系统标识 */
  sourceSystem?: string
  /** 低代码平台版本 */
  platformVersion?: string
  /** 各类型配置数量统计（configType → count） */
  configCounts?: Record<string, number>
  /** 实体表名列表（用于 DDL 导出范围说明） */
  entityTables?: string[]
  /** 是否已脱敏连接器凭据 */
  credentialsRedacted?: boolean
  /** 独立部署说明（写入 README） */
  deploymentGuide?: string
}

/** 查询可导出的应用列表（按 bizType 去重） */
export function listApps(): Promise<string[]> {
  return get<string[]>('/api/lowcode/app-source/apps')
}

/**
 * 预览导出清单（不生成 ZIP）。
 *
 * @param bizType 业务类型（应用分组键），为空时预览全部
 */
export function previewManifest(bizType?: string): Promise<AppSourceManifest> {
  return get<AppSourceManifest>('/api/lowcode/app-source/manifest', { bizType })
}

/**
 * 导出应用源码（ZIP）并以 Blob 形式返回。
 *
 * <p>该接口返回二进制流（application/zip，非统一 envelope），故绕过统一的
 * axios 响应拦截器，直接使用原始 axios 注入 token 并以 blob 形式接收。</p>
 *
 * @param bizType 业务类型（应用分组键），为空时导出全部
 */
export async function exportAsZip(bizType?: string): Promise<Blob> {
  const token = localStorage.getItem(TOKEN_KEY) || ''
  const response = await axios.get('/api/lowcode/app-source/export', {
    params: { bizType },
    responseType: 'blob',
    headers: { Authorization: `Bearer ${token}` }
  })
  return response.data as Blob
}

/**
 * 导出应用源码（ZIP）并直接触发浏览器下载。
 *
 * @param bizType 业务类型（应用分组键），为空时导出全部
 */
export async function exportAndDownload(bizType?: string): Promise<void> {
  const blob = await exportAsZip(bizType)
  const fileName = `lowcode-app${bizType ? `-${bizType}` : '-all'}.zip`
  triggerBlobDownload(blob, fileName)
}
