/**
 * 响应式断点统一常量（批次4-T9）。
 *
 * <p>本文件是全应用唯一的断点阈值定义源（Single Source of Truth），消除之前
 * design-tokens.scss / form-designer / responsive.scss 三处口径不一致的问题。</p>
 *
 * <h3>阈值口径（与 Element Plus el-col 媒体查询对齐）</h3>
 * <pre>
 * | 断点 | min-width | 对应 el-col prop | 用途 |
 * |------|-----------|------------------|------|
 * | xs   | 0px       | :xs              | 手机竖屏 |
 * | sm   | 768px     | :sm              | 平板竖屏 |
 * | md   | 992px     | :md              | 平板横屏 / 小桌面 |
 * | lg   | 1200px    | :lg              | 桌面 |
 * | xl   | 1920px    | :xl              | 大桌面 |
 * </pre>
 *
 * <p>注意：Element Plus el-col 的媒体查询阈值固定为 xs:<576 / sm:≥768 / md:≥992 / lg:≥1200 / xl:≥1920，
 * 其中 sm 的 min-width 是 768（非 576），与 Bootstrap 的 576 不同。
 * 本文件遵循 Element Plus 口径，确保 design-tokens.scss 的 CSS 变量、form-designer 的预览宽度、
 * responsive.scss 的 mixin 三者完全一致。</p>
 *
 * <p>使用方式：
 * <ul>
 *   <li>SCSS：@import '@/styles/breakpoints.scss' 后使用 $bp-sm 等变量</li>
 *   <li>TS/Vue：import { BREAKPOINTS, BREAKPOINT_ORDER } from '@/styles/breakpoints'</li>
 *   <li>CSS 变量：var(--pms-bp-sm) 等（由 design-tokens.scss 导出）</li>
 * </ul>
 * </p>
 */

/** 断点 key（自小到大排序） */
export type Breakpoint = 'xs' | 'sm' | 'md' | 'lg' | 'xl'

/** 断点顺序数组（自小到大，用于遍历与继承查找） */
export const BREAKPOINT_ORDER: Breakpoint[] = ['xs', 'sm', 'md', 'lg', 'xl']

/** 各断点对应的最小屏幕宽度（px） */
export const BREAKPOINTS: Record<Breakpoint, number> = {
  xs: 0,
  sm: 768,
  md: 992,
  lg: 1200,
  xl: 1920
}

/** 各断点的预览宽度（px，用于设计器画布预览模拟，取断点的典型设备宽度） */
export const BREAKPOINT_PREVIEW_WIDTH: Record<Breakpoint, number> = {
  xs: 375, // iPhone SE
  sm: 768, // iPad 竖屏
  md: 992, // iPad 横屏
  lg: 1200, // 桌面
  xl: 1920 // 大桌面
}

/** 各断点的显示文案（含屏幕宽度范围） */
export const BREAKPOINT_LABEL: Record<Breakpoint, string> = {
  xs: 'xs (<768px) 手机',
  sm: 'sm (≥768px) 平板竖屏',
  md: 'md (≥992px) 平板横屏',
  lg: 'lg (≥1200px) 桌面',
  xl: 'xl (≥1920px) 大桌面'
}

/** 各断点的设备类型（用于多端渲染器选择渲染策略） */
export type DeviceType = 'mobile' | 'tablet' | 'desktop'
export const BREAKPOINT_DEVICE: Record<Breakpoint, DeviceType> = {
  xs: 'mobile',
  sm: 'tablet',
  md: 'tablet',
  lg: 'desktop',
  xl: 'desktop'
}

/**
 * 根据屏幕宽度判断当前所属断点。
 *
 * @param width 屏幕宽度（px）
 * @returns 断点 key
 */
export function getBreakpointByWidth(width: number): Breakpoint {
  if (width < BREAKPOINTS.sm) return 'xs'
  if (width < BREAKPOINTS.md) return 'sm'
  if (width < BREAKPOINTS.lg) return 'md'
  if (width < BREAKPOINTS.xl) return 'lg'
  return 'xl'
}

/**
 * 根据断点获取对应的设备类型。
 */
export function getDeviceByBreakpoint(bp: Breakpoint): DeviceType {
  return BREAKPOINT_DEVICE[bp]
}

/**
 * 根据屏幕宽度获取设备类型。
 */
export function getDeviceByWidth(width: number): DeviceType {
  return getDeviceByBreakpoint(getBreakpointByWidth(width))
}
