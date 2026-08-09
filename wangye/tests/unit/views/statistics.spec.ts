import fs from 'fs'
import path from 'path'

describe('CloudMeal statistics states and export', () => {
  const page = fs.readFileSync(
    path.resolve(__dirname, '../../../src/views/statistics/index.vue'),
    'utf8'
  )
  const title = fs.readFileSync(
    path.resolve(__dirname, '../../../src/views/statistics/components/titleIndex.vue'),
    'utf8'
  )

  it('loads all existing reports with the selected begin and end dates', () => {
    expect(page).toContain('getTurnoverStatistics({ begin: begin,end:end })')
    expect(page).toContain('getUserStatistics({ begin: begin,end:end })')
    expect(page).toContain('getOrderStatistics({begin: begin,end:end })')
    expect(page).toContain('getTop({begin: begin,end:end })')
  })

  it('shows distinct loading, empty and error states', () => {
    expect(page).toContain('data-testid="statistics-loading"')
    expect(page).toContain('data-testid="statistics-empty"')
    expect(page).toContain('v-else-if="hasError"')
  })

  it('prevents duplicate exports and reports empty files', () => {
    expect(title).toContain('data-testid="export-report"')
    expect(title).toContain('if (this.exporting) return')
    expect(title).toContain('Number(data.size) === 0')
    expect(title).toContain("this.$message.error('报表导出失败，请稍后重试')")
  })
})
