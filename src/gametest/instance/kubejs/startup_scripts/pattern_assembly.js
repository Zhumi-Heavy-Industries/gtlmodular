if (Platform.isLoaded('gtlmodular')) {
    StartupEvents.registry('item', event => {
        for (const name of ['common', 'extra', 'x', 'y', 'circuit']) {
            event.create('pattern_gate_' + name)
        }
    })
}
