/**
 * 第三方无类型声明模块的类型声明。
 */

declare module 'js-yaml' {
  /**
   * Minimal type surface for js-yaml used by OpenApiImporter.
   * Full type definitions available via `@types/js-yaml`.
   */
  export function load(input: string, options?: unknown): unknown
  export function dump(input: unknown, options?: unknown): string
  export const FAILSAFE_SCHEMA: unknown
  export const JSON_SCHEMA: unknown
  export const CORE_SCHEMA: unknown
  export const DEFAULT_SCHEMA: unknown
}
