package com.gtlmodular.client.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gtlmodular.common.machine.display.ThreadDisplaySnapshot;
import com.gtlmodular.common.machine.display.ThreadDisplayText;
import com.gtlmodular.common.machine.independent.control.IndependentLogicAccess;
import com.gtlmodular.common.machine.independent.control.IndependentMachineController;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 通用独立线程引擎的 Jade 显示。排版、语言键、进度条颜色全部照 NAC 的
 * NanochipJadeDisplay / NanochipJadeThreadRows 复制，只把数据源换成引擎批次表。
 */
@WailaPlugin
public final class OtherMachineThreadsJadePlugin implements IWailaPlugin {
    private static final String DATA = "gtlmodular_independent_display";
    private static final ResourceLocation UID = new ResourceLocation("gtlmodular", "independent_threads");

    private static final String DIVIDER = "=========================";
    private static final int BAR_WORKING = 0xFF4CBB17;
    private static final int BAR_WAITING = 0xFFD9A441;
    private static final int BAR_TEXT = 0xFFFFFFFF;
    private static final int DISPLAY_LINES = 8;
    private static final int OUTPUT_LINES = 8;

    @Override public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(Provider.INSTANCE, MetaMachineBlockEntity.class);
    }

    @Override public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Provider.INSTANCE, Block.class);
        registration.addTooltipCollectedCallback((tooltip, accessor) -> {
            if (accessor instanceof BlockAccessor block && block.getServerData().getCompound(DATA).getBoolean("owned")) {
                tooltip.remove(new ResourceLocation("gtceu", "workable_provider"));
                tooltip.remove(new ResourceLocation("gtlcore", "recipe_multiplier_provider"));
            }
        });
    }

    public enum Provider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override public ResourceLocation getUid() { return UID; }

        /** 只发数字和少量字符串，不再序列化组件；这样每次刷新都能拿到最新进度条，成本仍然是微秒级。 */
        @Override public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity holder)) return;
            var controller = IndependentLogicAccess.controller(holder.getMetaMachine());
            if (controller == null) return;
            var data = build(controller);
            if (data != null) tag.put(DATA, data);
        }

        private static CompoundTag build(IndependentMachineController controller) {
            var lines = controller.threads();
            if (lines.isEmpty() && !controller.ownsWork()) return null;
            CompoundTag data = new CompoundTag();
            data.putBoolean("owned", controller.ownsWork());
            data.putInt("limit", controller.limit());
            data.putInt("running", controller.batchCount());
            long runs = controller.totalRuns();
            var distinct = new HashSet<ResourceLocation>();
            ListTag threads = new ListTag();
            for (var line : lines) {
                if (line.recipeId() != null) distinct.add(line.recipeId());
                CompoundTag row = new CompoundTag();
                row.putInt("label", line.label());
                row.putLong("eu", line.euPerTick());
                row.putInt("duration", line.duration());
                row.putInt("progress", line.progress());
                row.putLong("parallels", line.parallels());
                row.putInt("runs", line.runs());
                row.putBoolean("working", line.waitReason() == null);
                if (line.waitReason() != null) row.putString("reason", reasonKey(line.waitReason()));
                if (line.outputId() != null) {
                    row.putString("outputId", line.outputId());
                    row.putLong("outputCount", line.outputCount());
                }
                threads.add(row);
            }
            data.putLong("totalRuns", runs);
            data.putInt("distinct", distinct.size());
            data.put("threads", threads);
            // 地心模块的线圈减免：和 NAC 的「校准折减」同一位置、同一套百分比写法。
            try {
                if (viewMachine(controller) instanceof com.gtlmodular.common.machine.MoltenModuleMachineBase<?> module) {
                    data.putDouble("euMul", module.independentEnergyMultiplier());
                    data.putDouble("durMul", com.gtlmodular.common.config.GTLModularConfig.getCoilDiscountDurationMultiplier());
                }
            } catch (RuntimeException ignored) {
            }
            return data;
        }

        /** 显示层只认语言键：把引擎的中文等待原因映射到 NAC 同一套键。 */
        private static String reasonKey(String reason) {
            if (reason == null) return null;
            if (reason.contains("蓄电")) return reason;
            if (reason.contains("暂停")) return "gtlmodular.nanochip.thread_details.suspended";
            if (reason.contains("结构")) return "gtlmodular.nanochip.thread_details.unavailable";
            if (reason.contains("燃料")) return "gtlmodular.nanochip.thread_details.machine_blocked";
            return "gtlmodular.nanochip.thread_details.blocked";
        }

        private static Object viewMachine(IndependentMachineController controller) {
            return controller.machine();
        }

        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData().getCompound(DATA);
            if (data.isEmpty()) return;
            ThreadDisplaySnapshot snapshot = read(data);
            ThreadDisplayText.summary(snapshot).forEach(tooltip::add);
            // 主提示只放两行汇总；线程明细挪到「按住 Shift」的详情里，避免一屏线程把主显示顶掉。
            boolean details = accessor.getPlayer() != null && accessor.getPlayer().isShiftKeyDown();
            if (!details) {
                if (snapshot.running() != null && snapshot.running() > 0) {
                    tooltip.add(Component.translatable("gtlmodular.independent.jade.details_hint"));
                }
                return;
            }
            tooltip.add(Component.translatable("gtlmodular.nanochip.jade.distinct_recipes",
                    ThreadDisplayText.number(snapshot.running()),
                    ThreadDisplayText.number(snapshot.distinctRecipes())));
            if (snapshot.running() == null || snapshot.running() <= 0) return;

            tooltip.add(Component.literal(DIVIDER));
            int shown = 0;
            ListTag runRows = data.getList("threads", Tag.TAG_COMPOUND);
            for (ThreadDisplaySnapshot.Thread thread : snapshot.threads()) {
                if (shown >= DISPLAY_LINES) break;
                ThreadDisplayText.lines(thread).forEach(tooltip::add);
                appendRuns(tooltip, runRows, shown, thread);
                appendBar(tooltip, thread);
                shown++;
            }
            if (snapshot.totalRuns() != null) {
                tooltip.add(Component.translatable("gtlmodular.nanochip.threads.machine_equiv",
                        snapshot.running(), snapshot.running(),
                        FormattingUtil.formatNumbers(snapshot.totalRuns())));
            }
            if (shown < snapshot.running()) {
                tooltip.add(ThreadDisplayText.more(snapshot.running() - shown));
            }

            tooltip.add(Component.literal(DIVIDER));
            tooltip.add(Component.translatable("gtlmodular.nanochip.jade.thread_outputs"));
            int listed = 0;
            for (ThreadDisplaySnapshot.Thread thread : snapshot.threads()) {
                if (listed >= OUTPUT_LINES) break;
                Component label = ThreadDisplayText.number(thread.label());
                if (listed > 0) tooltip.add(Component.literal(DIVIDER));   // 线程之间用分割线隔开
                if (thread.outputId() == null) {
                    tooltip.add(Component.translatable("gtlmodular.nanochip.jade.thread_outputs.none", label));
                } else {
                    String encoded = thread.outputId();
                    boolean any = false;
                    for (String part : encoded.split(";")) {
                        String[] bits = part.split(":");
                        if (bits.length < 3) continue;
                        boolean fluid = "fluid".equals(bits[0]);
                        String id = bits[1];
                        long amount;
                        try {
                            amount = Long.parseLong(bits[2]);
                        } catch (NumberFormatException ignored) {
                            continue;
                        }
                        IElement icon = fluid ? fluidIconOf(tooltip, id, amount) : iconOf(tooltip, id);
                        Component line = Component.translatable("gtlmodular.nanochip.jade.thread_outputs.line",
                                label, ThreadDisplayText.number(amount), displayNameOf(fluid ? "fluid:" + id : id));
                        if (icon != null) {
                            tooltip.add(icon);
                            tooltip.append(line);
                        } else {
                            tooltip.add(line);
                        }
                        any = true;
                    }
                    if (!any) {
                        tooltip.add(Component.translatable("gtlmodular.nanochip.jade.thread_outputs.none", label));
                    }
                }
                listed++;
            }
            if (snapshot.running() != null && listed < snapshot.running()) {
                tooltip.add(ThreadDisplayText.more(snapshot.running() - listed));
            }
            if (data.contains("euMul") && data.contains("durMul")) {
                tooltip.add(Component.translatable("gtlmodular.independent.jade.coil_reduction",
                        reduction(data.getDouble("euMul")), reduction(data.getDouble("durMul"))));
            }
        }

        private static String reduction(double multiplier) {
            return String.format(java.util.Locale.ROOT, "%.1f％", (1.0D - multiplier) * 100.0D);
        }

        /** 长批次：一条批次内跑 N 次配方时补一行「第 k/N 次」，N=1 时不显示。 */
        private static void appendRuns(ITooltip tooltip, ListTag rows, int index, ThreadDisplaySnapshot.Thread thread) {
            if (index >= rows.size()) return;
            int runs = rows.getCompound(index).getInt("runs");
            if (runs <= 1 || thread.duration() == null || thread.progress() == null) return;
            int perRun = Math.max(1, thread.duration() / runs);
            int current = Math.max(1, Math.min(runs, thread.progress() / perRun + 1));
            tooltip.add(Component.translatable("gtlmodular.independent.runs",
                    Component.literal(Integer.toString(runs)), Component.literal(Integer.toString(perRun)),
                    Component.literal(Integer.toString(current))));
        }

        /** 与 NAC 完全相同的条：绿色=工作中，橙色=等待，白字。 */
        private static void appendBar(ITooltip tooltip, ThreadDisplaySnapshot.Thread thread) {
            if (thread.duration() == null || thread.progress() == null || thread.duration() <= 0) return;
            float ratio = Math.max(0.0F, Math.min(1.0F, (float) thread.progress() / thread.duration()));
            int color = thread.state() == ThreadDisplaySnapshot.State.WORKING ? BAR_WORKING : BAR_WAITING;
            tooltip.add(tooltip.getElementHelper().progress(ratio, ThreadDisplayText.progressTime(thread),
                    tooltip.getElementHelper().progressStyle().color(color).textColor(BAR_TEXT),
                    BoxStyle.DEFAULT, true));
        }

        private static ThreadDisplaySnapshot read(CompoundTag data) {
            List<ThreadDisplaySnapshot.Thread> threads = new ArrayList<>();
            ListTag rows = data.getList("threads", Tag.TAG_COMPOUND);
            for (int i = 0; i < rows.size(); i++) {
                CompoundTag row = rows.getCompound(i);
                threads.add(new ThreadDisplaySnapshot.Thread(
                        row.getInt("label"), row.getLong("eu"), row.getInt("duration"), row.getInt("progress"),
                        row.getLong("parallels"),
                        row.getBoolean("working") ? ThreadDisplaySnapshot.State.WORKING
                                : ThreadDisplaySnapshot.State.WAITING,
                        row.getString("reason"),
                        row.contains("outputId") ? row.getString("outputId") : null,
                        row.contains("outputId") ? row.getLong("outputCount") : null));
            }
            return new ThreadDisplaySnapshot(data.getInt("limit"), data.getInt("running"),
                    data.getLong("totalRuns"), data.getInt("distinct"), null, null, threads);
        }

        private static IElement iconOf(ITooltip tooltip, String outputId) {
            try {
                if (outputId == null || outputId.isEmpty()) return null;
                IElementHelper helper = tooltip.getElementHelper();
                if (outputId.startsWith("fluid:")) {
                    // 流体产物用桶物品当图标；没有桶就只显示文字。
                    var fluid = BuiltInRegistries.FLUID.get(ResourceLocation.tryParse(outputId.substring(6)));
                    if (fluid == null) return null;
                    var bucket = fluid.getBucket();
                    if (bucket == null || bucket == net.minecraft.world.item.Items.AIR) return null;
                    return helper == null ? null : helper.item(new ItemStack(bucket));
                }
                ResourceLocation id = ResourceLocation.tryParse(outputId);
                if (id == null) return null;
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item == null) return null;
                ItemStack stack = new ItemStack(item);
                if (stack.isEmpty()) return null;
                return helper == null ? null : helper.item(stack);
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        /** 流体产物图标：Jade 原生支持流体元素，直接用产物本身的贴图。 */
        private static IElement fluidIconOf(ITooltip tooltip, String id, long amount) {
            try {
                var fluid = BuiltInRegistries.FLUID.get(ResourceLocation.tryParse(id));
                if (fluid == null) return null;
                IElementHelper helper = tooltip.getElementHelper();
                if (helper == null) return null;
                return helper.fluid(snownee.jade.api.fluid.JadeFluidObject.of(fluid, Math.max(1L, amount)));
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        private static String displayNameOf(String outputId) {
            try {
                if (outputId != null && outputId.startsWith("fluid:")) {
                    var fluid = BuiltInRegistries.FLUID.get(ResourceLocation.tryParse(outputId.substring(6)));
                    if (fluid != null) {
                        return com.lowdragmc.lowdraglib.side.fluid.FluidStack.create(fluid, 1000L)
                                .getDisplayName().getString();
                    }
                    return outputId;
                }
                ResourceLocation id = ResourceLocation.tryParse(outputId);
                if (id == null) return outputId;
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item == null) return outputId;
                return new ItemStack(item).getHoverName().getString();
            } catch (RuntimeException ignored) {
                return outputId;
            }
        }
    }
}
