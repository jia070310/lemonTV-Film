/**
 * 列优先网格（每行 cols 格）在最后一行不满时的「下移」目标下标。
 * 正下方无格时，落到下一行中与当前列最接近的一格：targetCol = min(col, 下一行最后一列)。
 */
export function gridDownNeighborIndex(
  index: number,
  total: number,
  cols: number
): number | undefined {
  if (total <= 0 || cols <= 0) return undefined

  const row = Math.floor(index / cols)
  const col = index % cols
  const belowIdeal = index + cols

  if (belowIdeal < total) {
    return belowIdeal
  }

  const nextRowStart = (row + 1) * cols
  if (nextRowStart >= total) {
    return undefined
  }

  const nextRowEnd = Math.min(nextRowStart + cols - 1, total - 1)
  const nextRowLen = nextRowEnd - nextRowStart + 1
  const targetCol = Math.min(col, nextRowLen - 1)
  return nextRowStart + targetCol
}
