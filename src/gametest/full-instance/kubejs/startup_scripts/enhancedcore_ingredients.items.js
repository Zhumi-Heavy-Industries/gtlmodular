// 夹具环境缺少的 kubejs:* 物品：我们自己转换配方要用到的无条件注册，只给增强版做启动校验的按依赖守卫。
// 前三件是 NAC 反转换配方的原料/产物（data/gtlmodular/recipes/nanochip/conversion*），与增强版无关。
StartupEvents.registry('item', event => {
    [
        'cosmic_mainframe',
        'draconic_core',
        'exotic_mainframe'
    ].forEach(name => event.create(name))
})

// 后四件只用于满足增强版设备配方的启动校验；没装增强版时补了也没人用。
if (Platform.isLoaded('gtl_enhancedcore')) {
    StartupEvents.registry('item', event => {
        [
            'containment_field_generator',
            'dimension_creation_casing',
            'precision_steam_mechanism',
            'suprachronal_mainframe_complex'
        ].forEach(name => event.create(name))
    })
}
