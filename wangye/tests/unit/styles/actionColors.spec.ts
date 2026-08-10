import fs from 'fs'
import path from 'path'

describe('CloudMeal action hierarchy', () => {
  const styles = fs.readFileSync(
    path.resolve(__dirname, '../../../src/styles/_cloudmeal-components.scss'),
    'utf8'
  )
  const pages = ['setmeal', 'dish', 'category', 'employee'].map((name) =>
    fs.readFileSync(path.resolve(__dirname, `../../../src/views/${name}/index.vue`), 'utf8')
  )
  const title = fs.readFileSync(
    path.resolve(__dirname, '../../../src/views/statistics/components/titleIndex.vue'),
    'utf8'
  )

  it('defines primary and outlined query actions', () => {
    expect(styles).toContain('.cm-primary-action')
    expect(styles).toContain('.cm-query-action')
    expect(styles).toContain('background-color: $cm-primary !important;')
    expect(styles).toContain('color: $cm-primary !important;')
  })

  it('marks all visible actions semantically', () => {
    pages.forEach((source) => expect(source).toContain('cm-query-action'))
    pages.forEach((source) => expect(source).toContain('cm-primary-action'))
    expect(title).toContain('cm-primary-action')
  })

  it('keeps the selected date tab blue', () => {
    expect(styles).toContain('.statistics-page .li-tab.active')
    expect(styles).toContain('background: #e8f3fc !important;')
  })
})
