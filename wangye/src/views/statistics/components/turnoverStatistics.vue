<template>
  <div class="container">
    <h2 class="homeTitle">营业额统计</h2>
    <div class="charBox">
      <div id="main" style="width: 100%; height: 320px"></div>
      <ul class="orderListLine turnover">
        <li>营业额(元)</li>
      </ul>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue, Prop, Watch } from 'vue-property-decorator'
import * as echarts from 'echarts'
@Component({
  name: 'TurnoverStatistics',
})
export default class extends Vue {
  @Prop() private turnoverdata!: any
  private chart: echarts.ECharts | null = null

  mounted() {
    this.renderChart()
    window.addEventListener('resize', this.resizeChart)
  }

  beforeDestroy() {
    window.removeEventListener('resize', this.resizeChart)
    if (this.chart) {
      this.chart.dispose()
      this.chart = null
    }
  }

  @Watch('turnoverdata')
  getData() {
    this.renderChart()
  }

  private resizeChart = () => {
    if (this.chart) this.chart.resize()
  }

  private renderChart() {
    this.$nextTick(() => this.initChart())
  }

  private initChart() {
    const chartDom = document.getElementById('main') as HTMLElement | null
    if (!chartDom) return
    this.chart = this.chart || echarts.init(chartDom)

    var option: any
    option = {
      // title: {
      //   text: '营业额(元)',
      //   top: 'bottom',
      //   left: 'center',
      //   textAlign: 'center',
      //   textStyle: {
      //     fontSize: 12,
      //     fontWeight: 'normal',
      //   },
      // },
      tooltip: {
        trigger: 'axis',
      },
      grid: {
        top: '5%',
        left: '10',
        right: '50',
        bottom: '12%',
        containLabel: true,
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        axisLabel: {
          //X轴字体颜色
          textStyle: {
            color: '#666',
            fontSize: '12px',
          },
        },
        axisLine: {
          //X轴线颜色
          lineStyle: {
            color: '#DFE5EB',
            width: 1, //x轴线的宽度
          },
        },
        data: this.turnoverdata.dateList, //后端传来的动态数据
      },
      yAxis: [
        {
          type: 'value',
          min: 0,
          //max: 50000,
          //interval: 1000,
          axisLabel: {
            textStyle: {
              color: '#666',
              fontSize: '12px',
            }
            // formatter: "{value} ml",//单位
          }
        }
      ],
      series: [
        {
          name: '营业额',
          type: 'line',
          // stack: 'Total',
          smooth: false, //否平滑曲线
          showSymbol: false, //未显示鼠标上移的圆点
          symbolSize: 10,
          // symbol:"circle", //设置折线点定位实心点
          itemStyle: {
            normal: {
              color: '#147EE8',
              lineStyle: {
                color: '#147EE8',
              },
            },
            emphasis: {
              color: '#fff',
              borderWidth: 5,
              borderColor: '#2A8BED',
            },
          },

          data: this.turnoverdata.turnoverList,
        },
      ],
    }
    this.chart.setOption(option, true)
  }
}
</script>
