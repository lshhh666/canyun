<template>
  <main class="cm-login">
    <section class="cm-login__brand">
      <div class="cm-login__identity">
        <img src="@/assets/brand/cloudmeal-logo.png" alt="餐云 Logo" />
        <strong>餐云管理平台</strong>
      </div>
      <div class="cm-login__promise">
        <span>CloudMeal · 餐饮经营管理</span>
        <h1>让门店经营更清晰</h1>
        <p>订单、商品与经营数据集中管理，为每一次出餐提供可靠支持。</p>
      </div>
      <p class="cm-login__copyright">餐云 CloudMeal</p>
    </section>

    <section class="cm-login__panel">
      <div class="cm-login__form-wrap">
        <header>
          <h2>登录餐云</h2>
          <p>使用门店管理员账号继续</p>
        </header>
        <el-form ref="loginForm" :model="loginForm" :rules="loginRules">
          <label class="cm-login__label">账号</label>
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              type="text"
              auto-complete="off"
              placeholder="请输入账号"
              prefix-icon="iconfont icon-user"
            />
          </el-form-item>
          <label class="cm-login__label">密码</label>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              prefix-icon="iconfont icon-lock"
              @keyup.enter.native="handleLogin"
            />
          </el-form-item>
          <el-form-item class="cm-login__submit">
            <el-button
              :loading="loading"
              class="cm-login__button"
              size="medium"
              type="primary"
              style="width: 100%"
              @click.native.prevent="handleLogin"
            >
              <span v-if="!loading">登录</span>
              <span v-else>登录中...</span>
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </section>
  </main>
</template>

<script lang="ts">
import { Component, Vue, Watch } from 'vue-property-decorator'
import { Route } from 'vue-router'
import { Form as ElForm } from 'element-ui'
import { UserModule } from '@/store/modules/user'

@Component({
  name: 'Login',
})
export default class extends Vue {
  private validateUsername = (rule: any, value: string, callback: Function) => {
    if (!value) {
      callback(new Error('请输入用户名'))
    } else {
      callback()
    }
  }
  private validatePassword = (rule: any, value: string, callback: Function) => {
    if (value.length < 6) {
      callback(new Error('密码必须在6位以上'))
    } else {
      callback()
    }
  }
  private loginForm = {
    username: 'admin',
    password: '123456',
  } as {
    username: String
    password: String
  }

  loginRules = {
    username: [{ validator: this.validateUsername, trigger: 'blur' }],
    password: [{ validator: this.validatePassword, trigger: 'blur' }],
  }
  private loading = false
  private redirect?: string

  @Watch('$route', { immediate: true })
  private onRouteChange(route: Route) {}

  // 登录
  private handleLogin() {
    ;(this.$refs.loginForm as ElForm).validate(async (valid: boolean) => {
      if (valid) {
        this.loading = true
        await UserModule.Login(this.loginForm as any)
          .then((res: any) => {
            if (String(res.code) === '1') {
              this.$router.push('/')
            } else {
              // this.$message.error(res.msg)
              this.loading = false
            }
          })
          .catch(() => {
            // this.$message.error('用户名或密码错误！')
            this.loading = false
          })
      } else {
        return false
      }
    })
  }
}
</script>

<style lang="scss">
@import '@/styles/brand-tokens';

.cm-login {
  display: grid;
  grid-template-columns: minmax(480px, 1.08fr) minmax(440px, 0.92fr);
  min-height: 100%;
  background: $cm-surface;
}

.cm-login__brand {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 100vh;
  padding: 44px 56px 40px;
  overflow: hidden;
  color: #ffffff;
  background: $cm-nav;

  &::before,
  &::after {
    position: absolute;
    content: '';
    border: 1px solid rgba(75, 157, 232, 0.18);
    border-radius: 50%;
  }

  &::before { width: 420px; height: 420px; right: -190px; top: 8%; }
  &::after { width: 620px; height: 620px; left: -390px; bottom: -330px; }
}

.cm-login__identity,
.cm-login__promise,
.cm-login__copyright { position: relative; z-index: 1; }

.cm-login__identity {
  display: flex;
  align-items: center;
  gap: 12px;

  img { width: 42px; height: 42px; border-radius: 10px; }
  strong { font-size: 18px; font-weight: 600; letter-spacing: 0.5px; }
}

.cm-login__promise {
  max-width: 520px;
  margin-bottom: 10vh;

  span { color: #79b8ef; font-size: 13px; letter-spacing: 1.2px; }
  h1 { margin: 18px 0 16px; font-size: 38px; line-height: 1.3; font-weight: 600; }
  p { margin: 0; color: #b9c9d9; font-size: 15px; line-height: 1.9; }
}

.cm-login__copyright { margin: 0; color: #758ba1; font-size: 12px; }

.cm-login__panel {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 48px;
  background: #f8fafc;
}

.cm-login__form-wrap {
  width: 100%;
  max-width: 380px;
  padding: 36px 40px 40px;
  background: $cm-surface;
  border: 1px solid $cm-border;
  border-radius: $cm-radius-lg;

  header { margin-bottom: 30px; }
  h2 { margin: 0 0 8px; color: $cm-text-primary; font-size: 24px; font-weight: 600; }
  header p { margin: 0; color: $cm-text-secondary; font-size: 13px; }
  .el-form-item { margin-bottom: 22px; }
  .el-input__inner { height: 42px; padding-left: 38px; color: $cm-text-primary; }
  .el-input__icon { line-height: 42px; }
}

.cm-login__label {
  display: block;
  margin-bottom: 8px;
  color: $cm-text-regular;
  font-size: 13px;
}

.cm-login__submit { margin: 30px 0 0 !important; }
.cm-login__button { height: 42px; border-radius: $cm-radius-md; font-size: 14px; }

@media (max-width: 960px) {
  .cm-login { grid-template-columns: 1fr; }
  .cm-login__brand { display: none; }
  .cm-login__panel { padding: 24px; }
}
</style>
