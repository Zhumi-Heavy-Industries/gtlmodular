// 校准档位测试用的件数与物品：**件数只在这里定义一次**，server 脚本读同一个 global 生成配方。
// 以前 startup/server 各有一份清单，改阈值时漏改一处，输入物品就不存在 ⇒ 配方解析失败 ⇒ 夹具查不到配方把整台测试服搞崩。
global.NAC_TIER_AMOUNTS = {
    primitive: [12, 99, 15, 2, 14],
    family: [1, 2, 4, 9, 22, 3, 5, 7, 14, 23, 104],
    probes: [21, 61]
}

StartupEvents.registry('item', event => {
    ;['crystal', 'optical', 'unlisted'].forEach(name => {
        event.create('nac_calibration_' + name + '_input')
        event.create('nac_calibration_' + name + '_output')
    })
    ;['primitive', 'crystal', 'wetware', 'bioware', 'optical', 'exotic', 'cosmic', 'temporal'].forEach(family => {
        event.create('nac_tier_' + family + '_output')
        const amounts = family === 'primitive' ? global.NAC_TIER_AMOUNTS.primitive : global.NAC_TIER_AMOUNTS.family
        amounts.forEach(amount => event.create('nac_tier_history_' + family + '_' + amount + '_input'))
        global.NAC_TIER_AMOUNTS.probes.forEach(ticks => event.create('nac_tier_probe_' + family + '_' + ticks + '_input'))
    })
})
