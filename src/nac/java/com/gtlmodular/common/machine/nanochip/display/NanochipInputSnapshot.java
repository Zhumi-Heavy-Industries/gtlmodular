package com.gtlmodular.common.machine.nanochip.display;

import net.minecraft.network.chat.Component;

import java.util.List;

/** 只读传输视图不持有库存、handler 或可供扣料的配方引用。 */
public record NanochipInputSnapshot(long optionsTick, boolean fanOut, boolean perSlot, Sample sample) {
    public record Material(Component name, boolean fluid, long perCraft, Long first, Long aggregate) {
        public Material {
            name = name.copy();
        }

        @Override
        public Component name() {
            return name.copy();
        }
    }

    public record Sample(String recipeId, long tick, long recipeTick, int stockSlots, int probeSlots,
                         Long firstParallel, Long bestParallel, boolean complete, List<Material> materials) {
        public Sample {
            materials = List.copyOf(materials);
        }
    }
}
