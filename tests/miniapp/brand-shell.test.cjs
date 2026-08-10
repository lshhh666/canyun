const test = require('node:test')
const { fs, path, repoRoot, read, expectAll, expectNone } = require('./helpers.cjs')

test('brand uses the approved logo and blue tokens', () => {
  assertLogoMatchesWebsite()
  const tokens = read('xiaochengxu-source/styles/tokens.scss')
  expectAll(tokens, ['$cm-primary: #147ee8;', '$cm-radius-md: 16rpx;', '$cm-page: #f5f7fa;'])
  expectNone(tokens, ['linear-gradient', 'glass', '#ffc200'])
})

function assertLogoMatchesWebsite() {
  const source = fs.readFileSync(path.join(repoRoot, 'xiaochengxu-source/static/brand/cloudmeal-logo.png'))
  const website = fs.readFileSync(path.join(repoRoot, 'wangye/src/assets/brand/cloudmeal-logo.png'))
  require('node:assert/strict').deepEqual(source, website)
}

test('shell exposes exactly three primary destinations', () => {
  const tabbar = read('xiaochengxu-source/components/app-tabbar/app-tabbar.vue')
  expectAll(tabbar, ['点餐', '订单', '我的', "active: 'order'", "active: 'orders'", "active: 'account'"])
  expectNone(tabbar, ['优惠券', '积分', '会员'])
})

test('manifest and pages are branded as CloudMeal', () => {
  const manifest = read('xiaochengxu-source/manifest.json')
  const pages = read('xiaochengxu-source/pages.json')
  expectAll(manifest, ['餐云', 'wx718a307127ebbc96'])
  expectAll(pages, ['餐云', '#147EE8'])
  expectNone(manifest + pages, ['苍穹外卖', 'sky-take-out-user-mp', '#ffc200', '#FFC200'])
})
