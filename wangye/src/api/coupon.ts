import request from '@/utils/request'

export const getCouponPage = (params: any) => request({
  url: '/coupon/page',
  method: 'get',
  params
})

export const getCouponDetail = (id: number | string) => request({
  url: `/coupon/${id}`,
  method: 'get'
})

export const createCoupon = (data: any) => request({
  url: '/coupon',
  method: 'post',
  data
})

export const updateCoupon = (id: number | string, data: any) => request({
  url: `/coupon/${id}`,
  method: 'put',
  data
})

export const startCoupon = (id: number | string) => request({
  url: `/coupon/${id}/start`,
  method: 'put'
})

export const stopCoupon = (id: number | string) => request({
  url: `/coupon/${id}/stop`,
  method: 'put'
})

export const deleteCoupon = (id: number | string) => request({
  url: `/coupon/${id}`,
  method: 'delete'
})
