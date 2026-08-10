<template>
  <div class="title-index">
    <div class="month">
      <ul class="tabs">
        <li
          class="li-tab"
          v-for="(item, index) in tabsParam"
          @click="toggleTabs(index)"
          :class="{ active: index === nowIndex }"
          :key="index"
        >
          {{ item }}
          <span></span>
        </li>
      </ul>
    </div>
    <div class="get-time">
      <p>
        已选时间：{{ tateData[0] }} 至
        {{ tateData[tateData.length - 1] }}
      </p>
    </div>
    <el-button
      data-testid="export-report"
      icon="iconfont icon-download"
      class="right-el-button cm-primary-action"
      type="primary"
      :loading="exporting"
      :disabled="exporting"
      @click="handleExport"
      >{{ exporting ? '正在导出' : '导出报表' }}</el-button
    >
  </div>
</template>

<script lang="ts">
import { Component, Vue, Prop, Watch } from 'vue-property-decorator'
import { exportInfor } from '@/api/index'
@Component({
  name: 'TitleIndex',
})
export default class extends Vue {
  @Prop() private flag!: any
  @Prop() private tateData!: any
  @Prop() private turnoverData!: any

  nowIndex = 2 - 1
  value = []
  tabsParam = ['昨日', '近7日', '近30日', '本周', '本月']
  private exporting = false
  @Watch('flag')
  getNowIndex(val) {
    this.nowIndex = val
  }
  // tab切换
  toggleTabs(index: number) {
    this.nowIndex = index
    this.value = []
    this.$emit('sendTitleInd', index + 1)
  }
  //  数据导出
  /** 导出按钮操作 */
  async handleExport() {
    if (this.exporting) return
    this.exporting = true
    try {
      await this.$confirm('是否确认导出最近30天运营数据?', '导出经营报表', {
        confirmButtonText: '确定导出',
        cancelButtonText: '取消',
        type: 'warning',
      })
      const { data } = await exportInfor()
      if (!data || Number(data.size) === 0) {
        throw new Error('导出文件为空')
      }
      const url = window.URL.createObjectURL(data)
      const link = document.createElement('a')
      document.body.appendChild(link)
      link.href = url
      link.download = '餐云经营数据统计报表.xlsx'
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      this.$message.success('报表已导出')
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        this.$message.error('报表导出失败，请稍后重试')
      }
    } finally {
      this.exporting = false
    }
  }
}
</script>

<style lang="scss" scoped>
.title-index {
  display: flex;
  align-items: center;
  gap: $cm-space-4;
  padding: $cm-space-3 $cm-space-4;
  margin-bottom: $cm-space-4;
  background: $cm-surface;
  border: 1px solid $cm-border;
  border-radius: $cm-radius-lg;
}

.tabs { display: flex; margin: 0; padding: 0; list-style: none; }
.li-tab { padding: 7px 14px; color: $cm-text-regular; border-radius: $cm-radius-sm; cursor: pointer; }
.li-tab.active { color: $cm-primary; background: #e8f3fc; }
.get-time { flex: 1; color: $cm-text-secondary; font-size: 13px; }
.get-time p { margin: 0; }
</style>
