export interface SatisfactionViewProps {
  /** Omit for the existing standalone management page. A supplied ID locks the project. */
  projectId?: number
  /** Presentation restriction only; every request still uses the existing server authorization. */
  readonly?: boolean
}

export const satisfactionProjectContext = (projectId?: number, filter?: number) => {
  const scoped = projectId !== undefined
  const id = scoped ? projectId : (filter ?? undefined)
  return { scoped, projectId: id, valid: id === undefined || (Number.isSafeInteger(id) && id > 0) }
}
