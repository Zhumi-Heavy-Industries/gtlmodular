package com.gtlmodular.nac;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.common.data.GTCreativeModeTabs;
import com.gtlmodular.common.data.nanochip.NanochipMachines;
import com.gtlmodular.common.data.nanochip.NanochipRegistration;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;

public final class GTLNacRegistration {

    public static final GTRegistrate REGISTRATE = GTRegistrate.create(GTLNac.MOD_ID);
    public static final RegistryEntry<CreativeModeTab> CREATIVE_TAB;

    static {
        CREATIVE_TAB = REGISTRATE.defaultCreativeTab(GTLNac.MOD_ID, builder -> {
            builder.displayItems(new GTCreativeModeTabs.RegistrateDisplayItemsGenerator(GTLNac.MOD_ID, REGISTRATE));
            builder.title(REGISTRATE.addLang("itemGroup", new ResourceLocation(GTLNac.MOD_ID, "creative_tab"), "GTL NAC"));
            builder.icon(() -> NanochipMachines.NANOCHIP_ASSEMBLY_COMPLEX == null
                    ? new ItemStack(Items.COMPARATOR) : NanochipMachines.NANOCHIP_ASSEMBLY_COMPLEX.asStack());
            builder.build();
        }).register();
        // Registrate 只会将设置当前创造页之后注册的条目记入展示清单。
        REGISTRATE.creativeModeTab(() -> CREATIVE_TAB);
    }

    private GTLNacRegistration() {
    }

    public static void registerEventListeners(IEventBus modBus) {
        REGISTRATE.registerEventListeners(modBus);
        modBus.addGenericListener(MachineDefinition.class, GTLNacRegistration::onMachineRegisterEvent);
    }

    private static void onMachineRegisterEvent(GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {
        NanochipRegistration.registerMachines();
    }
}
