import fs from 'fs'
import path from 'path'

describe('CloudMeal dashboard contract', () => {
  const dashboard = fs.readFileSync(
    path.resolve(__dirname, '../../../src/views/dashboard/index.vue'),
    'utf8'
  )
  const overview = fs.readFileSync(
    path.resolve(__dirname, '../../../src/views/dashboard/components/overview.vue'),
    'utf8'
  )
  const orderview = fs.readFileSync(
    path.resolve(__dirname, '../../../src/views/dashboard/components/orderview.vue'),
    'utf8'
  )

  it('uses the approved real operating sections', () => {
    expect(overview).toContain('今日经营概览')
    expect(overview).toContain('实收金额')
    expect(orderview).toContain('待处理事项')
  })

  it('distinguishes loading and request errors from zero data', () => {
    expect(dashboard).toContain('data-testid="dashboard-loading"')
    expect(dashboard).toContain('v-else-if="hasError"')
    expect(dashboard).toContain('type="error"')
    expect(overview).toContain('Number(value || 0)')
  })

  it('keeps the existing dashboard API contracts', () => {
    expect(dashboard).toContain('getBusinessData()')
    expect(dashboard).toContain('getOrderData()')
    expect(dashboard).toContain('getOverviewDishes()')
    expect(dashboard).toContain('getSetMealStatistics()')
  })
})
