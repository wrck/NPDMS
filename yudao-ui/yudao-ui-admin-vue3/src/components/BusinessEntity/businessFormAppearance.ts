/** Presentation only: field permissions, values, validation and persistence remain shared. */
export interface BusinessFormAppearance {
  columns?: number
  labelPosition?: 'left' | 'right' | 'top'
  labelWidth?: string
  fields?: Record<string, {
    label?: string
    hidden?: boolean
    span?: number
    order?: number
    control?: 'editor' | 'textarea' | 'select' | 'checkbox'
    options?: Array<{ value: string | number; label: string }>
  }>
}
