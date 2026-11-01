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

/**
 * 模块B：熔岩星核剥离单元（宏原子式跨配方并行改造版）。
 *
 * <p><b>工作方式（对照「宏原子共振碎片剥离机」公式，按本模组需要调整）：</b>
 * <ul>
 *   <li><b>不允许放并行控制仓</b>：并行完全由本机公式给出。</li>
 *   <li><b>跨配方并行</b>：一次执行所有匹配配方，每个配方使用<b>相同</b>的并行数
 *       （不像常规多配方机按输入能力各自分配）。</li>
 *   <li><b>并行公式</b>：{@code 基础 1536 + max(0, 主机线圈温度 - 21600)/1200*300}，
 *       嵌入处理器主机后再乘 {@code 2^(6 + 10*((count-1)/184)^2)}（count=0 时不乘）。</li>
 *   <li><b>线圈来自主机</b>：温度取「地心涌动能量转换站」大楼的线圈（可整楼更换），模块自身无线圈。</li>
 *   <li><b>加强来源 = 嵌入处理器主机</b>（原「塞星阵」槽位）：右键本机塞入寰宇/奇异/光学/生物活性
 *       四种处理器主机，各自上限 128/64/32/16、总量封顶 128；塞入方式与星阵一致
 *       （右键即塞、潜行同样生效、物品被消耗、拆机掉落归还）。</li>
 *   <li><b>耗能倍率 600%（耗能 ×6，即原先的 6 倍）、耗时倍率 100%（耗时不变）</b>；
 *       耗电仍由主机集中供给（生态圈式），双流体税照旧。</li>
 * </ul>
 */
