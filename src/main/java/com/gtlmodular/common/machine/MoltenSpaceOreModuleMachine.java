package com.gtlmodular.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gtladd.gtladditions.utils.CommonUtils;
import com.gtlmodular.common.data.module.SpaceOreModulePattern;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.List;

/**
 * 模块C：猪咪撕咬炼矿机（天基炼矿单元）
 *
 * <p>机器骨架 = gtlcore 线圈多配方机（并行仓 + 线程引擎 + 线圈温度降耗 + 全局耗时倍率），
 * 配方表是 gtladditions 的 {@code space_ore_processor}（天基矿石处理）；不沿用星能火箭燃料税，
 * 只吃本模组的熔岩+液态烈焰双流体税（定义级回调）。
 *
 * <p><b>多方块结构</b>（投影实测，见 {@code SpaceOrePatternLayers}）：整根太空电梯塔，
 * 逐格要求：
 * <ul>
 *   <li>'C' {@code gtlcore:space_elevator_mechanical_casing} 太空电梯机械方块
 *       —— <b>可换</b>输入/输出仓与并行仓（不限数量）；</li>
 *   <li>'I' {@code kubejs:space_elevator_internal_support} 太空电梯内部支撑结构 —— 必须，不可替换；</li>
 *   <li>'B' {@code kubejs:module_base} 太空电梯模块基座 —— 必须，不可替换；</li>
 *   <li>'P' {@code gtlcore:power_module} / {@code _2}~{@code _7} 太空电梯动力模块
 *       —— 必须放动力模块，MK1~MKΩ 可互换；</li>
 *   <li>'.' 其余方块（混凝土 / 铱外壳 / 太空电梯模块连接器 / 空气…）不限。</li>
 * </ul>
 * 投影里的「太空电梯模块连接器」不算本机结构（它仍归主机那栋楼），图案里那格是 '.'。
 *
 * <p><b>太空电梯动力模块（可换级）</b>：跨配方线程数 = 结构里<b>最高等级</b>的动力模块
 * 决定 —— MK1 = {@value #GTLCORE_THREADS}，每升一级 <b>×4</b>
 * （64 / 256 / 1024 / 4096 / 16384 / 65536 / 262144，见
 * {@link SpaceOreModulePattern#threadsForTier(int)}）。
 * 换模块立即生效：判定时记下的槽位坐标在运行时实时回读，不需要拆结构。
 *
 * <p><b>界面显示与「天基无尽集成矿石处理厂」大致一致</b>：
 * <ul>
 *   <li>并行行（{@code 同时处理至多 N 个配方}）由基类按<b>并行仓的真实数值</b>画出（装了并行仓才显示）；</li>
 *   <li>线程行按本机真实的跨配方线程数写数字（动力模块等级决定）；</li>
 *   <li>不显示热容行（该参考机没有这一行）。</li>
 * </ul>
 *
 * <p><b>隐性联动（不显示为说明文字）</b>：主机热容达到 {@value #HIDDEN_PARALLEL_TEMP} K 时，
 * 本机并行直接拉满到 {@link Integer#MAX_VALUE}，并且<b>并行仓的设定不再生效</b>；
 * 热容高于该值同样持续生效。界面照常画「同时处理至多 N 个配方」，此时 N 显示为 int
 * （2147483647）并套用原来「跨配方无限」的彩虹特效，但不加任何解释性文字。
 */
public class MoltenSpaceOreModuleMachine extends MoltenBlastModuleMachine {

