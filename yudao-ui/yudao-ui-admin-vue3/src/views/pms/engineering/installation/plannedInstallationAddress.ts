import type { InstallationPlannedAddresses } from '@/api/pms/engineering/installation'

export const plannedAddressCandidates = (plan: InstallationPlannedAddresses, productCodes: Array<string | undefined>) => {
  const all = [...new Set(plan.addresses.map(row => row.location.trim()).filter(Boolean))]
  if (all.length <= 1) return all
  if (!productCodes.length || productCodes.some(code => !code?.trim())) return []
  const codes = [...new Set(productCodes.map(code => code!.trim()))]
  let candidates: Set<string> | undefined
  for (const code of codes) {
    const matches = new Set(plan.addresses.filter(row => row.productCode.trim() === code)
      .map(row => row.location.trim()).filter(Boolean))
    candidates = candidates ? new Set([...candidates].filter(location => matches.has(location))) : matches
  }
  return [...(candidates || [])]
}

export const commonPlannedAddress = (plan: InstallationPlannedAddresses, productCodes: Array<string | undefined>) => {
  const candidates = plannedAddressCandidates(plan, productCodes)
  return candidates.length === 1 ? candidates[0] : undefined
}
