StartupEvents.registry('item', event => {
    event.create('nac_processing_input')
    event.create('nac_processing_oversized_input')
    event.create('nac_processing_output')
})
