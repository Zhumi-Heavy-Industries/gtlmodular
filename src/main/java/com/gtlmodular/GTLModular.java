package com.gtlmodular;

import com.gtlmodular.client.config.GTLModularConfigScreen;
import com.gtlmodular.common.compat.PatternAssemblyRecipeCacheInvalidator;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.data.GTLModularNacContext;
import com.gtlmodular.common.data.GTLModularNacSelfCheck;
import com.gtlmodular.common.event.NanochipEventWiring;
import com.gtlmodular.common.data.nanochip.NanochipRegistration;
import com.gtlmodular.common.data.nanochip.NanochipRecipeCacheHooks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(GTLModular.MOD_ID)
public final class GTLModular {

    public static final String MOD_ID = "gtlmodular";
    public static final Logger LOGGER = LoggerFactory.getLogger("GTL-Modular");

    public GTLModular() {
        GTLModularNacContext.bind();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        NanochipRecipeCacheHooks.register(new PatternAssemblyRecipeCacheInvalidator());

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GTLModularConfig.SPEC);
        com.gtlmodular.common.machine.independent.control.IndependentMachineRegistry.register(modBus);
        warmUpIndependentEligibility();

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(GTLModularConfigScreen::create)));

        GTLModularMachines.registerEventListeners(modBus);

        // 配方表必须在 GTCEu 的注册事件内创建，冻结后注册会抛异常。
        NanochipRegistration.registerRecipeTypes(modBus);

        com.gtlmodular.common.data.module.SpaceOreRecipeMirror.register(modBus);
        modBus.addListener(com.gtlmodular.common.compat.EnhcoreIvBufferRegistration::onCommonSetup);

        // Registrate 条目必须在 Forge 方块注册事件前创建，机器则等待 GTCEu 的机器注册窗口。
        GTLModularMachines.initBlocks();

        // 材料走 MaterialEvent：GTCEu 的材料初始化入口不会调用 addon 的 registerMaterials。
        NanochipRegistration.registerMaterials(modBus);

        GTLModularNacSelfCheck.register();
        com.gtlmodular.common.command.GTLModularCommands.register();
        com.gtlmodular.common.command.IndependentBatchCommands.register();

        LOGGER.info("[gtlmodular] 加载中：机器注册委托给 GTM 机器注册事件窗口。");
        NanochipEventWiring.register(modBus);
    }

    /**
     * 资格判定用到的上游逻辑类必须在这里先加载好。
     * <p>
     * 如果第一次 {@code eligible()} 发生在区块加载里（机器 onLoad → updateTickSubscription），
     * 那次类加载会落在 Mixin 的字节码变换上下文里：ASM 为了合并栈帧要解析本模组 mixin 加上的接口
     * （{@code MutableRecipeLogicInvoker}），此时 TransformerClassWriter 会抛
     * 「Cannot find class …MutableRecipeLogicInvoker」，进而 ExceptionInInitializerError → 进不了存档。
     * 在 mod 构造阶段（变换已全部就绪）预热一次即可避开这条路径。
     */
    private void warmUpIndependentEligibility() {
        try {
            LOGGER.debug("[independent] 资格判定类预热：{}",
                    com.gtlmodular.common.machine.independent.eligibility.StandardRecipeLogicClasses.ELIGIBILITY);
        } catch (RuntimeException | LinkageError failure) {
            // 预热失败只影响引擎是否接管机器，不能影响模组加载：资格判定还有一层兜底。
            LOGGER.warn("[independent] 资格判定类预热失败（引擎会退回未准入，不影响存档加载）", failure);
        }
    }
}
