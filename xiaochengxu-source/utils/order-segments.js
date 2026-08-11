export const ORDER_SEGMENTS = {
  current: [1, 2, 3, 4, 5],
  history: [6, 7],
}

const ORDER_ACTIONS = {
  1: ['pay'],
  2: ['reminder'],
  6: ['repeat'],
  7: ['repeat'],
}

export function filterOrdersBySegment(orders, segment) {
  const statuses = ORDER_SEGMENTS[segment] || ORDER_SEGMENTS.current
  return (Array.isArray(orders) ? orders : []).filter(order => (
    statuses.includes(Number(order && order.status))
  ))
}

export function getOrderActions(status, options = {}) {
  if (Number(status) === 1 && options.timeout) return []
  return [...(ORDER_ACTIONS[Number(status)] || [])]
}
