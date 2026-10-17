ServerEvents.highPriorityData(event => {
    // 依赖 GTCEu：本夹具的配方类型与 gtceu:sized 原料都由它提供，裸环境会在数据包解析期报错；能跑这个夹具的环境里它必然在。
    if (!Platform.isLoaded('gtceu')) return
    const product = Java.loadClass('com.gtlmodular.common.data.nanochip.NanochipModContext').modId()
    const sized = (item, count) => ({content: {type: 'gtceu:sized', count: count, ingredient: {item: item}}})
    ;['crystal', 'optical'].forEach((family, index) => {
        // 白名单按 ID 识别，使用独特原料让主机两条独立批次不与矩阵测试争料。
        event.addJson(product + ':recipes/nanochip/assembly_matrix/test_persistence_host_' + family + '.json', {
            type: product + ':nanochip_assembly_complex', duration: 120 + index * 40,
            data: {'circuit-calibration': family.toUpperCase()},
            inputs: {item: [sized('kubejs:nac_persistence_host_' + family + '_input', 1)]},
            outputs: {item: [sized('kubejs:nac_calibration_' + family + '_output', 2 + index)]},
            tickInputs: {eu: [{content: 32}]}
        })
    })
})