    /** 自带同步字段（动力模块等级/数量/线程数）必须挂到自己的 ManagedFieldHolder 上才会同步到客户端。 */
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(MoltenSpaceOreModuleMachine.class,
                    MoltenModuleMachineBase.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /**
     * gtlcore {@code MultipleRecipesLogic} 的跨配方线程上限（{@code MAX_THREADS}）：
     * 其合并预算为 {@code getMaxParallel() * 64}，即一次最多同时处理 64 条不同配方。
     * 没装动力模块时按 MK1 算，用的就是它。
     */
    public static final int GTLCORE_THREADS = 64;

    /** 隐性联动阈值：主机热容（线圈温度）达到该值后并行拉满。 */
    public static final int HIDDEN_PARALLEL_TEMP = 16200;

    /** 结构里最高等级的动力模块（1 = MK1）。界面文字是客户端拼的，所以必须同步。 */
    @DescSynced
    private int syncedPowerTier = 1;

    /** 结构里动力模块的块数。 */
    @DescSynced
    private int syncedPowerModules;

    /** 服务端算好的跨配方线程数（客户端界面直接显示这个）。 */
    @DescSynced
    private int syncedThreads = GTLCORE_THREADS;

    /** 判定时记下的动力模块槽世界坐标（服务器端实时回读等级）。 */
    private final List<BlockPos> powerCells = new ObjectArrayList<>();

    /** 动力模块等级的实时缓存（20 刻内不重复扫）。 */
    private int livePowerTier = -1;
    private int livePowerModules;
    private long livePowerTick = -1L;

    public MoltenSpaceOreModuleMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    // ---------- 动力模块槽坐标（判定上下文 → 运行时回读）----------

    /** 成形时取走动力模块槽坐标（与主机取线圈格同一套做法）。 */
    private void capturePowerCells() {
        powerCells.clear();
        Object o = getMultiblockState().getMatchContext().get(SpaceOreModulePattern.POWER_CELLS_KEY);
        if (o instanceof List<?> list) {
            for (Object p : list) {
                if (p instanceof BlockPos bp && !powerCells.contains(bp)) {
                    powerCells.add(bp);
                }
            }
        }
        powerCells.sort(Comparator.comparingLong(BlockPos::asLong));
        livePowerTier = -1;
        livePowerTick = -1L;
    }

    /**
     * 实时回读动力模块槽上「现在装的是哪一级」：取<b>最高等级</b>的那块。
     * 换模块（MK1 ↔ MK5）立即生效，不需要结构重新判定。
     */
    private int readPowerModuleTier(Level lv) {
        if (powerCells.isEmpty()) {
            if (!isFormed()) {
                return 1;
            }
            // 兜底：读档/重载后可能没走到 onStructureFormed，从判定上下文再抓一次（抓到就不再来）
            capturePowerCells();
            if (powerCells.isEmpty()) {
                return syncedPowerTier > 0 ? syncedPowerTier : 1;
            }
        }
        long t = lv.getGameTime();
        if (livePowerTier > 0 && livePowerTick >= 0 && t - livePowerTick < 20L) {
            return livePowerTier;
        }
        livePowerTick = t;
        int best = 0;
        int count = 0;
        for (BlockPos p : powerCells) {
            if (!lv.isLoaded(p)) {
                continue;
            }
            int tier = SpaceOreModulePattern.powerTierOf(lv.getBlockState(p));
            if (tier > 0) {
                count++;
                if (tier > best) {
                    best = tier;
                }
            }
        }
        livePowerTier = Math.max(1, best);
        livePowerModules = count;
        if (!lv.isClientSide) {
            syncedPowerTier = livePowerTier;
            syncedPowerModules = count;
            syncedThreads = threadsForTier(livePowerTier);
        }
        return livePowerTier;
    }

    /** 当前生效的动力模块等级（服务端实时读世界；客户端用同步值）。 */
    public int powerModuleTier() {
        Level lv = getLevel();
        if (lv != null && !lv.isClientSide && isFormed()) {
            return readPowerModuleTier(lv);
        }
        return syncedPowerTier > 0 ? syncedPowerTier : 1;
    }

    /** 当前结构里动力模块的块数。 */
    public int powerModuleCount() {
        Level lv = getLevel();
        if (lv != null && !lv.isClientSide && isFormed()) {
            readPowerModuleTier(lv);
            return livePowerModules;
        }
        return syncedPowerModules;
    }

    /** 等级 → 跨配方线程数（MK1 = 64，每级 ×4）。 */
    public static int threadsForTier(int tier) {
        return SpaceOreModulePattern.threadsForTier(tier);
    }

    /** 本机跨配方线程（动力模块决定）。 */
    public int powerModuleThreads() {
        return threadsForTier(powerModuleTier());
    }

    /** 动力模块槽总数（结构里必须放动力模块的格子数）。 */
    public int powerCellCount() {
        return powerCells.size();
    }

    // ---------- 结构生命周期 ----------

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        capturePowerCells();
        Level lv = getLevel();
        if (lv != null && !lv.isClientSide) {
            readPowerModuleTier(lv);
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        powerCells.clear();
        livePowerTier = -1;
        livePowerTick = -1L;
        Level lv = getLevel();
        if (lv != null && !lv.isClientSide) {
            syncedPowerTier = 1;
            syncedPowerModules = 0;
            syncedThreads = GTLCORE_THREADS;
        }
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        long[] packed = new long[powerCells.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = powerCells.get(i).asLong();
        }
        tag.putLongArray("GTLModularSpaceOrePowerCells", packed);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        powerCells.clear();
        for (long l : tag.getLongArray("GTLModularSpaceOrePowerCells")) {
            powerCells.add(BlockPos.of(l));
        }
        powerCells.sort(Comparator.comparingLong(BlockPos::asLong));
        livePowerTier = -1;
        livePowerTick = -1L;
    }

    // ---------- 线程 / 并行 ----------

    /**
     * 额外线程数（gtladditions 的口径）：<b>外部 Jade/TOP 显示的「拥有 N 个跨配方线程」= 64 + 本方法</b>
     * （见 gtladditions 的 {@code ParallelProviderMixin}）。所以这里返回
     * 「动力模块线程数 − 64 + 天球分歧引擎额外线程」，让外面显示的数字与机器界面里的
     * {@link #totalThreads()} 完全一致。
     *
     * <p>本机结构不允许放天球分歧引擎仓，后半段通常为 0；这个改动只影响显示口径，
     * 不改变合并预算（预算仍是 {@code getMaxParallel() = 并行仓 × totalThreads / 64}）。
     */
    @Override
    public int getAdditionalThread() {
        long bonus = (long) powerModuleThreads() - GTLCORE_THREADS + super.getAdditionalThread();
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, bonus));
    }

    /**
     * 本机跨配方线程 = 基础 64 + 额外线程 = 动力模块给的线程数（MK1 64、每级 ×4）。
     * 与模块A同口径折进 gtlcore 的合并预算：预算 = {@code getMaxParallel() × 64}。
     */
    @Override
    public int totalThreads() {
        return (int) Math.min(Integer.MAX_VALUE, (long) GTLCORE_THREADS + getAdditionalThread());
    }

    /** 线程行由本机自己画（数字取 {@link #totalThreads()}），不让模块A 的界面逻辑重复画一遍。 */
    @Override
    protected boolean showsThreadLine() {
        return false;
    }

    /**
     * 隐性联动：热容达标时并行 = {@link Integer#MAX_VALUE}（并行仓失效）；
     * 否则照旧走 gtlcore 的并行仓读取，并按动力模块线程数放大合并预算。
     */
    @Override
    public int getMaxParallel() {
        if (isHiddenParallelActive()) {
            return Integer.MAX_VALUE;
        }
        return super.getMaxParallel();
    }

    /** 隐性并行条件是否生效（主机热容 ≥ 16200K）。 */
    public boolean isHiddenParallelActive() {
        return hostCoilTemperature() >= HIDDEN_PARALLEL_TEMP;
    }

    // ---------- 说明 / 界面 ----------

    /**
     * 物品说明：静态文案（语言文件）+ 双流体税行 + 动力模块换级规则。
     */
    public static void appendConfiguredTooltips(ItemStack stack, List<Component> lines) {
        lines.add(Component.translatable("block.gtlmodular.molten_space_ore_module.desc")
                .withStyle(ChatFormatting.GRAY));
        appendFluidTaxTooltip(lines);
        lines.add(Component.translatable("gtlmodular.space.tooltip.power",
                Component.literal(String.valueOf(GTLCORE_THREADS)).withStyle(ChatFormatting.GOLD),
                Component.literal(String.valueOf(threadsForTier(SpaceOreModulePattern.MAX_POWER_TIER)))
                        .withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY));
    }

    /** 与「天基无尽集成矿石处理厂」一致：不显示热容行。 */
    @Override
    protected void addHeatCapacityLine(List<Component> textList) {
        // 天基炼矿单元不显示热容行
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        if (!isFormed()) {
            return;
        }
        // 动力模块行：现在装的是哪一级、装了几块
        int tier = powerModuleTier();
        int count = powerModuleCount();
        Component module = count > 0
                ? Component.literal(SpaceOreModulePattern.tierName(tier) + " ×" + count).withStyle(ChatFormatting.GOLD)
                : Component.translatable("gtlmodular.space.power.none").withStyle(ChatFormatting.RED);
        textList.add(Component.translatable("gtlmodular.space.power.line", module)
                .withStyle(ChatFormatting.GRAY));
        // 隐性联动生效时：并行行照常显示，但数字换成「跨配方无限」那套彩虹特效
        if (isHiddenParallelActive()) {
            for (int i = 0; i < textList.size(); i++) {
                Component c = textList.get(i);
                if (c.getContents() instanceof TranslatableContents tc
                        && "gtceu.multiblock.parallel".equals(tc.getKey())) {
                    textList.set(i, Component.translatable("gtceu.multiblock.parallel", rainbowParallel())
                            .withStyle(ChatFormatting.GRAY));
                    break;
                }
            }
        }
        // 跨配方线程行：按结构里的动力模块等级显示真实线程数
        textList.add(Component.translatable("gtladditions.multiblock.threads",
                Component.literal(String.valueOf(powerModuleThreads())).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY));
    }

    /**
     * 并行数字的彩虹特效（沿用原来「无限」那个彩虹组件的写法），内容是本机真实的 int 并行值
     * {@link Integer#MAX_VALUE} = 2147483647。
     */
    private static Component rainbowParallel() {
        return CommonUtils.INSTANCE.createLanguageRainbowComponentOnServer(
                Component.literal(String.valueOf(Integer.MAX_VALUE)));
    }
}
