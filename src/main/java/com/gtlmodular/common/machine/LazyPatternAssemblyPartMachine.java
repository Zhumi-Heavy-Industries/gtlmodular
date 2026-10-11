package com.gtlmodular.common.machine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.AETextInputButtonWidget;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.misc.FluidStorage;
import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.fluid.FluidHelper;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import com.lowdragmc.lowdraglib.side.item.ItemTransferHelper;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.syncdata.managed.IRef;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.gtlcore.gtlcore.api.gui.MEPatternCatalystUIManager;
import org.gtlcore.gtlcore.api.gui.PatternCircuitConfigurator;
import org.gtlcore.gtlcore.api.machine.trait.IMERecipeHandlerTrait;
// 注意：gtlcore 是 Scala 编译的，MEPart 只有嵌套类文件（没有 MEPart.class），
// 所以必须直接 import 嵌套类型，不能 import 外层 MEPart（javac 会报「找不到符号: 类 MEPart」）。
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEIOTrait;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternTrait;
import org.gtlcore.gtlcore.common.data.GTLItems;
import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;
import org.gtlcore.gtlcore.common.machine.multiblock.part.PaginationUIManager;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEIOPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferRecipeHandlerTraitBase;
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.integration.ae2.handler.MEBufferPatternHelper;
import org.gtlcore.gtlcore.integration.ae2.widget.AEPatternViewExtendSlotWidget;

import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.ProcessingPatternItem;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMaps;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>惰性猪咪样板总成（P0 实验机）</b> —— 自研「按需建槽」版 ME 样板总成。
 *
 * <p>与现役 {@link MoltenPatternAssemblyPartMachine}（继承 gtlcore 的<b>具体类</b>
 * {@code MEPatternBufferPartMachine}）不同，本类继承 gtlcore 的<b>抽象基类</b>
 * {@link MEPatternBufferPartMachineBase}。原因是取证结论（见 {@code _自研猪咪兼容清单.md} 第二节）：
 * 具体子类有 27 处直接读 {@code internalInventory[i]} 数组（绕过访问器），
 * 而抽象基类 <b>0 处</b>直读、全部走 {@code getInternalSlot(i)} 访问器 ⇒ 只有在基类这一层做惰性才安全。
 *
 * <h3>惰性建槽（本类存在的唯一理由）</h3>
 * <ul>
 *   <li>常态容量 = <b>已用最高槽 + 1 + 预留</b>（{@link #RESERVE} = 2），<b>不再一次建满 128/640</b>；</li>
 *   <li>{@link #getInternalSlot(int)} 第一次被访问到 i 时才 {@code new InternalSlot(i)} 并缓存；</li>
 *   <li>基类里所有遍历都是 {@code IntStream.range(0, getInternalSlotCount())} 再调访问器，
 *       而我们把 {@code getInternalSlotCount()} 定义成「当前容量」⇒ 每次遍历只为已建出来的槽付费；</li>
 *   <li><b>批量一次性扩容</b>（队长硬需求）：{@link #growCapacityForBatchInsert(int)} /
 *       {@link #insertPatterns(List)} 会<b>先把容量一次扩到能装下全部 N</b>，绝不分几次扩。
 *       将来自研复制/剪切工具的 {@code pasteFromTag} 直接调这两个入口即可。</li>
 * </ul>
 *
 * <h3>P0 只做「能收样板 + 能跑配方」</h3>
 * <p>P0 当时<b>没有</b>倍率/神锻、催化剂槽、身份闸、电路嵌入、分页 UI 之外的附加页。
 * 这些按纵切逐批搬入：P1-① 分页 UI、P1-②B 专属催化剂槽、<b>P1-②C 样板倍率/除率/神锻（视图改写）</b>、
 * <b>P1-②D 样板身份闸</b>、<b>P1-②E 样板电路写入（电路嵌入）</b>。
 * <p><b>2026-09-26 勘误（此前把"没写在文档里"误当成"功能缺失"）</b>：
 * <ul>
 *   <li><b>无线 ME</b>：一直生效（共用 gtlcore 基类那条链），<b>无需实现</b> —— 队长实测确认；</li>
 *   <li><b>镜像（代理）联动</b>：由 gtlcore 的 {@code MEPatternBufferProxyPartMachine#onUse} 自己处理（读的是 {@code pos}），
 *       <b>无需实现</b> —— 队长实测「镜像也能连接上」；</li>
 *   <li><b>Jade</b>：由 gtlcore 的 Jade 插件统一提供（它对 {@code MEPatternBufferPartMachineBase} 做 instanceof 判定），
 *       <b>无需实现</b>；</li>
 *   <li>真正缺的只有 <b>电路嵌入</b>（gtlcore 具体类上的 {@code embedCircuitToPatterns} 那一套）⇒ 见 P1-②E。</li>
 * </ul>
 * 本类也<b>不改动</b>任何现有机器 ——
 * 它是一个全新的方块 id {@code gtlmodular:lazy_me_pattern_assembly}，用于旁路试验。
 *
 * <h3>P2 已搬运的功能（逐条照搬 {@link MoltenPatternAssemblyPartMachine} 的语义）</h3>
 * <ol>
 *   <li><b>每槽配方模式</b>：{@link #slotRecipeTypes}（自动识别缓存，可增长）+ {@link #manualSlotTypes}
 *       （手动指定，优先于自动识别），三条路径 = 手动指定 / 手动清除 / 自动识别；
 *       界面入口在 {@link RecipeModeConfigurator}；</li>
 *   <li><b>自动切换配方模式</b>：{@link #autoSwitchRecipeModes()}（并进既有 {@code lazyTick()} 订阅，
 *       <b>不</b>新增第二个 {@code subscribeServerTick}）；</li>
 *   <li><b>名字跟随</b>：{@link #syncCustomNameToMode(int)}（<b>★ 2026-10-04b：只在服务端；
 *       ★ 2026-10-14：<b>名字 = 本总成自己那个模式的中文名</b>（权威取值 = 本总成的模式页选中模式
 *       {@link #modeCursorIndex}，见 {@link #currentModeIndexForNaming(int)}）⇒ 用户口径
 *       「每个总成绑定一个固定模式」：在**这台总成上**换模式名字才跟着变 ✓，主机在跑别的模式名字不动 ✓；
 *       只在"名字归我们管"时才写（空 / 上游 Enhancedcore 兜底串「未命名加工模式」 /
 *       我们自己写过的串 / 本控制器任一模式的显示名），玩家手填过的绝不覆盖 ✗</b>）；</li>
 *   <li><b>模式页 UI</b>：{@link RecipeModeConfigurator}（◀ / 当前模式 x/y / ▶，Shift 点击跳 5 个），
 *       只在「可识别配方类型 &gt; 1」时挂载。</li>
 * </ol>
 *
 * <h3>P1-②C 已搬运的功能：样板倍率 / 除率 / 神锻（<b>视图改写</b>）</h3>
 * <p>语义与现役机逐条一致：<b>只改本仓向 AE 暴露的样板，仓里的样板物品一个字节都不动</b>。
 * <ul>
 *   <li>{@link #getAvailablePatterns()} 命中改写缓存就直接返回；设置/样板变化后由
 *       {@link #invalidateRewriteCache()}（设置变化）或 {@link #onPatternInventoryChanged()}（样板变化）作废；</li>
 *   <li><b>两侧同倍</b>：{@link #effectiveMul()} = 神锻 × 倍率（可叠加，例如神锻 15 + 倍率 2 = 30 批），
 *       再 ÷ 除率 —— 输入输出用同一个倍率，AE 声明与机器执行永远自洽；</li>
 *   <li>改写结果 {@code PatternDetailsHelper.encodeProcessingPattern → decodePattern} 出来是<b>新对象</b>，
 *       用 {@link IdentityHashMap} 记住"改写后 → 原始"，{@link #pushPattern} 靠它反查槽位；</li>
 *   <li>推送侧还照搬了「AE 仍把原样板推回来时在机器侧按有效倍率放大」的兜底
 *       （AE 的合成服务会长期缓存样板表，改倍率后它不一定立刻重读）；</li>
 *   <li>界面：{@link ScaleConfigurator} / {@link FoaConfigurator}，页序 = 模式页 → 倍率页 → 神锻页 → 催化剂页。</li>
 * </ul>
 *
 * <h3>NBT 契约（与现役机器保持一致，字段名不能改）</h3>
 * <ul>
 *   <li>{@code patternInventory} —— 样板本体（{@code patternInventory.Items} 是我们全部样板的存放处，改名 = 丢样板）；
 *       该字段同时带 {@link Persisted}（走 LDLib 托管字段机制，与现役机器完全同一条路），
 *       并在 {@link #saveCustomPersistedData}/{@link #loadCustomPersistedData} 里<b>再显式读写一次</b>做双保险；</li>
 *   <li>{@code internalInventory} —— 每槽状态列表，<b>按索引对齐</b>；
 *       写侧是短表（只写到最后一个已建出来的槽），读侧同时兼容旧档的满长度（128/640）与新档短表：</li>
 *   <li>{@code customName} —— 机器名（设置类，掉落时也写）。</li>
 * </ul>
 *
 * <h3>本次新增的 NBT 键（<b>刻意不与现役机同名</b>，避免将来互通时格式撞车）</h3>
 * <ul>
 *   <li>{@code gtlmodular$lazy_slot_recipe_type_sparse} —— 逐槽「记住的配方类型」稀疏表，
 *       {@code int[]} 扁平对：{@code [下标0, 类型0, 下标1, 类型1, ...]}，<b>按槽下标对齐</b>。
 *       只写「已识别（&gt;=0）」与「识别不出来（{@value #TYPE_UNKNOWN}）」的槽，跳过绝大多数默认的
 *       {@value #TYPE_NOT_TRIED}；类型值是<b>类型表下标</b>，读回时会逐个校验是否仍落在当前类型表内
 *       （越界或 &lt; -2 一律丢弃，见 {@code loadCustomPersistedData}）。
 *       <b>只增不减</b>：长度跟随容量增长（{@link #ensureRecipeTypeSlotCapacity()}），容量只增不减。</li>
 *   <li>{@code gtlmodular$lazy_manual_slot_types} —— 手动指定表 {@code CompoundTag}：
 *       键 {@code s<槽下标>}（0 起）→ 值 = 配方类型的 {@code ResourceLocation} 字符串。
 *       存 <b>id 而不是下标</b>（与现役机同口径）：换控制器 / 类型表顺序变了也不会指错。
 *       键名写的是本机专有的 {@code gtlmodular$lazy_} 前缀 —— <b>不与现役机的
 *       {@code gtlmodular$slot_recipe_type_sparse} / {@code gtlmodular$manual_slot_types} 互通</b>：
 *       两者格式虽都基于「槽下标」，但惰性机的下标空间是<b>动态容量</b>（32 起步、按需 +16），
 *       与现役机的固定 128/640 不是同一个坐标系，直接互通会指错槽。
 *       将来若要互通，必须先把两边的容量对齐（那属于复制/剪切工具的工作）。</li>
 *   <li>★ P1-②C 的五个<b>设置类</b>键（{@code gtlmodular$lazy_pattern_scale} / {@code ..._pattern_div_scale} /
 *       {@code ..._foa_mode} / {@code ..._foa_multiplier} / {@code ..._output_mode}）—— 见下方 forDrop 规则。
 *       现役机这几个字段只带 LDLib 的 {@code @DescSynced}（不带 {@code @Persisted}）
 *       ⇒ <b>它既不写世界存档也不写掉落物</b>（反汇编取证见 {@code _javap_p1c_api.txt} 第【13】节），
 *       所以"键名照抄现役机"这件事在现役机那边没有对应物；惰性机按本机既有规则自己补键，
 *       统一用 {@code gtlmodular$lazy_} 前缀，读侧一律夹到合法区间。</li>
 * </ul>
 * <p><b>forDrop 规则</b>：{@code forDrop == true}（被拆成掉落方块）时<b>只写设置类字段</b>
 * （{@code customName} + 上面那五个视图改写设置）；{@code patternInventory} / {@code internalInventory}
 * 以及逐槽表 / 催化剂表 <b>一律不写</b> —— 它们都是「按槽下标对齐的内容」,而掉落时样板本身并不随方块带走
 * （样板由 {@code onMachineRemoved} 掉到地上），只写表 = 留下一堆指错槽的孤儿数据，也是复制漏洞的一半。
 */
public class LazyPatternAssemblyPartMachine extends MEPatternBufferPartMachineBase {

    /** 预留空位（队长 2026-09-26 裁决：预留 = 2）。 */
    public static final int RESERVE = 2;

    /**
     * ★ 初始容量（2026-09-26 追加）：给一档起步，避免"空机器只有 2 格"的难受体验。
     *
     * <p>取证依据：基类 {@code getActiveSlots()} 的过滤 lambda 会对**每个下标**都调
     * {@code getInternalSlot(i)} ⇒ 遍历成本 ∝ 容量，所以容量不能一上来就给标称 128/640；
     * 但给 2 又会让玩家/AE 终端一开始只看到 2 格。折中：起步 {@value}，单张插入按
     * {@link #MIN_GROW_STEP} 一档一档长，<b>批量路径（{@link #growCapacityForBatchInsert(int)}）
     * 仍然一次到位</b>。
     */
    public static final int INITIAL_CAPACITY = 32;

    /** 单张插入时的最小扩容步长（批量路径不受此限制）。 */
    public static final int MIN_GROW_STEP = 16;

    /** ★ P1：样板格每行 16 个（与现役/超级猪咪的 {@code PATTERNS_PER_ROW} 一致）。 */
    public static final int PATTERNS_PER_ROW = 16;

    /** ★ P1：分页器用的"标准页行数"= 8 行（16×8 = 128/页，与现役机同口径）。 */
    public static final int ROWS_PER_PAGE = 8;

    /** 自带同步/托管字段挂在 gtlcore 基类的 holder 之上（与现役机器同款写法）。 */
    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(LazyPatternAssemblyPartMachine.class,
                    MEPatternBufferPartMachineBase.MANAGED_FIELD_HOLDER);

    /**
     * 缓存的 {@code NotifiableMERecipeHandlerTrait.notifyListeners()}（只解析一次）。
     *
     * <p>为什么必须反射：该方法虽为 {@code public}，但其声明类被 javac 跨包判为「不可访问」
     * （实测编译错误），且它<b>没有</b>写进公开接口 {@code IMERecipeHandlerTrait}
     * （接口上只有 {@code addChangedListener}/{@code addBufferChangedListener}）。
     * 拿不到就是 {@code null} ⇒ 退化为「不通知」，绝不崩。
     */
    private static final Method NOTIFY_LISTENERS = resolveNotifyListeners();

    private static Method resolveNotifyListeners() {
        try {
            Method method = Class.forName(
                            "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.NotifiableMERecipeHandlerTrait")
                    .getMethod("notifyListeners");
            method.setAccessible(true);
            return method;
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：拿不到 notifyListeners() ⇒ 缓冲内容变化将不通知主机配方逻辑"
                    + "（主机会退回自行轮询）。原因: {}", failure.toString());
            return null;
        }
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /** 机器电压等级（本 P0 机固定 MV = 2）。 */
    private final int tier;

    /** 槽位硬上限（永远不会越过它；由注册处按等级给出）。 */
    private final int maxPatternCount;

    /**
     * 样板仓 —— <b>字段名必须叫 {@code patternInventory}</b>。
     * 存档键 {@code patternInventory.Items} 就是全部样板的存放处，改名 = 丢样板。
     * 容量通过 {@link ItemStackTransfer#setSize(int)} 原地增长（不换对象，避免托管字段引用失效）。
     */
    @Persisted
    private final ItemStackTransfer patternInventory;

    /**
     * 惰性槽位表。<b>长度恒等于当前容量</b>，元素允许为 {@code null}（= 该槽还没被访问过、还没实体化）。
     * 只有 {@link #getInternalSlot(int)} 第一次拿到某个下标时才会填进去。
     */
    private final List<InternalSlot> internalInventory = new ArrayList<>();

    /** AE 终端看到的样板仓包装（AE 的 {@code PatternContainer} 接口要求）。 */
    private final InternalInventory terminalPatternInventory = new InternalInventory() {

        @Override
        public int size() {
            return LazyPatternAssemblyPartMachine.this.getCapacity();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return LazyPatternAssemblyPartMachine.this.patternInventory.getStackInSlot(slot);
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            LazyPatternAssemblyPartMachine.this.patternInventory.setStackInSlot(slot, stack);
            // ★ 0.2.8（审计发现）：LDLib 的 `ItemStackTransfer.setStackInSlot` 只是 `validateSlotIndex; stacks.set(...)`
            //   —— **不会**触发 onContentsChanged。猪咪的终端仓 `$1.setItemDirect` 会显式补一次
            //   `patternInventory.onContentsChanged(slot)` + `onPatternChange(slot)`；我们这里补一次
            //   `onPatternInventoryChanged()`，让 AE 终端/外部工具的写入**立刻**生效，而不是等 lazyTick 的签名轮询。
            LazyPatternAssemblyPartMachine.this.onPatternInventoryChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return LazyPatternAssemblyPartMachine.this.patternInventory.isItemValid(slot, stack);
        }
    };

    /** 配方处理器（必须自研：gtlcore 具体类那个构造器只吃具体类 {@code MEPatternBufferPartMachine}）。 */
    private final LazyPatternBufferRecipeHandler recipeHandler;

    /** 样板 → 槽号（值语义：AE 的样板类实现了 equals/hashCode）。脏了才重建。 */
    private final Map<IPatternDetails, Integer> patternSlotMap = new LinkedHashMap<>();
    private boolean patternSlotMapDirty = true;

    /** 上一次算出的「仓内容签名」，用来在 tick 里廉价地发现「样板变了」。 */
    private long lastUsageSignature;
    /**
     * 签名是否已初始化。
     *
     * <p>★ 2026-09-26：原实现用 {@code lastUsageSignature = Long.MIN_VALUE} 当哨兵，
     * 被本模组第 45 项自检「限流哨兵溢出体检」判红（`gameTime − Long.MIN_VALUE` 会溢出成负
     * ⇒ 限流恒真；本处虽是"变更标记"而非限流，但同类哨兵一律不用）。改成显式标志位。
     */
    private boolean usageSignatureValid = false;
    private TickableSubscription usageSubscription;

    /** ★ 0.3.5：正在通知 AE 重取样板表（防重入）。★ 照搬现役机 {@code refreshingPatterns}。 */
    private boolean refreshingPatterns;

    /** ★ 0.3.5：上一次打印「AE 推来一张本机没有的样板」的时间（gameTime），限流用。 */
    private long lastOrphanPushLog;

    /** ★ 0.3.5：基类样板同步 tick 的取证日志只打一条（见 lazyTick）。 */
    private boolean loggedBaseSyncState;

    // ==================== 主动写一次自定义名（绕开 Enhancedcore 自动命名白名单）====================

    /**
     * ★ 2026-10-04：<b>GTL-Enhancedcore 自动命名词典的硬编码兜底串</b>「未命名加工模式」。
     *
     * <p><b>出处（逐条可核）</b>：作者源码
     * {@code com.gtl.enhancedcore.common.recipe.iv.SuperBufferChineseNames:13}
     * —— {@code private static final String FALLBACK = "\u672a\u547d\u540d\u52a0\u5de5\u6a21\u5f0f";}
     * （源码里就是 Unicode 转义 ⇒ 用中文字面 grep 搜不到）；{@code :21-26} 的 {@code resolve()}
     * 只在「它那份词典查不到 **且** 传入值不含汉字」时才返回它。
     *
     * <p><b>为什么一定落到我们头上</b>：{@code :36-72} 的 {@code load()} 只扫**固定 5 个 mod 的 jar**
     * （{@code gtceu / gtmthings / gtlcore / gtladditions / gtl_enhancedcore}，关键 {@code :40}），
     * <b>不含 gtlmodular</b> ⇒ 我们的键（{@code assets/gtlmodular/lang/zh_cn.json:415-429}，
     * 形态 = {@code registryName.toLanguageKey()}，如 {@code gtlmodular.nanochip_assembly_complex}）
     * 从不进词典 ⇒ {@code SuperBufferNaming.targetKey()} 递进来的键被 {@code resolve} 判成兜底
     * ⇒ 部件自定义名被写成这四个字。
     *
     * <p>本类用它做一件事：<b>把"上游写的兜底串"识别成"这个名字不承载玩家意志"</b>，
     * 从而允许我们自己改写它。判定是全等比较，不猜测。
     */
    private static final String AUTO_NAME_FALLBACK_UPSTREAM = "\u672a\u547d\u540d\u52a0\u5de5\u6a21\u5f0f";

    /**
     * ★ 本补丁的<b>总开关</b>（默认开）。<b>与现役机 {@code MoltenPatternAssemblyPartMachine} 上那个是
     * <u>各自独立</u>的同名字段</b>（两边都默认 {@code true}；把某一处置 {@code false} 只影响那一类机器）。
     * 刻意不做成"一个共享常量"：任务边界要求只改这两个部件类，任何一个类都不去引用另一个类的静态状态。
     *
     * <p>置 {@code false} ⇒ 不再把上游兜底串当成"可改写"、不再主动抢名称 ⇒ <b>完全恢复本补丁之前的行为</b>。
     *
     * <p><b>为什么需要开关（理由与出处）</b>：本补丁靠一个**上游的副作用**来绕开它自己的白名单缺陷 ——
     * {@code com.gtl.enhancedcore.mixin.gtlcore.SuperBufferNamePersistenceMixin:54-66}：任何外部
     * {@code setCustomName(...)} 都会在 TAIL 把该总成标成 {@code enhanced$manualName = true;}
     * （并清空 {@code enhanced$automaticName}），而 {@code SuperBufferAutoName.java:47}
     * （{@code if (manual || ...) return false;}）让上游自动命名对 manual **永久让位**。
     * 我们要的正是这个效果（Enhancedcore 永远认不出我们的类型名，只能由我们自己写），
     * 但它确实改变了上游对这台机器的行为 ⇒ 留一键回退开关，便于对照与排障。
     *
     * <p><b>★ 2026-10-04b：本开关已置 {@code true}（启用）</b> —— 这不是可选装饰：
     * 上游 Enhancedcore 的命名白名单只扫固定 5 个 mod 的 jar（不含 {@code gtlmodular}），
     * 所以"我们自己写一次名字"是唯一能让总成显示中文模式名的路子
     * （写一次 ⇒ 被标 manual ⇒ 上游永久让位）。关掉它 ⇒ 名字只能由上游给 ⇒ 必然显示
     * 「未命名加工模式」✗。
     */
    public static boolean enableActiveCustomNaming = true;

    // ---------- ★ 2026-10-15：名字按「本总成 + 模式」记录（取代旧的"白名单"判据；与现役机同款）----------

    /**
     * <b>模式类型 id → 本总成给该模式起的名字</b>（例 {@code "gtceu:circuit_assembler"} → {@code "电路组装"}）。
     *
     * <p><b>这就是"重进游戏名字被改回去"的根治点</b>：名字跟着<b>模式</b>走，而且<b>落盘</b>
     * （见 {@link #saveCustomPersistedData} 的 {@link #NBT_MODE_NAMES}）⇒ 读档读得回来，
     * 不再依赖任何"内存态 + 猜这名字是谁写的"的判据 ✗。
     *
     * <p>存 id 不存下标（类型表顺序/长度会变）；<b>每台总成各记一份</b>（控制器不归我们所有、
     * 写不进它的存档；用户口径也是"两台各叫各的"）—— 与现役机完全同款。
     */
    private final java.util.Map<String, String> modeNames = new java.util.LinkedHashMap<>();

    /**
     * <b>玩家亲手改过的名字</b>（{@code ""} = 没改过）。非空 ⇒ 本总成的名字<b>永久归玩家</b>：
     * 换模式、改绑定、主机切模式、重进游戏、拆装重成型都<b>不再改写</b> ✗。
     * 落盘在 {@link #NBT_LOCKED_NAME}。唯一例外：名字被清空时把它写回来。
     */
    private String lockedName = "";

    /** 上一次<b>观察</b>到的 customName（内存态，不落盘）：识别"名字被别人改了"。 */
    private String observedName;

    /** 上一次套用名字时对应的模式 id（内存态，不落盘）：把玩家的改名归到"当时显示的那个模式"名下。 */
    private String appliedModeKey;

    /** 上一次命名时挂着的控制器（内存态，不落盘）：被搬到别的机器上 ⇒ 允许按新模式重新起名一次。 */
    private IRecipeLogicMachine lastNamingController;

    /** 落盘上界：名字表最多 {@value} 条模式（读侧同样上界）。 */
    private static final int MAX_SAVED_MODE_NAMES = 64;

    /** 落盘上界：单条名字最多 {@value} 字符（超过 ⇒ 写侧截断 / 读侧丢弃）。 */
    private static final int MAX_SAVED_NAME_LENGTH = 128;

    /** ★ 2026-10-15：名字按模式落盘的键（惰性机前缀，与现役机 gtlmodular$mode_names 格式独立）。 */
    private static final String NBT_MODE_NAMES = "gtlmodular$lazy_mode_names";

    /** ★ 2026-10-15：玩家亲手改过的名字（空/缺键 = 没改过）；见 {@link #lockedName}。 */
    private static final String NBT_LOCKED_NAME = "gtlmodular$lazy_locked_name";

    /** 成型材质补丁的剩余窗口（服务端；见 {@link FormedRenderSyncNudge}）。 */
    private int formedNudgeTicks = FormedRenderSyncNudge.WINDOW_TICKS;

    /** 客户端：载入后头若干次同步都补一次重烘焙（见 {@link #onSyncChanged} 的取证注释）。 */
    private int clientRebakeLeft = 40;

    /** 客户端同步诊断日志额度（有界）。 */
    private int clientSyncDiagLeft = 6;

    /**
     * 「保留副产物」开关（用于解样板时的身份计算）。
     *
     * <p>★ 0.3.1 定案：**默认 {@code false}** —— 依据是具体类 `MEPatternBufferPartMachine` 构造器字节码
     * {@code 24: iconst_0 / 25: putfield keepByProduct}（默认 false；另有从 NBT `keepByProduct` 读回、
     * 以及副产物开关处复位为 false 两条路径）。
     * <p>为什么必须用它而不是写死常量：基类 {@code getRealPatternHelper().processPatternWithCircuit(stack, c, level, keep)}
     * 的第 4 参就是它（字节码：{@code circuit<0 && keep} ⇒ 原样返回样板；否则走
     * {@code createPatternWithoutCircuit(..., keep)}）⇒ 传错会**改变样板身份**，进而改变"槽↔配方"识别结果。
     * <p>键名沿用具体类的 {@code keepByProduct}，将来搬「倍率/神锻」那一批时直接接着用。
     */
    @Persisted
    private boolean keepByProduct = false;

    /**
     * 上一次「每个槽里是哪张样板」的快照（只在服务端用，见 {@link #diffSlotPatterns()}）。
     *
     * <p>为什么必须自己记：基类/具体类那边这个信息在具体类的 {@code slot2PatternMap} 里，
     * 而惰性机的样板可能在**任何**时刻被换（AE 终端、管道、setItemDirect 都不触发回调），
     * 所以用「快照对比」而不是「依赖回调参数」。
     */
    private final List<ItemStack> patternSnapshot = new ArrayList<>();

    /**
     * 快照是否已建立基线：读档后第一次对比**只建基线、不动作**。
     * <p>否则读档时"快照为空 vs 存档里有样板"会被当成"所有槽的样板都被换掉了"
     * ⇒ 把存档里的存量料全部退还/清缓存（会产生莫名其妙的行为）。
     */
    private boolean patternSnapshotReady = false;

    // ==================== P2-① 每槽配方模式 ====================
    //
    // 逐条照搬 MoltenPatternAssemblyPartMachine（现役机）第 497-826 行的语义，只把「固定容量数组」
    // 换成「跟随容量增长的表」（惰性机的容量是动态的，构造期按 128 建数组会毁掉惰性化这个立项目的）。

    /** 自动模式轮询间隔（tick）。★ 照搬现役机 {@code AUTO_SWITCH_INTERVAL_TICKS}。 */
    private static final int AUTO_SWITCH_INTERVAL_TICKS = 20;
    /** 切过去后等它开跑的时间（tick），超了就把这个类型临时拉黑。★ 照搬现役机 {@code AUTO_SWITCH_GRACE_TICKS}。 */
    private static final int AUTO_SWITCH_GRACE_TICKS = 40;
    /** 被拉黑的类型多久内不再选（tick）。★ 照搬现役机 {@code AUTO_SWITCH_BLOCK_TICKS}。 */
    private static final int AUTO_SWITCH_BLOCK_TICKS = 200;

    /**
     * ★ 0.3.4：当前模式「样板都在、配方都配上过、槽里却没料」要**持续**这么久，才允许切到别的模式（tick）。
     *
     * <p>背景（队长 2026-09-26 晚实测：「他现在不会自动切换机器模式了」）：现役机/0.3.1 的那一遍是
     * 「当前模式只要还有样板就绝不切走」，而**最正常的用法就是多种模式的样板常驻仓里**
     * ⇒ 那条规则会让自动切换**永远不触发**（与配置项自己写的"一台机器可以同时挂多种模式的样板、
     * 轮流自动跑"直接矛盾）。这里改成「有活才不走」，并留这段空转宽限期兜住"AE 的料刚好在推送路上"。
     */
    private static final int AUTO_SWITCH_STOCKLESS_TICKS = 600;

    /** {@link #slotRecipeTypes} 的取值：-1 = 还没尝试识别；-2 = 试过但识别不出来（不再重复扫配方表）；>=0 = 类型下标。 */
    private static final int TYPE_NOT_TRIED = -1;
    private static final int TYPE_UNKNOWN = -2;

    /** 扫描结果：本遍**一个都没命中**（★ 照搬现役机 {@code MATCH_NONE}）。 */
    private static final int MATCH_NONE = -2;

    /**
     * ★ <b>冷槽重识别（"重置缓存"）总开关</b>：{@code true}（<b>默认开启</b>）⇒ {@link #selfHealColdSlotCaches()}
     * 生效。{@code false} ⇒ 整条功能一行都不执行，行为<b>逐字退回</b>加这段代码之前。
     *
     * <p>一行回退（逐字）：把本行结尾的 {@code = true} 改成 {@code = false}。
     */
    public static boolean COLD_SLOT_CACHE_HEAL = true;

    /** 两次重识别之间至少隔这么多 tick（重识别会扫配方表 ⇒ 必须限频）。 */
    private static final int COLD_SLOT_HEAL_INTERVAL_TICKS = 200;
    /** 整机一生最多重识别这么多个槽（同槽最多一次；这是防"永远识别不出来"的样板无限烧 CPU 的总量闸）。 */
    private static final int COLD_SLOT_HEAL_BUDGET = 64;

    private static final String NBT_SLOT_RECIPE_TYPE_SPARSE = "gtlmodular$lazy_slot_recipe_type_sparse";
    private static final String NBT_MANUAL_SLOT_TYPES = "gtlmodular$lazy_manual_slot_types";

    /**
     * ★ <b>2026-10-14（本轮：绑定模式持久化）</b>：**本总成负责的模式**的类型 id
     * （见 {@link #boundModeType}）。
     *
     * <p>键名沿用本机 {@code gtlmodular$lazy_} 前缀（与上面两个键、以及本机
     * {@code gtlmodular$lazy_pattern_scale} 等设置键同前缀），因此与现役机的同名功能键
     * （{@code gtlmodular$bound_mode_type}）**互不干扰**：两个类各写各的键，谁也不读谁的 ✓。
     */
    private static final String NBT_BOUND_MODE_TYPE = "gtlmodular$lazy_bound_mode_type";

    /** 手动选择的配方模式（自动识别失败时的兜底；只有一种模式时无意义）。同步给客户端供模式页显示。 */
    @DescSynced
    private int activeRecipeTypeIndex = 0;

    /**
     * ★ 0.3.2 修复（队长实测 bug：<b>「调2号模式去指定，结果还是指定成第1个机器模式」</b>）：
     * <b>模式页游标</b> —— 玩家在模式页上「◀ / ▶ 选中的那一个模式」，与机器的实际模式<b>解耦</b>。
     *
     * <p><b>为什么必须有它</b>：{@link #activeRecipeTypeIndex} 在 {@link #autoSwitchRecipeModes()}
     * 里是「只读跟随控制器」的（每 tick 被改写成 {@code controller.getActiveRecipeType()}），
     * 而 LDLib 的控件点击与方块实体 tick 在<b>同一个服务端 tick</b> 内先处理点击、后跑 tick
     * ⇒ 玩家点完 ◀/▶ 的下一刻就被改回主机的模式 ⇒ {@link #assignManualType(int, boolean)}
     * 读到的一直是<b>主机当前模式</b>（切割机），玩家选的车床被吃掉了。
     *
     * <p>游标与「机器实际模式」是两个概念（模式页的用途本来就是「<b>给这个槽指定哪种模式</b>」，
     * 见 {@code gtlmodular.pattern_assembly.mode.tooltip}：手动模式只在自动识别不出来时兜底），
     * 所以这里<b>不</b>把游标写回控制器（保持与现役机「点按钮不打断正在跑的配方」的纪律一致），
     * 而是在模式页上把<b>两个模式都显示出来</b>：
     * 游标 = 要指定下去的模式，第二行 = 主机现在的模式。
     */
    @DescSynced
    private int modeCursorIndex = 0;

    /** 玩家是否亲手拨过模式页。没拨过之前游标跟随主机当前模式（打开界面时默认值就是「机器现在的模式」）。 */
    private boolean modeCursorTouched = false;

    /**
     * ★ <b>2026-10-14（本轮：绑定模式持久化）</b>：<b>本总成负责的模式</b>的<b>类型 id</b>
     * （{@link GTRecipeType#registryName}，形如 {@code gtceu:lathe}）。{@code null} = <b>未绑定</b>。
     *
     * <p><b>为什么必须有它</b>：{@link #modeCursorIndex} 只带 {@code @DescSynced}、<b>没有 {@code @Persisted}</b>，
     * 也不在 {@link #saveCustomPersistedData} 的任何键里 ⇒ 读档后 {@code modeCursorIndex = 0}、
     * {@code modeCursorTouched = false} ⇒ {@link #autoSwitchRecipeModes()} 的「游标跟随控制器」立刻把它
     * 改回主机模式 ⇒ <b>「一次绑定」重启后失效</b>。本字段 + {@link #NBT_BOUND_MODE_TYPE} 键就是修这一条。
     *
     * <p><b>为什么存类型 id 而不是下标</b>：类型表由控制器提供（{@link #getAvailableRecipeTypes()}），
     * 顺序与长度随控制器/档位/模组增删模式变化 ⇒ 存下标读档后会<b>指到别的模式</b>；存 id 则
     * "认得出就恢复、认不出就当未绑定"，与本机既有的 {@link #manualSlotTypes}
     * （同样是"存 id 不存下标"，见该字段的注释）同口径 ✓。
     *
     * <p><b>只做落盘影子副本</b>：不带 {@code @Persisted}、不带 {@code @DescSynced} ——
     * 写由 {@link #saveCustomPersistedData}、读由 {@link #loadCustomPersistedData}；
     * 模式页显示的仍是既有的 {@code @DescSynced modeCursorIndex}（客户端一致性靠它，零改动）。
     */
    private ResourceLocation boundModeType;

    /**
     * ★ 读档拿到了 {@link #boundModeType} 但<b>那时解析不出下标</b>（读档期控制器/类型表还没挂上，
     * {@link #getAvailableRecipeTypes()} 为空）⇒ 置真，等每 tick 的
     * {@link #applyBoundModeTypeIfPossible()} 在类型表就绪后落到 {@link #modeCursorIndex} 上。
     *
     * <p>为什么要分两步：只有"类型表<b>非空</b>且里面没有这个 id"才等于"类型已被移除"；
     * 空表时判失效会把有效绑定误杀 ✗（详见 {@link #applyBoundModeTypeIfPossible()} 的注释）。
     */
    private boolean boundModePendingResolve;

    /**
     * 每个样板槽识别出来的配方类型下标；{@code null} = 还没尝试识别（{@value #TYPE_NOT_TRIED}），
     * {@value #TYPE_UNKNOWN} = 试过但识别不出来，{@code >= 0} = 类型下标。
     *
     * <p>★ <b>为什么是 List 而不是现役机的 {@code int[]}</b>：惰性机的容量是<b>动态</b>的
     * （{@link #INITIAL_CAPACITY} 起步、按需 +{@link #MIN_GROW_STEP}、上限 {@link #maxPatternCount}），
     * 构造期按固定 128 建数组会一次吃掉整个惰性化的收益。这里只增不减，长度跟随
     * {@link #getCapacity()}（见 {@link #ensureRecipeTypeSlotCapacity()}）；<b>元素可以为 null</b>，
     * 一律用 {@link #recipeTypeCacheAt(int)} / {@link #setRecipeTypeCache(int, int)} 访问。
     */
    private final List<Integer> slotRecipeTypes = new ArrayList<>();

    /**
     * 手动指定的「样板槽位（0 起） → 配方类型 id」。
     * <b>优先于自动识别</b>，用于产物有歧义、自动识别不出来的样板；只在服务端用，走存档。
     * 存 id 而不是下标，这样换控制器 / 类型表顺序变了也不会指错 —— ★ 与现役机 {@code manualSlotTypes} 同口径。
     */
    private final Map<Integer, ResourceLocation> manualSlotTypes = new HashMap<>();

    /** 手动指定页面上「要给哪个槽位指定」的槽号（1 起）。同步给客户端，数值框跟着走。 */
    @DescSynced
    private int manualAssignSlot = 1;

    /** 上面对应槽位当前的手动指定（类型 id；空 = 没指定）。同步给客户端显示中文模式名。 */
    @DescSynced
    private String manualAssignTypeId = "";

    /**
     * ★ 0.3.2：该槽「<b>自动识别</b>」出来的类型 id（空 = 识别不出来）。
     * 与 {@link #manualAssignTypeId} 一起显示在模式页上 ⇒ 玩家一眼能看出
     * 「手动指定 切割机 ≠ 自检 车床」这种指错（0.3.1 那个「钻石卡死在缓冲里」的事故，
     * 根因就是一张"只有钻石的样板"被手动指定到了切割机模式）。
     */
    @DescSynced
    private String manualAssignAutoTypeId = "";

    // ── ★【① 冷槽重识别 · 运行时状态】全部是运行期字段：不 @Persisted、不进 NBT、不进同步包 ──

    /** 下一个允许重识别的 tick（{@code getOffsetTimer()} 口径）；见 {@link #selfHealColdSlotCaches()}。 */
    private long coldSlotHealNextTick;
    /** 已经开始重识别过的槽位（每个槽一生最多一次；防"永远识别不出来"的样板反复烧 CPU）。 */
    private final Set<Integer> coldSlotHealTried = new HashSet<>();
    /** 上一次重识别时该槽的样板（换了样板才允许再来一次）；与 {@link #coldSlotHealTried} 成对使用。 */
    private final Map<Integer, ItemStack> coldSlotHealPattern = new HashMap<>();
    /** 真正把冷槽写热过几次（诊断/取证用；不参与任何判定）。 */
    private int coldSlotHealWarmed;

    // ==================== P2-② 自动切换配方模式 ====================

    /** 轮询间隔计数（别每 tick 都扫槽位）。★ 照搬现役机 {@code autoSwitchCooldown}。 */
    private int autoSwitchCooldown;

    /** 上一次自动切过去的类型；-1 = 没有待观察的切换。★ 照搬现役机 {@code pendingAutoSwitchType}。 */
    private int pendingAutoSwitchType = -1;

    /**
     * ★ 0.3.4：当前模式「最后还有活」的时刻（gameTime）；-1 = 现在有活/刚清过。
     * 见 {@link #findModeWithWaitingStock(List, int, long)} 第一遍的「空转宽限期」。
     */
    private long activeModeBusySince = -1L;

    /** ★ 0.3.4：自动切模式诊断（只给日志用，不参与逻辑）：当前模式的样板数 / 其中有料数 / 其中未认配方数。 */
    private int diagActivePatterns;
    private int diagActiveStock;
    private int diagActiveCold;

    /** 待观察窗口的到期时间（gameTime）。★ 照搬现役机 {@code pendingAutoSwitchDeadline}。 */
    private long pendingAutoSwitchDeadline;

    /** 「切过去但没跑起来」的类型 → 拉黑到期时间（gameTime），防止两个模式来回横跳。 */
    private final Map<Integer, Long> autoSwitchBlockedUntil = new HashMap<>();

    /** {@code getAvailableRecipeTypes()} 的缓存（控制器与它返回的数组都没变就复用）。★ 照搬现役机同名字段。 */
    private IRecipeLogicMachine cachedTypesController;
    private GTRecipeType[] cachedTypesSource;
    private List<GTRecipeType> cachedTypes = List.of();

    /** 上一次打印过的自动切模式状态摘要：只有摘要<b>变化</b>时才再打印（现役机用它会话式打印，这里只在真的切换时打一条）。 */
    private String lastAutoSwitchSignature;

    /** ★ 0.3.2：逐槽「上次打印过的推样板诊断口径」，口径没变就不再刷日志（见 {@link #logPushDiagnostic(int)}）。 */
    private final Map<Integer, String> lastPushDiagnostic = new HashMap<>();

    // ==================== P1-②C 样板倍率 / 除率 / 神锻（视图改写） ====================
    //
    // 逐条照搬现役机 MoltenPatternAssemblyPartMachine 第 127-134（常量）/ 283-310（字段）/
    // 1637-1862（getAvailablePatterns → 缓存签名 → rebuildRewriteCache → rewritePattern → scaleStacks）
    // / 1864-1927（读写器）/ 840-946（推送侧一致性）。
    //
    // 语义一句话：**只改本仓向 AE 暴露的样板，仓里的样板物品一个字节都不动** ——
    // 改写产物是新的 AEProcessingPattern 对象，进 rewrittenPatterns 缓存；
    // 关掉开关或把数值调回 1 立刻恢复（下一条取样板时返回原表）。
    //
    // 惰性化差异（唯一一处结构性差异，见正文注释）：现役机的底表是 gtlcore 具体类自己维护的
    // slot2PatternMap（固定 128/640 槽），惰性机的底表是我们的 patternSlotMap（动态容量、
    // 只含真正存在的样板）⇒ 底表来源换成 baseAvailablePatterns()，其余逐条一致。

    /** 神锻样板模式倍率范围（1~30，默认 15）。★ 照搬现役机 {@code MIN/MAX/DEFAULT_FOA_MULTIPLIER}。 */
    public static final int MIN_FOA_MULTIPLIER = 1;
    public static final int MAX_FOA_MULTIPLIER = 30;
    public static final int DEFAULT_FOA_MULTIPLIER = 15;

    /** 样板倍率范围（1~100）。★ 照搬现役机 {@code MIN_SCALE} / {@code MAX_SCALE}。 */
    public static final int MIN_SCALE = 1;
    public static final int MAX_SCALE = 100;

    /**
     * ★★ 0.3.132【倍增交给 AE · 撤自造样板改写】★★
     * <p>{@code true}（默认）：{@link #getAvailablePatterns()} <b>不再把改写过的样板报给 AE</b>，
     * 一律返回仓里的原样板 ⇒ 倍增完全由 gtlcore 的 AE 自动翻倍
     * （{@code CraftingCpuLogicMixin} 的 {@code @Overwrite executeCrafting} + {@code CraftingPatternAutoExpand}）承担。
     * <p>为什么：原版 {@code MEPatternBufferPartMachine.getAvailablePatterns()} 直接返回槽表、**无改写层**；
     * 而我们的改写让样板声明量本身就乘了 N，与 AE 的 {@code operations} 放大**重复且相乘**。
     * <p><b>一行回退</b>：把本行改成 {@code false} ⇒ 逐字恢复"向 AE 报改写样板"的旧行为。
     */
    public static boolean PATTERN_REWRITE_DISABLED = true;

    /** 倍率：与神锻一样<b>两侧同倍</b> ×patternScale。★ 照搬现役机（同为 @DescSynced）。 */
    @DescSynced
    private int patternScale = 1;

    /** 除率：两侧 ÷patternDivScale。★ 照搬现役机。 */
    @DescSynced
    private int patternDivScale = 1;

    /**
     * true = 倍率作用在输出侧，false = 作用在输入侧。
     *
     * <p>★ 照搬现役机：**已废弃** —— 两侧同倍之后它不参与任何缩放计算，保留字段/读写/同步
     * 只为兼容旧存档与同步布局（界面上的侧别按钮也已移除）。它仍在缓存签名里（照搬现役机口径）。
     */
    @DescSynced
    private boolean isOutputMode = true;

    /** 神锻样板模式开关（两侧一起 ×倍率）。★ 照搬现役机。 */
    @DescSynced
    private boolean foaMode = false;

    /** 神锻样板模式倍率。★ 照搬现役机。 */
    @DescSynced
    private int foaMultiplier = DEFAULT_FOA_MULTIPLIER;

    /**
     * 改写后的样板缓存（样板或设置变化时失效）。★ 照搬现役机。
     * <p>惰性化：这是**纯内存缓存**，构造期不建、不知道样板时不建 ⇒ 对"构造期不按满容量建对象"零影响。
     */
    private List<IPatternDetails> rewrittenPatterns;

    /**
     * 改写后的样板 → 原样板。
     * <p><b>必须是 IdentityHashMap</b>（★ 照搬现役机）：AE 推回来的是我们改写出来的**那个对象实例**，
     * 而改写后的样板与原始样板在值语义上不同（数量变了）⇒ 只有按<b>身份</b>反查才能翻译回原样板、
     * 才找得到槽位。用 HashMap（值语义）在这里是错的。
     */
    private final IdentityHashMap<IPatternDetails, IPatternDetails> rewrittenToOriginal = new IdentityHashMap<>();

    /**
     * 改写缓存是否<b>已构建</b>。★ 照搬现役机 {@code rewriteCacheBuilt}。
     *
     * <p>不能拿 {@code rewrittenPatterns == null} 兼作"未构建"哨兵：打开了倍率/神锻、
     * 但样板本身恰好没有任何实质变化时（例如 mul==div），结果也会被写成 null
     * ⇒ 那样每次取样板都会把全部样板重新缩放+重编码（大仓就是每槽一次 NBT 编解码）。
     */
    private boolean rewriteCacheBuilt;

    /** 「未改写」取样板日志的节流签名（只在状态变化时打印一次，避免刷屏）。★ 照搬现役机。 */
    private String lastQuerySignature;

    /** 「AE 推送样板」诊断日志的最后记录 tick（1 秒最多一条）。★ 照搬现役机。 */
    private long lastPushLogTick = -1000;

    /**
     * ★ 0.3.132【撤自造 · 机器侧二次放大】是否已经就"倍率/神锻这次没生效"提醒过一次。
     * <p>含义与理由同现役机同名字段：撤掉放大后 {@code scalingApplies == true} 表示
     * "AE 推的是原样板 ⇒ 本次按 AE 声明的小量做，倍率/神锻在机器侧未生效"（功能静默失效），
     * 故每个实例只 WARN 一次（有界、不刷屏、不进存档）。
     */
    private boolean machineScaleDisabledWarned;

    /**
     * ★ 照搬现役机：<b>「料不够 / 被占用」提示的节流与去重</b>。
     * 30 秒最多一条；「可用=0 且在途=0」这种真死锁每个物品只喊一次（有界，惰性机也不能无限长）。
     */
    private static final int STUCK_LOG_INTERVAL_TICKS = 600;
    private static final int STUCK_WARNED_LIMIT = 64;
    private long lastStuckLogTick;
    private final Set<String> stuckWarned = new HashSet<>();

    /**
     * ★★ 0.3.132【C1 · 与现役机同构】★★ 惰性机这份"料不足 / 被占用"提示的分档与去重口径，
     * <b>逐条照搬现役机</b>（`MoltenPatternAssemblyPartMachine` 里同名常量/字段，取证见那边的注释）：
     * <ul>
     *   <li>旧口径把 `可用=0 && 在途=0` 直接判成「被占用且没有任何任务在合成它」并催玩家「全部取消」——
     *       实机取证（4 条 WARN 的 `isRequesting(key)` **全为 true**）证明那是**误报**，
     *       玩家照做只会把已排好的任务丢掉；</li>
     *   <li>新口径：**只有整个 AE 一个手工合成任务都没有**（`isRequestingAny()=false`）时才敢说是真占用。</li>
     * </ul>
     * 纯诊断性调整：只影响提示的**分档与频率**，不参与任何配方/并行/扣料判定。
     */
    private static final boolean STUCK_WARN_REPEAT_WHEN_TRACKED = true;

    /** 见 {@link #STUCK_WARN_REPEAT_WHEN_TRACKED}：该物品上一次报"料取不到"的游戏刻（用于 30 秒限流复报）。 */
    private final Map<String, Long> stuckWarnedTick = new HashMap<>();

    // ---------- 视图改写设置的存档键（★ 惰性机专有前缀，不与现役机格式撞车） ----------

    /** 见类 javadoc 的 forDrop 规则：**设置类字段**，掉落时也写（机器拎起来再放下不该丢倍率）。 */
    private static final String NBT_PATTERN_SCALE = "gtlmodular$lazy_pattern_scale";
    private static final String NBT_PATTERN_DIV_SCALE = "gtlmodular$lazy_pattern_div_scale";
    private static final String NBT_FOA_MODE = "gtlmodular$lazy_foa_mode";
    private static final String NBT_FOA_MULTIPLIER = "gtlmodular$lazy_foa_multiplier";
    private static final String NBT_OUTPUT_MODE = "gtlmodular$lazy_output_mode";

    // ==================== P1-②B 专属催化剂槽 ====================
    //
    // 逐条照搬现役机 MoltenPatternAssemblyPartMachine（它继承 gtlcore 的**具体类**）的那套做法，
    // 唯一的差别 = 「构造期一次建满的固定容量数组」（ItemStackTransfer[] / FluidTransferList[] / byte[]）
    // 换成「跟随 {@link #getCapacity()} 只增不减的 List」，并且**惰性建仓**：
    // 只有界面上真显示到的那一槽、或真被写入催化剂的那一槽，才会 new 出它的 9 格物品仓 + 9 罐流体仓。
    //
    // ── 每槽容量的字节码取证（`javap -p -c MEPatternBufferPartMachine`，工作区 _javap_p1b_concrete_ctor.txt）──
    //   · catalystItems：构造器 `Arrays.setAll(catalystItems, lambda$new$2)`；
    //     `lambda$new$2(int)`：`new ItemStackTransfer(**bipush 9**)` + `setFilter(lambda$new$1)`；
    //     `lambda$new$1(ItemStack)`：`Boolean.valueOf(!(stack.getItem() instanceof ProcessingPatternItem))`
    //     ⇒ 催化剂物品仓 = **9 格**，过滤器 = **拒绝处理样板**（与样板仓 AEUtils.PROCESS_FILTER 正好相反）。
    //   · catalystFluids：构造器 `Arrays.setAll(catalystFluids, lambda$new$4)`；
    //     `lambda$new$4(int)`：`new FluidTransferList(Stream.generate(lambda$new$3).limit(**9L**).toList())`；
    //     `lambda$new$3()`：`new FluidStorage(**16L** * FluidHelper.getBucket())`
    //     ⇒ 催化剂流体仓 = **9 罐 × 16 桶 = 16 000 mB**。
    //   · cacheRecipeCount：构造器 `Arrays.fill(cacheRecipeCount, **iconst_1**)` ⇒ 阈值默认 **1**。
    //   · 现役机这三个字段都挂着 LDLib 的 `@Persisted`（`javap -v` 的 RuntimeVisibleAnnotations 实证），
    //     靠托管字段机制自动存/读；惰性机它们是**动态增长的 List**，托管字段机制的数组/集合支持风险太大，
    //     所以照本文件既有的「逐槽表稀疏存」风格在 save/load 里自己写（见 saveCustomPersistedData ⑦）。

    /** 每槽催化剂物品仓的格数（字节码 `bipush 9`，见上）。 */
    private static final int CATALYST_ITEM_SLOTS = 9;
    /** 每槽催化剂流体仓的罐数（字节码 `limit(9L)`，见上）。 */
    private static final int CATALYST_FLUID_TANKS = 9;
    /** 每罐容量 = 16 桶（字节码 `16L * FluidHelper.getBucket()`，见上）。 */
    private static final long CATALYST_TANK_CAPACITY = 16L * FluidHelper.getBucket();
    /** 默认缓存阈值（字节码 `Arrays.fill(cacheRecipeCount, (byte) 1)`，见上）。 */
    private static final int DEFAULT_CACHE_RECIPE_COUNT = 1;
    /** 缓存阈值上限（阈值是 byte；界面上限别让它溢出成负数）。 */
    private static final int MAX_CACHE_RECIPE_COUNT = 127;

    /**
     * ★ 惰性机专有的催化剂存档键（**刻意加 {@code gtlmodular$lazy_} 前缀**）。
     * 现役机是靠 LDLib `@Persisted` 数组字段自动存的，格式与本键**不互通**（见类 javadoc 的坐标口径说明）。
     */
    private static final String NBT_LAZY_CATALYST_SLOTS = "gtlmodular$lazy_catalyst_slots";
    /** 上面那个 CompoundTag 里，每个槽的子键：催化剂物品仓 / 流体仓 / 缓存阈值。 */
    private static final String NBT_CATALYST_ITEMS = "items";
    private static final String NBT_CATALYST_FLUIDS = "fluids";
    private static final String NBT_CATALYST_COUNT = "count";

    /**
     * 逐槽催化剂物品仓。<b>长度只增不减</b>（跟随容量），元素允许为 {@code null} = 该槽还没建过仓。
     * 一律用 {@link #catalystItemTransferAt(int)} / {@link #ensureCatalystTables(int)} 访问。
     */
    private final List<ItemStackTransfer> catalystItems = new ArrayList<>();
    /** 逐槽催化剂流体仓（同上，元素可为 {@code null}）。 */
    private final List<FluidTransferList> catalystFluids = new ArrayList<>();
    /**
     * 逐槽缓存阈值（同上；缺省/越界 = {@value #DEFAULT_CACHE_RECIPE_COUNT}）。
     * 语义与具体类 {@code byte[] cacheRecipeCount} 一致，只是长度跟随动态容量。
     */
    private final List<Byte> cacheRecipeCount = new ArrayList<>();

    /** 「专属催化剂槽」页当前查看的样板槽位（1 起）。★ 照搬现役机同名字段（同一对同步字段）。 */
    @DescSynced
    private int catalystViewSlot = 1;

    /** 「专属催化剂槽」页是否展开。★ 照搬现役机同名字段。 */
    @DescSynced
    private boolean catalystPageOpen = false;

    /**
     * ★★ 0.3.7b（队长：「一打开反而看不见了…你确定你部署了吗」）★★
     * 把「当前查看槽」的催化剂内容打成一份 SNBT 文本同步给客户端（服务端写、客户端只读）。
     *
     * <p><b>为什么不走原版槽位同步、也不走控件级同步</b>：原版槽位内容<b>只在值变化时</b>重发，
     * 而"页面刚建好那一包"会在客户端还没建出槽控件时被丢弃、且**永不补发** ⇒ 页面一直空到内容变一次
     * （切页）为止；控件级同步又依赖 LDLib 的控件路由。而 {@code @DescSynced String} 是<b>本机已经
     * 实机验证过能同步</b>的字段类型（{@link #manualAssignTypeId} 用的就是它，日志里可见其同步记录）
     * ⇒ 走它最稳：内容只要变一次就会被推到客户端，与"菜单槽位表/控件是否已建好"完全无关。
     *
     * <p>内容 = {@code {items:[9×ItemStack NBT], fluids:[9×FluidStack NBT], count:byte}} 的
     * {@code CompoundTag.toString()}（SNBT）。
     */
    @DescSynced
    private String catalystViewPayload = "";

    /** 客户端读同步文本用（{@code catalystViewPayload} 只给服务端写，这里只暴露读）。 */
    public String getCatalystViewPayload() {
        return catalystViewPayload;
    }

    /**
     * ★★ <b>0.3.14 修复：「粘贴样板后，总成界面看到的还是那么多（要开关多次才看全）」</b> ★★
     *
     * <p><b>病根（读证结论）</b>：分页样板区的格子数是<b>每侧自己算</b>出来的 ——
     * {@link #createUIWidget()} 里把 {@code getCapacity()} 原样交给 gtlcore 的
     * {@code PaginationUIManager}，而后者只按这个数字建格子
     * （javap 实证：7 参构造器第 3 参直接落 {@code maxPatternCount} 字段，
     * {@code rebuildPatternSlots} 只建到 {@code min(页尾, maxPatternCount)}）。
     * 可是 {@code patternInventory} 只带 {@code @Persisted}、<b>没有</b> {@code @DescSynced}
     * ⇒ <b>客户端的容量永远停在构造期那一档 {@link #INITIAL_CAPACITY} = 32</b>，
     * 服务端却会随粘贴长到 48/64/128/640。
     * 于是：服务端的样板已经在 33 号及以后的槽里了、原版槽位同步也确实把它们发过来了，
     * <b>但客户端压根没建那些格子</b> ⇒ 画面上就是"看不见"，与开关几次无关（老注释那句
     * "重开界面即可看到新格子"是错的：重开还是按同一个恒为 32 的容量建）。
     *
     * <p><b>修法（照抄 24c 已验证路线：{@code @DescSynced} 字段级同步）</b>：
     * 服务端把"本机权威容量"写进这个字段（只在容量<b>变化</b>时写一次，见
     * {@link #publishPatternCapacity()}），客户端收到后在
     * {@link #applySyncedPatternCapacity()} 里把本侧 {@code patternInventory} 长到同一个容量
     * ⇒ 两侧的"格子数"与"菜单槽位数"重新一致 ⇒ <b>粘贴之后第一次打开界面就是完整的</b>。
     *
     * <p>为什么不用 {@code writeUpdateInfo/readUpdateInfo}（控件级同步）与 {@code addSlotListener}：
     * 前者本机实测无效（见 {@code _自研猪咪兼容清单.md} 第 24b/24c 节），
     * 后者在 1.20.1 不可行（{@code ServerPlayer} 不是 {@code ContainerListener}）。
     */
    @DescSynced
    private int syncedPatternCapacity = 0;

    /** 客户端读同步容量用（只暴露读，写入一律走服务端发布路径）。 */
    public int getSyncedPatternCapacity() {
        return syncedPatternCapacity;
    }

    /** 当前这一份 UI 里的「专属催化剂槽」页（客户端/服务端各一份）。★ 照搬现役机。 */
    transient CatalystAccessConfigurator activeCatalystPage;

    public LazyPatternAssemblyPartMachine(IMachineBlockEntity holder, int tier, int maxPatterns) {
        // gtlcore 的 ME 样板总成：IO.BOTH = 自带 ME 输出（与现役机器一致）
        super(holder, IO.BOTH);
        this.tier = tier;
        this.maxPatternCount = Math.max(RESERVE, maxPatterns);
        // ★ 初始容量：起步一档（INITIAL_CAPACITY），不再一次建满标称 128/640；
        //   空机器只实体化被访问到的槽，其余保持 null（惰性）。
        int initial = Math.max(RESERVE, Math.min(maxPatternCount, INITIAL_CAPACITY));
        this.patternInventory = new ItemStackTransfer(initial);
        this.patternInventory.setFilter(AEUtils.PROCESS_FILTER);
        this.patternInventory.setOnContentsChanged(this::onPatternInventoryChanged);
        for (int i = 0; i < initial; i++) {
            this.internalInventory.add(null);
            // ★ P2：逐槽配方类型表与容量一起起步（null = 还没尝试识别 TYPE_NOT_TRIED）
            this.slotRecipeTypes.add(null);
        }
        this.recipeHandler = new LazyPatternBufferRecipeHandler(this, IO.BOTH);
        // ★★ 产物回流唯一通道（0.2.1 修复）★★
        // 取证：MEItemInputHandlerBase.meHandleRecipeOutputInner() 并不把产物直接塞进 ME 网络，
        // 而是 machine.buffer.addTo(AEItemKey.of(stack), 数量)；真正推上网络的是具体类
        // MEPatternBufferPartMachine$Ticker（IGridTickable）里调的 AEUtils.reFunds(buffer, grid, actionSource)。
        // 而 Ticker 只存在于具体类里、基类没有 ⇒ 自研机器必须自己注册一个，否则
        // 「能合成、样板能存、但产物永远卡在 buffer 里不返回」（0.2.0 用户实测症状）。
        // 唤醒（TickingRequest 第 4 个参数 canBeAlerted=true ⇒ wakeDevice）由继承来的
        // MEIOTrait.notifySelfIO() 负责（MEIOPartMachine.onMainNodeStateChanged / MECraftHandler 都会调）。
        if (io == IO.BOTH) {
            getMainNode().addService(IGridTickable.class, new LazyBufferTicker());
        }
    }

    public int getMachineTier() {
        return tier;
    }

    /** 槽位硬上限。 */
    public int getMaxPatternCount() {
        return maxPatternCount;
    }

    // ==================== 惰性容量 ====================

    /**
     * 当前容量（= 逻辑槽位数 = {@code patternInventory} 的槽数）。
     * <p>这个值就是基类看到的 {@link #getInternalSlotCount()}，因此「容量」决定了遍历成本。
     */
    public int getCapacity() {
        ItemStackTransfer inventory = this.patternInventory;
        // 构造期（super() 内部）字段还没赋值，这里必须容错
        return inventory == null ? 0 : inventory.getSlots();
    }

    /**
     * ★ 扩容入口（单张插入 / 外部按需调用）：保证容量 <b>至少</b> {@code needed}，只增不减。
     *
     * <p>批量路径请用 {@link #growCapacityForBatchInsert(int)}——那个才会「一次扩到位」。
     */
    public void growCapacityFor(int needed) {
        int current = getCapacity();
        // ★ 0.2.9（审计发现 P0）：`needed <= current` 时必须**直接返回**。
        //   原来无论 needed 多小都会 `Math.max(needed, current + MIN_GROW_STEP)` ⇒ 每次读档、
        //   每次仓内容变化（onPatternInventoryChanged 会调它）都 +16，一路顶到 maxPatternCount
        //   —— 有界、不丢样板，但把"惰性"彻底抹平、存档 Size 与短表长度也跟着涨。
        //   "防抖动"的 +16 只在**确实需要扩容**时才用（下面那一句），语义不变。
        if (needed <= current) {
            return;
        }
        // 确实要长：单张插入也一档一档长（MIN_GROW_STEP），避免"塞一张扩容一格"的抖动；
        // 批量路径传入的 needed 可能远大于 current+STEP，此时按 needed 一次到位 ✓
        int target = Math.min(maxPatternCount, Math.max(needed, current + MIN_GROW_STEP));
        if (target <= current) {
            return;
        }
        resizePatternInventory(target);
        while (internalInventory.size() < target) {
            internalInventory.add(null);
        }
        // ★ P2：逐槽配方类型表跟着容量一起长（只增不减），与 internalInventory 严格同长；
        //   新槽一律填 null = TYPE_NOT_TRIED（还没尝试识别）。失败不影响扩容本身。
        try {
            while (slotRecipeTypes.size() < target) {
                slotRecipeTypes.add(null);
            }
        } catch (Throwable ignored) {
            // 极端情况下（表被外部改坏）也不许打断扩容
        }
        patternSlotMapDirty = true;
        // ★ 0.3.14：容量变了 ⇒ 把权威容量发布给客户端（修「粘贴后客户端分页样板区看不到新增的那些格子」）。
        //   落在"容量增长的唯一实现"上 ⇒ 单张插入 / 批量粘贴 / 对齐粘贴 / 读档补齐 全都被这一句覆盖。
        publishPatternCapacity();
    }

    /**
     * ★★ <b>批量一次性扩容（队长硬需求）</b>：把容量<b>一次</b>扩到
     * 「已用最高槽 + 1 + count + 预留」，保证一次操作能把 count 张样板全部落地，
     * <b>绝不允许「放 10 张要分几次」</b>。
     *
     * <p>入口：将来的自研复制/剪切工具 {@code pasteFromTag}（P1）、以及 {@link #insertPatterns(List)}。
     */
    public void growCapacityForBatchInsert(int count) {
        if (count <= 0) {
            return;
        }
        growCapacityFor(highestUsedSlot() + 1 + count + RESERVE);
    }

    // ---------- 0.3.14：容量的字段级同步（修「粘贴后界面看不到全部」） ----------

    /**
     * 服务端：把"本机权威容量"发布给客户端（<b>只在真的变了时</b>写字段 ⇒ 不产生每 tick 的网络量）。
     *
     * <p>调用点全部落在"容量可能变化"的三个既有出口上：
     * {@link #growCapacityFor(int)}（单张/批量扩容的唯一实现）、
     * {@link #clampCapacityToMax()}（读档截断）、以及界面构建前的兜底。
     * <b>不新增任何订阅</b>：写的是已有的 {@code @DescSynced} 机制（24c 已实测有效的那条路）。
     *
     * <p>只在服务端写（客户端写这个字段毫无意义，且会让同步值抖动）。
     */
    public void publishPatternCapacity() {
        if (isRemote()) {
            return;
        }
        try {
            int capacity = Math.min(getCapacity(), maxPatternCount);
            if (capacity == syncedPatternCapacity) {
                return;   // ★ 变更判定：没变就一个字都不写（不是每 tick 重建）
            }
            syncedPatternCapacity = capacity;
            GTLModular.LOGGER.info("[gtlmodular] 惰性机样板页: 服务端发布视图 槽={} 容量={} 签名={}",
                    countNonEmptyPatterns(), capacity, patternViewSignature());
        } catch (Throwable failure) {
            // 发布失败绝不影响机器本身（最坏结果 = 客户端仍按本侧容量建格子）
            GTLModular.LOGGER.warn("[gtlmodular] 惰性机样板页: 服务端发布容量失败（已忽略）pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    /**
     * 客户端：把同步来的权威容量落到本侧 {@code patternInventory} 上
     * ⇒ 分页样板区/菜单槽位两侧一致（这是"一打开就看得全"的<b>唯一</b>前提）。
     *
     * <p>两个刻意的设计：
     * <ol>
     *   <li><b>只增不减</b>：与 {@link #growCapacityFor(int)} 的既有纪律一致（本类容量本来只增不减）；
     *       若换档位把硬上限改小了，读档路径的 {@link #clampCapacityToMax()} 会在服务端收口，
     *       客户端多出来的空格子不显示任何东西，无害；</li>
     *   <li>直接用私有 {@link #resizePatternInventory(int)} 原地 resize（它内部<b>先备份再恢复</b>，
     *       且<b>不换对象</b> ⇒ LDLib 托管字段的引用与客户端那份镜像内容都不受影响）。</li>
     * </ol>
     * 不走 {@link #growCapacityFor(int)}，避免在客户端碰"只增不减表"的其它副作用。
     *
     * <p>调用点：客户端每 tick（{@code clientTick}）一次廉价整数比较 + 界面构建前再兜一次
     * （防"同步包比开界面晚一帧"⇒ 最坏情况是那一次界面少几格、重开即好，而不会反复开关都不好）。
     */
    public void applySyncedPatternCapacity() {
        if (!isRemote()) {
            return;
        }
        try {
            int published = syncedPatternCapacity;
            if (published <= 0) {
                return;   // 还没收到过同步（首次进世界/刚重载区块）⇒ 保持本侧现状
            }
            int target = Math.min(published, maxPatternCount);   // 夹到本机硬上限（存档/改配置都可能越界）
            int local = getCapacity();
            if (target <= local) {
                return;   // ★ 变更判定：没变就什么都不做（不是每 tick 重建）
            }
            resizePatternInventory(target);
            GTLModular.LOGGER.info("[gtlmodular] 惰性机样板页: 客户端应用视图 槽={} 容量={} 本侧原容量={}",
                    countNonEmptyPatterns(), target, local);
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性机样板页: 客户端应用容量失败（已忽略）pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    /**
     * 界面构建时"该建多少格子"的<b>权威口径</b>：两侧都用这个数字交给 {@code PaginationUIManager}。
     *
     * <p>服务端 = 本机真实容量（{@code getCapacity()}）；
     * 客户端 = 服务端同步过来的权威容量（收不到时退回本侧容量，保证不会比以前更差）。
     * ⚠ 客户端这里<b>顺手</b>调一次 {@link #applySyncedPatternCapacity()}：分页样板区的控件与
     * LDLib 的原版菜单槽位表都在"界面构建期"一次性建出来，同一次构建里先把本侧仓补齐，
     * 才能保证"两侧槽位数相同 ⇒ 原版槽位同步一个不漏"。
     */
    private int patternWidgetCapacity() {
        int local = getCapacity();
        if (isRemote()) {
            applySyncedPatternCapacity();
            local = getCapacity();
            int published = getSyncedPatternCapacity();
            if (published > local) {
                // 理论到不了这里（上面刚补齐）；真到了也按下界返回，绝不越硬上限
                local = Math.min(published, maxPatternCount);
            }
        }
        return Math.max(PATTERNS_PER_ROW, Math.min(local, maxPatternCount));
    }

    /** 诊断用：仓里非空样板张数（只读扫描，不做任何解码）。 */
    public int countNonEmptyPatterns() {
        try {
            int count = 0;
            int capacity = Math.min(getCapacity(), maxPatternCount);
            for (int i = 0; i < capacity; i++) {
                if (!patternInventory.getStackInSlot(i).isEmpty()) {
                    count++;
                }
            }
            return count;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /**
     * 诊断用：<b>紧凑</b>内容签名 —— {@code "容量:非空数:最高已用槽:每个非空槽下标"}。
     * 一行就能看出"服务端发出去的那份视图"到底是什么（张数/容量/落位），
     * 且成本 = 一次线性扫描 + 一个短字符串（<b>只在容量变化时</b>生成，不在 tick/帧路径上）。
     */
    public String patternViewSignature() {
        try {
            StringBuilder builder = new StringBuilder(48);
            int capacity = Math.min(getCapacity(), maxPatternCount);
            int nonEmpty = 0;
            builder.append(capacity).append(':');
            StringBuilder slots = new StringBuilder(32);
            for (int i = 0; i < capacity; i++) {
                if (!patternInventory.getStackInSlot(i).isEmpty()) {
                    nonEmpty++;
                    if (slots.length() > 0) {
                        slots.append(',');
                    }
                    slots.append(i + 1);   // 1 起，和界面上的槽号一致
                }
            }
            builder.append(nonEmpty).append(':').append(highestUsedSlot() + 1).append(':').append(slots);
            return builder.toString();
        } catch (Throwable ignored) {
            return "?";
        }
    }

    /**
     * 原地扩容：{@code ItemStackTransfer.setSize} 会丢弃内容，所以先备份再恢复，
     * <b>不换对象</b>（换对象会让 LDLib 托管字段的引用失效，风险不必要）。
     */
    private void resizePatternInventory(int newCapacity) {
        int old = patternInventory.getSlots();
        if (newCapacity <= old) {
            return;
        }
        ItemStack[] backup = new ItemStack[old];
        for (int i = 0; i < old; i++) {
            backup[i] = patternInventory.getStackInSlot(i);
        }
        patternInventory.setSize(newCapacity);
        for (int i = 0; i < old; i++) {
            if (!backup[i].isEmpty()) {
                patternInventory.setStackInSlot(i, backup[i]);
            }
        }
    }

    /**
     * ★ 0.2.8（审计发现）：把容量**截断**到 {@link #maxPatternCount}。
     *
     * <p>读数来自 LDLib {@code ItemStackTransfer.deserializeNBT}：它会读存档里的 {@code "Size"} 并
     * {@code setSize(Size)} —— 也就是**容量由存档决定**，可能大于本机硬上限（跨版本/改配置/外部工具导入）。
     * 一旦越界，我们的 {@code getInternalSlot(i≥max)} 会返回 null，而基类遍历是
     * {@code IntStream.range(0, getInternalSlotCount()).filter(InternalSlot::isActive)}
     * —— 方法引用在 null 接收者上直接抛 NPE。
     *
     * <p>截断会丢样板，所以**只在真的越界时**才做，并且打一条 WARN 说清丢了几张（不静默吞）。
     */
    private void clampCapacityToMax() {
        int capacity = patternInventory.getSlots();
        if (capacity <= maxPatternCount) {
            return;
        }
        ItemStack[] backup = new ItemStack[maxPatternCount];
        int dropped = 0;
        for (int i = 0; i < capacity; i++) {
            ItemStack stack = patternInventory.getStackInSlot(i);
            if (i < maxPatternCount) {
                backup[i] = stack;
            } else if (!stack.isEmpty()) {
                dropped++;
            }
        }
        patternInventory.setSize(maxPatternCount);
        for (int i = 0; i < maxPatternCount; i++) {
            if (!backup[i].isEmpty()) {
                patternInventory.setStackInSlot(i, backup[i]);
            }
        }
        while (internalInventory.size() > maxPatternCount) {
            internalInventory.remove(internalInventory.size() - 1);
        }
        GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：存档容量 {} 超过本机上限 {} ⇒ 已截断（丢弃 {} 张样板）pos={}",
                capacity, maxPatternCount, dropped, getPos());
        // ★ 0.3.14：截断也改变了权威容量 ⇒ 同步给客户端（否则客户端会按更大的旧容量建空格子）
        publishPatternCapacity();
    }

    /** 已用最高槽下标；没有任何样板时返回 -1。 */
    public int highestUsedSlot() {
        for (int i = getCapacity() - 1; i >= 0; i--) {
            if (!patternInventory.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private int firstEmptySlot() {
        int capacity = getCapacity();
        for (int i = 0; i < capacity; i++) {
            if (patternInventory.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    // ==================== 写入样板（P0 预留 API） ====================

    /**
     * 放一张样板：按需 +1 并保持 ≥{@value #RESERVE} 个空位。返回落地的槽号，失败（满仓 / 空物品）返回 -1。
     */
    public int insertPattern(ItemStack pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return -1;
        }
        int slot = firstEmptySlot();
        if (slot < 0) {
            if (getCapacity() >= maxPatternCount) {
                return -1;
            }
            slot = getCapacity();
            growCapacityFor(slot + 1);
        }
        patternInventory.setStackInSlot(slot, pattern.copy());
        // 单张插入：保持 ≥RESERVE 空位
        growCapacityFor(slot + 1 + RESERVE);
        onPatternInventoryChanged();
        return slot;
    }

    /**
     * ★ 批量写入样板：<b>先一次性扩容等到能装下全部 N</b>，再逐张放；返回实际放进去的张数。
     *
     * <p>这个方法就是「批量插入点」的样板：将来自研复制/剪切工具的 {@code pasteFromTag}
     * 照这个顺序写（先 {@link #growCapacityForBatchInsert(int)}，再写入）即可。
     */
    public int insertPatterns(List<ItemStack> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return 0;
        }
        growCapacityForBatchInsert(patterns.size());
        int inserted = 0;
        for (ItemStack stack : patterns) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int slot = firstEmptySlot();
            if (slot < 0) {
                if (getCapacity() >= maxPatternCount) {
                    break;
                }
                slot = getCapacity();
                growCapacityFor(slot + 1);
            }
            patternInventory.setStackInSlot(slot, stack.copy());
            inserted++;
        }
        growCapacityFor(highestUsedSlot() + 1 + RESERVE);
        onPatternInventoryChanged();
        return inserted;
    }

    // ==================== 基类要求的 5 个抽象方法 ====================

    /** 样板 → 槽号（找不到返回 null）。 */
    @Override
    protected Integer getSlotIndexForPattern(IPatternDetails pattern) {
        if (pattern == null) {
            return null;
        }
        ensurePatternSlotMap();
        Integer index = patternSlotMap.get(pattern);
        if (index != null) {
            return index;
        }
        // 值语义没命中时兜底：逐个解码 + equals / 身份比对（自定义样板可能只认身份）
        Level level = getLevel();
        if (level == null) {
            return null;
        }
        int capacity = getCapacity();
        for (int i = 0; i < capacity; i++) {
            ItemStack stack = patternInventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            IPatternDetails decoded = PatternDetailsHelper.decodePattern(stack, level);
            if (decoded != null && (decoded == pattern || decoded.equals(pattern))) {
                patternSlotMap.put(decoded, i);
                return i;
            }
        }
        return null;
    }

    /** 槽位总数 = 当前容量（惰性的关键：基类所有遍历都以此为界）。 */
    @Override
    protected int getInternalSlotCount() {
        // ★ 0.2.8（审计发现）：这里**必须**夹到 maxPatternCount。
        //   基类的遍历都是 `IntStream.range(0, getInternalSlotCount())` 再 `.filter(InternalSlot::isActive)`
        //   （方法引用在 null 接收者上会抛 NPE），而我们的 getInternalSlot(i) 在 i ≥ maxPatternCount 时返回 null。
        //   若存档里的 patternInventory.Size 大于本机上限（跨版本/改配置/外部工具导入），
        //   容量就会越界 ⇒ 基类遍历到越界下标 ⇒ NPE。夹住上界后，越界下标永远不会被遍历到。
        return Math.min(getCapacity(), maxPatternCount);
    }

    /**
     * 取槽 —— <b>惰性实体化的核心</b>：第一次访问下标 i 时才 {@code new InternalSlot(i)} 并缓存
     * （{@code InternalSlot} 是基类的内部类，外层实例由 javac 隐式传入）。
     *
     * <p>若 i 超出当前容量（例如外部直接按下标写入），则先把容量扩到 i+1 再实体化；
     * 越过 {@link #maxPatternCount} 硬上限时返回 null（基类在调用前都会做边界检查）。
     */
    @Override
    protected InternalSlot getInternalSlot(int index) {
        if (index < 0) {
            return null;
        }
        if (index >= getCapacity()) {
            if (index >= maxPatternCount) {
                return null;
            }
            growCapacityFor(index + 1);
            if (index >= getCapacity()) {
                return null;
            }
        }
        // 防御：托管字段（@Persisted patternInventory）由 LDLib 还原、与我们的 loadCustomPersistedData
        // 谁先谁后不由我们决定；这里保证「槽表长度 ≥ 容量」，否则容量被还原成 128 而槽表只有 32 时，
        // 后面那些槽会全部返回 null（样板在、槽状态却读不到）。
        int capacity = getCapacity();
        while (internalInventory.size() < capacity) {
            internalInventory.add(null);
        }
        if (internalInventory == null || index >= internalInventory.size()) {
            return null;
        }
        InternalSlot slot = internalInventory.get(index);
        if (slot == null) {
            // InternalSlot 是基类的内部类，外层实例（this）由 javac 隐式传入。
            // ★ P1-②B：改成照搬现役机的**逐槽子类**（它把逐槽催化剂表接进基类那组空实现，
            //   见 LazyPatternBufferInternalSlot 的逐条字节码对照）。**未实体化的槽依然不实体化**：
            //   这里仍然只在"第一次访问到 index"时才 new，构造期一个都不建。
            slot = new LazyPatternBufferInternalSlot(index);
            // ★★ 0.2.6 修复：「收到材料但主机不工作」的关键一挂 ★★
            // 取证（javap 逐指令）：
            //  ① InternalSlot.pushPattern(KeyCounter[]) 末尾是 `onContentsChanged.run()`
            //     —— AE 把料推进槽之后，槽会主动回调这个 Runnable；
            //  ② 猪咪的做法（MEPatternBufferPartMachine 构造器 lambda$new$5 / onLoad lambda$onLoad$7）：
            //     `slot.setOnContentsChanged(() -> { meFluidHandler.notifyListeners(); meItemHandler.notifyListeners(); })`
            //     —— 即"料变了就通知两个 ME 处理器"；
            //  ③ gtlcore 在 WorkableMultiblockMachineMixin.handleMETraitPart 里对这两个处理器
            //     注册了 addBufferChangedListener(recipeLogic::…)：这就是**主机唯一的唤醒来源**；
            //  ④ 而该 mixin 的 keepSubscribing() 常态返回 false（只有 FAIL_NO_ENOUGH_CWU_IN 才 true）
            //     ⇒ 空闲的 GTL 多方块**不会自己轮询配方**，全靠这条通知被叫醒。
            // 于是：料进来了、我们却不通知 ⇒ 主机永远不重算 ⇒「接收到材料但机器不工作」。
            // （notifyListeners() 定义在 gtlcore 的 NotifiableMERecipeHandlerTrait 上，javac 跨包判为
            //   "不可访问"，且没写进公开接口 IMERecipeHandlerTrait ⇒ 只能反射调，见 notifyMERecipeHandlers。）
            slot.setOnContentsChanged(this::notifyMERecipeHandlers);
            internalInventory.set(index, slot);
        }
        return slot;
    }

    /**
     * 通知两个 ME 配方处理器「缓冲内容变了」。
     *
     * <p>{@code notifyListeners()} 是 {@code public} 方法（javap 确认），但 javac 会报
     * {@code NotifiableMERecipeHandlerTrait.notifyListeners() is defined in an inaccessible class or interface}
     * —— 该类虽为 public，跨包仍判不可访问，且该方法<b>没有</b>出现在公开接口
     * {@code IMERecipeHandlerTrait}（那里只有 {@code addChangedListener}/{@code addBufferChangedListener}）。
     * 所以这里用反射调用（{@link #NOTIFY_LISTENERS} 只解析一次）。
     *
     * <p><b>失败不影响任何功能</b>：拿不到方法或调用抛异常都只吞掉（顶多是"主机少被叫醒一次"）。
     */
    private void notifyMERecipeHandlers() {
        Method notify = NOTIFY_LISTENERS;
        boolean fired = false;
        if (notify != null && recipeHandler != null) {
            try {
                notify.invoke(recipeHandler.getMeItemHandler());
                notify.invoke(recipeHandler.getMeFluidHandler());
                fired = true;
            } catch (Throwable ignored) {
                // 落到下面的兜底
            }
        }
        if (!fired) {
            // ★ 兜底（不碰那个不可访问的类）：直接捅宿主的 RecipeLogic。
            //   取证：gtlcore 注册进 addBufferChangedListener 的那个监听器，其 BootstrapMethods #2 的目标是
            //   `REF_invokeVirtual com/gregtechceu/gtceu/api/machine/trait/RecipeLogic.updateTickSubscription:()V`
            //   ⇒ 兜底调的就是同一个方法。反射那条若哪天失效（gtlcore 改版），这里仍能把主机叫醒。
            wakeHostRecipeLogic();
        }
    }

    /** 兜底唤醒：把宿主（我们所属的控制器）的配方逻辑叫醒 —— 与 gtlcore 注册的监听器同一个方法。 */
    private void wakeHostRecipeLogic() {
        try {
            for (IMultiController controller : getControllers()) {
                if (controller instanceof IRecipeLogicMachine logicMachine) {
                    RecipeLogic logic = logicMachine.getRecipeLogic();
                    if (logic != null) {
                        logic.updateTickSubscription();
                    }
                }
            }
        } catch (Throwable ignored) {
            // 兜底也失败 ⇒ 只能等主机自行轮询；绝不影响机器本身
        }
    }

    @Override
    protected boolean hasPatternInSlot(int slot) {
        return slot >= 0 && slot < getCapacity() && !patternInventory.getStackInSlot(slot).isEmpty();
    }

    /**
     * ★ 0.2.8（审计发现 B）：与具体类对齐 —— 具体类返回 {@code cacheRecipe[slot]}
     * （阈值默认 1，等价于"该槽已有配方缓存"）。
     *
     * <p>★ P1-②B：口径改成与具体类逐字一致的三段式
     * {@code 集合非空 && set.size() >= cacheRecipeCount[slot]}（字节码取证见
     * {@link LazyMEPatternTrait#hasCacheInSlot(int)}）。阈值默认 1 时与原来的「集合非空」完全等价，
     * 只有阈值被调高（或将来复制工具粘贴一份高阈值旧档）才不同。
     *
     * <p>唯一消费方是基类 {@code getActiveAndUnCachedSlots()} 的过滤（→
     * {@code getActiveAndUnCachedSlotsLimitContentsMap()} → gtlcore {@code GTRecipeLookupMixin}
     * 构造按槽索引的原料树）。若不覆写就一直是基类默认的 {@code false}
     * ⇒ 已缓存槽的余料也会被喂进原料树 ⇒ 与具体类的搜索面不一致。
     */
    @Override
    protected boolean hasRecipeCacheInSlot(int slot) {
        LazyMEPatternTrait trait = (LazyMEPatternTrait) getMETrait();
        if (trait == null) {
            return false;
        }
        ObjectSet<GTRecipe> cached = trait.slotRecipes(slot);
        return cached != null && !cached.isEmpty() && cached.size() >= cacheRecipeCountAt(slot);
    }

    // ================= 查找侧逐槽隔离（★ 2026-10-15 新增：纯覆写，零 mixin） =================
    // 依据：_census/逐槽隔离_查找侧方案.md（§1.2 / §3 / §4）。★ 与现役机 MoltenPatternAssemblyPartMachine
    // 里的同名段**逐条对齐**（那边是 int[]，这边是只增不减的 List + 动态容量，读写都走既有访问器）。

    /**
     * ★ <b>查找侧收窄总开关</b>：{@code true}（<b>默认开启，与下一行代码的实际值一致</b>）⇒
     * {@link #lookupNarrowingModeIndex()} 的判据生效；{@code false} ⇒ 两个覆写的判定入口立刻
     * 退回 {@code super} 的全量口径 ⇒ 行为与加覆写之前<b>逐字一致</b>（零行为差异，可作第一手回退）。
     *
     * <p>⚠️ <b>2026-10-27 文档订正（只改注释、不改行为）</b>：本 javadoc 原文写的是
     * 「{@code false}（<b>默认，出厂关闭</b>）」，与下面那行的实际值 {@code true} <b>不符</b> ——
     * 该「文档与代码不一致」已登记为既存事实（{@code _census/收窄与多类型_冲突分析.md} §⑦-3）。
     * 现按代码订正为「默认开启」；<b>本行代码一个字未动</b>（仍是 {@code true}），
     * 回退方式依旧是把它置 {@code false} ✓。
     * ⚠️ 两台总成（本类 与 {@link MoltenPatternAssemblyPartMachine}）的这个开关<b>必须同时改</b>，
     * 否则两机行为不一致（{@code _census/逐槽隔离_查找侧_实施记录.md:790} 的既有纪律）。
     *
     * <p><b>当初为什么建议默认关</b>：方案 §4 步骤 4 与 §7-R5 建议「第一版默认关、先观察一轮」——
     * 收窄会改变「一台总成里混装多模式样板」这种旧玩法的匹配面（那是设计目标，但会打断旧存档用法）
     * ⇒ 由队长实测后再决定是否翻转默认值；现值为 {@code true}（已翻转）✓。
     *
     * <p><b>与结果侧口径无关</b>：{@link PatternSlotIsolationGuard#STRICT_SLOT_ISOLATION} 是结果侧闸门（默认开），
     * 本开关只管查找侧收窄，两者独立、互不覆盖 ✓。
     */
    public static boolean LOOKUP_SIDE_SLOT_NARROWING = true;

    /**
     * 「本次查找该按哪个模式收窄」的**只读**解析结果：
     * <ul>
     *   <li>{@code -1} = <b>本次不收窄</b>（开关关 / 类型表空 / R6 模式不一致 / 本总成模式取不到）⇒ 调用方原样返回 {@code super}；</li>
     *   <li>{@code >= 0} = 该模式下标。</li>
     * </ul>
     *
     * <p><b>这个字段存在的唯一目的 = 性能</b>：{@link #narrowSlotsToCurrentMode(int[])}
     * 在 {@code getActiveSlots()} / {@code getActiveAndUnCachedSlots()} 的**每个槽**上跑一次，
     * 而解析模式要碰 {@link #getAvailableRecipeTypes()}（内层 {@code getControllers()} + {@code getRecipeTypes()}）。
     * 每次调用只算一次并缓存在这里，避免「每槽 × 每 tick」重算。**它只是缓存，不落盘、不同步**（无 @Persisted / @DescSynced）
     * ⇒ 存档格式零变化 ✓。
     */
    private int lookupNarrowingModeIndex = -1;

    /**
     * ★ <b>只读</b>解析「本总成自己负责的模式」，<b>只认本总成自己的两档指示，绝不回落到控制器当权威</b>。
     *
     * <p>判据顺序（与 {@link #currentModeIndexForNaming(int)} 的 ① ② ③ <b>逐条同源</b>，
     * 保证「界面显示哪个模式」与「实际限哪个模式」是同一个下标 ✓）：
     * <ol>
     *   <li>{@link #modeCursorIndex}（模式页游标 = 本总成自己的模式，权威）；</li>
     *   <li>{@link #activeRecipeTypeIndex}（本机缓存下标）。</li>
     * </ol>
     *
     * <p><b>为什么不用 {@link #currentModeIndexForNaming(int)} 的第 ④ ⑤ 档</b>：
     * 那两档的语义是「宁可显示主机模式 / 宁可显示第 0 个」，用于**命名**是合适的兜底（显示错总比不显示好），
     * 但用于**收窄参与槽集合**是危险动作（收窄错 = 握着料不动 ✗）⇒ 这里一律 <b>fail-open</b> 返回 {@code -1}，
     * 让调用方退回 {@code super} 的全量口径 ✓（方案 §4 步骤 1 的「取不到 ⇒ -1 ⇒ 退回 super」）。
     *
     * @return 模式下标（{@code >= 0}）；{@code -1} = 取不到 ⇒ 不收窄
     */
    private int isolationOwnModeIndexReadOnly() {
        try {
            List<GTRecipeType> types = getAvailableRecipeTypes();
            int size = types == null ? 0 : types.size();
            if (size <= 0) {
                return -1;   // 类型表还没就绪（刚成形 / 读档期）⇒ 不收窄
            }
            if (modeCursorIndex >= 0 && modeCursorIndex < size) {
                return modeCursorIndex;   // ① 权威：本总成的模式页游标
            }
            if (activeRecipeTypeIndex >= 0 && activeRecipeTypeIndex < size) {
                return activeRecipeTypeIndex;   // ② 本机缓存下标
            }
            return -1;   // 都取不到 ⇒ fail-open
        } catch (Throwable failure) {
            return -1;   // 只读解析也不许把查找路径掀翻
        }
    }

    /**
     * ★ <b>R6 兜底闸：本次查找「该不该按本总成的模式收窄」</b>。
     *
     * <p><b>R6 是什么</b>（方案 §7-R6）：控制器的自动切模式
     * （{@link #autoSwitchRecipeModes()} → 找有待料的模式 → {@code lockRecipeTypeForSlot(int)}）
     * 改的是**控制器**的 {@code getActiveRecipeType()}，而本方案看的是**本总成自己的** {@link #modeCursorIndex}
     * —— 两者按既有注释是**明确解耦**的（见 {@link #currentModeIndexForNaming(int)} 的「注意」段）。
     * 若控制器私底下切到了别的模式、而本总成模式不跟随 ⇒ 按本总成模式收窄会让「控制器正在跑的那个模式」
     * 的样板槽**被挡在原料树之外** ⇒ 表现就是「<b>握着料不动</b>」✗。
     *
     * <p><b>兜底取舍（三档，我们选最保守的③）</b>：
     * <ol>
     *   <li>按「并集」（本总成模式 ∪ 控制器模式）参与 ⇒ 安全但等于不收窄，且实现要两套判据（复杂）；</li>
     *   <li>按控制器模式收窄 ⇒ 直接假设控制器说了算，与本方案「每个总成绑定一个模式」的口径矛盾 ✗；</li>
     *   <li><b>★ 本实现：不一致 ⇒ 一律放行（不收窄）</b> ⇒ 退回 {@code super} 的全量口径，
     *       最多少收窄一次，<b>绝不</b>因为模式不一致而把槽挡在门外 ✓。
     *       代价：模式不一致的那段时间里跨模式样板又会被喂进查找 —— 但**结果侧闸门全在**
     *       （类型闸 / 身份闸 / {@link PatternSlotIsolationGuard}）⇒ 不会串货，只是少省一点 ✓。</li>
     * </ol>
     *
     * <p><b>本闸还要求「控制器存在且模式合法可解析」才允许收窄</b>：拿不到控制器（刚放下 / 未成形 /
     * 控制器没挂上）时按保守放行 —— 方案 §2 的机制链条（查找按 {@code holder} 的能力集展开）在
     * 「没有控制器」时根本无从判断，此时收窄纯属冒险 ✗。
     *
     * @return {@code >= 0} = 本次按该模式收窄；{@code -1} = <b>不收窄</b>（放行全量）
     */
    private int lookupNarrowingModeIndex() {
        lookupNarrowingModeIndex = -1;   // 先清缓存（任何异常路径上都不留上一 tick 的陈旧值）
        if (!LOOKUP_SIDE_SLOT_NARROWING) {
            return -1;   // ★ 关 = 逐字等价于加本覆写之前
        }
        try {
            int own = isolationOwnModeIndexReadOnly();
            if (own < 0) {
                return -1;   // 本总成模式取不到 ⇒ 不收窄（fail-open）
            }
            List<GTRecipeType> types = getAvailableRecipeTypes();
            IRecipeLogicMachine controller = firstRecipeLogicController();
            if (controller == null || types == null || types.isEmpty()) {
                return -1;   // 没有控制器 / 类型表空 ⇒ 无从判定一致性 ⇒ 不收窄
            }
            int live = controller.getActiveRecipeType();
            if (live < 0 || live >= types.size()) {
                return -1;   // 控制器模式不可解析 ⇒ 不收窄
            }
            if (live != own) {
                // ★ R6：控制器正在跑的模式 ≠ 本总成负责的模式 ⇒ 收窄会让控制器"握着料不动" ✗
                //   ⇒ 本 tick 一律放行（不收窄），交由结果侧闸门保证不串。
                return -1;
            }
            if (controller instanceof MultiTypeLookupHost multiTypeHost && multiTypeHost.searchesAllRecipeTypes()) {
                // ★ R6′（2026-10-27）：控制器这一轮会搜「多个类型」（复合型 / 多类型搜索）⇒ 本轮不收窄。
                //   语义：**收窄面 = 本轮会被搜的类型集合**，而不是「单一激活模式」（收窄的合法前提是
                //   「控制器这一轮只搜一个类型」，复合型机器破掉了这个前提 ⇒ 前提不成立就不该收）。
                //   目标（队长的原话口径）：**保障多类型并发** —— 让跨配方线程能各跑不同模式的配方，
                //   而不是掐死它们 ✗。不改这一条会怎样：非激活类型的**冷**样板槽（没有 recipes2SlotsMap
                //   快路径）被摘出查找面，而冷槽只能走 MEPatternRecipeHandlePart.handleRecipe 的
                //   「遍历 getActiveSlots()」回退分支 ⇒ 拿不到第一次匹配 ⇒ 该类冷样板永久饥饿
                //   （热样板仍走快路径，故表现为「看似两类、只吃一类」）✗。
                //   代价：本总成挂在多类型主机上时失去收窄的性能收益（多类型本来就要全遍历，无法两全）。
                //   回退：删掉主机的 implements 即逐字回到此处不放行（见 MultiTypeLookupHost javadoc 三档）。
                return -1;
            }
            lookupNarrowingModeIndex = own;   // 缓存给逐槽判据用（见字段注释）
            return own;
        } catch (Throwable failure) {
            lookupNarrowingModeIndex = -1;
            return -1;   // 任何意外 ⇒ 不收窄
        }
    }

    /**
     * ★ <b>参与槽集合的收窄规则（唯一实现）</b> —— {@link #getActiveSlots()} 与
     * {@link #getActiveAndUnCachedSlots()} <b>共用同一份判据</b>（方案 §4 步骤 3 R1：
     * 只改一个会出现「配平扫描看得见、匹配却看不见」的怪象 ✗）。
     *
     * <p><b>硬纪律（性能，方案 §6.3 R2）</b>：本方法<b>只读缓存值</b> ——
     * 手动指定表 {@link #manualSlotTypes}（经 {@link #manualTypeIndexOf(int, List)} 折算下标）
     * 与逐槽识别缓存 {@link #slotRecipeTypes}（经 {@link #recipeTypeCacheAt(int)} 纯读）。
     * <b>绝不</b>调用 {@link #recipeTypeOfSlot(int)} / {@link #autoDetectTypeOfSlot(int)} /
     * {@link #detectRecipeType(int, List)} 这些**会触发表扫描**（逐类型 × 该类型全部配方 ×
     * {@code patternIdentityMatches}）的解析方法 ✗ —— 本方法在每个槽上都跑，一旦误调就是
     * 「每 tick × 每槽 全表扫描」✗（比不收窄还糟）。
     * 缓存是"只写不主动读"的：识别与推送路径（{@code recipeTypeOfSlot} / {@code autoDetectTypeOfSlot}
     * / {@code assignManualType} / 推送到槽）已经把它填好，这里只**读**，绝不改写它 ✓。
     *
     * <p><b>哨兵放行清单（方案 §4 步骤 2 / §7-R4：宁可少收窄，绝不误挡 ✗）</b>：
     * {@code -1}（{@link #TYPE_NOT_TRIED}，还没试过识别，也包含 {@code null} 元素与越界读取）、
     * {@link #TYPE_UNKNOWN}（试过但识别不出来）、任何不在 {@code [0, size)} 里的越界值、
     * 缓存表未初始化 / 长度不匹配 / 下标越界 —— <b>一律「放行」（不参与收窄）</b>。
     *
     * <p><b>与现役机唯一的差别</b>：那边读的是定长 {@code int[] slotRecipeType}（长度不匹配就整份重建），
     * 这边读的是**只增不减**的 {@link #slotRecipeTypes}（元素可为 {@code null}）⇒ 一律经
     * {@link #recipeTypeCacheAt(int)} 读，<b>绝不</b>在这里调 {@link #ensureRecipeTypeSlotCapacity()}
     * 去顺手扩容（那是写动作，不是只读）✓。
     *
     * @param base {@code super} 给出的原始参与槽集合（永远非 null，可直接遍历）
     * @return 收窄后的新数组；<b>任何不确定情形都原样返回 {@code base}</b>（不返回 null —— 上游会直接遍历它）
     */
    private int[] narrowSlotsToCurrentMode(int[] base) {
        if (base == null || base.length == 0) {
            return base;   // 原样返回（绝不给 null：上游 getActiveAndUnCachedSlotsLimitContentsMap 直接遍历）
        }
        if (lookupNarrowingModeIndex() < 0) {
            return base;   // ★ 开关关 / 模式取不到 / R6 不一致 ⇒ 逐字等价于 super
        }
        int kept = 0;
        try {
            for (int i = 0; i < base.length; i++) {
                if (slotParticipatesInLookup(base[i])) {
                    base[kept++] = base[i];
                }
            }
        } catch (Throwable failure) {
            // 逐槽判据出任何意外 ⇒ 保守回落到"全量参与"（绝不让查找面被意外掏空 ✗）
            return base;
        }
        if (kept == base.length) {
            return base;   // 一个都没被收窄 ⇒ 原样交回（稳态零分配）
        }
        return Arrays.copyOf(base, kept);
    }

    /**
     * 该槽**是否参与**本次（按模式收窄后的）查找 —— 见 {@link #narrowSlotsToCurrentMode(int[])} 的纪律说明。
     *
     * <p><b>只读缓存</b>：① {@link #manualSlotTypes}（手动指定优先，与 {@link #recipeTypeOfSlot(int)} 同优先级）
     * → ② {@link #slotRecipeTypes}。**绝不**触发表扫描 ✗。
     *
     * @return true = 参与（放行）；false = 已知类型与本次模式不同 ⇒ 不参与
     */
    private boolean slotParticipatesInLookup(int slot) {
        try {
            List<GTRecipeType> types = getAvailableRecipeTypes();
            if (types == null || types.isEmpty()) {
                return true;   // 类型表空 ⇒ 放行
            }
            // ① 手动指定优先（只查 id 表 + 与类型表比 id，不扫配方表）
            int manual = manualTypeIndexOf(slot, types);
            if (manual >= 0) {
                return manual == lookupNarrowingModeIndex;
            }
            // ② 逐槽识别缓存（**纯读**；未初始化 / 未试过 / 越界 一律放行）
            int cached = recipeTypeCacheAt(slot);
            if (cached == TYPE_UNKNOWN) {
                return true;   // 试过但识别不出来 ⇒ 放行（R4：绝不把认不出的槽误锁在门外）
            }
            if (cached == TYPE_NOT_TRIED) {
                return true;   // 还没试过识别（含 null 元素 / 越界读取）⇒ 放行（**绝不顺手触发识别** ✗）
            }
            if (cached < 0 || cached >= types.size()) {
                return true;   // 哨兵/越界 ⇒ 放行
            }
            return cached == lookupNarrowingModeIndex;
        } catch (Throwable failure) {
            return true;   // 只读判据异常 ⇒ 放行（fail-open）
        }
    }

    /**
     * ★ <b>查找侧「参与槽集合」收窄（第一处）</b> —— 本方法就是「哪些槽的料参与本次配方查找/匹配」的唯一口径入口
     * （方案 §1.1-S7 / §1.3 / §3；调用方取证：{@code MEItemInputHandlerBase.getActiveSlots()} →
     * {@code getMachine().getActiveSlots()}、{@code MEFluidHandlerBase} 同款、{@code MEPatternRecipeHandlePart.handleRecipe} 的回落分支）。
     *
     * <p><b>签名取证（确是 protected 且非 final ⇒ 可覆写）</b>：
     * {@code _census/_javap_simple_gtlcore_bufferbase.txt:766} {@code protected int[] getActiveSlots();}
     * <p><b>先例</b>：星律 {@code RecipeTypePatternBufferPartMachine:1102}（同类覆写）；
     * Enhancedcore {@code IvBufferLifecycleMixin.java:57-60}（同目标，用 mixin 是因为要管别人的基类）。
     *
     * <p><b>为什么它 = 「查找侧」</b>：查找的作用域由控制器的能力代理（{@code holder.getCapabilitiesProxy()}）决定，
     * 而 ME 那份能力的**参与槽范围**就是这两个方法给的
     * （{@code _javap_simple_handlerbase.txt:41-46}、{:48-105}；{@code _javap_simple_upkeep_after.txt:299-312}）
     * ⇒ 把返回集合收成「本总成当前模式的槽」= 按模式限查找 ✓（"逐槽"仍由结果侧闸门保证，见 §5）。
     *
     * <p><b>本机特有的注意</b>：{@code super} 的遍历是
     * {@code IntStream.range(0, getInternalSlotCount()).filter(InternalSlot::isActive)}，
     * 而本机 {@link #getInternalSlotCount()} = {@code min(getCapacity(), maxPatternCount)}（惰性容量）
     * ⇒ 收窄**不会**改变这一层的遍历成本（方案 §6.3 的反向代价，与现役机同）。
     */
    @Override
    protected int[] getActiveSlots() {
        return narrowSlotsToCurrentMode(super.getActiveSlots());
    }

    /**
     * ★ <b>查找侧「参与槽集合」收窄（第二处）</b> —— 与 {@link #getActiveSlots()} <b>必须一起改</b>
     * （方案 §4 步骤 3 / §7-R1：只改一个 ⇒ 两处口径不一致 ⇒「配平扫描看得见、匹配却看不见」✗）。
     *
     * <p><b>签名取证</b>：{@code _census/_javap_simple_gtlcore_bufferbase.txt:778}
     * {@code protected int[] getActiveAndUnCachedSlots();}
     *
     * <p><b>它为什么也在「查找侧」这条链上</b>：它是
     * {@code getActiveAndUnCachedSlotsLimitContentsMap()} 的来源（{@code _javap_simple_handlerbase.txt:48-105}，
     * 调用在 {@code :63}），后者被**配平/维持（upkeep）扫描**消费（{@code _javap_simple_upkeep_after.txt:299-312}），
     * 而这条链的终点就是 gtlcore {@code GTRecipeLookupMixin} 构造的「按槽索引的原料树」
     * —— 本类 {@link #hasRecipeCacheInSlot(int)} 的既有注释已经把这条链写清楚了（:1413-1416）⇒ 这条链就是查找侧本身 ✓。
     */
    @Override
    protected int[] getActiveAndUnCachedSlots() {
        return narrowSlotsToCurrentMode(super.getActiveAndUnCachedSlots());
    }
    // ================= 查找侧逐槽隔离（新增段结束） =================

    @Override
    public Pair<IMERecipeHandlerTrait<Ingredient, ItemStack>,
            IMERecipeHandlerTrait<FluidIngredient, FluidStack>> getMERecipeHandlerTraits() {
        return Pair.of(recipeHandler.getMeItemHandler(), recipeHandler.getMeFluidHandler());
    }

    // ==================== AE 侧（ICraftingProvider / PatternContainer） ====================

    /**
     * AE 推一张样板进来。
     *
     * <p>★ P1-②C 起完整照搬现役机 {@code pushPattern}（MoltenPatternAssemblyPartMachine 840-1043）的四段：
     * <ol>
     *   <li><b>(a) 反查原始样板</b>：AE 推回来的可能是<b>我们改写过的那个对象</b>
     *       （改写是"给 AE 看的视图"，AE 缓存的就是改写后的实例）⇒ 先用 {@code rewrittenToOriginal}
     *       按<b>身份</b>翻译回仓里那张原样板，之后认槽位/锁模式一律用原样板；</li>
     *   <li><b>(b) 存量不足判读</b>：网络可用量 &lt; 本次需求（含机器侧放大）时<b>只记一条限流日志</b> ——
     *       ★ 现役机的注释写着"直接返回 false"，但它的代码里<b>没有</b>那句 return（全方法只有末尾一处 return），
     *       所以真正生效的语义是"只留痕、不拒收"；惰性机照<b>代码</b>搬，理由见方法内注释；</li>
     *   <li><b>(c) 机器侧兜底放大</b>：AE 的合成服务会长期缓存"可用样板表"，改倍率/神锻之后它仍可能
     *       把【原样板】推回来 ⇒ 这时在机器侧按有效倍率把推送量放大（gtlcore 按推送量决定做几倍）；</li>
     *   <li><b>(d) 找槽 → 锁模式 → 交给父类</b>：0.3.1 就有的三步，一字未改（只是"认槽位"现在吃的是原样板）。</li>
     * </ol>
     *
     * <p>纪律：整条推送路径上的新逻辑（反查/存量判定/放大/诊断）全部包在 try/catch(Throwable) 里，
     * <b>绝不外抛</b>；拿不到就 fail-open（当作"没启用改写"继续走原路）。
     */
    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        // ===== (a) 反查原始样板（★ 照搬现役机 839-841）=====
        IPatternDetails original = patternDetails;
        boolean aePushedOriginal = true;
        int mul = 1;
        int div = 1;
        boolean scalingApplies = false;
        boolean machineSideScaled = false;
        try {
            // 按【身份】反查：AE 推回来的是改写出来的那个实例，值语义（数量已变）对不上
            original = rewrittenToOriginal.getOrDefault(patternDetails, patternDetails);
            aePushedOriginal = original == patternDetails;
            mul = effectiveMul();
            div = effectiveDiv();
            scalingApplies = aePushedOriginal && isRewriteEnabled()
                    && inputHolder != null && (mul != 1 || div != 1);
        } catch (Throwable ignored) {
            original = patternDetails;
            aePushedOriginal = true;
            scalingApplies = false;
        }
        Level pushLevel = getLevel();

        // ===== (b) 存量不足判读（★ 照搬现役机 847-928 的判读与限流日志；**不拒收**，见段末说明）=====
        // 现役机这段注释的用意：解决"AE 把中间品搬进合成 CPU 交给供应器、而 gtlcore 样板总成
        // 只能从网络取料"造成的死锁 —— 思路是网络可用量 < 本次需求（含机器侧放大）时拒收，
        // 让 AE 把这次派发当失败、释放/退回预留的料、稍后重试，于是大单能一批批滚动推进。
        // 但现役机代码里并没有真的 return false（见下），所以这里先把判读与"是哪种卡"的分层日志搬全。
        // 注意：无限流体/物品元件返回 Long.MAX_VALUE，天然通过；查询失败则 fail-open 不拦。
        try {
            boolean stockShort = false;
            String shortName = "";
            long shortNeed = 0;
            long shortHave = 0;
            AEKey shortKey = null;
            var stockGrid = getMainNode().getGrid();
            if (stockGrid != null && inputHolder != null) {
                KeyCounter avail = stockGrid.getStorageService().getInventory().getAvailableStacks();
                outer:
                for (KeyCounter counter : inputHolder) {
                    if (counter == null) {
                        continue;
                    }
                    for (var entry : counter) {
                        // ★ 0.3.132【撤自造 · 与 (c) 同步】同现役机：真实消费量就是 AE 推来的原量，
                        //   不再按"放大后的量"比库存（否则会报出与实际消费不符的"料不够"）。
                        //   一行回退：改回 `scalingApplies ? Math.max(1, entry.getLongValue() * mul / div) : entry.getLongValue()`。
                        long need = entry.getLongValue();
                        long have = avail.get(entry.getKey());
                        if (have < need) {
                            stockShort = true;
                            shortNeed = need;
                            shortHave = have;
                            shortKey = entry.getKey();
                            // 用本地化名（游戏是中文时就是"石脑油"这种），附上 id 便于排查
                            try {
                                shortName = "『" + entry.getKey().getDisplayName().getString() + "』("
                                        + entry.getKey().getId() + ")";
                            } catch (Throwable ex) {
                                shortName = entry.getKey().getId().toString();
                            }
                            break outer;
                        }
                    }
                }
            }
            if (stockShort && pushLevel != null) {
                // 分层：查 AE 的**在途量**（getRequestedAmount，被合成任务预定的量）——
                //   在途 > 0 ⇒ 上游还在做，等它就好（INFO，30 秒一条）；
                //   在途 = 0 且可用 = 0 ⇒ **没有任何任务在合成它** = 真死锁占用（WARN，每个物品只喊一次）。
                long nowTick = pushLevel.getGameTime();
                long inFlight = 0;
                // ★★ 0.3.132【C1 · 与现役机同构】★★ 多读两格 AE 侧读数，用来分档：
                //   `isRequesting(key)` = "这个 key 有没有在等/在做"（AE2 自己的方法，gtlcore @Inject 扩展，
                //     见 `CraftingServiceMixin:284-295`）；
                //   `isRequestingAny()`  = "整个 AE 到底还有没有手工合成任务在跑"（同文件 `:297-308`，
                //     聚合实现 `TransfiniteComputationArrayMachine:407-417`）。
                //   为什么要这两格：`可用=0 && 在途=0` **分不出**"AE 真没任务"与"料已交付未消费的空窗期"——
                //   实机取证证明后者才是常见情形，而旧文案把它说成了前者。
                //   纯只读、零副作用；查不到就保持 false（fail-safe，绝不自己造读数）。
                boolean aeRequesting = false;
                boolean aeRequestingAny = false;
                try {
                    if (shortKey != null) {
                        inFlight = stockGrid.getCraftingService().getRequestedAmount(shortKey);
                        aeRequesting = stockGrid.getCraftingService().isRequesting(shortKey);
                        aeRequestingAny = stockGrid.getCraftingService().isRequestingAny();
                    }
                } catch (Throwable ignored) {
                    // 查不到就不报在途量 / 不报追踪态（绝不因为查询失败而造出一个读数）
                }
                // ★ 新口径：**只有整个 AE 一个手工合成任务都没有**时才敢说是真占用。
                boolean trueDeadlock = shortHave <= 0 && inFlight <= 0 && aeRequestingAny;
                String stuckId = shortKey == null ? "?" : String.valueOf(shortKey.getId());
                // 去重：真占用 ⇒ 每物品只喊一次（有界）；其余（含空窗期）⇒ 30 秒限流复报，
                // 否则第一次的误判会把后续真相永久盖掉（我们这一路的排查正是被这点带偏的）。
                boolean firstTimeForId = stuckWarned.size() < STUCK_WARNED_LIMIT && stuckWarned.add(stuckId);
                boolean repeatAllowed = STUCK_WARN_REPEAT_WHEN_TRACKED && !trueDeadlock
                        && nowTick - stuckWarnedTick.getOrDefault(stuckId, Long.MIN_VALUE) >= STUCK_LOG_INTERVAL_TICKS;
                if (nowTick - lastStuckLogTick >= STUCK_LOG_INTERVAL_TICKS
                        && (!(shortHave <= 0 && inFlight <= 0) || firstTimeForId || repeatAllowed)) {
                    lastStuckLogTick = nowTick;
                    stuckWarnedTick.put(stuckId, nowTick);
                    if (shortHave > 0) {
                        GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪提示：{} 存量不足（网络可用={}，在途={}，"
                                        + "AE追踪本件={}，AE全局有任务={}，本次需要={}）@{}（本条 30 秒最多一条）",
                                shortName, shortHave, inFlight, aeRequesting, aeRequestingAny, shortNeed, getPos());
                    } else if (inFlight > 0) {
                        GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪提示：{} 还没进网络（可用=0，**在合成中={}**，"
                                        + "AE追踪本件={}，本次需要={}）@{} —— 上游还在做，等它就好（本条 30 秒最多一条）",
                                shortName, inFlight, aeRequesting, shortNeed, getPos());
                    } else if (aeRequesting || trueDeadlock) {
                        // ★ 档③（修正后）：AE 侧仍有任务 ⇒ **这是空窗期**。
                        //   措辞修正的关键：**不再说"没有任何任务在合成它"、不再催「全部取消」**——
                        //   实机已证明那两句是误报，而且会把玩家引去做无效动作。改成 INFO（是正常节拍，不是故障）。
                        GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪提示：{} **料暂时取不到：网络可用=0、在途=0，"
                                        + "但 AE 侧仍有合成任务**（AE追踪本件={}，AE全局有任务={}，本次需要={}）@{} —— "
                                        + "这是「料已交付、还没被消费/产出」的**空窗期**：请等下一轮 `AE 推送样板`"
                                        + "（对照 `累计推来` 看是不是刚被吃光），**不要**「全部取消」"
                                        + "（那对空窗期没有帮助，只会把已排好的任务丢掉）。（本条 30 秒最多一条。"
                                        + "0.3.132【C1】修正：旧文案「被占用且没有任何任务在合成它」经实机取证是误报）",
                                shortName, aeRequesting, aeRequestingAny, shortNeed, getPos());
                    } else {
                        // ★ 档④（真正该玩家处理的那一档）：**整个 AE 一个手工合成任务都没有**，而料又不在网络里。
                        GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪提示：{} **被占用，且整个 AE 当前没有任何手工合成任务**"
                                        + "（网络可用=0，在途=0，AE追踪本件={}，AE全局有任务={}，本次需要={}）@{} —— "
                                        + "这是真占用：料很可能卡在本机内部槽里（推来后没被消费掉）。"
                                        + "可先在 AE 终端「全部取消」再重新下单；若反复出现，请把本行与同一时刻的 "
                                        + "`AE 推送样板` / `累计推来` 两行一起留档。（每个物品只提醒一次）",
                                shortName, aeRequesting, aeRequestingAny, shortNeed, getPos());
                    }
                }
                // ★★ 照搬现役机【实际行为】：这一段只【判读 + 留痕】，**不真的拒收** ★★
                //   取证：现役机 pushPattern（840-1043）全方法只有一处 return（末尾 `return pushedOk`），
                //   没有任何 `return false` —— 所以它那句注释「…时直接返回 false」与代码不一致，
                //   真正生效的语义是"存量不足只记一条日志，照样把样板交给父类去试"。
                //   惰性机照【代码】搬，不照【注释】搬：若在这里 return false，
                //   AE 会把派发当失败、退回预留料再重试，而料往往正被合成 CPU 预留
                //   ⇒ 会把"能跑的任务"变成反复失败的重试环（比原症状更糟）。
            }
        } catch (Throwable ignored) {
            // 查不到就不判断（fail-open：宁可让父类去试，也不因为查询失败而不收样板）
        }

        // ===== (c) 机器侧兜底放大 = **已撤**（0.3.132【撤自造 · 机器侧二次放大】方案 §⑤-A1 / 第 1 期）=====
        //   原行为：`if (scalingApplies) { machineSideScaled = true; 把 inputHolder 每项 × mul ÷ div }`。
        //   为什么撤：原初 `pushPattern` 从不改 inputHolder（同现役机那块注释的完整依据；
        //     出处 `_census/NAC合成链路_原初对照与重构方案.md` §⑤-A1 / §⑧-5）。
        //   ⚠ 同现役机：删掉后若 AE 仍按缓存的【原样板】推送，倍率/神锻在机器侧不再生效
        //     （按 AE 声明的小量做）—— 这是与 AE 自洽的回落；要恢复该功能须走 §⑤-A2（需队长裁决）。
        //   一行回退（逐字）：把上面那段 if 块原样贴回，并删掉下面这行。
        machineSideScaled = false;

        // ===== (d0) 诊断（★ 照搬现役机 947-986 的那一条汇总行；啰嗦，默认关）=====
        // 配置开关打开时 1 秒最多一条：一眼看出 AE 推的是改写样板还是原样板、机器侧有没有放大、有效缩放是多少。
        try {
            // ★ 0.3.132【撤自造】把"这次倍率/神锻没生效"如实说一次（每个实例只一次；见字段 javadoc）。
            //   同现役机：AE 推的是【原样板】而机器侧二次放大已按 0.3.132 撤销 ⇒ 本次按 AE 声明量执行，
            //   倍率/神锻未在机器侧生效（附有效缩放 describeEffectiveScale()）。
            //   ⚠ 必须并列登记：这是与 AE 一致的回落，不是故障；根治见
            //   _census/NAC合成链路_原初对照与重构方案.md §⑤-A2（需队长裁决）。
            //   一行回退：把本 if 块整段删除即可（见 _census/撤自造放大_回退包/ORIGINAL_HUNKS.md）。
            if (scalingApplies && !this.machineScaleDisabledWarned) {
                this.machineScaleDisabledWarned = true;
                GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪样板总成 @{}：AE 推送的是【原样板】而机器侧二次放大"
                                + "已按 0.3.132 撤销 ⇒ 本次按 AE 声明量执行，倍率/神锻未在机器侧生效（有效缩放[{}]）。"
                                + "这是与 AE 一致的回落，不是故障；根治见 _census/NAC合成链路_原初对照与重构方案.md §⑤-A2。",
                        getPos(), describeEffectiveScale());
            }
            if (GTLModularConfig.isPatternDebugLogsEnabled()
                    && pushLevel != null && pushLevel.getGameTime() - lastPushLogTick >= 20) {
                lastPushLogTick = pushLevel.getGameTime();
                long pushed = 0;
                StringBuilder detail = new StringBuilder();
                int shown = 0;
                if (inputHolder != null) {
                    for (KeyCounter c : inputHolder) {
                        if (c == null) {
                            continue;
                        }
                        for (var entry : c) {
                            pushed += entry.getLongValue();
                            if (shown++ < 5) {
                                detail.append(' ').append(entry.getKey().getId()).append('=')
                                        .append(entry.getLongValue());
                            }
                        }
                    }
                }
                GTLModular.LOGGER.info(
                        "[gtlmodular] 惰性猪咪 AE 推送样板 @{}: 是否缩放样板={} 机器侧放大={} 输入总量={} 有效缩放[{}] 明细:{}",
                        getPos(), !aePushedOriginal, machineSideScaled, pushed, describeEffectiveScale(), detail);
            }
        } catch (Throwable ignored) {
            // 诊断日志绝不影响推样板
        }

        // ===== (d) 找槽 → 锁模式 → 交给父类（0.3.1 既有三步，逻辑一字未改）=====
        int diagSlot = -1;
        try {
            Integer slot = getSlotIndexForPattern(original);
            if (slot != null && slot >= 0) {
                diagSlot = slot;
                lockRecipeTypeForSlot(slot);
            }
        } catch (Throwable ignored) {
            // 锁模式失败只是"少锁一次"，绝不打断推样板本身
        }
        try {
            if (diagSlot >= 0) {
                logPushDiagnostic(diagSlot);
            } else {
                // ★★ 0.3.5：AE 推来一张「本机样板仓里没有的样板」★★
                //   这就是队长那句「下单也不知道发配到哪里去了」的**现场**：AE 的合成服务还拿着旧样板表，
                //   把一张本机已经没有的样板派了过来。以前这条路是静默的（getSlotIndexForPattern 返回 null
                //   ⇒ 连诊断都不打），于是"料去哪了"完全无从查证 ⇒ 现在留痕（限流 5 秒一条，防刷屏）。
                long now = getLevel() == null ? 0L : getLevel().getGameTime();
                if (now - lastOrphanPushLog >= 100L) {
                    lastOrphanPushLog = now;
                    GTLModular.LOGGER.warn(
                            "[gtlmodular] 惰性猪咪收到一张本机样板仓里没有的样板推送（AE 侧样板表可能已过期）pos={} 样板={}",
                            getPos(), original == null ? "?" : String.valueOf(original));
                }
            }
        } catch (Throwable ignored) {
            // 诊断日志绝不影响推样板
        }
        // ★ 交给父类的是【原样板】：gtlcore 的槽间映射、身份、扣料都建立在原样板之上，
        //   改写只是"给 AE 看的视图"（AE 推来的量已经按视图放大过了）。
        return super.pushPattern(original, inputHolder);
    }

    /**
     * ★ 0.3.2：推样板时打一条<b>只在该槽口径变化时</b>才出现的诊断 —— 队长 0.3.1 报的
     * 「下单有瑕的钻石、钻石被吃进缓冲、机器却在做 100 个钻石杆、润滑油没扣、产物不回」
     * 这类「配错模式」事故，原来日志里<b>一条线索都没有</b>（只有结构检查与字段同步）。
     *
     * <p>打出来的口径（全部是服务端权威值）：
     * <ul>
     *   <li>{@code 槽} 1 起；{@code 样板} = 该槽样板物品的显示名（AE 编码样板的名字就是它的产物摘要）；</li>
     *   <li>{@code 手动指定} = {@link #manualTypeIndexOf(int, List)}（-1 = 没指定）；</li>
     *   <li>{@code 自检} = {@link #autoDetectTypeOfSlot(int)}（<b>无视</b>手动指定的自动识别结果）；</li>
     *   <li>{@code 主机模式} = 控制器<b>当下</b>的配方模式；{@code 实际锁定} = {@link #recipeTypeOfSlot(int)}
     *       （手动优先）——<b>它才是主机接下来按哪个模式搜配方的依据</b>；</li>
     *   <li>手动指定与自检不一致时追加 {@code ★冲突} 标记。</li>
     * </ul>
     *
     * <p>模式名打的是 {@code registryName}（如 {@code gtceu:cutter}）而不是中文名：服务端语言不一定是
     * 中文，打 id 才能在日志里逐字对齐。
     */
    private void logPushDiagnostic(int slot) {
        if (isRemote()) {
            return;
        }
        List<GTRecipeType> types = getAvailableRecipeTypes();
        int manual = manualTypeIndexOf(slot, types);
        int auto = autoDetectTypeOfSlot(slot);
        IRecipeLogicMachine controller = firstRecipeLogicController();
        int live = controller == null ? -1 : controller.getActiveRecipeType();
        int locked = recipeTypeOfSlot(slot);
        String signature = slot + "|" + manual + "|" + auto + "|" + live + "|" + locked;
        if (signature.equals(lastPushDiagnostic.get(slot))) {
            return;   // 这个槽的口径没变 ⇒ 不重复刷日志
        }
        lastPushDiagnostic.put(slot, signature);
        GTLModular.LOGGER.info(
                "[gtlmodular] 推样板诊断: 槽={} 样板={} 手动指定={} 自检={} 主机模式={} 实际锁定={}{}",
                slot + 1, patternItemName(slot), typeIdName(manual, types), typeIdName(auto, types),
                typeIdName(live, types), typeIdName(locked, types),
                manual >= 0 && auto >= 0 && manual != auto
                        ? " ★冲突：手动指定与自检不一致（主机可能被锁到错模式、按错配方吃掉这批料）"
                        : "");
    }

    /** 该槽样板物品的显示名（AE 编码样板的名字就是产物摘要）。 */
    private String patternItemName(int slot) {
        try {
            ItemStack stack = patternInventory.getStackInSlot(slot);
            return stack.isEmpty() ? "(空)" : stack.getHoverName().getString();
        } catch (Throwable ignored) {
            return "?";
        }
    }

    /** 类型下标 → {@code registryName} 文本（服务端语言无关）；越界/未知 → "-"。 */
    private static String typeIdName(int index, List<GTRecipeType> types) {
        if (index < 0 || index >= types.size()) {
            return "-";
        }
        GTRecipeType type = types.get(index);
        if (type == null || type.registryName == null) {
            return "?";
        }
        return type.registryName.toString();
    }

    /**
     * ★ P1-②C：<b>惰性机的样板</b>底表。
     *
     * <p>现役机在这一步是 {@code super.getAvailablePatterns()}（gtlcore 具体类自己维护的
     * {@code slot2PatternMap} + 一整套样板槽换算）；惰性机没有那份表，底表就是我们的
     * {@link #patternSlotMap}（{@link #ensurePatternSlotMap()} 解码出来的、仓里<b>真实存在</b>的样板）。
     * <b>这就是"惰性化差异"在视图改写链路上的唯一一处</b>：底表来源不同，后面逐条一致。
     */
    private List<IPatternDetails> baseAvailablePatterns() {
        ensurePatternSlotMap();
        return new ArrayList<>(patternSlotMap.keySet());
    }

    /**
     * ★ P1-②C：AE 要的样板表 —— <b>缓存命中就直接给改写后的表</b>，否则按当前设置重建。
     *
     * <p>逐条照搬现役机 {@code getAvailablePatterns()}（MoltenPatternAssemblyPartMachine 1671-1708）：
     * <ol>
     *   <li>缓存已建且真有改写结果 ⇒ 直接返回（不必再算一遍底表）；</li>
     *   <li>底表为空 / 没开改写 ⇒ <b>作废缓存</b>并返回底表；同时按
     *       {@code base.size()|slotCount()|isRewriteEnabled()|patternScale|patternDivScale|foaMode|isOutputMode}
     *       这个签名限流打一条「取样板(未改写)」，只在签名变化时打（空机器静默，否则会被 AE 的网络轮询刷屏）；</li>
     *   <li>否则按需重建，再返回改写表（重建出来是空的就退回底表）。</li>
     * </ol>
     *
     * <p><b>惰性化差异（有意，仅此一处）</b>：第 2 步现役机调的是 {@code invalidateRewriteCache()}
     * （那条会<b>顺带</b>通知 AE 重取样板表）。惰性机 0.3.5 已经把"通知 AE"合并进
     * {@link #lazyTick()} 里每 tick 一次（防批量粘贴 640 张时 O(n²)，见
     * {@link #onPatternInventoryChanged()} 的取证注释），从<b>读样板的热路径</b>里再捅一次
     * 等于把那个设计绕开；而"设置变了必须通知 AE"已由 {@link #invalidateRewriteCache()}（setter 里调）
     * 完整负责 ⇒ 这里改成<b>静默作废</b>：语义（缓存失效、下一条重建）完全一致，只是不再重复通知。
     */
    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        // ★★ 0.3.132【倍增交给 AE】开关打开时：直接返回仓里的原样板，绝不把改写样板报给 AE。★★
        //   原版 gtlcore 的 getAvailablePatterns() 就是直接返回槽表（MEPatternBufferPartMachine:776-778），
        //   倍增由 AE 的自动翻倍承担（CraftingCpuLogicMixin @Overwrite executeCrafting + CraftingPatternAutoExpand）。
        //   为什么必须这样：我们改写样板会让"样板声明量"自身乘 N，与 AE 的 operations 放大重复且在相乘
        //   ⇒ 需求被推到天上去 ⇒ AE 提取全有或全无 ⇒ 本 tick 零推送 ⇒「被占用、没有任务」。
        //   一行回退：把 PATTERN_REWRITE_DISABLED 改成 false。
        if (PATTERN_REWRITE_DISABLED) {
            return baseAvailablePatterns();
        }
        if (rewriteCacheBuilt && rewrittenPatterns != null) {
            return rewrittenPatterns;   // 缓存有效且有改写结果：不必再让底表造一遍 List
        }
        List<IPatternDetails> base = baseAvailablePatterns();
        if (base.isEmpty() || !isRewriteEnabled()) {
            invalidateRewriteCacheQuietly();
            // 诊断（临时）：只在"仓里/样板表里真有样板"时打印一次；完全空的机器静默，
            // 否则会被 AE 网络每秒轮询刷屏（现役机实测那台空机器刷了 1300+ 行）。
            String sig = base.size() + "|" + slotCount() + "|" + isRewriteEnabled() + "|" + patternScale + "|"
                    + patternDivScale + "|" + foaMode + "|" + isOutputMode;
            if (!sig.equals(lastQuerySignature)) {
                lastQuerySignature = sig;
                int patterns = 0;
                for (int i = 0; i < getCapacity(); i++) {
                    if (!patternInventory.getStackInSlot(i).isEmpty()) {
                        patterns++;
                    }
                }
                if (base.size() > 0 || patterns > 0) {
                    GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪 取样板(未改写): @{} {} 原始={} 容量槽={} 仓内样板={}"
                                    + " 开关={} 倍率={} 除率={} 神锻={}({}) 输出模式={}",
                            getLevel() == null ? "?" : getLevel().dimension().location(), getPos(), base.size(),
                            slotCount(), patterns, isRewriteEnabled(), patternScale, patternDivScale, foaMode,
                            foaMultiplier, isOutputMode);
                }
            }
            return base;
        }
        if (!rewriteCacheBuilt) {
            rebuildRewriteCache(base);
            rewriteCacheBuilt = true;
        }
        return rewrittenPatterns == null || rewrittenPatterns.isEmpty() ? base : rewrittenPatterns;
    }

    /** 当前容量（现役机这里叫 {@code slotCount()}，惰性机的容量是动态的 ⇒ 直接复用已有访问器）。 */
    private int slotCount() {
        return getCapacity();
    }

    /** 是否启用了视图改写（倍率 / 除率 / 神锻 任一）。★ 照搬现役机 {@code isRewriteEnabled()}。 */
    private boolean isRewriteEnabled() {
        return foaMode || patternScale > 1 || patternDivScale > 1;
    }

    /** 有效倍率（神锻 × 倍率，两侧同倍）；与 {@link #rewritePattern} 同一套公式。★ 照搬现役机。 */
    private int effectiveMul() {
        int bothMul = foaMode ? Math.max(MIN_FOA_MULTIPLIER, Math.min(MAX_FOA_MULTIPLIER, foaMultiplier)) : 1;
        int sideMul = Math.max(MIN_SCALE, Math.min(MAX_SCALE, patternScale));
        return bothMul * sideMul;
    }

    /** 有效除率。★ 照搬现役机。 */
    private int effectiveDiv() {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, patternDivScale));
    }

    /**
     * 当前设置下的<b>有效</b>缩放（仅供日志判读）。★ 照搬现役机。
     *
     * <p>倍率/除率与神锻一样<b>两侧同倍</b>（旧版"输出模式"只放大产物侧，已废弃）：
     * 例如神锻 15 + 倍率 2 → 两侧 ×30；除率 3 → 两侧 ÷3。总量不变、合成次数按倍率下降。
     */
    private String describeEffectiveScale() {
        return "两侧×" + effectiveMul() + "÷" + effectiveDiv();
    }

    /**
     * 设置变了 ⇒ 作废改写缓存，并<b>通知 AE 重新取样板表</b>。★ 照搬现役机 {@code invalidateRewriteCache()}。
     *
     * <p><b>关键：光作废是不够的。</b>改写是"懒计算"——只在 {@link #getAvailablePatterns()} 里做。
     * 现役机实测：改了倍率/神锻之后 AE <b>不会</b>主动再来取一次样板表（日志里那台机器整局只在打开时
     * 被取过一次），于是缩放永远不生效；只有 requestUpdate 才会让合成服务当秒重取。
     *
     * <p>惰性机直接复用 0.3.5 已有的 {@link #refreshPatternsNow()}（同款两个 AE 官方入口：
     * {@code ICraftingService.refreshNodeCraftingProvider} + {@code ICraftingProvider.requestUpdate}），
     * <b>没有</b>再写第二份（那一份自带防重入 {@code refreshingPatterns}、远端直接放过、异常吞掉）。
     */
    public void invalidateRewriteCache() {
        invalidateRewriteCacheQuietly();
        refreshPatternsNow();
    }

    /**
     * 只作废、<b>不通知 AE</b>（静默版）。
     *
     * <p>两个调用点都是"通知由别人负责"的地方：① 读样板热路径（AE 正在读，不需要再喊一次）；
     * ② {@link #onPatternInventoryChanged()}（那里只置 {@code needPatternSync}，由 lazyTick 每 tick 合并通知一次，
     * 见 0.3.5 的 O(n²) 取证注释）。
     */
    private void invalidateRewriteCacheQuietly() {
        rewrittenPatterns = null;
        rewrittenToOriginal.clear();
        rewriteCacheBuilt = false;
    }

    /**
     * 重建改写缓存。★ 照搬现役机 {@code rebuildRewriteCache}（1791-1809）。
     *
     * <p>惰性化：只在<b>真的要给 AE 一张新表</b>时才走这里（缓存签名命中就完全不走），
     * 且集合按底表大小分配（底表 = 真实存在的样板数，不是 128/640）⇒ 空仓/半仓都不会白付。
     */
    private void rebuildRewriteCache(List<IPatternDetails> base) {
        rewrittenToOriginal.clear();
        List<IPatternDetails> result = new ArrayList<>(base.size());
        int changedCount = 0;
        for (IPatternDetails details : base) {
            IPatternDetails rewritten = rewritePattern(details);
            if (rewritten == null || rewritten == details) {
                result.add(details);
                continue;
            }
            result.add(rewritten);
            rewrittenToOriginal.put(rewritten, details);
            changedCount++;
        }
        rewrittenPatterns = changedCount > 0 ? result : null;
        // 诊断（临时）：改倍率/神锻后若这行不出现 = 缓存没失效；出现但"已改写=0" = 改写逻辑本身没产出。
        // 只在**重建时**打（本方法只在缓存未建时被调）⇒ 不会每 tick 刷。
        GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪 缩放缓存重建 @{}: 样板={} 已改写={} 有效缩放[{}] 倍率={} 除率={} 神锻={}({})",
                getPos(), base.size(), changedCount, describeEffectiveScale(), patternScale, patternDivScale,
                foaMode, foaMultiplier);
    }

    /**
     * 单个样板的缩放。★ 照搬现役机 {@code rewritePattern}（1811-1843）。
     *
     * <p><b>两侧同倍</b>（与神锻一致）：输入和输出用<b>同一个</b>倍率/除率。
     * 原因：AE 只会把"样板声明里要的输入量"推给机器，机器就按推来的量做 ——
     * 如果只放大某一侧（旧的"输出模式"），AE 以为能拿到 N 倍产出、机器实际只跑 1 倍，
     * 声明与实际执行就对不上了。两侧同倍则永远自洽（N 倍输出必然配 N 倍输入）。
     *
     * <p>惰性化：返回 null 表示"这一张不用改写"（非处理样板 / 拿不到 level / 编码失败），
     * 调用方会原样保留底表里的那一张 —— 与现役机同口径。
     */
    private IPatternDetails rewritePattern(IPatternDetails details) {
        Level level = getLevel();
        if (level == null || !(details instanceof AEProcessingPattern pattern)) {
            return null;
        }
        int bothMul = foaMode ? Math.max(MIN_FOA_MULTIPLIER, Math.min(MAX_FOA_MULTIPLIER, foaMultiplier)) : 1;
        int sideMul = Math.max(MIN_SCALE, Math.min(MAX_SCALE, patternScale));
        int sideDiv = Math.max(MIN_SCALE, Math.min(MAX_SCALE, patternDivScale));
        int mul = bothMul * sideMul;   // 神锻 × 倍率（两者可叠加：神锻15 + 倍率2 = 30 批）
        int div = sideDiv;
        if (mul == 1 && div == 1) {
            return null;
        }
        try {
            GenericStack[] inputs = scaleStacks(pattern.getSparseInputs(), mul, div);
            GenericStack[] outputs = scaleStacks(pattern.getSparseOutputs(), mul, div);
            ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
            if (encoded == null || encoded.isEmpty()) {
                return null;
            }
            return PatternDetailsHelper.decodePattern(encoded, level);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** 把一组 stack 的量 ×mul÷div（向下取整但至少留 1，避免出现 0 数量的 stack）。★ 照搬现役机。 */
    private static GenericStack[] scaleStacks(GenericStack[] source, int mul, int div) {
        if (source == null) {
            return null;
        }
        GenericStack[] result = new GenericStack[source.length];
        for (int i = 0; i < source.length; i++) {
            GenericStack stack = source[i];
            if (stack == null) {
                continue;
            }
            long amount = stack.amount() * (long) mul / Math.max(1, div);
            result[i] = new GenericStack(stack.what(), Math.max(1, amount));
        }
        return result;
    }

    // ---------- 倍率 / 除率 / 神锻 的字段读写（★ 逐条照搬现役机 1862-1927）----------

    public int getPatternScale() {
        return patternScale;
    }

    /**
     * ★ 与现役机唯一的有意差异：值没变就<b>直接返回</b>（不作废、不通知）。
     * <p>依据：LDLib {@code TextFieldWidget.updateScreen()} 只在供应商值与控件值<b>不同</b>时才
     * {@code setCurrentString}，responder 也只在 {@code onTextChanged} 时触发（反汇编存盘
     * {@code _javap_p1c_api.txt} 第【14】节）⇒ 现役机那句"无条件 invalidate"本身不会每 tick 触发；
     * 这里再加一道守卫，是为了满足本机"不许在每 tick 做重活"的纪律：
     * <b>只有设置真的变了才 requestUpdate</b>，语义上仍是"任一设置变化都让 AE 重取样板表"。
     */
    public void setPatternScale(int scale) {
        int next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        if (next == this.patternScale) {
            return;
        }
        GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪 缩放设置: 倍率 {} -> {}（{}）",
                this.patternScale, next, isRemote() ? "客户端" : "服务端");
        this.patternScale = next;
        invalidateRewriteCache();
    }

    public int getPatternDivScale() {
        return patternDivScale;
    }

    public void setPatternDivScale(int scale) {
        int next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        if (next == this.patternDivScale) {
            return;
        }
        GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪 缩放设置: 除率 {} -> {}（{}）",
                this.patternDivScale, next, isRemote() ? "客户端" : "服务端");
        this.patternDivScale = next;
        invalidateRewriteCache();
    }

    public boolean isOutputMode() {
        return isOutputMode;
    }

    /**
     * @deprecated ★ 照搬现役机：旧版"输出模式（只放大产物侧）"。两侧同倍之后不再参与缩放计算，
     * 保留字段/读写仅为兼容旧存档与同步布局；界面上已移除该按钮。
     */
    @Deprecated
    public void toggleOutputMode() {
        this.isOutputMode = !isOutputMode;
        invalidateRewriteCache();
        GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪 缩放设置: 输出模式={}（{}，已废弃：不再影响缩放）",
                isOutputMode, isRemote() ? "客户端" : "服务端");
    }

    public boolean isFoaModeEnabled() {
        return foaMode;
    }

    public void setFoaModeEnabled(boolean enabled) {
        if (enabled == this.foaMode) {
            return;   // 同上：只有设置真的变了才通知 AE
        }
        GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪 缩放设置: 神锻模式={}（{}）",
                enabled, isRemote() ? "客户端" : "服务端");
        this.foaMode = enabled;
        invalidateRewriteCache();
    }

    public int getFoaPatternOutputMultiplier() {
        return foaMultiplier;
    }

    public void setFoaPatternOutputMultiplier(int multiplier) {
        int next = Math.max(MIN_FOA_MULTIPLIER, Math.min(MAX_FOA_MULTIPLIER, multiplier));
        if (next == this.foaMultiplier) {
            return;
        }
        this.foaMultiplier = next;
        invalidateRewriteCache();
    }

    @Override
    public appeng.api.inventories.InternalInventory getTerminalPatternInventory() {
        return terminalPatternInventory;
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        MachineDefinition definition = getDefinition();
        Component name = Component.translatable(definition.getDescriptionId());
        String custom = getCustomName();
        if (custom != null && !custom.isEmpty()) {
            name = Component.literal(custom);
        }
        return new PatternContainerGroup(AEItemKey.of(definition.asStack()), name, Collections.emptyList());
    }

    /** 脏了才重建「样板 → 槽号」映射（解码样板不便宜，绝不能每次查询都重来）。 */
    private void ensurePatternSlotMap() {
        if (!patternSlotMapDirty) {
            return;
        }
        patternSlotMapDirty = false;
        patternSlotMap.clear();
        Level level = getLevel();
        if (level == null) {
            return;
        }
        int capacity = getCapacity();
        for (int i = 0; i < capacity; i++) {
            ItemStack stack = patternInventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            IPatternDetails details = PatternDetailsHelper.decodePattern(stack, level);
            if (details != null) {
                patternSlotMap.putIfAbsent(details, i);
            }
        }
    }

    // ==================== 配方处理器（自研，因为具体类的构造器只吃具体类） ====================

    /** 继承 gtlcore 的<b>基类</b>处理器（构造器吃 {@code MEPatternBufferPartMachineBase}），实现两个抽象工厂。 */
    public static class LazyPatternBufferRecipeHandler extends MEPatternBufferRecipeHandlerTraitBase {

        /** MachineTrait 没有提供 holder，抽象处理器子类必须自己挂一个（与 gtlcore 具体处理器同款写法）。 */
        protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
                new ManagedFieldHolder(LazyPatternBufferRecipeHandler.class);

        @Override
        public ManagedFieldHolder getFieldHolder() {
            return MANAGED_FIELD_HOLDER;
        }

        public LazyPatternBufferRecipeHandler(MEPatternBufferPartMachineBase machine, IO io) {
            super(machine, io);
        }

        /**
         * ★ P1-②D 身份闸：两个扣料 handler 换成<b>带闸</b>的版本（现役机 1221-1278 的
         * {@code IdentityGatedRecipeHandler} 的基类链等价物）。
         *
         * <p><b>为什么挂在这两个方法上（javap 依据见工作区根 {@code _javap_p1d_api.txt}）</b>：
         * <ol>
         *   <li>现役机覆写的是 {@code MEPatternBufferPartMachine#createRecipeHandler(IO)}；
         *       那个方法<b>只声明在具体类</b> {@code MEPatternBufferPartMachine} 上（证据 [10]），
         *       惰性机的父类 {@code MEPatternBufferPartMachineBase} 方法表里<b>根本没有它</b>、
         *       也没有 {@code recipeHandler} 字段（证据 [9]）⇒ <b>惰性机没有那个覆写点</b>。</li>
         *   <li>惰性机的等价物 = 本类唯一的构造点
         *       {@code new LazyPatternBufferRecipeHandler(this, IO.BOTH)}
         *       （机器构造器里那一句，语义等价于现役机的 {@code createRecipeHandler}）。</li>
         *   <li>基类 trait {@code MEPatternBufferRecipeHandlerTraitBase} 的构造器字节码
         *       （证据 [2]，offset 8 / 17）是 {@code invokevirtual createMEItemHandler} /
         *       {@code createMEFluidHandler} ⇒ 这两个<b>受保护抽象工厂</b>才是基类链上的植入点；
         *       而且是<b>虚分派</b>，我们这两个覆写会在
         *       {@code MEPatternBufferRecipeHandlerTraitBase.<init>} 内部就被调到
         *       （构造期只有 {@code MachineTrait.machine} 已就绪 ⇒ <b>只能用 {@code getMachine()}，
         *       绝不能碰本类 {@code LazyPatternBufferRecipeHandler} 自己的字段</b>
         *       —— 与现役机 1206 那条注释同一条纪律）。</li>
         * </ol>
         *
         * <p>为什么"闸"要挂在 {@code meHandleRecipeInner} 上（证据 [4]/[5]/[6]/[7]）：
         * 它是 gtlcore 接口 {@code IMERecipeHandler} 的<b>抽象方法</b>，同接口默认方法
         * {@code meHandleRecipe(recipe, simulate, slot)} 的方法体就是
         * {@code meHandleRecipeInner(recipe, getPreparedMEHandleContents(), simulate, slot)}
         * —— 即"这条配方对这个槽扣料"的那一下；物品侧实现体里可见
         * {@code getInternalSlotOrNull(slot) → InternalSlot.handleItemInternal(...)}。
         * 具体类的 {@code $MEItemInputHandler} / {@code $MEFluidHandler} <b>没有</b>覆写它
         * （证据 [4] 里它们只有 {@code getMachine()}）⇒ 具体链与基类链跑的是同一份实现，
         * 所以挂点与现役机同名同签名。
         */
        @Override
        protected MEItemInputHandlerBase createMEItemHandler(IO io) {
            return new GatedItemHandler(getMachine(), io);
        }

        @Override
        protected MEFluidHandlerBase createMEFluidHandler(IO io) {
            return new GatedFluidHandler(getMachine(), io);
        }

        /** 物品侧：这条配方不属于该槽的样板 → 直接返回 false（该槽不参与这条配方），其余原样交给 gtlcore。 */
        static final class GatedItemHandler extends MEItemInputHandlerBase {

            private final LazyPatternAssemblyPartMachine machine;

            GatedItemHandler(MEPatternBufferPartMachineBase machine, IO io) {
                super(machine, io);
                // 构造期（基类 ctor 内部）就要把机器引用定下来，所以这里只做一次类型判定、不干别的活：
                // 不是本模组的机器 ⇒ machine = null ⇒ 该 handler 与"没挂闸"逐字节等价（fail-open）。
                this.machine = machine instanceof LazyPatternAssemblyPartMachine lazy ? lazy : null;
            }

            @Override
            public boolean meHandleRecipeInner(GTRecipe recipe, Object2LongMap<Ingredient> left,
                                               boolean simulate, int slot) {
                if (machine != null && !machine.slotAllowsRecipe(slot, recipe)) {
                    machine.logGateReject(slot, recipe, "物品");
                    return false;
                }
                return super.meHandleRecipeInner(recipe, left, simulate, slot);
            }
        }

        /** 流体侧：同物品侧。 */
        static final class GatedFluidHandler extends MEFluidHandlerBase {

            private final LazyPatternAssemblyPartMachine machine;

            GatedFluidHandler(MEPatternBufferPartMachineBase machine, IO io) {
                super(machine, io);
                this.machine = machine instanceof LazyPatternAssemblyPartMachine lazy ? lazy : null;
            }

            @Override
            public boolean meHandleRecipeInner(GTRecipe recipe, Object2LongMap<FluidIngredient> left,
                                               boolean simulate, int slot) {
                if (machine != null && !machine.slotAllowsRecipe(slot, recipe)) {
                    machine.logGateReject(slot, recipe, "流体");
                    return false;
                }
                return super.meHandleRecipeInner(recipe, left, simulate, slot);
            }
        }
    }

    // ==================== AE 网格 tickable：产物回流 ====================

    /**
     * <b>产物回流通道</b> —— 与 gtlcore 具体类 {@code MEPatternBufferPartMachine$Ticker} 逐指令对齐。
     *
     * <p>为什么必须有它（0.2.0 实测「产物不返回」的根因，javap 实证）：
     * <ol>
     *   <li>{@code MEItemInputHandlerBase.meHandleRecipeOutputInner(outputs, false)} 把机器产物写进
     *       基类字段 {@code buffer}（{@code Object2LongOpenHashMap<AEKey>}），<b>没有</b>直接碰网络；</li>
     *   <li>真正把 {@code buffer} 推上 ME 的是本 ticker 调用的 {@code AEUtils.reFunds}
     *       （内部走 {@code StorageHelper.poweredInsert}，每次最多处理 64 种键，推不动的留在 map 里下 tick 再推）；</li>
     *   <li>{@code Ticker} 定义在具体类里、基类<b>没有</b>，所以继承基类的自研机器必须自己补。</li>
     * </ol>
     *
     * <p>频率取 {@code ConfigHolder.MEPatternOutputMin/Max}（与 gtlcore 的 MIN/MAX_FREQUENCY 同源：
     * 它们在 gtlcore 里就是这两个配置项的只读镜像）。
     * {@code TickingRequest} 第 4 个参数 {@code canBeAlerted=true} ⇔ 允许 {@code wakeDevice} 唤醒；
     * 唤醒动作由继承来的 {@code MEIOTrait.notifySelfIO()} 完成。
     */
    public class LazyBufferTicker implements IGridTickable {

        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(ConfigHolder.INSTANCE.MEPatternOutputMin,
                    ConfigHolder.INSTANCE.MEPatternOutputMax, false, true);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!getMainNode().isActive()) {
                isSleeping = true;
                return TickRateModulation.SLEEP;
            }
            if (buffer.isEmpty()) {
                if (ticksSinceLastCall >= ConfigHolder.INSTANCE.MEPatternOutputMax) {
                    isSleeping = true;
                    return TickRateModulation.SLEEP;
                }
                return TickRateModulation.SLOWER;
            }
            // 推进去了就加急继续推；网络满/断电则降速下 tick 重试（残留留在 buffer，不会丢）
            return AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource)
                    ? TickRateModulation.URGENT
                    : TickRateModulation.SLOWER;
        }
    }

    // ==================== ME 配方 trait（基类 getMETrait() 会把 meTrait 强转成 IMEPatternTrait） ====================

    /**
     * gtlcore 基类 {@code getMETrait()} 的实现体是
     * {@code (IMEPatternTrait) this.meTrait}（javap 实证有 checkcast），
     * 而 {@code MEIOPartMachine.createMETrait()} 默认返回的普通 {@code MEIOTrait} <b>不是</b>
     * {@code IMEPatternTrait} ⇒ 必须自己覆写 {@code createMETrait()}，否则运行期一调就 ClassCastException。
     */
    @Override
    protected IMEIOTrait createMETrait() {
        return new LazyMEPatternTrait();
    }

    /** 自研的样板 trait：只补 P0 需要的配方缓存壳子（P0 不用缓存，缓存逻辑属 P1）。 */
    public class LazyMEPatternTrait extends MEIOPartMachine.MEIOTrait implements IMEPatternTrait {

        private final Int2ReferenceMap<ObjectSet<GTRecipe>> slot2RecipesCache = new Int2ReferenceOpenHashMap<>();
        private IntConsumer onPatternChange = slot -> {};

        public LazyMEPatternTrait() {
            super(LazyPatternAssemblyPartMachine.this);
        }

        @Override
        public LazyPatternAssemblyPartMachine getMachine() {
            return LazyPatternAssemblyPartMachine.this;
        }

        @Override
        public ObjectSet<GTRecipe> getCachedGTRecipe() {
            // ★ 0.2.8 修正：不能"全量并集"交给主机。猪咪（javap 实证）的过滤是：
            //   ① 集合为空 → 从缓存里删掉该槽（iterator.remove()）；
            //   ② cacheRecipe[slot]（阈值达标）——**P1-②B 复核时补全**（见下方闸门 ②）：
            //      阈值默认 1 时它 = "集合非空"，与原实现等价；阈值被调高时不再等价，必须显式判。
            //   ③ internalInventory[slot].isActive()（= 有样板 且 槽里有料）—— **关键**：
            //      下游 gtlcore `GTRecipeLookupMixin.find()` 拿到非空缓存集是
            //      `iterator().next()` 直接返回、**不校验谓词** ⇒
            //      把"当前没料可跑"的配方留在缓存里 = 主机反复拿到一条跑不动的配方（可能就卡在那条上）。
            ObjectSet<GTRecipe> all = new ObjectOpenHashSet<>();
            ObjectIterator<Int2ReferenceMap.Entry<ObjectSet<GTRecipe>>> iterator =
                    Int2ReferenceMaps.fastIterator(slot2RecipesCache);
            while (iterator.hasNext()) {
                Int2ReferenceMap.Entry<ObjectSet<GTRecipe>> entry = iterator.next();
                ObjectSet<GTRecipe> recipes = entry.getValue();
                if (recipes == null || recipes.isEmpty()) {
                    iterator.remove();   // 与猪咪一致：空集清掉，别留在缓存里
                    continue;
                }
                int slot = entry.getIntKey();
                // 直读槽表，避免为了查询而实体化槽；未实体化 = 该槽没料 ⇒ 跳过
                InternalSlot internal = slot >= 0 && slot < internalInventory.size()
                        ? internalInventory.get(slot) : null;
                if (internal == null || !internal.isActive()) {
                    continue;
                }
                // ★★ P1-②B 复核补全（闸门 ②）：具体类字节码同一位置的判断是
                //    `getfield cacheRecipe:[Z → iload slot → baload → ifeq 跳过`
                //    （_javap_p1b_trait_getcached.txt 偏移 76-86），而 cacheRecipe[slot] 的值来自
                //    `setSlotCacheRecipe` 里的 `set.size() >= cacheRecipeCount[slot]`
                //    ⇒ 语义 =「本槽配方数 ≥ 该槽阈值」。阈值默认 1 时与上面的"空集已清"完全等价
                //    ⇒ 这一行对现状**零影响**；但阈值被调高（存档字段 / 将来的复制工具会粘贴高阈值旧档）时，
                //    未达阈值的"部分缓存"**绝不能**喂给主机 —— 否则 GTRecipeLookupMixin 的
                //    "非空即取一条"会拿它去配一条还没攒够的配方。
                if (recipes.size() < cacheRecipeCountAt(slot)) {
                    continue;
                }
                // ★★ 0.3.133【跨表并行 · 读侧类型校验】★★（闸门 ④）
                //   只把「属于本机可用配方类型表」的配方交给主机 —— 这一句就是「快路径不校验谓词」
                //   这个前提的对策：即使下游真的「缓存集非空 ⇒ iterator().next() 直接返回」，
                //   能取到的也必定是本机某一张表里的配方，取不到"上一个模式的陈旧配方"。
                //   一行回退：`FILTER_CACHED_RECIPES_BY_AVAILABLE_TYPE_ON_READ = false`（见其 javadoc）。
                if (FILTER_CACHED_RECIPES_BY_AVAILABLE_TYPE_ON_READ) {
                    for (GTRecipe cached : recipes) {
                        if (recipeTypeIsUsableByThisMachine(cached)) {
                            all.add(cached);
                        }
                    }
                } else {
                    all.addAll(recipes);
                }
            }
            return all;
        }

        @Override
        public void setSlotCacheRecipe(int slot, GTRecipe recipe) {
            if (recipe == null) {
                slot2RecipesCache.remove(slot);
                return;
            }
            // ★ 0.2.8：与猪咪一致地跳过 DUMMY_RECIPES（猪咪字节码：`recipe.recipeType != GTRecipeTypes.DUMMY_RECIPES`
            //   才写入）。DUMMY 配方若进了缓存，会被 GTRecipeLookupMixin 的"非空即取一条"快路径当成可用配方返回。
            if (recipe.recipeType == GTRecipeTypes.DUMMY_RECIPES) {
                return;
            }
            slot2RecipesCache.computeIfAbsent(slot, key -> new ObjectOpenHashSet<>()).add(recipe);
        }

        @Override
        public Int2ReferenceMap<ObjectSet<GTRecipe>> getSlot2RecipesCache() {
            return slot2RecipesCache;
        }

        @Override
        public void setOnPatternChange(IntConsumer consumer) {
            this.onPatternChange = consumer == null ? slot -> {} : consumer;
        }

        /** 取某槽已缓存的配方集合（没有则 null）；供机器的 {@code hasRecipeCacheInSlot} 复用。 */
        public ObjectSet<GTRecipe> slotRecipes(int slot) {
            return slot2RecipesCache.get(slot);
        }

        @Override
        public boolean hasCacheInSlot(int slot) {
            // ★ 语义依据（javap 实证，0.2.8 首次定稿、P1-②B 补全阈值那一半）：
            //   ① 具体类构造器里 `Arrays.fill(cacheRecipeCount, (byte) 1)` —— **阈值默认就是 1**；
            //   ② 具体类 MEPatternTrait.setSlotCacheRecipe(int, GTRecipe) 的字节码是
            //      `set = recipeMultipleCacheMap.computeIfAbsent(slot, i -> new ObjectArraySet())`
            //      → `if (set.add(recipe))` → `cacheRecipe[slot] = (set.size() >= cacheRecipeCount[slot])`
            //      （`if_icmplt` + `iconst_1/iconst_0` + `bastore`，见 _javap_p1b_concrete_ctor.txt）；
            //   ③ 具体类 MEPatternTrait.hasCacheInSlot(int) 就是 `cacheRecipe[slot]`（`baload; ireturn`）。
            //   ⇒ 阈值默认 1 时它 =「**有一条就算已缓存**」（与原实现逐条一致）；
            //      阈值被调高（界面/AE 把 cacheRecipeCount 调大，或将来复制工具粘贴高阈值旧档）时，
            //      这里必须跟着返回 false，主机才知道「还没到阈值、继续往里攒」。
            //   ★ 唯一消费方是 gtlcore 主机侧 meHandleRecipe 的
            //      `if (!hasCacheInSlot(slot)) setSlotCacheRecipe(slot, recipe)`
            //      （_census/_javap_simple_handleparts.txt 偏移 380 附近的 invokeinterface）——
            //      返回 false = 继续攒配方；返回 true = 认为该槽已经缓存好了。
            ObjectSet<GTRecipe> set = slotRecipes(slot);
            return set != null && !set.isEmpty() && set.size() >= cacheRecipeCountAt(slot);
        }

        /** 槽里的样板变了时调用（P0：让「样板 → 槽」映射与容量跟着走）。 */
        public void notifyPatternChange(int slot) {
            onPatternChange.accept(slot);
        }

        /**
         * ★ 0.2.6：清掉该槽的配方缓存（等价猪咪 `removeSlotFromGTRecipeCache` 里的
         * {@code recipeMultipleCacheMap.remove(slot)}）。
         *
         * <p>为什么必须清：主机侧「这台舱室能做哪些配方」是从这份缓存学的
         * （{@code MEPatternRecipeHandlePart.restoreMachineCache} 只读 {@code getSlot2RecipesCache()}）。
         * 样板被换掉后不清 ⇒ 缓存里留着"已经撤掉的样板对应的配方"，
         * 主机可能拿它去配 {@code recipes2SlotsMap}，即"无样板也能跑/跑错配方"。
         */
        public void clearSlotCache(int slot) {
            slot2RecipesCache.remove(slot);
        }
    }

    // ==================== P2-① 每槽配方模式（不串配方的核心） ====================
    //
    // 逐条照搬 MoltenPatternAssemblyPartMachine（现役机）：
    //   getAvailableRecipeTypes / firstRecipeLogicController / cycleRecipeType / recipeTypeOfSlot /
    //   manualTypeIndexOf / assignManualType / detectRecipeType / scanTypesForMatch /
    //   patternIdentityMatches（+ 它的 6 个静态小工具）/ tableNameOf。
    // 唯一改动 = 「固定数组 slotRecipeType[i]」→「跟随容量增长的 slotRecipeTypes.get(i)」。

    /** 控制器暴露的全部配方类型（没有控制器时为空）。★ 照搬现役机同名方法，缓存口径一致。 */
    public List<GTRecipeType> getAvailableRecipeTypes() {
        IRecipeLogicMachine controller = firstRecipeLogicController();
        if (controller == null) {
            cachedTypesController = null;
            cachedTypesSource = null;
            cachedTypes = List.of();
            return cachedTypes;
        }
        GTRecipeType[] types = controller.getRecipeTypes();
        if (controller == cachedTypesController && types == cachedTypesSource) {
            return cachedTypes;   // 同一个控制器、同一个数组 → 直接复用，省掉 Arrays.asList
        }
        cachedTypesController = controller;
        cachedTypesSource = types;
        cachedTypes = types == null ? List.of() : java.util.Arrays.asList(types);
        return cachedTypes;
    }

    /** 第一个能当「配方机」用的控制器（多方块主机）。★ 照搬现役机同名方法。 */
    private IRecipeLogicMachine firstRecipeLogicController() {
        for (IMultiController controller : getControllers()) {
            if (controller instanceof IRecipeLogicMachine logicMachine) {
                return logicMachine;
            }
        }
        return null;
    }

    public int getActiveRecipeTypeIndex() {
        return activeRecipeTypeIndex;
    }

    public void setActiveRecipeTypeIndex(int index) {
        this.activeRecipeTypeIndex = index;
    }

    /** 当前激活的配方类型（下标越界/空表时退回第一个）。★ 照搬现役机同名方法。 */
    public GTRecipeType getActiveRecipeType() {
        List<GTRecipeType> types = getAvailableRecipeTypes();
        if (activeRecipeTypeIndex >= 0 && activeRecipeTypeIndex < types.size()) {
            return types.get(activeRecipeTypeIndex);
        }
        return types.isEmpty() ? null : types.get(0);
    }

    /**
     * 手动切换模式（界面上的 ◀ / ▶）。
     * ★ 照搬现役机 {@code cycleRecipeType} —— 注意它<b>只改本类的下标缓存</b>，不直接动控制器：
     * 真正的切换由 {@link #lockRecipeTypeForSlot(int)}（推送时）与 {@link #autoSwitchRecipeModes()}
     * （自动切换）去做，避免玩家点一下按钮就把正在跑的配方打断。
     */
    /**
     * 手动切换模式（界面上的 ◀ / ▶）——
     * ★ 0.3.2 起改的是<b>模式页游标</b> {@link #modeCursorIndex}（{@link #assignManualType(int, boolean)}
     * 与模式页显示读的就是它），<b>不动控制器</b>：
     * 真正的机器模式切换由 {@link #lockRecipeTypeForSlot(int)}（推送时）与 {@link #autoSwitchRecipeModes()}
     * （自动切换）去做，避免玩家点一下按钮就把正在跑的配方打断（与现役机同一条纪律）。
     *
     * <p>0.3.1 的写法是 {@code activeRecipeTypeIndex = 下一个}，但那个字段每 tick 被「跟随控制器」改写
     * ⇒ 玩家看到车床、指定下去的却是切割机（队长实测的 bug）。游标不受那条跟随影响，问题即消失。
     */
    public void cycleRecipeType(boolean forward) {
        List<GTRecipeType> types = getAvailableRecipeTypes();
        if (types.isEmpty()) {
            return;
        }
        int size = types.size();
        int base = modeCursorIndex >= 0 && modeCursorIndex < size ? modeCursorIndex : activeRecipeTypeIndex;
        setRecipeType(forward ? (base + 1) % size : (base - 1 + size) % size);
    }

    public void setRecipeType(int index) {
        if (index >= 0 && index < getAvailableRecipeTypes().size()) {
            this.modeCursorIndex = index;
            this.modeCursorTouched = true;
            rememberBoundModeType();   // ★ 绑定模式持久化：把"本总成负责的模式"记成类型 id（落盘用）
        }
    }

    /** 模式页游标（要「指定」下去的那个模式）。客户端标签读它。 */
    public int getModeCursorIndex() {
        return modeCursorIndex;
    }

    /** 主机（控制器）当前的配方模式下标；拿不到控制器时回落到游标。供模式页第二行显示。 */
    public int getControllerRecipeTypeIndex() {
        IRecipeLogicMachine controller = firstRecipeLogicController();
        if (controller != null) {
            int live = controller.getActiveRecipeType();
            if (live >= 0) {
                return live;
            }
        }
        return modeCursorIndex;
    }

    // ---------- 绑定模式持久化（gtlmodular$lazy_bound_mode_type）----------

    /**
     * ★ <b>绑定模式持久化</b>：把"本总成负责的模式"（模式页游标对应的 {@code GTRecipeType}）
     * 的类型 id 记进 {@link #boundModeType} —— 它就是 {@link #saveCustomPersistedData} 写进
     * {@link #NBT_BOUND_MODE_TYPE} 的那份影子副本。
     *
     * <p><b>调用点</b>（都是"玩家选定模式"那一步，绝不每 tick 调）：
     * {@link #setRecipeType(int)}（模式页 ◀/▶ 与模式名芯片点击的落点）与
     * {@link #assignManualType(int, boolean)}（点"指定"时顺带刷新一次）。
     *
     * <p><b>只在"玩家真的拨过"时才算绑定</b>（{@code modeCursorTouched}）：没拨过时游标是"跟随控制器"的
     * 镜像 ⇒ 那不是绑定，不记也不写键（保持老档的"缺键 = 跟随控制器"语义）✓。
     *
     * <p><b>只在服务端记</b>（{@code isRemote()} 直接 return）：这个键是<b>服务端持久化</b>的，
     * 客户端那份 BE 只做显示（模式页读 {@code @DescSynced modeCursorIndex}），不需要也不应该参与落盘。
     * <b>本机这条守卫尤其必要</b>：本机的 ◀/▶ 回调刻意**没有** {@code isRemote()} 守卫
     * （见 {@code cycle(...)} 的注释：那是照搬现役机早期口径）⇒ {@code cycleRecipeType} → 本方法会在
     * 客户端也跑一次 ⇒ 必须在这里把客户端挡掉，绝不能把客户端镜像当成"玩家的绑定"写进字段 ✓。
     *
     * <p>幂等 + 廉价：id 没变时<b>不</b>调 {@code markDirty()}（稳态点击不产生多余的区块标脏）；
     * 越界/空表/空 id 一律安全回退（什么都不改、不抛异常）。
     */
    private void rememberBoundModeType() {
        if (isRemote()) {
            return;   // 服务端持久化字段：客户端不参与
        }
        if (!modeCursorTouched) {
            return;   // 没拨过 = 没绑定 ⇒ 不记（继续"跟随控制器"，键也不写）
        }
        List<GTRecipeType> types = getAvailableRecipeTypes();
        if (modeCursorIndex < 0 || modeCursorIndex >= types.size()) {
            return;   // 空表 / 下标越界 ⇒ 安全回退（绝不动既有状态）
        }
        GTRecipeType type = types.get(modeCursorIndex);
        if (type == null || type.registryName == null) {
            return;   // 类型本身没 id ⇒ 记不了（不写半个值）
        }
        if (!type.registryName.equals(boundModeType)) {
            this.boundModeType = type.registryName;
            // 让服务端 BE 把这次改动纳入本次落盘（MetaMachine.markDirty() → BlockEntity.setChanged()，
            // 取证：_census/_gtceu_MetaMachine_javap.txt:174-180 调 BlockEntity.m_6596_()）。
            // 只在 id 真变了时调 ⇒ 不会每次点击/每 tick 刷脏。
            markDirty();
        }
        this.boundModePendingResolve = false;   // 游标此刻就是权威值 ⇒ 不需要再"待解析"
    }

    /**
     * ★ <b>绑定模式持久化</b>：把 {@link #boundModeType} 落到 {@link #modeCursorIndex} 上，
     * 并置 {@link #modeCursorTouched} = {@code true}。
     *
     * <p><b>{@code modeCursorTouched} 就是"禁止跟随"的开关</b>（本轮已确认）——
     * 判定点有两处，都在 {@link #autoSwitchRecipeModes()}：
     * <ol>
     *   <li>「游标跟随控制器」：{@code if (!this.modeCursorTouched && this.modeCursorIndex != live)}
     *       （0.3.2 那一行）⇒ 置真后<b>绝不</b>把玩家绑定的模式改回主机模式 ✓；</li>
     *   <li>自动切换收尾：{@code if (!this.modeCursorTouched) this.modeCursorIndex = target;}
     *       ⇒ 自动切换也只动"没绑定"的游标 ✓。</li>
     * </ol>
     * 不需要另造开关。
     *
     * <p><b>为什么读档时不直接解析，而要放到每 tick</b>：读档期机器还没挂到 level / 控制器上
     * （本类既有注释已多次取证：{@code getControllers()} 为空）⇒ {@code getAvailableRecipeTypes()} 为空
     * ⇒ 那时<b>无法区分</b>"类型被移除"与"控制器还没就绪"。所以读档只记 id + 置
     * {@link #boundModePendingResolve}，由本方法在类型表非空时判定。
     *
     * <p>三种结果（全部不抛异常、不改任何既有键）：
     * <ol>
     *   <li>没有待解析（{@code boundModeType == null} 或已解析过）⇒ 立刻 return（一次引用比较 + 一次布尔判断）；
     *   <li>类型表为空 ⇒ 保持待解析、什么都不做，下一 tick 再试（<b>绝不在空表时判失效</b>）；
     *   <li>类型表非空：找到同 id ⇒ 游标 = 该下标 + {@code modeCursorTouched = true}（绑定生效 ✓，一步到位）；
     *       找不到 ⇒ <b>忽略并视为未绑定</b>：保持 {@code modeCursorTouched = false}（游标继续跟随控制器 =
     *       现有默认行为 ✓）、不报错、不抛异常；id 留在字段里（下次落盘照旧写回）⇒ 若该类型随控制器
     *       回来自动复活，属于"只增不改"的一侧，不会破坏任何已有数据。
     * </ol>
     *
     * <p>做成幂等 + O(类型数)：解析成功后 {@code boundModePendingResolve = false}
     * ⇒ 之后每 tick 只有一次布尔判断，零对象构造 ✓。
     */
    private void applyBoundModeTypeIfPossible() {
        if (boundModeType == null || !boundModePendingResolve) {
            return;
        }
        List<GTRecipeType> types = getAvailableRecipeTypes();
        if (types.isEmpty()) {
            return;   // 控制器/类型表还没就绪 ⇒ 下次再试（绝不在此判"类型已移除"）
        }
        for (int i = 0; i < types.size(); i++) {
            GTRecipeType type = types.get(i);
            if (type != null && boundModeType.equals(type.registryName)) {
                this.modeCursorIndex = i;
                this.modeCursorTouched = true;   // ★ 把"玩家已选定"这个状态一起恢复 ⇒ 跟随逻辑不再覆盖它
                this.boundModePendingResolve = false;
                return;
            }
        }
        // 类型表已就绪却没有这个 id（模组更新/移除、或换了个不带该模式的控制器）⇒ 视为未绑定：
        // 保持 modeCursorTouched = false ⇒ 完全走现有"跟随控制器"默认行为，不报错、不动既有键。
        this.boundModePendingResolve = false;
    }

    /**
     * ★ 惰性化改造点：保证 {@link #slotRecipeTypes} 的长度 ≥ 当前容量，<b>只增不减</b>。
     *
     * <p>现役机的 {@code ensureSlotRecipeTypeArray()} 是「长度不等于槽数就整份重建」
     * （因为它容量固定）；我们的容量会一路长上去，重建会把已经识别好的结果全丢掉，
     * 所以这里改成「缺多少补一个 null（= 还没尝试识别）」。
     */
    private void ensureRecipeTypeSlotCapacity() {
        int capacity = getCapacity();
        while (slotRecipeTypes.size() < capacity) {
            slotRecipeTypes.add(null);
        }
    }

    /** 读逐槽类型缓存；未初始化/未识别 → {@value #TYPE_NOT_TRIED}。 */
    private int recipeTypeCacheAt(int slot) {
        if (slot < 0 || slot >= slotRecipeTypes.size()) {
            return TYPE_NOT_TRIED;
        }
        Integer value = slotRecipeTypes.get(slot);
        return value == null ? TYPE_NOT_TRIED : value;
    }

    /** 写逐槽类型缓存（只增不减；越界写入自动补齐）。 */
    private void setRecipeTypeCache(int slot, int value) {
        if (slot < 0) {
            return;
        }
        ensureRecipeTypeSlotCapacity();
        while (slotRecipeTypes.size() <= slot) {
            slotRecipeTypes.add(null);
        }
        slotRecipeTypes.set(slot, value);
    }

    /** 某个样板槽的配方类型下标；识别不出来返回 -1。<b>手动指定的优先</b>。★ 照搬现役机 {@code recipeTypeOfSlot}。 */
    private int recipeTypeOfSlot(int slot) {
        List<GTRecipeType> types = getAvailableRecipeTypes();
        if (types.isEmpty() || slot < 0) {
            return -1;
        }
        // ① 手动指定优先
        int manual = manualTypeIndexOf(slot, types);
        if (manual >= 0) {
            return manual;
        }
        ensureRecipeTypeSlotCapacity();
        if (slot >= slotRecipeTypes.size()) {
            return -1;
        }
        int cached = recipeTypeCacheAt(slot);
        if (cached >= 0 && cached < types.size()) {
            return cached;
        }
        if (cached == TYPE_UNKNOWN) {
            return -1;   // 试过了，识别不出来，别再扫一遍配方表（这是扣料热路径）
        }
        int detected = detectRecipeType(slot, types);
        setRecipeTypeCache(slot, detected >= 0 ? detected : TYPE_UNKNOWN);
        return detected;
    }

    /** 手动指定的类型在<b>当前</b>类型表里的下标；没指定 / 类型已不在表里 → -1。★ 照搬现役机 {@code manualTypeIndexOf}。 */
    private int manualTypeIndexOf(int slot, List<GTRecipeType> types) {
        ResourceLocation id = manualSlotTypes.get(slot);
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < types.size(); i++) {
            GTRecipeType type = types.get(i);
            if (type != null && id.equals(type.registryName)) {
                return i;
            }
        }
        return -1;
    }

    /** 模式页上「要给哪个槽位指定」的槽号（1 起）。 */
    public int getManualAssignSlot() {
        return manualAssignSlot;
    }

    /** 模式页/样板槽总数（夹到本机硬上限，供 {@code IntInputWidget} 的 max 用）。 */
    public int getPatternSlotCount() {
        return Math.max(1, getInternalSlotCount());
    }

    public void setManualAssignSlot(int slot) {
        this.manualAssignSlot = Math.max(1, Math.min(getPatternSlotCount(), slot));
        refreshManualAssignDisplay();
    }

    public String getManualAssignTypeId() {
        return manualAssignTypeId;
    }

    /** 该槽自动识别出来的类型 id（空 = 没识别出来）。模式页用它跟手动指定并排显示。 */
    public String getManualAssignAutoTypeId() {
        return manualAssignAutoTypeId;
    }

    /**
     * 把「当前选中槽位的手动指定 / 自动识别」同步给客户端显示（服务端调）。
     * ★ 照搬现役机 {@code refreshManualAssignDisplay}，0.3.2 追加「自动识别」那一半。
     */
    private void refreshManualAssignDisplay() {
        if (isRemote()) {
            return;
        }
        ResourceLocation id = manualSlotTypes.get(manualAssignSlot - 1);
        this.manualAssignTypeId = id == null ? "" : id.toString();
        try {
            List<GTRecipeType> types = getAvailableRecipeTypes();
            int auto = autoDetectTypeOfSlot(manualAssignSlot - 1);
            GTRecipeType autoType = auto >= 0 && auto < types.size() ? types.get(auto) : null;
            this.manualAssignAutoTypeId =
                    autoType == null || autoType.registryName == null ? "" : autoType.registryName.toString();
        } catch (Throwable ignored) {
            this.manualAssignAutoTypeId = "";
        }
    }

    /**
     * 「该槽自检出来是哪种模式」——与 {@link #recipeTypeOfSlot(int)} <b>不同</b>：这里<b>无视手动指定</b>，
     * 只看自动识别（结果进同一份 {@link #slotRecipeTypes} 缓存，不重复扫配方表）。
     *
     * @return 类型下标；识别不出来返回 -1
     */
    private int autoDetectTypeOfSlot(int slot) {
        if (slot < 0 || slot >= getCapacity()) {
            return -1;
        }
        List<GTRecipeType> types = getAvailableRecipeTypes();
        if (types.isEmpty()) {
            return -1;
        }
        int cached = recipeTypeCacheAt(slot);
        if (cached >= 0 && cached < types.size()) {
            return cached;
        }
        if (cached == TYPE_UNKNOWN) {
            return -1;
        }
        int detected = detectRecipeType(slot, types);
        setRecipeTypeCache(slot, detected >= 0 ? detected : TYPE_UNKNOWN);
        return detected;
    }

    /**
     * ★★ 0.3.133【跨表并行 · 读侧类型校验】★★
     *
     * <p>{@code true}（默认）⇒ {@link LazyMEPatternTrait#getCachedGTRecipe()} 只把
     * 「{@code recipe.recipeType} ∈ 本机 {@link #getAvailableRecipeTypes()}」的缓存配方交给主机。
     *
     * <p><b>为什么必须有这一道</b>：0.3.1 事故的前提是「缓存里的旧模式配方会绕过谓词被主机直接取用」
     * （本仓既有取证逐字：`_自研猪咪兼容清单.md:574-576`「主机侧『这台舱室能跑哪些配方』学自
     * {@code getSlot2RecipesCache()}，而 gtlcore {@code GTRecipeLookupMixin.find()} 的快路径是
     * 『缓存集非空 ⇒ {@code iterator().next()} 直接返回、**不校验谓词**』」）。
     * 本闸门把「喂给主机的那份集合」在**读侧**按类型收干净 —— 于是「非空即取一条」再怎么不校验谓词，
     * 也取不到一条不属于本机任何可用配方表的配方 ⇒ 该前提被堵死，而**不需要**再靠「换模式就清光全部缓存」
     * 这种会连坐另一张表的粗暴手段。
     *
     * <p>⚠ 判据刻意<b>不是</b>「控制器当下那一个模式」：跨表候选恰恰要求在「主机模式 = A」时
     * A/B 两表的缓存都还能进候选池（{@code NanochipCandidateSearch.searchIsolating} 逐表调
     * {@code type.getLookup().getRecipeIterator(machine, r -> true)}）⇒ 按「单个当前模式」过滤会
     * 把另一张表全部掐死。这里用的是「∈ 本机可用类型表」这个**上界**（只收紧到本机自己的表）。
     *
     * <p><b>一行回退</b>：改成 {@code false} ⇒ 读侧逐字回到改动前（不过滤）；
     * 配合把 {@link #KEEP_FOREIGN_SLOT_RECIPE_CACHE_ON_MODE_SWITCH} 也改回 {@code false}
     * 即**逐字回到改动前**的两条行为。
     */
    public static final boolean FILTER_CACHED_RECIPES_BY_AVAILABLE_TYPE_ON_READ = true;

    /**
     * ★★ 0.3.133【跨表并行 · 换表只失效本表】★★
     *
     * <p>{@code true}（默认）⇒ 真的换模式时（{@link #lockRecipeTypeForSlot(int)} /
     * {@code autoSwitchRecipeModes()}），只把「目标模式那一张表」的槽配方缓存失效，
     * <b>保留</b>另一张表的缓存 ⇒ 下一次 {@code searchIsolating} 在**同一 tick** 就能同时拿到两张表的候选。
     * {@code false} ⇒ 逐字回到 0.3.2 的 {@link #clearAllSlotRecipeCaches(int)} 全清语义。
     *
     * <p>一行回退：把本常量改成 {@code false}。
     */
    public static final boolean KEEP_FOREIGN_SLOT_RECIPE_CACHE_ON_MODE_SWITCH = true;

    /** 清掉该槽「主机已经配上过的 GT 配方缓存」（见 {@link #assignManualType(int, boolean)} 里的取证说明）。 */
    private void clearSlotRecipeCache(int slot) {
        try {
            ((LazyMEPatternTrait) getMETrait()).clearSlotCache(slot);
        } catch (Throwable ignored) {
            // 清缓存失败只是"少清一次"，绝不影响指定本身
        }
    }

    /**
     * 清掉「刚被换掉的那张表」的槽 GT 配方缓存 —— 在我们真的把主机切到另一个模式时调。
     *
     * <p>理由同 {@link #clearSlotRecipeCache(int)}：缓存里可能留着"上一个模式被配上过的配方"，
     * 而 GTRecipeLookupMixin 的快路径不校验谓词 ⇒ 切了模式却还在跑旧模式的配方。
     *
     * <p>★★ 0.3.133【跨表并行】★★ <b>不再全清</b>：全清会把**另一张表**的候选一起清掉，
     * 于是跨表并行永远起不来（A/B 两表在同一 tick 都可见的前提被破坏）。
     * 现在只失效 {@code activeTypeIndex} 那一张表（+ 类型识别不出来的槽），
     * 其它表的条目原样留着 —— 而「读侧类型校验」
     * （{@link #FILTER_CACHED_RECIPES_BY_AVAILABLE_TYPE_ON_READ}）保证了
     * 留下的条目绝不会被当成"当前模式能做"的配方喂给主机 ⇒ 0.3.1 那个事故的前提依旧被堵住。
     *
     * <p><b>一行回退</b>：把 {@link #KEEP_FOREIGN_SLOT_RECIPE_CACHE_ON_MODE_SWITCH} 改成 {@code false}
     * ⇒ 逐字回到 0.3.2 的 {@code trait.getSlot2RecipesCache().clear()} 全清语义。
     */
    private void clearAllSlotRecipeCaches() {
        clearAllSlotRecipeCaches(getActiveRecipeTypeIndex());
    }

    /**
     * ★ 0.3.133：带「换过去的目标模式下标」的全清/选择性失效。
     *
     * @param activeTypeIndex 目标模式下标（&lt;0 ⇒ 认不出来 ⇒ 按"全清"处理，即回到旧语义）
     */
    private void clearAllSlotRecipeCaches(int activeTypeIndex) {
        try {
            LazyMEPatternTrait trait = (LazyMEPatternTrait) getMETrait();
            if (!KEEP_FOREIGN_SLOT_RECIPE_CACHE_ON_MODE_SWITCH) {
                trait.getSlot2RecipesCache().clear();   // ★ 一行回退后的原语义（0.3.2）
                return;
            }
            List<GTRecipeType> types = getAvailableRecipeTypes();
            GTRecipeType current = activeTypeIndex >= 0 && activeTypeIndex < types.size()
                    ? types.get(activeTypeIndex) : null;
            if (current == null) {
                trait.getSlot2RecipesCache().clear();
                return;
            }
            // 只删"当前这张表"的条目（以及识别不出类型的条目）——另一张表的条目原样保留。
            ObjectIterator<Int2ReferenceMap.Entry<ObjectSet<GTRecipe>>> iterator =
                    Int2ReferenceMaps.fastIterator(trait.getSlot2RecipesCache());
            while (iterator.hasNext()) {
                Int2ReferenceMap.Entry<ObjectSet<GTRecipe>> entry = iterator.next();
                ObjectSet<GTRecipe> recipes = entry.getValue();
                if (recipes == null || recipes.isEmpty()) {
                    continue;
                }
                recipes.removeIf(recipe -> recipe == null
                        || recipe.recipeType == null
                        || recipe.recipeType == current);
                if (recipes.isEmpty()) {
                    iterator.remove();
                }
            }
        } catch (Throwable ignored) {
            // 同上
        }
    }

    /**
     * ★ 0.3.133【读侧类型校验】这条缓存配方属不属于<b>本机可用配方类型表</b>里的某一张表。
     *
     * <p>{@link #getAvailableRecipeTypes()} 为空（控制器还没就绪 / 结构未成形）或本开关关掉 ⇒
     * 一律返回 {@code true} = <b>不过滤</b>（fail-open：宁可维持改动前的行为，也绝不因为读不到类型表
     * 而把候选池清空 ⇒ 不会造成"机器完全不开工"这类更难查的症状）。
     */
    private boolean recipeTypeIsUsableByThisMachine(GTRecipe recipe) {
        if (!FILTER_CACHED_RECIPES_BY_AVAILABLE_TYPE_ON_READ || recipe == null) {
            return true;
        }
        try {
            GTRecipeType type = recipe.recipeType;
            if (type == null) {
                return false;   // 没有类型 = 不可能是任何一张表里的配方 ⇒ 绝不喂给主机
            }
            List<GTRecipeType> types = getAvailableRecipeTypes();
            if (types.isEmpty()) {
                return true;    // fail-open（见上）
            }
            return types.contains(type);
        } catch (Throwable ignored) {
            return true;        // 读类型表抛异常 ⇒ 同样 fail-open
        }
    }

    /**
     * 把<b>当前选中的模式</b>手动指定给某个样板槽位（或清除指定）。
     * 只是登记「这个槽的样板属于哪种类型」，不动样板本身。
     *
     * <p>★ 照搬现役机 {@code assignManualType}：三条路径里的「手动指定 / 手动清除」都走这里；
     * 指定变了就把该槽的识别缓存与拉黑表作废（否则会自动识别出的旧结论继续压着手动指定）。
     */
    public void assignManualType(int slotNumber, boolean clear) {
        try {
            List<GTRecipeType> types = getAvailableRecipeTypes();
            int slot = slotNumber - 1;
            if (slot < 0 || slot >= getCapacity()) {
                return;
            }
            if (clear) {
                manualSlotTypes.remove(slot);
            } else {
                if (types.isEmpty()) {
                    return;
                }
                // ★ 0.3.2：读**模式页游标**（玩家在界面上看到的那个模式），
                //   不再读 activeRecipeTypeIndex（那个每 tick 被"跟随控制器"改写 ⇒ 永远指向主机当前模式）。
                int index = Math.max(0, Math.min(types.size() - 1, modeCursorIndex));
                GTRecipeType type = types.get(index);
                if (type == null || type.registryName == null) {
                    return;
                }
                manualSlotTypes.put(slot, type.registryName);
            }
            // 指定变了 → 该槽的识别缓存与拉黑表都作废
            setRecipeTypeCache(slot, TYPE_NOT_TRIED);
            // ★ 0.3.2（队长实测「钻石卡死在缓冲里」的那个事故的收尾）：
            //   只清"类型缓存"不够 —— 主机侧「这台舱室能跑哪些配方」是从 **GT 配方缓存**
            //   （{@code getSlot2RecipesCache()}）学的，而 GTRecipeLookupMixin 的快路径是
            //   「缓存集非空就 iterator().next() 直接返回、不校验谓词」。
            //   于是"上次用错模式时被主机配上过的配方"（比如切割机·钻石杆）会一直留着，
            //   玩家把模式改对了它仍然把那条跑不动的配方喂给主机 ⇒ 料卡在缓冲里、产物不回流。
            //   这里连同该槽的 GT 配方缓存一起清掉，逼主机按新模式重新搜一次。
            clearSlotRecipeCache(slot);
            autoSwitchBlockedUntil.clear();
            refreshManualAssignDisplay();
            rememberBoundModeType();                 // ★ 绑定模式持久化：指定动作顺带刷新一次绑定 id（只在玩家拨过时生效）
            syncCustomNameToMode(modeCursorIndex);   // ★ 名字跟随模式（照搬现役机同一行）
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：手动指定配方模式失败 pos={} 槽={} 原因={}",
                    getPos(), slotNumber, failure.toString());
        }
    }

    /** 这个槽有没有被手动指定过（UI 提示用）。 */
    public boolean hasManualType(int slot) {
        return manualSlotTypes.containsKey(slot);
    }

    /**
     * 「读样板的配方类型」：把样板解成 AE 样板，然后在每一种配方类型的配方里找<b>身份匹配</b>的那一条
     * （{@link #patternIdentityMatches}：非电路输入被样板输入覆盖 + 配方主产物落在样板输出里）。
     *
     * <p>★ 逐条照搬现役机 {@code detectRecipeType}（含「两遍扫描、第一遍跳过主机表」那条 2026-09-21 的修复）：
     * 第一遍跳过主机表（唯一命中就直接采用，不再被它搅成歧义）；只有第一遍一个都不命中时，
     * 第二遍才回落到主机表；第一遍内部若两个非主机表都命中，仍判歧义返回 -1（不猜）。
     *
     * <p>解样板用的也是现役机同一条路 —— 基类的 {@code getRealPatternHelper().processPatternWithCircuit(...)}，
     * 与「推样板时认槽位」用的身份是同一份（已剥电路、按 {@code keepByProduct} 裁副产物）。
     */
    private int detectRecipeType(int slot, List<GTRecipeType> types) {
        Level level = getLevel();
        if (level == null || slot < 0 || slot >= getCapacity()) {
            return -1;
        }
        ItemStack stack = patternInventory.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return -1;
        }
        IPatternDetails pattern;
        try {
            pattern = getRealPatternHelper().processPatternWithCircuit(stack, count -> {
            }, level, keepByProduct);
        } catch (Throwable ignored) {
            return -1;
        }
        if (pattern == null) {
            return -1;
        }
        int withoutHost = scanTypesForMatch(types, pattern, level, true);
        if (withoutHost != MATCH_NONE) {
            supplyResolvedRecipeToMachine(slot, withoutHost);   // ★ 2026-10-27【直供配方】唯一命中 ⇒ 直接交给机器
            return withoutHost;   // 唯一命中（>=0）或歧义（-1），都按第一遍的结论
        }
        int hostTableOnly = scanTypesForMatch(types, pattern, level, false);
        supplyResolvedRecipeToMachine(slot, hostTableOnly);     // ★ 2026-10-27【直供配方】第二遍（只看主机表）
        return hostTableOnly;
    }

    /**
     * 在 {@code types} 里逐类型找「身份匹配」的配方。★ 照搬现役机 {@code scanTypesForMatch}。
     *
     * @param skipHostTable {@code true} = <b>跳过</b>主机表（{@code NanochipRecipeTypes.ASSEMBLY_COMPLEX}）；
     *                      {@code false} = <b>只看</b>主机表（两遍扫描的第二遍）。
     * @return 唯一命中的类型下标／{@code -1}（≥2 个类型都命中 = 歧义，不猜）／{@link #MATCH_NONE}
     */
    private int scanTypesForMatch(List<GTRecipeType> types, IPatternDetails pattern, Level level, boolean skipHostTable) {
        this.lastMatchedSlotRecipe = null;   // ★ 直供配方：本遍「唯一命中」时才记下那条配方（无命中/歧义 ⇒ 保持 null）
        int matched = MATCH_NONE;
        for (int i = 0; i < types.size(); i++) {
            GTRecipeType type = types.get(i);
            if (type == null) {
                continue;
            }
            boolean isHostTable = type == com.gtlmodular.common.data.nanochip.NanochipRecipeTypes.ASSEMBLY_COMPLEX;
            if (isHostTable == skipHostTable) {
                continue;   // 第一遍跳过主机表；第二遍只看主机表
            }
            for (GTRecipe recipe : level.getRecipeManager().getAllRecipesFor(type)) {
                if (patternIdentityMatches(recipe, pattern)) {
                    if (matched >= 0 && matched != i) {
                        this.lastMatchedSlotRecipe = null;   // ★ 直供配方：歧义 ⇒ 不直供（绝不给机器喂猜出来的配方）
                        return -1;   // 两种类型都能匹配 → 歧义，交给手动指定
                    }
                    matched = i;
                    this.lastMatchedSlotRecipe = recipe;     // ★ 直供配方：唯一命中 ⇒ 记住它（detectRecipeType 收尾时交给机器）
                    break;
                }
            }
        }
        return matched;
    }

    // ================= ★ 2026-10-27【直供配方】：把「该槽对应的那条配方」直接交给机器 =================
    // 依据：_census/直供配方_实施记录.md（§②/§③ 逐行取证）。与现役机 MoltenPatternAssemblyPartMachine
    // 的同一段**逐条对齐**（那台是 gtlcore 具体类 `MEPatternBufferPartMachine$MEPatternTrait`，
    // 本台是我们自己的 `LazyMEPatternTrait`；两者都实现 `IMEPatternTrait`，
    // `getMETrait()` 的静态类型都是 `IMEPatternTrait` ⇒ 调用同一个官方 API，无需强转）。
    // 通道：gtlcore `IMEPatternTrait.getCachedGTRecipe()`
    //   → gtladditions `GTRecipeLookupMixin.getRecipeIterator`（@Overwrite，jar 内已挂载）
    //   → `IAdditionalRecipeIterator.setAdditionalRecipes(...)`（`_enh_extract/gtladditions-…mixins_javap.txt:2215-2302`）
    //   ⇒ 配方作为**附加候选**进机器本轮候选集，机器不必靠原料树把它搜出来；
    //     判据是 `cacheRecipe[slot] && internalSlot.isActive()`（本类 `getCachedGTRecipe():2501-2541`
    //     同款 + 阈值判据）——**不看 `getActiveSlots()` ⇒ 不受查找侧收窄影响**。
    // 机器侧（NAC）一个字都不改：这条通道机器本来就在消费，我们只是往里喂（零 mixin、零 @Persisted）。

    /**
     * ★ <b>「直供配方」总开关</b>（★ 2026-10-27 新增）：{@code true}（<b>默认开启</b>）⇒ 样板进槽、
     * 类型被<b>唯一</b>识别出来时，把那条 {@code GTRecipe} 写进该槽的配方缓存
     * （{@link #supplyResolvedRecipeToMachine}）；{@code false} ⇒ 本功能整体不执行，
     * 行为<b>逐字退回</b>加这段代码之前（= 机器照旧自己遍历配方表去找）。
     *
     * <p>默认开的理由、一行回退方式、与现役机口径必须一致这三条，
     * 与 {@code MoltenPatternAssemblyPartMachine#DIRECT_SLOT_RECIPE_SUPPLY} 的 javadoc 逐条相同
     * （那边写全了，这里不重复）：纯增量 + 全链路 fail-open + 不做就永远没解。
     *
     * <p><b>本台特有的一个边界（诚实登记）</b>：本台的 {@code LazyMEPatternTrait.getCachedGTRecipe()}
     * 多一道「本槽配方数 ≥ 该槽阈值（{@code cacheRecipeCountAt}）」的闸门（`:2535-2537`）——
     * 阈值<b>默认 1</b>（gtlcore 构造器 {@code Arrays.fill(cacheRecipeCount, (byte) 1)}，本类注释 `:2575`），
     * 此时我们喂进去的这 1 条就已达标 ⇒ 直供生效；若玩家把某槽阈值调到 &gt;1，
     * 该槽的直供会与 gtlcore 自己的语义一起"继续攒"，<b>不会</b>被特判绕过（这是刻意与上游一致，不是缺陷）。
     */
    public static boolean DIRECT_SLOT_RECIPE_SUPPLY = true;

    /**
     * 最近一次「唯一命中」的那条配方 —— {@link #scanTypesForMatch} 写的**只读中转**。
     * <p>无命中 / 歧义 ⇒ {@code null}。不落盘、不同步（无 {@code @Persisted} / 无 {@code @DescSynced}）。
     */
    private GTRecipe lastMatchedSlotRecipe;

    /**
     * ★ 2026-10-27【直供配方】：把「该槽对应的那条配方」写进该槽的配方缓存 ——
     * 机器随后从 {@code getCachedGTRecipe()} 把它当<b>附加候选</b>直接拿到，不必遍历配方表去找它。
     *
     * <p>五条 fail-open 纪律与 {@code MoltenPatternAssemblyPartMachine#supplyResolvedRecipeToMachine}
     * 逐条相同（开关/唯一命中/客户端/已有缓存不覆盖/任何异常都退回今天的行为），此处不再重复。
     * 两台机的差别只有一处：本台写的是**我们自己的** {@code LazyMEPatternTrait} 的
     * {@code slot2RecipesCache}（经由 {@code IMEPatternTrait.setSlotCacheRecipe} 这个官方口），
     * 现役机写的是 gtlcore 具体 trait 的 {@code recipeMultipleCacheMap}。
     *
     * @param slot             样板槽下标
     * @param detectedTypeIndex {@link #detectRecipeType(int, List)} 的结论；必须 {@code >= 0} 才算「唯一识别」
     */
    private void supplyResolvedRecipeToMachine(int slot, int detectedTypeIndex) {
        if (!DIRECT_SLOT_RECIPE_SUPPLY || detectedTypeIndex < 0) {
            return;   // ① 没识别出唯一类型（含歧义 -1）⇒ 不直供
        }
        GTRecipe recipe = this.lastMatchedSlotRecipe;
        if (recipe == null || isRemote()) {
            return;   // ② 没有唯一命中的配方 / ③ 客户端 ⇒ 不直供
        }
        try {
            if (getMETrait().hasCacheInSlot(slot)) {
                return;   // ④ 该槽已有配方缓存 ⇒ 不重复写、不覆盖上游记账
            }
            getMETrait().setSlotCacheRecipe(slot, recipe);
        } catch (Throwable ignored) {
            // ⑤ 直供失败 = 逐字退回今天的行为（机器照旧自己搜配方表）⇒ 绝不影响扣料
        }
    }

    /**
     * 把控制器的搜索端<b>锁到这个槽位的模式</b>上。★ 照搬现役机 {@code lockRecipeTypeForSlot}，
     * 含 2026-09-26 那条<b>必须原样保留</b>的修复：
     *
     * <p>识别不出类型（{@code index < 0}）时<b>绝不回落、绝不动控制器</b>
     * （老实现会回落到缓存下标 {@code activeRecipeTypeIndex} 并强行设置控制器 ⇒
     * 「玩家手动设成车床，一推不认识的样板就被抢回卷板机」）。这里是 fail-open：认不出来就不动。
     *
     * <p>另外两条安全线也一并照搬：控制器已经在目标模式时只更新缓存不动 logic；
     * {@code logic.isWorking()} 时不动（配方一旦开始输入就已扣掉，这时候 reset 会让已扣的料蒸发）。
     *
     * <p>入参用「样板所在槽」，从 {@code pushPattern} 里调（与现役机调用点一致）。
     */
    /** ★★ 0.3.132【止血开关】★★ 见 {@code lockRecipeTypeForSlot} 里那段注释。 */
    public static final boolean ALLOW_MODE_SWITCH_WITH_RUNNING_BATCHES = false;

    private void lockRecipeTypeForSlot(int slot) {
        try {
            List<GTRecipeType> types = getAvailableRecipeTypes();
            if (types.size() <= 1) {
                return;
            }
            int index = recipeTypeOfSlot(slot);
            if (index < 0 || index >= types.size()) {
                // ★★ fail-open：认不出来就绝不动控制器（见方法注释，这条不许改回去）★★
                return;
            }
            IRecipeLogicMachine controller = firstRecipeLogicController();
            if (controller == null) {
                return;
            }
            if (controller.getActiveRecipeType() == index) {
                this.activeRecipeTypeIndex = index;
                return;
            }
            RecipeLogic logic = controller.getRecipeLogic();
            if (logic.isWorking()) {
                return;
            }
            // ★★ 0.3.132【止血 · 队长裁决 ①②③④ 里的第 ② 步】★★
            //   **为什么光判 `isWorking()` 不够**：`RecipeLogic.isWorking()` 的判据是
            //   **`status == WORKING`**（字节码逐字见 `_census/_javap_simple_recipelogic.txt:819-828`），
            //   而 NAC 的独立线程逻辑在「**有批次，但在等电 / 等料**」时置的是 **WAITING**
            //   ⇒ **等的瞬间 `isWorking() == false` ⇒ 闸门打开 ⇒ 下面的 `logic.resetRecipeLogic()`
            //     把【整张批次表】丢掉（含已经扣掉的料，不退）**。
            //   取证：`NanochipThreadedRecipeLogic.resetRecipeLogic`（`:1859-1862`）
            //     `super.resetRecipeLogic(); batches.clear(); batchStates.clear();`
            //     并在丢弃非空表时打 `NAC 独立线程：配方逻辑被重置 ⇒ 丢弃 N 条在跑批次`。
            //   ⇒ 这里补一道：**只要还有批次在跑（或已开好尚未收尾），就不许切模式**。
            //   ⚠ 本步只"止血"：它**不**让两个表同时跑（跨表并行是第 ③ 步的事），
            //     只是**不再无故丢掉整张批次表**。代价 = 换模式可能被推迟到当前批次跑完（更保守）。
            //   一行回退：把 `ALLOW_MODE_SWITCH_WITH_RUNNING_BATCHES` 改成 true。
            if (!ALLOW_MODE_SWITCH_WITH_RUNNING_BATCHES
                    && logic instanceof com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic threaded
                    && threaded.runningThreadCount() > 0) {
                return;
            }
            controller.setActiveRecipeType(index);
            // 只切类型不够：GT 的 RecipeLogic 会优先继续跑 lastRecipe，必须 reset 才会按新模式重新搜
            logic.resetRecipeLogic();
            // ★ 0.3.2：模式真的换了 ⇒ 把主机学到的 GT 配方缓存清一遍。
            //   否则「上一个模式里被配上过的那条配方」还在缓存里，而快路径不校验谓词
            //   ⇒ 主机切了模式却仍旧拿到旧模式的配方（0.3.1 实测：料卡在缓冲里、产物不回流）。
            //   ★★ 0.3.133【跨表并行】★★ 改成**只失效"换过去的那张表"**（另一张表的候选必须留着，
            //   否则 A/B 两表永远不可能在同一 tick 同时可见）；"快路径不校验谓词"这个前提由
            //   `LazyMEPatternTrait.getCachedGTRecipe()` 里新增的**读侧类型校验**堵住（见那两个开关的 javadoc）。
            //   一行回退：`KEEP_FOREIGN_SLOT_RECIPE_CACHE_ON_MODE_SWITCH = false`。
            clearAllSlotRecipeCaches(index);
            this.activeRecipeTypeIndex = index;
        } catch (Throwable ignored) {
            // 锁模式失败只是"少锁一次"，绝不打断推样板本身
        }
    }

    // ==================== P2-② 自动切换配方模式 ====================

    /**
     * 自动模式轮询：当前模式已经跑不动（既不 WORKING，处于 IDLE/WAITING）时，
     * 扫描仓里其它「有料在槽位等着」的样板槽，把控制器切到那个模式并重置配方逻辑。
     * 一台机器可以同时挂多种模式的样板、轮流自动跑。
     *
     * <p><b>★ 本方法并进已有的 {@code lazyTick()} 服务端订阅</b>（不新增第二个
     * {@code subscribeServerTick}，避免同一件事被订阅两次）。
     *
     * <p>判据顺序（逐条照搬现役机 {@code autoSwitchTick}，第 1048-1127 行）：
     * <ol>
     *   <li>客户端直接 return；</li>
     *   <li><b>名字跟随</b>（{@link #syncCustomNameToMode(int)}）：<b>每 tick 都调</b>（★ 2026-10-04b；
     *       原来"只在名字为空/等于兜底串时才调"⇒ 写一次之后名字再也不会跟模式变 ✗），
     *       真正要不要写由方法内部的幂等判据 + 廉价性能闸决定 ⇒ 机器模式一变名字立刻跟 ✓ ——
     *       与自动切换开关无关（起名是显示层的事）；</li>
     *   <li><b>下标跟随控制器</b>（只读同步）：玩家在主机的模式页上改了模式、或结构重成形把模式重置时，
     *       我们的缓存不能停在旧值，否则锁模式/起名/模式页都会用错模式；</li>
     *   <li>{@code GTLModularConfig.isAutoRecipeModeSwitchEnabled()} 为 false → 到此为止
     *       （即：开关关掉后，只有"起名 + 跟随"仍然生效，切换策略完全停手）；</li>
     *   <li>类型表 ≤ 1 或拿不到控制器 → return；</li>
     *   <li>{@code logic.isWorking()} → 清掉待观察窗口并 return（跑动中打断会让已扣输入蒸发）；</li>
     *   <li><b>观察窗口</b>：上一次切过去的类型若在 {@value #AUTO_SWITCH_GRACE_TICKS} tick 内还没开跑，
     *       就把它拉黑 {@value #AUTO_SWITCH_BLOCK_TICKS} tick，然后 return；</li>
     *   <li>冷却：{@value #AUTO_SWITCH_INTERVAL_TICKS} tick 才扫一次（别每 tick 扫槽）；</li>
     *   <li>{@link #findModeWithWaitingStock(List, int, long)} 找目标；<b>返回 -1 时绝不回退抢占控制器</b>
     *       —— 这条是队长报过的 bug 修复，原样保留；</li>
     *   <li>真正切换：{@code setActiveRecipeType} + {@code logic.resetRecipeLogic()} +
     *       记下标 + 名字跟随 + 记待观察窗口 + 记一条 INFO。</li>
     * </ol>
     */
    private void autoSwitchRecipeModes() {
        try {
            if (isRemote()) {
                return;
            }
            // ★ 2026-10-14（绑定模式持久化）：读档拿到的"本总成负责的模式"在这里落到模式页游标上
            //   （见 applyBoundModeTypeIfPossible 的注释）。放在本方法**最前面**的两条理由：
            //   ① 读档期类型表还没就绪 ⇒ 那时只记 id，这里等类型表就绪后补解析；
            //   ② 名字跟随（下面的 syncCustomNameToMode）取的就是本总成的模式游标 ⇒ 先解析再起名，
            //      读档后第一 tick 就起对名字，不会先按"类型表第 0 个"误命名一次再改回来 ✓。
            //   解析成功即置 modeCursorTouched=true ⇒ 下面「游标跟随控制器」绝不再覆盖它；
            //   已解析过 / 无绑定时它只做一次引用比较 + 一次布尔判断（零开销）✓。
            applyBoundModeTypeIfPossible();
            // ★ 名字跟随（照搬现役机：判据 ="这个名字归我们管"，手填过的名字绝不覆盖）。
            //   一开始取不到控制器/类型表时静默返回，下一 tick 再试 ⇒ 结构一成形就会自动起名（自愈）。
            //   ★ 2026-10-04：判据扩成"名字为空 **或** 等于上游 Enhancedcore 的兜底串
            //   「未命名加工模式」"（isUpstreamPlaceholderName）—— 否则上游一写上那四个字，
            //   这里就再也不填名了（那正是本补丁要修的症状）；玩家手填的名字仍然绝不覆盖 ✗。
            //   ★ 2026-10-04b（**本轮改动：名字跟随当前机器模式**）：这里**去掉了守卫**
            //   ——原来只在"名字还空着 / 等于兜底串"时才调本方法 ⇒ 名字写下去一次之后就再也进不来，
            //   机器从「纳米芯片组装复合体」切到「纳米芯片换算」名字却一动不动（队长本轮报的症状 ✗）。
            //   现在**每 tick 都调**，"要不要真写"由方法内部的幂等判据 + 廉价性能闸决定
            //   ⇒ 模式一变名字立刻跟 ✓、切回来再改回来 ✓；
            //   ★ 2026-10-15（本轮重做）：判据不再是任何"白名单"，而是**按「本总成 + 模式」查名字表**
            //   （{@link #syncCustomNameToMode(int)}）：有名的模式沿用、没名的写一次显示名、
            //   玩家改过名则永久让位 ✗。**名字与"控制器当前模式"解耦** ✗（主机切模式不改名字）。
            //   ★ 2026-10-14（用户口径「每个总成绑定一个固定模式」）：这里传进去的 hint 只是**兜底**，
            //   真正决定名字的是**本总成自己的模式**（模式页选中模式 {@link #modeCursorIndex}，
            //   见 {@link #currentModeIndexForNaming(int)}）⇒ 主机在跑别的模式时，本行**不会**改名字 ✓；
            //   玩家在**这台总成上**换了自己负责的模式 ⇒ 下一次服务端 tick 就把名字换成新模式名 ✓。
            syncCustomNameToMode(activeRecipeTypeIndex);
            // ★ 让 activeRecipeTypeIndex 跟随控制器（只读同步，自动切换关掉也照样执行）。
            //   0.3.2：模式页游标 modeCursorIndex 在玩家**还没拨过**模式页时也一起跟随
            //   （打开界面看到的默认值就等于「机器现在的模式」）；玩家拨过之后游标归玩家管，
            //   绝不被这行改回去 —— 这一行就是 0.3.1「选车床、指定下去却是切割机」的根因。
            IRecipeLogicMachine controller = firstRecipeLogicController();
            if (controller != null) {
                int live = controller.getActiveRecipeType();
                if (this.activeRecipeTypeIndex != live) {
                    this.activeRecipeTypeIndex = live;
                }
                if (!this.modeCursorTouched && this.modeCursorIndex != live) {
                    this.modeCursorIndex = live;
                }
            }
            if (!GTLModularConfig.isAutoRecipeModeSwitchEnabled()) {
                return;
            }
            List<GTRecipeType> types = getAvailableRecipeTypes();
            if (types.size() <= 1) {
                return;
            }
            if (controller == null) {
                return;
            }
            RecipeLogic logic = controller.getRecipeLogic();
            if (logic.isWorking()) {
                pendingAutoSwitchType = -1;   // 跑起来了，观察结束
                return;
            }
            Level level = getLevel();
            if (level == null) {
                return;
            }
            long now = level.getGameTime();
            if (pendingAutoSwitchType >= 0) {
                if (now < pendingAutoSwitchDeadline) {
                    return;   // 还在观察窗口里，先别乱切
                }
                autoSwitchBlockedUntil.put(pendingAutoSwitchType, now + AUTO_SWITCH_BLOCK_TICKS);
                pendingAutoSwitchType = -1;
            }
            if (autoSwitchCooldown > 0) {
                autoSwitchCooldown--;
                return;
            }
            autoSwitchCooldown = AUTO_SWITCH_INTERVAL_TICKS;

            int active = controller.getActiveRecipeType();
            int target = findModeWithWaitingStock(types, active, now);
            // ★ 0.3.4：把「自动切模式检查」诊断也搬进惰性机（现役机本来就有这一行）。
            //   只在与上次不同的口径下打一条 —— 这次「不会自动切换」之所以只能靠读代码反推，
            //   就是因为惰性机这条路径之前**一条日志都没有**（只有真切换时才打）。
            long idleTicks = activeModeBusySince < 0L ? 0L : now - activeModeBusySince;
            String signature = active + "|" + target + "|" + logic.getStatus() + "|" + diagActivePatterns
                    + "|" + diagActiveStock + "|" + diagActiveCold + "|" + (idleTicks / 100);
            if (!signature.equals(lastAutoSwitchSignature)) {
                lastAutoSwitchSignature = signature;
                GTLModular.LOGGER.info(
                        "[gtlmodular] 惰性猪咪自动切模式检查: 当前={}/{} 状态={} 找到目标={} 当前模式样板={} 其中有料={} 未认配方={} 空转={}tick（满 {}tick 才允许切走）",
                        active, types.size(), logic.getStatus(), target, diagActivePatterns, diagActiveStock,
                        diagActiveCold, idleTicks, AUTO_SWITCH_STOCKLESS_TICKS);
            }
            if (target < 0 || target == active) {
                // ★ -1 = 没有任何「类型已识别 + 槽里有料等着」的候选 ⇒ 原样保留（不回退、不抢占控制器）。
                return;
            }
            controller.setActiveRecipeType(target);
            logic.resetRecipeLogic();
            // ★ 0.3.2：同 lockRecipeTypeForSlot —— 换模式就清掉主机学到的旧配方缓存
            //   ★★ 0.3.133【跨表并行】★★ 同上：只失效"换过去的 target 这张表"，
            //   另一张表的候选留着 ⇒ searchIsolating 同一 tick 能同时拿到两表候选。
            clearAllSlotRecipeCaches(target);
            this.activeRecipeTypeIndex = target;
            if (!this.modeCursorTouched) {
                this.modeCursorIndex = target;   // 玩家没拨过模式页 ⇒ 游标跟着机器走
            }
            syncCustomNameToMode(target);   // ★ 名字跟随模式
            this.pendingAutoSwitchType = target;
            this.pendingAutoSwitchDeadline = now + AUTO_SWITCH_GRACE_TICKS;
            GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪自动切换配方模式: {} -> {} pos={}",
                    active, target, getPos());
        } catch (Throwable ignored) {
            // tick 路径一律不许把异常抛出去
        }
    }

    /**
     * 找一个「类型已识别 + 槽里有料等着 + 不是当前模式 + 没被拉黑」的模式下标。
     * ★ 逐条照搬现役机 {@code findModeWithWaitingStock}（第 1130-1175 行），含两条关键语义：
     *
     * <ol>
     *   <li><b>第一遍：当前模式「还有活」就绝不切走</b>（直接返回 -1）。0.3.4 把"有活"的判据从
     *       现役机的「还有样板」改成了两条（队长 2026-09-26 晚实测：「他现在不会自动切换机器模式了」——
     *       现役机那条判据会让"多模式样板常驻仓里"这种最正常用法**永远切不动**）：
     *       <ol>
     *         <li>该模式的槽里<b>还有料等着</b> ⇒ 有活，绝不走；</li>
     *         <li>该模式的槽里没料了 ⇒ 先<b>空转 {@value #AUTO_SWITCH_STOCKLESS_TICKS} tick</b> 再放行
     *             （这一段宽限期兜住"这一模式的料还在 AE 推送路上"，就是队长 2026-09-26 那条原始口径；
     *             料真到了会走「推送锁模式」那条路把机器切回来，不会漏跑）；</li>
     *       </ol>
     *       「主机还没认过这条配方」只做<b>诊断统计</b>、不作为拦截条件 —— 拿它拦截会过头：
     *       玩家把多种模式的样板一起插进来、其中只有一种有订单时，机器会被一张认不出配方的样板摁住。
     *       控制器停在<b>一个样板都没有</b>的模式上时不受这两条限制（原有的自愈场景）；</li>
     *   <li><b>第二遍：类型必须已识别</b>（{@code recipeTypeOfSlot < 0} 一律跳过）—— 这就是
     *       「只在该类型确实有等待中的样板时才能选」；没有任何候选时返回 -1，
     *       调用方据此<b>不许回退抢占控制器</b>。</li>
     * </ol>
     * 「槽里有料等着」的判据与现役机一致：该槽内部槽的 {@code getItemStackInputMap()} /
     * {@code getFluidStackInputMap()} 都不为空（未实体化的槽直读 {@link #internalInventory} 表，不为了查询而实体化）。
     */
    private int findModeWithWaitingStock(List<GTRecipeType> types, int active, long now) {
        int capacity = getCapacity();
        ensureRecipeTypeSlotCapacity();
        int slots = Math.min(capacity, slotRecipeTypes.size());
        // ★★ 0.3.4 第一遍（修订，见方法 javadoc）：判「当前模式还有活吗」，而不是「当前模式还有样板吗」。
        diagActivePatterns = 0;
        diagActiveStock = 0;
        diagActiveCold = 0;
        boolean hasWaitingStock = false;
        for (int slot = 0; slot < slots; slot++) {
            if (patternInventory.getStackInSlot(slot).isEmpty()) {
                continue;
            }
            if (recipeTypeOfSlot(slot) != active) {
                continue;
            }
            diagActivePatterns++;
            if (!hasRecipeCacheInSlot(slot)) {
                // 主机还没把这条配方配上过 ⇒ 大概率"料还在 AE 路上"。**只统计、不拦截**：
                // 拦截会过头 —— 玩家把多种模式的样板一起插进来、其中只有一种有订单时，
                // 机器会被一张"永远认不出配方"的样板摁住，又变成"不会自动切换"。判定交给下面的空转宽限期。
                diagActiveCold++;
            }
            if (hasSlotWaitingStock(slot)) {
                diagActiveStock++;
                hasWaitingStock = true;
                break;   // 有活，后面的槽不用再看了
            }
        }
        if (hasWaitingStock) {
            activeModeBusySince = -1L;   // 有料在等 ⇒ 就是它该跑，绝不切走
            return -1;
        }
        if (diagActivePatterns > 0) {
            // 样板都在、槽里却没料 ⇒ 可能这一轮干完了、也可能"料还在 AE 路上"：
            // 先空转够 AUTO_SWITCH_STOCKLESS_TICKS 再放行（真到了料会走推送锁模式那条路回来）。
            if (activeModeBusySince < 0L) {
                activeModeBusySince = now;
                return -1;
            }
            if (now - activeModeBusySince < AUTO_SWITCH_STOCKLESS_TICKS) {
                return -1;
            }
        } else {
            activeModeBusySince = -1L;   // 当前模式一个样板都没有 ⇒ 自愈场景，随时可以对齐
        }
        for (int slot = 0; slot < slots; slot++) {
            // 空槽先跳过：先做类型识别（含手动指定查表）再判空是纯浪费
            if (patternInventory.getStackInSlot(slot).isEmpty()) {
                continue;
            }
            int type = recipeTypeOfSlot(slot);   // 手动指定的优先，其次才是自动识别
            if (type < 0 || type >= types.size() || type == active) {
                continue;
            }
            Long blocked = autoSwitchBlockedUntil.get(type);
            if (blocked != null && now < blocked) {
                continue;   // 刚切过去没跑起来的类型，暂时不再选
            }
            if (hasSlotWaitingStock(slot)) {
                return type;   // 找到了一个有料等着、且不属于当前模式的候选
            }
        }
        return -1;
    }

    /**
     * 该槽里有没有料等着（模板：{@code getItemStackInputMap()/getFluidStackInputMap()} 有一侧非空）。
     * <b>直读槽表</b>：未实体化的槽 = 没料 ⇒ 返回 false，绝不为了这次查询把槽建出来（惰性化红线）。
     */
    private boolean hasSlotWaitingStock(int slot) {
        try {
            InternalSlot internal = slot >= 0 && slot < internalInventory.size()
                    ? internalInventory.get(slot) : null;
            if (internal == null) {
                return false;
            }
            return !internal.getItemStackInputMap().isEmpty() || !internal.getFluidStackInputMap().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ==================== P2-③ 名字跟随 ====================

    /**
     * ★ 2026-10-15（本轮重做，<b>与现役机同款</b>）：<b>总成的名字 = 本总成"绑定的那个模式"的名字</b>，
     * 按「本总成 + 模式」记在 {@link #modeNames} 里并<b>落盘</b>（{@link #NBT_MODE_NAMES}）。
     *
     * <p><b>规则（用户口径逐条落地）</b>：
     * <ol>
     *   <li><b>名字跟着"模式"走，不跟着控制器当前模式走</b> ✗：命名用的模式 = 本总成自己的模式
     *       （{@link #modeCursorIndex}，绑定持久化在 {@link #NBT_BOUND_MODE_TYPE}）
     *       ⇒ 同一控制器下两台总成各绑一个模式 ⇒ <b>各叫各的</b>，主机在跑什么与名字无关 ✓；</li>
     *   <li><b>该模式已经有名字</b>（我们设过 / 玩家改过）⇒ <b>继续用那个名字</b>（"延续"）；</li>
     *   <li><b>该模式还没有名字</b> ⇒ 用该模式的<b>显示名</b>（{@link #modeNameForNaming}，真翻译）写一次；</li>
     *   <li><b>玩家手动改名 ⇒ 永久让位</b>（{@link #lockedName}，落盘 {@link #NBT_LOCKED_NAME}）：
     *       此后换模式、改绑定、主机切模式、重进游戏、拆装重成型都<b>不再改写</b> ✗
     *       （唯一例外：名字被清空时把玩家那个名字写回来）；</li>
     *   <li>取不到显示名（连原文都取不到）⇒ <b>保持原名不动</b> ✗（绝不写空）。</li>
     * </ol>
     *
     * <p><b>★ 为什么删掉了旧的"白名单"判据</b>：旧版允许"名字逐字等于本控制器某个模式显示名"
     * （{@code isModeDisplayNameOfController}）时改写 —— 玩家把总成命名成某个模式的名字就会被当成
     * "我们自己写的"而<b>被改回自动名</b> ✗；而且它只认<b>当前控制器</b>的模式名 ⇒ 总成换挂到另一台机器后，
     * 旧自动名不在新模式的名单里 ⇒ 从此一个字都不写（「接在电路组装机上的总成没有起名」✗）。
     * 该判据整条删除 ✓。
     *
     * <p><b>纪律</b>：起名纯粹是显示层的事，任何异常都不许影响机器逻辑 ⇒ 整个方法包在 try 里、
     * 失败就静默跳过（连日志都不打，免得刷屏）。
     */
    private void syncCustomNameToMode(int modeIndexHint) {
        // ★ 只在服务端做（与 Enhancedcore 的 `SuperBufferNaming.applyNames` 同口径：它第一句就是
        //   `if (machine.isRemote()) return;`；本类 autoSwitchRecipeModes() 也是首句即 isRemote() 早退）。
        if (isRemote()) {
            return;
        }
        try {
            List<GTRecipeType> types = getAvailableRecipeTypes();
            String current = getCustomName();
            // ① 识别"别人改了名字"（玩家在界面里改、或上游/别的代码写进来）⇒ 记为"玩家改名"
            //   （{@link #lockedName} 永久让位），并归到"当时显示的那个模式"名下。
            //   上游兜底串「未命名加工模式」不算名字；名字被清空不算改名。成本 = 一次 String.equals ✓。
            if (observedName != null && current != null && !current.isEmpty()
                    && !current.equals(observedName) && !isUpstreamPlaceholderName(current)) {
                this.lockedName = current;
                if (appliedModeKey != null) {
                    modeNames.put(appliedModeKey, current);
                }
                markDirty();
            }
            // ② 开关关掉 ⇒ 逐字回到改动前的行为（唯一允许写的情况 = 名字为空），不写名字表、不记让位 ✓。
            if (!enableActiveCustomNaming) {
                if (current == null || current.isEmpty()) {
                    int legacy = currentModeIndexForNaming(modeIndexHint);
                    if (legacy >= 0 && legacy < types.size()) {
                        String legacyName = modeNameForNaming(types.get(legacy));
                        if (legacyName != null) {
                            setCustomName(legacyName);
                        }
                    }
                }
                this.observedName = getCustomName();
                return;
            }
            // ③ 绑定模式还在"待解析"（读档后类型表还没就绪）⇒ 本 tick 什么都不做（免得记到下标 0 名下）✓
            if (boundModePendingResolve) {
                this.observedName = current;
                return;
            }
            boolean bound = this.modeCursorTouched;
            int mode = namingModeIndexOf(bound, modeIndexHint, types);
            IRecipeLogicMachine controller = firstRecipeLogicController();
            boolean movedToAnotherController = controller != null && this.lastNamingController != null
                    && this.lastNamingController != controller;
            this.lastNamingController = controller;
            String key = mode < 0 ? null : modeKeyOf(types.get(mode));
            if (key == null) {
                this.observedName = current;
                return;
            }
            // ④ 玩家改过名 ⇒ 名字永久归玩家：只在"名字为空"时把玩家那个名字写回来 ✓
            if (!this.lockedName.isEmpty()) {
                if (current == null || current.isEmpty()) {
                    setCustomName(this.lockedName);
                    this.observedName = this.lockedName;
                } else {
                    this.observedName = current;
                }
                this.appliedModeKey = key;
                return;
            }
            // ⑤ 目标名字 = 该模式已经记着的名字（"延续"）；没有 ⇒ 用该模式显示名并记下来
            String target = modeNames.get(key);
            if (target == null || target.isEmpty()) {
                if (!bound && current != null && !current.isEmpty() && !movedToAnotherController) {
                    // 未绑定 + 名字已有主 + 没换过控制器 ⇒ 一个字都不写（绝不因为主机换模式就改名 ✓）
                    this.observedName = current;
                    this.appliedModeKey = key;
                    return;
                }
                String display = modeNameForNaming(types.get(mode));
                if (display == null) {
                    this.observedName = current;
                    return;
                }
                target = display;
                modeNames.put(key, target);
                markDirty();
            }
            // ⑥ 幂等写：值已经对了就一个 setter 都不调
            if (!target.equals(current)) {
                setCustomName(target);
            }
            this.observedName = target;
            this.appliedModeKey = key;
        } catch (Throwable ignored) {
            // 起名失败绝不影响机器
        }
    }

    /**
     * ★ 2026-10-15：<b>玩家在界面里确认了改名</b>（{@code AETextInputButtonWidget.setOnConfirm} 的落点）。
     * 当场把"玩家亲手改的"记下来（{@link #lockedName} ⇒ 落盘 ⇒ 永久让位），不依赖"下一 tick 对比名字"
     * 那条兜底判据。LDLib 控件回调两端都会跑：客户端那次只更新本地字段，权威落盘在服务端那次 ✓。
     */
    public void onPlayerConfirmedCustomName(String name) {
        if (name == null) {
            return;
        }
        if (!isRemote()) {
            this.lockedName = name;
            String key = this.appliedModeKey;
            if (key == null) {
                List<GTRecipeType> types = getAvailableRecipeTypes();
                int mode = namingModeIndexOf(this.modeCursorTouched, activeRecipeTypeIndex, types);
                key = mode < 0 ? null : modeKeyOf(types.get(mode));
            }
            if (key != null) {
                modeNames.put(key, name);
            }
            markDirty();
        }
        setCustomName(name);
        this.observedName = name;
    }

    /**
     * ★ 2026-10-15：命名用哪一个模式 —— <b>绑定模式优先，未绑定才跟随控制器的当前模式</b>
     * （顺序与理由见现役机 {@code MoltenPatternAssemblyPartMachine#namingModeIndexOf}，逐条同款）。
     */
    private int namingModeIndexOf(boolean bound, int modeIndexHint, List<GTRecipeType> types) {
        int size = types.size();
        if (size <= 0) {
            return -1;
        }
        if (bound && modeCursorIndex >= 0 && modeCursorIndex < size) {
            return modeCursorIndex;
        }
        IRecipeLogicMachine controller = firstRecipeLogicController();
        if (controller != null) {
            int live = controller.getActiveRecipeType();
            if (live >= 0 && live < size) {
                return live;
            }
        }
        if (modeIndexHint >= 0 && modeIndexHint < size) {
            return modeIndexHint;
        }
        if (activeRecipeTypeIndex >= 0 && activeRecipeTypeIndex < size) {
            return activeRecipeTypeIndex;
        }
        return modeCursorIndex >= 0 && modeCursorIndex < size ? modeCursorIndex : -1;
    }

    /** ★ 2026-10-15：模式类型 ⇒ 名字表的键（模式 id）。取不到 id ⇒ {@code null}（放弃本次命名）。 */
    private static String modeKeyOf(GTRecipeType type) {
        return type == null || type.registryName == null ? null : type.registryName.toString();
    }

    // ★ 2026-10-15：旧的"能不能由我们改名"白名单判据（canAutoWriteCustomName / isModeDisplayNameOfController /
    //   modeDisplayNames + 它们的缓存）**整段删除**（与现役机同款）—— 那一套正是"玩家手动名被改回自动名"
    //   的根因（名字逐字等于某个模式显示名 ⇒ 被当成我们自己写的 ✗），也是"换挂到别的机器后永不起名"的根因
    //   （只认当前控制器的模式名 ✗）。现在由「按模式记名字 + 玩家改名永久让位」决定，不再猜名字归属 ✓。

    /**
     * ★ 2026-10-14：**"本总成自己负责的模式"的权威取值** —— 自动命名只认这一个来源
     * （<b>与现役机同款</b>）。
     *
     * <p><b>为什么权威从控制器换成本总成</b>（用户口径原话：「<b>每个总成绑定一个固定模式</b>」）：
     * 一台主机有两（多）个模式时，玩家放两（多）个样板总成、<b>各自负责一种模式</b>，
     * 名字必须**固定显示自己负责的那个模式**、<b>不随主机当前在跑什么而变</b> ✗。
     * 上一版以主机当前模式为准 ⇒ 同一主机下所有总成必然显示同一个名字（撞车 ✗）。
     *
     * <p>取值顺序（逐条都有依据）：
     * <ol>
     *   <li><b>{@link #modeCursorIndex} = 权威</b> —— 它就是"<b>这台总成自己的模式</b>"：
     *       模式页（{@code RecipeModeConfigurator}）上 ◀/▶ 选中的那一个，也是「指定」按钮真正
     *       写下去的那个（{@link #assignManualType(int, boolean)} 读的就是它，:2597）；
     *       {@link #syncCustomNameToMode(int)} 的另一个调用点给的也是它（:2616）。
     *       ⇒ "用户在总成上选定、并用于决定这台总成匹配/推送哪个模式"的那个就是它 ✓。
     *       <b>已知边界</b>：它带 {@code @DescSynced}、<b>无</b> {@code @Persisted}（:533），
     *       既不在本类 {@code saveCustomPersistedData} 也不在 {@code loadCustomPersistedData} 里
     *       ⇒ <b>读档后回默认值 0</b>（随后由 tick 里的"游标跟随控制器"改写，:2804）。
     *       这一条<b>本轮不动</b>（不新增/不改任何 {@code @Persisted} 字段 ⇒ 存档格式零变化是硬纪律 ✗），
     *       所以"重启后名字要重新跟随玩家在本总成上选的那次操作"这一点<b>未取证/未解决</b>，
     *       详见 _census/命名绑定自有模式_执行记录.md 的「未取证项」；</li>
     *   <li>调用点给的 {@code modeIndexHint} —— 只在 {@link #modeCursorIndex} <b>非法</b>时兜底
     *       （换控制器导致类型表变短、下标越界等）。调用点：
     *       {@link #assignManualType(int, boolean)} 给 {@link #modeCursorIndex}；
     *       tick / 读档路径给本类缓存下标 {@link #activeRecipeTypeIndex}；</li>
     *   <li>{@link #activeRecipeTypeIndex}（同样要求下标合法）—— 再退一档的本机缓存；</li>
     *   <li><b>控制器的 {@code getActiveRecipeType()} 只作最后兜底</b>（★ 2026-10-14 从"权威"降级）：
     *       本机模式完全不可得时宁可显示"主机现在的模式"，也不要空着/不动 ——
     *       这是<b>兜底</b>、不是权威，正常路径永不走到它 ✓；</li>
     *   <li>以上全不成立 ⇒ 回落 <b>{@code 0}</b>（= "机器自己的第 0 个类型"，★ 注释义务：
     *       类型表非空时下标 0 恒合法 ⇒ 一定有名字，最保守的兜底）。</li>
     * </ol>
     *
     * <p><b>注意</b>：{@link #modeCursorIndex} 与主机实际模式<b>解耦</b>（0.3.2 的既定设计：
     * 拨游标不打断正在跑的配方）—— 这正是本轮要的语义：<b>拨本总成的模式页 = 改这台总成负责谁</b>
     * ⇒ 名字跟着变 ✓；主机真的切了模式（主机模式页 / 自动切换 / 推送锁模式）⇒ <b>名字不动</b> ✓。
     *
     * @return 合法的模式下标；类型表为空 ⇒ {@code -1}
     *         （调用方据此**保持当前名字不动** ✗）
     */
    private int currentModeIndexForNaming(int modeIndexHint) {
        List<GTRecipeType> types = getAvailableRecipeTypes();
        int size = types.size();
        if (size <= 0) {
            return -1;
        }
        // ① ★ 2026-10-14 权威：**本总成自己的模式**（模式页选中 / 「指定」写下去的那个）
        if (modeCursorIndex >= 0 && modeCursorIndex < size) {
            return modeCursorIndex;
        }
        // ② 调用点给的兜底下标
        if (modeIndexHint >= 0 && modeIndexHint < size) {
            return modeIndexHint;
        }
        // ③ 本机缓存下标
        if (activeRecipeTypeIndex >= 0 && activeRecipeTypeIndex < size) {
            return activeRecipeTypeIndex;
        }
        // ④ ★ 2026-10-14 降级为**最后兜底**：控制器（主机）当前的模式（正常路径永不走到这里）
        IRecipeLogicMachine controller = firstRecipeLogicController();
        if (controller != null) {
            int live = controller.getActiveRecipeType();
            if (live >= 0 && live < size) {
                return live;
            }
        }
        // ⑤ 最后：机器自己的第 0 个类型（类型表非空 ⇒ 下标 0 恒合法）
        return 0;
    }

    /**
     * ★ 2026-10-15：一个配方类型 ⇒ <b>可以写进自定义名的显示名</b>（<b>与现役机同款</b>）。
     *
     * <p><b>取名的权威口径 = 上游自己用的那一个</b>：{@code Component.translatable(registryName.toLanguageKey())}
     * —— GTCEu 的 JEI 分类标题就是这一句（{@code GTRecipeTypeCategory.getTitle} 字节码，
     * 转述见 {@code NanochipRecipeTypes.java:103-104}）⇒ 我们与 JEI/模式页显示的名字<b>永远同一个来源</b> ✓。
     * 键形态逐字实证：{@code gtceu:circuit_assembler ⇒ "gtceu.circuit_assembler" ⇒ 语言文件里
     * 「电路组装机」（_census/_probe_chain_lang/gtceu-1.20.1-1.4.4/assets/gtceu/lang/zh_cn.json:2626）}。
     *
     * <p>判定顺序：① {@link #nameOverride}（本模组自己的少量覆盖表，与现役机同表）；
     * ② 真翻译（{@code Language} 自带 en_us 兜底 ⇒ 中文客户端得中文、英文客户端得英文 ✓）；
     * ③ 连原文都取不到（键整个不存在）⇒ 退回<b>原文路径名</b>（如 {@code circuit_assembler}）——
     * 用户口径「取不到中文就退回英文/原文，不允许因为取不到就整条不生效」✓；绝不写空、
     * 也绝不把带命名空间的 lang 键写进玩家看到的名字 ✗。
     */
    private static String modeNameForNaming(GTRecipeType type) {
        if (type == null || type.registryName == null) {
            return null;
        }
        String overridden = nameOverride(type, null);
        if (overridden != null && !overridden.isEmpty()) {
            return overridden;
        }
        try {
            String key = type.registryName.toLanguageKey();
            String translated = Component.translatable(key).getString();
            if (translated == null || translated.isEmpty() || translated.equals(key)) {
                String path = type.registryName.getPath();
                return path == null || path.isEmpty() ? null : path;
            }
            return translated;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * ★ 2026-10-15：自动命名的**名字覆盖表**（<b>与现役机同表、同一口径</b>；队长要求）。
     *
     * <p>只列需要特殊处理的模式，其余一律走 {@link #modeNameForNaming} 的真翻译。
     * <b>刻意不做"统一去掉末尾的『机』"</b> —— 那会把「线材轧机」变成「线材轧」✗。
     * 现有条目：{@code gtceu:circuit_assembly} / {@code gtceu:circuit_assembler} ⇒ 「<b>电路组装</b>」
     * （用户口径：接电路组装机的总成应显示「电路组装」，不叫「电路组装机」）。
     * 判据是<b>配方类型的注册路径</b>（不是机器 id / 类名）⇒ 任何挂在该模式上的总成都一致 ✓。
     */
    private static String nameOverride(GTRecipeType type, String fallback) {
        if (type != null && type.registryName != null) {
            String path = type.registryName.getPath();
            if ("circuit_assembly".equals(path) || "circuit_assembler".equals(path)) {
                return "电路组装";
            }
        }
        return fallback;
    }

    /**
     * ★ 2026-10-04：这个字符串是不是上游 Enhancedcore 写的兜底串「未命名加工模式」。
     *
     * <p><b>为什么在我们自己的代码里也用 Unicode 转义字面量</b>：与上游
     * {@code SuperBufferChineseNames:13} 的写法**逐字同款** —— ① 一眼能看出"这就是上游那个常量"；
     * ② 不受本仓库文件编码/工具链影响；③ 用中文字面 grep 本仓库时不会误当成我们的文案。
     * 判据是全等（{@code String.equals}），不含任何模糊匹配 ✓。
     */
    private static boolean isUpstreamPlaceholderName(String name) {
        return name != null && AUTO_NAME_FALLBACK_UPSTREAM.equals(name);
    }

    /** 这个名字是不是已经被「<b>同一控制器下别的样板总成</b>」用了（队长口径里的「已经有了」）。★ 照搬现役机同名方法。 */
    private boolean isNameTakenByOther(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        IRecipeLogicMachine controller = firstRecipeLogicController();
        if (!(controller instanceof com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine mb)) {
            return false;
        }
        try {
            for (com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart part : mb.getParts()) {
                if (part == this) {
                    continue;
                }
                if (part instanceof org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase other
                        && name.equals(other.getCustomName())) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            // 枚举不到就当没占用（宁可重名，也不要因为枚举失败而不起名）
        }
        return false;
    }

    /**
     * 配方表 → <b>中文名</b>（与模式页同一套口径：{@code registryName.toLanguageKey()} 查语言文件；
     * 查不到就退回路径名）。★ 照搬现役机 {@code tableNameOf}。
     */
    private static String tableNameOf(GTRecipeType type) {
        if (type == null || type.registryName == null) {
            return "?";
        }
        try {
            String key = type.registryName.toLanguageKey();
            String translated = Component.translatable(key).getString();
            return (!translated.isEmpty() && !translated.equals(key)) ? translated : type.registryName.getPath();
        } catch (RuntimeException ignored) {
            return type.registryName.getPath();
        }
    }

    // ==================== P1-②D 样板身份闸（扣料入口的<b>额外</b>一道）====================
    //
    // 逐条照搬现役机 MoltenPatternAssemblyPartMachine 的
    //   slotAllowsRecipe(1412-1460) / logGateReject(1334-1360) / noteIfHostTableMisidentified(1376-1410)
    //   以及它们的两个记账字段（1658 / 1666 / 1669）。
    // 惰性化差异（唯一一处）：槽位/容量一律按惰性机口径 —— 现役机是固定 128/640 的 int[] 与
    //   getPatternInventory()，惰性机的容量动态增长（getCapacity()、slotRecipeTypes 只增不减），
    //   所以边界判定改用 getCapacity() 夹，其余逻辑逐条一致。
    //
    // ★ 闸门纪律（与现役机同）：这是**额外**一道闸，绝不动"认不出来一律放行"的既有安全线；
    //   开关关掉 / 下标 -1 / 类型表为空 / 样板为空 / 解不出来 / 抛任何异常 ⇒ 一律放行。

    /**
     * 「身份闸拒绝」诊断日志的最后记录 tick（2 秒最多一条）。
     * ★ 照搬现役机 {@code lastGateRejectLogTick}；哨兵写 {@code -1000} 而**不是** {@code Long.MIN_VALUE}
     * （{@code gameTime - Long.MIN_VALUE} 会溢出成负 ⇒ 限流恒真 ⇒ 日志永远静默，本仓库自检第 45 项专治这个坑）。
     */
    private long lastGateRejectLogTick = -1000;

    /**
     * ★ 2026-09-21 <b>只读判读台账</b>（照搬现役机的 {@code hostTableMisidentifyNotes}）：
     * 已经记过的「槽位 → 该槽被误判成主机表时正在跑的那个配方类型」组合。
     *
     * <p>用途见 {@link #noteIfHostTableMisidentified(int, GTRecipe)}。它只在"已经决定拒绝之后"写入，
     * 只写日志、不改任何判定；每个组合只记一次，且台账<b>有界</b>（{@link #MISIDENTIFY_NOTE_LIMIT}）。
     *
     * <p>★ 口径说明：现役机字段上的注释还写着"只在 {@code patternAssembly.debugPatternLogs=true} 时才会被写入"，
     * 而它的实现体早已改成<b>默认就记</b>（那一改正是队长卡住时"日志里什么都看不到"的教训）。
     * 惰性机照搬的、也是这里写的，是<b>实现体口径</b>（默认就记）。
     */
    private final Set<String> hostTableMisidentifyNotes = new HashSet<>();

    /** 上面那本台账的上限（惰性机标称上限 640 槽，不能让台账无限长）。★ 照搬现役机 {@code MISIDENTIFY_NOTE_LIMIT}。 */
    private static final int MISIDENTIFY_NOTE_LIMIT = 64;

    /**
     * 这个槽的样板能不能执行这条配方。<b>两道闸</b>：
     * <ol>
     *   <li><b>类型闸</b>：该槽识别出的配方类型必须与候选配方的类型一致
     *       （只在识别出来时生效；识别不出来就跳过这层）——
     *       挡住"两种类型的输入输出长得一样"那种（例如 0.144B 液态铁+1 铁粉，分子解构/提取机都能匹配）；</li>
     *   <li><b>身份闸</b>：见 {@link #patternIdentityMatches}（惰性机 0.3.x 已照搬，直接复用）。</li>
     * </ol>
     *
     * <p><b>一律 fail-open</b>：开关关掉、样板为空、解不出来、出任何异常 → 放行（退回 gtlcore 原生行为）。
     * 宁可漏挡一次，也绝不让机器因为闸门而卡死。
     *
     * <p>★ 逐条照搬现役机 {@code slotAllowsRecipe}（1412-1460）。<b>惰性化差异</b>：
     * 现役机的槽位是固定 {@code int[]}、容量来自 {@code getPatternInventory()}（128/640 写死），
     * 惰性机的容量是<b>动态</b>的（{@link #getCapacity()}）⇒ 越界（= 这个槽还没被建出来）
     * 直接放行，不抛、不猜。
     */
    boolean slotAllowsRecipe(int slot, GTRecipe recipe) {
        if (recipe == null || slot < 0) {
            return true;
        }
        if (!GTLModularConfig.isPatternIdentityGateEnabled()) {
            return true;
        }
        try {
            // ---- ① 类型闸 ----
            int detected = recipeTypeOfSlot(slot);
            if (detected >= 0) {
                List<GTRecipeType> types = getAvailableRecipeTypes();
                if (detected < types.size()) {
                    GTRecipeType expected = types.get(detected);
                    // GTRecipeType 是注册期单例，直接比引用即可
                    if (expected != null && recipe.recipeType != expected && !PatternTypeGate.allows(expected, recipe.recipeType)) {
                        return false;
                    }
                }
            } else {
                // 类型识别不出来（样板涉及多种类型 / 识别有歧义）→ 不做身份判定，直接放行。
                // 实测踩过：大型化学反应釜的石脑油配方在"识别类型下标=-1"时仍被身份闸拒掉，
                // 机器握着料却不消费 → 表现为"材料明明够却卡住"。
                // 按 fail-open 原则：我们没有可靠信息时不该拦。
                return true;
            }

            // ---- ② 身份闸 ----
            // ★ 惰性化差异：容量动态增长，槽位先按本机口径夹一次（现役机这里是
            //   `slot >= inventory.getSlots()`，语义相同，只是容量来源不同）。
            if (slot >= getCapacity()) {
                return true;
            }
            ItemStack stack = patternInventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                return true;
            }
            Level level = getLevel();
            if (level == null) {
                return true;
            }
            // 解出来的就是 gtlcore 存在 slot2PatternMap 里的那份样板身份（已剥电路、按 keepByProduct 裁副产）
            IPatternDetails pattern = getRealPatternHelper().processPatternWithCircuit(
                    stack, count -> {
                    }, level, keepByProduct);
            if (pattern == null) {
                return true;
            }
            return patternIdentityMatches(recipe, pattern);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * 身份闸拒绝时的诊断日志（2 秒最多一条）。★ 逐条照搬现役机 {@code logGateReject}（1334-1360）。
     *
     * <p>拒绝是"静默 return false"，机器会表现为<b>握着料却不动</b>（配方取不到输入）。
     * 所以一旦闸门挡了正确的配方，必须能看出来：这里把槽位/识别类型/配方类型都打出来。
     *
     * <p>★ 用<b>中文表名</b>而不是下标：一眼就能看出是"标识判错了"还是"闸门在干正事"。
     *
     * <p>文案是<b>硬编码中文</b>（与现役机逐字一致），因此<b>不需要任何语言键</b>
     * ⇒ 本次纵切一个字都没动 {@code zh_cn.json} / {@code en_us.json}。
     *
     * <p>★ 与现役机的唯一差别（有意为之）：整个方法体包在 {@code try/catch (Throwable)} 里 ——
     * 它跑在扣料热路径上，本仓库红线是"热路径一律不外抛"；现役机靠下游 {@code tableNameOf}
     * 自己的 try/catch 兜，这里再加一层，行为不变（它只写日志、不 return 判定结果）。
     */
    void logGateReject(int slot, GTRecipe recipe, String side) {
        try {
            long now = getLevel() == null ? 0 : getLevel().getGameTime();
            if (now - lastGateRejectLogTick < 40) {
                return;
            }
            lastGateRejectLogTick = now;
            // ★ 2026-09-21（队长「那个配方表到底叫什么名字」）：日志一律给**中文表名**，不再只给下标 + id。
            //   ⇒ 一眼就能看出是"标识判错了"还是"闸门在干正事"，不用再对着下标猜。
            List<GTRecipeType> types = getAvailableRecipeTypes();
            int detectedIndex = recipeTypeOfSlot(slot);
            String detectedName = (detectedIndex >= 0 && detectedIndex < types.size())
                    ? tableNameOf(types.get(detectedIndex))
                    : "识别不出来";
            GTLModular.LOGGER.info("[gtlmodular] 身份闸拒绝配方（{}侧）槽位[{}]: 该槽样板标识=『{}』(下标{})，"
                            + "而这条配方属于『{}』({}) —— 若这台机器本该跑它，就是标识判错了："
                            + "Shift+右键该样板槽可手动指定正确标识（模式页也能填槽号指定）",
                    side, slot + 1, detectedName, detectedIndex,
                    tableNameOf(recipe == null ? null : recipe.recipeType),
                    recipe == null ? "null" : String.valueOf(recipe.recipeType));
            noteIfHostTableMisidentified(slot, recipe);
        } catch (Throwable ignored) {
            // 诊断日志绝不允许影响扣料本身
        }
    }

    /**
     * ★ 2026-09-21 <b>只读判读</b>：识别到"主机表"但配方类型是别的表时，记一条（默认就记，每个组合只记一次）。
     * ★ 逐条照搬现役机 {@code noteIfHostTableMisidentified}（1376-1410）。
     *
     * <p>为什么需要它：主机表在 {@code byType} 里有了电路组装机配方之后，
     * {@code detectRecipeType} 的过近似判据**有能力**把非主机表的样板判成下标 0；而实机日志里
     * 「识别类型下标=0 + 配方类型=另一个模块的类型」这个组合<b>确实出现过</b>。
     * 光看现有日志分不清那是"该槽本就不属于这条配方"（闸门干正事）还是"样板被误判成主机表"（潜在缺陷）。
     *
     * <p><b>本方法的纪律（不许改坏）</b>：
     * <ul>
     *   <li><b>零行为影响</b>：不 return、不改 {@code slotAllowsRecipe} 的结论、不动闸门开关、
     *       不动 {@code slotRecipeTypes} 缓存 —— 它只在"已经决定拒绝之后"被调用，只写日志；</li>
     *   <li><b>默认就开</b>（照搬现役机 2026-09-21 的改动）：判读本身只读、且每个
     *       「槽位 → 配方类型」组合只打一次（{@link #hostTableMisidentifyNotes} 台账），
     *       调用点还被 {@code logGateReject} 的 40 tick 节流挡着 ⇒ 代价可忽略；</li>
     *   <li><b>台账有界</b>：上限 {@value #MISIDENTIFY_NOTE_LIMIT} 条，满了就不再新增
     *       （惰性机标称容量上限 640 槽，不能让它无限长）。</li>
     * </ul>
     */
    private void noteIfHostTableMisidentified(int slot, GTRecipe recipe) {
        if (recipe == null) {
            return;
        }
        try {
            List<GTRecipeType> types = getAvailableRecipeTypes();
            int detected = recipeTypeOfSlot(slot);
            if (detected < 0 || detected >= types.size()) {
                return;
            }
            GTRecipeType detectedType = types.get(detected);
            if (detectedType == null || detectedType == recipe.recipeType) {
                return;   // 类型一致（不该走到这里，闸门已放行）⇒ 没什么好记的
            }
            // 只在「识别结论是主机表」且「配方属于别的表」时记 —— 这正是需要事后判读的那个组合
            if (detectedType != com.gtlmodular.common.data.nanochip.NanochipRecipeTypes.ASSEMBLY_COMPLEX) {
                return;
            }
            if (hostTableMisidentifyNotes.size() >= MISIDENTIFY_NOTE_LIMIT) {
                return;
            }
            String key = slot + "->" + recipe.recipeType;
            if (!hostTableMisidentifyNotes.add(key)) {
                return;   // 这个组合已经记过了
            }
            GTLModular.LOGGER.warn("[gtlmodular] 【只读判读】槽位[{}] 的样板标识被认成 **『{}』**，"
                            + "但正在跑的配方属于『{}』 ⇒ 二选一：① 该槽样板本就属于别的机器"
                            + "（闸门在干正事，可忽略本行）；② 标识判错了（潜在缺陷，请把本行与"
                            + "紧跟其后的『身份闸拒绝配方』一起发回）；也可 Shift+右键该样板槽手动指定正确标识。"
                            + "本行只读、不改任何判定 @{}",
                    slot + 1, tableNameOf(detectedType), tableNameOf(recipe.recipeType), getPos());
        } catch (Throwable ignored) {
            // 诊断代码绝不允许影响游戏
        }
    }

    // ==================== 样板身份判据（照搬现役机的静态小工具） ====================

    /**
     * 配方身份匹配：非电路输入被样板输入覆盖 + 配方主产物落在样板输出里。
     * ★ 逐条照搬现役机 {@code patternIdentityMatches}（含它的 6 个静态小工具）。
     *
     * <p>为什么不用「产物集合完全相等」：gtlcore 的样板身份在 {@code keepByProduct=false} 时
     * <b>会裁掉副产物</b>，拿它去和「配方全部产物集合」比相等会永远不相等 → 识别全失败。
     * 用身份匹配口径和扣料时的身份闸完全一致，且<b>多类型命中就判为歧义、返回 -1 不猜</b>。
     */
    static boolean patternIdentityMatches(GTRecipe recipe, IPatternDetails pattern) {
        if (recipe == null || pattern == null) {
            return true;
        }
        Set<Item> patternOutItems = new HashSet<>();
        Set<Fluid> patternOutFluids = new HashSet<>();
        collectFingerprint(pattern.getOutputs(), patternOutItems, patternOutFluids);

        Set<Item> patternInItems = new HashSet<>();
        Set<Fluid> patternInFluids = new HashSet<>();
        IPatternDetails.IInput[] inputs = pattern.getInputs();
        if (inputs != null) {
            for (IPatternDetails.IInput input : inputs) {
                if (input != null) {
                    collectFingerprint(input.getPossibleInputs(), patternInItems, patternInFluids);
                }
            }
        }

        // 输入：配方（除电路外）每一路都要被样板输入覆盖
        if (!recipeInputItemsCovered(recipe, patternInItems) || !recipeInputFluidsCovered(recipe, patternInFluids)) {
            return false;
        }

        // 输出：配方主产物（物品或流体，任一命中即可）要落在样板输出里
        boolean itemMatches = !patternOutItems.isEmpty() && primaryItemOutputCovered(recipe, patternOutItems);
        boolean fluidMatches = !patternOutFluids.isEmpty() && primaryFluidOutputCovered(recipe, patternOutFluids);
        return itemMatches || fluidMatches;
    }

    private static void collectFingerprint(GenericStack[] stacks, Set<Item> items, Set<Fluid> fluids) {
        if (stacks == null) {
            return;
        }
        for (GenericStack stack : stacks) {
            if (stack == null) {
                continue;
            }
            AEKey key = stack.what();
            if (key instanceof AEItemKey itemKey) {
                items.add(itemKey.getItem());
            } else if (key instanceof AEFluidKey fluidKey) {
                fluids.add(fluidKey.getFluid());
            }
        }
    }

    private static boolean recipeInputItemsCovered(GTRecipe recipe, Set<Item> patternItems) {
        List<Content> contents = recipe.getInputContents(ItemRecipeCapability.CAP);
        if (contents == null || contents.isEmpty()) {
            return true;
        }
        for (Content content : contents) {
            if (content == null || content.chance <= 0) {
                continue;   // 非硬性输入不构成约束
            }
            if (!(content.getContent() instanceof Ingredient ingredient)) {
                continue;
            }
            if (isCircuitIngredient(ingredient)) {
                continue;   // 编程电路：样板身份里已被剥离
            }
            if (!ingredientCoveredByItems(ingredient, patternItems)) {
                return false;
            }
        }
        return true;
    }

    private static boolean recipeInputFluidsCovered(GTRecipe recipe, Set<Fluid> patternFluids) {
        List<Content> contents = recipe.getInputContents(FluidRecipeCapability.CAP);
        if (contents == null || contents.isEmpty()) {
            return true;
        }
        for (Content content : contents) {
            if (content == null || content.chance <= 0) {
                continue;
            }
            if (!(content.getContent() instanceof FluidIngredient ingredient)) {
                continue;
            }
            if (!fluidIngredientCoveredByFluids(ingredient, patternFluids)) {
                return false;
            }
        }
        return true;
    }

    private static boolean primaryItemOutputCovered(GTRecipe recipe, Set<Item> patternItems) {
        List<Content> contents = recipe.getOutputContents(ItemRecipeCapability.CAP);
        if (contents == null) {
            return false;
        }
        for (Content content : contents) {
            if (content != null && content.getContent() instanceof Ingredient ingredient) {
                return ingredientCoveredByItems(ingredient, patternItems);
            }
        }
        return false;
    }

    private static boolean primaryFluidOutputCovered(GTRecipe recipe, Set<Fluid> patternFluids) {
        List<Content> contents = recipe.getOutputContents(FluidRecipeCapability.CAP);
        if (contents == null) {
            return false;
        }
        for (Content content : contents) {
            if (content != null && content.getContent() instanceof FluidIngredient ingredient) {
                return fluidIngredientCoveredByFluids(ingredient, patternFluids);
            }
        }
        return false;
    }

    private static boolean ingredientCoveredByItems(Ingredient ingredient, Set<Item> patternItems) {
        ItemStack[] stacks = ingredient.getItems();
        if (stacks == null || stacks.length == 0) {
            return true;   // 空 Ingredient 不构成约束
        }
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty() && patternItems.contains(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    private static boolean fluidIngredientCoveredByFluids(FluidIngredient ingredient, Set<Fluid> patternFluids) {
        FluidStack[] stacks = ingredient.getStacks();
        if (stacks == null || stacks.length == 0) {
            return true;
        }
        for (FluidStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            Fluid fluid = stack.getFluid();
            if (fluid != null && fluid != Fluids.EMPTY && patternFluids.contains(fluid)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCircuitIngredient(Ingredient ingredient) {
        ItemStack[] stacks = ingredient.getItems();
        if (stacks == null) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty() && IntCircuitBehaviour.isIntegratedCircuit(stack)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 生命周期 / 内容变化 ====================

    @Override
    public void onLoad() {
        super.onLoad();
        // 舱室在多方块里要能任意面接 ME 线
        getMainNode().setExposedOnSides(EnumSet.allOf(Direction.class));
        // P0 用「廉价签名轮询」发现样板变化（AE 终端 / 管道 / setItemDirect 都不会触发 ItemStackTransfer 回调）
        // ★ 0.2.9（审计建议）：先退订旧的再订阅，避免 onLoad 被调两次时 lazyTick 双跑。
        if (this.usageSubscription != null) {
            this.usageSubscription.unsubscribe();
        }
        this.usageSubscription = subscribeServerTick(this::lazyTick);
        // ★★ 0.3.5：把基类那条「needPatternSync → requestUpdate」的 tick 也确保挂上 ★★
        //   取证：`updateSubscription()` 全类**只有一个调用点** —— `onMainNodeStateChanged`（节点状态**变化**），
        //   条件是 `getMainNode().isOnline()`。若节点在监听器挂上之前就已在线（读档/区块重载/主机重成形），
        //   `updateSubs` 永远是 null ⇒ 标记置了没人消费 ⇒ AE 一直拿着旧样板表（现役机也有同样现象）。
        //   幂等：`updateSubscription()` 内部自己判在线 + 判空。我们的 lazyTick 里还有一次兜底消费。
        updateSubscription();
    }

    /**
     * 客户端「刚收到同步字段」的钩子（{@code IManaged} 默认实现是空方法，这里不调 super 也不会挡掉逻辑
     * —— 我们的继承链上没有任何类覆写它）。
     *
     * <p>取证：本版 LDLib（1.0.33.b）里 {@code @RequireRerender} 的**消费者确实存在**
     * （{@code FieldManagedStorage.initEnhancedFeature()}：对带该注解的 sync 字段调
     * {@code addSyncUpdateListener(… scheduleRenderUpdate)}），**但该符号在 ldlib / gtceu / gtlcore
     * 三个 jar 里除声明外零调用者** ⇒ 实际效果 = "同步到位后没人自动重烘焙"（这条结论已由第二次审计复核）。
     * 而「谁是我的控制器」正是带该注解的 {@code MultiblockPartMachine.controllerPositions}
     * ⇒ 同步到位后<b>没人自动重烘焙</b>，模型会留在旧材质上（「重进世界后不跟机器连接」的观感）。
     * 这里在「载入后的初始同步」与 {@code controllerPositions} 变化的时刻各补一次重绘，并有界打诊断日志，
     * 用来区分「名单没到客户端」与「到了但没重烤」。
     */
    @Override
    public void onSyncChanged(IRef ref, boolean isClient) {
        if (!isClient) {
            return;
        }
        String field = ref.getKey().getName();
        boolean controllerField = "controllerPositions".equals(field);
        boolean early = this.clientRebakeLeft > 0;
        if (!early && !controllerField) {
            return;
        }
        if (early) {
            this.clientRebakeLeft--;
        }
        if (this.clientSyncDiagLeft > 0) {
            this.clientSyncDiagLeft--;
            GTLModular.LOGGER.info("[gtlmodular] 客户端同步: {} pos={} 字段={} 控制器数={}",
                    getDefinition().getId(), getPos(), field, getControllers().size());
        }
        scheduleRenderUpdate();
    }

    /**
     * ★ 0.3.14：<b>客户端</b>每 tick 的容量对账（一行整数比较；变了才 resize 本侧仓）。
     *
     * <p>为什么必须在这里兜一次（而不是只靠 {@link #patternWidgetCapacity()} 里那次）：
     * 玩家<b>一直开着界面</b>时服务端扩容（或玩家在别处粘贴），LDLib 的原版菜单槽位表
     * 不会因为"字段变了"当场重建；客户端至少要有一处稳定的地方把本侧容量补齐，
     * 才能保证"关掉再打开"这一下不会又因为本侧容量没跟上而少几格
     * （主修复目标是"粘贴后<b>第一次</b>打开就完整"，见 {@link #syncedPatternCapacity} 的说明）。
     *
     * <p>成本：一次字段读 + 一次整数比较；只在真的变了时才 resize（不重建任何字符串）。
     * 任何异常都在 {@link #applySyncedPatternCapacity()} 内部吞掉，绝不影响 tick。
     */
    @Override
    public void clientTick() {
        super.clientTick();
        try {
            applySyncedPatternCapacity();
        } catch (Throwable ignored) {
            // 对账失败绝不影响客户端 tick（顶多是这一帧少几格）
        }
    }

    private void lazyTick() {
        if (isRemote()) {
            return;
        }
        // 成型材质补丁：重进世界后让客户端补一次烘焙（见 FormedRenderSyncNudge 的取证注释）
        if (formedNudgeTicks > 0) {
            formedNudgeTicks = FormedRenderSyncNudge.tick(this, formedNudgeTicks, "惰性猪咪");
        }
        // ★ P2：模式跟随 + 名字跟随 + 自动切换配方模式。
        //   并进**这一个**已有的服务端 tick 订阅里做（不新增第二个 subscribeServerTick），
        //   与现役机把 autoSwitchTick() 订阅在 onLoad 里是同一件事的两种写法；
        //   方法内部自带 try/catch(Throwable)，tick 路径绝不外抛。
        autoSwitchRecipeModes();
        long signature = usageSignature();
        if (!usageSignatureValid || signature != lastUsageSignature) {
            usageSignatureValid = true;
            lastUsageSignature = signature;
            onPatternInventoryChanged();
        }
        // ★ 0.3.5 兜底：基类本该在 onMainNodeStateChanged → updateSubscription() → update() 里消费
        //   needPatternSync 并 requestUpdate（javap 实证它确实有这段），但那条订阅链是否挂上不由我们控制
        //   （惰性机的 onLoad 与基类并不完全同构），所以这里自己再兜一次：幂等，重复调用无害。
        if (needPatternSync) {
            needPatternSync = false;
            refreshPatternsNow();
        }
        // ★【① 冷槽重识别】并进**这一个**已有的服务端 tick 订阅（不新增第二个 subscribeServerTick）。
        //   它同时解决外部建议里的「重置缓存」与「读取缓存」两半，依据见该方法的完整 javadoc。
        selfHealColdSlotCaches();
        // ★ 0.3.5 一次性取证（只打一条）：基类那条「needPatternSync → requestUpdate」的 tick 到底挂上没有？
        //   取证依据：基类 `updateSubscription()` 只在 `onMainNodeStateChanged`（节点状态**变化**）里被调用，
        //   而 `updateSubs` 是基类 protected 字段，我们这边读得到。若这里打出「未订阅」，就等于证明了
        //   「置位了标记却没人消费」——队长报的"现役机也有这个问题"就是同一条基类链（现役机/超级机/
        //   gtlcore 自家样板仓共用它）。
        if (!loggedBaseSyncState) {
            loggedBaseSyncState = true;
            GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪基类样板同步 tick 取证: updateSubs={} needPatternSync={}",
                    this.updateSubs == null ? "未订阅(基类那条链没挂上)" : "已订阅", needPatternSync);
        }
    }

    /**
     * ★ <b>【① 冷槽重识别】"重置缓存（重识别）+ 读取缓存（直供喂热）"的自愈闭环</b>。
     *
     * <h3>它修的到底是什么（把"已缓存配方=0"这条链补上最后一环）</h3>
     * 主机侧候选搜索读的是 gtlcore 的「(配方 → 槽)」记账
     * （{@code getCachedGTRecipe()} 的追加路 + {@code MEPatternRecipeHandlePart} 的槽快路径），
     * 而那份记账的本体就是本部件机的 {@code LazyMEPatternTrait.slot2RecipesCache}
     * （读侧 {@code LazyMEPatternTrait.getCachedGTRecipe():2523-2563}；诊断读数
     * {@code NanochipThreadedRecipeLogic.mePatternCachedRecipeCount():1373-1390} 逐槽累加它）。
     *
     * <p>而写侧<b>只有两条路</b>，都能被"卡住"：
     * <ol>
     *   <li>{@link #supplyResolvedRecipeToMachine(int, int)}（直供）——由
     *       {@code detectRecipeType → scanTypesForMatch} 的<b>唯一命中</b>驱动；</li>
     *   <li>gtlcore 主机自己的 {@code meHandleRecipe} 回退分支。</li>
     * </ol>
     * 而 {@code detectRecipeType} 的运行期入口最终只有 {@link #recipeTypeOfSlot(int)}；
     * 后者一旦把该槽写成 {@link #TYPE_UNKNOWN} 或下标，就<b>再也不扫配方表</b>
     * （{@code :2894-2898} 的既有注释："试过了，识别不出来，别再扫一遍配方表（这是扣料热路径）"）
     * ⇒ <b>该槽的"不识别"结论被永久固化，直供这一半就再也不会被触发</b>
     * ⇒ 表现为诊断行里的 {@code 已缓存配方=0}（= 冷槽），而机器仍在每 tick 只读地搜候选 ⇒ 自锁。
     * 更早一步：槽被写入时 {@link #onSlotPatternChanged(int)} 已经把该槽的类型缓存重置为
     * {@link #TYPE_NOT_TRIED} 并把 {@code slot2RecipesCache} 清掉——<b>重置是有的，"重识别"没有</b>。
     *
     * <h3>所以这一条就是外部建议的落点（"重置缓存或者读取缓存"）</h3>
     * <ul>
     *   <li><b>重置</b> = 把该冷槽的识别缓存写回 {@link #TYPE_NOT_TRIED}，解掉 {@link #TYPE_UNKNOWN}
     *       的永久固化；</li>
     *   <li><b>读取</b> = 重跑一次既有的 {@link #recipeTypeOfSlot(int)} ⇒ 它内部照旧走
     *       {@code detectRecipeType}，唯一命中时<b>顺带</b>唤起 {@code supplyResolvedRecipeToMachine}
     *       ⇒ 经 gtlcore 官方口 {@code IMEPatternTrait.setSlotCacheRecipe} 把这条配方喂进
     *       {@code slot2RecipesCache}，紧接着 {@code getCachedGTRecipe()} 的读侧立刻就能读到。</li>
     * </ul>
     *
     * <h3>判据（两条都是既有的只读方法，不新增任何表）</h3>
     * 同时满足才动手：① 该槽<b>真的冷</b>（{@link #hasRecipeCacheInSlot(int)} 为假，
     * 与主机侧判据同源）；② 该槽<b>有料在等</b>（{@link #hasSlotWaitingStock(int)}）——
     * 没料说明主机这一轮还没派活，扫了也是白扫。
     *
     * <h3>成本闸（三重；这是"扫配方表"这类动作的硬要求）</h3>
     * ① 时间：两次之间至少 {@value #COLD_SLOT_HEAL_INTERVAL_TICKS} tick；
     * ② 总量：整机一生最多重识别 {@value #COLD_SLOT_HEAL_BUDGET} 次（{@link #coldSlotHealTried} 记录已试槽，
     *    同一个槽最多试一次，除非样板真的换了——那时 {@link #onSlotPatternChanged(int)} 已清过缓存，
     *    这里按"槽里的样板与上次试过的不同"再放行一次）；
     * ③ 只在<b>服务端</b>、只在<b>拿得到控制器与类型表</b>时。
     *
     * <h3>为什么绝不双扣料（与既有"绝不双扣料"纪律逐条对齐）</h3>
     * 本方法只做两件事：写一份<b>纯表</b>（{@code slotRecipeTypes[slot]}，本机自己的识别缓存）
     * 与调用 {@link #recipeTypeOfSlot(int)} ⇒ {@code detectRecipeType}（纯只读扫描）⇒
     * {@code supplyResolvedRecipeToMachine}（<b>纯表写</b> {@code setSlotCacheRecipe}）。
     * 整条链里<b>没有</b> {@code RecipeRunner*} / {@code handleRecipeInput} / {@code initMEHandleContents}
     * / {@code setupRecipe} / {@code onRecipeFinish} ⇒ 不可能多扣一份料，也不可能推进任何配方。
     *
     * <h3>回退（独立、一行、逐字）</h3>
     * ① <b>首选</b>：把 {@link #COLD_SLOT_CACHE_HEAL} 改成 {@code false}（⇒ 本方法首句即返回）；
     * ② 更彻底：删掉 {@code lazyTick()} 里那一句 {@code selfHealColdSlotCaches();}
     * —— 本方法与三个字段全是加法，删掉调用点后一行都不会被执行。
     */
    private void selfHealColdSlotCaches() {
        if (!COLD_SLOT_CACHE_HEAL) {
            return;
        }
        try {
            if (isRemote()) {
                return;
            }
            if (coldSlotHealTried.size() >= COLD_SLOT_HEAL_BUDGET) {
                // 总量闸：已经试满 ⇒ 直接停手（不再做任何槽遍历）。
                // 样板真的换过时 onSlotPatternChanged(slot) 会把该槽从 coldSlotHealTried 里摘掉，
                // 所以这里"试满即停"不会把玩家后续新插的样板一起锁死。
                return;
            }
            long now = getOffsetTimer();
            if (now < coldSlotHealNextTick) {
                return;   // 时间闸（首次 coldSlotHealNextTick=0 ⇒ 立刻即可试一次）
            }
            if (firstRecipeLogicController() == null) {
                return;   // 没有控制器 ⇒ 连"有哪几种模式"都不知道，扫不了
            }
            List<GTRecipeType> types = getAvailableRecipeTypes();
            if (types.isEmpty()) {
                return;
            }
            int capacity = getCapacity();
            for (int slot = 0; slot < capacity; slot++) {
                if (patternInventory.getStackInSlot(slot).isEmpty()) {
                    continue;   // 空槽没有"槽 → 配方"可言
                }
                if (hasRecipeCacheInSlot(slot)) {
                    continue;   // 闸①：已经不再冷（就是"读取缓存"那一半的正常命中态）⇒ 不需要动
                }
                if (!hasSlotWaitingStock(slot)) {
                    continue;   // 闸①b：主机还没往这个槽派活，扫了也是白扫
                }
                if (coldSlotHealTried.size() >= COLD_SLOT_HEAL_BUDGET) {
                    return;   // 总量闸：本次遍历到此为止
                }
                ItemStack patternNow = patternInventory.getStackInSlot(slot);
                if (coldSlotHealTried.contains(slot)) {
                    ItemStack patternBefore = coldSlotHealPattern.get(slot);
                    if (patternBefore != null && ItemStack.matches(patternBefore, patternNow)) {
                        continue;   // 这个槽这一张样板已经试过了 ⇒ 不再重复烧 CPU
                    }
                }
                // ★★ 重置（外部建议的前半句）：解掉 TYPE_UNKNOWN / 旧下标的永久固化 ★★
                setRecipeTypeCache(slot, TYPE_NOT_TRIED);
                coldSlotHealTried.add(slot);
                coldSlotHealPattern.put(slot, patternNow.copy());
                coldSlotHealNextTick = now + COLD_SLOT_HEAL_INTERVAL_TICKS;
                // ★★ 读取（外部建议的后半句）：重识别 ⇒ 唯一命中时经官方口喂进 slot2RecipesCache ★★
                int detected = recipeTypeOfSlot(slot);
                if (detected >= 0 && hasRecipeCacheInSlot(slot)) {
                    coldSlotHealWarmed++;
                    GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪冷槽重识别成功: pos={} 槽={} 识别类型下标={}"
                                    + "（已喂热，累计 {} 次；冷槽重识别≈『重置缓存+读取缓存』）",
                            getPos(), slot + 1, detected, coldSlotHealWarmed);
                }
                return;   // 一次 tick 最多处理一个槽，把"扫配方表"这类动作摊开
            }
        } catch (Throwable failure) {
            // 自愈是"补救"动作：任何异常都不许打断 tick，也不许影响机器本身
            // （fail-open 口径与 onSlotPatternChanged / slotAllowsRecipe 完全一致）。
            // 这里**记一条 debug**而不是留空 catch：空 catch 是本仓库红线，
            // 而 debug 级在正式游玩时默认不输出 ⇒ 既不刷屏、也不掩盖异常。
            GTLModular.LOGGER.debug("[gtlmodular] 惰性猪咪冷槽重识别失败（已忽略，不影响机器本身）：pos={} {}",
                    getPos(), failure.toString());
        }
    }

    /**
     * 仓内容签名：容量 + 每个非空槽的 (下标, 物品) 指纹。
     * 廉价（{@code ItemStack.hashCode()} 只算 item id + NBT 哈希），但足以发现插入/取出/换样板。
     */
    private long usageSignature() {        long signature = 31L * 31 + getCapacity();
        int capacity = getCapacity();
        for (int i = 0; i < capacity; i++) {
            ItemStack stack = patternInventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                signature = signature * 31 + i;
            } else {
                signature = signature * 31 + (i + 1) * 1_000_003L + stack.hashCode();
            }
        }
        return signature;
    }

    /** 样板仓变了：作废映射 + 请求 AE 刷新 + 保证「已用 + 预留」的容量 + ★ 逐槽处理"换了样板"的后果。 */
    void onPatternInventoryChanged() {
        patternSlotMapDirty = true;
        needPatternSync = true;
        diffSlotPatterns();
        growCapacityFor(highestUsedSlot() + 1 + RESERVE);
        // ★★ P1-②C：样板增删/替换（以及随之而来的容量变化）⇒ **改写缓存必须作废** ★★
        //   现役机是在 `onPatternChange(int)` 里调 `invalidateRewriteCache()`（第 515 行）。
        //   惰性机对应的变化检测点就是本方法（`diffSlotPatterns()` 与 `growCapacityFor()` 都由它触发），
        //   所以挂在这里：**一行都不改 diffSlotPatterns / onSlotPatternChanged 的实现体**（红线），
        //   也不动它们「退还残留料 + 清槽缓存 + 通知主机删旧配方」的既有语义。
        //   这里用**静默版**：AE 通知交给下面那句 needPatternSync → lazyTick 的合并通知，
        //   避免在"每槽变化一次"的路径上直接捅 AE（O(n²)，见下）。
        invalidateRewriteCacheQuietly();
        // ★★ 0.3.5：这里**只置标记，真正的 AE 通知合并到 {@code lazyTick} 里每 tick 一次** ★★
        //   为什么不在这儿直接捅 AE：本方法在"每个槽变化一次"的路径上都会跑（批量粘贴 640 张样板
        //   = 640 次），而 `refreshNodeCraftingProvider` 会让 AE 同步重读全部样板（= 每次解码全部样板）
        //   ⇒ 直接调用会退化成 O(n²)（一次粘贴卡死几秒）。合并进每 tick 的订阅里：最多每 tick 一次，
        //   延迟 ≤ 1 tick（50 ms），玩家完全无感。
        //   为什么要自己兜：AE2 的合成服务把"这台节点能做哪些样板"缓存在自己那边，只有
        //   ① grid.getCraftingService().refreshNodeCraftingProvider(node) ② ICraftingProvider.requestUpdate(mainNode)
        //   能让它重读；而基类那条消费 needPatternSync 的 tick 只在 onMainNodeStateChanged（节点状态**变化**）
        //   里才挂上（javap 实证），时机不对就永远挂不上 ⇒ 换样板后 AE 仍按旧样板表下单（队长实测）。
    }

    /**
     * ★ 0.3.5：真正执行一次「通知 AE 重新取样板表」（防重入；远端/异常一律放过）。
     * 逐条照搬现役机 {@code refreshPatternsNow}（MoltenPatternAssemblyPartMachine 第 1744-1770 行），
     * 两个官方入口都调 —— 现役机的实测注释说明只调其中一个在它的场景里不生效。
     */
    private void refreshPatternsNow() {
        if (isRemote() || refreshingPatterns) {
            return;
        }
        refreshingPatterns = true;
        try {
            appeng.api.networking.IManagedGridNode mainNode = getMainNode();
            appeng.api.networking.IGrid grid = mainNode.getGrid();
            if (grid != null) {
                appeng.api.networking.crafting.ICraftingService crafting = grid.getCraftingService();
                appeng.api.networking.IGridNode node = mainNode.getNode();
                if (crafting != null && node != null) {
                    crafting.refreshNodeCraftingProvider(node);
                }
            }
            // AE2 官方入口二：通用的"样板表变了"通知（gtlcore 基类的 update() 用的也是它）。
            appeng.api.networking.crafting.ICraftingProvider.requestUpdate(mainNode);
        } catch (Throwable ignored) {
            // 拿不到就算了：映射已作废，AE 下次主动取样板仍会重建
        } finally {
            refreshingPatterns = false;
        }
    }

    /**
     * ★ 0.2.6 补齐：逐槽对比「样板有没有被换掉」，并对换掉的槽做猪咪同款的三件事。
     *
     * <p>猪咪的对应实现是 {@code MEPatternBufferPartMachine.onPatternChange(int)}，
     * 它在「槽里的样板与上一次不同」时依次做（javap 实证）：
     * <ol>
     *   <li>{@code slot.getCacheManager().clearAllCaches()} —— 清掉"某原料用哪个 AE 键最优"的缓存
     *       （不清 ⇒ 换样板后可能继续按旧配方的匹配结果供料）；</li>
     *   <li>{@code slot.clearVirtualSupply()} —— 清虚拟供给标记；</li>
     *   <li>{@code removeSlotFromGTRecipeCache(slot)} → {@code removeSlotFromMap.accept(slot)}
     *       —— 这个 IntConsumer 正是 {@code IMEPatternTrait.setOnPatternChange} 注册进来的回调
     *       （由 {@code MEPatternRecipeHandlePart.restoreMachineCache} 注册），
     *       用来把主机侧 {@code recipes2SlotsMap} 里<b>该槽的旧配方条目删掉</b>。
     *       不通知 ⇒ 主机可能拿着"已经撤掉的样板"去做配方；</li>
     *   <li>{@code refundSlot(itemInventory, fluidInventory)} —— 把该槽残留的料<b>退还到
     *       {@code buffer}</b>（基类实现：逐项 {@code buffer.addTo(key, amount)} 后清空槽），
     *       我们的 {@code LazyBufferTicker} 会把它推回 ME 网络 ⇒ 换样板不吞料。</li>
     * </ol>
     *
     * <p>只在服务端做（退还是破坏性操作）；读档后<b>第一次只做基线、不动作</b>
     * （{@link #patternSnapshotReady}），否则会把存档里的存量料当"被换掉的样板"全退回去。
     */
    private void diffSlotPatterns() {
        if (isRemote()) {
            return;
        }
        int capacity = getCapacity();
        while (patternSnapshot.size() < capacity) {
            patternSnapshot.add(ItemStack.EMPTY);
        }
        boolean first = !patternSnapshotReady;
        for (int i = 0; i < capacity; i++) {
            ItemStack now = patternInventory.getStackInSlot(i);
            ItemStack before = patternSnapshot.get(i);
            boolean changed = !ItemStack.matches(before, now);
            if (changed) {
                patternSnapshot.set(i, now.copy());
                if (!first) {
                    onSlotPatternChanged(i);
                }
            }
        }
        patternSnapshotReady = true;
    }

    /** 某个槽的样板被换掉（含新增/移除）之后的收尾：清缓存 + 退还残留料 + 通知主机删旧配方条目。 */
    private void onSlotPatternChanged(int slot) {
        InternalSlot internal = getInternalSlot(slot);
        if (internal == null) {
            return;
        }
        try {
            internal.getCacheManager().clearAllCaches();
            internal.clearVirtualSupply();
            // 退还残留料：基类 refundSlot 把它们搬进 buffer，我们的 ticker 负责推回网络
            refundSlot(internal.getItemInventory(), internal.getFluidInventory());
        } catch (Throwable ignored) {
            // 清缓存/退还是"补救"动作：任何异常都不许打断换样板本身
        }
        // ★ 0.2.8（审计发现 A）：退完必须**立刻推网 + 叫醒 ticker**。
        //   猪咪在 `onPatternChange` 里 refundSlot 之后紧跟一句
        //   `AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource)`（javap 实证，偏移 136-156）；
        //   而我们的 LazyBufferTicker 在 buffer 空时会把 isSleeping 置真并返回 SLEEP，
        //   唤醒只来自 gtlcore 那 4 处 notifySelfIO —— "换样板"本身不在其中
        //   ⇒ 只退还而不推网的话，退回的料会**停在机器内部 buffer 里（网络看不见）**，
        //   要等下一次合成/网格事件才会冒出来。
        try {
            AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource);
        } catch (Throwable ignored) {
            // 推不动就交给 ticker（下同）
        }
        try {
            getMETrait().notifySelfIO();   // isSleeping && 在线 → wakeDevice，把 ticker 叫醒继续推
        } catch (Throwable ignored) {
            // 叫不醒也不影响机器的其它部分
        }
        // ★ 0.2.8（审计发现 C）：镜像（代理）总成侧也维护着一份 recipes2SlotsMap，
        //   猪咪的 removeSlotFromGTRecipeCache 末尾会调它（基类实现 = 遍历 getProxies() 通知）。
        try {
            notifyProxySlotRemoved(slot);
        } catch (Throwable ignored) {
            // 没挂镜像时是无害 no-op
        }
        // ★ 通知主机：把该槽的旧配方条目从 recipes2SlotsMap 里删掉（等价猪咪的 removeSlotFromMap），
        //   并清掉本 trait 里该槽的配方缓存（等价猪咪的 recipeMultipleCacheMap.remove(slot)）。
        LazyMEPatternTrait patternTrait = (LazyMEPatternTrait) getMETrait();
        if (patternTrait != null) {
            patternTrait.clearSlotCache(slot);
            patternTrait.notifyPatternChange(slot);
        }
        // ★ P2-①：样板被换掉 ⇒ 该槽「记住的配方类型」必须跟着清（照搬现役机 onPatternChange 的第一步
        //   `slotRecipeType[index] = -1`，语义相同：新样板要重新识别，旧的结论绝不能留着压新样板）。
        //   放在上面同一条通知/退款路径**之后**，不影响任何既有动作。
        //   注意：**手动指定不清**（现役机同样只在样板变化时清自动识别结果）—— 手动指定是玩家对该槽的
        //   长期意图，换一张同模式的样板不该把它抹掉；玩家可以随时在模式页上「清除」。
        try {
            setRecipeTypeCache(slot, TYPE_NOT_TRIED);
            // 该槽的类型结论变了 ⇒ 拉黑表作废（否则新样板会被上一次的拉黑误伤）
            autoSwitchBlockedUntil.clear();
            // ★【① 冷槽重识别】样板换了 ⇒ 把该槽的重试记账一起作废，
            //   让"重置缓存 + 重识别"对这张新样板重新有一次机会（冷槽重识别本身有总量闸，不会失控）。
            coldSlotHealTried.remove(slot);
            coldSlotHealPattern.remove(slot);
        } catch (Throwable ignored) {
            // 清缓存失败不影响换样板本身
        }
    }

    @Override
    public void onMachineRemoved() {
        if (usageSubscription != null) {
            usageSubscription.unsubscribe();
            usageSubscription = null;
        }
        super.onMachineRemoved();
        // ★★ 拆机时把样板**掉落**到地上（0.2.2 修复）★★
        // gtlcore 具体类 MEPatternBufferPartMachine.onMachineRemoved() 里就有这一句
        // `clearInventory(this, patternInventory)`；MetaMachine.clearInventory 的字节码实证是
        // 「逐个非空槽 → 清空槽 → transfer.onContentsChanged() → Block.popResource(level, pos, stack)」，
        // 也就是真掉落到地上。我 0.2.0/0.2.1 漏了这句 ⇒ 拆机后样板直接蒸发（用户实测）。
        // 注意：这里与 saveCustomPersistedData 的 forDrop 分支是**一对**——
        // 掉落物里不能再带一份样板，否则就是「地上掉一份 + 方块里藏一份」的复制漏洞。
        clearInventory(this.patternInventory);
        // ★ P1-②B：催化剂物品也照现役机一样掉到地上。
        //   现役机 onMachineRemoved 的字节码（_javap_p1b_concrete_ctor.txt 偏移 12-41）就是
        //   `for (ItemStackTransfer t : catalystItems) clearInventory(t)`：
        //   MetaMachine.clearInventory 会把每个非空槽 popResource 到地上。
        //   不搬这一句 = 拆机时催化剂物品**静默蒸发**（样板有掉落、催化剂没有，说不过去）。
        //   流体那一份现役机也不掉（clearInventory 只吃 IItemTransfer）⇒ 这里同样只处理物品。
        try {
            for (ItemStackTransfer catalyst : catalystItems) {
                if (catalyst != null) {
                    clearInventory(catalyst);
                }
            }
        } catch (Throwable ignored) {
            // 掉催化剂失败绝不许影响拆机本身
        }
    }

    // ==================== 存档 ====================

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        // ★★ forDrop 分支是防复制漏洞的关键（0.2.2 修复）★★
        // LDLib IAutoPersistBlockEntity.saveManagedPersistentData(tag, forDrop) 字节码：
        //     for (IRef ref : getRootStorage().getPersistedFields()) {
        //         if (forDrop && !ref.getKey().isDrop()) continue;   // 掉落时只写「可掉落」的 @Persisted 字段
        //         ...写入...
        //     }
        //     saveCustomPersistedData(tag, forDrop);                 // ← 而这一句是**无条件**的
        // 结论：我们的 @Persisted patternInventory（drop 默认 false）在掉落时**不会**由托管机制写入；
        // 但如果我在这里无条件手写一份，就会把整仓样板塞进掉落方块 ⇒ 配合 onMachineRemoved 的地面掉落 = **复制**。
        // 猪咪的做法：托管字段不写、内容靠 clearInventory 掉地上。这里照做：
        //   · forDrop == true （被拆成掉落物）→ 只写「机器设置」类信息（机器名），内容一律不写；
        //   · forDrop == false（世界存档）    → 照写（与托管字段同键同值，属双保险）。
        if (!forDrop) {
            // ① 样板本体：键名必须叫 patternInventory（patternInventory.Items 是全部样板的存放处）
            tag.put("patternInventory", patternInventory.serializeNBT());
            // ② 每槽状态：写短表（写到最后一个已实体化的槽为止），空槽位写一个空 CompoundTag 占位保持索引对齐
            ListTag slotList = new ListTag();
            int lastBuilt = -1;
            for (int i = 0; i < internalInventory.size(); i++) {
                if (internalInventory.get(i) != null) {
                    lastBuilt = i;
                }
            }
            for (int i = 0; i <= lastBuilt; i++) {
                InternalSlot slot = internalInventory.get(i);
                slotList.add(slot == null ? new CompoundTag() : slot.serializeNBT());
            }
            tag.put("internalInventory", slotList);
        }
        // ③ 机器名（设置类信息，掉落时保留，与猪咪一致）
        String name = getCustomName();
        tag.putString("customName", name == null ? "" : name);

        // ★★ P1-②C：视图改写设置（倍率 / 除率 / 神锻开关 / 神锻倍率 / 输出模式）★★
        //   【forDrop 行为】这五个都是**设置类**字段（不带任何物品/流体内容，抄不掉东西）
        //   ⇒ 与上面那句 customName 同口径：**掉落时也写**（机器被拎起来再放下不该丢倍率），
        //     所以刻意放在下面 `if (forDrop) return;` **之前** —— 与「内容类一律不写」的红线不冲突。
        //   【为什么现役机不需要这一段】见 _javap_p1c_api.txt 第【13】节：现役机这几个字段只带
        //   LDLib 的 @DescSynced，而 ManagedFieldUtils.createKey 只在带 @Persisted 时才把 isPersist 置真
        //   ⇒ 现役机既不写世界存档、也不写掉落物（重启后回到默认值）。惰性机按本机既有规则自己补键，
        //     键名统一带 gtlmodular$lazy_ 前缀（与本机其它新键同前缀，不与现役机格式撞车）。
        tag.putInt(NBT_PATTERN_SCALE, patternScale);
        tag.putInt(NBT_PATTERN_DIV_SCALE, patternDivScale);
        tag.putBoolean(NBT_FOA_MODE, foaMode);
        tag.putInt(NBT_FOA_MULTIPLIER, foaMultiplier);
        tag.putBoolean(NBT_OUTPUT_MODE, isOutputMode);

        // ★★ 2026-10-14（本轮新增）：**绑定模式持久化** —— 本总成负责的模式的**类型 id** ★★
        //   【为什么需要】modeCursorIndex 只带 @DescSynced、**无 @Persisted**，也不在既有任何键里
        //     ⇒ 读档后归 0、modeCursorTouched=false ⇒ 每 tick 的「游标跟随控制器」把它改回主机模式
        //     ⇒ 重启后绑定失效（本键要修的就是这一条）。
        //   【键名】NBT_BOUND_MODE_TYPE = `gtlmodular$lazy_bound_mode_type`：沿用本机 gtlmodular$lazy_ 前缀；
        //     已 grep 全仓确认此前无人使用，与本机既有键（gtlmodular$lazy_slot_recipe_type_sparse /
        //     gtlmodular$lazy_manual_slot_types / gtlmodular$lazy_pattern_* / gtlmodular$lazy_catalyst_slots）
        //     以及现役机新键 gtlmodular$bound_mode_type 都不撞车。
        //   【格式】单个字符串：ResourceLocation#toString()（例 `gtceu:lathe`）——
        //     存 id 不存下标：类型表的顺序/长度随控制器与配置变化，存下标读档后会指到别的模式；
        //     存 id 与本机 manualSlotTypes 同口径（那边也是 id 字符串，见该字段的注释）。
        //   【未绑定 ⇒ 一个字节都不写】键缺失的语义就是"未绑定/跟随控制器"（= 老档的默认行为）✓；
        //     玩家选定模式时由 rememberBoundModeType() 记进 field 并 markDirty，这里只做序列化。
        //   【forDrop 口径】与上面 5 个设置类键同口径：它不带任何物品/流体内容、抄不掉东西
        //     ⇒ 刻意放在下面 `if (forDrop) return;` **之前**（机器拎起来再放下不该丢绑定）。
        if (boundModeType != null) {
            tag.putString(NBT_BOUND_MODE_TYPE, boundModeType.toString());
        }

        // ★★ 2026-10-15（本轮新增）：**名字按「本总成 + 模式」落盘** —— "重进游戏名字被改回去"的根治点 ★★
        //   【键名】{@link #NBT_MODE_NAMES}（CompoundTag：模式 id → 名字）与 {@link #NBT_LOCKED_NAME}
        //     （String：玩家亲手改过的名字，空 = 没改过）。已 grep 全仓确认这两个键名此前无人使用，
        //     与本机既有键（NBT_BOUND_MODE_TYPE / lazy_slot_recipe_type* / lazy_manual_slot_types /
        //     lazy_pattern_* / lazy_catalyst_slots）以及现役机的 gtlmodular$mode_names 都不撞车 ✓。
        //   【向后兼容】键缺失 ⇒ 名字表为空 + 未锁定 = 改动前行为（该模式还没有名字 ⇒ 用显示名写一次）✓；
        //     既有键一个都没碰（**只新增**）✓。
        //   【上界】名字表 ≤ {@value #MAX_SAVED_MODE_NAMES} 条、单条 ≤ {@value #MAX_SAVED_NAME_LENGTH} 字符。
        //   【forDrop】与 boundModeType / 5 个设置类键同口径：刻意放在下面 `if (forDrop) return;` **之前**
        //     —— 掉落也写（机器拎起来再放下不该丢名字/绑定）。
        if (!modeNames.isEmpty()) {
            CompoundTag names = new CompoundTag();
            int written = 0;
            for (java.util.Map.Entry<String, String> entry : modeNames.entrySet()) {
                if (written >= MAX_SAVED_MODE_NAMES) {
                    break;
                }
                String value = entry.getValue();
                if (entry.getKey() == null || entry.getKey().isEmpty() || value == null || value.isEmpty()) {
                    continue;
                }
                names.putString(entry.getKey(), value.length() > MAX_SAVED_NAME_LENGTH
                        ? value.substring(0, MAX_SAVED_NAME_LENGTH) : value);
                written++;
            }
            if (written > 0) {
                tag.put(NBT_MODE_NAMES, names);
            }
        }
        if (!lockedName.isEmpty()) {
            tag.putString(NBT_LOCKED_NAME, lockedName.length() > MAX_SAVED_NAME_LENGTH
                    ? lockedName.substring(0, MAX_SAVED_NAME_LENGTH) : lockedName);
        }

        // ★ P2：逐槽「记住的配方类型」+ 手动指定表。
        //   **只在 forDrop == false 时写**（见类 javadoc 的 forDrop 规则）：两者都是「按槽下标对齐」的内容，
        //   而掉落时样板本身随 onMachineRemoved 掉到地上、不随方块走 ⇒ 写了就是一堆指错槽的孤儿数据。
        //   宽度按**当前容量**（= 表长）序列化，不做定长填充；读回时再按当时的容量校验。
        if (forDrop) {
            return;
        }
        ensureRecipeTypeSlotCapacity();
        IntArrayList sparse = new IntArrayList();
        for (int i = 0; i < slotRecipeTypes.size(); i++) {
            Integer value = slotRecipeTypes.get(i);
            if (value == null || value == TYPE_NOT_TRIED) {
                continue;   // 绝大多数槽都是"没尝试过"，稀疏写掉
            }
            sparse.add(i);
            sparse.add(value);
        }
        if (!sparse.isEmpty()) {
            tag.putIntArray(NBT_SLOT_RECIPE_TYPE_SPARSE, sparse.toIntArray());
        }
        if (!manualSlotTypes.isEmpty()) {
            CompoundTag manual = new CompoundTag();
            manualSlotTypes.forEach((key, id) -> manual.putString("s" + key, id.toString()));
            tag.put(NBT_MANUAL_SLOT_TYPES, manual);
        }

        // ⑦ ★ P1-②B：逐槽催化剂仓（物品 9 格 / 流体 9 罐 / 缓存阈值）。
        //   同样是「按槽下标对齐的内容」⇒ 只在世界存档写、掉落时不写（上面 forDrop 已经 return）。
        //   现役机靠 LDLib @Persisted 数组字段自动存取，惰性机的动态 List 只能自己写，见 saveCatalystSlots 的说明。
        saveCatalystSlots(tag);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);

        // ① 样板本体（托管字段机制已先读过一次，这里再显式读一次做双保险；键名一致，重复读是幂等的）
        if (tag.contains("patternInventory", Tag.TAG_COMPOUND)) {
            patternInventory.deserializeNBT(tag.getCompound("patternInventory"));
        }
        // ★ 0.2.8（审计发现）：存档里的 Size 可能大于本机上限（跨版本/改配置/外部工具导入）。
        //   截断到上限，避免"容量越界 ⇒ getInternalSlot 返回 null ⇒ 基类 filter(InternalSlot::isActive) NPE"，
        //   也避免我们把装不下的样板当成合法内容。丢弃的会打 WARN 说明清楚（不静默吞）。
        clampCapacityToMax();

        // ② 每槽状态：读侧<同时>兼容「旧档满长度 128/640」与「新档短表」
        //    规则：按索引对齐；读不出的槽视为空；绝不因为长度不符就丢掉 patternInventory。
        ListTag slotList = tag.getList("internalInventory", Tag.TAG_COMPOUND);
        int lastStateful = -1;
        for (int i = 0; i < slotList.size(); i++) {
            if (!slotList.getCompound(i).isEmpty()) {
                lastStateful = i;
            }
        }
        int used = highestUsedSlot() + 1;
        // 容量至少要装下「已用样板」与「真的有状态的槽」，再留预留
        growCapacityFor(Math.max(used, lastStateful + 1) + RESERVE);
        while (internalInventory.size() < getCapacity()) {
            internalInventory.add(null);
        }
        for (int i = 0; i < slotList.size() && i < getCapacity(); i++) {
            CompoundTag slotTag = slotList.getCompound(i);
            if (slotTag.isEmpty()) {
                continue;
            }
            InternalSlot slot = getInternalSlot(i);
            if (slot != null) {
                slot.deserializeNBT(slotTag);
            }
        }

        // ③ 机器名（托管字段已读过；只在还空着时补，避免覆盖）
        if (tag.contains("customName", Tag.TAG_STRING)) {
            String name = tag.getString("customName");
            String current = getCustomName();
            if (!name.isEmpty() && (current == null || current.isEmpty())) {
                setCustomName(name);
            }
        }

        // ③' ★ P1-②C：视图改写设置（与 saveCustomPersistedData 的那五个键一一对应）。
        //   读侧一律**夹到合法区间**（存档可能来自改过配置/手改 NBT 的档），缺键则保持字段初值。
        //   直接用字段赋值、不用 setter：读档期节点多半还没上线，通知 AE 是白通知；
        //   而且下面紧接着就会置 needPatternSync ⇒ lazyTick 的合并通知会在上线后补一次。
        //   只调**静默作废**（缓存此刻本来就还没建，写这一句是为了"读档后一定从零重建"这件事显式可见）。
        try {
            if (tag.contains(NBT_PATTERN_SCALE, Tag.TAG_INT)) {
                patternScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, tag.getInt(NBT_PATTERN_SCALE)));
            }
            if (tag.contains(NBT_PATTERN_DIV_SCALE, Tag.TAG_INT)) {
                patternDivScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, tag.getInt(NBT_PATTERN_DIV_SCALE)));
            }
            if (tag.contains(NBT_FOA_MODE, Tag.TAG_BYTE)) {
                foaMode = tag.getBoolean(NBT_FOA_MODE);
            }
            if (tag.contains(NBT_FOA_MULTIPLIER, Tag.TAG_INT)) {
                foaMultiplier = Math.max(MIN_FOA_MULTIPLIER,
                        Math.min(MAX_FOA_MULTIPLIER, tag.getInt(NBT_FOA_MULTIPLIER)));
            }
            if (tag.contains(NBT_OUTPUT_MODE, Tag.TAG_BYTE)) {
                isOutputMode = tag.getBoolean(NBT_OUTPUT_MODE);
            }
            invalidateRewriteCacheQuietly();
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：视图改写设置读档失败（已忽略，按默认值）pos={} 原因={}",
                    getPos(), failure.toString());
        }

        patternSlotMapDirty = true;
        needPatternSync = true;
        usageSignatureValid = false;   // 下一 tick 强制重算一次签名（原为 lastUsageSignature = Long.MIN_VALUE）
        // ★ 读档后重建「样板快照」基线：只记基线、不触发退还/清缓存（见 diffSlotPatterns 的注释）
        patternSnapshotReady = false;
        patternSnapshot.clear();
        diffSlotPatterns();
        // 上面已把 internalInventory 对齐到容量（容量只会增长，不会缩）

        // ④ ★ P2：逐槽配方类型表（稀疏格式，见类 javadoc）—— 越界/非法值一律丢弃，绝不猜
        try {
            ensureRecipeTypeSlotCapacity();
            int capacity = getCapacity();
            int[] sparse = tag.getIntArray(NBT_SLOT_RECIPE_TYPE_SPARSE);
            for (int i = 0; i + 1 < sparse.length; i += 2) {
                int slot = sparse[i];
                int value = sparse[i + 1];
                if (slot < 0 || slot >= capacity) {
                    continue;   // 容量缩过（换档位/改配置）时的陈旧下表：直接丢
                }
                if (value < TYPE_UNKNOWN) {
                    continue;   // 非法值：宁可当"没识别"
                }
                setRecipeTypeCache(slot, value);
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：逐槽配方类型读档失败（已忽略，按未识别处理）pos={} 原因={}",
                    getPos(), failure.toString());
        }

        // ⑤ ★ P2：手动指定表（键 s<槽下标> → 类型 id 字符串）
        try {
            manualSlotTypes.clear();
            CompoundTag manual = tag.getCompound(NBT_MANUAL_SLOT_TYPES);
            int capacity = getCapacity();
            for (String key : manual.getAllKeys()) {
                if (!key.startsWith("s")) {
                    continue;
                }
                try {
                    int slot = Integer.parseInt(key.substring(1));
                    if (slot < 0 || slot >= capacity) {
                        continue;   // 与上面同口径：容量外的下表直接丢
                    }
                    ResourceLocation id = ResourceLocation.tryParse(manual.getString(key));
                    if (id != null) {
                        manualSlotTypes.put(slot, id);
                    }
                } catch (RuntimeException ignored) {
                    // 坏条目直接跳过
                }
            }
            refreshManualAssignDisplay();
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：手动指定表读档失败（已忽略）pos={} 原因={}",
                    getPos(), failure.toString());
        }

        // ⑤' ★ 2026-10-14（本轮新增）：绑定模式读档（与 saveCustomPersistedData 的 NBT_BOUND_MODE_TYPE 一一对应）
        //   【读侧纪律】
        //   ① contains 守卫（带类型）：老存档没有这个键 ⇒ boundModeType 保持 null = 未绑定 ⇒
        //      modeCursorTouched 保持 false ⇒ **完全走现有默认行为**（游标跟随控制器）✓ 既有键一个都没碰 ✓；
        //   ② 坏值（空串 / 不是合法的 ResourceLocation）⇒ tryParse 返回 null ⇒ 同"未绑定"处理，
        //      **不报错、不抛异常**（与上面 manualSlotTypes 的坏条目同口径）✓；
        //   ③ 解析下标**不在这里做**：读档期机器还没挂到 level / 控制器上（getControllers() 为空）⇒
        //      getAvailableRecipeTypes() 为空 ⇒ 此刻无法区分"类型被移除"与"控制器还没就绪"（见
        //      applyBoundModeTypeIfPossible 的注释）⇒ 这里只记 id + 置"待解析"，由每 tick 的服务端
        //      autoSwitchRecipeModes() 补解析；顺手调一次是给"控制器已经挂上"的场合（热重载）当场生效；
        //   ④ 整体 try/catch(Throwable) 吞掉：一个绑定键坏掉绝不许把整台机器读挂（同 ③/④/⑤ 三条纪律）。
        try {
            // 【服务端持久化】客户端那份 BE 的 NBT（区块包/同步）里同样会有这个键，但那不是权威状态
            //   ⇒ 客户端不读回落盘状态；模式页显示靠既有的 @DescSynced modeCursorIndex 同步回来 ✓。
            if (!isRemote() && tag.contains(NBT_BOUND_MODE_TYPE, Tag.TAG_STRING)) {
                ResourceLocation bound = ResourceLocation.tryParse(tag.getString(NBT_BOUND_MODE_TYPE));
                if (bound != null) {
                    this.boundModeType = bound;
                    this.boundModePendingResolve = true;
                    applyBoundModeTypeIfPossible();
                }
            }
        } catch (Throwable failure) {
            this.boundModeType = null;
            this.boundModePendingResolve = false;
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：绑定模式读档失败（已忽略，按未绑定处理）pos={} 原因={}",
                    getPos(), failure.toString());
        }

        // ⑤'' ★ 2026-10-15（本轮新增）：**名字表 + "玩家改过名"标记读档**
        //   （与 saveCustomPersistedData 的 NBT_MODE_NAMES / NBT_LOCKED_NAME 一一对应）
        //   【读侧纪律】① contains 守卫（带类型）：老存档没有这两个键 ⇒ 名字表为空 + 未锁定 =
        //     改动前的行为，既有键一个都没碰 ✓；② 坏条目（空键/空值/超长）一律跳过、整体
        //     try/catch(Throwable) 吞掉 —— 一个坏键绝不许把整台机器读挂（与 manualSlotTypes 同口径）；
        //   ③ 读完必须把"上次观察到的名字"清空：否则读档恢复出来的名字会被当成"玩家改名"而误锁 ✗。
        try {
            if (!isRemote()) {
                modeNames.clear();
                this.lockedName = "";
                this.observedName = null;
                this.appliedModeKey = null;
                this.lastNamingController = null;
                if (tag.contains(NBT_MODE_NAMES, Tag.TAG_COMPOUND)) {
                    CompoundTag names = tag.getCompound(NBT_MODE_NAMES);
                    int accepted = 0;
                    for (String modeKey : names.getAllKeys()) {
                        if (accepted >= MAX_SAVED_MODE_NAMES) {
                            break;
                        }
                        String value = names.getString(modeKey);
                        if (modeKey.isEmpty() || value.isEmpty() || value.length() > MAX_SAVED_NAME_LENGTH) {
                            continue;   // 坏条目 / 超长 ⇒ 跳过
                        }
                        modeNames.put(modeKey, value);
                        accepted++;
                    }
                }
                if (tag.contains(NBT_LOCKED_NAME, Tag.TAG_STRING)) {
                    String locked = tag.getString(NBT_LOCKED_NAME);
                    this.lockedName = locked.length() > MAX_SAVED_NAME_LENGTH ? "" : locked;
                }
            }
        } catch (Throwable failure) {
            modeNames.clear();
            this.lockedName = "";
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：名字表读档失败（已忽略，按「该模式还没有名字」处理）pos={} 原因={}",
                    getPos(), failure.toString());
        }

        // ⑥ ★ P2-③：名字按模式 —— 读档/放置后也补一次，省得等一次模式切换才起名。
        //   判据与 tick 主落点**同一套**（{@link #syncCustomNameToMode(int)}：查「本总成 + 模式」的名字表，
        //   玩家改过名则永久让位 ✗）。读档期控制器还没挂上 ⇒ 方法内部解析不到模式、什么都不写；
        //   真正的起名由读档后第一次服务端 tick 完成 ✓。
        //   ★ 2026-10-04b：这一行**不再**自带判空/兜底串守卫（那种守卫会让"写一次之后再也不进"✗），
        //   但读档期机器还没挂到 level / 控制器上（{@code getControllers()} 为空）⇒ 方法内部解析不到
        //   "当前机器模式" ⇒ 自动**保持原名不动** ✗；真正的起名由读档后第一次服务端 tick 完成
        //   （{@link #autoSwitchRecipeModes()} 的主落点）。保留这一行只为"自愈"语义兜底：
        //   控制器已经挂上时（例如热重载）能当场补名 ✓。
        syncCustomNameToMode(activeRecipeTypeIndex);

        // ⑦ ★ P1-②B：逐槽催化剂仓（物品 / 流体 / 缓存阈值）。
        //   放在最后：那时「容量（getPatternSlotCount）」与「逐槽状态（internalInventory 短表）」都已还原完，
        //   才知道哪些槽下标是合法的、才能用刚还原的催化剂仓重算该槽的催化剂表。
        loadCatalystSlots(tag);
    }

    // ==================== P1-②B 专属催化剂槽：逐槽子类 ====================

    /**
     * ★ P1-②B <b>逐槽 InternalSlot</b> —— 逐条照搬现役机
     * {@code MEPatternBufferPartMachine$PatternBufferInternalSlot}
     * （反汇编存盘：{@code _javap_p1b_slot.txt}）。
     *
     * <p><b>为什么必须自己写一个子类</b>：基类 {@code MEPatternBufferPartMachineBase$InternalSlot}
     * 里那一组催化剂方法是**空实现**（逐条字节码实证，反汇编存盘 {@code _javap_p1b_base_slot.txt}）：
     * <ul>
     *   <li>{@code getItemCatalystInventory()} → {@code Object2LongMaps.emptyMap()}（不可变空表）；</li>
     *   <li>{@code getFluidCatalystInventory()} → {@code Object2LongMaps.emptyMap()}；</li>
     *   <li>{@code hasItemCatalystInventory()} / {@code hasFluidCatalystInventory()} → {@code iconst_0 / ireturn}；</li>
     *   <li>{@code appendLimitItemCatalystInputs} / {@code appendLimitFluidCatalystInputs} → {@code return}（什么都不做）；</li>
     *   <li>{@code testCatalystItemInternal} / {@code testCatalystFluidInternal} → {@code iconst_1 / ireturn}（永远放行）。</li>
     * </ul>
     * 而基类里的消费方读的就是这一组方法（{@code isItemActive} → {@code hasItemCatalystInventory}；
     * {@code getLimitItemStackInput} → {@code appendLimit*CatalystInputs}；{@code handleItemInternal} →
     * {@code getItemCatalystInventory} / {@code testCatalystItemInternal}）
     * ⇒ 不覆写 = 催化剂永远不起作用。现役机正是用这个子类把它们换成「本槽自己的表」。
     */
    public class LazyPatternBufferInternalSlot extends InternalSlot {

        /**
         * 本槽的催化剂表。★ 与现役机同款：{@code Object2LongArrayMap} + {@code defaultReturnValue(0L)}
         * （现役机子类构造器字节码：`new Object2LongArrayMap` ×2 → `super(base, index)` →
         * 两次 `defaultReturnValue:(J)V`）。
         */
        private final Object2LongMap<AEItemKey> itemCatalystInventory = new Object2LongArrayMap<>();
        private final Object2LongMap<AEFluidKey> fluidCatalystInventory = new Object2LongArrayMap<>();

        LazyPatternBufferInternalSlot(int index) {
            // 基类的 InternalSlot 是**内部类**（外层 = MEPatternBufferPartMachineBase），
            // 而本类又是 LazyPatternAssemblyPartMachine（基类的子类）的内部类 ⇒
            // 外层实例由 javac 隐式传入（就是 LazyPatternAssemblyPartMachine.this），
            // 源码侧只传槽下标（字节码侧才看得到 `(Base, int)` 两个参数，与现役机子类构造器逐字一致）。
            super(index);
            itemCatalystInventory.defaultReturnValue(0L);
            fluidCatalystInventory.defaultReturnValue(0L);
        }

        /** 照搬现役机子类（字节码：`getfield itemCatalystInventory; areturn`）。 */
        @Override
        public Object2LongMap<AEItemKey> getItemCatalystInventory() {
            return itemCatalystInventory;
        }

        /** 照搬现役机子类（字节码：`getfield fluidCatalystInventory; areturn`）。 */
        @Override
        public Object2LongMap<AEFluidKey> getFluidCatalystInventory() {
            return fluidCatalystInventory;
        }

        /** 照搬现役机子类（字节码：`map.isEmpty() ifne false` ⇒ 非空即 true）。 */
        @Override
        protected boolean hasItemCatalystInventory() {
            return !itemCatalystInventory.isEmpty();
        }

        /** 照搬现役机子类（同口径，换成流体表）。 */
        @Override
        protected boolean hasFluidCatalystInventory() {
            return !fluidCatalystInventory.isEmpty();
        }

        /**
         * 照搬现役机子类 {@code appendLimitItemCatalystInputs(ObjectList<ItemStack>)}：
         * 逐项 {@code AEItemKey.toStack((int) Math.min(数量, 2147483647L))} 追加
         * （字节码：`getLongValue` → `Math.min(JJ)` → `l2i` → `AEItemKey.toStack(I)`）。
         */
        @Override
        protected void appendLimitItemCatalystInputs(ObjectList<ItemStack> inputs) {
            for (Object2LongMap.Entry<AEItemKey> entry : Object2LongMaps.fastIterable(itemCatalystInventory)) {
                inputs.add(entry.getKey().toStack((int) Math.min(entry.getLongValue(), Integer.MAX_VALUE)));
            }
        }

        /** 照搬现役机子类 {@code appendLimitFluidCatalystInputs}：{@code FluidStack.create(流体, 数量)}。 */
        @Override
        protected void appendLimitFluidCatalystInputs(ObjectList<FluidStack> inputs) {
            for (Object2LongMap.Entry<AEFluidKey> entry : Object2LongMaps.fastIterable(fluidCatalystInventory)) {
                inputs.add(FluidStack.create(entry.getKey().getFluid(), entry.getLongValue()));
            }
        }

        /**
         * 照搬现役机子类 {@code testCatalystItemInternal(GTRecipe)}：本槽的催化剂表里**已经含有**
         * 这条配方某个（{@code chance > 0} 的）物品输入 ⇒ 返回 false（这条配方不能在本槽跑，
         * 免得把放进催化剂槽的那件东西当原料吃掉）。
         */
        @Override
        public boolean testCatalystItemInternal(GTRecipe recipe) {
            if (recipe == null) {
                return true;
            }
            for (Content content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
                if (content.chance <= 0) {
                    continue;   // 字节码：`getfield chance: ifgt 42`（chance ≤ 0 直接跳过）
                }
                Object raw = content.getContent();
                if (!(raw instanceof Ingredient ingredient)) {
                    continue;
                }
                for (ItemStack stack : ingredient.getItems()) {
                    if (itemCatalystInventory.containsKey(AEItemKey.of(stack))) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** 照搬现役机子类 {@code testCatalystFluidInternal(GTRecipe)}（口径同上，换成流体输入）。 */
        @Override
        public boolean testCatalystFluidInternal(GTRecipe recipe) {
            if (recipe == null) {
                return true;
            }
            for (Content content : recipe.getInputContents(FluidRecipeCapability.CAP)) {
                if (content.chance <= 0) {
                    continue;
                }
                Object raw = content.getContent();
                if (!(raw instanceof FluidIngredient ingredient)) {
                    continue;
                }
                for (FluidStack stack : ingredient.getStacks()) {
                    if (fluidCatalystInventory.containsKey(AEFluidKey.of(stack.getFluid()))) {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * 照搬现役机子类 {@code serializeNBT()}：在基类结果之上追加
         * {@code catalystInventory}（{@code AEItemKey::toTag}）与
         * {@code catalystFluidInventory}（{@code AEFluidKey::toTag}），空表不写
         * （字节码：`ListTag.isEmpty() ifne 跳过 put`）。
         */
        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = super.serializeNBT();
            ListTag items = AEUtils.createListTag(AEItemKey::toTag, itemCatalystInventory);
            if (!items.isEmpty()) {
                tag.put("catalystInventory", items);
            }
            ListTag fluids = AEUtils.createListTag(AEFluidKey::toTag, fluidCatalystInventory);
            if (!fluids.isEmpty()) {
                tag.put("catalystFluidInventory", fluids);
            }
            return tag;
        }

        /**
         * 照搬现役机子类 {@code deserializeNBT(CompoundTag)}：先 {@code super}、再清表、
         * 然后 {@code AEUtils.loadInventory(list, AEItemKey::fromTag / AEFluidKey::fromTag, map)} 读回。
         */
        @Override
        public void deserializeNBT(CompoundTag tag) {
            super.deserializeNBT(tag);
            itemCatalystInventory.clear();
            AEUtils.loadInventory(tag.getList("catalystInventory", Tag.TAG_COMPOUND),
                    AEItemKey::fromTag, itemCatalystInventory);
            fluidCatalystInventory.clear();
            AEUtils.loadInventory(tag.getList("catalystFluidInventory", Tag.TAG_COMPOUND),
                    AEFluidKey::fromTag, fluidCatalystInventory);
        }
    }

    // ==================== P1-②B 专属催化剂槽：逐槽催化剂表（惰性增长） ====================

    /**
     * 把三张逐槽催化剂表补到至少 {@code index + 1} 长（<b>只增不减</b>，与 {@link #internalInventory}
     * / {@link #slotRecipeTypes} 同纪律）。只补"没建过"的占位（{@code null} / 默认阈值），
     * <b>不建实体仓</b>。
     */
    private void ensureCatalystTableLength(int index) {
        while (catalystItems.size() <= index) {
            catalystItems.add(null);
        }
        while (catalystFluids.size() <= index) {
            catalystFluids.add(null);
        }
        while (cacheRecipeCount.size() <= index) {
            cacheRecipeCount.add((byte) DEFAULT_CACHE_RECIPE_COUNT);
        }
    }

    /**
     * ★ <b>惰性建出</b>某个槽的催化剂仓（9 格物品 + 9 罐 × 16 桶流体），只增不减。
     * 两个仓的参数逐条照搬现役机构造器里那两个 {@code Arrays.setAll} 的 lambda
     * （本体见类里「P1-②B 专属催化剂槽」字段段的字节码取证）。
     *
     * <p><b>谁调它</b>（只有这两条路）：① 视图代理（{@link CatalystItemView#serverTransfer()} /
     * {@link CatalystFluidView#serverFluids()}）读写它当前指向的那一槽时（= 界面上真显示到的那一槽）；
     * ② 该槽的催化剂仓真被写入了内容（回调 {@link #onCatalystItemContentsChanged(int)}）。
     * <b>构造期一次都不调</b> ⇒ 不破坏惰性化的意义。
     */
    private void ensureCatalystTables(int index) {
        if (index < 0 || index >= maxPatternCount) {
            return;   // 越界（含越硬上限）一律不建
        }
        ensureCatalystTableLength(index);
        if (catalystItems.get(index) == null) {
            ItemStackTransfer transfer = new ItemStackTransfer(CATALYST_ITEM_SLOTS);
            // 字节码 lambda$new$1：`!(stack.getItem() instanceof ProcessingPatternItem)`
            // ⇒ 催化剂槽**拒收**处理样板（与样板仓的 AEUtils.PROCESS_FILTER 正好相反，别搞混）
            transfer.setFilter(stack -> !(stack.getItem() instanceof ProcessingPatternItem));
            if (!isRemote()) {
                // 现役机是在 onLoad 的 ServerLevel 分支里逐个挂这个回调的（客户端不挂，免得两边都重算）
                transfer.setOnContentsChanged(() -> onCatalystItemContentsChanged(index));
            }
            catalystItems.set(index, transfer);
        }
        if (catalystFluids.get(index) == null) {
            FluidStorage[] tanks = new FluidStorage[CATALYST_FLUID_TANKS];
            for (int i = 0; i < CATALYST_FLUID_TANKS; i++) {
                tanks[i] = new FluidStorage(CATALYST_TANK_CAPACITY);
            }
            FluidTransferList fluids = new FluidTransferList(tanks);
            if (!isRemote()) {
                // 现役机同样（onLoad 的 ServerLevel 分支）：逐个 FluidStorage.setOnContentsChanged
                for (FluidStorage storage : tanks) {
                    storage.setOnContentsChanged(() -> onCatalystFluidContentsChanged(index));
                }
            }
            catalystFluids.set(index, fluids);
        }
    }

    /** 取该槽的催化剂物品仓（没建过 = null）。**不会**顺手建仓 —— 热路径上可以放心调。 */
    private ItemStackTransfer catalystItemTransferAt(int index) {
        return index >= 0 && index < catalystItems.size() ? catalystItems.get(index) : null;
    }

    /** 取该槽的催化剂流体仓（没建过 = null）。 */
    private FluidTransferList catalystFluidTransferAt(int index) {
        return index >= 0 && index < catalystFluids.size() ? catalystFluids.get(index) : null;
    }

    /** 某槽的缓存阈值（缺省/越界 = {@value #DEFAULT_CACHE_RECIPE_COUNT}，与现役机 {@code Arrays.fill(...,1)} 同口径）。 */
    private int cacheRecipeCountAt(int slot) {
        if (slot < 0 || slot >= cacheRecipeCount.size()) {
            return DEFAULT_CACHE_RECIPE_COUNT;
        }
        Byte value = cacheRecipeCount.get(slot);
        return value == null ? DEFAULT_CACHE_RECIPE_COUNT : Math.max(1, value);
    }

    /** 写某槽的缓存阈值（越界自动补齐；夹到 [1, {@value #MAX_CACHE_RECIPE_COUNT}]，别让 byte 溢出成负数）。 */
    private void setCacheRecipeCount(int slot, int value) {
        if (slot < 0 || slot >= maxPatternCount) {
            return;
        }
        ensureCatalystTableLength(slot);
        cacheRecipeCount.set(slot, (byte) Math.max(1, Math.min(MAX_CACHE_RECIPE_COUNT, value)));
    }

    // ==================== P1-②B 专属催化剂槽：内容变化 → 重算该槽的催化剂表 ====================

    /**
     * 催化剂<b>物品仓</b>内容变了（服务端）—— 逐条照搬现役机
     * {@code MEPatternBufferPartMachine.reCalculateCatalystItemMap(int)}：
     * <ol>
     *   <li>{@code slot.getItemCatalystInventory().clear()}（先清空本槽的催化剂表）；</li>
     *   <li>清 {@code configuredVirtualItems} / {@code configuredVirtualFluids}；</li>
     *   <li>逐格读催化剂物品仓：空 → 跳过；{@code GTLItems.VIRTUAL_INGREDIENT} → 把载荷 AE 键塞进
     *       「已配置虚拟供给」两张表（基类 InternalSlot 的虚拟供给链路是齐的：{@code suppliesItem} /
     *       {@code appendVirtualItemInputs} / {@code clearVirtualSupply} 都在，
     *       用户放"虚拟原料"就是靠这一条生效）；其余 → {@code map.mergeLong(AEItemKey.of(stack), count, Long::sum)}；</li>
     *   <li>等价现役机 {@code removeSlotFromGTRecipeCache(index)}：该槽已缓存的配方作废（见
     *       {@link #invalidateSlotRecipeCache(int)}）；</li>
     *   <li>跑该槽的 {@code onContentsChanged}（惰性机里这个回调 = {@link #notifyMERecipeHandlers()}
     *       ⇒ 叫醒主机；与现役机末尾那句 {@code slot.getOnContentsChanged().run()} 逐字同义）。</li>
     * </ol>
     *
     * <p><b>红线说明</b>：这里会 {@code getInternalSlot(index)}（= 惰性实体化那一槽）。
     * 这是**有意为之**、也是必需的：只有"真的往这一槽的催化剂仓里放了东西"才会走到这里，
     * 而被配了催化剂的槽本来就必须有 InternalSlot 才能承载那张表。构造期仍然一个槽都不建。
     */
    private void reCalculateCatalystItemMap(int index) {
        if (isRemote()) {
            return;   // 现役机只在服务端的 onLoad 分支挂回调 ⇒ 重算也只该在服务端发生
        }
        ItemStackTransfer transfer = catalystItemTransferAt(index);
        if (transfer == null) {
            return;
        }
        try {
            InternalSlot raw = getInternalSlot(index);
            if (!(raw instanceof LazyPatternBufferInternalSlot slot)) {
                return;
            }
            Object2LongMap<AEItemKey> map = slot.getItemCatalystInventory();
            map.clear();
            slot.getConfiguredVirtualItems().clear();
            slot.getConfiguredVirtualFluids().clear();
            for (int i = 0; i < transfer.getSlots(); i++) {
                ItemStack stack = transfer.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                if (GTLItems.VIRTUAL_INGREDIENT.isIn(stack)) {
                    AEItemKey itemKey = VirtualIngredientBehavior.payloadItemKey(stack);
                    if (itemKey != null) {
                        slot.getConfiguredVirtualItems().add(itemKey);
                    }
                    AEFluidKey fluidKey = VirtualIngredientBehavior.payloadFluidKey(stack);
                    if (fluidKey != null) {
                        slot.getConfiguredVirtualFluids().add(fluidKey);
                    }
                } else {
                    map.mergeLong(AEItemKey.of(stack), stack.getCount(), Long::sum);
                }
            }
            invalidateSlotRecipeCache(index);
            Runnable callback = raw.getOnContentsChanged();
            if (callback != null) {
                callback.run();
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：催化剂物品重算失败（已忽略）pos={} 槽={} 原因={}",
                    getPos(), index + 1, failure.toString());
        }
    }

    /**
     * 催化剂<b>流体仓</b>内容变了（服务端）—— 逐条照搬现役机
     * {@code reCalculateCatalystFluidMap(int)}：清表 → 逐罐 {@code getFluidInTank} →
     * 非空则 {@code mergeLong(AEFluidKey.of(fluid), amount, Long::sum)} → 跑该槽的 onContentsChanged。
     * （现役机的流体这一路**不做** removeSlotFromGTRecipeCache，这里也照做不额外加。）
     */
    private void reCalculateCatalystFluidMap(int index) {
        if (isRemote()) {
            return;
        }
        FluidTransferList fluids = catalystFluidTransferAt(index);
        if (fluids == null) {
            return;
        }
        try {
            InternalSlot raw = getInternalSlot(index);
            if (!(raw instanceof LazyPatternBufferInternalSlot slot)) {
                return;
            }
            Object2LongMap<AEFluidKey> map = slot.getFluidCatalystInventory();
            map.clear();
            for (int i = 0; i < fluids.getTanks(); i++) {
                FluidStack stack = fluids.getFluidInTank(i);
                if (stack.isEmpty()) {
                    continue;
                }
                map.mergeLong(AEFluidKey.of(stack.getFluid()), stack.getAmount(), Long::sum);
            }
            Runnable callback = raw.getOnContentsChanged();
            if (callback != null) {
                callback.run();
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：催化剂流体重算失败（已忽略）pos={} 槽={} 原因={}",
                    getPos(), index + 1, failure.toString());
        }
    }

    /** 催化剂物品仓的内容变化回调 —— <b>可能落在 tick / 网络路径上</b>：吞掉一切异常，绝不往外抛。 */
    private void onCatalystItemContentsChanged(int index) {
        try {
            reCalculateCatalystItemMap(index);
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：催化剂物品回调异常（已忽略）pos={} 槽={} 原因={}",
                    getPos(), index + 1, failure.toString());
        }
    }

    /** 催化剂流体仓的内容变化回调（同上，吞异常）。 */
    private void onCatalystFluidContentsChanged(int index) {
        try {
            reCalculateCatalystFluidMap(index);
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：催化剂流体回调异常（已忽略）pos={} 槽={} 原因={}",
                    getPos(), index + 1, failure.toString());
        }
    }

    /**
     * 催化剂 / 缓存阈值变了 ⇒ 该槽已缓存的配方结论作废。
     *
     * <p>等价现役机 {@code removeSlotFromGTRecipeCache(slot)} 里最要紧的三步：① 清掉本 trait 里
     * 该槽的配方集合（主机侧 {@code restoreMachineCache} 读的就是它）；② 通过
     * {@code notifyPatternChange} 把主机 {@code recipes2SlotsMap} 里该槽的旧条目删掉
     * （那个 IntConsumer 是 gtlcore 的 {@code MEPatternRecipeHandlePart.restoreMachineCache} 注册进来的）；
     * ③ {@code notifyProxySlotRemoved}（现役机那一句的末尾，基类实现 = 遍历镜像通知）。
     * 全部包 try/catch：叫不动主机也绝不许影响催化剂本身。
     */
    private void invalidateSlotRecipeCache(int slot) {
        try {
            LazyMEPatternTrait trait = (LazyMEPatternTrait) getMETrait();
            if (trait != null) {
                trait.clearSlotCache(slot);
                trait.notifyPatternChange(slot);
            }
        } catch (Throwable ignored) {
            // 清缓存/通知失败只是"少清一次"
        }
        try {
            notifyProxySlotRemoved(slot);
        } catch (Throwable ignored) {
            // 没挂镜像时是无害 no-op
        }
    }

    // ==================== P1-②B 专属催化剂槽：催化剂表的存档 ====================

    /** 该催化剂物品仓里有没有东西（有才值得写档）。 */
    private static boolean hasAnyCatalystItem(ItemStackTransfer transfer) {
        if (transfer == null) {
            return false;
        }
        for (int i = 0; i < transfer.getSlots(); i++) {
            if (!transfer.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 该催化剂流体仓里有没有东西。 */
    private static boolean hasAnyCatalystFluid(FluidTransferList fluids) {
        if (fluids == null) {
            return false;
        }
        for (int i = 0; i < fluids.getTanks(); i++) {
            if (!fluids.getFluidInTank(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * ★ P1-②B：逐槽催化剂仓写档（<b>稀疏</b>：只写"真有内容 / 阈值非默认"的槽，键 {@code s<槽下标>}）。
     *
     * <p><b>为什么必须自己写</b>：现役机那三个字段都挂着 LDLib {@code @Persisted}
     * （{@code javap -v} 的 RuntimeVisibleAnnotations 实证），托管字段机制会连数组一起存/读；
     * 而惰性机的三张表是<b>长度随容量变化的 List</b>（交给托管字段机制风险太大），
     * 所以照本文件既有的「逐槽表稀疏写」风格自己来（与 {@code gtlmodular$lazy_slot_recipe_type_sparse}
     * 同思路：<b>按槽下标对齐的内容，容量外的陈旧下表一律丢</b>）。
     *
     * <p>键名刻意用 {@code gtlmodular$lazy_catalyst_slots}（**不与现役机的 @Persisted 数组格式互通**：
     * 两者的槽坐标系不同 —— 惰性机是动态容量，现役机是固定 128/640）。
     * 只在 {@code forDrop == false}（世界存档）时写；掉落时不写（与逐槽表同一条防复制规则）。
     */
    private void saveCatalystSlots(CompoundTag tag) {
        try {
            int slots = Math.max(catalystItems.size(), Math.max(catalystFluids.size(), cacheRecipeCount.size()));
            CompoundTag root = new CompoundTag();
            for (int i = 0; i < slots; i++) {
                CompoundTag entry = new CompoundTag();
                boolean any = false;
                ItemStackTransfer items = catalystItemTransferAt(i);
                if (hasAnyCatalystItem(items)) {
                    entry.put(NBT_CATALYST_ITEMS, items.serializeNBT());
                    any = true;
                }
                FluidTransferList fluids = catalystFluidTransferAt(i);
                if (hasAnyCatalystFluid(fluids)) {
                    entry.put(NBT_CATALYST_FLUIDS, fluids.serializeNBT());
                    any = true;
                }
                int count = cacheRecipeCountAt(i);
                if (count != DEFAULT_CACHE_RECIPE_COUNT) {
                    entry.putByte(NBT_CATALYST_COUNT, (byte) count);
                    any = true;
                }
                if (any) {
                    root.put("s" + i, entry);
                }
            }
            if (!root.isEmpty()) {
                tag.put(NBT_LAZY_CATALYST_SLOTS, root);
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：催化剂表写档失败（已忽略）pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    /**
     * ★ P1-②B：逐槽催化剂仓读档。读回后<b>重算一次该槽的催化剂表</b>
     * （催化剂表是"按仓内容算出来的"，重算让两者一致；重算顺带作废该槽的配方缓存，读档时无害）。
     * 容量外的陈旧下表直接丢（与逐槽配方类型表同口径）；任何异常都只吞掉，绝不打断读档本身。
     */
    private void loadCatalystSlots(CompoundTag tag) {
        try {
            CompoundTag root = tag.getCompound(NBT_LAZY_CATALYST_SLOTS);
            if (root.isEmpty()) {
                return;
            }
            int slots = getPatternSlotCount();
            for (String key : root.getAllKeys()) {
                if (!key.startsWith("s")) {
                    continue;
                }
                int index;
                try {
                    index = Integer.parseInt(key.substring(1));
                } catch (RuntimeException ignored) {
                    continue;   // 坏键名直接跳过
                }
                if (index < 0 || index >= slots) {
                    continue;   // 容量外的陈旧下表直接丢
                }
                CompoundTag entry = root.getCompound(key);
                if (entry.contains(NBT_CATALYST_COUNT)) {
                    setCacheRecipeCount(index, entry.getByte(NBT_CATALYST_COUNT));
                }
                boolean hasItems = entry.contains(NBT_CATALYST_ITEMS, Tag.TAG_COMPOUND);
                boolean hasFluids = entry.contains(NBT_CATALYST_FLUIDS, Tag.TAG_COMPOUND);
                if (!hasItems && !hasFluids) {
                    continue;
                }
                ensureCatalystTables(index);
                if (hasItems) {
                    ItemStackTransfer items = catalystItemTransferAt(index);
                    if (items != null) {
                        items.deserializeNBT(entry.getCompound(NBT_CATALYST_ITEMS));
                    }
                }
                if (hasFluids) {
                    FluidTransferList fluids = catalystFluidTransferAt(index);
                    if (fluids != null) {
                        fluids.deserializeNBT(entry.getCompound(NBT_CATALYST_FLUIDS));
                    }
                }
                reCalculateCatalystItemMap(index);
                reCalculateCatalystFluidMap(index);
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：催化剂表读档失败（已忽略）pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    // ==================== P1-②B 专属催化剂槽：视图代理（控件结构永不变） ====================

    /**
     * ★ <b>催化剂物品槽的「视图代理」</b>：界面上那 9 个物品槽控件<b>只建一次</b>，
     * 切页只换 {@link #target}（= 当前指向的样板槽下标，0 起），控件结构一个字节都不动。
     *
     * <p><b>为什么必须这么干</b>（队长实测的病根）：
     * LDLib 的 {@code SlotWidget(IItemTransfer, index, ...)} 在构造期就建出一个<b>真·原版</b>
     * {@code net.minecraft.world.inventory.Slot}（javap 实证：
     * {@code SlotWidget$WidgetSlotItemTransfer extends Slot}，构造器把
     * {@code this.itemHandler = transfer; this.index = index}，底下的 {@code Container} 是一个
     * 0 格的 {@code SimpleContainer}）。槽里的内容由原版
     * {@code ClientboundContainerSetSlotPacket} / {@code initializeContents} <b>按菜单槽位表的下标</b>
     * 下发，客户端落到 {@code Slot.set(ItemStack)} → {@code IItemTransfer.setStackInSlot(index, stack)}
     * （javap 实证：{@code m_5852_ → setStackInSlot}），渲染读 {@code Slot.getItem()} →
     * {@code IItemTransfer.getStackInSlot(index)}（同前者的 {@code m_7993_}）。
     *
     * <p>⇒ 菜单槽位表是<b>按下标定位</b>的：老实现切页时走
     * {@code MEPatternCatalystUIManager.toggleFor(index)} → {@code show()} →
     * {@code clearAllWidgets()} 再重建「9 物品 + 9 罐」控件，而客户端（每帧 {@code updateScreen}）
     * 与服务端（每 tick {@code detectAndSendChanges}）<b>并不在同一时刻重建</b>
     * ⇒ 两侧槽位下标错开 ⇒ 实测病象「点慢显示到下一槽、点快了偶尔凑对」。
     * 现在「控件永不变 + 代理只换指向」⇒ 这个错位从根上消失。
     */
    final class CatalystItemView implements IItemTransfer {

        /**
         * 当前指向的样板槽下标（0 起）。客户端也存着，但客户端的读写一律走
         * {@link #clientCache}，<b>绝不</b>看这个值（客户端那份真仓是空壳，见下）。
         */
        private int target;

        /**
         * ★ <b>客户端本地缓存</b>（长度固定 = {@link #CATALYST_ITEM_SLOTS}，下标 = 槽控件下标）。
         * 原版槽位同步会把服务端的内容「按槽写下标」写进来（{@code WidgetSlotItemTransfer.set}
         * → {@link #setStackInSlot}），渲染再从这里读回去 —— 这就是客户端那 9 个格子的<b>唯一</b>
         * 内容来源（惰性机的逐槽催化剂表没有 {@code @DescSynced}，客户端那份是空壳，动不得）。
         */
        private final ItemStack[] clientCache = new ItemStack[CATALYST_ITEM_SLOTS];

        CatalystItemView() {
            for (int i = 0; i < clientCache.length; i++) {
                clientCache[i] = ItemStack.EMPTY;
            }
        }

        /** 切页：只换指向（O(1)，不建任何仓、不动任何控件）。 */
        void setTarget(int index) {
            this.target = index;
        }

        /**
         * 服务端真仓（<b>读也要懒建</b>：只有"真被查看的那一槽"会被建出来）。
         * 客户端一律 {@code null} —— 客户端根本不碰本侧真仓（它是空壳）。
         */
        private ItemStackTransfer serverTransfer() {
            if (isRemote()) {
                return null;
            }
            ensureCatalystTables(target);
            return catalystItemTransferAt(target);
        }

        @Override
        public int getSlots() {
            // ★ 固定 9（= CATALYST_ITEM_SLOTS）：**绝不能**按"本侧容量"算 ——
            //   客户端 patternInventory 容量恒为 INITIAL_CAPACITY（它没有 @DescSynced），会算错。
            return CATALYST_ITEM_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (isRemote()) {
                return slot >= 0 && slot < clientCache.length ? clientCache[slot] : ItemStack.EMPTY;
            }
            ItemStackTransfer transfer = serverTransfer();
            return transfer != null ? transfer.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            ItemStack value = stack == null ? ItemStack.EMPTY : stack;
            if (isRemote()) {
                // 客户端：只写本地缓存（真正的写入由原版容器把点击发到服务端执行）。
                // 写缓存是为了让"刚收到的包"与"这一帧的渲染/预测"一致 —— 渲染读的就是这里。
                if (slot >= 0 && slot < clientCache.length) {
                    clientCache[slot] = value;
                }
                return;
            }
            if (slot < 0 || slot >= CATALYST_ITEM_SLOTS) {
                return;
            }
            ItemStackTransfer transfer = serverTransfer();
            if (transfer == null) {
                return;
            }
            // ItemStackTransfer.setStackInSlot 自身**不**触发 onContentsChanged（javap 实证：
            // 只有 `validateSlotIndex` + `stacks.set`），所以这里必须补一次「内容变了」的回调
            // （= 重算该槽催化剂表 + 作废该槽配方缓存）。
            transfer.setStackInSlot(slot, value);
            onCatalystItemContentsChanged(target);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate, boolean notify) {
            if (isRemote()) {
                // 客户端：只在本地缓存上做一次**纯模拟**插入，好让原版槽位预测的口径与服务端一致。
                // 原版的 `WidgetSlotItemTransfer.m_5866_`（= Slot.getMaxStackSize(ItemStack)）会这样问：
                // 存下当前内容 → 写 EMPTY → insertItem(simulate=true) → 再把原内容写回。
                // 口径不对会让"往催化剂槽里放东西"慢一 tick 才见效（服务端随后仍会纠正过来）。
                return simulatedInsert(slot, stack, simulate);
            }
            if (slot < 0 || slot >= CATALYST_ITEM_SLOTS) {
                return stack;
            }
            ItemStackTransfer transfer = serverTransfer();
            if (transfer == null) {
                return stack;
            }
            ItemStack remainder = transfer.insertItem(slot, stack, simulate, notify);
            if (!simulate) {
                onCatalystItemContentsChanged(target);
            }
            return remainder;
        }

        /**
         * 客户端专用：在 {@link #clientCache} 上按服务端 {@code ItemStackTransfer.insertItem} 的口径
         * 模拟插入（同样的过滤器、同样的 64 上限、同样的"同类同 NBT"判断）。
         * <b>只碰本地缓存</b>，绝不碰本侧真仓。
         */
        private ItemStack simulatedInsert(int slot, ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (slot < 0 || slot >= clientCache.length || !isItemValid(slot, stack)) {
                return stack;
            }
            ItemStack cached = clientCache[slot];
            // 真仓的 getStackLimit = min(getSlotLimit(slot)=64, stack.getMaxStackSize())
            int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
            if (!cached.isEmpty()) {
                if (!ItemTransferHelper.canItemStacksStack(stack, cached)) {
                    return stack;
                }
                limit -= cached.getCount();
            }
            if (limit <= 0) {
                return stack;
            }
            int moved = Math.min(limit, stack.getCount());
            if (!simulate) {
                ItemStack grown = cached.isEmpty() ? stack.copy() : cached.copy();
                grown.setCount(cached.isEmpty() ? moved : cached.getCount() + moved);
                clientCache[slot] = grown;
            }
            if (moved >= stack.getCount()) {
                return ItemStack.EMPTY;
            }
            ItemStack remainder = stack.copy();
            remainder.setCount(stack.getCount() - moved);
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate, boolean notify) {
            if (isRemote()) {
                // ★ 客户端**必须**照缓存如实回答：原版槽位预测会问
                //   `WidgetSlotItemTransfer.m_8010_(mayPickup)` → `extractItem(i, 1, true)`。
                //   缓存就是原版同步下来的服务端内容，所以这里的回答与服务端一致
                //   （返回空 = 客户端预测「取不出来」，会让左键取件慢一 tick 才见效）。
                if (slot < 0 || slot >= clientCache.length || amount <= 0) {
                    return ItemStack.EMPTY;
                }
                ItemStack cached = clientCache[slot];
                if (cached.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                ItemStack taken = cached.copy();
                taken.setCount(Math.min(amount, cached.getCount()));
                return taken;
            }
            if (slot < 0 || slot >= CATALYST_ITEM_SLOTS) {
                return ItemStack.EMPTY;
            }
            ItemStackTransfer transfer = serverTransfer();
            if (transfer == null) {
                return ItemStack.EMPTY;
            }
            ItemStack extracted = transfer.extractItem(slot, amount, simulate, notify);
            if (!simulate) {
                onCatalystItemContentsChanged(target);
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            // ★ 与 ItemStackTransfer.getSlotLimit(int) 逐字一致（字节码 `bipush 64; ireturn`）。
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // ★ 与服务端真仓同一把过滤器（字节码 lambda$new$1：
            //   `!(stack.getItem() instanceof ProcessingPatternItem)` ⇒ 催化剂槽拒收处理样板）。
            return stack == null || !(stack.getItem() instanceof ProcessingPatternItem);
        }

        @Override
        public void onContentsChanged() {
            // 原版容器的 setChanged()（点击收尾）会走到这里，两侧都可能调到。
            // 服务端：补一次内容变化回调；客户端：什么都不做（重算只在服务端发生）。
            if (!isRemote()) {
                onCatalystItemContentsChanged(target);
            }
        }

        @Override
        public Object createSnapshot() {
            ItemStack[] copy = new ItemStack[CATALYST_ITEM_SLOTS];
            for (int i = 0; i < copy.length; i++) {
                copy[i] = getStackInSlot(i).copy();
            }
            return copy;
        }

        @Override
        public void restoreFromSnapshot(Object snapshot) {
            if (!(snapshot instanceof ItemStack[] stacks) || stacks.length != CATALYST_ITEM_SLOTS) {
                return;
            }
            for (int i = 0; i < stacks.length; i++) {
                setStackInSlot(i, stacks[i] == null ? ItemStack.EMPTY : stacks[i].copy());
            }
        }
    }

    /**
     * ★ <b>催化剂流体罐的「视图代理」</b>（<b>一罐一个</b>）。切页同样只换 {@link #target}。
     *
     * <p><b>为什么是"一罐一个代理"而不是"一个 9 罐代理"</b>（javap 实证）：
     * {@code MEPatternCatalystUIManager.show} 建罐控件时，罐数取的是
     * <b>{@code FluidTransferList.transfers} 的元素个数</b>
     * （{@code iload 5 : fluidTankTransfers[i].transfers.length} → {@code createFluidContainer(list, 该长度)}
     * → 逐元素 {@code createTankWidget(transfers[j], x, y)} → {@code new TankWidget(transfer, 0, x, y, true, true)}），
     * 也就是说 <b>数组元素 = 罐控件</b>。gtlcore 具体机的 {@code catalystFluids[i]} 正是
     * {@code new FluidTransferList(9 个 FluidStorage)}（字节码：
     * {@code Stream.generate(lambda$new$3).limit(9L).toList()}，每个元素都是单罐的
     * {@code FluidStorage(16 * FluidHelper.getBucket())}）。
     * ⇒ 我们的代理也必须"一罐一个"，否则界面上只会剩 1 个罐控件（其余 8 罐看不见）。
     *
     * <p><b>客户端渲染的流体从哪来</b>：<b>不是</b>从这里。{@code TankWidget} 的绘制走的是它自己的
     * {@code lastFluidInTank}/{@code lastTankCapacity}（javap 实证：{@code drawInBackground} 里
     * 只有 {@code isClientSideWidget} 才回读 {@code fluidTank.getFluidInTank}），
     * 那份数据由服务端 {@code detectAndSendChanges → writeUpdateInfo} 与客户端
     * {@code readUpdateInfo} 同步（{@code readUpdateInfo} 只写 {@code lastFluidInTank}，
     * <b>不</b>回写 {@code fluidTank}）。所以本代理在客户端只是"占位 + 缓存"，
     * 罐里的显示天然跟着原版/LDLib 的同步走。
     */
    final class CatalystFluidView implements IFluidTransfer {

        /** 本代理在"一槽 9 罐"里的罐下标（0..{@value #CATALYST_FLUID_TANKS}-1，构造后不变）。 */
        private final int tankIndex;

        /** 当前指向的样板槽下标（0 起）。客户端不看它。 */
        private int target;

        /**
         * 客户端本地缓存（本代理恒定只有 1 罐）。
         * 注意 {@code FluidStack.empty()} 是 LDLib 的<b>共享单例</b>：字段只做整体替换、
         * 读出去的一律是 {@code copy()}，绝不原地改它。
         */
        private FluidStack clientFluid = FluidStack.empty();

        CatalystFluidView(int tankIndex) {
            this.tankIndex = tankIndex;
        }

        /** 切页：只换指向（O(1)）。 */
        void setTarget(int index) {
            this.target = index;
        }

        /** 服务端该槽的 9 罐真仓（读也要懒建）；客户端一律 {@code null}。 */
        private FluidTransferList serverFluids() {
            if (isRemote()) {
                return null;
            }
            ensureCatalystTables(target);
            return catalystFluidTransferAt(target);
        }

        /** 本代理恒定只有 1 罐（罐下标构造期固定）⇒ 入参 tank 忽略，一律映射到 {@link #tankIndex}。 */
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (isRemote()) {
                return clientFluid.copy();
            }
            FluidTransferList fluids = serverFluids();
            return fluids != null ? fluids.getFluidInTank(tankIndex) : FluidStack.empty();
        }

        @Override
        public void setFluidInTank(int tank, FluidStack stack) {
            FluidStack value = stack == null ? FluidStack.empty() : stack.copy();
            if (isRemote()) {
                clientFluid = value;
                return;
            }
            FluidTransferList fluids = serverFluids();
            if (fluids == null) {
                return;
            }
            fluids.setFluidInTank(tankIndex, value);
            onCatalystFluidContentsChanged(target);
        }

        @Override
        public long getTankCapacity(int tank) {
            // ★ 容量是固定常量（16 桶 = 与真仓 `new FluidStorage(16 * FluidHelper.getBucket())` 同款），
            //   两侧一致，不牵扯"本侧容量"问题，直接给常量。
            return CATALYST_TANK_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            // 真仓是 `new FluidStorage(capacity)`（没给 validator）⇒ 一律放行。
            return true;
        }

        @Override
        public long fill(int tank, FluidStack resource, boolean simulate, boolean notify) {
            if (isRemote()) {
                // 客户端不预测充液（真写入由 TankWidget 把动作发到服务端执行）。
                return 0L;
            }
            FluidTransferList fluids = serverFluids();
            if (fluids == null) {
                return 0L;
            }
            long filled = fluids.fill(tankIndex, resource, simulate, notify);
            if (!simulate) {
                onCatalystFluidContentsChanged(target);
            }
            return filled;
        }

        @Override
        public boolean supportsFill(int tank) {
            // FluidStorage.supportsFill(int) ⇒ true。
            return true;
        }

        @Override
        public FluidStack drain(int tank, FluidStack resource, boolean simulate, boolean notify) {
            if (isRemote()) {
                return FluidStack.empty();
            }
            FluidTransferList fluids = serverFluids();
            if (fluids == null) {
                return FluidStack.empty();
            }
            FluidStack drained = fluids.drain(tankIndex, resource, simulate, notify);
            if (!simulate) {
                onCatalystFluidContentsChanged(target);
            }
            return drained;
        }

        @Override
        public boolean supportsDrain(int tank) {
            // FluidStorage.supportsDrain(int) ⇒ true。
            return true;
        }

        @Override
        public void onContentsChanged() {
            if (!isRemote()) {
                onCatalystFluidContentsChanged(target);
            }
        }

        @Override
        public Object createSnapshot() {
            return new FluidStack[]{ getFluidInTank(0).copy() };
        }

        @Override
        public void restoreFromSnapshot(Object snapshot) {
            if (!(snapshot instanceof FluidStack[] stacks) || stacks.length != 1) {
                return;
            }
            setFluidInTank(0, stacks[0] == null ? FluidStack.empty() : stacks[0].copy());
        }
    }

    /**
     * 界面上改了「当前查看的那一槽」的缓存阈值：把界面写进缓冲的值<b>抄回正规表</b>，
     * 并作废该槽已缓存的配方结论。
     *
     * <p><b>为什么需要这个"抄回"</b>：{@code MEPatternCatalystUIManager} 是把新阈值直接写进
     * <b>我们交给它的那个 {@code byte[]}</b> 里、然后才回调这个 IntConsumer
     * （javap 实证 {@code lambda$show$1}：{@code if (cacheRecipeCount[i] != v) { cacheRecipeCount[i] = v;
     * onCacheCountChange.accept(i); }}，回调拿到的是<b>下标</b> i、不是值）。现役机把
     * {@code machine.cacheRecipeCount} 这个真数组直接给它，所以不用抄；我们的阈值表是长度可变的
     * List，交给界面的只是一个长度 1 的缓冲 —— 这个回调就是那条"抄回"的路。
     * （现役机在这里传的是 {@code machine::removeSlotFromGTRecipeCache}，语义也一并照搬。）
     *
     * <p>注意：这个阈值控件随后会被 {@code layoutSideBySide()} → {@code removeCacheCountWidget()}
     * 从界面里摘掉（与现役机同款处理），所以正常路径下这个回调不会被触发；留着它是为了
     * "万一将来摘除失败，也不至于变成一个纯装饰的输入框"。
     */
    private void onCacheCountChangedFromUi(int slot, byte[] buffer) {
        try {
            if (buffer != null && buffer.length > 0 && slot >= 0) {
                // 界面交给我们的是长度 1 的缓冲（只显示"当前查看的那一槽"）⇒ 值永远在 [0]。
                setCacheRecipeCount(slot, buffer[0]);
            }
            invalidateSlotRecipeCache(slot);
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：缓存阈值同步失败（已忽略）pos={} 槽={} 原因={}",
                    getPos(), slot + 1, failure.toString());
        }
    }

    // ---------- 专属催化剂槽页字段访问器（★ 照搬现役机的同名访问器） ----------

    /** 催化剂页当前查看的样板槽位（1 起）。 */
    public int getCatalystViewSlot() {
        return catalystViewSlot;
    }

    /**
     * 设催化剂页当前查看的样板槽位。
     *
     * <p>★ <b>客户端不钳位</b>：客户端的 {@code patternInventory} 容量<b>恒为</b>
     * {@link #INITIAL_CAPACITY}（它只有 {@code @Persisted}、没有 {@code @DescSynced}，
     * 构造器在两侧都跑 ⇒ 服务端会长到 48/128/640，客户端永远 32）。
     * 拿本侧容量去钳位会把"服务端合法的大槽号"错误地夹小 ⇒ 两侧看到的槽号不一致。
     * 所以客户端只把原值交给服务端（服务端负责钳位 + 真正生效，再把权威值同步回来）。
     */
    public void setCatalystViewSlot(int slot) {
        if (isRemote()) {
            this.catalystViewSlot = slot;   // 不钳位、不写任何本侧状态（等权威值同步回来）
            return;
        }
        this.catalystViewSlot = Math.max(1, Math.min(getPatternSlotCount(), slot));
    }

    /** 催化剂页是否展开。 */
    public boolean isCatalystPageOpen() {
        return catalystPageOpen;
    }

    public void setCatalystPageOpen(boolean open) {
        this.catalystPageOpen = open;
    }

    // ==================== 界面 ====================

    /**
     * ★ P1-①：<b>分页样板区</b>（16 列 × 最多 8 行 = 128 格/页），复用 gtlcore 的公开类
     * {@link PaginationUIManager}（现役/超级猪咪用的就是它）。
     *
     * <p>为什么用<b>7 参</b>构造器：它的第 3 个参数就是<b>样板格数</b>（javap 实证：
     * {@code this.maxPatternCount = 参数3}，而 8 参版本是 {@code patternsPerRow*rowsPerPage*maxPages}）。
     * 我们的容量是<b>动态</b>的（初始 32、按需 +16、上限 {@link #maxPatternCount}）——
     * 分页器内部建格子时会 {@code min(页尾, maxPatternCount)} 且是**按 maxPatternCount 建**
     * （不是按仓的真实槽数），所以必须把「当前容量」原样给它，否则会为不存在的槽建控件
     * （LDLib 的 {@code setStackInSlot} 会 validateSlotIndex ⇒ 越界点击直接抛异常）。
     * 因此分页器**每次开界面时按当前容量新建**。
     *
     * <p>★ <b>0.3.14 勘误（这句老话是错的，也是队长那个 bug 的成因之一）</b>：
     * 原文写"容量在界面打开期间增长的话，重开界面即可看到新格子"——只有当两侧都拿得到真实容量时才成立。
     * 客户端那份 {@code patternInventory} 只带 {@code @Persisted}、<b>没有</b> {@code @DescSynced}
     * ⇒ 客户端容量恒为 32 ⇒ <b>重开多少次都还是 32 格</b>。
     * 现在由 {@link #patternWidgetCapacity()} 统一给出权威格数（服务端真容量 / 客户端同步容量），
     * 并在客户端这次构建里先把本侧仓补齐 ⇒ "粘贴后第一次打开就完整"。
     *
     * <p>用它的额外好处（都是"栏位看起来对"的原因，javap 实证它在内部这样建格子）：
     * 样板格是 {@code AEPatternViewExtendSlotWidget}，底图是
     * {@code setBackground(GuiTextures.SLOT, GuiTextures.PATTERN_OVERLAY)}（**空白样板的幽灵底图**），
     * 右键可查看样板内容，槽被改动时会回调我们的 {@code onPatternChange}。
     */
    @Override
    public Widget createUIWidget() {
        // ★ 0.3.14 修复（队长实测：「粘贴样板以后，样板确实都放进去了，但是总成界面能看到的还是那么多，
        //   需要多次打开关闭才能看到完整的」）：
        //   这里原来用的是**本侧** getCapacity()。服务端那侧它是真容量（会随粘贴长到 48/64/128/640），
        //   可客户端那份 patternInventory 只有 @Persisted、没有 @DescSynced ⇒ 容量恒为 32
        //   ⇒ 客户端永远只建 32 个格子（多出来的样板明明已经同步到客户端、却没有任何控件去画它们；
        //   而且"重开界面"还是按同一个 32 建 ⇒ 反复开关也不可能变好）。
        //   现在改成两侧都用**权威口径** patternWidgetCapacity()：
        //   服务端 = 真容量；客户端 = 服务端同步过来的容量（并在这一次构建里先把本侧仓补齐）
        //   ⇒ 格子数 == 原版菜单槽位数 == 服务端容量 ⇒ 一打开就是完整的，且容量/页数一致。
        int capacity = patternWidgetCapacity();
        // ★ 行数**封顶 8 行**（= 一页）：容量 ≤128 时一页装下；将来若注册 640 格的档位，
        //   这里仍是 8 行/页 ⇒ 分页器自己算出 ceil(640/(8*16)) = 5 页（与现役机同口径）。
        int rows = Math.max(1, Math.min(ROWS_PER_PAGE,
                (capacity + PATTERNS_PER_ROW - 1) / PATTERNS_PER_ROW));
        int uiWidth = Math.max(PATTERNS_PER_ROW * 18 + 16, 106);
        // 高度 = 标题行(16) + 格子区(rows*18) + 分页控件行(约 20)：分页器的控件画在 16+rows*18+4
        int uiHeight = rows * 18 + 36;
        // 行数按当前容量给（分页器会用它算格子高度与分页控件位置；maxPages 由它自己算 = ceil(容量/(行数*16)) = 1）
        PaginationUIManager pager = new PaginationUIManager(PATTERNS_PER_ROW, rows, capacity, uiWidth, uiHeight,
                this::onSlotChangedFromGui, patternInventory);
        WidgetGroup group = new WidgetGroup(0, 0, uiWidth, uiHeight);
        group.addWidget(new LabelWidget(8, 2, () -> Component.translatable(isOnline()
                ? "gtceu.gui.me_network.online"
                : "gtceu.gui.me_network.offline").getString()));
        group.addWidget(new AETextInputButtonWidget(uiWidth - 78, 2, 70, 10)
                .setText(getCustomName())
                .setOnConfirm(this::onPlayerConfirmedCustomName)
                .setButtonTooltips(Component.translatable("gui.gtceu.rename.desc")));
        // 中键点样板槽 → 打开该槽的专属催化剂槽（★ P1-②B：分页器本来就给我们一个中键回调，
        // 现役机也是用同一个入口 `createPaginationUI(slot -> {...})`；下面那层透明覆盖层是右键的补充入口）
        group.addWidget(pager.createPaginationUI(slot -> {
            setCatalystViewSlot(slot + 1);
            setCatalystPageOpen(true);
        }));
        // ★ P1-②B：样板槽上的透明右键/中键层（照搬现役机 SlotRightClickOverlay）。
        //   必须加在最后（LDLib 从子控件列表末尾往前派点击，末尾 = 最上层）；
        //   它只接管右键/中键，左键一律透传 ⇒ 不影响分页样板区原有的左键/拖拽行为。
        group.addWidget(new SlotRightClickOverlay(this, group, uiWidth, uiHeight));
        return group;
    }

    /**
     * 界面里某个样板槽被改动（分页器回调）。
     * <p><b>只在服务端动作</b>：客户端的改动由 LDLib 的 SlotWidget 自己发回服务端；
     * 若在客户端也跑 {@link #onPatternInventoryChanged()}，会去改本地容量
     * （容量是服务端权威的同步字段）⇒ 客户端/服务端容量不一致。
     */
    private void onSlotChangedFromGui(int slot) {
        if (isRemote()) {
            return;
        }
        onPatternInventoryChanged();
    }

    // ==================== P2-④ 配方模式页 / P1-②C 倍率页 + 神锻页 ====================

    /**
     * ★ 照搬现役机 {@code attachConfigurators}（第 2057-2075 行）的<b>页序</b>：
     * <b>模式页 → 倍率页 → 神锻页 → 催化剂页</b>。
     *
     * <p>逐条：
     * <ol>
     *   <li>{@code super} 先挂（gtlcore 基类自己的页）；</li>
     *   <li><b>只有「可识别配方类型 &gt; 1」时</b>才挂 {@link RecipeModeConfigurator} ——
     *       只有一种模式时这一页没有意义（自动识别也不需要它）；</li>
     *   <li>{@link ScaleConfigurator}（倍率 / 除率 / 重新应用）；</li>
     *   <li>{@link FoaConfigurator}（神锻开关 + 倍率）；</li>
     *   <li>最后挂 {@link CatalystAccessConfigurator}（P1-②B 已有的那页）。</li>
     * </ol>
     *
     * <p>★ <b>催化剂页的 tab 绑定为什么插入两页后仍然正确</b>：它<b>不写死下标</b>，
     * 而是在"挂它<b>之前</b>"抓一次 {@code panel.getTabs().size()} 当作它自己的下标
     * （{@code attachConfigurators} 是追加式，新页的下标必然 = 挂之前的 tab 数）。
     * 现役机用的是 {@code before + pages.size() - 1} 的等价算法（它先攒 List 再一次性挂），
     * 这里是同一个等式的另一种写法 ⇒ <b>前面挂了几页都不影响</b>，绝不会出现
     * 「点开催化剂页却切到别的页」。另外每一页各自 try/catch：某一页挂失败也不会连累催化剂页的下标。
     */
    @Override
    public void attachConfigurators(ConfiguratorPanel panel) {
        super.attachConfigurators(panel);
        try {
            if (getAvailableRecipeTypes().size() > 1) {
                panel.attachConfigurators(new RecipeModeConfigurator(this));
            }
        } catch (Throwable failure) {
            // 挂页失败绝不影响机器本身（顶多是玩家看不到这一页）
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：配方模式页挂载失败 pos={} 原因={}",
                    getPos(), failure.toString());
        }
        // ★ P1-②C：倍率页（模式页之后、神锻页之前 —— 页序与现役机逐字一致）
        try {
            panel.attachConfigurators(new ScaleConfigurator(this));
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：样板倍率页挂载失败 pos={} 原因={}",
                    getPos(), failure.toString());
        }
        // ★ P1-②C：神锻页（倍率页之后、催化剂页之前）
        try {
            panel.attachConfigurators(new FoaConfigurator(this));
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：神锻样板模式页挂载失败 pos={} 原因={}",
                    getPos(), failure.toString());
        }
        // ★ P1-②B：「专属催化剂槽」页 —— 照搬现役机 {@code attachConfigurators} 的挂法：
        //   最后挂这一页，并把「面板 + 本页的 tab」交给它自己 ——
        //   右键样板槽时要顺手把配置面板切到这一页（见 CatalystAccessConfigurator.openSlotFromPattern）。
        //   下标按**挂之前的 tab 数**取（不写死）⇒ 上面插了几页都不会绑错。
        try {
            CatalystAccessConfigurator catalystPage = new CatalystAccessConfigurator(this);
            this.activeCatalystPage = catalystPage;
            int before = panel.getTabs().size();
            panel.attachConfigurators(catalystPage);
            List<ConfiguratorPanel.Tab> tabs = panel.getTabs();
            catalystPage.bindPanel(panel, tabs.size() > before ? tabs.get(before) : null);
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：专属催化剂槽页挂载失败 pos={} 原因={}",
                    getPos(), failure.toString());
        }
        // ★ P1-②E：「样板电路写入」页 —— **逐条照搬** gtlcore 具体类 attachConfigurators 的最后一段。
        //   页面本体直接用 gtlcore 自己的公开类 org.gtlcore.gtlcore.api.gui.PatternCircuitConfigurator：
        //   它内部建的就是「样板电路写入」标题 + 「目标电路编号」IntInputWidget + 「跳过已有电路」SwitchWidget
        //   + 「写入」/「清空电路」两个按钮，**10 个语言键全部是 gtlcore 已存在的
        //   gui.gtlcore.pattern_buffer_* / tooltip.gtlcore.pattern_buffer_***（见 _javap_p1e2_api.txt【6】）
        //   ⇒ 惰性机一个字都不用自己画，也一个字都不用加进我们的 lang JSON。
        //   六个构造参数与 gtlcore 传的六个**同序同义**（取值 / 写值 / 取值 / 写值 / 写入 / 清空）：
        //     #21 Supplier<Integer>            ↔ this::getEmbeddedCircuitConfig
        //     #22 Consumer<Integer>            ↔ this::setEmbeddedCircuitConfig
        //     #23 Supplier<Boolean>            ↔ this::isSkipExistingCircuitPatterns
        //     #24 BiConsumer<ClickData,Boolean>↔ (data, value) -> setSkipExistingCircuitPatterns(...)
        //     #25 Consumer<ClickData>          ↔ this::applyConfiguredCircuit
        //     #26 Consumer<ClickData>          ↔ this::removeAllPatternCircuits
        //   【页序】放在**现有页之后**（模式页 / 倍率页 / 神锻页 / 催化剂页之后）：
        //   催化剂页的 tab 下标是"挂它**之前**"抓一次 getTabs().size() 得来的（见上面 bindPanel 那句），
        //   追加在它后面绝不会改变它的下标 ⇒ 上面几页的绑定一个字都不用动。
        try {
            panel.attachConfigurators(new PatternCircuitConfigurator(
                    this::getEmbeddedCircuitConfig,
                    this::setEmbeddedCircuitConfig,
                    this::isSkipExistingCircuitPatterns,
                    (data, value) -> setSkipExistingCircuitPatterns(Boolean.TRUE.equals(value)),
                    this::applyConfiguredCircuit,
                    this::removeAllPatternCircuits));
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：样板电路写入页挂载失败 pos={} 原因={}",
                    getPos(), failure.toString());
        }
        // ★ P2：「归档（复制 / 剪切）」页 —— 设计稿第三节要求的界面入口。
        //   【页序】放在**最后一页之后**（模式页 / 倍率页 / 神锻页 / 催化剂页 / 电路页之后）：
        //   这一步只是"再追加一个 tab"，既不改任何已有页的下标，也不碰催化剂页那套
        //   "挂它之前抓 getTabs().size()" 的绑定（见上面 bindPanel 那句）⇒ 现有页一个字都不用动。
        try {
            panel.attachConfigurators(new ArchiveConfigurator(this));
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪：归档页挂载失败 pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    // ==================== P1-②E 样板电路写入 / 电路嵌入 ====================
    //
    // 把 gtlcore **具体类** MEPatternBufferPartMachine 的这套功能搬进惰性机（它的父类是同一族的**抽象基类**，
    // 所以这一整套在惰性机上"天生没有"）。逐条照搬的五个成员 + 字节码偏移见工作区根 `_javap_p1e2_api.txt`：
    //   · {@code private int embeddedCircuitConfig}          —— ACC_PRIVATE + @DescSynced + @Persisted，构造器初值 1；
    //   · {@code private boolean skipExistingCircuitPatterns} —— 同上，**初值 true**（界面默认就勾着"跳过已有电路"）；
    //   · {@code private void setEmbeddedCircuitConfig(int)}  —— {@code Math.max(1, Math.min(32, v))}；
    //   · {@code private void applyConfiguredCircuit(ClickData)} —— 远端放过 → {@code embed(config, !skipExisting)}；
    //   · {@code private void removeAllPatternCircuits(ClickData)} —— 远端放过 → 逐槽 removeCircuitFromPattern；
    //   · {@code private void embedCircuitToPatterns(int, boolean)} —— 逐槽 createPatternWithCircuit。
    // 界面入口见上面 attachConfigurators 的那一段（直接 new gtlcore 的 PatternCircuitConfigurator）。
    //
    // ── 「处理样板」的判定口径（与 gtlcore 完全一致，依据 _javap_p1e2_api.txt【7】）──
    //   真正的判定在 gtlcore 的 MEBufferPatternHelper 里：`rebuildPatternCircuit` 先
    //   `PatternDetailsHelper.decodePattern(stack, level)`，**不是 AEProcessingPattern 就返回 ItemStack.EMPTY**；
    //   调用方对 EMPTY 一律跳过 ⇒ 合成样板 / 空白样板 / 解不出来的样板都不会被碰。
    //   惰性机因此**不自己写"是不是处理样板"的判断**，只照搬"EMPTY 就跳过"这一句，
    //   把"能不能改"整个交给同一个 helper ⇒ 口径不可能与 gtlcore 走偏。
    //   （顺带：helper 内部对"写入"会**替换**旧电路——先把电路从输入里摘掉、再把新电路插到输入第 0 位；
    //     `overwrite=false` 且已有电路时它原样返回原样板，这正是"跳过已有电路"的落点。）
    //
    // ── 惰性化差异（本段全部差异，逐条对着现役链写清）──
    //   ① **遍历范围**：现役链是 `internalPatternInventory.size()`（固定 128/640，整仓都过一遍解码），
    //      惰性机是 `getCapacity()`（= 已用最高槽 + 预留，通常远小于硬上限）⇒ 只扫"真的可能存在样板"的那一段，
    //      空槽先判 `isEmpty()` 再解码 ⇒ 遍历成本 ∝ 现有容量而不是标称槽数；
    //   ② **落盘通道**：现役链是 `internalPatternInventory.setItemDirect(i, newStack)`（在那一句里逐槽触发
    //      一次 onPatternChange），惰性机是 `patternInventory.setStackInSlot(i, newStack)`，**先把所有目标样板
    //      算完、再逐槽写**，最后**只调一次** {@link #onPatternInventoryChanged()} —— 收尾后果与 gtlcore 的
    //      逐槽 onPatternChange 一一对应（清槽缓存 / 清虚拟供给 / 退还残留料并推回网络 / 摘掉主机侧该槽的旧配方
    //      条目 / 复位该槽"记住的配方类型"），但不会在 N 个槽上各跑一次"重建整张样板表"级别的活
    //      （惰性机的样板→槽映射是**脏标记 + 下次读取时重建一次**，见 ensurePatternSlotMap）；
    //   ③ **不实体化、不扩容**：全程只用 `patternInventory.getStackInSlot(i)`，**绝不**去 `getInternalSlot(i)`
    //      建新槽、也不调 growCapacityFor ⇒ 容量与"只增不减表"一个字都没动
    //      （真有样板的那几个槽本来就已经被基类遍历实体化了，收尾里的 onSlotPatternChanged 只碰这些槽）；
    //   ④ **AE 重取样板表**：不在这里自己捅 AE，只置 `needPatternSync`，交给 lazyTick 里每 tick 合并一次的那条
    //      既有路径（refreshPatternsNow）—— 与 0.3.5 对批量粘贴的防 O(n²) 设计同一条，不绕开。
    //
    // ── 改样板 = 改玩家存档里的真东西 ⇒ 本段纪律 ──
    //   **先算后写、失败不动**：阶段①只读地把每个槽的"目标样板"全部算出来（解码/改写抛异常、helper 返回 EMPTY、
    //   结果与原来相同的，一律不进清单 —— 此刻仓里一个字节都没动）；阶段②才逐槽落盘，每一槽单独 try/catch，
    //   写不进去的那一槽保持原样。于是任何一步失败都不会留下"半张样板"：写进去的每个值都是 gtlcore helper
    //   产出的**完整样板物品**；最坏情况是"只写了一半的槽"，不是"写出坏样板"。整段再包一层 try/catch，绝不外抛。

    /** 目标电路编号的合法区间。★ 照搬 gtlcore 两处 {@code Math.max(1, Math.min(32, ...))}（【0】【1】【4】）。 */
    public static final int MIN_EMBEDDED_CIRCUIT = 1;
    public static final int MAX_EMBEDDED_CIRCUIT = 32;

    /**
     * 「目标电路编号」。★ 逐条照搬 gtlcore {@code embeddedCircuitConfig}：
     * <b>@DescSynced</b>（客户端要显示当前值）+ <b>@Persisted</b>（走 LDLib 托管字段机制落盘世界存档，
     * 键名 = 字段名 ⇒ 与 gtlcore 同键同格式），初值 1（构造器字节码 {@code iconst_1 / putfield}）。
     *
     * <p>@Persisted 默认 {@code drop=false} ⇒ 掉落物里不写这两个设置（与 gtlcore 行为一致；
     * 本机其它设置类字段是自研键、掉落也写，那批是另一回事，见类 javadoc 的 forDrop 规则）。
     */
    @DescSynced
    @Persisted
    private int embeddedCircuitConfig = 1;

    /**
     * 「跳过已有电路」。★ 逐条照搬 gtlcore {@code skipExistingCircuitPatterns}（同为 @DescSynced + @Persisted），
     * 初值 <b>true</b>（构造器字节码 {@code iconst_1 / putfield}）—— 即默认"不覆盖已经有电路的样板"。
     */
    @DescSynced
    @Persisted
    private boolean skipExistingCircuitPatterns = true;

    /** 「电路写入 / 清空」诊断日志限流间隔（tick）：同一个按钮被按住也最多 0.5 秒一条。 */
    private static final int CIRCUIT_EMBED_LOG_INTERVAL_TICKS = 10;

    /** 上面那条日志最多列几个槽位（防刷屏）。 */
    private static final int CIRCUIT_EMBED_LOG_SLOTS = 8;

    /**
     * 上一次打印「电路写入 / 清空」的时间（gameTime）。
     * 哨兵用 {@code -1000} 而<b>不是</b> {@code Long.MIN_VALUE}（后者会让 {@code now - 哨兵} 溢出成负
     * ⇒ 限流恒真 ⇒ 日志永远静默；本仓库自检第 45 项专治这个坑）。
     */
    private long lastCircuitEmbedLogTick = -1000;

    public int getEmbeddedCircuitConfig() {
        return embeddedCircuitConfig;
    }

    /** ★ 逐条照搬 gtlcore {@code setEmbeddedCircuitConfig}：{@code Math.max(1, Math.min(32, value))}。 */
    public void setEmbeddedCircuitConfig(int value) {
        this.embeddedCircuitConfig = Math.max(MIN_EMBEDDED_CIRCUIT, Math.min(MAX_EMBEDDED_CIRCUIT, value));
    }

    public boolean isSkipExistingCircuitPatterns() {
        return skipExistingCircuitPatterns;
    }

    /** ★ 照搬 gtlcore {@code lambda$attachConfigurators$20}（SwitchWidget 的回调就是直接写这个字段）。 */
    public void setSkipExistingCircuitPatterns(boolean value) {
        this.skipExistingCircuitPatterns = value;
    }

    /**
     * 界面「写入」按钮。★ 逐条照搬 gtlcore {@code applyConfiguredCircuit}（字节码偏移 0-28）：
     * {@code if (data.isRemote) return;} 然后
     * {@code embedCircuitToPatterns(embeddedCircuitConfig, !skipExistingCircuitPatterns)}。
     *
     * <p>★ 最后那个布尔是 <b>overwrite（覆盖已有）</b>：勾了"跳过已有电路"就传 false。
     * 方向极易写反（gtlcore 那边是靠 {@code ifne / iconst_0} 那两字节定的，见 _javap_p1e2_api.txt【2】）。
     */
    private void applyConfiguredCircuit(ClickData data) {
        if (data == null || data.isRemote) {
            return;
        }
        embedCircuitToPatterns(embeddedCircuitConfig, !skipExistingCircuitPatterns);
    }

    /** 界面「清空电路」按钮。★ 逐条照搬 gtlcore {@code removeAllPatternCircuits}（远端放过 + 逐槽移除）。 */
    private void removeAllPatternCircuits(ClickData data) {
        if (data == null || data.isRemote) {
            return;
        }
        rewritePatternCircuits(false, MIN_EMBEDDED_CIRCUIT, true);
    }

    /**
     * 把电路写进所有可修改的处理样板。★ 逐条照搬 gtlcore {@code embedCircuitToPatterns(int, boolean)}。
     *
     * @param circuit   目标电路编号（本方法内<b>再</b>夹一次 [1,32]，与 gtlcore 一致）
     * @param overwrite true = 连已经有电路的样板一起改（= 界面上的"跳过已有电路"<b>没</b>勾）
     */
    private void embedCircuitToPatterns(int circuit, boolean overwrite) {
        rewritePatternCircuits(true, circuit, overwrite);
    }

    /**
     * 「电路写入 / 清空」的公共引擎 —— 两件事只差"调哪个 helper + 日志措辞"，其余（遍历、判定、落盘、收尾）
     * 字字相同，所以合成一条路径，避免两份 200 行的复制粘贴各修各的。
     *
     * @param embed     true = 写入（{@code createPatternWithCircuit}）；false = 清空（{@code removeCircuitFromPattern}）
     * @param circuit   目标电路编号（只有 embed 用；内部夹到 [1,32]）
     * @param overwrite 只有 embed+已有电路时才起作用（false ⇒ 原样跳过）
     */
    private void rewritePatternCircuits(boolean embed, int circuit, boolean overwrite) {
        if (isRemote()) {
            return;
        }
        try {
            Level level = getLevel();
            if (level == null) {
                return;
            }
            MEBufferPatternHelper helper;
            try {
                helper = getRealPatternHelper();
            } catch (Throwable ignored) {
                helper = null;
            }
            if (helper == null) {
                GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪样板电路{}：拿不到样板助手，本次不动任何样板 pos={}",
                        embed ? "写入" : "清空", getPos());
                return;
            }
            int config = Math.max(MIN_EMBEDDED_CIRCUIT, Math.min(MAX_EMBEDDED_CIRCUIT, circuit));

            // ---------- 阶段①：**只读**，先把要写的目标样板全部算出来（此刻仓里一个字节都没动）----------
            List<Integer> targetSlots = new ArrayList<>();
            List<ItemStack> targetStacks = new ArrayList<>();
            int calcFailed = 0;
            int capacity = getCapacity();
            for (int i = 0; i < capacity; i++) {
                ItemStack current;
                try {
                    current = patternInventory.getStackInSlot(i);
                } catch (Throwable ignored) {
                    continue;   // 这一槽读不出来 ⇒ 不动它
                }
                if (current == null || current.isEmpty()) {
                    continue;   // 空槽：连解码都不做（惰性化差异①：只扫真有样板的那一段）
                }
                ItemStack next;
                try {
                    next = embed
                            ? helper.createPatternWithCircuit(current, config, overwrite, level)
                            : helper.removeCircuitFromPattern(current, level);
                } catch (Throwable failure) {
                    calcFailed++;   // 这一槽解/改失败 ⇒ 记一笔、**不动它**，其它槽继续
                    continue;
                }
                // ★★「处理样板」判定口径与 gtlcore 逐字一致：不是 AEProcessingPattern ⇒ helper 返回 EMPTY ⇒ 跳过 ★★
                if (next == null || next.isEmpty()) {
                    continue;
                }
                // ★ helper 判定"该跳过"时会把原样板原样还回来（同一对象）⇒ 内容没变就别写（照搬 gtlcore 的 isSame 判定）
                if (ItemStack.matches(current, next)) {
                    continue;
                }
                targetSlots.add(i);
                targetStacks.add(next);
            }

            // ---------- 阶段②：逐槽落盘（每槽单独 try/catch；写不进去的那一槽保持原样）----------
            int applied = 0;
            int writeFailed = 0;
            for (int k = 0; k < targetSlots.size(); k++) {
                try {
                    patternInventory.setStackInSlot(targetSlots.get(k), targetStacks.get(k));
                    applied++;
                } catch (Throwable ignored) {
                    writeFailed++;
                }
            }

            if (applied > 0) {
                // ★★ 唯一一次收尾：走 0.3.5 既有那条通道 ★★
                //   · patternSlotMapDirty = true        → 样板→槽映射下次读取时重建（不在这里重建，防 O(n²)）
                //   · needPatternSync  = true           → AE 重取样板表，由 lazyTick **每 tick 合并一次** 消费
                //   · diffSlotPatterns()                → 逐槽认出"被改动的槽"并做与 gtlcore onPatternChange
                //                                          一一对应的收尾（清槽缓存/清虚拟供给/退残留料并推网/
                //                                          摘主机旧配方条目/复位该槽配方类型缓存）
                //   · invalidateRewriteCacheQuietly()   → 倍率/神锻那份"给 AE 看的视图改写"缓存作废
                try {
                    onPatternInventoryChanged();
                } catch (Throwable failure) {
                    GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪样板电路{}：写入后的收尾失败"
                                    + "（样板本身已经写好，缓存最迟由下一 tick 的签名轮询兜住）pos={} 原因={}",
                            embed ? "写入" : "清空", getPos(), failure.toString());
                }
            }

            // ---------- 留痕（限流 0.5 秒一条；成功/失败都记，方便"点了没反应"时有据可查）----------
            try {
                long now = level.getGameTime();
                if (now - lastCircuitEmbedLogTick >= CIRCUIT_EMBED_LOG_INTERVAL_TICKS) {
                    lastCircuitEmbedLogTick = now;
                    StringBuilder slotText = new StringBuilder();
                    for (int k = 0; k < targetSlots.size() && k < CIRCUIT_EMBED_LOG_SLOTS; k++) {
                        if (k > 0) {
                            slotText.append(',');
                        }
                        slotText.append(targetSlots.get(k) + 1);
                    }
                    if (targetSlots.size() > CIRCUIT_EMBED_LOG_SLOTS) {
                        slotText.append("...");
                    }
                    GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪样板电路{}: 目标电路={} 覆盖已有={} 改动槽数={}"
                                    + " 槽位(1起)=[{}] 算不动={} 写入失败={}"
                                    + " —— 已作废样板缓存，AE 样板表由 lazyTick 合并重取 pos={}",
                            embed ? "写入" : "清空",
                            embed ? String.valueOf(config) : "-",
                            embed ? String.valueOf(overwrite) : "-",
                            applied, slotText.toString(), calcFailed, writeFailed, getPos());
                }
            } catch (Throwable ignored) {
                // 诊断日志绝不影响改样板
            }
        } catch (Throwable failure) {
            // 整段兜底：改样板是"动玩家存档里的真东西"，任何意外都不许外抛出去掀翻 tick / 控件回调
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪样板电路{}异常（本次操作已中止，仓内样板保持操作前状态）pos={} 原因={}",
                    embed ? "写入" : "清空", getPos(), failure.toString());
        }
    }

    // ==================== P2 样板仓「归档（复制 / 剪切）」====================
    //
    // 为什么必须自己做（取证见 _javap_p2_api.txt【1】）：
    //   gtlcore 的复制 / 剪切工具物品（MEPatternBufferCopyBehavior / MEPatternBufferCutBehavior）在字节码里
    //   先 `instanceof MetaMachineBlockEntity → checkcast`、再 **`instanceof MEPatternBufferPartMachine`**
    //   → checkcast → `invokevirtual copyFromTag/copyToTag/pasteFromTag/cutToTag` —— 判定落在**具体类**上；
    //   而我们（惰性猪咪）继承的是**基类** MEPatternBufferPartMachineBase ⇒ 那两个 behavior 直接跳过我们。
    //   同理，gtlcore 那四个方法**只在具体类上**（基类一个都没有，基类只有 save/loadCustomPersistedData）。
    //
    // 所以本类自己实现**与 gtlcore 同签名**的四个方法（同签名 = 将来若要接 gtlcore 的通用调用链也不必改签名），
    // 语义对照 gtlcore 的实现（字节码逐条见 _javap_p2_api.txt【2】），但**要搬全我们的东西**：
    //   样板本体 + 逐槽配方类型缓存 + 手动指定 + 逐槽催化剂（含阈值）。
    //
    // ── 与 gtlcore 的四点**有意差异**（都是惰性化/坐标系带来的，逐条对照【2】）──
    //   ① **按槽下标对齐**（gtlcore 是"流水账"）：gtlcore 的 copyToTag/cutToTag 只写
    //      `patterns` 数组（元素是 {pattern, cacheCount}，无槽号），pasteFromTag/copyFromTag 再
    //      "从 0 开始找第一个空槽、顺序填"；惰性机的容量是**动态**的、槽位坐标系与固定 128/640 不同，
    //      顺序填会让"逐槽元数据"和"样板"两个坐标系错开 ⇒ 我们每个条目都带 `s` = 槽下标，
    //      粘贴时**按槽下标落位**，元数据与样板永远对齐。这就是设计稿里"按槽下标对齐"的落点。
    //   ② **容量保护 = 整体拒绝**（gtlcore 是"装不下就整批放弃"）：gtlcore pasteFromTag 先数空槽
    //      `size() - 已用 < ListTag.size()` 就 return false（连名字也不写）；我们多了一层**一次扩容**：
    //      惰性机的容量本来就该按需长（这是它存在的理由）⇒ 需求 = max(已用+预留, 最高归档槽+1+预留)，
    //      夹到 maxPatternCount；夹不住才拒绝。两种口径的**共同点**是"绝不搬一半"。
    //   ③ **收尾只调一次** {@link #onPatternInventoryChanged()}（gtlcore 是逐槽 setItemDirect →
    //      逐槽 onPatternChange）：惰性机的收尾里有"重建样板→槽映射 + 通知 AE"这类 O(容量) 的活，
    //      逐槽调会退化成 O(n²)（0.3.5 的防 O(n²) 设计，红线）⇒ 全部写完只调一次。
    //   ④ **设置类与名字默认不搬**（gtlcore 无条件搬名字）：设计稿裁决 —— 设置属于"这台机器"而不是
    //      "这批样板"；面板上给两个勾选框（{@link #isArchiveIncludeSettings()} /
    //      {@link #isArchiveIncludeName()}），档案里记下"这份档案带了没有"，粘贴时两边都同意才写。
    //
    //   ── ⑤ 补（0.3.13，队长报"粘贴会把已有样板顶掉"的数据安全 bug）：**落位模式**──
    //      ① 里"按槽下标对齐"从此**不是默认**：默认改为 **追加**（{@link #isArchiveAlignSlots()} = false）
    //      —— 从本机**当前已用最高槽的下一格**（{@link #highestUsedSlot()} + 1）开始往后延伸，
    //      目标机为空时结果与"对齐"逐槽一致（26 台空机迁移不受影响）；
    //      ② "按槽下标对齐"降级为一个**显式开关**（克隆/迁移到另一台同布局机器时用）：该模式下
    //      目标槽已被占用就**跳过**（不写、不覆盖），并在结果提示里报告跳过了几个；
    //      ③ 两种模式都**绝不覆盖已有样板**；其余承诺（装不下整体拒绝且不碰容量、先算后写、
    //      单槽失败保持原样、成功后只调一次 {@link #onPatternInventoryChanged()}）一条不改。
    //      落盘引擎在 {@link LazyPatternArchiveCodec#apply(LazyPatternAssemblyPartMachine,
    //      LazyPatternArchiveCodec.Payload, boolean, boolean, LazyPatternArchiveCodec.PlaceMode)}。
    //
    // ── 纪律（动玩家存档真数据）──
    //   一律「先算后写、失败不动、整体拒绝优于半成品」；tick / 控件回调路径 try/catch(Throwable) 不外抛。
    //   引擎在 {@link LazyPatternArchiveCodec}（可单独审阅），这里只留"薄访问器 + 与 gtlcore 同签名的四个方法"。

    /** 「归档」页是否包含设置类（倍率 / 除率 / 神锻 / 输出模式）。默认 <b>false</b>（设计稿裁决）。 */
    @DescSynced
    private boolean archiveIncludeSettings = false;

    /** 「归档」页是否包含机器名字。默认 <b>false</b>。 */
    @DescSynced
    private boolean archiveIncludeName = false;

    public boolean isArchiveIncludeSettings() {
        return archiveIncludeSettings;
    }

    public void setArchiveIncludeSettings(boolean value) {
        this.archiveIncludeSettings = value;
    }

    public boolean isArchiveIncludeName() {
        return archiveIncludeName;
    }

    public void setArchiveIncludeName(boolean value) {
        this.archiveIncludeName = value;
    }

    /**
     * ★ <b>粘贴落位模式开关</b>（0.3.13 队长报的数据安全 bug）：
     * {@code false}（默认）= <b>追加</b> —— 档案内容从本机<b>当前已用最高槽的下一格</b>开始往后延伸；
     * {@code true} = <b>按原槽对齐</b> —— 按档案里的槽下标落位，目标槽已被占用则<b>跳过</b>（不写、不覆盖）。
     *
     * <p>为什么默认必须是追加：旧口径是"按档案里的槽下标落位"，粘进一台<b>已经有样板</b>的机器时
     * 会把这些槽<b>直接顶掉</b>（队长逐字：「他会把那个位置的样板直接给顶掉，换成粘贴的样板，
     * 而不是往后延伸」）⇒ 动玩家存档真样板，这一条不能有第二种解释。
     *
     * <p>与 {@link #isArchiveIncludeSettings()} / {@link #isArchiveIncludeName()} 同口径：
     * 只带 LDLib 的 {@code @DescSynced}（客户端要显示按钮文字）、<b>不落盘</b>
     * （换机器/重开档回到默认"追加"最安全）。
     */
    @DescSynced
    private boolean archiveAlignSlots = false;

    /** 归档粘贴是否用"按原槽对齐"（{@code false} = 追加，默认）。 */
    public boolean isArchiveAlignSlots() {
        return archiveAlignSlots;
    }

    public void setArchiveAlignSlots(boolean value) {
        this.archiveAlignSlots = value;
    }

    /**
     * 粘贴落位模式（门面/面板/物品右键共用同一个判据）：只读、不动任何数据。
     */
    public LazyPatternArchiveCodec.PlaceMode archivePlaceMode() {
        return archiveAlignSlots
                ? LazyPatternArchiveCodec.PlaceMode.ALIGN
                : LazyPatternArchiveCodec.PlaceMode.APPEND;
    }

    /**
     * <b>批量改动样板仓之后的唯一收尾入口</b>（归档粘贴 / 剪切清源都只调它一次）。
     *
     * <p>它就是已有的 {@link #onPatternInventoryChanged()}，这里只是给"包外调用方"
     * （{@link LazyPatternArchiveApi}）一个明确的、**不会顺手多做别的事**的落点：
     * 作废「样板 → 槽」映射 + 逐槽收尾（清缓存 / 退残留料 / 通知主机删旧配方 / 复位该槽配方类型）
     * + 置 {@code needPatternSync} 交给既有 lazyTick 合并通道让 AE 重取样板表。
     * <b>绝不新增第二条刷新路径</b>（红线）。
     */
    public void afterArchiveBulkChange() {
        onPatternInventoryChanged();
    }

    /** 样板仓本体（只给本包的归档编解码器用；返回值就是那个**不换对象**的原地增长的仓）。 */
    ItemStackTransfer getPatternInventory() {
        return this.patternInventory;
    }

    /** 源机所在维度的 id（诊断用；拿不到返回空串）。 */
    private String archiveDimensionId() {
        try {
            Level level = getLevel();
            if (level == null) {
                return "";
            }
            return level.dimension().location().toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * ★ <b>取出全仓</b>（复制语义，不动本机）—— 归档编解码器唯一的数据来源。
     *
     * <p>逐条对照设计稿第二节的"要搬运的东西"：
     * <ul>
     *   <li>样板本体：只读 {@code patternInventory.getStackInSlot(i)}（**惰性**：绝不 getInternalSlot，
     *       未实体化的槽一个都不建）；</li>
     *   <li>逐槽配方类型缓存：{@link #recipeTypeCacheAt(int)}，{@value #TYPE_NOT_TRIED} 不写（稀疏）；</li>
     *   <li>手动指定：{@link #manualSlotTypes} 的 id（存 id 不存下标，与存档同口径）；</li>
     *   <li>逐槽催化剂（含阈值）：只搬**仓里的内容**，用 {@link #ensureCatalystTables(int)} 的
     *       "按需建"语义 —— 这里改用 {@link #catalystItemTransferAt(int)} / {@link #catalystFluidTransferAt(int)}
     *       等只读访问器（**不建**新仓、不实体化空仓，保持惰性）。</li>
     * </ul>
     *
     * @param includeSettings 档案是否包含设置类
     * @param includeName     档案是否包含名字
     */
    public LazyPatternArchiveCodec.Payload buildArchivePayload(boolean includeSettings, boolean includeName) {
        LazyPatternArchiveCodec.Payload payload = new LazyPatternArchiveCodec.Payload();
        payload.capacity = getCapacity();
        payload.includeSettings = includeSettings;
        payload.includeName = includeName;
        payload.name = getCustomName() == null ? "" : getCustomName();
        payload.dimension = archiveDimensionId();
        payload.pos = getPos() == null ? 0L : getPos().asLong();
        if (includeSettings) {
            CompoundTag settings = new CompoundTag();
            settings.putInt(NBT_PATTERN_SCALE, patternScale);
            settings.putInt(NBT_PATTERN_DIV_SCALE, patternDivScale);
            settings.putBoolean(NBT_FOA_MODE, foaMode);
            settings.putInt(NBT_FOA_MULTIPLIER, foaMultiplier);
            settings.putBoolean(NBT_OUTPUT_MODE, isOutputMode);
            payload.settings = settings;
        }
        int highest = -1;
        int capacity = getCapacity();
        for (int i = 0; i < capacity; i++) {
            ItemStack stack;
            try {
                stack = patternInventory.getStackInSlot(i);
            } catch (Throwable ignored) {
                continue;   // 这一槽读不出来 ⇒ 跳过它（绝不因此丢掉别的槽）
            }
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            Integer recipeType = null;
            int cached = recipeTypeCacheAt(i);
            if (cached != TYPE_NOT_TRIED) {
                recipeType = cached;
            }
            ResourceLocation manual = manualSlotTypes.get(i);
            CompoundTag catalyst = buildArchivedCatalyst(i);
            payload.entries.add(new LazyPatternArchiveCodec.Entry(i, stack.copy(), recipeType, manual, catalyst));
            if (i > highest) {
                highest = i;
            }
        }
        payload.slotCount = highest + 1;
        payload.patternCount = payload.entries.size();
        return payload;
    }

    /**
     * 打包一个槽的催化剂内容（含阈值）。
     * <p><b>只搬内容不搬"已实体化的空仓"</b>（设计稿第二节）：没有内容的槽返回 {@code null}，
     * 空催化剂仓<b>不会</b>被写进档案，也就不会在粘贴时被实体化。
     */
    private CompoundTag buildArchivedCatalyst(int slot) {
        try {
            CompoundTag entry = new CompoundTag();
            ItemStackTransfer items = catalystItemTransferAt(slot);
            if (hasAnyCatalystItem(items)) {
                entry.put(NBT_CATALYST_ITEMS, items.serializeNBT());
            }
            FluidTransferList fluids = catalystFluidTransferAt(slot);
            if (hasAnyCatalystFluid(fluids)) {
                entry.put(NBT_CATALYST_FLUIDS, fluids.serializeNBT());
            }
            int count = cacheRecipeCountAt(slot);
            if (count != DEFAULT_CACHE_RECIPE_COUNT) {
                entry.putByte(NBT_CATALYST_COUNT, (byte) count);
            }
            return entry.isEmpty() ? null : entry;
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：第 {} 槽催化剂打包失败（该槽只搬样板）pos={} 原因={}",
                    slot + 1, getPos(), failure.toString());
            return null;
        }
    }

    /**
     * ★ <b>清空本仓</b>（剪切语义的第二半，只有调用方确认目标机写成功之后才许调）。
     *
     * <p>清空的内容与 {@link #buildArchivePayload} 搬走的内容**一一对应**，一个都不多清：
     * 样板 + 逐槽配方类型缓存 + 手动指定 + 逐槽催化剂（含阈值）。
     * 容量、internalInventory 的实体化状态、其它表一律不动（容量只增不减是既有红线）。
     *
     * <p>收尾**不在这里**：由调用方在清空完成后调一次 {@link #onPatternInventoryChanged()}
     * （既有的"作废缓存 + AE 重取样板表"通道），避免新增第二条刷新路径。
     */
    void clearArchiveContent() {
        try {
            int capacity = getCapacity();
            for (int i = 0; i < capacity; i++) {
                try {
                    patternInventory.setStackInSlot(i, ItemStack.EMPTY);
                } catch (Throwable ignored) {
                    // 单槽清不掉不影响别的槽
                }
                try {
                    setRecipeTypeCache(i, TYPE_NOT_TRIED);
                } catch (Throwable ignored) {
                    // 同上
                }
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：清空样板失败（已尽力而为）pos={} 原因={}",
                    getPos(), failure.toString());
        }
        try {
            manualSlotTypes.clear();
            refreshManualAssignDisplay();
        } catch (Throwable ignored) {
            // 手动指定表清不掉只是"多留了一张表"
        }
        try {
            // 逐槽催化剂：清内容 + 阈值复位，**不动容量表**（与逐槽配方类型表"只增不减"同口径）。
            int tables = Math.max(catalystItems.size(),
                    Math.max(catalystFluids.size(), cacheRecipeCount.size()));
            for (int i = 0; i < tables; i++) {
                ItemStackTransfer items = catalystItemTransferAt(i);
                if (items != null) {
                    for (int s = 0; s < items.getSlots(); s++) {
                        items.setStackInSlot(s, ItemStack.EMPTY);
                    }
                    reCalculateCatalystItemMap(i);
                }
                FluidTransferList fluids = catalystFluidTransferAt(i);
                if (fluids != null) {
                    for (int t = 0; t < fluids.getTanks(); t++) {
                        fluids.setFluidInTank(t, FluidStack.empty());
                    }
                    reCalculateCatalystFluidMap(i);
                }
                if (i < cacheRecipeCount.size()) {
                    cacheRecipeCount.set(i, (byte) DEFAULT_CACHE_RECIPE_COUNT);
                }
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：清空催化剂失败（已忽略）pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    /** 写一槽的配方类型缓存（归档粘贴用；读侧已经在编解码器里夹过区间）。★ 只走既有的 setRecipeTypeCache。 */
    void writeArchivedRecipeType(int slot, int recipeType) {
        try {
            ensureRecipeTypeSlotCapacity();
            setRecipeTypeCache(slot, recipeType);
        } catch (Throwable ignored) {
            // 类型缓存写不进去只是"少记一条"，样板的正确性不受影响
        }
    }

    /** 写一槽的手动指定（归档粘贴用）。★ 与存档同口径：存 id，不存下标。 */
    void writeArchivedManualType(int slot, ResourceLocation typeId) {
        try {
            if (slot < 0 || slot >= maxPatternCount || typeId == null) {
                return;
            }
            manualSlotTypes.put(slot, typeId);
            refreshManualAssignDisplay();
        } catch (Throwable ignored) {
            // 同上
        }
    }

    /**
     * 写一槽的催化剂内容（含阈值）。★ 这里**允许**实体化该槽的催化剂仓 —— 理由与
     * {@link #reCalculateCatalystItemMap(int)} 的红线说明同一条：只有"档案里真的有这一槽的催化剂内容"
     * 才会走到这里，而那种槽本来就必须有仓才能承载内容。空内容（{@code null}）根本不会进来。
     *
     * @return 是否真的写了东西
     */
    boolean writeArchivedCatalyst(int slot, CompoundTag catalyst) {
        if (catalyst == null || catalyst.isEmpty() || slot < 0 || slot >= maxPatternCount) {
            return false;
        }
        boolean wrote = false;
        try {
            boolean hasItems = catalyst.contains(NBT_CATALYST_ITEMS, Tag.TAG_COMPOUND);
            boolean hasFluids = catalyst.contains(NBT_CATALYST_FLUIDS, Tag.TAG_COMPOUND);
            if (hasItems || hasFluids) {
                ensureCatalystTables(slot);
            }
            if (hasItems) {
                ItemStackTransfer items = catalystItemTransferAt(slot);
                if (items != null) {
                    items.deserializeNBT(catalyst.getCompound(NBT_CATALYST_ITEMS));
                    // ★ 照搬 gtlcore pasteFromTag 的顺序：解完**必须**补一次 onContentsChanged
                    //   —— LDLib 的 `ItemStackTransfer.deserializeNBT` 走的是内部列表、**不触发**
                    //   内容变化回调（本机在 saveCatalystSlots 的注释里记过同一条）；
                    //   少这一句，粘贴进来的催化剂仓在"下一次真有内容变化"之前不会重算催化剂表。
                    try {
                        items.onContentsChanged();
                    } catch (Throwable ignored) {
                        // 回调失败也不影响仓内容本身（reCalculate 下面还会再算一次）
                    }
                    reCalculateCatalystItemMap(slot);
                    wrote = true;
                }
            }
            if (hasFluids) {
                FluidTransferList fluids = catalystFluidTransferAt(slot);
                if (fluids != null) {
                    fluids.deserializeNBT(catalyst.getCompound(NBT_CATALYST_FLUIDS));
                    try {
                        fluids.onContentsChanged();
                    } catch (Throwable ignored) {
                        // 同上（FluidTransferList.onContentsChanged 会逐个 FluidStorage 转达）
                    }
                    reCalculateCatalystFluidMap(slot);
                    wrote = true;
                }
            }
            if (catalyst.contains(NBT_CATALYST_COUNT, Tag.TAG_BYTE)) {
                setCacheRecipeCount(slot, catalyst.getByte(NBT_CATALYST_COUNT));
                invalidateSlotRecipeCache(slot);
                wrote = true;
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：第 {} 槽催化剂写入失败（该槽催化剂保持原样）pos={} 原因={}",
                    slot + 1, getPos(), failure.toString());
        }
        return wrote;
    }

    /**
     * 写归档里的设置类（倍率 / 除率 / 神锻开关 / 神锻倍率 / 输出模式）。
     *
     * <p>与 {@link #loadCustomPersistedData} 的读侧同口径：<b>直接用字段赋值</b>（不走 setter），
     * 因为 setter 会每改一个就 {@code invalidateRewriteCache()} 一次（5 次白通知），
     * 而这里在末尾统一调一次静默作废 —— AE 通知由编解码器收尾时那一次
     * {@link #onPatternInventoryChanged()} 的 {@code needPatternSync} 合并通道负责。
     * 读侧一律夹到合法区间（档案可能来自改过配置 / 手改 NBT 的档）。
     */
    void writeArchivedSettings(CompoundTag settings) {
        if (settings == null || settings.isEmpty()) {
            return;
        }
        try {
            if (settings.contains(NBT_PATTERN_SCALE, Tag.TAG_INT)) {
                patternScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, settings.getInt(NBT_PATTERN_SCALE)));
            }
            if (settings.contains(NBT_PATTERN_DIV_SCALE, Tag.TAG_INT)) {
                patternDivScale = Math.max(MIN_SCALE,
                        Math.min(MAX_SCALE, settings.getInt(NBT_PATTERN_DIV_SCALE)));
            }
            if (settings.contains(NBT_FOA_MODE, Tag.TAG_BYTE)) {
                foaMode = settings.getBoolean(NBT_FOA_MODE);
            }
            if (settings.contains(NBT_FOA_MULTIPLIER, Tag.TAG_INT)) {
                foaMultiplier = Math.max(MIN_FOA_MULTIPLIER,
                        Math.min(MAX_FOA_MULTIPLIER, settings.getInt(NBT_FOA_MULTIPLIER)));
            }
            if (settings.contains(NBT_OUTPUT_MODE, Tag.TAG_BYTE)) {
                isOutputMode = settings.getBoolean(NBT_OUTPUT_MODE);
            }
            invalidateRewriteCacheQuietly();
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：设置类写入失败（已忽略，按原设置）pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    /** 写归档里的机器名字（只有调用方确认"档案带了 + 玩家勾了"才会走到这里）。 */
    void writeArchivedName(String name) {
        try {
            if (name == null || name.isEmpty()) {
                return;
            }
            setCustomName(name);
        } catch (Throwable ignored) {
            // 改名失败不影响样板
        }
    }

    /**
     * 归档的<b>只读摘要</b>：{@code [0] = 样板张数, [1] = 最低槽（1 起，0 = 无）, [2] = 最高槽（1 起，0 = 无）}。
     * 给「归档」页的实时文本用（面板每帧都会问 ⇒ 只扫一遍仓、不做任何解码）。
     */
    public int[] archiveSummary() {
        int count = 0;
        int min = -1;
        int max = -1;
        int capacity = getCapacity();
        for (int i = 0; i < capacity; i++) {
            ItemStack stack;
            try {
                stack = patternInventory.getStackInSlot(i);
            } catch (Throwable ignored) {
                continue;
            }
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            count++;
            if (min < 0) {
                min = i;
            }
            max = i;
        }
        return new int[]{count, min + 1, max + 1};
    }

    // ==================== P2：与 gtlcore **同签名**的四个方法 ====================
    //
    // 同签名的理由：① 语义可直接对照（见 _javap_p2_api.txt【2】的四段字节码）；
    //              ② 将来若有人写"通用"调用方（按基类/接口判定后反射调这四个名字），惰性机也能接上；
    //              ③ 我们的物品/面板与"通用调用方"共用同一条引擎，避免两套语义各修各的。

    /**
     * 复制：把本机全仓（含逐槽元数据）写进传进来的 {@code tag}，<b>返回同一个 tag</b>
     * （gtlcore 的 copyToTag 也是"写进传入的 tag 并 areturn 它"，见【2】）。
     *
     * <p>写入位置：{@code tag.gtlmodular$archive}（新键名，不与 gtlcore 的 {@code tag} 键撞车）。
     * 与 gtlcore 一样：<b>不写"空仓"标记</b> —— 空仓也照样写一个 {@code slot_count=0} 的档案。
     *
     * @param tag 目标 tag（通常来自物品 NBT 根）；{@code null} 返回 {@code null}（不动任何东西）
     */
    public CompoundTag copyToTag(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        try {
            LazyPatternArchiveCodec.Payload payload =
                    buildArchivePayload(archiveIncludeSettings, archiveIncludeName);
            // 序列化的唯一实现在编解码器里（encode），这里只负责把它挂到调用方给的 tag 上 ——
            // 这样"面板 / 物品 / 通用调用方"三条入口的档案格式**只有一份定义**。
            CompoundTag written = LazyPatternArchiveCodec.encode(payload);
            if (written != null) {
                tag.put(LazyPatternArchiveCodec.NBT_STAMP, written);
            }
            return tag;
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：复制失败（本机未改动）pos={} 原因={}",
                    getPos(), failure.toString());
            return tag;
        }
    }

    /**
     * 粘贴：把 {@code tag} 里的档案写进本机。
     *
     * <p>与 gtlcore 的 {@code pasteFromTag} 同义（成功 true / 装不下 false），差异见本段开头的①②③④。
     * 额外接受两种 tag：① 直接是档案本体（带 {@code gtlmodular$archive_version}）；
     * ② gtlcore 的 {@code tag}/{@code cut} 容器（**只读**，见编解码器的版本兼容说明）。
     *
     * @return 是否至少写进去点东西（装不下 / 空档 / 版本太新一律 false，且机器一个字节都没动）
     */
    public boolean pasteFromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }
        try {
            LazyPatternArchiveCodec.Payload payload = readPayloadFromTag(tag);
            if (payload == null || payload.isEmpty()) {
                return false;
            }
            LazyPatternArchiveCodec.ApplyResult result = LazyPatternArchiveCodec.apply(
                    this, payload, archiveIncludeSettings, archiveIncludeName, archivePlaceMode());
            return result != null && result.ok;
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：粘贴失败（已中止，仓内容按操作前）pos={} 原因={}",
                    getPos(), failure.toString());
            return false;
        }
    }

    /**
     * 从档案 {@code tag} 覆盖本机全仓（gtlcore 的 {@code copyFromTag(tag, player)} 同签名）。
     *
     * <p>语义对照（【2】的 copyFromTag 字节码）：gtlcore 的做法是"按 patterns 数组顺序、逐张塞进第一个空槽，
     * 并从玩家背包里<b>扣掉</b>一张空白样板（{@code AEItems.BLANK_PATTERN}）当作耗材，扣不到就中止"。
     * 惰性机<b>有意不搬"扣空白样板"这一步</b>：① 我们的粘贴路径本来就不吃耗材（设计稿未要求）；
     * ② 那一步会动玩家背包（破坏性、且和"先算后写"纪律冲突）。
     * 其余语义一致：读到装不下/解不出就停下，已写的部分保留。
     *
     * @param player 调用者（仅用于日志；本实现不动玩家背包，允许为 {@code null}）
     */
    public void copyFromTag(CompoundTag tag, net.minecraft.server.level.ServerPlayer player) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        try {
            LazyPatternArchiveCodec.Payload payload = readPayloadFromTag(tag);
            if (payload == null || payload.isEmpty()) {
                return;
            }
            LazyPatternArchiveCodec.ApplyResult result = LazyPatternArchiveCodec.apply(
                    this, payload, archiveIncludeSettings, archiveIncludeName, archivePlaceMode());
            GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪归档：按档案覆盖本仓 player={} pos={} 结果={} 样板={} 元数据={}",
                    player == null ? "-" : player.getGameProfile().getName(), getPos(),
                    result == null ? "?" : (result.ok ? result.messageKey : "拒绝/空"),
                    result == null ? 0 : result.patternCount, result == null ? 0 : result.metadataCount);
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：按档案覆盖失败（已中止）pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    /**
     * ★ <b>剪切</b>：把本机全仓（含逐槽元数据）写进 {@code tag}，然后清空本机。
     *
     * <p>与 gtlcore 的 {@code cutToTag} 同签名同语义（写进传入 tag 并 areturn 它），
     * gtlcore 还会额外写 {@code dimension/bufferPos/proxies/sharedCatalystInventory/...}
     * （见【2】），惰性机**只写我们自己的档案结构**（含 dimension/pos 诊断字段），
     * 因为那些共享仓/镜像字段在惰性机上没有对应物。
     *
     * <p><b>"先写成功再清源"在这里的落点</b>：本方法先把整仓<b>读进内存并序列化成功</b>
     * （{@code written != null} 才算成功），<b>只有那之后</b>才调 {@link #clearArchiveContent()}。
     * 序列化失败 ⇒ 直接返回，本机一张样板都不动。
     *
     * <p>清源之后的收尾：只调一次 {@link #onPatternInventoryChanged()}
     * （= 作废样板→槽映射 + 逐槽清缓存/退残留料/通知主机删旧配方 + {@code needPatternSync}
     * 交给既有 lazyTick 合并通道让 AE 取样板表）—— 复用既有机制，<b>不新增刷新路径</b>。
     */
    public CompoundTag cutToTag(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        try {
            LazyPatternArchiveCodec.Payload payload =
                    buildArchivePayload(archiveIncludeSettings, archiveIncludeName);
            // ---------- ① 先写（序列化到内存 tag；这一步失败就绝不清源） ----------
            CompoundTag written = writePayloadToTag(payload);
            if (written == null) {
                GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：剪切中止 —— 档案序列化失败，本机一张样板都没动 pos={}",
                        getPos());
                return tag;
            }
            tag.put(LazyPatternArchiveCodec.NBT_STAMP, written);
            // ---------- ② 写成功了才清源 ----------
            clearArchiveContent();
            // ---------- ③ 唯一一次收尾（既有通道） ----------
            try {
                onPatternInventoryChanged();
            } catch (Throwable failure) {
                GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：剪切后的收尾失败"
                                + "（样板已清空，缓存最迟由下一 tick 的签名轮询兜住）pos={} 原因={}",
                        getPos(), failure.toString());
            }
            GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪归档：剪切完成 张数={} 元数据={} pos={}",
                    payload.patternCount, payload.entries.size(), getPos());
            return tag;
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档：剪切异常（已中止）pos={} 原因={}",
                    getPos(), failure.toString());
            return tag;
        }
    }

    /**
     * 把一份档案序列化成一个独立 CompoundTag（复用编解码器的写侧：**档案格式只有一份定义**）。
     * 失败返回 {@code null}（调用方据此判断"能不能清源"）。
     */
    private static CompoundTag writePayloadToTag(LazyPatternArchiveCodec.Payload payload) {
        // 序列化的**唯一实现在编解码器里**（{@code encode}），物品侧只是"把 encode 的结果塞进物品 NBT"
        // ⇒ 面板 / 物品 / 通用调用方三条入口写出来的档案逐字节相同，不会出现"某一处少写了一个键"。
        // 空档也照样能序列化（gtlcore 的 copyToTag 同样会写一个 patterns=[] 的档案）。
        return LazyPatternArchiveCodec.encode(payload);
    }

    /**
     * 从一个 tag 里读出档案 —— <b>直接转发给编解码器的读侧唯一实现</b>
     * （{@link LazyPatternArchiveCodec#readFromTag}）：它自己会依次试
     * ① 我们的 {@code gtlmodular$archive}、② gtlcore 的 {@code cut}/{@code tag} 容器（只读兼容）、
     * ③ tag 本身就是一份档案。
     */
    private static LazyPatternArchiveCodec.Payload readPayloadFromTag(CompoundTag tag) {
        return LazyPatternArchiveCodec.readFromTag(tag);
    }

    // ==================== P1-②C 样板倍率页 ====================

    /**
     * 样板倍率页：倍率 / 除率 / 重新应用（全部是<b>视图倍率</b>，不动仓里的样板）。
     * ★ 逐条照搬现役机 {@code ScaleConfigurator}（2225-2270），版式/尺寸/文案键逐字一致。
     *
     * <p>现役机那个「侧别（输入/输出）」按钮<b>照现役机一起不搬</b>：倍率/除率现在与神锻一样两侧同倍，
     * 单侧缩放会让"AE 声明"与"机器实际执行"对不上（详见 {@link #rewritePattern}）。
     *
     * <p>槽位/容量相关的一切都没有：本页只写机器字段（三个访问器），所以对惰性化零影响。
     */
    static final class ScaleConfigurator implements IFancyConfigurator {

        private final LazyPatternAssemblyPartMachine machine;

        ScaleConfigurator(LazyPatternAssemblyPartMachine machine) {
            this.machine = machine;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.pattern_assembly.scale.title");
        }

        @Override
        public IGuiTexture getIcon() {
            return new ResourceTexture("gtceu:textures/item/portable_debug_scanner.png");
        }

        @Override
        public List<Component> getTooltips() {
            // 悬停只显示页名（★ 照搬现役机）
            return List.of(Component.translatable("gtlmodular.pattern_assembly.scale.title"));
        }

        @Override
        public Widget createConfigurator() {
            WidgetGroup group = new WidgetGroup(0, 0, 124, 56);
            group.addWidget(new LabelWidget(4, 2,
                    Component.translatable("gtlmodular.pattern_assembly.scale.multiply.label").getString()));
            group.addWidget(new IntInputWidget(4, 14, 58, 14,
                    machine::getPatternScale, machine::setPatternScale)
                    .setMin(MIN_SCALE).setMax(MAX_SCALE));
            group.addWidget(new LabelWidget(62, 2,
                    Component.translatable("gtlmodular.pattern_assembly.scale.divide.label").getString()));
            group.addWidget(new IntInputWidget(58, 14, 58, 14,
                    machine::getPatternDivScale, machine::setPatternDivScale)
                    .setMin(MIN_SCALE).setMax(MAX_SCALE));
            // 旧的「侧别（输入/输出）」按钮已移除：倍率/除率现在与神锻一样两侧同倍，
            // 单侧缩放会让"AE 声明"与"机器实际执行"对不上（详见 rewritePattern 的说明）。
            group.addWidget(new ButtonWidget(4, 34, 110, 14,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(
                            Component.translatable("gtlmodular.pattern_assembly.scale.apply").getString())),
                    data -> machine.invalidateRewriteCache())
                    .setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.scale.apply.tip")));
            return group;
        }
    }

    // ==================== P2 归档（复制 / 剪切）页 ====================

    /**
     * ★ <b>「归档」页</b>（设计稿第三节）：复制 / 剪切 / 粘贴 / 清空 + 「含设置」「含名字」两个开关
     * + <b>「落位模式」开关（0.3.13 新增：默认=追加〔往后延伸、绝不覆盖〕/ 打开=按原槽对齐〔占用则跳过〕）</b>
     * + 本机仓内容与手上工具档案的<b>只读</b>张数展示。
     *
     * <h3>页序</h3>
     * 挂在**最后一页之后**（模式页 / 倍率页 / 神锻页 / 催化剂页 / 电路页之后，见
     * {@link LazyPatternAssemblyPartMachine#attachConfigurators}）：只是追加一个 tab，
     * 不动任何已有页的下标，也不碰催化剂页那套"挂它之前抓 {@code getTabs().size()}"的绑定。
     *
     * <h3>三个动作的落点（全部走同一个引擎 {@link LazyPatternArchiveApi}）</h3>
     * <ul>
     *   <li><b>复制</b>：源机 → 手上的工具（源机一个字节都不动）；</li>
     *   <li><b>剪切</b>：源机 → 工具，<b>写成功之后才</b>清空源机（"先写后清"的唯一落点）；</li>
     *   <li><b>粘贴</b>：工具 → 本机（容量装不下 ⇒ <b>整体拒绝</b>，机器一个字节都不动；
     *       落位方式看本页的「落位模式」开关：默认追加、打开则按原槽对齐);</li>
     *   <li><b>清空工具</b>：只清工具里的档案数据（物品本身留在手上）。</li>
     * </ul>
     *
     * <p><b>玩家是谁</b>：配置面板（{@code IFancyConfigurator}）的构造只给得到机器，
     * 而"手上的工具"必须有玩家才能拿到。这里用与 gtlcore 的 {@code PatternTestBehavior} 同一套口径
     * —— <b>按坐标找最近的玩家</b>（打开本机界面的那个人一定站在旁边），拿不到就退回"最近的玩家"，
     * 再拿不到就只是提示"没找到玩家"，绝不猜一个别人的背包。
     *
     * <p><b>控件只用已验证的三种</b>（{@code ButtonWidget} / {@code GuiTextures.BUTTON} / {@code LabelWidget}）：
     * 这一页刻意不引入 {@code ToggleButtonWidget}（它会把 {@code tooltipText} 拼上 {@code .enabled/.disabled}
     * 当语言键用），改用"文字随状态变"的按钮 —— 少一个会踩坑的隐式契约。
     */
    static final class ArchiveConfigurator implements IFancyConfigurator {

        /** 面板尺寸（与模式页同宽 240，保证展开后不越出主界面）。 */
        private static final int PAGE_WIDTH = 240;
        /** 0.3.13：第三行（含设置 / 含名字）之后多了一行"落位模式"开关 ⇒ 高度 118 → 134。 */
        private static final int PAGE_HEIGHT = 134;

        /** 找玩家的半径（玩家就站在机器旁边开界面）。 */
        private static final double FIND_PLAYER_RADIUS = 8.0D;

        private final LazyPatternAssemblyPartMachine machine;

        ArchiveConfigurator(LazyPatternAssemblyPartMachine machine) {
            this.machine = machine;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.pattern_assembly.archive.title");
        }

        @Override
        public IGuiTexture getIcon() {
            // 直接复用 gtlcore 那件工具物品的贴图（取证：assets/gtlcore/textures/item/me_pattern_buffer_copy.png，
            // 见 _javap_p2_api.txt【3】）——语义一致（都是"把样板总成整仓搬走"），不额外造图。
            return new ResourceTexture("gtlcore:textures/item/me_pattern_buffer_copy.png");
        }

        @Override
        public List<Component> getTooltips() {
            return List.of(Component.translatable("gtlmodular.pattern_assembly.archive.title"));
        }

        @Override
        public Widget createConfigurator() {
            WidgetGroup group = new WidgetGroup(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
            // ---- 第一行：复制 / 剪切 ----
            group.addWidget(new ButtonWidget(4, 4, 74, 16,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(
                            Component.translatable("gtlmodular.pattern_assembly.archive.copy").getString())),
                    data -> doCopy())
                    .setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.archive.copy.tip")));
            group.addWidget(new ButtonWidget(82, 4, 74, 16,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(
                            Component.translatable("gtlmodular.pattern_assembly.archive.cut").getString())),
                    data -> doCut())
                    .setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.archive.cut.tip")));
            // 复制 / 剪切作用的是**手上的工具**，这里显式说明，免得玩家以为按钮是"存到机器里"。
            group.addWidget(new LabelWidget(160, 8, this::heldText));

            // ---- 第二行：粘贴 / 清空工具 ----
            group.addWidget(new ButtonWidget(4, 24, 74, 16,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(
                            Component.translatable("gtlmodular.pattern_assembly.archive.paste").getString())),
                    data -> doPaste())
                    .setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.archive.paste.tip")));
            group.addWidget(new ButtonWidget(82, 24, 74, 16,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(
                            Component.translatable("gtlmodular.pattern_assembly.archive.clear").getString())),
                    data -> doClearTool())
                    .setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.archive.clear.tip")));

            // ---- 第三行：两个"含什么"开关（默认都关：设置与名字属于"这台机器"而不是"这批样板"）----
            group.addWidget(new ButtonWidget(4, 46, 116, 16,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(this::settingsToggleText)),
                    data -> toggleSettings())
                    .setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.archive.include_settings.tip")));
            group.addWidget(new ButtonWidget(124, 46, 112, 16,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(this::nameToggleText)),
                    data -> toggleName())
                    .setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.archive.include_name.tip")));

            // ---- 第四行（0.3.13 新增）：粘贴的**落位模式**开关 ----
            // 默认"追加"= 往后延伸、绝不覆盖；打开 = "按原槽对齐"（占用则跳过）。
            // 与上面两个开关同款控件（ButtonWidget + 文字随状态变），不引入 ToggleButtonWidget。
            group.addWidget(new ButtonWidget(4, 66, 232, 16,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(this::placeToggleText)),
                    data -> togglePlaceMode())
                    .setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.archive.place.tip")));

            // ---- 第五 / 六 / 七行：只读展示（本机读的是活仓，工具读的是手上那份 NBT）----
            group.addWidget(new LabelWidget(4, 86, this::machineLineText));
            group.addWidget(new LabelWidget(4, 100, this::toolLineText));
            group.addWidget(new LabelWidget(4, 114, () ->
                    Component.translatable("gtlmodular.pattern_assembly.archive.hint").getString()));
            return group;
        }

        // ---------- 动作（全部服务端权威；客户端点了只会被服务端拒掉并回一条提示） ----------

        private void doCopy() {
            try {
                ServerPlayer player = findPlayer();
                ItemStack stack = heldTool(player);
                if (stack == null || player == null) {
                    GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪归档页：复制未执行 —— 没找到在场玩家 pos={}", machine.getPos());
                    return;
                }
                LazyPatternArchiveApi.copyFrom(machine, stack, player).tell(player);
            } catch (Throwable failure) {
                GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档页：复制异常（已忽略）pos={} 原因={}",
                        machine.getPos(), failure.toString());
            }
        }

        private void doCut() {
            try {
                ServerPlayer player = findPlayer();
                ItemStack stack = heldTool(player);
                if (stack == null || player == null) {
                    GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪归档页：剪切未执行 —— 没找到在场玩家 pos={}", machine.getPos());
                    return;
                }
                // ★ 先写成功再清源：整个顺序在 LazyPatternArchiveApi.cutFrom 里（见那边的三步注释）。
                LazyPatternArchiveApi.cutFrom(machine, stack, player).tell(player);
            } catch (Throwable failure) {
                GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档页：剪切异常（已忽略）pos={} 原因={}",
                        machine.getPos(), failure.toString());
            }
        }

        private void doPaste() {
            try {
                ServerPlayer player = findPlayer();
                ItemStack stack = heldTool(player);
                if (stack == null || player == null) {
                    GTLModular.LOGGER.info("[gtlmodular] 惰性猪咪归档页：粘贴未执行 —— 没找到在场玩家 pos={}", machine.getPos());
                    return;
                }
                LazyPatternArchiveApi.pasteInto(machine, stack, player).tell(player);
            } catch (Throwable failure) {
                GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档页：粘贴异常（已忽略）pos={} 原因={}",
                        machine.getPos(), failure.toString());
            }
        }

        private void doClearTool() {
            try {
                ServerPlayer player = findPlayer();
                ItemStack stack = heldTool(player);
                if (stack == null || player == null) {
                    return;
                }
                int before = LazyPatternArchiveApi.archivePatternCount(stack);
                LazyPatternArchiveApi.clearArchive(stack);
                LazyPatternArchiveApi.tellKey(player, "gtlmodular.pattern_assembly.archive.cleared", before);
            } catch (Throwable failure) {
                GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪归档页：清空工具异常（已忽略）pos={} 原因={}",
                        machine.getPos(), failure.toString());
            }
        }

        private void toggleSettings() {
            if (machine.isRemote()) {
                // 客户端只发按钮事件、不改本侧字段（与"指定/清除"两个按钮同口径：服务端权威）
                return;
            }
            machine.setArchiveIncludeSettings(!machine.isArchiveIncludeSettings());
        }

        private void toggleName() {
            if (machine.isRemote()) {
                return;
            }
            machine.setArchiveIncludeName(!machine.isArchiveIncludeName());
        }

        /**
         * 切换粘贴落位模式（追加 ⇄ 按原槽对齐）。与上面两个开关同口径：<b>服务端权威</b>
         * （客户端只发按钮事件、不改本侧字段 ⇒ 真值永远来自服务端 {@code @DescSynced} 同步）。
         */
        private void togglePlaceMode() {
            if (machine.isRemote()) {
                return;
            }
            machine.setArchiveAlignSlots(!machine.isArchiveAlignSlots());
        }

        // ---------- 只读展示 ----------

        /** 第三行右半：手上工具的状态（有没有档案 / 几张）。 */
        private String heldText() {
            ItemStack stack = heldTool(findPlayer());
            if (stack == null) {
                return Component.translatable("gtlmodular.pattern_assembly.archive.no_tool").getString();
            }
            int count = LazyPatternArchiveApi.archivePatternCount(stack);
            if (count <= 0) {
                return Component.translatable("gtlmodular.pattern_assembly.archive.tool_empty").getString();
            }
            return Component.translatable("gtlmodular.pattern_assembly.archive.tool_count", count).getString();
        }

        private String settingsToggleText() {
            return Component.translatable("gtlmodular.pattern_assembly.archive.include_settings",
                    onOff(machine.isArchiveIncludeSettings())).getString();
        }

        private String nameToggleText() {
            return Component.translatable("gtlmodular.pattern_assembly.archive.include_name",
                    onOff(machine.isArchiveIncludeName())).getString();
        }

        /**
         * 「落位模式」按钮上的文字（随状态变，与上面两个开关同款做法）。
         * <p>默认（{@code archiveAlignSlots = false}）= 追加；打开 = 按原槽对齐。
         */
        private String placeToggleText() {
            return Component.translatable(machine.isArchiveAlignSlots()
                    ? "gtlmodular.pattern_assembly.archive.place.align"
                    : "gtlmodular.pattern_assembly.archive.place.append").getString();
        }

        private static String onOff(boolean value) {
            return Component.translatable(value
                    ? "gtlmodular.pattern_assembly.archive.on"
                    : "gtlmodular.pattern_assembly.archive.off").getString();
        }

        /** 第五行：本机仓的只读摘要（张数 + 槽位范围）。 */
        private String machineLineText() {
            int[] summary = machine.archiveSummary();
            if (summary[0] <= 0) {
                return Component.translatable("gtlmodular.pattern_assembly.archive.machine_empty").getString();
            }
            return Component.translatable("gtlmodular.pattern_assembly.archive.machine_line",
                    summary[0], summary[1], summary[2]).getString();
        }

        /** 第六行：手上工具档案的只读摘要（张数 + 来源）。 */
        private String toolLineText() {
            ItemStack stack = heldTool(findPlayer());
            if (stack == null) {
                return "";
            }
            int count = LazyPatternArchiveApi.archivePatternCount(stack);
            if (count <= 0) {
                return "";
            }
            String source = LazyPatternArchiveApi.archiveSource(stack);
            if (source.isEmpty()) {
                return Component.translatable("gtlmodular.pattern_assembly.archive.tool_count", count).getString();
            }
            return Component.translatable("gtlmodular.pattern_assembly.archive.tool_source", count, source).getString();
        }

        // ---------- 玩家 / 工具定位 ----------

        /**
         * 打开本机界面的那个玩家：先按坐标找最近的（半径 8 格），再退回"最近的玩家"。
         * 与 gtlcore {@code PatternTestBehavior} 同口径；拿不到就返回 {@code null}（调用方提示、不猜别人）。
         */
        private ServerPlayer findPlayer() {
            try {
                Level level = machine.getLevel();
                if (level == null || level.isClientSide || machine.getPos() == null) {
                    return null;
                }
                net.minecraft.server.level.ServerLevel serverLevel = level.getServer() == null
                        ? null : level.getServer().getLevel(level.dimension());
                if (serverLevel == null) {
                    return null;
                }
                net.minecraft.world.phys.Vec3 center =
                        net.minecraft.world.phys.Vec3.atCenterOf(machine.getPos());
                Player nearest = serverLevel.getNearestPlayer(
                        center.x, center.y, center.z, FIND_PLAYER_RADIUS, false);
                if (nearest instanceof ServerPlayer serverPlayer) {
                    return serverPlayer;
                }
                // 兜底：世界里只有一个人时也让他能用（否则"没反应"会被当成 bug）
                List<ServerPlayer> players = level.getServer() == null
                        ? java.util.Collections.emptyList() : level.getServer().getPlayerList().getPlayers();
                return players.isEmpty() ? null : players.get(0);
            } catch (Throwable ignored) {
                return null;
            }
        }

        /** 玩家主手上的"样板仓归档"工具；没拿工具返回 {@code null}（调用方提示，绝不改别的物品）。 */
        private ItemStack heldTool(ServerPlayer player) {
            if (player == null) {
                return null;
            }
            try {
                ItemStack stack = player.getMainHandItem();
                if (stack == null || stack.isEmpty()) {
                    return null;
                }
                if (!(stack.getItem() instanceof com.gtlmodular.common.item.PatternArchiveItem)) {
                    return null;
                }
                return stack;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    /**
     * 神锻样板模式页（版式对齐 gtladditions：电源开关 + 神锻模式 + 倍率数值框）。
     * ★ 逐条照搬现役机 {@code FoaConfigurator}（2272-2322），含它那段
     * 「gtladditions 不在时退回本机图标」的兜底（gtladditions 是可选依赖，取不到就 catch 掉）。
     */
    private static final class FoaConfigurator implements IFancyConfigurator {

        private final LazyPatternAssemblyPartMachine machine;

        FoaConfigurator(LazyPatternAssemblyPartMachine machine) {
            this.machine = machine;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.pattern_assembly.foa.title");
        }

        @Override
        public IGuiTexture getIcon() {
            // 与 gtladditions 的神锻配置页保持一致：用「伪神之煅炉」(Forge of the Antichrist) 的图标。
            // gtladditions 不在时退回本机图标（它只是可选依赖）。
            try {
                var definition = com.gtladd.gtladditions.common.machine.multiblock.MultiBlockMachine.INSTANCE
                        .getFORGE_OF_THE_ANTICHRIST();
                if (definition != null) {
                    return new ItemStackTexture(definition.asStack());
                }
            } catch (RuntimeException ignored) {
                // 定义尚未注册 / 没装 gtladditions
            }
            return new ItemStackTexture(machine.getDefinition().asStack());
        }

        @Override
        public List<Component> getTooltips() {
            // 悬停只显示页名（★ 照搬现役机）
            return List.of(Component.translatable("gtlmodular.pattern_assembly.foa.title"));
        }

        @Override
        public Widget createConfigurator() {
            WidgetGroup group = new WidgetGroup(0, 0, 124, 56);
            group.addWidget(new ToggleButtonWidget(6, 5, 20, 20, GuiTextures.BUTTON_POWER,
                    machine::isFoaModeEnabled, machine::setFoaModeEnabled)
                    // ★ 照搬现役机：ToggleButtonWidget.updateScreen() 会把这个字符串拼上
                    // .enabled / .disabled 再当【语言键】用（javap 实证，见 _javap_p1c_api.txt 第【12】节），
                    // 而 gtlmodular.pattern_assembly.foa.enabled / .foa.disabled 两个键现役机已在用
                    // ⇒ 这里一个字都不用改、也【不需要新增语言键】。
                    .setTooltipText("gtlmodular.pattern_assembly.foa"));
            group.addWidget(new LabelWidget(32, 10,
                    Component.translatable("gtlmodular.pattern_assembly.foa.mode").getString()));
            group.addWidget(new LabelWidget(6, 36,
                    Component.translatable("gtlmodular.pattern_assembly.foa.multiplier.label").getString()));
            group.addWidget(new IntInputWidget(58, 31, 54, 20,
                    machine::getFoaPatternOutputMultiplier, machine::setFoaPatternOutputMultiplier)
                    .setMin(MIN_FOA_MULTIPLIER).setMax(MAX_FOA_MULTIPLIER));
            return group;
        }
    }

    /**
     * 配方模式页：◀ / 当前模式 x/y / ▶，Shift 点击一次跳 5 个；下方是「填槽号 → 指定 / 清除」。
     * ★ 逐条照搬现役机 {@code RecipeModeConfigurator}（版式与文案键都沿用同一个，
     * 语言键全部复用 {@code gtlmodular.pattern_assembly.mode.*}，本次<b>不需要新增语言键</b>）。
     *
     * <p>与现役机唯一的差别：槽位上界用 {@link #getPatternSlotCount()}（= 当前容量，夹到本机硬上限），
     * 而不是现役机那个固定的 {@code getCatalystSlotCount()} —— 我们的容量是动态的。
     */
    static final class RecipeModeConfigurator implements IFancyConfigurator {

        private final LazyPatternAssemblyPartMachine machine;

        RecipeModeConfigurator(LazyPatternAssemblyPartMachine machine) {
            this.machine = machine;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.pattern_assembly.mode.title");
        }

        @Override
        public IGuiTexture getIcon() {
            return new ResourceTexture("gtceu:textures/item/portable_debug_scanner.png");
        }

        @Override
        public List<Component> getTooltips() {
            List<GTRecipeType> types = machine.getAvailableRecipeTypes();
            if (types.isEmpty()) {
                return List.of(Component.translatable("gtlmodular.pattern_assembly.mode.none"));
            }
            int pick = Math.max(0, Math.min(types.size() - 1, machine.getModeCursorIndex()));
            int live = Math.max(0, Math.min(types.size() - 1, machine.getControllerRecipeTypeIndex()));
            // ★ 0.3.2：悬停把「要指定的模式」与「机器当前模式」都列出来（游标与机器实际模式是两个概念）
            return List.of(
                    Component.translatable("gtlmodular.pattern_assembly.mode.pick",
                            modeComponent(types.get(pick)).getString(), pick + 1, types.size()),
                    Component.translatable("gtlmodular.pattern_assembly.mode.live",
                            modeComponent(types.get(live)).getString(), live + 1, types.size()));
        }

        @Override
        public Widget createConfigurator() {
            // ★ 0.3.2 版式（队长：「整体UI有点短了，拉宽一些，清除的按钮跟边合到一起了」）：
            //   202×70 → 240×96；三行信息各自独立一行，两个按钮右侧留 8px 边距。
            //   加宽是安全的：这一页是"向左展开"的浮层，而主界面（样板格 16×18+16）宽 304，
            //   现役机 202 宽的页只占右边一段 —— 240 宽仍在界面内。
            WidgetGroup group = new WidgetGroup(0, 0, 240, 96);

            // ★ v2：两块半透明分组底板 + 左侧 2px 青竖条（与猪咪合并页同一套语言 ✓）
            //   色值出处：底色沿用 v1 0x33000000 ✓；竖条用 AE2 实测 SELECTION_COLOR #00D5FF ✓
            final int MODEPAGE_BAND = 0x33000000;
            final int MODEPAGE_ACCENT = 0xCC00D5FF;
            // ① 模式游标那一行（◀ 模式名 ▶）
            group.addWidget(new ImageWidget(4, 2, 232, 20, new ColorRectTexture(MODEPAGE_BAND)));
            group.addWidget(new ImageWidget(4, 2, 2, 20, new ColorRectTexture(MODEPAGE_ACCENT)));
            // ② 槽位指定那一行（输入框 + 指定 / 清除）
            group.addWidget(new ImageWidget(4, 24, 232, 22, new ColorRectTexture(MODEPAGE_BAND)));
            group.addWidget(new ImageWidget(4, 24, 2, 22, new ColorRectTexture(MODEPAGE_ACCENT)));
            group.addWidget(new ButtonWidget(4, 4, 18, 16,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture("\u25c0")),
                    data -> cycle(data, false)));
            group.addWidget(new ImageWidget(26, 4, 178, 16,
                    new TextTexture(this::modeLabel).setType(TextTexture.TextType.LEFT)));
            group.addWidget(new ButtonWidget(208, 4, 18, 16,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture("\u25b6")),
                    data -> cycle(data, true)));

            // 手动给槽位指定模式（优先于自动识别，专治产物歧义）
            group.addWidget(new LabelWidget(4, 30, () -> Component.translatable(
                    "gtlmodular.pattern_assembly.mode.manual_slot").getString()));
            group.addWidget(new IntInputWidget(46, 26, 92, 16,
                    machine::getManualAssignSlot, machine::setManualAssignSlot)
                    .setMin(1).setMax(machine.getPatternSlotCount()));
            group.addWidget(new ButtonWidget(144, 26, 42, 16,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(
                            Component.translatable("gtlmodular.pattern_assembly.mode.manual_assign").getString())),
                    data -> {
                        if (!machine.isRemote()) {
                            machine.assignManualType(machine.getManualAssignSlot(), false);
                        }
                    }).setHoverTooltips(
                            // ★ P1-②B：这里把现役机那条 manual_rc.tip（「Shift + 右键/中键点样板槽」）**挂回去** ——
                            //   惰性机现在也有 SlotRightClickOverlay 了，不再是假提示。
                            Component.translatable("gtlmodular.pattern_assembly.mode.manual_assign.tip"),
                            Component.translatable("gtlmodular.pattern_assembly.mode.manual_rc.tip")));
            group.addWidget(new ButtonWidget(190, 26, 42, 16,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(
                            Component.translatable("gtlmodular.pattern_assembly.mode.manual_clear").getString())),
                    data -> {
                        if (!machine.isRemote()) {
                            machine.assignManualType(machine.getManualAssignSlot(), true);
                        }
                    }).setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.mode.manual_clear.tip")));
            // 以下三行都是活文本：手动指定 / 该槽自检（含"指定与自检不一致"告警）/ 机器当前模式
            group.addWidget(new LabelWidget(4, 50, this::assignStateText));
            group.addWidget(new LabelWidget(4, 64, this::autoDetectStateText));
            group.addWidget(new LabelWidget(4, 78, this::liveModeText));
            return group;
        }

        /** ◀ / ▶：Shift 点击一次跳 5 个（★ 照搬现役机的 {@code data.isShiftClick ? 5 : 1}）。 */
        private void cycle(com.lowdragmc.lowdraglib.gui.util.ClickData data, boolean forward) {
            // ★ 0.3.1：这里**不能**加 `if (machine.isRemote()) return;`。
            //   现役机的同名方法没有这个守卫（它是生产代码、玩家一直在用），
            //   而「指定/清除」那两个按钮用的是 `!machine.isRemote()` ⇒ 说明 LDLib 会把控件点击
            //   **同时**投递到服务端（否则那两个按钮在单机里根本不可能生效）。
            //   加了守卫反而有"客户端直接 return、服务端又没收到"而把按钮点死的风险 ⇒ 与现役机保持一致。
            int times = data != null && data.isShiftClick ? 5 : 1;
            for (int i = 0; i < times; i++) {
                machine.cycleRecipeType(forward);
            }
        }

        /**
         * 配方类型 → 组件。用 {@code registryName.toLanguageKey()}（即 {@code gtceu.cutter} 这种）
         * 去查语言文件，所以显示的是「切割机」这种中文名；没翻译时才退回路径名。★ 照搬现役机同名方法。
         */
        private static Component modeComponent(GTRecipeType type) {
            if (type == null || type.registryName == null) {
                return Component.literal("?");
            }
            String key = type.registryName.toLanguageKey();
            String translated = Component.translatable(key).getString();
            if (translated.isEmpty() || translated.equals(key)) {
                return Component.literal(type.registryName.getPath());
            }
            return Component.literal(translated);
        }

        /** 该槽当前的手动指定状态（活文本，跟着同步字段走）。★ 照搬现役机同名方法。 */
        private String assignStateText() {
            String id = machine.getManualAssignTypeId();
            if (id == null || id.isEmpty()) {
                return Component.translatable("gtlmodular.pattern_assembly.mode.manual_state.none").getString();
            }
            return Component.translatable("gtlmodular.pattern_assembly.mode.manual_state.assigned",
                    recipeTypeName(id)).getString();
        }

        /** 类型 id → 中文模式名（和模式页同一套口径）。★ 照搬现役机同名方法。 */
        private static String recipeTypeName(String typeId) {
            try {
                ResourceLocation id = ResourceLocation.tryParse(typeId);
                if (id != null) {
                    String key = id.toLanguageKey();
                    String translated = Component.translatable(key).getString();
                    if (!translated.isEmpty() && !translated.equals(key)) {
                        return translated;
                    }
                }
            } catch (RuntimeException ignored) {
                // 退回落路径名
            }
            try {
                ResourceLocation id = ResourceLocation.tryParse(typeId);
                return id == null ? typeId : id.getPath();
            } catch (RuntimeException ignored) {
                return typeId;
            }
        }

        private String modeName(int index) {
            List<GTRecipeType> types = machine.getAvailableRecipeTypes();
            if (types.isEmpty()) {
                return "?";
            }
            return modeComponent(types.get(Math.max(0, Math.min(types.size() - 1, index)))).getString();
        }

        /**
         * 第一行：「<b>指定用模式</b>：中文名 x/y」——0.3.2 起读的是<b>模式页游标</b>
         * （玩家 ◀/▶ 选的、也是「指定」按钮会写下去的那个模式），不再是每 tick 被改写的
         * {@code activeRecipeTypeIndex}（那个是"机器当前模式"，现在单独显示在第 4 行）。
         */
        private String modeLabel() {
            List<GTRecipeType> types = machine.getAvailableRecipeTypes();
            if (types.isEmpty()) {
                return Component.translatable("gtlmodular.pattern_assembly.mode.none").getString();
            }
            int index = Math.max(0, Math.min(types.size() - 1, machine.getModeCursorIndex()));
            return Component.translatable("gtlmodular.pattern_assembly.mode.pick",
                    modeName(index), index + 1, types.size()).getString();
        }

        /**
         * 第三行：「该槽自检 = 哪种模式」。手动指定与自检<b>不一致</b>时改显示红色告警
         * —— 0.3.1 那个「只有钻石的样板被指定到切割机、料卡死在缓冲里」的事故，
         * 玩家在这一行本可以一眼看出来。
         */
        private String autoDetectStateText() {
            String auto = machine.getManualAssignAutoTypeId();
            if (auto == null || auto.isEmpty()) {
                return Component.translatable("gtlmodular.pattern_assembly.mode.slot_auto.none").getString();
            }
            String autoName = recipeTypeName(auto);
            String manual = machine.getManualAssignTypeId();
            if (manual != null && !manual.isEmpty() && !manual.equals(auto)) {
                return Component.translatable("gtlmodular.pattern_assembly.mode.slot_conflict", autoName).getString();
            }
            return Component.translatable("gtlmodular.pattern_assembly.mode.slot_auto.assigned", autoName).getString();
        }

        /** 第四行：「机器当前模式」= 控制器当下的配方模式（模式页游标只决定"要指定什么"）。 */
        private String liveModeText() {
            List<GTRecipeType> types = machine.getAvailableRecipeTypes();
            if (types.isEmpty()) {
                return "";
            }
            int live = Math.max(0, Math.min(types.size() - 1, machine.getControllerRecipeTypeIndex()));
            return Component.translatable("gtlmodular.pattern_assembly.mode.live",
                    modeName(live), live + 1, types.size()).getString();
        }
    }

    // ==================== P1-②B 专属催化剂槽页 ====================

    /**
     * 「专属催化剂槽」页 —— <b>逐条照搬现役机 {@code CatalystAccessConfigurator}</b>
     * （开关按钮 + 展开动画 44 → 128、槽号输入框、{@code MEPatternCatalystUIManager} 的五个参数、
     * {@code toggleFor(0)}、右侧并排布局 {@code layoutSideBySide}，版式与文案键全都一样）。
     *
     * <p>与现役机的<b>两处差别</b>：
     * <ol>
     *   <li>槽位上界用机器当前的动态容量（{@code getPatternSlotCount()}），
     *       不用现役机那个固定的 {@code getCatalystSlotCount()}；</li>
     *   <li>★ <b>交给 UIManager 的不是「每槽一个仓」的数组，而是「长度 1 的数组 + 视图代理」</b>
     *       （{@link CatalystItemView} / {@link CatalystFluidView}）：
     *       槽控件只建一次，切页只换代理的 {@code target} —— 这样"惰性"仍然成立
     *       （只有真被查看的那一槽才会建出真仓），但两侧的菜单槽位表<b>永不变动</b>，
     *       原版槽位同步的下标就不会错开（老实现切页重建控件的病根，见代理类 javadoc）。</li>
     * </ol>
     *
     * <p>槽位状态一律走机器上的同步字段（{@code catalystViewSlot} / {@code catalystPageOpen}）：
     * 客户端每帧 {@code updateScreen}、服务端每 tick {@code detectAndSendChanges} 各自读字段
     * 调整"可见性 + 代理指向"（这两件事都是 O(1)、都不动控件结构）。
     * 注意 LDLib 的数值框文本回调<b>只在服务器侧执行</b>，所以界面绝不能直接由数值框驱动。
     */
    static final class CatalystAccessConfigurator implements IFancyConfigurator {

        private final LazyPatternAssemblyPartMachine machine;

        /** 代理当前指向的样板槽下标（0 起；-1 = 还没同步过）。 */
        private int shownIndex = -1;

        /**
         * ★ 0.3.7b：上一次 {@code followMachineState} 时页面是否展开。
         * 用于「页面刚展开 ⇒ 强制原版把整份槽内容重发一次」（修"刚建好那一包丢失"）。
         */
        private boolean wasPageOpen;

        /**
         * ★ 0.3.7b：客户端上一次"应用过"的同步文本（只有变了才解析）。
         * 服务端则用 {@link #lastSentIndex}/{@link #lastSentItems}/{@link #lastSentFluids} 做变更判定。
         */
        private String lastAppliedPayload = "";

        /** ★ 0.3.7b 取证：客户端"收到内容同步"只打一条日志。 */
        private boolean loggedPayloadApply;

        private WidgetGroup root;
        private MEPatternCatalystUIManager catalystUI;

        /** ★ 长度 1 的代理：界面上那套控件（9 物品 + 9 罐）永远指着这两个对象，切页只换 target。 */
        private CatalystItemView itemView;
        private CatalystFluidView[] fluidViews;

        /** 交给 UIManager 的阈值缓冲（长度 1，与代理数组对偶；控件随后被并排布局摘掉）。 */
        private byte[] countBuffer;

        private ConfiguratorPanel panel;
        private ConfiguratorPanel.Tab tab;

        /**
         * ★★ 0.3.7（队长实测：「一打开反而看不见了，切到二再切回一反而看到了」）★★
         * <b>控件级内容同步</b>的 id 与「上次推过的内容」快照。
         *
         * <p>为什么会看不见：页面的槽控件是<b>开界面时</b>才建出来的，而槽里的内容原版是走
         * {@code ClientboundContainerSetSlotPacket} <b>按菜单槽位表下标</b>下发的 ——
         * 「页面刚建好那一瞬」服务端可能已经把值当"已发送"记下了、客户端那时还没有那些槽
         * ⇒ <b>包被丢掉且不再重发</b>，直到内容发生一次变化（切页正好是一次变化）才重新下发
         * ⇒ 表现就是「一打开看不见、切到二再切回一才看见」。
         * 这里改用 LDLib 的<b>控件级同步</b>（{@code writeUpdateInfo}/{@code readUpdateInfo}，
         * 与 LDLib 自己的 {@code TankWidget} 同一套路，javap 实证）：它是"控件→控件"的点对点，
         * 不依赖菜单槽位表是否已经建好 ⇒ 无论客户端什么时候建出控件，下一 tick 就会收到正确内容。
         * 顺带把流体罐的客户端缓存也填上（原来客户端只靠 {@code TankWidget} 自己的同步渲染，
         * 点击/JEI 路径会报空）。
         */
        /** 上一次推给客户端的 9 格物品 / 9 罐流体；只需"变了才推"。 */
        private final ItemStack[] lastSentItems = new ItemStack[CATALYST_ITEM_SLOTS];
        private final FluidStack[] lastSentFluids = new FluidStack[CATALYST_FLUID_TANKS];

        /** 上一次推的槽号：切页一定重推（{@code Integer.MIN_VALUE} = 还没推过 ⇒ 页面一出现就推一次）。 */
        private int lastSentIndex = Integer.MIN_VALUE;

        /** 面板固定尺寸：不随内容动态变大，避免展开视图顶出屏幕被切掉。★ 照搬现役机。 */
        private static final int PAGE_WIDTH = 202;
        private static final int PAGE_CLOSED_HEIGHT = 44;
        /** 展开高度 = 顶部那一行(42) + 槽位容器高(3×18+24=78) + 余量。★ 照搬现役机。 */
        private static final int PAGE_OPEN_HEIGHT = 128;

        CatalystAccessConfigurator(LazyPatternAssemblyPartMachine machine) {
            this.machine = machine;
            // "上次推过的内容"初始化为空（用 EMPTY/empty 而不是 null：比较时要拿它当基准）
            for (int i = 0; i < lastSentItems.length; i++) {
                lastSentItems[i] = ItemStack.EMPTY;
            }
            for (int i = 0; i < lastSentFluids.length; i++) {
                lastSentFluids[i] = FluidStack.empty();
            }
        }

        void bindPanel(ConfiguratorPanel panel, ConfiguratorPanel.Tab tab) {
            this.panel = panel;
            this.tab = tab;
        }

        private int slotCount() {
            return machine.getPatternSlotCount();
        }

        /** 当前要查看的样板槽下标（0 起；按机器同步字段算，越界一律夹到 0）。 */
        private int currentViewIndex() {
            return Math.max(0, machine.getCatalystViewSlot() - 1);
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.pattern_assembly.catalyst.title");
        }

        @Override
        public IGuiTexture getIcon() {
            return new ItemStackTexture(machine.getDefinition().asStack());
        }

        @Override
        public List<Component> getTooltips() {
            // 悬停只显示页名（★ 照搬现役机）
            return List.of(Component.translatable("gtlmodular.pattern_assembly.catalyst.title"));
        }

        @Override
        public Widget createConfigurator() {
            int slots = slotCount();
            shownIndex = -1;
            root = new WidgetGroup(0, 0, PAGE_WIDTH, PAGE_CLOSED_HEIGHT) {

                @Override
                public void updateScreen() {
                    super.updateScreen();
                    followMachineState();
                }

                @Override
                public void detectAndSendChanges() {
                    super.detectAndSendChanges();
                    followMachineState();
                }
            };
            root.addWidget(new LabelWidget(6, 4,
                    Component.translatable("gtlmodular.pattern_assembly.catalyst.slot").getString()));
            root.addWidget(new IntInputWidget(6, 18, 74, 20,
                    machine::getCatalystViewSlot, machine::setCatalystViewSlot).setMin(1).setMax(slots));
            root.addWidget(new ButtonWidget(86, 18, 110, 20, buttonTexture(false), data -> toggle())
                    .setHoverTexture(buttonTexture(true)));

            // ★★ 惰性化 + 结构永不变：交给 UIManager 的是「长度 1 的数组」，里面装的是视图代理。
            //    · 物品侧：1 个代理（getSlots() 恒为 9）⇒ 9 个物品槽控件；
            //    · 流体侧：FluidTransferList 装 9 个「单罐代理」（与 gtlcore 具体机
            //      `new FluidTransferList(9 个 FluidStorage)` 的元素个数一致）⇒ 9 个罐控件。
            //    代理的构造**不碰**任何催化剂真仓 ⇒ 只是打开界面、从没点过催化剂页时，
            //    一个催化剂仓都不会被建出来；只有真被查看的那一槽（代理 target）才在首次读写时懒建。
            //    （两个代理是机器的内部类 → 用限定式 `machine.new` 建，保证绑定到本侧这台机器。）
            this.itemView = machine.new CatalystItemView();
            List<IFluidTransfer> tanks = new ArrayList<>(CATALYST_FLUID_TANKS);
            this.fluidViews = new CatalystFluidView[CATALYST_FLUID_TANKS];
            for (int i = 0; i < CATALYST_FLUID_TANKS; i++) {
                fluidViews[i] = machine.new CatalystFluidView(i);
                tanks.add(fluidViews[i]);
            }
            this.countBuffer = new byte[]{(byte) DEFAULT_CACHE_RECIPE_COUNT};
            catalystUI = new MEPatternCatalystUIManager(0,
                    new IItemTransfer[]{itemView},
                    new FluidTransferList[]{new FluidTransferList(tanks)},
                    countBuffer,
                    ignored -> machine.onCacheCountChangedFromUi(currentViewIndex(), countBuffer));
            catalystUI.setSelfPosition(2, 42);
            catalystUI.setSize(PAGE_WIDTH - 8, PAGE_OPEN_HEIGHT - 48);
            catalystUI.setVisible(false);
            catalystUI.setActive(false);
            root.waitToAdded(catalystUI);
            // ★ 全套槽控件**在这里、也只在这里**建一次（UIManager 的 lastIndex 初值是 -1，
            //   所以第一次 toggleFor(0) 一定走 show(0,...) 真正建出 9 物品 + 9 罐，而不是"翻转可见性"）。
            //   两侧都在 createConfigurator()（= 开界面时）跑这一句 ⇒ 菜单槽位表从第一刻起就对齐。
            //   之后**永远不再** toggleFor / clearAllWidgets —— 切页只换代理指向（见 followMachineState）。
            catalystUI.toggleFor(0);
            // 并排布局同样只做一次（控件结构不变，布局也就不需要重做）。
            layoutSideBySide();
            followMachineState();
            return root;
        }

        private IGuiTexture buttonTexture(boolean hover) {
            TextTexture text = new TextTexture(
                    () -> Component.translatable(machine.isCatalystPageOpen()
                            ? "gtlmodular.pattern_assembly.catalyst.close"
                            : "gtlmodular.pattern_assembly.catalyst.open").getString());
            text.setWidth(114);
            text.setDropShadow(true);
            if (hover) {
                return new GuiTextureGroup(GuiTextures.BUTTON,
                        new ColorRectTexture(0x40FFFFFF), text);
            }
            return new GuiTextureGroup(GuiTextures.BUTTON, text);
        }

        private void toggle() {
            machine.setCatalystPageOpen(!machine.isCatalystPageOpen());
        }

        /** 右键/中键样板槽（只在服务器侧调用）：切到该槽 + 展开本页 + 把配置面板切到这一页。 */
        void openSlotFromPattern(int slotIndex) {
            machine.setCatalystViewSlot(slotIndex + 1);
            machine.setCatalystPageOpen(true);
            try {
                if (panel != null && tab != null && panel.getExpanded() != tab) {
                    panel.expandTab(tab);
                }
            } catch (RuntimeException ignored) {
                // 面板结构拿不到就只切槽位
            }
        }

        /**
         * ★ 每 tick（服务端 {@code detectAndSendChanges}）/ 每帧（客户端 {@code updateScreen}）跟一次机器状态。
         *
         * <p><b>这里只做三件 O(1) 的事</b>：① 可见性；② 面板高度；③ <b>把代理指向切到要看的槽</b>。
         * <b>绝不再增删/重排/重建任何控件</b>（老实现的 {@code fillCatalystSnapshot(index)} +
         * 逐 index {@code toggleFor(index)} + 每次切页都 {@code layoutSideBySide()} 就是病根：
         * 两侧在不同时刻重建控件 ⇒ 原版菜单槽位表下标错开 ⇒ 显示跟着切页"错槽"）。
         *
         * <p>惰性化红线不破：{@code setTarget} 只是改一个 int；真仓的懒建发生在代理首次
         * 读写 {@code target} 那一槽时（见 {@link CatalystItemView#serverTransfer()}），
         * 且**只**发生在 {@code target} 上。
         */
        private void followMachineState() {
            if (catalystUI == null) {
                return;
            }
            if (!machine.isCatalystPageOpen()) {
                if (catalystUI.isVisible()) {
                    catalystUI.setVisible(false);
                    catalystUI.setActive(false);
                }
                if (root != null && root.getSizeHeight() != PAGE_CLOSED_HEIGHT) {
                    root.setSize(PAGE_WIDTH, PAGE_CLOSED_HEIGHT);
                }
                return;
            }
            int index = currentViewIndex();
            if (index != shownIndex) {
                shownIndex = index;
                // ★ 切页的全部动作：只换代理指向（客户端也换，但客户端读的是本地缓存，
                //   那份缓存由原版槽位同步按**固定的槽写下标**持续灌入 ⇒ 天然跟着服务端走）。
                if (itemView != null) {
                    itemView.setTarget(index);
                }
                if (fluidViews != null) {
                    for (CatalystFluidView view : fluidViews) {
                        view.setTarget(index);
                    }
                }
            }
            if (!catalystUI.isVisible()) {
                catalystUI.setVisible(true);
                catalystUI.setActive(true);
            }
            if (root != null && root.getSizeHeight() != PAGE_OPEN_HEIGHT) {
                root.setSize(PAGE_WIDTH, PAGE_OPEN_HEIGHT);
            }
            // ★★ 0.3.7b：内容同步 —— 服务端把"当前查看槽"的内容写进机器的同步字段，
            //   客户端每帧读该字段并落进代理缓存（槽控件渲染读的就是它）。
            //   走 @DescSynced String（本机已验证）而不是"原版槽位同步/控件同步"，理由见字段注释。
            if (machine.isRemote()) {
                applyCatalystViewFromMachine();
            } else {
                publishCatalystViewToMachine();
            }
        }

        /**
         * 服务端：内容变了（或切了槽）就把「当前查看槽」的催化剂内容序列化进
         * {@code machine.catalystViewPayload}。变更判定避免每 tick 重建字符串。
         */
        private void publishCatalystViewToMachine() {
            if (itemView == null || fluidViews == null) {
                return;
            }
            int index = currentViewIndex();
            boolean indexChanged = index != lastSentIndex;
            boolean itemsChanged = indexChanged;
            for (int i = 0; i < CATALYST_ITEM_SLOTS && !itemsChanged; i++) {
                if (!ItemStack.matches(itemView.getStackInSlot(i), lastSentItems[i])) {
                    itemsChanged = true;
                }
            }
            boolean fluidsChanged = indexChanged;
            for (int i = 0; i < CATALYST_FLUID_TANKS && !fluidsChanged; i++) {
                FluidStack now = fluidViews[i].getFluidInTank(0);
                if (!now.isFluidEqual(lastSentFluids[i]) || now.getAmount() != lastSentFluids[i].getAmount()) {
                    fluidsChanged = true;
                }
            }
            if (!itemsChanged && !fluidsChanged) {
                return;
            }
            try {
                CompoundTag payload = new CompoundTag();
                net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
                for (int i = 0; i < CATALYST_ITEM_SLOTS; i++) {
                    ItemStack stack = itemView.getStackInSlot(i).copy();
                    lastSentItems[i] = stack;
                    items.add(stack.serializeNBT());
                }
                payload.put("items", items);
                net.minecraft.nbt.ListTag fluids = new net.minecraft.nbt.ListTag();
                for (int i = 0; i < CATALYST_FLUID_TANKS; i++) {
                    FluidStack fluid = fluidViews[i].getFluidInTank(0).copy();
                    lastSentFluids[i] = fluid;
                    fluids.add(fluid.saveToTag(new CompoundTag()));
                }
                payload.put("fluids", fluids);
                machine.catalystViewPayload = payload.toString();
                lastSentIndex = index;
            } catch (Throwable failure) {
                // 同步失败绝不影响页面本身（原版槽位同步那条路仍在）
                GTLModular.LOGGER.warn("[gtlmodular] 惰性猪咪催化剂页: 内容同步失败（已忽略）pos={} 槽={} 原因={}",
                        machine.getPos(), index + 1, failure.toString());
            }
        }

        /** 客户端：同步文本变了就解析并落进代理缓存（每帧只做一次字符串比较）。 */
        private void applyCatalystViewFromMachine() {
            if (itemView == null || fluidViews == null) {
                return;
            }
            String payload = machine.getCatalystViewPayload();
            if (payload == null || payload.isEmpty() || payload.equals(lastAppliedPayload)) {
                return;
            }
            lastAppliedPayload = payload;
            try {
                CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(payload);
                net.minecraft.nbt.ListTag items = tag.getList("items", Tag.TAG_COMPOUND);
                int nonEmpty = 0;
                for (int i = 0; i < CATALYST_ITEM_SLOTS && i < items.size(); i++) {
                    ItemStack stack = ItemStack.of(items.getCompound(i));
                    if (!stack.isEmpty()) {
                        nonEmpty++;
                    }
                    itemView.setStackInSlot(i, stack);
                }
                net.minecraft.nbt.ListTag fluids = tag.getList("fluids", Tag.TAG_COMPOUND);
                for (int i = 0; i < CATALYST_FLUID_TANKS && i < fluids.size(); i++) {
                    fluidViews[i].setFluidInTank(0, FluidStack.loadFromTag(fluids.getCompound(i)));
                }
                if (!loggedPayloadApply) {
                    // ★ 0.3.7b 取证：客户端收到内容同步（一条就够）
                    loggedPayloadApply = true;
                    GTLModular.LOGGER.info(
                            "[gtlmodular] 惰性猪咪催化剂页: 客户端收到内容同步 非空物品={}/{} 槽={}",
                            nonEmpty, CATALYST_ITEM_SLOTS, currentViewIndex() + 1);
                }
            } catch (Throwable ignored) {
                // 解析失败就当这次没同步
            }
        }

        /**
         * 摘掉 gtlcore 的「实际配方数量」，把物品催化剂槽与流体催化剂槽左右并排。
         * 只挪容器组本身（内层槽位组在容器里，跟着走）；用 self 坐标，别用含父级的绝对坐标。
         * ★ 逐字照搬现役机同名方法。
         *
         * <p>★ 惰性机只在 {@code createConfigurator()} 里<b>建完控件之后调一次</b>：
         * 控件结构永不变 ⇒ 布局也不需要跟着切页重做（老实现每次切页都调它，也是错位病根的一部分）。
         */
        private void layoutSideBySide() {
            if (catalystUI == null) {
                return;
            }
            List<Widget> children = catalystUI.widgets;
            removeCacheCountWidget();
            Widget itemBox = findContainer(children, SlotWidget.class, null);
            Widget fluidBox = findContainer(children, TankWidget.class, itemBox);
            if (itemBox == null || fluidBox == null) {
                return;
            }
            itemBox.setSelfPosition(4, 0);
            fluidBox.setSelfPosition(4 + itemBox.getSizeWidth() + 8, 0);
            catalystUI.setSize(PAGE_WIDTH - 8, PAGE_OPEN_HEIGHT - 48);
            if (root != null) {
                root.setSize(PAGE_WIDTH, PAGE_OPEN_HEIGHT);
            }
        }

        private void removeCacheCountWidget() {
            if (catalystUI == null) {
                return;
            }
            List<Widget> children = catalystUI.widgets;
            for (int i = children.size() - 1; i >= 0; i--) {
                if (containsIntInput(children.get(i))) {
                    children.remove(i);
                }
            }
        }

        private static boolean containsIntInput(Widget widget) {
            if (widget instanceof IntInputWidget) {
                return true;
            }
            if (widget instanceof WidgetGroup group) {
                for (Widget child : group.widgets) {
                    if (containsIntInput(child)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static WidgetGroup findContainer(List<Widget> widgets, Class<?> slotType, Widget exclude) {
            for (Widget child : widgets) {
                if (child instanceof WidgetGroup group && group != exclude && containsSlot(group, slotType)) {
                    return group;
                }
            }
            return null;
        }

        private static boolean containsSlot(Widget widget, Class<?> slotType) {
            if (slotType.isInstance(widget)) {
                return true;
            }
            if (widget instanceof WidgetGroup group) {
                for (Widget child : group.widgets) {
                    if (containsSlot(child, slotType)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    // ==================== P1-②B 样板槽右键/中键层 ====================

    /**
     * 样板槽上的透明点击层：<b>右键/中键</b>点样板槽 → 打开该槽的专属催化剂槽；
     * <b>Shift + 右键/中键</b> → 把当前选中的模式指定给该槽。
     * 只接管右键和中键，左键返回 false 透传；手上拿着东西时右键也透传。
     * 客户端只发 client action，槽位/展开状态写在服务器侧的同步字段上。★ 逐条照搬现役机。
     *
     * <p><b>风险评估（P1-②B 结论：做了）</b>：覆盖层与现役机作用在**同一套控件**上
     * （同一个 {@code PaginationUIManager} 建出来的 {@code AEPatternViewExtendSlotWidget}），
     * 而现役机是长期在跑的产线代码 ⇒ 判定"可控"。
     * 已知的**行为变化**（与现役机一致，故接受）：样板格上的普通右键不再透传给分页器
     * （现役机同样如此），而是打开该槽的催化剂页；左键/拖拽完全不受影响。
     */
    private static final class SlotRightClickOverlay extends WidgetGroup {
        private static final int ACTION_OPEN_CATALYST_SLOT = 20;
        private static final int ACTION_ASSIGN_MODE_SLOT = 21;

        private final LazyPatternAssemblyPartMachine machine;
        private final WidgetGroup base;

        SlotRightClickOverlay(LazyPatternAssemblyPartMachine machine, WidgetGroup base, int width, int height) {
            super(0, 0, width, height);
            this.machine = machine;
            this.base = base;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 1 && button != 2) {
                return false;
            }
            if (button == 1 && handsNotEmpty()) {
                return false;
            }
            int slot = findPatternSlotIndex(base, mouseX, mouseY);
            if (slot < 0) {
                return false;
            }
            // Shift + 右键/中键 = 把当前选中的模式指定给这个样板槽；普通右键/中键 = 开该槽的催化剂页
            boolean assign = net.minecraft.client.gui.screens.Screen.hasShiftDown();
            writeClientAction(assign ? ACTION_ASSIGN_MODE_SLOT : ACTION_OPEN_CATALYST_SLOT,
                    buffer -> buffer.writeVarInt(slot));
            return true;
        }

        @Override
        public void handleClientAction(int id, FriendlyByteBuf buffer) {
            super.handleClientAction(id, buffer);
            if (id == ACTION_ASSIGN_MODE_SLOT) {
                int slot = buffer.readVarInt();
                machine.setManualAssignSlot(slot + 1);
                machine.assignManualType(slot + 1, false);
                return;
            }
            if (id != ACTION_OPEN_CATALYST_SLOT) {
                return;
            }
            int slot = buffer.readVarInt();
            machine.setManualAssignSlot(slot + 1);   // 顺手让模式页的槽号跟着走
            CatalystAccessConfigurator page = machine.activeCatalystPage;
            if (page != null) {
                page.openSlotFromPattern(slot);
            } else {
                machine.setCatalystViewSlot(slot + 1);
                machine.setCatalystPageOpen(true);
            }
        }

        private static boolean handsNotEmpty() {
            try {
                net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
                return minecraft.player != null && !minecraft.player.containerMenu.getCarried().isEmpty();
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        private static int findPatternSlotIndex(Widget widget, double mouseX, double mouseY) {
            if (widget instanceof AEPatternViewExtendSlotWidget slot) {
                if (!slot.isMouseOverElement(mouseX, mouseY) || slot.getHandler() == null) {
                    return -1;
                }
                return slot.getHandler().getContainerSlot();
            }
            if (widget instanceof WidgetGroup group) {
                for (Widget child : group.widgets) {
                    int found = findPatternSlotIndex(child, mouseX, mouseY);
                    if (found >= 0) {
                        return found;
                    }
                }
            }
            return -1;
        }
    }
}
