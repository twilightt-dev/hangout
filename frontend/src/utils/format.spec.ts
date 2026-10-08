import { describe, expect, it } from 'vitest'
import { formatDistance, formatPrice } from './format'

describe('formatPrice', () => {
  it.each([
    [1, '0.01'],
    [99, '0.99'],
    [1234, '12.34'],
    ['1234', '12.34'],
    [null, '0.00'],
    [undefined, '0.00'],
    ['not-a-price', '0.00'],
  ] as const)('formats %s cents as %s', (value, expected) => {
    expect(formatPrice(value)).toBe(expected)
  })
})

describe('formatDistance', () => {
  it.each([
    [null, ''],
    [undefined, ''],
    [0, '0.00km'],
    [0.456, '0.46km'],
    [1.234, '1.23km'],
  ] as const)('formats %s as %s', (value, expected) => {
    expect(formatDistance(value)).toBe(expected)
  })
})
