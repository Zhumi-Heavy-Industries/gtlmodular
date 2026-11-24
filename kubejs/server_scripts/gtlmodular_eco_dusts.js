// 只处理 eco（neoecoae）自己添加的铁粉/钨粉/铝粉：
// 1) 从 forge/c 粉标签里移除 ⇒ GTCEu 及其它模组按标签匹配的配方不再接受它们；
// 2) 不删物品、不删 GTCEu 的粉。
ServerEvents.tags('item', event => {
  const mats = ['iron', 'tungsten', 'aluminum', 'aluminium']
  mats.forEach(m => {
    const id = `neoecoae:${m}_dust`
    ;[
      'forge:dusts',
      'c:dusts',
      `forge:dusts/${m}`,
      `c:dusts/${m}`,
      `neoecoae:compat/gt/${m}_dusts`
    ].forEach(tag => event.remove(tag, id))
  })
  // eco 自己的 GT 兼容标签用的是 aluminium 拼写
  event.remove('neoecoae:compat/gt/aluminium_dusts', 'neoecoae:aluminum_dust')
})
