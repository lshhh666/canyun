const USER_COUPON_STATUS = {
  0: 'AVAILABLE',
  1: 'LOCKED',
  2: 'USED',
  3: 'EXPIRED'
}

export function normalizeCouponStatus(coupon) {
  const raw = coupon && coupon.status
  if (raw && typeof raw === 'object') {
    if (raw.name) return String(raw.name).toUpperCase()
    if (raw.value !== undefined) return USER_COUPON_STATUS[Number(raw.value)] || ''
  }
  if (typeof raw === 'number' || /^\d+$/.test(String(raw || ''))) {
    return USER_COUPON_STATUS[Number(raw)] || ''
  }
  return String(raw || '').toUpperCase()
}

export function couponTimestamp(value) {
  if (!value) return Number.NaN
  if (Array.isArray(value)) {
    const parts = value.map(Number)
    const parsed = new Date(parts[0], (parts[1] || 1) - 1, parts[2] || 1,
      parts[3] || 0, parts[4] || 0, parts[5] || 0).getTime()
    return Number.isFinite(parsed) ? parsed : Number.NaN
  }
  const parsed = Date.parse(String(value).replace(/-/g, '/').replace('T', ' '))
  return Number.isFinite(parsed) ? parsed : Number.NaN
}

export function getCouponEligibility(coupon, goodsAmount, now = Date.now()) {
  if (!coupon || normalizeCouponStatus(coupon) !== 'AVAILABLE') {
    return { eligible: false, reason: '优惠券当前不可用' }
  }

  const threshold = Number(coupon.thresholdAmount)
  const amount = Number(goodsAmount)
  if (!Number.isFinite(threshold) || !Number.isFinite(amount)) {
    return { eligible: false, reason: '优惠券金额信息异常' }
  }
  if (threshold > amount) {
    return { eligible: false, reason: `还差￥${(threshold - amount).toFixed(2)}可用` }
  }

  const start = couponTimestamp(coupon.validStartTime)
  const end = couponTimestamp(coupon.validEndTime)
  if (!Number.isFinite(start) || !Number.isFinite(end) || start >= end) {
    return { eligible: false, reason: '优惠券有效期信息异常' }
  }
  if (start > now) return { eligible: false, reason: '优惠券尚未生效' }
  if (end <= now) return { eligible: false, reason: '优惠券已过期' }
  return { eligible: true, reason: '' }
}
