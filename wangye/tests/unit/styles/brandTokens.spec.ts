import fs from 'fs'
import path from 'path'

describe('CloudMeal brand tokens', () => {
  const tokens = fs.readFileSync(
    path.resolve(__dirname, '../../../src/styles/_brand-tokens.scss'),
    'utf8'
  )

  it('defines the approved C2 palette', () => {
    expect(tokens).toContain('$cm-primary: #147ee8;')
    expect(tokens).toContain('$cm-nav: #162b42;')
    expect(tokens).toContain('$cm-page-bg: #f5f7f9;')
    expect(tokens).toContain('$cm-border: #dfe5eb;')
  })

  it('defines the approved application shell sizes', () => {
    expect(tokens).toContain('$cm-sidebar-width: 208px;')
    expect(tokens).toContain('$cm-topbar-height: 56px;')
  })

  it('keeps normal surface radii within the approved range', () => {
    expect(tokens).toContain('$cm-radius-sm: 4px;')
    expect(tokens).toContain('$cm-radius-md: 6px;')
    expect(tokens).toContain('$cm-radius-lg: 8px;')
  })
})
