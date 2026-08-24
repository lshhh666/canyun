import fs from 'fs'
import path from 'path'

const projectRoot = path.resolve(__dirname, '../../..')

describe('coupon management contracts', () => {
  it('registers the coupon management route and page', () => {
    const router = fs.readFileSync(path.join(projectRoot, 'src/router.ts'), 'utf8')
    const page = fs.readFileSync(path.join(projectRoot, 'src/views/coupon/index.vue'), 'utf8')

    expect(router).toContain('path: "coupon"')
    expect(router).toContain('title: "优惠券管理"')
    expect(page).toContain('<PageHeader title="优惠券管理"')
    expect(page).toContain('草稿')
    expect(page).toContain('发放中')
    expect(page).toContain('已停用')
  })

  it('uses explicit business endpoints and state-specific actions', () => {
    const api = fs.readFileSync(path.join(projectRoot, 'src/api/coupon.ts'), 'utf8')
    const page = fs.readFileSync(path.join(projectRoot, 'src/views/coupon/index.vue'), 'utf8')

    expect(api).toContain('`/coupon/${id}/start`')
    expect(api).toContain('`/coupon/${id}/stop`')
    expect(api).toContain("method: 'delete'")
    expect(page).toContain("statusValue(scope.row.status) === 0")
    expect(page).toContain("statusValue(scope.row.status) === 1")
    expect(page).toContain('开始发放')
    expect(page).toContain('停止发放')
  })

  it('submits only administrator-owned input fields', () => {
    const page = fs.readFileSync(path.join(projectRoot, 'src/views/coupon/index.vue'), 'utf8')
    const payload = page.slice(page.indexOf('const payload = {'), page.indexOf('try {', page.indexOf('const payload = {')))

    expect(payload).toContain('totalStock')
    expect(payload).toContain('receiveStartTime')
    expect(payload).toContain('validEndTime')
    expect(payload).not.toContain('stock:')
    expect(payload).not.toContain('status:')
    expect(payload).not.toContain('createTime')
  })

  it('uses the backend LocalDateTime minute format', () => {
    const page = fs.readFileSync(path.join(projectRoot, 'src/views/coupon/index.vue'), 'utf8')

    expect(page).toContain('value-format="yyyy-MM-dd HH:mm"')
    expect(page).not.toContain('value-format="yyyy-MM-dd HH:mm:ss"')
    expect(page).toContain('formTimeText(coupon.receiveStartTime)')
  })
})
