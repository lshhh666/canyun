import { actionsForStatus } from '@/views/orderDetails/orderActions'

describe('order action visibility', () => {
  it('shows only actions valid for each order state', () => {
    expect(actionsForStatus(2)).toEqual(['查看', '接单', '拒单'])
    expect(actionsForStatus(3)).toEqual(['查看', '派送'])
    expect(actionsForStatus(4)).toEqual(['查看', '完成'])
    expect(actionsForStatus(5)).toEqual(['查看'])
  })
})
