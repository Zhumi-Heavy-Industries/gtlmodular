ServerEvents.highPriorityData(event => {
    const product = Java.loadClass('com.gtlmodular.common.data.nanochip.NanochipModContext').modId()
    const sized = (item, count) => ({content: {type: 'gtceu:sized', count: count, ingredient: {item: item}}})
    const recipe = (name, family, amount, ticks, history) => {
        const path = history ? 'nanochip/assembly_matrix/test_tier_' : 'calibration/test_tier_'
        // 数据包路径保留完整白名单 ID，避免 GT 的脚本 builder 自动追加配方类型目录。
        event.addJson(product + ':recipes/' + path + name + '.json', {
            type: product + ':nanochip_assembly_matrix', duration: ticks,
            data: {'circuit-calibration': family.toUpperCase()},
            inputs: {item: [sized('kubejs:nac_tier_' + name + '_input', 1)]},
            outputs: {item: [sized('kubejs:nac_tier_' + family + '_output', amount)]},
            tickInputs: {eu: [{content: history ? 32 : 1048576}]}
        })
    }
    ;['primitive', 'crystal', 'wetware', 'bioware', 'optical', 'exotic', 'cosmic', 'temporal'].forEach(family => {
        const amounts = family === 'primitive' ? [12, 99, 15, 2] : [1, 2, 4, 9, 22]
        amounts.forEach(amount => recipe('history_' + family + '_' + amount, family, amount, 20, true))
        ;[21, 61].forEach(ticks => recipe('probe_' + family + '_' + ticks, family, 1, ticks, false))
    })
})
