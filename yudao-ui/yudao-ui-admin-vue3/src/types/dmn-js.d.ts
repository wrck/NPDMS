/** The small public dmn-js manager surface used by the optional decision-table editor. */
declare module 'dmn-js/lib/Modeler' {
  export interface DmnDecisionView {
    type: string
    element: {
      id: string
      name?: string
      decisionLogic?: {
        input?: {
          id: string
          label?: string
          inputExpression?: { text?: string; typeRef?: string }
        }[]
        output?: { id: string; name?: string; label?: string; typeRef?: string }[]
      }
    }
  }
  export default class DmnManager {
    constructor(options: { container: HTMLElement })
    importXML(xml: string): Promise<{ warnings: unknown[] }>
    saveXML(options: { format: boolean }): Promise<{ xml: string }>
    getViews(): DmnDecisionView[]
    open(view: DmnDecisionView): Promise<unknown>
    on(
      event: 'viewer.created',
      listener: (event: { viewer: { on(event: string, listener: () => void): void } }) => void
    ): void
    destroy(): void
  }
}
declare module 'dmn-js/lib/NavigatedViewer' {
  import DmnManager from 'dmn-js/lib/Modeler'
  export default class DmnViewer extends DmnManager {}
}
