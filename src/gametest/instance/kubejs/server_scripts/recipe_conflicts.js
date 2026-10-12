ServerEvents.highPriorityData(event => {
    const item = (name, amount) => ({content: {
        type: 'gtceu:sized', count: amount || 1,
        ingredient: {item: 'kubejs:conflict_scan_' + name}
    }})
    const fluid = amount => ({content: {amount: amount, value: [{fluid: 'minecraft:water'}]}})
    const recipe = (name, inputs, fluids, output, type) => {
        const data = {
            type: 'gtceu:' + (type || 'assembler'), duration: 20,
            inputs: {item: inputs}, outputs: {item: [item(output || 'output')]},
            tickInputs: {eu: [{content: 32}]}
        }
        if (fluids) data.inputs.fluid = fluids
        event.addJson('gtl_gametest:recipes/conflict_scan/' + name + '.json', data)
    }
    recipe('subset', [item('a')])
    recipe('superset', [item('a'), item('b')])
    recipe('different_output', [item('a'), item('b')], null, 'other')
    recipe('different_type', [item('a')], null, null, 'mixer')
    recipe('amount_one', [item('amount')])
    recipe('amount_four', [item('amount', 4)])
    recipe('circuit_0', [item('circuit'), {
        content: {type: 'gtceu:circuit', configuration: 0}, chance: 0
    }])
    for (const circuit of [1, 2]) {
        recipe('circuit_' + circuit, [item('circuit'), {
            content: {type: 'gtceu:circuit', configuration: circuit}, chance: 0
        }])
        recipe('nbt_' + circuit, [{content: {type: 'gtceu:sized', count: 1, ingredient: {
            type: 'forge:nbt', item: 'kubejs:conflict_scan_nbt', nbt: '{scan:' + circuit + '}'
        }}}])
    }
    event.addJson('gtl_gametest:tags/items/conflict_alternatives.json', {
        replace: false, values: ['kubejs:conflict_scan_tag_a', 'kubejs:conflict_scan_tag_b']
    })
    recipe('tag', [{content: {type: 'gtceu:sized', count: 1,
        ingredient: {tag: 'gtl_gametest:conflict_alternatives'}}}])
    recipe('tag_b', [item('tag_b')])
    recipe('fluid_small', [item('fluid')], [fluid(144)])
    recipe('fluid_large', [item('fluid'), item('extra')], [fluid(288)])
    const catalyst = item('catalyst')
    catalyst.chance = 0
    recipe('needs_catalyst', [item('a'), catalyst])
})
