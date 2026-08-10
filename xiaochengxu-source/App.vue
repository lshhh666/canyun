<script>
export default {
  onLaunch() {},
  onShow() {},
  onHide() {}
}
</script>

<style lang="scss">
@import '@/styles/tokens.scss';

page {
  min-height: 100%;
  color: $cm-text;
  background-color: $cm-page;
  font-size: 28rpx;
  line-height: 1.8;
}

/* #ifdef H5 */
@media screen and (min-width: 768px) {
  body {
    overflow-y: scroll;
  }
}

uni-page-body {
  min-height: 100% !important;
  height: auto !important;
  background-color: $cm-page !important;
}
/* #endif */
</style>
