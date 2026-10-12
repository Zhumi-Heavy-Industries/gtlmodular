package com.gtlmodular.common.machine;

import com.gtlmodular.GTLModular;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 物品和面板共用归档编排，避免复制、剪切和粘贴各自维护扣料与清源顺序。 */
public final class LazyPatternArchiveApi {

    public static final String MOD_ARCHIVE_KEY = LazyPatternArchiveCodec.NBT_STAMP;

    public static final int ARCHIVE_VERSION = LazyPatternArchiveCodec.VERSION;

    private LazyPatternArchiveApi() {
    }

    public static final class StampResult {
        private final boolean ok;
        private final String messageKey;
        private final Object[] args;

        StampResult(boolean ok, String messageKey, Object[] args) {
            this.ok = ok;
            this.messageKey = messageKey;
            this.args = args == null ? new Object[0] : args;
        }

        public boolean isOk() {
            return ok;
        }

        /** 仅服务端发送提示，避免单人游戏重复显示。 */
        public void tell(Player player) {
            tellKey(player, messageKey, args);
        }
    }

    public static void tellKey(Player player, String key, Object... args) {
        if (player == null || key == null) {
            return;
        }
        try {
            Level level = player.level();
            if (level != null && level.isClientSide) {
                return;
            }
            player.displayClientMessage(Component.translatable(key, args == null ? new Object[0] : args), false);
        } catch (Throwable ignored) {
            // 提示失败不影响已完成的动作。
        }
    }

