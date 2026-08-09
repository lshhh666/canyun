<template>
  <section :class="['cm-empty-state', `cm-empty-state--${type}`]" role="status">
    <span class="cm-empty-state__icon" aria-hidden="true">
      {{ type === 'error' ? '!' : '○' }}
    </span>
    <h3>{{ title }}</h3>
    <p v-if="description">{{ description }}</p>
    <div v-if="$slots.action" class="cm-empty-state__action">
      <slot name="action" />
    </div>
  </section>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'

@Component
export default class EmptyState extends Vue {
  @Prop({ default: 'empty' }) private readonly type!: 'empty' | 'error'
  @Prop({ required: true }) private readonly title!: string
  @Prop({ default: '' }) private readonly description!: string
}
</script>

<style lang="scss" scoped>
.cm-empty-state {
  padding: 48px 20px;
  color: #7b8998;
  text-align: center;
}

.cm-empty-state__icon {
  display: block;
  margin-bottom: 12px;
  color: #9aa7b4;
  font-size: 28px;
  line-height: 32px;
}

h3 {
  margin: 0 0 6px;
  color: #34495e;
  font-size: 15px;
  font-weight: 600;
}

p {
  margin: 0;
  font-size: 13px;
  line-height: 20px;
}

.cm-empty-state__action {
  margin-top: 16px;
}

.cm-empty-state--error .cm-empty-state__icon {
  color: #d95656;
}
</style>
