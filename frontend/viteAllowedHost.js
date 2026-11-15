export function getAllowedDevHosts(value) {
  if (value === undefined) return []

  const host = value.trim().toLowerCase()
  const labels = host.split('.')
  if (host.length > 253 || labels.some((label) => (
    label.length > 63 || !/^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/.test(label)
  ))) {
    throw new Error('CINELODE_DEV_ALLOWED_HOST must be an exact DNS hostname.')
  }
  return [host]
}