public class MoltenStripperModuleMachine extends MoltenModuleMachineBase<MoltenStripperModuleMachine>
        implements IInteractedMachine, IMachineModifyDrops {

    /** 并行倍率公式里使用的等效数量上限（= 可嵌入处理器主机的总量上限）。 */
    public static final int MAX_BOOST_COUNT = 128;

    /** 允许嵌入的处理器主机 → 各自上限（物品 ID → 上限）。 */
    private static final Map<String, Integer> EMBEDDABLE_MAINFRAMES = new LinkedHashMap<>();

    static {
        EMBEDDABLE_MAINFRAMES.put("kubejs:cosmic_mainframe", 128);
        EMBEDDABLE_MAINFRAMES.put("kubejs:exotic_mainframe", 64);
        EMBEDDABLE_MAINFRAMES.put("kubejs:optical_mainframe", 32);
        EMBEDDABLE_MAINFRAMES.put("kubejs:bioware_mainframe", 16);
    }

    /** 并行公式：线圈温度超过基线后，每 1200K 并行 +300。 */
    private static final int COIL_TEMP_BASELINE = 21600;
    private static final int COIL_TEMP_STEP = 1200;
    private static final int COIL_TEMP_STEP_PARALLEL = 300;

    /** 基础并行数。 */
    private static final long BASE_PARALLEL = 1536L;

    /** 并行倍率公式：{@code 2^(BASE + SCALE × ((n−1)/DIVISOR)²)}（常量同源，说明里按这份打印）。 */
    private static final int PARALLEL_BOOST_BASE_EXP = 6;
    private static final int PARALLEL_BOOST_EXP_SCALE = 10;
    private static final int PARALLEL_BOOST_DIVISOR = 184;

    /** 展示给玩家的公式串（与 {@link #getRealParallel()} 里的常数保持同源）。 */
    private static final String PARALLEL_BOOST_FORMULA = "2^(" + PARALLEL_BOOST_BASE_EXP + " + "
            + PARALLEL_BOOST_EXP_SCALE + "×((n−1)/" + PARALLEL_BOOST_DIVISOR + ")²)";

    /**
     * 耗能倍数（总耗能 × 该值，与耗时倍数一起走 {@code setReduction(energy, duration)}）：
     * 默认 <b>6 = 600%</b>，读配置 {@code stripper.energyMultiplier}（可在 toml 里改）。
     * gtlcore 的 Jade 插件读的就是这两个字段，所以游戏内显示「总耗能倍率：600% / 总耗时倍率：100%」。
     */
    public static double energyMultiplier() {
        return GTLModularConfig.getStripperEnergyMultiplier();
    }

    /** 耗时倍数：默认 1 = 100%（不额外改耗时），读配置 {@code stripper.durationMultiplier}。 */
    public static double durationMultiplier() {
        return GTLModularConfig.getStripperDurationMultiplier();
    }

    /** 已嵌入的处理器主机数量（物品 ID → 数量）。 */
    private final Map<String, Integer> installedMainframes = new LinkedHashMap<>();

    /** 并行数缓存（仅用于界面显示；实际值由 {@link #getRealParallel()} 实时计算）。 */
    private long parallelAmount = BASE_PARALLEL;

    public MoltenStripperModuleMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    // ---------- 嵌入处理器主机（原「塞星阵」槽位）----------

    /**
     * 物品 → 注册表 id 字符串 的缓存（每种物品只解析一次）。
     *
     * <p>右键<b>任意</b>方块都会走到 {@link #isEmbeddable}，原来每次都做一次注册表查表 + 一次
     * {@code toString()} 字符串分配。这里只缓存 id；是否可嵌入仍然<b>实时查</b>
     * {@link #EMBEDDABLE_MAINFRAMES}（那两张表可能在游戏内被改）。
     */
    private static final java.util.Map<net.minecraft.world.item.Item, String> ITEM_ID_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 手持物是否为允许嵌入的处理器主机。 */
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

    /** 找出该位置的剥离单元：<b>只有点到本机（控制器方块）本身才算</b>，外壳/管道等结构方块不算。 */
    public static MoltenStripperModuleMachine findStripperFor(Level level, BlockPos pos) {
        MetaMachine machine = MetaMachine.getMachine(level, pos);
        return (machine instanceof MoltenStripperModuleMachine stripper) ? stripper : null;
    }

    /** 已嵌入总量（未按上限截断）。 */
    public int installedMainframeRaw() {
        int total = 0;
        for (int v : installedMainframes.values()) {
            total += v;
        }
        return total;
    }

    /** 某一种处理器主机的已嵌入数量。 */
    public int installedCount(String itemId) {
        return installedMainframes.getOrDefault(itemId, 0);
    }

    /** 参与并行倍率计算的嵌入数量 n（总量封顶 {@link #MAX_BOOST_COUNT}）。 */
    public int installedMainframeCount() {
        return Math.min(installedMainframeRaw(), MAX_BOOST_COUNT);
    }

    /** 右键本机：手持处理器主机时直接塞入（与塞星阵一致，不打开界面）。 */
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

    /** 从手持物品塞入（仅服务端调用）；与星阵一致：能塞多少塞多少、超出上限给提示。 */
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

    /**
     * 单次拆机的掉落件数<b>防御上限</b>（★2026-09-18 新增）。
     *
     * <p>为什么需要（卡死类防御）：下面这个 while 的次数 = {@code ceil(每台主机的数量之和 / 64)}，
     * 而那个数量是从 NBT 读回来的（{@code GTLModularInstalledMainframes}）—— 旧档 / 手改档 / 坏档
     * 若给出一个极大值（例如 {@code Integer.MAX_VALUE}），拆一块方块就要循环 3355 万次、
     * <b>每次都 new 一个 ItemStack</b> ⇒ 服务端线程当场停摆 + GC 雪崩。
     * 正常玩法里这个数最多几百（每台主机都是一个物品），所以上限取 4096 件等价于"不限制"，
     * 只是把"坏数据 ⇒ 卡死"变成"坏数据 ⇒ 少掉几件 + 一条 WARN"。
     */
    private static final int MAINFRAME_DROP_GUARD = 4096;

    /** 拆机掉落：已嵌入的处理器主机全部归还。 */
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

    // ---------- 并行 ----------

    /**
     * 实时并行数：{@code 1536 + max(0, 线圈温度-21600)/1200*300}；已嵌入处理器主机数量 n&gt;0 时
     * 再乘 {@code 2^(6 + 10*((n-1)/184)^2)}（沿用原星阵的倍率公式）。
     * 温度取主机同步值（见 {@link #hostCoilTemperature()}），客户端显示与服务端一致。
     */
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

    /** 刷新界面显示用的并行缓存。 */
    private void refreshParallel() {
        parallelAmount = getRealParallel();
    }

    /** 常规多配方路径使用的并行数（本机实际走 {@link StripperRecipeLogic} 的同并行合并）。 */
    @Override
    public int getMaxParallel() {
        return Ints.saturatedCast(getRealParallel());
    }

    // ---------- 生命周期 ----------

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new StripperRecipeLogic(this, EBF_CHECK);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        // 耗能倍数 / 耗时倍数走 setReduction（与参考机一致，Jade 也读这两个值）
        if (getRecipeLogic() != null) {
            getRecipeLogic().setReduction(energyMultiplier(), durationMultiplier());
        }
        refreshParallel();
    }

    /** 主机连接/重建后同样把倍率写回去（setReduction 的值会随 RecipeLogic 重建而丢）。 */
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

    /** 方块被移除：已嵌入的处理器主机由 {@link #onDrops} 归还。 */
    @Override
    public void onMachineRemoved() {
        super.onMachineRemoved();
    }

    // ---------- 物品说明（按当前配置/注册表动态生成）----------

    /**
     * 物品说明（挂到 {@code MachineDefinition#setTooltipBuilder}）：
     * 耗能倍数读配置、处理器主机名与上限读代码常量，改了配置或换了物品名，说明会跟着变。
     */
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

    /** 「寰宇 128 / 奇异 64 / 光学 32 / 生物活性 16」：名字取物品注册名的本地化名，上限取代码常量。 */
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

    // ---------- 界面 ----------

    /**
     * 显示行与「宏原子共振碎片剥离机」同步：
     * <ul>
     *   <li>同时处理至多 N 个配方（N = 当前并行数，跨配方并行）</li>
     *   <li>拥有无限个跨配方线程</li>
     *   <li>已安装 x/128 个处理器主机（本机嵌入，原星阵槽位）</li>
     * </ul>
     * 不提供「配方最短耗时」与「切换机器模式」两个配置器。
     */
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

    // ---------- 跨配方合并逻辑：所有配方同并行数 ----------

    /**
     * 星核剥离专用 RecipeLogic：在 gtlcore 多配方合并的基础上，把"每条配方的并行上限"从
     * 共享预算改为<b>固定并行数</b>（{@link #getRealParallel()}），实现"一次执行所有配方、
     * 每个配方拥有相同的并行数"；产出合并、EUt/时长折算与原逻辑一致。
     */
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

        /** 候选配方校验（与 gtlcore MultipleRecipesLogic 内部一致）。 */
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

        /**
         * 合并所有匹配配方为一条执行计划：每条配方的并行上限固定为当前并行数（同并行），
         * 汇总产出与总能耗，再折算出 EUt 与总时长。
         */
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
                // 关键差异：并行上限 = 固定并行数（而非"剩余共享预算"）
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
            // 耗能倍数已由 setReduction(energyMultiplier, durationMultiplier) → getEuMultiplier()
            // 乘进 totalEu，这里不能再乘一次（否则会变成倍数平方）。
            merged.tickInputs.put(EURecipeCapability.CAP,
                    List.of(new Content(Math.max(1L, eut), 10000, 10000, 0, null, null)));
            merged.duration = (int) Math.max(1.0D,
                    Math.round(Math.max(ratio, 20.0D) * durationMultiplier()));
            IGTRecipe.of(merged).setHasTick(true);
            return merged;
        }
    }
}
