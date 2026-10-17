ServerEvents.highPriorityData(event => {
    // 依赖 GTCEu：本夹具的配方类型与 gtceu:sized 原料都由它提供，裸环境会在数据包解析期报错；能跑这个夹具的环境里它必然在。
    if (!Platform.isLoaded('gtceu')) return
    const product = Java.loadClass('com.gtlmodular.common.data.nanochip.NanochipModContext').modId()
    // 件数表由 startup_scripts/nac_calibration.js 单点定义；两份清单手工同步的做法崩过一次测试服。
    const amounts = global.NAC_TIER_AMOUNTS
    if (!amounts) {
        throw new Error('nac_calibration_tiers: global.NAC_TIER_AMOUNTS 缺失（startup 脚本未加载或未共享）')
    }
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
        const list = family === 'primitive' ? amounts.primitive : amounts.family
        list.forEach(amount => recipe('history_' + family + '_' + amount, family, amount, 20, true))
        amounts.probes.forEach(ticks => recipe('probe_' + family + '_' + ticks, family, 1, ticks, false))
    })
})
