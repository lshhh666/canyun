<template>
  <header class="cm-page-header">
    <div class="cm-page-header__copy">
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="cm-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'

@Component
export default class PageHeader extends Vue {
  @Prop({ required: true }) private readonly title!: string
  @Prop({ default: '' }) private readonly description!: string
}
</script>

<style lang="scss" scoped>
.cm-page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: $cm-space-4;
  margin-bottom: $cm-space-4;
}

.cm-page-header__copy {
  min-width: 0;
}

h1 {
  margin: 0 0 $cm-space-1;
  color: $cm-text-primary;
  font-size: 20px;
  font-weight: 600;
  line-height: 28px;
}

p {
  margin: 0;
  color: $cm-text-secondary;
  font-size: 13px;
  line-height: 20px;
}

.cm-page-header__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: $cm-space-2;
}
</style>
