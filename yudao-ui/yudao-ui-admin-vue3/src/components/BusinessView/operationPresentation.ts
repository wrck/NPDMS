/** Presentation restriction only; this is neither an Owner permission nor a command permit. */
export interface PresentationObservation { status?: string; reason?: string | null }
export interface PresentationState { readonly: boolean; reason?: string }

export function operationPresentation(mode: string, value?: PresentationObservation | null): PresentationState {
  // Existing standalone and legacy views retain their own presentation contract.
  if (mode === 'INDEPENDENT' || mode === 'LEGACY') return { readonly: false }
  if (mode !== 'CONTROLLED') return { readonly: true }
  if (value?.status === 'AVAILABLE') return { readonly: false }
  return {
    readonly: true,
    reason: value?.reason || (value?.status === 'READ_ONLY' ? 'VIEW_READ_ONLY' : 'VIEW_UNAVAILABLE')
  }
}
