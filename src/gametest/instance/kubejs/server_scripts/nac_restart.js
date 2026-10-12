ServerEvents.recipes(event => {
    const product = Java.loadClass('com.gtlmodular.common.data.nanochip.NanochipModContext').modId()
    // 长批次的总耗电超过真实 LV 仓容量，才能发现错误的整批预蓄电门槛。
    ;['nanochip_assembly_complex', 'nanochip_assembly_matrix'].forEach(type => {
        event.recipes[product][type]('gtl_gametest:nac_restart')
            .itemInputs('kubejs:nac_restart_input')
            .inputFluids(Fluid.of('minecraft:water', 1000))
            .itemOutputs('kubejs:nac_restart_output')
            .outputFluids(Fluid.of('minecraft:lava', 1000))
            .EUt(32).duration(200)
    })
})
