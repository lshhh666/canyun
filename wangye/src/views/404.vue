<template>
  <main class="cm-not-found">
    <section class="cm-not-found__card">
      <img src="@/assets/brand/cloudmeal-logo.png" alt="餐云 Logo">
      <span>404</span>
      <h1>这个页面暂时找不到</h1>
      <p>地址可能已变更，或你没有访问该页面的权限。</p>
      <router-link to="/">返回工作台</router-link>
    </section>
  </main>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'

@Component({ name: 'Page404' })
export default class extends Vue {}
</script>

<style lang="scss" scoped>
@import '@/styles/brand-tokens';

.cm-not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 32px;
  background: $cm-page-bg;
}

.cm-not-found__card {
  width: 100%;
  max-width: 480px;
  padding: 52px 40px;
  text-align: center;
  background: $cm-surface;
  border: 1px solid $cm-border;
  border-radius: $cm-radius-lg;

  img { width: 48px; height: 48px; margin-bottom: 20px; border-radius: 10px; }
  span { display: block; color: $cm-primary; font-size: 54px; font-weight: 600; line-height: 1; }
  h1 { margin: 18px 0 10px; color: $cm-text-primary; font-size: 22px; font-weight: 600; }
  p { margin: 0 0 26px; color: $cm-text-secondary; font-size: 14px; line-height: 22px; }
  a { display: inline-block; padding: 10px 20px; color: #fff; background: $cm-primary; border-radius: $cm-radius-md; }
}
</style>
