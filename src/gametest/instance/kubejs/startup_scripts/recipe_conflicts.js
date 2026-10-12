StartupEvents.registry('item', event => {
    for (const name of ['a', 'b', 'circuit', 'amount', 'nbt', 'tag_a', 'tag_b', 'fluid', 'extra', 'catalyst', 'output', 'other']) {
        event.create('conflict_scan_' + name)
    }
})
