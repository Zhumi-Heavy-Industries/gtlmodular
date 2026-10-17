ServerEvents.highPriorityData(event => {
    const product = Java.loadClass('com.gtlmodular.common.data.nanochip.NanochipModContext').modId()
    const sized = (item, count) => ({content: {type: 'gtceu:sized', count: count, ingredient: {item: item}}})
    // 外壳档 5 远高于默认 lv 矩阵，用来验证「外壳档低于配方要求时不取这条配方」。
    event.addJson(product + ':recipes/nanochip/assembly_matrix/test_tier_gate.json', {
        type: product + ':nanochip_assembly_matrix',
        duration: 20,
        data: {'nanochip_assembly_matrix_tier': 5, 'circuit-calibration': 'CRYSTAL'},
        inputs: {item: [sized('kubejs:nac_matrix_tier_input', 1)]},
        outputs: {item: [sized('kubejs:nac_matrix_tier_output', 1)]},
        tickInputs: {eu: [{content: 32}]}
    })
})
