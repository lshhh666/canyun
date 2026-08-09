import { shallowMount } from '@vue/test-utils'
import StatusTag from '@/components/StatusTag/index.vue'

describe('StatusTag', () => {
  it.each([
    ['success', '营业中'],
    ['warning', '待接单'],
    ['danger', '已取消'],
    ['info', '派送中'],
    ['neutral', '已停售']
  ])('renders %s semantics', (status, text) => {
    const wrapper = shallowMount(StatusTag, { propsData: { status, text } })

    expect(wrapper.classes()).toContain(`cm-status--${status}`)
    expect(wrapper.text()).toBe(text)
  })
})
