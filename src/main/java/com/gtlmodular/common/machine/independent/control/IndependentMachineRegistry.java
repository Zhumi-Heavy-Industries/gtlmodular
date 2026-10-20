package com.gtlmodular.common.machine.independent.control;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtlmodular.common.GameTestHooks;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.machine.independent.eligibility.StandardRecipeLogicClasses;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public final class IndependentMachineRegistry {
    private static final LoadedMachineIndex<RecipeLogic> LOADED = new LoadedMachineIndex<>();
    private static final IndependentSettingsReload SETTINGS = new IndependentSettingsReload();

    private IndependentMachineRegistry() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(IndependentMachineRegistry::configChanged);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, IndependentMachineRegistry::serverTick);
        MinecraftForge.EVENT_BUS.addListener(IndependentMachineRegistry::serverStopped);
    }

    private static void configChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() == GTLModularConfig.SPEC) {
            SETTINGS.request();
        }
    }

    private static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START
                || !SETTINGS.publishPending(GTLModularConfig.OTHER_MACHINE_THREADS::snapshot)) return;
        // 配置监听可能来自文件监控线程，机器和订阅只能在服务器线程修改。
        LOADED.visit(logic -> {
            if (logic.getMachine().isInValid()) return;
            var controller = IndependentLogicAccess.controller(logic);
            if (controller != null) controller.configurationChanged();
            if (eligible(logic) || controller != null && controller.ownsWork()) logic.updateTickSubscription();
        });
    }

    private static void serverStopped(ServerStoppedEvent event) {
        LOADED.clear();
        SETTINGS.serverStopped();
    }

    public static void loaded(RecipeLogic logic) {
        // 关态只建立弱索引；否则空闲退订机器无法被配置热更新唤醒。
        if (!logic.getMachine().isRemote()) LOADED.add(logic);
    }

    public static void unloaded(RecipeLogic logic) {
        if (!logic.getMachine().isRemote()) LOADED.remove(logic);
    }

    public static boolean eligible(RecipeLogic logic) {
        if (!logic.getMachine().isRemote()
                && GameTestHooks.isOreModule(logic.getMachine().getDefinition().getId().toString())) {
            // GameTest 对炼矿模块及其真实外壳测试机打开引擎，不污染其他机器。
            return true;
        }
        var settings = SETTINGS.current();
        if (!settings.enabled() || logic.getMachine().isRemote()) return false;
        return StandardRecipeLogicClasses.ELIGIBILITY.evaluate(settings,
                logic.getMachine().getDefinition().getId().toString(), logic.getClass(), false).eligibleForNewBatches();
    }

}
