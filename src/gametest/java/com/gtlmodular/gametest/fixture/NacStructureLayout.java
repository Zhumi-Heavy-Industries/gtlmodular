package com.gtlmodular.gametest.fixture;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.server.ServerStartingEvent;

import java.util.Arrays;

/** 包围盒从真实预览计算，避免塔身变化后测试越过模板或撞到相邻用例。 */
public record NacStructureLayout(Shape host, Shape module, BlockPos hostPosition, BlockPos modulePosition,
                                 BlockPos size) {

    private static NacStructureLayout current;

    public static ResourceLocation templateId() {
        return new ResourceLocation(NanochipModContext.modId(), "nac_structure");
    }

    public static NacStructureLayout current() {
        if (current == null) throw new IllegalStateException("结构测试模板尚未准备");
        return current;
    }

    public static void prepareTemplate(ServerStartingEvent event) {
        Shape host = shape("nanochip_assembly_complex");
        Shape module = shape("nanochip_assembly_matrix");
        var slot = NanochipComplexPattern.moduleControllerOffsets().get(0);
        if (slot.front() != Direction.NORTH) throw new IllegalStateException("此 fixture 只覆盖 NORTH/UP");
        BlockPos moduleOrigin = host.controller().offset(slot.offset()).subtract(module.controller());
        BlockPos min = new BlockPos(Math.min(0, moduleOrigin.getX()), Math.min(0, moduleOrigin.getY()),
                Math.min(0, moduleOrigin.getZ()));
        BlockPos end = moduleOrigin.offset(module.size());
        BlockPos max = new BlockPos(Math.max(host.size().getX(), end.getX()),
                Math.max(host.size().getY(), end.getY()), Math.max(host.size().getZ(), end.getZ()));
        BlockPos margin = new BlockPos(2, 2, 2);
        BlockPos hostPosition = host.controller().subtract(min).offset(margin);
        BlockPos size = max.subtract(min).offset(4, 4, 4);
        current = new NacStructureLayout(host, module, hostPosition, hostPosition.offset(slot.offset()), size);

        CompoundTag template = new CompoundTag();
        template.put("size", coordinates(size));
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

    private static Shape shape(String path) {
        ResourceLocation id = new ResourceLocation(NanochipModContext.modId(), path);
        if (!(GTRegistries.MACHINES.get(id) instanceof MultiblockMachineDefinition definition)) {
            throw new IllegalStateException("缺少多方块定义：" + id);
        }
        BlockPattern pattern = definition.getPatternFactory().get();
        BlockInfo[][][] preview = pattern.getPreview(Arrays.stream(pattern.aisleRepetitions)
                .mapToInt(range -> range[0]).toArray());
        BlockPos controller = null;
        for (int x = 0; x < preview.length; x++) {
            for (int y = 0; y < preview[x].length; y++) {
                for (int z = 0; z < preview[x][y].length; z++) {
                    // GTLCore 的预览跳过 any 谓词，这些格子保留为 null。
                    if (preview[x][y][z] != null && preview[x][y][z].getBlockState().is(definition.getBlock())) {
                        if (controller != null) throw new IllegalStateException("预览有多个控制器：" + id);
                        controller = new BlockPos(x, y, z);
                    }
                }
            }
        }
        if (controller == null) throw new IllegalStateException("预览缺少控制器：" + id);
        return new Shape(definition, controller, new BlockPos(preview.length, preview[0].length,
                preview[0][0].length));
    }

    public record Shape(MultiblockMachineDefinition definition, BlockPos controller, BlockPos size) {}
}
