import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ShopCard from './ShopCard.vue'
import type { Shop } from '@/types/api'

describe('ShopCard', () => {
  it('使用图片字段中的第一张有效图片，并以米格式显示距离', () => {
    const shop: Shop = {
      id: 12,
      name: '巷口咖啡',
      images: ' , https://cdn.example.com/first.jpg,https://cdn.example.com/second.jpg',
      score: 48,
      comments: 36,
      area: '西湖区',
      address: '文三路 88 号',
      avgPrice: 3500,
      distance: 0.85,
    }

    const wrapper = mount(ShopCard, { props: { shop } })

    expect(wrapper.get('[data-test="shop-image"]').attributes('src')).toBe('https://cdn.example.com/first.jpg')
    expect(wrapper.get('[data-test="shop-distance"]').text()).toBe('0.85km')
    expect(wrapper.get('.shop-card__score').text()).toContain('★ 4.8')
  })

  it('距离为 0 时仍显示格式化后的距离', () => {
    const wrapper = mount(ShopCard, { props: { shop: { id: 13, name: '零距离门店', distance: 0 } } })

    expect(wrapper.get('[data-test="shop-distance"]').text()).toBe('0.00km')
  })
})
