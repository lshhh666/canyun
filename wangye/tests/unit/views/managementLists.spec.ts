import fs from 'fs'
import path from 'path'

const viewsRoot = path.resolve(__dirname, '../../../src/views')

function read(relativePath: string) {
  return fs.readFileSync(path.join(viewsRoot, relativePath), 'utf8')
}

describe('CloudMeal management page structure', () => {
  it.each([
    ['category/index.vue', '分类管理'],
    ['employee/index.vue', '员工管理'],
    ['dish/index.vue', '菜品管理'],
    ['setmeal/index.vue', '套餐管理'],
  ])('%s uses the shared list archetype', (file, title) => {
    const source = read(file)
    expect(source).toContain('class="cm-page dashboard-container management-list"')
    expect(source).toContain(`<PageHeader title="${title}"`)
    expect(source).toContain('cm-filter-bar')
    expect(source).toContain('<el-table')
    expect(source).toContain('<EmptyState')
  })

  it.each([
    'employee/addEmployee.vue',
    'dish/addDishtype.vue',
    'setmeal/addSetmeal.vue',
  ])('%s preserves validation and uses the shared form archetype', (file) => {
    const source = read(file)
    expect(source).toContain('management-form')
    expect(source).toContain('<PageHeader')
    expect(source).toContain(':rules="rules"')
    expect(source).toContain('submitForm')
    expect(source).toContain('取消')
    expect(source).toContain('保存')
  })
})
