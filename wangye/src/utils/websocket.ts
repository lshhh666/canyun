type PageLocation = Pick<Location, 'protocol' | 'host'>

export function buildWebSocketUrl(
  configuredBase: string | undefined,
  clientId: string,
  pageLocation: PageLocation = window.location
) {
  const configured = (configuredBase || '').trim()
  if (configured) {
    const base = configured.endsWith('/') ? configured : `${configured}/`
    return `${base}${clientId}`
  }

  const protocol = pageLocation.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${pageLocation.host}/ws/${clientId}`
}