    public static Object machineAt(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        try {
            return com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(level, pos);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean supports(Object machine) {
        return machine instanceof org.gtlcore.gtlcore.common.machine.multiblock.part.ae
                .MEPatternBufferPartMachineBase;
    }

    public static boolean isLazy(Object machine) {
        return machine instanceof LazyPatternAssemblyPartMachine;
    }

    public static boolean isMolten(Object machine) {
        return machine instanceof MoltenPatternAssemblyPartMachine;
    }

    private static PatternArchiveTarget toTarget(Object machine) {
        if (machine instanceof LazyPatternAssemblyPartMachine lazy) {
            return new LazyPatternArchiveTarget(lazy);
        }
        if (machine instanceof MoltenPatternAssemblyPartMachine molten) {
            return new MoltenPatternArchiveTarget(molten);
        }
        // 本模组总成也是上游具体类的子类，必须先匹配它，否则会遗漏专有设置和收尾。
        if (machine instanceof org.gtlcore.gtlcore.common.machine.multiblock.part.ae
                .MEPatternBufferPartMachine buffer) {
            return new VanillaPatternArchiveTarget(buffer);
        }
        return null;
    }

    /** 复制前预检空白样板数量，写入工具成功后再实际扣除，避免写档失败时白扣耗材。 */
    public static StampResult copyFrom(Object machine, ItemStack stack, Player player) {
        PatternArchiveTarget target = toTarget(machine);
        if (target == null) {
            return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.unsupported", null);
        }
        try {
            LazyPatternArchiveCodec.Payload payload = target.buildArchive(
                    target.includeSettingsInArchive(), target.includeNameInArchive());
            if (payload == null || payload.isEmpty()) {
                return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.source_empty", null);
            }
            int need = payload.patternCount;
            int have = countBlankPatterns(player);
            if (have < need) {
                GTLModular.LOGGER.info("[gtlmodular] 样板仓归档：复制被拒绝（空白样板不足）需要={} 身上={} pos={}",
                        need, have, target.pos());
                return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.blank_short",
                        new Object[]{need, have});
            }
            if (!LazyPatternArchiveCodec.writeToStack(stack, payload, false)) {
                return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.write_failed", null);
            }
            int consumed = extractBlankPatterns(player, need);
            if (consumed != need) {
                // 实际扣量不足必须如实报告，不能用预检数量代替实扣数量。
                GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：复制扣空白样板数量不符 需要={} 实扣={} pos={}",
                        need, consumed, target.pos());
            }
            GTLModular.LOGGER.info("[gtlmodular] 样板仓归档：复制完成 张数={} 消耗空白样板={} pos={} 玩家={}",
                    payload.patternCount, consumed, target.pos(),
                    player == null ? "-" : player.getGameProfile().getName());
            return new StampResult(true, "gtlmodular.pattern_assembly.archive.result.copied",
                    new Object[]{payload.patternCount, consumed});
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：复制失败（源机未改动）pos={} 原因={}",
                    target.pos(), failure.toString());
            return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.error", null);
        }
    }

    /** 档案写入工具成功前不能清源，否则写入失败会丢失样板。 */
    public static StampResult cutFrom(Object machine, ItemStack stack, Player player) {
        PatternArchiveTarget target = toTarget(machine);
        if (target == null) {
            return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.unsupported", null);
        }
        try {
            LazyPatternArchiveCodec.Payload payload = target.buildArchive(
                    target.includeSettingsInArchive(), target.includeNameInArchive());
            if (payload == null || payload.isEmpty()) {
                // 源仓看起来为空时，AE 仍可能持有旧样板表，因此也要触发收尾刷新。
                try {
                    target.afterBulkChange();
                } catch (Throwable failure) {
                    GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：空剪切的收尾通知失败（已忽略）pos={} 原因={}",
                            target.pos(), failure.toString());
                }
                return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.source_empty", null);
            }
            if (!LazyPatternArchiveCodec.writeToStack(stack, payload, true)) {
                return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.write_failed", null);
            }
            target.clearAll();
            try {
                target.afterBulkChange();
            } catch (Throwable failure) {
                GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：剪切后的收尾失败"
                                + "（样板已清空，缓存最迟由下一 tick 的签名轮询兜住）pos={} 原因={}",
                        target.pos(), failure.toString());
            }
            GTLModular.LOGGER.info("[gtlmodular] 样板仓归档：剪切完成 张数={} 元数据条数={} pos={}",
                    payload.patternCount, payload.entries.size(), target.pos());
            return new StampResult(true, "gtlmodular.pattern_assembly.archive.result.cut",
                    new Object[]{payload.patternCount});
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：剪切异常（已中止）pos={} 原因={}",
                    target.pos(), failure.toString());
            return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.error", null);
        }
    }

    public static StampResult pasteInto(Object machine, ItemStack stack, Player player) {
        PatternArchiveTarget target = toTarget(machine);
        if (target == null) {
            return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.unsupported", null);
        }
        try {
            LazyPatternArchiveCodec.Payload payload = LazyPatternArchiveCodec.readFromStack(stack);
            if (payload == null || payload.isEmpty()) {
                return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.empty", null);
            }
            boolean cutSourced = payload.cutSourced;
            LazyPatternArchiveCodec.ApplyResult result = LazyPatternArchiveCodec.apply(
                    target, payload, target.includeSettingsInArchive(), target.includeNameInArchive(),
                    target.placeMode());
            if (result == null) {
                return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.error", null);
            }
            if (!result.ok) {
                // 粘贴失败必须保留工具档案，避免源仓已清空后再丢失唯一副本。
                GTLModular.LOGGER.info("[gtlmodular] 样板仓归档：粘贴未生效（工具里的档案原样保留）"
                                + "剪切档案={} 结果={} pos={}", cutSourced, result.messageKey, target.pos());
                return new StampResult(false, result.messageKey, result.messageArgs);
            }
            // 剪切档案成功落地后必须消耗，否则同一批样板可被重复粘贴。
            if (cutSourced) {
                LazyPatternArchiveCodec.clearStack(stack);
                GTLModular.LOGGER.info("[gtlmodular] 样板仓归档：剪切档案已随本次粘贴消耗"
                                + "（工具已清空，不能再次粘贴）张数={} pos={}", payload.patternCount, target.pos());
                tellKey(player, "gtlmodular.pattern_assembly.archive.result.cut_consumed",
                        payload.patternCount);
            }
        // 粘贴成功后通知 AE 重读，避免网络仍显示旧样板表。
        target.afterBulkChange();
            return new StampResult(true, result.messageKey, result.messageArgs);
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：粘贴异常（已中止，目标机按操作前）pos={} 原因={}",
                    target.pos(), failure.toString());
            return new StampResult(false, "gtlmodular.pattern_assembly.archive.result.error", null);
        }
    }

    private static ItemStack blankPatternStack() {
        try {
            ItemStack stack = appeng.core.definitions.AEItems.BLANK_PATTERN.stack();
            if (stack != null && !stack.isEmpty()) {
                return stack;
            }
        } catch (Throwable ignored) {
        }
        try {
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .get(new ResourceLocation("ae2", "blank_pattern"));
            if (item != null) {
                return new ItemStack(item);
            }
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }

    public static int countBlankPatterns(Player player) {
        if (player == null) {
            return 0;
        }
        ItemStack blank = blankPatternStack();
        if (blank.isEmpty()) {
            return 0;
        }
        int count = 0;
        try {
            net.minecraftforge.items.IItemHandler handler = new net.minecraftforge.items.wrapper.PlayerInvWrapper(player.getInventory());
            int slots = handler.getSlots();
            for (int i = 0; i < slots; i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty() && stack.is(blank.getItem())) {
                    count += stack.getCount();
                }
            }
        } catch (Throwable ignored) {
            // 读不到背包时按不足处理，避免无依据地扣料。
        }
        return count;
    }

    /** 只累计 extractItem 返回的实际取出量；返回值是取出物，再写回原槽会破坏实扣结果。 */
    public static int extractBlankPatterns(Player player, int amount) {
        if (player == null || amount <= 0) {
            return 0;
        }
        ItemStack blank = blankPatternStack();
        if (blank.isEmpty()) {
            return 0;
        }
        int remaining = amount;
        int consumed = 0;
        try {
            net.minecraftforge.items.IItemHandler handler = new net.minecraftforge.items.wrapper.PlayerInvWrapper(player.getInventory());
            int slots = handler.getSlots();
            for (int i = 0; i < slots && remaining > 0; i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty() || !stack.is(blank.getItem())) {
                    continue;
                }
                int take = Math.min(remaining, stack.getCount());
                ItemStack extracted = handler.extractItem(i, take, false);
                int got = extracted == null ? 0 : extracted.getCount();
                if (got > 0) {
                    remaining -= got;
                    consumed += got;
                } else {
                    break;   // 扣不到时停止，避免空转。
                }
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：扣空白样板时出错（已扣 {} 张，其余放弃）原因={}",
                    consumed, failure.toString());
        }
        return consumed;
    }

    public static boolean hasArchive(ItemStack stack) {
        return LazyPatternArchiveCodec.hasStackArchive(stack);
    }

    /** 提示每帧读取，只取计数键以避免反复解码整份档案。 */
    public static int archivePatternCount(ItemStack stack) {
        return LazyPatternArchiveCodec.stackPatternCount(stack);
    }

    public static String archiveSource(ItemStack stack) {
        return LazyPatternArchiveCodec.stackSource(stack);
    }

    public static void clearArchive(ItemStack stack) {
        LazyPatternArchiveCodec.clearStack(stack);
    }
}
