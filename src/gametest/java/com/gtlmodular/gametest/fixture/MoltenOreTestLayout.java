package com.gtlmodular.gametest.fixture;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.server.ServerStartingEvent;

/** 炼矿模块专用空模板：40×40×40，足够容纳 15×31×11 模块和 3×3×3 测试主机。 */
public final class MoltenOreTestLayout {
    private static final BlockPos SIZE = new BlockPos(40, 40, 40);
    private static final BlockPos HOST = new BlockPos(5, 5, 5);
    private static final BlockPos MODULE = new BlockPos(10, 2, 10);

    private MoltenOreTestLayout() {
    }

    public static ResourceLocation templateId() {
        return new ResourceLocation(NanochipModContext.modId(), "molten_ore_test");
    }

    public static BlockPos hostPosition() {
        return HOST;
    }

    public static BlockPos modulePosition() {
        return MODULE;
    }

    public static void prepareTemplate(ServerStartingEvent event) {
        CompoundTag template = new CompoundTag();
        template.put("size", coordinates(SIZE));
        CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air");
        ListTag palette = new ListTag();
        palette.add(air);
        template.put("palette", palette);
        CompoundTag origin = new CompoundTag();
        origin.put("pos", coordinates(BlockPos.ZERO));
        origin.putInt("state", 0);
        ListTag blocks = new ListTag();
        blocks.add(origin);
        template.put("blocks", blocks);
        template.put("entities", new ListTag());
        event.getServer().getStructureManager().getOrCreate(templateId()).load(
                event.getServer().registryAccess().lookupOrThrow(Registries.BLOCK), template);
    }

    private static ListTag coordinates(BlockPos pos) {
        ListTag result = new ListTag();
        result.add(IntTag.valueOf(pos.getX()));
        result.add(IntTag.valueOf(pos.getY()));
        result.add(IntTag.valueOf(pos.getZ()));
        return result;
    }
}
