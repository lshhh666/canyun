export function actionsForStatus(status: number): string[] {
  switch (status) {
    case 2:
      return ['查看', '接单', '拒单']
    case 3:
      return ['查看', '派送']
    case 4:
      return ['查看', '完成']
    default:
      return ['查看']
  }
}
