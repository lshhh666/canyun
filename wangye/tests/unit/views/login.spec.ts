import { shallowMount } from '@vue/test-utils'
import Login from '@/views/login/index.vue'
import { UserModule } from '@/store/modules/user'

jest.mock('@/store/modules/user', () => ({ UserModule: { Login: jest.fn() } }))

describe('CloudMeal login', () => {
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
