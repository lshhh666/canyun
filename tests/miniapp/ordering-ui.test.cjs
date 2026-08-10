const test = require('node:test')
const { read, expectAll, expectNone } = require('./helpers.cjs')

test('ordering page keeps business handlers and uses CloudMeal shell', () => {
  const page = read('xiaochengxu-source/pages/index/index.vue')
  expectAll(page, [
    '<cloudmeal-header',
    '<app-tabbar active="order"',
    '@tap.stop="swichMenu(item, index)"',
    '@click="openDetailHandle(item)"',
    "redDishAction(item, '普通')",
    "addDishAction(item, '普通')",
    '@click="goOrder()"'
  ])
  expectNone(page, ['苍穹外卖', '月销量', '店铺已打烊', '本店已打样', 'logo_ruiji.png'])
})

test('cart and dish overlays preserve their event contracts', () => {
  const files = ['dishDetail.vue', 'popMask.vue', 'popCart.vue']
    .map(name => read(`xiaochengxu-source/pages/index/components/${name}`))
    .join('\n')
  expectAll(files, [
    '$emit("checkMoreNormPop"',
    '$emit("addShop"',
    '$emit("closeMoreNorm"',
    '$emit("clearCardOrder"',
    '$emit("addDishAction"',
    '$emit("redDishAction"',
    '$emit("moreNormDataesHandle"',
    '$emit("dishClose"'
  ])
  expectNone(files, ['#ffc200', '#FFC200', 'linear-gradient'])
})

test('ordering layout keeps browsing available while checkout follows store status', () => {
  const page = read('xiaochengxu-source/pages/index/index.vue')
  const script = read('xiaochengxu-source/pages/index/index.js')
  const styles = read('xiaochengxu-source/pages/index/style.scss')
  expectAll(page, [
    'class="menu-layout"',
    'v-if="dishListItems && dishListItems.length > 0"',
    ':disabled="orderListData().length === 0 || shopStatus !== 1"',
    "shopStatus !== 1 ? '门店休息中'",
    '<state-panel'
  ])
  expectAll(styles, [
    'width: 176rpx;',
    'background: #147ee8;',
    'bottom: calc(108rpx + env(safe-area-inset-bottom));',
    'padding-bottom: calc(228rpx + env(safe-area-inset-bottom));'
  ])
  expectAll(script, [
    'getErrorMessage(error, "菜单加载失败，请重试")',
    'title: "门店休息中，暂时无法结算"',
    'if (item && item.obj)',
    'form = item.item',
    'item = item.obj',
    'this.getDishListDataes(res.data[this.typeIndex || 0], this.typeIndex || 0)',
    'const requestId = ++this.menuRequestId',
    'if (requestId !== this.menuRequestId) return',
    'selectAll(".type_list .type_item")',
    'this.arr = rects || []'
  ])
  expectNone(page + styles + script, ['linear-gradient', '#ffc200', '#FFC200', 'selectAll(".class-item")'])
})
