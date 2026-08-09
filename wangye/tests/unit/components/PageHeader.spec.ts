import { shallowMount } from '@vue/test-utils'
import PageHeader from '@/components/PageHeader/index.vue'

describe('PageHeader', () => {
  it('renders title, description and actions', () => {
    const wrapper = shallowMount(PageHeader, {
      propsData: { title: '订单管理', description: '查看并处理门店订单' },
      slots: { actions: '<button>导出订单</button>' }
    })

    expect(wrapper.find('h1').text()).toBe('订单管理')
    expect(wrapper.text()).toContain('查看并处理门店订单')
    expect(wrapper.text()).toContain('导出订单')
  })
})
