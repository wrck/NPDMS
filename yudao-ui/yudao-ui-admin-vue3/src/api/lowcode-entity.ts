// src/api/lowcode-entity.ts
import { del, get, post } from '@/utils/request'

export interface LowCodeEntity {
  id?: number
  code: string
  name: string
  tableName: string
  description?: string
  bizType?: string
  status?: string
  version?: number
}

export interface LowCodeField {
  id?: number
  entityId?: number
  name: string
  label: string
  fieldType: 'STRING' | 'INTEGER' | 'LONG' | 'DECIMAL' | 'BOOLEAN' | 'DATE' | 'DATETIME' | 'TEXT'
  length?: number
  scale?: number
  nullable: number
  primaryKey: number
  indexed: number
  uniqueFlag: number
  defaultValue?: string
  /** 主键策略（仅当 primaryKey=1 时使用）：AUTO_INCREMENT / UUID / SNOWFLAKE / BUSINESS */
  pkStrategy?: 'AUTO_INCREMENT' | 'UUID' | 'SNOWFLAKE' | 'BUSINESS'
  sortOrder: number
}

export interface LowCodeRelation {
  id?: number
  fromEntityId: number
  toEntityId: number
  relationType: 'ONE_TO_ONE' | 'ONE_TO_MANY' | 'MANY_TO_ONE' | 'MANY_TO_MANY'
  fromFieldName: string
  toFieldName?: string
  reverseName?: string
  junctionTable?: string
  onDelete: 'CASCADE' | 'SET_NULL' | 'RESTRICT'
  onUpdate: 'CASCADE' | 'RESTRICT'
}

export interface EntityDesignDTO {
  entity: LowCodeEntity
  fields: LowCodeField[]
  relations?: LowCodeRelation[]
}

export interface DdlResultDTO {
  tableName: string
  ddlStatements: string[]
  hasJunctionTable: boolean
  junctionTableDdl?: string
}

/** DDL 备份记录（与后端 DdlBackup 对应） */
export interface DdlBackup {
  id?: number
  entityId?: number
  entityCode?: string
  tableName?: string
  /** 备份类型: CREATE/ALTER/DROP_COLUMN */
  backupType?: string
  /** 备份 SQL（SHOW CREATE TABLE 结果） */
  backupSql?: string
  /** DROP COLUMN 时备份的列数据 JSON */
  backupData?: string
  createTime?: string
}

export function getEntityList() {
  return get<LowCodeEntity[]>('/api/lowcode/entity/list')
}

export function getEntityDesign(id: number) {
  return get<EntityDesignDTO>(`/api/lowcode/entity/${id}`)
}

export function saveEntityDesign(data: EntityDesignDTO) {
  return post<LowCodeEntity>('/api/lowcode/entity', data)
}

export function generateDdl(id: number) {
  return get<DdlResultDTO>(`/api/lowcode/entity/${id}/ddl`)
}

export function publishEntity(id: number, changeLog?: string) {
  return post<LowCodeEntity>(`/api/lowcode/entity/${id}/publish`, null, {
    params: { changeLog }
  })
}

export function deleteEntity(id: number) {
  return del(`/api/lowcode/entity/${id}`)
}

export function checkTableName(tableName: string, excludeId?: number) {
  return get<boolean>('/api/lowcode/entity/check-table-name', {
    tableName,
    excludeId
  })
}

/** 保存实体关联 */
export function saveRelations(entityId: number, relations: LowCodeRelation[]) {
  return post(`/api/lowcode/entity/${entityId}/relations`, relations)
}

/** 查询实体 DDL 备份记录列表（按时间倒序） */
export function listDdlBackups(entityId: number) {
  return get<DdlBackup[]>(`/api/lowcode/entity/${entityId}/ddl-backups`)
}

/** 回滚最近一次 DDL 操作，返回回滚的备份类型 */
export function rollbackLastDdl(entityId: number) {
  return post<string>(`/api/lowcode/entity/${entityId}/rollback-ddl`)
}

/** 按备份记录 ID 回滚 DDL */
export function rollbackByBackupId(backupId: number) {
  return post<void>(`/api/lowcode/entity/ddl/rollback/${backupId}`)
}

