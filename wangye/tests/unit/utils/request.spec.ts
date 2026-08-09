import fs from 'fs'
import path from 'path'

describe('request state contract', () => {
  const source = fs.readFileSync(
    path.resolve(__dirname, '../../../src/utils/request.ts'),
    'utf8'
  )

  it('redirects a 401 only once and rejects the original request', () => {
    expect(source).toContain('redirectToLoginOnce()')
    expect(source).toContain('redirectingToLogin')
    expect(source).toContain("return Promise.reject(error)")
  })

  it('keeps existing page-level business-code handling', () => {
    expect(source).toContain('Business code !== 1')
    expect(source).toContain('return response')
  })

  it('cleans pending state for network failures before rejecting', () => {
    const cleanup = source.indexOf('cleanupRequest(error && error.config)')
    const rejection = source.lastIndexOf('return Promise.reject(error)')
    expect(cleanup).toBeGreaterThan(-1)
    expect(rejection).toBeGreaterThan(cleanup)
  })
})
