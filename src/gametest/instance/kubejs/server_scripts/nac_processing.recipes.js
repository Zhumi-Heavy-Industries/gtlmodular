ServerEvents.recipes(event => {
    // 依赖 GTCEu：本夹具的配方类型与 gtceu:sized 原料都由它提供，裸环境会在数据包解析期报错；能跑这个夹具的环境里它必然在。
    if (!Platform.isLoaded('gtceu')) return
    const product = Java.loadClass('com.gtlmodular.common.data.nanochip.NanochipModContext').modId()
    // 独特原料避免精简环境中的其他配方抢走测试输入。
    event.recipes[product].nanochip_assembly_matrix('gtl_gametest:nac_processing')
        .itemInputs('kubejs:nac_processing_input')
        .inputFluids(Fluid.of('minecraft:water', 1000))
        .itemOutputs('kubejs:nac_processing_output')
        .outputFluids(Fluid.of('minecraft:lava', 1000))
        .EUt(32).duration(20)
    event.recipes[product].nanochip_assembly_matrix('gtl_gametest:nac_processing_oversized')
        .itemInputs('kubejs:nac_processing_oversized_input')
        .inputFluids(Fluid.of('minecraft:water', 1000))
        .itemOutputs('kubejs:nac_processing_output')
        .outputFluids(Fluid.of('minecraft:lava', 1000))
        .EUt(32).duration(40)
})
