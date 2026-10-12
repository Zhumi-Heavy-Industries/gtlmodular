package com.gtlmodular.common.machine.nanochip.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;

/** 仅由 KubeJS 的插件发现入口加载，避免无 KubeJS 时解析可选类型。 */
public final class NanochipKubeJSPlugin extends KubeJSPlugin {

    private static final EventGroup EVENTS = EventGroup.of("NACEvents");
    private static final EventHandler REGISTER_MODULES = EVENTS.startup(
            "registerModules", () -> NanochipModuleEventJS.class);
    private boolean posted;

    @Override
    public void registerEvents() {
        EVENTS.register();
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        if (event.getType().isStartup() && posted) {
            ScriptType.STARTUP.console.warn(
                    "NACEvents.registerModules requires a full restart; startup reload cannot change NAC modules.");
        }
    }

    @Override
    public void initStartup() {
        if (posted) {
            throw new IllegalStateException("NACEvents.registerModules was already posted; restart to change modules");
        }
        posted = true;
        NanochipModuleEventJS event = new NanochipModuleEventJS();
        int errors = ScriptType.STARTUP.console.errors.size();
        try {
            var result = REGISTER_MODULES.post(ScriptType.STARTUP, null, event, (ignored, handler, error) -> {
                event.failed(error);
                return error;
            });
            // KubeJS 会记录并吞下回调异常，不能因此提交回调中只填写了一半的规格。
            if (ScriptType.STARTUP.console.errors.size() > errors || !result.pass()) {
                event.failed(new IllegalStateException("NAC module startup callback did not complete"));
            }
        } finally {
            event.close();
        }
        int count = event.submit();
        ScriptType.STARTUP.console.info("Collected " + count + " NAC script modules for the machine registration window.");
    }
}
