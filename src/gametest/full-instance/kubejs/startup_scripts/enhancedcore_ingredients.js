// Enhancedcore 启动时校验设备配方原料；这里只补精简环境缺少的物品，不参与 NAC 测量配方。
StartupEvents.registry('item', event => {
    [
        'containment_field_generator',
        'cosmic_mainframe',
        'dimension_creation_casing',
        'draconic_core',
        'exotic_mainframe',
        'precision_steam_mechanism',
        'suprachronal_mainframe_complex'
    ].forEach(name => event.create(name))
})
