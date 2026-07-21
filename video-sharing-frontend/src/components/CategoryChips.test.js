import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CategoryChips from './CategoryChips.vue'

describe('CategoryChips', () => {
  it('emits the selected category when a chip is clicked', async () => {
    const wrapper = mount(CategoryChips, {
      props: { modelValue: '全部' },
    })

    await wrapper.findAll('button').find((button) => button.text() === '游戏').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['游戏']])
  })
})
