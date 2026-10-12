StartupEvents.registry('item', event => {
    ;['crystal', 'optical'].forEach(family => event.create('nac_persistence_host_' + family + '_input'))
})
