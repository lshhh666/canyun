import { shallowMount } from '@vue/test-utils'
import Login from '@/views/login/index.vue'
import { UserModule } from '@/store/modules/user'
import fs from 'fs'
import path from 'path'

jest.mock('@/store/modules/user', () => ({ UserModule: { Login: jest.fn() } }))

describe('CloudMeal login', () => {
  it('uses the approved brand-blue primary action states', () => {
    const source = fs.readFileSync(
      path.resolve(__dirname, '../../../src/views/login/index.vue'),
      'utf8'
    )
    expect(source).toContain('background-color: $cm-primary !important;')
    expect(source).toContain('background-color: $cm-primary-hover !important;')
    expect(source).toContain('background-color: $cm-primary-active !important;')
  })

  it('renders the brand and preserves the login payload', async () => {
    const push = jest.fn()
    ;(UserModule.Login as jest.Mock).mockResolvedValue({ code: 1 })
    const wrapper = shallowMount(Login, {
      mocks: { $router: { push }, $route: {} },
      stubs: {
        'el-form': {
          template: '<form><slot /></form>',
          methods: { validate(callback: Function) { callback(true) } }
        },
        'el-form-item': { template: '<div><slot /></div>' },
        'el-input': true,
        'el-button': { template: '<button><slot /></button>' }
      }
    })

    expect(wrapper.text()).toContain('餐云管理平台')
    expect(wrapper.text()).toContain('登录餐云')
    ;(wrapper.vm as any).handleLogin()
    await (wrapper.vm as any).$nextTick()
    await Promise.resolve()
    expect(UserModule.Login).toHaveBeenCalledWith({ username: 'admin', password: '123456' })
    expect(push).toHaveBeenCalledWith('/')
  })
})
