if (Platform.isLoaded('gtlmodular')) {
    ServerEvents.recipes(event => {
        const recipe = (type, name, output, extra, circuit) => {
            const builder = event.recipes.gtceu[type]('gtl_gametest:pattern_gate_' + name)
                .itemInputs('kubejs:pattern_gate_common')
                .inputFluids(Fluid.of('minecraft:water', 144))
                .itemOutputs((circuit === 2 ? '2x ' : '') + 'kubejs:pattern_gate_' + output)
                .EUt(32).duration(80)
            if (extra) builder.itemInputs('kubejs:pattern_gate_extra')
            if (circuit) builder.circuit(circuit)
        }
        // X 可以吃掉 Y 的全部公共输入，必须由样板身份而非原料是否足够排除它。
        recipe('assembler', 'x', 'x', false, 0)
        recipe('assembler', 'y', 'y', true, 0)
        // 产物种类相同，身份闸忽略数量；只有电路能区分，数量让两张 AE 样板保持不同。
        recipe('assembler', 'c1', 'circuit', true, 1)
        recipe('assembler', 'c2', 'circuit', true, 2)
        // 与 X 身份相同但类型不同，用真实 handler 的模拟扣料单独检验类型闸。
        recipe('mixer', 'foreign', 'x', false, 0)
    })
}
