import type { InstallationPlannedAddresses } from '@/api/pms/engineering/installation'

export const commonPlannedAddress = (plan: InstallationPlannedAddresses, productCodes: Array<string | undefined>) => {
  if (!productCodes.length || productCodes.some(code => !code?.trim())) return undefined
  const codes = [...new Set(productCodes.map(code => code!.trim()))]
  const locations = new Set<string>()
  for (const code of codes) {
    const matches = new Set(plan.addresses.filter(row => row.productCode.trim() === code)
      .map(row => row.location.trim()).filter(Boolean))
    if (matches.size !== 1) return undefined
    locations.add([...matches][0])
  }
  return locations.size === 1 ? [...locations][0] : undefined
}
