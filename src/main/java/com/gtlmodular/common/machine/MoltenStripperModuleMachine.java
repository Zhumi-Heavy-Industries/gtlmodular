package com.gtlmodular.common.machine;

import com.google.common.primitives.Ints;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineModifyDrops;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gtlmodular.common.config.GTLModularConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.api.machine.ISuspendableMachine;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

public class MoltenStripperModuleMachine extends MoltenModuleMachineBase<MoltenStripperModuleMachine>
        implements IInteractedMachine, IMachineModifyDrops {

    public static final int MAX_BOOST_COUNT = 128;

    private static final Map<String, Integer> EMBEDDABLE_MAINFRAMES = new LinkedHashMap<>();

    static {
        EMBEDDABLE_MAINFRAMES.put("kubejs:cosmic_mainframe", 128);
        EMBEDDABLE_MAINFRAMES.put("kubejs:exotic_mainframe", 64);
        EMBEDDABLE_MAINFRAMES.put("kubejs:optical_mainframe", 32);
        EMBEDDABLE_MAINFRAMES.put("kubejs:bioware_mainframe", 16);
    }

    private static final int COIL_TEMP_BASELINE = 21600;
    private static final int COIL_TEMP_STEP = 1200;
    private static final int COIL_TEMP_STEP_PARALLEL = 300;

    private static final long BASE_PARALLEL = 1536L;

    private static final int PARALLEL_BOOST_BASE_EXP = 6;
    private static final int PARALLEL_BOOST_EXP_SCALE = 10;
    private static final int PARALLEL_BOOST_DIVISOR = 184;

    private static final String PARALLEL_BOOST_FORMULA = "2^(" + PARALLEL_BOOST_BASE_EXP + " + "
            + PARALLEL_BOOST_EXP_SCALE + "×((n−1)/" + PARALLEL_BOOST_DIVISOR + ")²)";

    public static double energyMultiplier() {
        return GTLModularConfig.getStripperEnergyMultiplier();
    }

    public static double durationMultiplier() {
        return GTLModularConfig.getStripperDurationMultiplier();
    }

    private final Map<String, Integer> installedMainframes = new LinkedHashMap<>();

    private long parallelAmount = BASE_PARALLEL;

    public MoltenStripperModuleMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    /** 只缓存物品 ID；允许嵌入的列表可能在运行中变化，资格仍须实时判断。 */
    private static final java.util.Map<net.minecraft.world.item.Item, String> ITEM_ID_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean isEmbeddable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        net.minecraft.world.item.Item item = stack.getItem();
        String id = ITEM_ID_CACHE.get(item);
        if (id == null) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            id = key == null ? "" : key.toString();
            ITEM_ID_CACHE.put(item, id);
        }
        return !id.isEmpty() && EMBEDDABLE_MAINFRAMES.containsKey(id);
    }

    public static MoltenStripperModuleMachine findStripperFor(Level level, BlockPos pos) {
        MetaMachine machine = MetaMachine.getMachine(level, pos);
        return (machine instanceof MoltenStripperModuleMachine stripper) ? stripper : null;
    }

    public int installedMainframeRaw() {
        int total = 0;
        for (int v : installedMainframes.values()) {
            total += v;
        }
        return total;
    }

    public int installedCount(String itemId) {
        return installedMainframes.getOrDefault(itemId, 0);
    }

    public int installedMainframeCount() {
        return Math.min(installedMainframeRaw(), MAX_BOOST_COUNT);
    }

    @Override
    public InteractionResult onUse(BlockState state, Level level, BlockPos pos, Player player,
                                   InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isEmbeddable(stack)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            embedFromHeld(player, stack);
        }
        return InteractionResult.SUCCESS;
    }

    public void embedFromHeld(Player player, ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return;
        }
        Integer cap = EMBEDDABLE_MAINFRAMES.get(id.toString());
        if (cap == null) {
            return;
        }
        int current = installedCount(id.toString());
        int allowed = Math.min(stack.getCount(),
                Math.min(cap - current, MAX_BOOST_COUNT - installedMainframeRaw()));
        if (allowed <= 0) {
            player.displayClientMessage(Component.translatable("gtlmodular.stripper.embed_full"), true);
            return;
        }
        installedMainframes.put(id.toString(), current + allowed);
        if (!player.isCreative()) {
            stack.shrink(allowed);
        }
        player.displayClientMessage(Component.translatable("gtlmodular.stripper.embed_ok",
                allowed, installedMainframeCount(), MAX_BOOST_COUNT), true);
    }

    /** 掉落数量来自 NBT，必须设上限以免坏档驱动巨量掉落实体导致卡死。 */
    private static final int MAINFRAME_DROP_GUARD = 4096;

    @Override
    public void onDrops(List<ItemStack> drops) {
        for (Map.Entry<String, Integer> e : installedMainframes.entrySet()) {
            var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(e.getKey()));
            if (item == null) {
                continue;
            }
            int left = Math.max(0, e.getValue());
            int guard = 0;
            while (left > 0 && guard++ < MAINFRAME_DROP_GUARD) {
                int give = Math.min(left, 64);
                drops.add(new ItemStack(item, give));
                left -= give;
            }
            if (left > 0) {
                com.gtlmodular.GTLModular.LOGGER.warn(
                        "[gtlmodular] 熔融剥离模块拆机掉落触发防御上限：{} 还有 {} 件未掉落"
                                + "（上限 {} 件；此值来自存档，正常玩法不应出现）",
                        e.getKey(), left, MAINFRAME_DROP_GUARD);
            }
        }
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        CompoundTag embedded = new CompoundTag();
        for (Map.Entry<String, Integer> e : installedMainframes.entrySet()) {
            embedded.putInt(e.getKey(), e.getValue());
        }
        tag.put("GTLModularInstalledMainframes", embedded);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        installedMainframes.clear();
        if (tag.contains("GTLModularInstalledMainframes")) {
            CompoundTag embedded = tag.getCompound("GTLModularInstalledMainframes");
            for (String key : embedded.getAllKeys()) {
                int v = embedded.getInt(key);
                if (v > 0) {
                    installedMainframes.put(key, v);
                }
            }
        }
    }

    public long getRealParallel() {
        int coilTemp = hostCoilTemperature();
        long base = BASE_PARALLEL
                + (long) (Math.max(0, coilTemp - COIL_TEMP_BASELINE) / COIL_TEMP_STEP) * COIL_TEMP_STEP_PARALLEL;
        int n = installedMainframeCount();
        if (n <= 0) {
            return base;
        }
        double x = (Math.min(n, MAX_BOOST_COUNT) - 1) / (double) PARALLEL_BOOST_DIVISOR;
        double exponent = PARALLEL_BOOST_BASE_EXP + (double) PARALLEL_BOOST_EXP_SCALE * x * x;
        return Math.round(base * Math.pow(2.0D, exponent));
    }

    private void refreshParallel() {
        parallelAmount = getRealParallel();
    }

    @Override
    public int getMaxParallel() {
        return Ints.saturatedCast(getRealParallel());
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new StripperRecipeLogic(this, EBF_CHECK);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        if (getRecipeLogic() != null) {
            getRecipeLogic().setReduction(energyMultiplier(), durationMultiplier());
        }
        refreshParallel();
    }

    /** RecipeLogic 重建会丢失倍率，主机连接或重建后需要重新设置。 */
    @Override
    public void onHostLinked() {
        if (isFormed() && getRecipeLogic() != null) {
            getRecipeLogic().setReduction(energyMultiplier(), durationMultiplier());
            refreshParallel();
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        if (getRecipeLogic() != null) {
            getRecipeLogic().setReduction(1.0D, 1.0D);
        }
    }

    @Override
    public void onMachineRemoved() {
        super.onMachineRemoved();
    }

    /** 说明读取当前配置和物品名称，避免配置改变后提示失真。 */
    public static void appendConfiguredTooltips(ItemStack stack, List<Component> lines) {
        lines.add(Component.translatable("gtlmodular.stripper.tooltip.cross_recipe")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gtlmodular.stripper.tooltip.energy",
                Component.literal(trimNumber(energyMultiplier())).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gtlmodular.stripper.tooltip.embed",
                mainframeList(), Component.literal(String.valueOf(MAX_BOOST_COUNT)).withStyle(ChatFormatting.GOLD),
                Component.literal(PARALLEL_BOOST_FORMULA).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY));
        appendFluidTaxTooltip(lines);
    }

    private static Component mainframeList() {
        Component result = Component.empty();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : EMBEDDABLE_MAINFRAMES.entrySet()) {
            Component name = mainframeName(entry.getKey());
            if (!first) {
                result = result.copy().append(Component.literal(" / ").withStyle(ChatFormatting.GRAY));
            }
            first = false;
            result = result.copy()
                    .append(name.copy().withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" " + entry.getValue()).withStyle(ChatFormatting.GOLD));
        }
        return result;
    }

    private static Component mainframeName(String itemId) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        return item == null ? Component.literal(itemId) : item.getDescription();
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        if (!isFormed()) {
            return;
        }
        long parallel = getRealParallel();
        if (parallel > 1) {
            textList.add(Component.translatable("gtlmodular.multiblock.parallel",
                    Component.literal(FormattingUtil.formatNumbers(parallel))
                            .withStyle(ChatFormatting.DARK_PURPLE))
                    .withStyle(ChatFormatting.GRAY));
        }
        textList.add(Component.translatable("gtladditions.multiblock.threads",
                Component.translatable("gtladditions.multiblock.forge_of_the_antichrist.parallel")
                        .withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY));
        textList.add(Component.translatable("gtlmodular.multiblock.installed_mainframes",
                Component.literal(installedMainframeCount() + "/" + MAX_BOOST_COUNT)
                        .withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY));
    }

    private static final class StripperRecipeLogic extends MoltenModuleRecipeLogic {

        StripperRecipeLogic(MoltenStripperModuleMachine machine,
                            BiPredicate<CompoundTag, IRecipeLogicMachine> dataCheck) {
            super(machine, dataCheck);
        }

        @Override
        public void findAndHandleRecipe() {
            lastRecipe = null;
            setRecipeStatus(null);
            GTRecipe recipe = buildUniformRecipe();
            if (recipe != null) {
                RecipeResult.of(machine, RecipeResult.SUCCESS);
                if (RecipeRunnerHelper.matchRecipeOutput(machine, recipe)) {
                    setupRecipe(recipe);
                }
            }
        }

        @Override
        public void onRecipeFinish() {
            machine.afterWorking();
            if (lastRecipe != null) {
                RecipeRunnerHelper.handleRecipeOutput(machine, lastRecipe);
            }
            if (machine instanceof ISuspendableMachine suspend && suspend.gtlcore$isSuspendAfterFinish()) {
                setStatus(RecipeLogic.Status.SUSPEND);
                suspend.gtlcore$setSuspendAfterFinish(false);
                progress = 0;
                duration = 0;
                return;
            }
            GTRecipe recipe = buildUniformRecipe();
            if (recipe != null && RecipeRunnerHelper.matchRecipeOutput(machine, recipe)) {
                setupRecipe(recipe);
                return;
            }
            setStatus(RecipeLogic.Status.IDLE);
            progress = 0;
            duration = 0;
        }

        private boolean isRecipeValid(GTRecipe recipe) {
            if (!RecipeRunnerHelper.matchRecipe(machine, recipe)) {
                return false;
            }
            if (IGTRecipe.of(recipe).getEuTier() > getMachine().getTier()) {
                return false;
            }
            if (!recipe.checkConditions(this).isSuccess()) {
                return false;
            }
            BiPredicate<CompoundTag, IRecipeLogicMachine> check = getDataCheck();
            return check == null || check.test(recipe.data, machine);
        }

        private GTRecipe buildUniformRecipe() {
            long overclockVoltage = getMachine().getOverclockVoltage();
            if (overclockVoltage <= 0) {
                return null;
            }
            long parallel = ((MoltenStripperModuleMachine) machine).getRealParallel();
            if (parallel <= 0) {
                return null;
            }
            GTRecipe merged = GTRecipeBuilder.ofRaw().buildRawRecipe();
            merged.outputs.put(ItemRecipeCapability.CAP, new ObjectArrayList<>());
            merged.outputs.put(FluidRecipeCapability.CAP, new ObjectArrayList<>());
            double totalEu = 0;
            double euMultiplier = getEuMultiplier();

            Iterator<GTRecipe> iterator =
                    machine.getRecipeType().getLookup().getRecipeIterator(machine, this::isRecipeValid);
            while (iterator.hasNext()) {
                GTRecipe recipe = iterator.next();
                if (recipe == null) {
                    continue;
                }
                long p = IParallelLogic.getMaxParallel(machine, recipe, parallel);
                if (p <= 0) {
                    continue;
                }
                if (p > 1) {
                    recipe = recipe.copy(ContentModifier.multiplier((double) p), false);
                }
                IGTRecipe.of(recipe).setRealParallels(p);
                recipe = IParallelLogic.getRecipeOutputChance(machine, recipe);
                if (!RecipeRunnerHelper.handleRecipeInput(machine, recipe)) {
                    continue;
                }
                totalEu += getTotalEuOfRecipe(recipe) * euMultiplier;
                List<Content> items = recipe.outputs.get(ItemRecipeCapability.CAP);
                if (items != null) {
                    merged.outputs.get(ItemRecipeCapability.CAP).addAll(items);
                }
                List<Content> fluids = recipe.outputs.get(FluidRecipeCapability.CAP);
                if (fluids != null) {
                    merged.outputs.get(FluidRecipeCapability.CAP).addAll(fluids);
                }
                if (totalEu / (double) overclockVoltage > 10000.0D) {
                    break;
                }
            }

            if (merged.outputs.get(ItemRecipeCapability.CAP).isEmpty()
                    && merged.outputs.get(FluidRecipeCapability.CAP).isEmpty()) {
                if (getRecipeStatus() == null || getRecipeStatus().isSuccess()) {
                    RecipeResult.of(machine, RecipeResult.FAIL_FIND);
                }
                return null;
            }

            double ratio = totalEu / (double) overclockVoltage;
            long eut = ratio > 20.0D ? overclockVoltage : (long) (overclockVoltage * ratio / 20.0D);
            // 耗能倍率已计入 totalEu，这里再次相乘会变成倍率平方。
            merged.tickInputs.put(EURecipeCapability.CAP,
                    List.of(new Content(Math.max(1L, eut), 10000, 10000, 0, null, null)));
            merged.duration = (int) Math.max(1.0D,
                    Math.round(Math.max(ratio, 20.0D) * durationMultiplier()));
            IGTRecipe.of(merged).setHasTick(true);
            return merged;
        }
    }
}
