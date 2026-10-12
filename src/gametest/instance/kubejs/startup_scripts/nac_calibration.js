StartupEvents.registry('item', event => {
    ;['crystal', 'optical', 'unlisted'].forEach(name => {
        event.create('nac_calibration_' + name + '_input')
        event.create('nac_calibration_' + name + '_output')
    })
    ;['primitive', 'crystal', 'wetware', 'bioware', 'optical', 'exotic', 'cosmic', 'temporal'].forEach(family => {
        event.create('nac_tier_' + family + '_output')
        const amounts = family === 'primitive' ? [12, 99, 15, 2] : [1, 2, 4, 9, 22]
        amounts.forEach(amount => event.create('nac_tier_history_' + family + '_' + amount + '_input'))
        ;[21, 61].forEach(ticks => event.create('nac_tier_probe_' + family + '_' + ticks + '_input'))
    })
})
