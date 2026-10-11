package com.gtlmodular.common.machine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.AETextInputButtonWidget;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.syncdata.managed.IRef;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.gtlcore.gtlcore.api.gui.MEPatternCatalystUIManager;
import org.gtlcore.gtlcore.common.machine.multiblock.part.PaginationUIManager;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferRecipeHandlerTrait;
import org.gtlcore.gtlcore.integration.ae2.widget.AEPatternViewExtendSlotWidget;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 猪咪样板总成（MV ~ UIV，共 10 级）。
 *
 * <p><b>不依赖任何第三方「样板总成」实现</b>：本类直接继承 gtlcore 的
 * {@link MEPatternBufferPartMachine}（ME 样板总成的公共基类），自己拼装：
 * <ul>
 *   <li>分页样板槽 —— 用 gtlcore 公开的 {@link PaginationUIManager}（16 列 × 8 行 = 128/页）；</li>
 *   <li>专属催化剂槽页 —— 用 gtlcore 公开的 {@link MEPatternCatalystUIManager}；</li>
 *   <li>配方模式页 —— 自己实现，并且<b>逐槽自动识别</b>（见下）；</li>
 *   <li>样板倍率页 / 神锻样板模式 —— 自己实现的「视图倍率」：只改本仓向 AE 暴露的样板，
 *       仓里的样板物品一个字节都不动，关掉/改数值立刻恢复。</li>
 * </ul>
 *
 * <h3>配方模式：为什么会「串」，以及怎么做到不串</h3>
 * <p>GT 的配方机（含大型切割机这类多模式多方块）每 tick <b>只在「当前激活的那一种配方类型」里找配方</b>
 * （{@code RecipeLogic.searchRecipe()} 用的是 {@code machine.getRecipeType()}，不是遍历全部类型）。
 * 所以一台机器上两种模式的样板混在一个仓里时，如果只有「一个全局模式选择」，推送另一种模式的样板，
 * 控制器还停在原模式下搜配方 —— 而切割/车床这类模式<b>经常共用同一种原料</b>，
 * 于是原模式下也能匹配到「另一个配方」，把对方的料吃掉、产出错的货，这就是串。
 *
 * <p>本类的做法：
 * <ol>
 *   <li><b>读样板的配方类型</b>：样板进槽时（{@link #onPatternChange(int)}）把它解出来，
 *       拿它的<b>输出</b>去控制器的每一种配方类型里比对（{@link #detectRecipeType(int, List)}），
 *       命中哪一种就把类型下标记在这个槽位上（{@link #slotRecipeType}）；</li>
 *   <li><b>锁搜索端</b>：AE 推这个槽位的样板时（{@link #pushPattern}），先把控制器切到该槽记住的类型，</li>
 *   <li><b>并作废上一次配方</b>（{@code RecipeLogic.markLastRecipeDirty()}）——
 *       否则 GT 会继续优先跑 {@code lastRecipe}，模式切了也没用，照样串。</li>
 * </ol>
 * 识别不出来时（例如两种模式输出完全相同）退回手动选的模式，界面上的「配方模式」页仍然可用。
 */
public class MoltenPatternAssemblyPartMachine extends MEPatternBufferPartMachine {

    /** 每页样板数：16 列 × 8 行 = 128。 */
    public static final int PATTERNS_PER_ROW = 16;
    public static final int ROWS_PER_PAGE = 10;   // ★ 0.3.60：恢复"每页加两行"（0.3.59 诊断已证明它与剪切 bug 无关 ✓）
    public static final int PER_PAGE = PATTERNS_PER_ROW * ROWS_PER_PAGE;
    /**
     * ★ 0.3.80 修复（用户实测：升级后样板全消失 ✗）：
     * <b>等级容量必须与 UI 每页行数解耦</b> ✗ —— 等级容量决定了注册给 gtlcore 的
     * {@code maxPatternCount}，而它以 {@code internalInventory} 表长**写进存档** ✓
     * 一旦把每页从 8 行（128 格）改成 10 行（160 格）✗，低级机（MV=128）的容量会变成 160 ✗
     * ⇒ 读旧档时表被 128→160 重建 ⇒ **样板被丢** ✗（探针实测：金样本 128 / 坏档 160 ✓）
     * ⇒ 故容量一律按历史公式 {@code 16 × 8 = 128} 计算 ✓ UI 那 10 行不受影响 ✓
     */
    public static final int CAPACITY_ROWS_PER_PAGE = 8;
    /** 等级容量用的每页格数（**恒为历史值 128**，绝不要跟 {@link #PER_PAGE} 联动 ✗）。 */
    public static final int CAPACITY_PER_PAGE = PATTERNS_PER_ROW * CAPACITY_ROWS_PER_PAGE;

    /**
     * ★ 0.3.25（方案 A 的<b>唯一旋钮</b>）：在「与惰性机同款的高度公式」之上<b>再额外加多少像素</b>。
     *
     * <p>0 = 与惰性机同高（{@code 8*18+36 = 180}，队长那台惰性机就是这一档，实测该机归档页正常）。
     * 若实测「锚点还高、页还是顶出去」⇒ <b>只改这一个数</b>：8 / 16 / <b>26</b>（26 = 正好抵掉片 C 多加的那一个
     * tab 的高度，见 {@link #PANEL_DOWN_SHIFT} 的推导）。界面会跟着变高。
     *
     * <p><b>上限</b>：逻辑屏 640×360（GUI scale 3，队长机实测口径）内，"主界面 + 展开子页"都得放得下；
     * 主界面每加高 1px，屏幕上沿余量就少 1px（整机在屏幕上是居中的）。惰性机的 180 已被实测接受，
     * 因此建议按 8px 步进试，不行就退回上一档（改回 0 即恢复本轮之前的样子）。
     */
    // ★ 0.3.34：-8 → **44**（uiHeight 172 → 224，加高 52px）。
    //   方向依据（本条曾被我搞反过一次，务必看这段）：
    //     · 本文件 450-457 行留着 0.3.25 的实机对照 —— 面板锚点贴在**机器界面左下角**
    //       ⇒ "界面越矮 ⇒ 锚点越高" ⇒ **把界面做高才是把锚点压低的杠杆**；
    //       （惰性机 UI=180 的归档页正常、本机 UI=172 越界，就是这条的实证）
    //     · 0.3.33 实机实测（13 页签）：面板Y 分别为 −27 / −12 / −1，页眉（面板顶之上那 24px）随之出屏或可见
    //       ⇒ 要给页眉腾出 ~27px。加高 52px 即使按"居中⇒效果减半"的最保守口径也够（+26px）✓
    //   代价：主界面变高（屏幕上仍是居中，224 < 360 ✓），**槽位数量、页数、页签数都不变** ✓
    // ★ 0.3.35：**还原成 -8（uiHeight 172）** —— 0.3.34 的"加高 52px"只是绕路，
    //   真正的修复已由客户端 mixin 承担（{@code com.gtlmodular.mixin.ConfiguratorPanelTabMixin}：
    //   给 GTCEu 的 {@code ConfiguratorPanel$Tab.expand()} 补上它缺的"上越界夹取"）
    //   ⇒ 主界面回到原样、页眉照样完整落在屏幕内。上面 0.3.34 那段记录保留，
    //   作为"为什么靠主界面高度也能顶动它"的存档（同一条链的另一个杠杆）。
    private static final int UI_HEIGHT_EXTRA = -8;

    /** 页数对应的等级范围（MV ~ UIV）。 */
    private static final int MIN_TIER = 2;
    private static final int MAX_TIER = 11;

    /**
     * 页数上限（队长 2026-09-25 裁决：「样板总成最多有 5 页就够了」）。
     *
     * <p>原设计 EV 往后每升一级多一页、超级版 16 页；建槽是父类构造器里按
     * {@code maxPatternCount} 一次性批量创建（每槽 7 个缓存集合），页数越多、加载峰值越大，
     * 因此统一封顶到 5 页 = {@code 5 × 128 = 640} 槽。
     */
    public static final int MAX_PAGES = 5;   // 0.3.81：容量页数回到历史值 5（5×128=640 ✓ 与存档一致 ✓ 绝不缩容 ✗）UI 页数由 slots/PER_PAGE 算 ⇒ 4 页 ✓

    /** 神锻样板模式倍率范围（1~30，默认 15）。 */
    public static final int MIN_FOA_MULTIPLIER = 1;
    public static final int MAX_FOA_MULTIPLIER = 30;
    public static final int DEFAULT_FOA_MULTIPLIER = 15;

    /** 样板倍率范围（1~100）。 */
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

    /** 自动模式轮询间隔（tick）。 */
    private static final int AUTO_SWITCH_INTERVAL_TICKS = 20;
    /** 切过去后等它开跑的时间（tick），超了就把这个类型临时拉黑。 */
    private static final int AUTO_SWITCH_GRACE_TICKS = 40;
    /** 被拉黑的类型多久内不再选（tick）。 */
    private static final int AUTO_SWITCH_BLOCK_TICKS = 200;

    /**
     * ★ 0.3.4（同惰性机 0.3.4 的修法）：当前模式的样板都在、槽里却<b>没料</b>时，要<b>持续</b>这么久
     * 才允许自动切到别的模式（tick）＝ 30 秒。
     *
     * <p>为什么必须有它（队长 2026-09-26 晚实测：「他现在不会自动切换机器模式了」）：
     * 原来第一遍判据是「<b>当前模式只要还有样板就绝不切走</b>」，而最正常的用法就是
     * <b>多种模式的样板常驻仓里</b> ⇒ 那条规则让自动切换<b>永远不触发</b>，与配置项自己写的
     * 「一台机器可以同时挂多种模式的样板、轮流自动跑」直接矛盾。这里改成「<b>有活才不走</b>」，
     * 并留这段空转宽限期兜住「AE 的料刚好还在推送路上」。
     */
    private static final int AUTO_SWITCH_STOCKLESS_TICKS = 600;

    // ==================== ★【① 冷槽重识别】"重置缓存 + 读取缓存"的自愈闭环 ====================
    // 与惰性机（LazyPatternAssemblyPartMachine）的同名机制逐条对齐；两台的开关各自独立，
    // 因为两台的识别缓存与清缓存实现**不是同一个东西**（见 clearSlotRecipeCache 的注释）。

    /**
     * ★ <b>冷槽重识别（"重置缓存"）总开关</b>：{@code true}（<b>默认开启</b>）⇒
     * {@link #selfHealColdSlotCaches()} 生效。{@code false} ⇒ 整条功能一行都不执行，
     * 行为<b>逐字退回</b>加这段代码之前。
     *
     * <p>一行回退（逐字）：把本行结尾的 {@code = true} 改成 {@code = false}。
     */
    public static boolean COLD_SLOT_CACHE_HEAL = true;

    /** 两次重识别之间至少隔这么多 tick（重识别会扫配方表 ⇒ 必须限频）。 */
    private static final int COLD_SLOT_HEAL_INTERVAL_TICKS = 200;
    /** 整机一生最多重识别这么多个槽（同槽最多一次；防"永远识别不出来"的样板无限烧 CPU）。 */
    private static final int COLD_SLOT_HEAL_BUDGET = 64;

    // ==================== 主动写一次自定义名（绕开 Enhancedcore 自动命名白名单）====================

    /**
     * ★ 2026-10-04：<b>GTL-Enhancedcore 自动命名词典的硬编码兜底串</b>「未命名加工模式」。
     *
     * <p><b>出处（逐条可核）</b>：作者源码
     * {@code com.gtl.enhancedcore.common.recipe.iv.SuperBufferChineseNames:13}
     * —— {@code private static final String FALLBACK = "\u672a\u547d\u540d\u52a0\u5de5\u6a21\u5f0f";}
     * 源码里就是**Unicode 转义**（所以用中文字面 grep 搜不到它）；{@code :21-26} 的
     * {@code resolve(keyOrName)} 只有在「词典里查不到 **且** 传入值不含汉字」时才返回它。
     *
     * <p><b>为什么会落到我们头上</b>：{@code :36-72} 的 {@code load()} 只扫**固定 5 个 mod 的 jar**
     * （{@code gtceu / gtmthings / gtlcore / gtladditions / gtl_enhancedcore}，关键在 {@code :40}），
     * <b>不含 gtlmodular</b> ⇒ 我们的键 {@code gtlmodular.nanochip_assembly_complex}
     * （= {@code NanochipRecipeTypes.ASSEMBLY_COMPLEX.registryName.toLanguageKey()}，
     * 语言文件 {@code assets/gtlmodular/lang/zh_cn.json:415}）**从不进它的词典** ⇒
     * {@code SuperBufferNaming.targetKey()} 递进来的键被 {@code resolve} 判成兜底 ⇒
     * 部件自定义名被写成这四个字。
     *
     * <p>本类要用它做一件事：<b>把"上游写的兜底串"识别成"这个名字不承载玩家意志"</b>，
     * 从而允许我们自己改写它。判定用 {@code String.equals}，**不看是否含汉字**（不猜测、不模糊匹配）。
     */
    private static final String AUTO_NAME_FALLBACK_UPSTREAM = "\u672a\u547d\u540d\u52a0\u5de5\u6a21\u5f0f";

    /**
     * ★ 本补丁的<b>总开关</b>（默认开）。置 {@code false} ⇒
     * ① 不再把上游兜底串当成"可改写"，② 不再主动抢名称 ⇒ <b>完全恢复本补丁之前的行为</b>。
     *
     * <p><b>为什么要这个开关（理由与出处）</b>：本补丁是在**不改上游、不混入、不加 mixin** 的前提下，
     * 用一个**副作用**来修正上游的一个**白名单缺陷** ——
     * 依据 {@code com.gtl.enhancedcore.mixin.gtlcore.SuperBufferNamePersistenceMixin:54-66}：
     * 任何外部 {@code setCustomName(...)} 都会在 TAIL 被标成 {@code enhanced$manualName = true;}
     * {@code enhanced$automaticName = "";}，而 {@code SuperBufferAutoName.java:47}
     * （{@code if (manual || ...) return false;}）让自动命名对 manual <b>永久让位</b>。
     * 也就是说：<b>我们写一次名字，就等价于告诉 Enhancedcore "这台总成是手动命名的"</b>。
     * 这正是我们要的效果（我们的类型名 Enhancedcore 永远认不出来，必须由我们自己写），
     * 但它确实改变了上游对这台机器的行为 ⇒ 必须留一个"一键回到原样"的开关，方便对照与排障。
     *
     * <p><b>为什么用 {@code public static} 而不是 Forge 配置项</b>：任务边界要求
     * "只改这两个部件类、不扩大改动面"（{@code GTLModularConfig} 是另一个文件），
     * 且这个开关是**诊断/回退**用途（重启即生效、不需要存档迁移）。
     *
     * <p><b>★ 2026-10-04b：本开关已置 {@code true}（启用）</b> —— 这不是可选装饰：
     * 上游 Enhancedcore 的命名白名单（{@code SuperBufferChineseNames} 里那个 mod 名数组）
     * 永远认不出 {@code gtlmodular} 的类型名，所以"我们自己写一次名字"是唯一能让总成显示
     * 中文模式名的路子（写一次 ⇒ 上游把它标 manual ⇒ 上游自动命名永久让位）。
     * 关掉它 ⇒ 名字只能由上游给 ⇒ 必然显示「未命名加工模式」✗。
     */
    public static boolean enableActiveCustomNaming = true;

    /** 自带同步字段挂在 gtlcore 那个 holder 之上。 */
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(MoltenPatternAssemblyPartMachine.class,
                    MEPatternBufferPartMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /** 机器电压等级（MV=2 ... UIV=11）。 */
    private final int tier;

    /** 分页样板槽（gtlcore 的公开实现）。 */
    private final PaginationUIManager paginationUIManager;

    // ---------- 配方模式 ----------

    /** 手动选择的配方模式（自动识别失败时的兜底；只有一种模式时无意义）。 */
    @DescSynced
    private int activeRecipeTypeIndex = 0;

    /**
     * ★ 0.3.2 修复（<b>同惰性机 0.3.2 的修法</b>；队长实测 bug：「调 2 号模式去指定，结果还是指定成第 1 个机器模式」）：
     * <b>模式页游标</b> —— 玩家在模式页上「◀ / ▶ 选中的那一个模式」，与机器的实际模式<b>解耦</b>。
     *
     * <p><b>为什么必须有它</b>：{@link #activeRecipeTypeIndex} 在 {@link #autoSwitchTick()} 里是
     * 「只读跟随控制器」的（每 tick 被改写成 {@code controller.getActiveRecipeType()}），而 LDLib 的控件点击
     * 与方块实体 tick 在<b>同一个服务端 tick</b> 内先处理点击、后跑 tick ⇒ 玩家点完 ◀/▶ 的下一刻就被改回主机的
     * 模式 ⇒ {@link #assignManualType(int, boolean)} 读到的一直是<b>主机当前模式</b>（比如切割机），
     * 玩家选的车床被吃掉了。
     *
     * <p>游标与「机器实际模式」是两个概念（模式页的用途本来就是「<b>给这个槽指定哪种模式</b>」，
     * 见 {@code gtlmodular.pattern_assembly.mode.tooltip}：手动模式只在自动识别不出来时兜底），
     * 所以这里<b>不</b>把游标写回控制器（保持与现役机「点按钮不打断正在跑的配方」的纪律一致），
     * 而是让模式页显示的就是这个游标值。
     */
    @DescSynced
    private int modeCursorIndex = 0;

    /** 玩家是否亲手拨过模式页。没拨过之前游标跟随主机当前模式（打开界面时默认值就是「机器现在的模式」）。 */
    private boolean modeCursorTouched = false;

    /**
     * ★ <b>2026-10-14（本轮：绑定模式持久化）</b>：<b>本总成负责的模式</b>的<b>类型 id</b>
     * （{@code GTRecipeType.registryName}，形如 {@code gtceu:lathe}）。{@code null} = <b>未绑定</b>。
     *
     * <p><b>为什么必须有它</b>：{@link #modeCursorIndex} 只带 {@code @DescSynced}、<b>没有 {@code @Persisted}</b>，
     * 也不在 {@link #saveCustomPersistedData} 的任何键里 ⇒ 读档后 {@code modeCursorIndex = 0}、
     * {@code modeCursorTouched = false} ⇒ {@link #autoSwitchTickInner()} 的「游标跟随控制器」立刻把它改回
     * 主机模式 ⇒ <b>「一次绑定」重启后失效</b>。本字段 + {@code gtlmodular$bound_mode_type} 键就是修这一条。
     *
     * <p><b>为什么存类型 id 而不是下标</b>：类型表由控制器提供（{@link #getAvailableRecipeTypes()}），
     * 顺序与长度随控制器/档位/模组增删模式变化 ⇒ 存下标读档后会<b>指到别的模式</b>；存 id 则
     * "认得出就恢复、认不出就当未绑定"，与既有的 {@code gtlmodular$manual_slot_types}（同样存 id，
     * 见 {@link #manualTypeIndexOf(int, List)}）同口径 ✓。
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

    // ---------- 主动写一次自定义名（状态；见 AUTO_NAME_FALLBACK_UPSTREAM 的说明）----------

    // ---------- ★ 2026-10-15：名字按「本总成 + 模式」记录（取代旧的"白名单"判据）----------

    /**
     * <b>模式类型 id → 本总成给该模式起的名字</b>（例 {@code "gtceu:circuit_assembler"} → {@code "电路组装"}）。
     *
     * <p><b>这就是"重进游戏名字被改回去"的根治点</b>：名字跟着<b>模式</b>走，而且<b>落盘</b>
     * （见 {@link #saveCustomPersistedData} 的 {@code gtlmodular$mode_names}）⇒ 读档读得回来，
     * 不再依赖任何"内存态 + 猜这名字是谁写的"的判据 ✗。
     *
     * <p><b>为什么存 id 不存下标</b>：类型表的顺序/长度随控制器与配置变化，存下标读档后会指到别的模式
     * （与既有 {@code gtlmodular$bound_mode_type} 同口径 ✓）。
     *
     * <p><b>为什么是"每台总成各记一份"，而不是"控制器 + 模式"共享一份</b>：
     * ① 用户口径是「两台各叫各的」——同一控制器下两台总成各自改名互不影响；
     * ② 控制器不归本模组所有，写不进它的存档 ⇒ 只能记在本总成自己的存档里；
     * ③ 与既有的 {@code gtlmodular$bound_mode_type}（也是每台总成自己一份）同口径 ✓。
     */
    private final java.util.Map<String, String> modeNames = new java.util.LinkedHashMap<>();

    /**
     * <b>玩家亲手改过的名字</b>（{@code ""} = 没改过）。非空 ⇒ 本总成的名字<b>永久归玩家</b>：
     * 换模式、改绑定、主机切模式、重进游戏、拆装重成型都<b>不再改写</b> ✗。
     * <b>落盘</b>（{@code gtlmodular$locked_name}）。
     *
     * <p>唯一例外：名字被清空时（某些读档/掉落路径会把 customName 丢掉）把它写回来 —— 那正是
     * "重进游戏后玩家改的名字消失了"要防的另一半 ✓。
     */
    private String lockedName = "";

    /** 上一次<b>观察</b>到的 customName（内存态，不落盘）：用来识别"名字被别人改了"（见 syncCustomNameToMode ①）。 */
    private String observedName;

    /** 上一次套用/认领名字时对应的模式 id（内存态，不落盘）：把玩家的改名归到"当时显示的那个模式"名下。 */
    private String appliedModeKey;

    /** 上一次命名时挂着的控制器（内存态，不落盘）：本总成被搬到别的机器上 ⇒ 允许按新模式重新起名一次。 */
    private IRecipeLogicMachine lastNamingController;

    /** 落盘上界：名字表最多存这么多条模式（防坏档 / 防 NBT 无界膨胀；读侧同样上界）。 */
    private static final int MAX_SAVED_MODE_NAMES = 64;

    /** 落盘上界：单条名字最多这么多字符（超过 ⇒ 截断/读侧丢弃，绝不让坏 NBT 变成巨串）。 */
    private static final int MAX_SAVED_NAME_LENGTH = 128;

    /** 每个样板槽识别出来的配方类型下标；-1 = 还没尝试识别，-2 = 试过但识别不出来。服务端算，走存档。 */
    private int[] slotRecipeType;

    // ---------- 自动切换配方模式 ----------

    /** 服务端 tick 订阅（自动模式轮询用）。 */
    private TickableSubscription autoSwitchSubscription;

    /**
     * 成型材质补丁的剩余窗口（服务端）。见 {@link FormedRenderSyncNudge}：
     * 重进世界后「舱室不跟多方块材质连接」是同步/烘焙时序问题，这里在有界窗口内补发同步 + 重绘。
     */
    private int formedNudgeTicks = FormedRenderSyncNudge.WINDOW_TICKS;

    /**
     * 客户端：载入后头若干次同步都补一次重烘焙。
     * <p>取证：这一版 LDLib（1.0.33.b）里 {@code @RequireRerender} 的消费者确实存在
     * （{@code FieldManagedStorage.initEnhancedFeature()}），**但该符号在 ldlib/gtceu/gtlcore 三个 jar 里
     * 除声明外零调用者**（第二次审计复核），而 {@code controllerPositions} 正是带这个注解的字段
     * ⇒ <b>同步到位后没人会自动重烘焙</b>，模型就留在旧材质上（正是「重进世界后不跟机器连接」）。
     * 而 {@code IManaged.onSyncChanged(IRef, boolean)} 是 LDLib 唯一可靠的「客户端刚收到同步」钩子
     * （{@code FieldManagedStorage.lambda$init$1} 会带 {@code isClient=true} 调它；接口默认实现是空方法）。
     */
    private int clientRebakeLeft = 40;

    /** 客户端同步诊断日志额度（有界，避免刷日志；只为定位「名单到底到没到客户端」）。 */
    private int clientSyncDiagLeft = 6;

    /** 轮询间隔计数（别每 tick 都扫槽位）。 */
    private int autoSwitchCooldown;

    /** 上一次自动切过去的类型；-1 = 没有待观察的切换。 */
    private int pendingAutoSwitchType = -1;

    /** 待观察窗口的到期时间（gameTime）。 */
    private long pendingAutoSwitchDeadline;

    /** 「切过去但没跑起来」的类型 → 拉黑到期时间（gameTime），防止两个模式来回横跳。 */
    private final Map<Integer, Long> autoSwitchBlockedUntil = new java.util.HashMap<>();

    /** 上一次打印过的自动切模式状态摘要：只有当摘要<b>变化</b>时才再打印，避免每 5 秒刷一条。 */
    private String lastAutoSwitchSignature;

    /**
     * ★ 0.3.4（同惰性机 0.3.4）：当前模式「最后一次还有活」的时刻（{@code level.getGameTime()}）；
     * {@code -1} = 现在有活 / 刚清过。见 {@link #findModeWithWaitingStock(List, int, long)} 第一遍的空转宽限期。
     *
     * <p>不落盘、不同步（纯运行期状态）：重启后从 -1 起算，最坏情况只是多等 30 秒，绝不会因此切错。
     */
    private long activeModeBusySince = -1L;

    /** ★ 0.3.4：自动切模式诊断（<b>只给日志用、不参与任何判据</b>）：当前模式的样板数 / 其中有料数 / 其中主机还没配上配方的数。 */
    private int diagActivePatterns;
    private int diagActiveStock;
    private int diagActiveCold;

    /** ★ tick 路径兜底 try/catch 的日志节流（同类异常最多 30 秒一条，绝不刷屏）。 */
    private long lastAutoSwitchWarnTick;

    // ── ★【① 冷槽重识别 · 运行时状态】全部是运行期字段：不 @Persisted、不进 NBT、不进同步包 ──

    /** 下一个允许重识别的 tick（{@code getOffsetTimer()} 口径）；见 {@link #selfHealColdSlotCaches()}。 */
    private long coldSlotHealNextTick;
    /** 已经开始重识别过的槽位（每个槽最多一次；防"永远识别不出来"的样板反复烧 CPU）。 */
    private final Set<Integer> coldSlotHealTried = new java.util.HashSet<>();
    /** 上一次重识别时该槽的样板（换了样板才允许再来一次）；与 {@link #coldSlotHealTried} 成对使用。 */
    private final Map<Integer, ItemStack> coldSlotHealPattern = new java.util.HashMap<>();
    /** 真正把冷槽写热过几次（诊断/取证用；不参与任何判定）。 */
    private int coldSlotHealWarmed;

    /**
     * ★ 片 D（2026-09-27）：样板表标记置位的<b>有界诊断额度</b>（只为判读"哪条通道在捅 AE"，不参与任何判据）。
     *
     * <p>纯运行期状态、<b>不落盘、不同步</b>（不新增任何 @Persisted/@DescSynced 字段 ⇒ 存档格式零变化）。
     * 每次机器加载/读档后从 {@value #PATTERN_SYNC_MARK_LOG_LIMIT} 重新起算，最多这么多条，绝不刷屏。
     */
    private static final int PATTERN_SYNC_MARK_LOG_LIMIT = 8;
    private int patternSyncMarkLogLeft = PATTERN_SYNC_MARK_LOG_LIMIT;

    /**
     * ★ 2026-09-21（队长「这样突然卡住，真的很无奈」）：<b>「料不够 / 被占用」提示的节流与去重</b>。
     * <ul>
     *   <li>{@link #STUCK_LOG_INTERVAL_TICKS} = 30 秒 —— 同类提示最多 30 秒一条
     *       （原来 20 tick 一条，实机刷了 67 条）；</li>
     *   <li>{@link #lastStuckLogTick} 独立于调试开关（{@code lastPushLogTick} 只服务详细推送日志）；</li>
     *   <li>{@link #stuckWarned}：**「可用=0 且没有任何任务在合成它」这种真死锁，每个物品只喊一次**
     *       （有界：超过 {@value #STUCK_WARNED_LIMIT} 条就不再新增，超级总成不能无限长）。</li>
     * </ul>
     */
    private static final int STUCK_LOG_INTERVAL_TICKS = 600;
    private static final int STUCK_WARNED_LIMIT = 64;

    // ================= 惰性建槽（A 档第一步：容量可控 + 按需增长；父类与对外行为不变）=================
    // 依据：_census\惰性建槽_移植评估.md（现状=未移植；A 档=分阶段第一步）。
    // 常量照抄惰性机 LazyPatternAssemblyPartMachine:230/241/244（RESERVE/INITIAL_CAPACITY/MIN_GROW_STEP）。
    // 纪律（照抄惰性机的三条，见 Lazy…:889 / :1012-1047 / :845-849）：
    //   · **只增不减** —— 任何入口都不许缩容（缩容会静默丢样板 ✗）；
    //   · 起步容量**不得小于**「已用最高槽 + RESERVE」⇒ 读档必须回填（否则截断丢样板 ✗）；
    //   · 上界恒为本机既有的 maxPatternCount（gtlcore 具体类的 protected final，按等级 128~640）。
    // 为什么本档只能做"部分惰性"✗：父类必须保持具体类 MEPatternBufferPartMachine
    //   （Enhancedcore 2.9.4 的 IvBufferRegistry.compatible 是 instanceof **该具体类**，
    //    换父类会立刻失去刚打通的 IV 样板仓/超级冶炼炉兼容）⇒ 基类构造期仍会建满
    //    internalInventory[maxPatternCount] ⇒ 本档省的是样板仓那张 ItemStack 表，
    //    省不到"槽对象数组"（那一层要换父类，见评估报告 §四-1）。
    /** 起步容量（= 惰性机 INITIAL_CAPACITY）；低等级主机（MV=128 槽）会被 maxPatternCount 夹住。 */
    private static final int LAZY_INITIAL_CAPACITY = 32;
    /** 单张写入触发的扩容步长（= 惰性机 MIN_GROW_STEP）。 */
    private static final int LAZY_MIN_GROW_STEP = 16;
    /** 预留空位（= 惰性机 RESERVE）。 */
    private static final int LAZY_RESERVE = 2;
    /**
     * ★ 总闸：{@code true} = 启用惰性容量（构造期收到起步容量、写前按需增长）；
     * {@code false} = 完全退回"构造期建满 maxPatternCount"的老行为（**一行回退**，见报告 §验收）。
     */
    private static final boolean LAZY_CAPACITY_ENABLED = false;   // 2026-10-02 崩溃取证：gtceu 的 AEPatternViewSlotWidget 会遍历到 maxPatternCount(640) ⇒ 容量只有 32 时 getStackInSlot(32) 越界（ItemStackTransfer.validateSlotIndex）⇒ 渲染崩溃 ✗。等 B3 阶段让所有读取方按容量夹取后再打开 ✓;
    /** 扩容/回填诊断日志的条数上限（每台机器每次加载；防止 640 槽刷屏）。 */
    private static final int LAZY_CAPACITY_LOG_LIMIT = 4;
    private int lazyCapacityLogCount;

    /** 当前样板仓容量（= 那张 {@code ItemStackTransfer} 的 size）。 */
    private int patternCapacity() {
        ItemStackTransfer inventory = getPatternInventory();
        return inventory == null ? maxPatternCount : inventory.getSlots();
    }

    /**
     * <b>只增不减</b>地保证容量 ≥ {@code needed}，并夹在 {@code [1, maxPatternCount]} 内。
     *
     * @return 是否真的扩大了容量
     */
    private boolean growCapacityFor(int needed) {
        ItemStackTransfer inventory = getPatternInventory();
        if (inventory == null) {
            return false;
        }
        int cap = Math.min(maxPatternCount, Math.max(1, inventory.getSlots()));
        int want = Math.min(maxPatternCount, Math.max(needed, cap + LAZY_MIN_GROW_STEP));
        if (want <= cap) {
            return false;   // 只增不减：不缩、不动
        }
        inventory.setSize(want);
        if (lazyCapacityLogCount < LAZY_CAPACITY_LOG_LIMIT) {
            lazyCapacityLogCount++;
            GTLModular.LOGGER.info("[gtlmodular] 样板仓惰性扩容：{} → {}（需求 {}，硬上限 {}）",
                    cap, want, needed, maxPatternCount);
        }
        return true;
    }

    /** 批量写入前的扩容：一次到位，避免逐槽 +{@value #LAZY_MIN_GROW_STEP} 的抖动（惰性机同款）。 */
    private boolean growCapacityForBatchInsert(int count) {
        return count > 0 && growCapacityFor(count + LAZY_RESERVE);
    }

    /** 写某槽之前的守卫：保证 {@code capacity > slot}（越界交给既有 maxPatternCount 守卫，不掺和）。 */
    private void ensureCapacityForSlot(int slot) {
        if (!LAZY_CAPACITY_ENABLED) {
            return;   // 总闸关闭时不做任何容量动作（容量由 restoreFullCapacity 保证拉满）✓
        }
        if (slot < 0 || slot >= maxPatternCount) {
            return;
        }
        if (slot >= patternCapacity()) {
            growCapacityFor(slot + 1 + LAZY_RESERVE);
        }
    }

    /**
     * ★ <b>把容量拉回硬上限</b>（只增不减 ✓）。
     *
     * <p><b>为什么必须有它（2026-10-02 回归修复，用户实测"AE 里看不到样板总成的样板"）</b>：
     * 0.3.48/0.3.49 的惰性起步运行过 {@code ItemStackTransfer#setSize(32)}，而**容量是随存档落盘的** ✗；
     * 0.3.50 关掉总闸只是"不再收窄"，**并不会**把已经写进存档的 32 还原回来 ✗
     * ⇒ 读档后容量仍是 32 ⇒ 高槽（≥32）的样板既不在 {@code slot2PatternMap} 的重扫范围内、
     * 也不会进 AE 的可用样板表 ⇒ **AE 终端/样板访问终端里看不到这些样板** ✗（用户现象）。
     *
     * <p>所以总闸关闭时，构造期与读档后都必须走这里把容量恢复成 {@code maxPatternCount} ⇒ 存档自愈 ✓。
     *
     * @param phase 只为日志判读（"构造期" / "读档"）
     */
    private void restoreFullCapacity(String phase) {
        try {
            ItemStackTransfer inventory = getPatternInventory();
            if (inventory == null) {
                return;
            }
            int cap = Math.min(maxPatternCount, Math.max(1, inventory.getSlots()));
            if (cap >= maxPatternCount) {
                return;
            }
            // ★ 0.3.79 修复（用户实测：升级后读档样板全消失 ✗）：
            //   gtlcore 样板仓 Inventory.setSize() **会重建内部数组 ⇒ 原内容被丢** ✗
            //   故先取内容 → 改容 → 放回 ✓（一张样板都不丢 ✓）
            net.minecraft.world.item.ItemStack[] saved = new net.minecraft.world.item.ItemStack[cap];
            for (int i = 0; i < cap; i++) {
                try { saved[i] = inventory.getStackInSlot(i).copy(); }
                catch (Throwable ignored) { saved[i] = net.minecraft.world.item.ItemStack.EMPTY; }
            }
            inventory.setSize(maxPatternCount);
            int restored = 0;
            for (int i = 0; i < saved.length; i++) {
                if (saved[i] != null && !saved[i].isEmpty()) {
                    try { inventory.setStackInSlot(i, saved[i]); restored++; }
                    catch (Throwable ignored) { }
                }
            }
            GTLModular.LOGGER.info("[gtlmodular] 容量修复时保住样板 {} 张（槽位 0..{}）", restored, cap - 1);
            GTLModular.LOGGER.info("[gtlmodular] 样板仓容量修复（{}）：{} → {}（硬上限；修复惰性落盘残留，恢复高槽样板对 AE 可见）",
                    phase, cap, maxPatternCount);
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 样板仓容量修复失败（已忽略，按存档容量走）: {}", t.toString());
        }
    }

    /**
     * ★ 2026-10-02（用户实测「猪咪界面里没样板，AE 却显示样板」＋「用剪切工具全部剪切了，AE 里还有」）：
     * 读档把容量补回上限之后，**必须在服务端第一个 tick 再补一次全量重扫 + 通知 AE** ——
     * 因为在 NBT 读档期（{@code loadCustomPersistedData}）机器还没挂到 level / 网络节点上，
     * 那会儿调 {@code refreshAllByProduct()} / 置 {@code needPatternSync} 的通知会丢 ✗。
     * 一次性：在 {@code autoSwitchTickInner()} 里消费即清 ✓
     */
    private boolean archiveRescanAfterLoad = false;

    /** 构造期定起步容量（总闸关掉时把曾落盘的窄容量**补回上限**，见 {@link #restoreFullCapacity}）。 */
    private void initLazyCapacity() {
        if (!LAZY_CAPACITY_ENABLED) {
            restoreFullCapacity("构造期");
            return;
        }
        try {
            ItemStackTransfer inventory = getPatternInventory();
            if (inventory == null) {
                return;
            }
            int start = Math.min(LAZY_INITIAL_CAPACITY, maxPatternCount);
            int before = Math.min(maxPatternCount, Math.max(1, inventory.getSlots()));
            if (before > start) {
                inventory.setSize(start);
                GTLModular.LOGGER.info("[gtlmodular] 样板仓惰性起步：容量 {} → {}（硬上限 {}）",
                        before, start, maxPatternCount);
            }
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 样板仓惰性起步失败（已忽略，按父类容量走）: {}", t.toString());
        }
    }

    /**
     * ★ <b>读档后回填容量</b>（本档最重要的一条正确性纪律）：
     * 扫出「最高已用槽」，把容量一次性拉到 {@code 最高已用槽 + 1 + RESERVE}，
     * 保证起步容量小于存档内容时**绝不截断**已有样板。
     *
     * <p>惰性机同款纪律：{@code LazyPatternAssemblyPartMachine:845-849}、{@code :1100-1123}。
     */
    private void backfillCapacityAfterLoad() {
        if (!LAZY_CAPACITY_ENABLED) {
            // ★ 2026-10-02 回归修复：关闸时**不是**什么都不做 ✗ —— 必须把「惰性落盘残留的窄容量」补回上限，
            //   否则读档后高槽样板对 AE 不可见（用户实测现象）。详见 restoreFullCapacity 的注释 ✓
            restoreFullCapacity("读档");
            // ★ 2026-10-02：容量补回上限还**不够** ✗ —— 高槽样板要重新进 slot2PatternMap，AE 端那张
            //   （可能已经很旧的）样板表也要重读一次，否则就会出现"机器空 / AE 有一堆"的双向不一致 ✗。
            //   读档期通知会丢 ⇒ 置一次性标记，交给服务端第一个 tick 去做 ✓
            archiveRescanAfterLoad = true;
            return;
        }
        try {
            ItemStackTransfer inventory = getPatternInventory();
            if (inventory == null) {
                return;
            }
            int capBefore = Math.min(maxPatternCount, Math.max(1, inventory.getSlots()));
            int highestUsed = -1;
            for (int i = 0; i < capBefore; i++) {
                ItemStack s = inventory.getStackInSlot(i);
                if (s != null && !s.isEmpty()) {
                    highestUsed = i;
                }
            }
            int wanted = highestUsed + 1 + LAZY_RESERVE;
            if (wanted > capBefore) {
                growCapacityFor(wanted);
            }
            GTLModular.LOGGER.info("[gtlmodular] 样板仓容量回填：读档 容量 {} → {}（最高已用槽 {}，硬上限 {}）",
                    capBefore, patternCapacity(), highestUsed, maxPatternCount);
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 样板仓容量回填失败（已忽略，不影响读档）: {}", t.toString());
        }
    }
    private long lastStuckLogTick;
    private final java.util.Set<String> stuckWarned = new java.util.HashSet<>();

    /**
     * ★★ 0.3.132【只加读数 · C1】★★ 上面那条「被占用且没有任何任务在合成它」的 WARN
     * 曾经<b>每天只能喊一次</b>（`stuckWarned` 去重），而后来的证据说明它可能是<b>空窗期</b>而非真死锁
     * ⇒ 现在改成「**只在确定没有 AE 任务时**才永久去重」，其余情况按 `STUCK_LOG_INTERVAL_TICKS` 限流复报。
     * 这样"实际是什么"能重复看到，而不会因为第一次的误判被永久静音。
     *
     * <p>纯诊断性行为调整：只影响这条提示的**频率**，不参与任何配方/并行/扣料判定。
     */
    private static final boolean STUCK_WARN_REPEAT_WHEN_TRACKED = true;

    /**
     * 见 {@link #STUCK_WARN_REPEAT_WHEN_TRACKED}：该物品上一次报「被占用」的**游戏刻**。
     * <p>用来在"AE 仍在追踪这个 key"时按 30 秒限流复报（而不是永久静音）。纯诊断，不进存档/同步包。
     */
    private final java.util.Map<String, Long> stuckWarnedTick = new java.util.HashMap<>();

    /**
     * 诊断用：一行说清"样板几个 / 哪些槽类型已知（含手动指定）/ 哪些槽有料等着"。
     * 判断"为什么不切"只需要看这一行：<br>
     * ① 样板=0 → 仓里根本没样板；② 类型已知=[] → 样板在但识别不出来（产物歧义，需手动指定）；
     * ③ 有料槽=[] → 另一种模式的料没被 AE 推进来（需要虚拟执行链）。
     */
    private String describeAutoSwitchState() {
        ItemStackTransfer inventory = getPatternInventory();
        if (inventory == null) {
            return "样板库存不可用";
        }
        StringBuilder known = new StringBuilder();
        StringBuilder stock = new StringBuilder();
        int patterns = 0;
        int shownKnown = 0;
        int shownStock = 0;
        int slots = Math.min(inventory.getSlots(), slotCount());
        for (int slot = 0; slot < slots; slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                continue;
            }
            patterns++;
            int type = recipeTypeOfSlot(slot);
            if (type >= 0 && shownKnown < 12) {
                if (shownKnown++ > 0) {
                    known.append(' ');
                }
                known.append(slot + 1).append('=').append(type);
            }
            try {
                var internal = getInternalSlot(slot);
                if (internal != null && shownStock < 12
                        && (!internal.getItemStackInputMap().isEmpty() || !internal.getFluidStackInputMap().isEmpty())) {
                    if (shownStock++ > 0) {
                        stock.append(' ');
                    }
                    stock.append(slot + 1);
                }
            } catch (RuntimeException ignored) {
                // 拿不到就跳过
            }
        }
        return "样板=" + patterns + " 类型已知=[" + known + "] 有料槽=[" + stock + "]";
    }

    /**
     * 手动指定的「样板槽位（0 起） → 配方类型 id」。
     * <b>优先于自动识别</b>，用于产物有歧义、自动识别不出来的样板；只在服务端用，走存档。
     * 存 id 而不是下标，这样换控制器 / 类型表顺序变了也不会指错。
     */
    private final Map<Integer, net.minecraft.resources.ResourceLocation> manualSlotTypes = new java.util.HashMap<>();

    /** 手动指定页面上「要给哪个槽位指定」的槽号（1 起）。同步给客户端，右键选槽时数值框也会跟着变。 */
    @DescSynced
    private int manualAssignSlot = 1;

    /** 上面对应槽位当前的手动指定（类型 id；空 = 没指定）。同步给客户端显示中文模式名。 */
    @DescSynced
    private String manualAssignTypeId = "";

    // ---------- 样板倍率 / 神锻（都是「视图倍率」，不动仓里的样板）----------

    /** 倍率：对选中的那一侧 ×patternScale。 */
    @DescSynced
    private int patternScale = 1;

    /** 除率：对选中的那一侧 ÷patternDivScale。 */
    @DescSynced
    private int patternDivScale = 1;

    /** true = 倍率作用在输出侧，false = 作用在输入侧。 */
    @DescSynced
    private boolean isOutputMode = true;

    /** 神锻样板模式开关（两侧一起 ×倍率）。 */
    @DescSynced
    private boolean foaMode = false;

    /** 神锻样板模式倍率。 */
    @DescSynced
    private int foaMultiplier = DEFAULT_FOA_MULTIPLIER;

    /** 改写后的样板缓存（样板或设置变化时失效）。 */
    private List<IPatternDetails> rewrittenPatterns;

    /** 改写后的样板 → 原样板（AE 推回来的是改写过的那个对象，要翻译回原样板才能落到槽位）。 */
    private final IdentityHashMap<IPatternDetails, IPatternDetails> rewrittenToOriginal = new IdentityHashMap<>();

    // ---------- 专属催化剂槽 ----------

    /** 「专属催化剂槽」页当前查看的样板槽位（1 起）。 */
    @DescSynced
    private int catalystViewSlot = 1;

    /** 「专属催化剂槽」页是否展开。 */
    @DescSynced
    private boolean catalystPageOpen = false;

    /** 当前这一份 UI 里的「专属催化剂槽」页（两侧各一份）。 */
    transient CatalystAccessConfigurator activeCatalystPage;

    // ---------- 片 C：样板仓归档（复制 / 剪切 / 粘贴）----------

    /**
     * ★ <b>片 C</b>：归档的落位模式开关。{@code false}（默认）= <b>追加</b>
     * （从本机当前已用最高槽的下一格开始往后延伸、绝不覆盖）；{@code true} = <b>按原槽对齐</b>
     * （按档案里的槽下标落位，目标槽已被占用则<b>跳过</b>）。
     *
     * <p>与惰性机同名同语义同默认值（{@code LazyPatternAssemblyPartMachine#archiveAlignSlots}）：
     * 只带 LDLib 的 {@code @DescSynced}（客户端要显示按钮文字）、<b>不落盘</b>
     * ⇒ 换机器/重开档回到默认"追加"最安全。
     */
    @DescSynced
    private boolean archiveAlignSlots = false;

    /** 归档是否包含设置类（倍率 / 除率 / 神锻 / 输出模式）。默认 <b>false</b>。 */
    @DescSynced
    private boolean archiveIncludeSettings = false;

    /** 归档是否包含机器名字。默认 <b>false</b>。 */
    @DescSynced
    private boolean archiveIncludeName = false;

    /**
     * ★ 片 C：<b>归档批量落盘过的槽</b>（只读兜底用，见 {@link #hasPatternInSlot(int)}）。
     *
     * <p>为什么需要它：gtlcore 具体类的 {@code hasPatternArray} 是 {@code private}，
     * 只在<b>逐槽</b> {@code onPatternChange(int)} / onLoad 异步补齐时被置位；而归档按纪律
     * <b>绝不逐槽调 {@code onPatternChange}</b>（640 槽会逐槽捅 AE = 卡死几秒）
     * ⇒ 这批"新写进去的槽"在 gtlcore 自己的数组里会一直是 {@code false}，
     * 而 {@code hasPatternInSlot} 又是 {@code InternalSlot.isActive()} 的门（配方匹配会看它）
     * ⇒ 不兜这一层，粘进去的样板<b>会跑不起来</b>。
     */
    private final it.unimi.dsi.fastutil.ints.IntOpenHashSet archiveLandedSlots =
            new it.unimi.dsi.fastutil.ints.IntOpenHashSet();

    /**
     * ★ 2026-10-02（用户实测「把样板粘贴回去 ⇒ AE 里仍然看不到」）：归档批量落盘的**重入窗口**守卫。
     *
     * <p><b>为什么需要它</b>：gtlcore 具体类维护<b>两张平行存储</b> ——
     * AE/终端面的 {@code internalPatternInventory}（{@code getTerminalPatternInventory()} 返回的就是它）
     * 与 LDLib 面的 {@code patternInventory}（{@code getPatternInventory()}）。
     * 它自己的 {@code pasteFromTag} / {@code cutToTag} <b>两张都写</b>（字节码实证：
     * {@code _census\_javap_simple_gtlcore_buffer.txt:1162} = paste 写 {@code setItemDirect}、
     * {@code :1582} + {@code :1607} = cut 写 {@code setStackInSlot} + {@code onContentsChanged}）。
     * 我们以前的归档路径<b>只写 LDLib 面</b> ⇒ 机器自己的槽位网格（读 {@code patternInventory}）看得到、
     * 而 AE/终端（读 {@code internalPatternInventory}）看不到 ✗ —— 这就是用户现象。
     *
     * <p>修复做法是把 AE 面也写一遍，而 {@code setItemDirect} 会逐槽回调 {@code onPatternChange(index)}；
     * 本类的覆写在这个回调里做了 {@code invalidateRewriteCache()} 与「样板表过期」置位
     * ⇒ 逐槽调用会退化成 O(n²) 的 AE 重读（片 D 明令禁止）。因此批量窗口内用本标记
     * 把逐槽回调降级为"只做父类记账"（{@code hasPatternArray} / {@code slot2PatternMap} / 该槽缓存），
     * 通知与改写缓存作废统一留给 {@link #afterArchiveBulkChange()} 那一次 ✓
     */
    private boolean archiveBulkPatternWrite = false;

    /** 归档批量写入开始（由 {@link MoltenPatternArchiveTarget} 的容量准备方法在写循环前调一次）。 */
    void beginArchiveBulkWrite() {
        archiveBulkPatternWrite = true;
    }

    public MoltenPatternAssemblyPartMachine(IMachineBlockEntity holder, int tier) {
        this(holder, tier, slotsForTier(tier));
    }

    public MoltenPatternAssemblyPartMachine(IMachineBlockEntity holder, int tier, int slots) {
        // gtlcore 的 ME 样板总成：IO.BOTH = 自带 ME 输出
        super(holder, slots, IO.BOTH);
        this.tier = tier;
        int uiWidth = Math.max(PATTERNS_PER_ROW * 18 + 16, 106);
        // ★ 0.3.25（方案 A）：高度改成**与惰性机同款的公式**（原来是本机独有的固定 172）。
        //   惰性机（LazyPatternAssemblyPartMachine.createUIWidget）：rows = max(1, min(ROWS_PER_PAGE,
        //   ceil(容量/PATTERNS_PER_ROW)))、高度 = rows*18 + 36。本机容量恒 = 页数 × PER_PAGE（≥ 128）
        //   ⇒ ceil(容量/16) ≥ 8 ⇒ rows 恒被夹到 ROWS_PER_PAGE = 10 ⇒ 两条公式在本机上**等价于**
        //   8*18 + 36 = 180（比原来的 ROWS_PER_PAGE*18+28 = 172 高 8px）。
        //   为什么要加高（唯一有实机对照的杠杆）：配置器面板的锚点贴在机器界面左下角（面板 x 恒 -26 ✓；
        //   y 的口径来自我们自己的旧记录 `NanochipVacuumHatchMachine:1282-1285`「new ConfiguratorPanel(-26,
        //   UI高度)」——**本机没有 FancyMachineUIWidget 构造器字节码，这一条未证实 ✗**，先用它当假设），
        //   也就是说"界面越矮 ⇒ 锚点越高"；
        //   而 GTCEu 的 ConfiguratorPanel$Tab.expand() **只夹 X 左越界与 Y 下越界、没有上越界分支**
        //   （gtl-dev/api-dump/configurator_tab.javap.txt:26-181）⇒ 自有代码里唯一能"把锚点压低"的杠杆
        //   就是把界面做高。队长实测对照：惰性机（180）归档页正常、本机（172）越界；原版 gtlcore 机器
        //   同样越界且我们改不动它 ⇒ 全模组级的根治只能走 mixin（方案 B，设计见报告）。
        //   UI_HEIGHT_EXTRA 是"加高多少"的旋钮：0 = 与惰性机同高；不够就 8/16/26。
        // ★ 0.3.82 修复（崩溃取证 ✓）：`Slot 128 not in valid range - [0,128)` ✗
        //   gtceu 的 AEPatternViewSlotWidget 会**按格数逐个画** ⇒ 若 UI 排的格子数 > 样板仓容量 ✗
        //   （MV 容量 128，而每页 10 行 = 160 格 ✗）⇒ 画到第 128 号槽就崩 ✓
        //   ⇒ 每页行数必须按**本机容量**夹取 ✓（MV → 8 行 = 128 ✓；超级 → 仍 10 行 = 160 ✓）
        int capRows = Math.max(1, Math.min(ROWS_PER_PAGE, Math.max(1, slots / PATTERNS_PER_ROW)));
        int perPage = PATTERNS_PER_ROW * capRows;
        int uiHeight = capRows * 18 + 36 + UI_HEIGHT_EXTRA;
        // ★ 0.3.48（惰性 A 档第一步）：构造期把样板仓容量收到起步容量，之后按需增长。
        //   放在建 PaginationUIManager 之前 ⇒ 管理器拿到的是"已经收过的"那张表（同一对象引用，setSize 原地改 ✓）。
        initLazyCapacity();
        this.paginationUIManager = new PaginationUIManager(PATTERNS_PER_ROW, capRows, Math.max(1, slots / perPage), uiWidth, uiHeight,
                this::onPatternChange, i -> i >= 0 && i < cacheRecipe.length && cacheRecipe[i],
                getPatternInventory());
    }

    public int getMachineTier() {
        return tier;
    }

    /**
     * 等级 → 页数：MV = 1 页、HV = 2 页、EV = 4 页，
     * <b>EV 以上统一封顶到 {@value #MAX_PAGES} 页</b>
     * （原设计是 IV = 5、LuV = 6 … UIV = 11，现按队长裁决一律不超过 5 页）。
     */
    public static int pagesForTier(int tier) {
        int t = Math.max(MIN_TIER, Math.min(MAX_TIER, tier));
        int pages = (t <= 3) ? (t - 1) : (4 + (t - 4));
        return Math.min(MAX_PAGES, pages);
    }

    /** 等级 → 样板槽数 = 页数 × {@value #PER_PAGE}。 */
    public static int slotsForTier(int tier) {
        return pagesForTier(tier) * CAPACITY_PER_PAGE;
    }

    // ================= 生命周期 =================

    @Override
    public void onLoad() {
        super.onLoad();
        // 舱室在多方块里要能任意面接 ME 线
        getMainNode().setExposedOnSides(EnumSet.allOf(Direction.class));
        // ★★ 2026-09-26（队长：「我们的现役机也有这种问题，很诡异」）★★
        // 基类那条「needPatternSync → ICraftingProvider.requestUpdate」的 tick **只在 `onMainNodeStateChanged`
        // 里才被挂上**（javap 实证：`updateSubscription()` 全类只有一个调用点，就在那个回调里，条件是
        // `getMainNode().isOnline()`）。节点状态**变化**才会触发那个回调 —— 若节点在监听器挂上之前就已经在线
        // （读档/区块重载/主机重成形都可能这样），`updateSubs` 就永远是 null ⇒ 样板变化时只是把
        // `needPatternSync` 置位、**没有任何人去消费它** ⇒ AE 的合成服务一直拿着旧样板表
        // （撤掉/换掉样板后 AE 还认为能做，下单不知去向）。现役机/超级机/gtlcore 自家样板仓走的是同一条基类链，
        // 所以队长在现役机上也看到了同样的现象。这里补一次订阅（幂等：`updateSubscription()` 内部自己判空 + 判在线）。
        updateSubscription();
        // 自动切换配方模式：只在服务端跑
        this.autoSwitchSubscription = subscribeServerTick(this::autoSwitchTick);
    }

    /**
     * 客户端「刚收到同步字段」的钩子（{@code IManaged} 默认实现是空的，这里不调 super 也不会挡掉任何逻辑
     * —— gtlcore 这一条继承链上没有任何类覆写它，已用 findstr 全树确认）。
     *
     * <p>做两件事：
     * <ol>
     *   <li><b>补一次重烘焙</b>：{@code @RequireRerender} 在本版 LDLib 有消费者却无人调用（见字段注释），同步到位后没人重烘焙模型；
     *       这里在「载入后的初始同步风暴」与「{@code controllerPositions} 变化」两个时刻各补一次，
     *       保证「谁是我的控制器」这份名单一到，模型立刻按成形外观重烤；</li>
     *   <li><b>有界诊断</b>：把客户端看到的控制器数量打出来（≤6 行/机器/次加载），
     *       用来区分「名单没到客户端」与「名单到了但没重烤」。</li>
     * </ol>
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

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        getMainNode().setExposedOnSides(EnumSet.allOf(Direction.class));
    }

    @Override
    public void onMachineRemoved() {
        super.onMachineRemoved();
        getMainNode().destroy();
    }

    /**
     * 闪存（数据闪存）连镜像总成：<b>不需要我们做任何事</b> —— gtlcore 的
     * {@code MEPatternBufferPartMachineBase#onUse} 已经把本机坐标写进闪存的 {@code pos} 键，
     * 镜像总成（{@code MEPatternBufferProxyPartMachine#onUse}）读的也是 {@code pos}。
     *
     * <p>之前的「闪存连不上镜像总成」是 GTLsupb 的锅：它覆写 onUse 后写的是 {@code superPos}，
     * 还顺手把 {@code pos} 删了，而代理只认 {@code pos}。现在不继承它、也不覆写 onUse，
     * 走 gtlcore 原生那条路，闪存就能正常连线了。
     */

    // ================= 配方类型 =================

    /** getAvailableRecipeTypes 的缓存（控制器与它返回的数组都没变就复用）：它在闸门/自动切模式热路径上被反复调用。 */
    private IRecipeLogicMachine cachedTypesController;
    private GTRecipeType[] cachedTypesSource;
    private List<GTRecipeType> cachedTypes = List.of();

    /** 控制器暴露的全部配方类型（没有控制器时为空）。 */
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
        cachedTypes = types == null ? List.of() : Arrays.asList(types);
        return cachedTypes;
    }

    private IRecipeLogicMachine firstRecipeLogicController() {
        for (var controller : getControllers()) {
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

    public GTRecipeType getActiveRecipeType() {
        List<GTRecipeType> types = getAvailableRecipeTypes();
        if (activeRecipeTypeIndex >= 0 && activeRecipeTypeIndex < types.size()) {
            return types.get(activeRecipeTypeIndex);
        }
        return types.isEmpty() ? null : types.get(0);
    }

    /**
     * 手动切换模式（界面上的 ◀ / ▶）。
     *
     * <p>★ 0.3.2 起改的是<b>模式页游标</b> {@link #modeCursorIndex}（{@link #assignManualType(int, boolean)}
     * 与模式页显示读的就是它），<b>不动控制器、也不写 {@link #activeRecipeTypeIndex}</b>：
     * 真正的机器模式切换由 {@link #lockRecipeTypeForSlot(int)}（推送时）与 {@link #autoSwitchTick()}
     * （自动切换）去做，避免玩家点一下按钮就把正在跑的配方打断（原有纪律不变）。
     *
     * <p>原来的写法是 {@code activeRecipeTypeIndex = 下一个}，但那个字段每 tick 被「跟随控制器」改写
     * ⇒ 玩家看到车床、指定下去的却是切割机（队长实测的 bug）。游标不受那条跟随影响，问题即消失。
     */
    public void cycleRecipeType(boolean forward) {
        List<GTRecipeType> types = getAvailableRecipeTypes();
        if (types.isEmpty()) {
            return;
        }
        int size = types.size();
        // 游标越界（换控制器导致类型表变短）时先从机器实际模式起算，避免拨出界
        int base = modeCursorIndex >= 0 && modeCursorIndex < size ? modeCursorIndex : activeRecipeTypeIndex;
        setRecipeType(forward ? (base + 1) % size : (base - 1 + size) % size);
    }

    public void setRecipeType(int index) {
        if (index >= 0 && index < getAvailableRecipeTypes().size()) {
            this.modeCursorIndex = index;
            this.modeCursorTouched = true;   // 玩家亲手拨过 ⇒ 游标归玩家管，不再被"跟随控制器"改写
            rememberBoundModeType();         // ★ 绑定模式持久化：把"本总成负责的模式"记成类型 id（落盘用）
        }
    }

    /** 模式页游标（要「指定」下去的那个模式）。客户端标签读它。 */
    public int getModeCursorIndex() {
        return modeCursorIndex;
    }

    /**
     * ★ 0.3.51：**只写本地**的模式游标 —— 供界面芯片在客户端点击时做"立刻跟手"的视觉反馈。
     *
     * <p>为什么必须有它（取证）：{@link #modeCursorIndex} 是 {@code @DescSynced}（:224）⇒ 服务端权威；
     * 而 LDLib 的按钮回调在**客户端与服务端各跑一次**（{@code ldd_button.javap.txt:120-171}）⇒
     * 客户端那一次不能走 {@link #setRecipeType(int)} 的正常路径去"等同步"（点了要等一个来回才动 ✗），
     * 但直接改字段又会因为没置 {@code modeCursorTouched} 而被"跟随控制器"改回去（:1681 ✗）。
     * 所以这里把两件事一起做掉：本地游标 + 本地"玩家拨过"标记，且**不发包、不碰服务端** ✓。
     * 服务端的权威更新由同一次点击的**服务端回调**（{@link ModeScaleConfigurator} 里的 {@code selectMode}）
     * 走 {@link #setRecipeType(int)} 完成 ✓。
     */
    public void applyModeCursorLocally(int index) {
        if (index >= 0 && index < getAvailableRecipeTypes().size()) {
            this.modeCursorIndex = index;
            this.modeCursorTouched = true;
        }
    }

    // ---------- 绑定模式持久化（gtlmodular$bound_mode_type）----------

    /**
     * ★ <b>绑定模式持久化</b>：把"本总成负责的模式"（模式页游标对应的 {@code GTRecipeType}）
     * 的类型 id 记进 {@link #boundModeType} —— 它就是 {@link #saveCustomPersistedData} 写进
     * {@code gtlmodular$bound_mode_type} 的那份影子副本。
     *
     * <p><b>调用点</b>（都是"玩家选定模式"那一步，绝不每 tick 调）：
     * {@link #setRecipeType(int)}（模式页 ◀/▶ 与芯片直选的服务端落点）与
     * {@link #assignManualType(int, boolean)}（点"指定"时顺带刷新一次）。
     *
     * <p><b>只在"玩家真的拨过"时才算绑定</b>（{@code modeCursorTouched}）：没拨过时游标是"跟随控制器"的
     * 镜像 ⇒ 那不是绑定，不记也不写键（保持老档的"缺键 = 跟随控制器"语义）✓。
     *
     * <p><b>只在服务端记</b>（{@code isRemote()} 直接 return）：这个键是<b>服务端持久化</b>的，
     * 客户端那份 BE 只做显示（模式页读 {@code @DescSynced modeCursorIndex}），不需要也不应该参与落盘；
     * LDLib 的控件点击回调**两侧都会跑**（本类既有取证：{@code ldd_button.javap.txt:120-171}），
     * 这条守卫同时把客户端那次挡在门外 ✓（客户端"立刻跟手"走既有的
     * {@link #applyModeCursorLocally(int)}，那条路刻意不记绑定）。
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
     * 判定点：{@link #autoSwitchTickInner()} 里那一行
     * {@code if (!this.modeCursorTouched && this.modeCursorIndex != live) { this.modeCursorIndex = live; }}
     * ⇒ 置真之后"游标跟随控制器"这条每 tick 的改写<b>绝不会</b>再把玩家绑定的模式改回主机模式 ✓；
     * 另一处同判据在自动切换收尾（{@code if (!this.modeCursorTouched) this.modeCursorIndex = target;}）✓。
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

    // ================= 逐槽配方模式（不串配方的核心）=================

    @Override
    protected void onPatternChange(int index) {
        // ★ 0.3.48（惰性 A 档第一步）：**先**保证这个槽在容量之内，再让父类干活。
        //   这是"写前增长"的兜底入口：界面放/取/换样板（InternalSlot.setOnContentsChanged → 这里）、
        //   AE 推送落地都会经过它；先增长可避免父类在该下标上读写越界。
        ensureCapacityForSlot(index);
        super.onPatternChange(index);
        refreshSlotPatternMirror(index);   // ★ A 层：记录该槽样板身份（只建表 ✓ 不拦截 ✓）
        // ★ 2026-10-02：归档批量窗口内（AE 面 setItemDirect 的逐槽回调）只做"父类记账"，
        //   跳过改写缓存作废与「样板表过期」置位 —— 那两件事由 afterArchiveBulkChange() 合并成一次 ✓
        //   目的：既让父类把 hasPatternArray / slot2PatternMap / 该槽缓存更新对（这是 AE 面写入的副作用），
        //   又不产生 640 次 AE 重读（片 D 纪律）✓
        if (archiveBulkPatternWrite) {
            if (slotRecipeType != null && index >= 0 && index < slotRecipeType.length) {
                slotRecipeType[index] = -1;
            }
            return;
        }
        // 槽里的样板换了 → 重新识别它的配方类型，并让换算缓存失效
        if (slotRecipeType != null && index >= 0 && index < slotRecipeType.length) {
            slotRecipeType[index] = -1;
        }
        invalidateRewriteCache();
        // ★★ 片 D（2026-09-27）：**样板增删要主动把「样板表过期」标记置起来** ★★
        //   现场取证（逐指令对过 `_census/_gto_wildcard/javap_gtlcore/GTL_Buffer_full.txt`）：
        //   gtlcore 具体类的 `needPatternSync = true` 全类**只有 3 处** ——
        //     ① `refreshAllByProduct()` 偏移 60–66（读档补齐 / 批量粘贴的收尾）；
        //     ② `lambda$onLoad$6()` 偏移 58–64（onLoad 异步补齐）；
        //     ③ `onMachineRemoved()` 偏移 157–163（拆机）。
        //   ★ 而 `onPatternChange(int)`（:279–364）**根本不置这个标记** —— 它只做
        //     「刷 slot2PatternMap + hasPatternArray + 清该槽缓存 + 退残留料 + reCalculatePatternSlotMap」。
        //   于是「玩家在界面上放/取/换一张样板」这条路（`InternalSlot.setOnContentsChanged`(:235)
        //   → `onPatternChange`）**从来没人捅 AE** ⇒ 合成服务的可用样板表要等很久（甚至要重开界面）。
        //   这里补的**就是这一句**：只置标记，**绝不**在"每个槽变化一次"的路径上直接
        //   `refreshPatternsNow()` —— 批量粘贴 640 张会变成 640 次"AE 同步重读全部样板"= O(n²) 卡死
        //   （惰性机 0.3.5 踩过，见 `LazyPatternAssemblyPartMachine.java:3498–3500` 的同款纪律）。
        //   真正的消费只有一个地方：每 tick 的 `autoSwitchTickInner()`（约 :1265，合并成一次）。
        //   `needPatternSync` 是基类 `MEPatternBufferPartMachineBase` 的 **protected** 字段
        //   （`_census/_gto_wildcard/javap_gtlcore/GTL_MEPatternBufferPartMachineBase.sig.txt:11`
        //    + `_census/_javap_simple_gtlcore_bufferbase.txt:21`）⇒ 子类直接读写，**没有新造第二套机制**。
        markPatternTableStale("界面/槽变化#" + (index + 1));
    }

    /**
     * 片 D 的「样板表过期」置位入口：<b>只置标记，不做任何重活</b>。
     *
     * <p>纪律（与惰性机 0.3.5 完全一致）：
     * <ul>
     *   <li><b>不</b>直接调 {@link #refreshPatternsNow()} —— 那是"AE 同步重读全部样板"，
     *       在逐槽路径上调用会退化成 O(n²)（批量粘贴 640 张 = 卡死几秒）；</li>
     *   <li>只置 {@code needPatternSync}，由**每 tick 的既有消费点**（{@code autoSwitchTickInner()} 里
     *       `if (needPatternSync) { needPatternSync = false; refreshPatternsNow(); }`）合并成一次；</li>
     *   <li>置位路径一律 {@code try/catch(Throwable)} —— 这是界面/网络/tick 三处都会走到的地方，
     *       绝不允许把异常抛出去；</li>
     *   <li>诊断日志<b>有界</b>（{@code PATTERN_SYNC_MARK_LOG_LIMIT} 条 / 每次加载），默认只打前几条，
     *       用来判读"到底哪条通道在干活"，绝不刷屏。</li>
     * </ul>
     *
     * @param source 只为日志判读用的来源标签（不参与任何判据、不入档）
     */
    private void markPatternTableStale(String source) {
        try {
            // 与 refreshPatternsNow() 同一道门：客户端不该置这个标记（样板表是服务端的事；消费点在服务端 tick）。
            // 这不是为了正确性（多置一次也无害），而是为了让"谁在动样板表"这件事在客户端侧保持零副作用。
            if (isRemote()) {
                return;
            }
            this.needPatternSync = true;
            if (this.patternSyncMarkLogLeft > 0) {
                this.patternSyncMarkLogLeft--;
                GTLModular.LOGGER.info("[gtlmodular] 样板表标记置位（片D）: @{} {} 来源={} 剩余诊断额度={}",
                        getLevel() == null ? "?" : getLevel().dimension().location(), getPos(), source,
                        this.patternSyncMarkLogLeft);
            }
        } catch (Throwable ignored) {
            // 界面 / 网络 / tick 路径都不许抛异常出去；标记没置上下一 tick 由别的入口补
        }
    }

    private int slotCount() {
        ItemStackTransfer inventory = getPatternInventory();
        return inventory == null ? 0 : inventory.getSlots();
    }

    // ================= A 层（甲案）：样板身份 → 槽 的映射 =================
    // 见 _census/严格逐槽隔离_方案.md。本层只建表、**不做拦截** ✓（拦截在 B 层）
    private final java.util.Map<Integer, appeng.api.crafting.IPatternDetails> slotPatternMirror = new java.util.HashMap<>();

    /** 记下某槽当前的样板身份（槽空/解不出 ⇒ 移除 ✓）。只读不改 ✓。 */
    private void refreshSlotPatternMirror(int index) {
        try {
            if (index < 0) {
                return;
            }
            net.minecraft.world.item.ItemStack stack = null;
            var inv = getPatternInventory();
            if (inv != null && index < inv.getSlots()) {
                stack = inv.getStackInSlot(index);
            }
            if (stack == null || stack.isEmpty()) {
                slotPatternMirror.remove(index);
                return;
            }
            net.minecraft.world.level.Level lv = getLevel();
            appeng.api.crafting.IPatternDetails decoded = PatternDetailsHelper.decodePattern(stack, lv);
            if (decoded == null) {
                slotPatternMirror.remove(index);
            } else {
                slotPatternMirror.put(index, decoded);
            }
        } catch (Throwable ignored) {
            // 建表失败绝不影响功能 ✓
        }
    }

    /** 这条配方属于哪个槽？不属于任何已记录槽 ⇒ -1（B 层用它判身份 ✗）。 */
    public int slotOwning(com.gregtechceu.gtceu.api.recipe.GTRecipe recipe) {
        if (recipe == null) {
            return -1;
        }
        for (java.util.Map.Entry<Integer, appeng.api.crafting.IPatternDetails> e : slotPatternMirror.entrySet()) {
            try {
                if (patternIdentityMatches(recipe, e.getValue())) {
                    return e.getKey();
                }
            } catch (Throwable ignored) {
                // 单条比对失败不影响其它槽 ✓
            }
        }
        return -1;
    }

    /** B 层用：当前"参与匹配"的槽集合。现在返回**全部有样板的槽** = 现状 ✓ 行为不变 ✓ */
    public int[] activePatternSlotsForIsolation() {
        try {
            return slotPatternMirror.keySet().stream().mapToInt(Integer::intValue).toArray();
        } catch (Throwable ignored) {
            return new int[0];
        }
    }
    // ================= 部件外观：成型后融入多方块机壳（开关默认关 ✓）=================
    /**
     * true = 装上多方块并成型后显示成**控制器外壳方块**（融进机壳 ✓）；
     * false = 保持自带外壳贴图（默认 ✓ 便于找到这台部件 ✓）。
     * <p>走 GTCEu 既有钩子 {@code IAppearance#getBlockAppearance}（MetaMachine 已实现 ✓），
     * 不新增注册项、不需要 mixin ✓。签名与 GTCEu 1.4.4 逐字一致（javap 实证 ✓）。
     */
    public static boolean BLEND_WITH_CASING = false;   // 0.3.88：按队长要求关闭（融壳会让成型后连接纹理(CTM)出现缝隙 ✗ + 疑似 JVM 原生崩溃相关 ✗）；改回 true 即恢复

    @Override
    public net.minecraft.world.level.block.state.BlockState getBlockAppearance(
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.BlockAndTintGetter level,
            net.minecraft.core.BlockPos pos,
            net.minecraft.core.Direction side,
            net.minecraft.world.level.block.state.BlockState sourceState,
            net.minecraft.core.BlockPos sourcePos) {
        // ★ 0.3.87 修复（崩溃取证 ✓ ConcurrentModificationException @ MultiblockPartMachine.getControllers:67 ✗）：
        //   本方法**在渲染线程被调用**（建区块网格 / 连接纹理判定 ✓）⇒ 在里面遍历 getControllers() ✗
        //   会与 GTCEu 自己的遍历并发 ⇒ 抛 ConcurrentModificationException ⇒ 崩 ✓
        //   而且 GTCEu 的 MetaMachine.getBlockAppearance → IMultiPart.getFormedAppearance
        //   → IMultiController.getPartAppearance **本来就会**给成型部件返回机壳外观 ✓
        //   ⇒ 本机**不再自己判**，一律交给官方那条链 ✓（"直接用人家官方的" ✓）
        return super.getBlockAppearance(state, level, pos, side, sourceState, sourcePos);
    }
    private void ensureSlotRecipeTypeArray() {
        int slots = slotCount();
        if (slotRecipeType == null || slotRecipeType.length != slots) {
            int[] fresh = new int[slots];
            Arrays.fill(fresh, -1);
            slotRecipeType = fresh;
        }
    }

    /** {@code slotRecipeType} 的取值：-1 = 还没尝试识别；-2 = 试过但识别不出来（不再重复扫配方表）；>=0 = 类型下标。 */
    private static final int TYPE_NOT_TRIED = -1;
    private static final int TYPE_UNKNOWN = -2;

    /** 某个样板槽的配方类型下标；识别不出来返回 -1。<b>手动指定的优先</b>。 */
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
        ensureSlotRecipeTypeArray();
        if (slot >= slotRecipeType.length) {
            return -1;
        }
        int cached = slotRecipeType[slot];
        if (cached >= 0 && cached < types.size()) {
            return cached;
        }
        if (cached == TYPE_UNKNOWN) {
            return -1;   // 试过了，识别不出来，别再扫一遍配方表（这是扣料热路径）
        }
        int detected = detectRecipeType(slot, types);
        slotRecipeType[slot] = detected >= 0 ? detected : TYPE_UNKNOWN;
        return detected;
    }

    /** 手动指定的类型在<b>当前</b>类型表里的下标；没指定 / 类型已不在表里 → -1。 */
    private int manualTypeIndexOf(int slot, List<GTRecipeType> types) {
        net.minecraft.resources.ResourceLocation id = manualSlotTypes.get(slot);
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

    public int getManualAssignSlot() {
        return manualAssignSlot;
    }

    public void setManualAssignSlot(int slot) {
        this.manualAssignSlot = Math.max(1, Math.min(getCatalystSlotCount(), slot));
        refreshManualAssignDisplay();
    }

    public String getManualAssignTypeId() {
        return manualAssignTypeId;
    }

    /** 把「当前选中槽位的手动指定」同步给客户端显示（服务端调）。 */
    private void refreshManualAssignDisplay() {
        if (isRemote()) {
            return;
        }
        net.minecraft.resources.ResourceLocation id = manualSlotTypes.get(manualAssignSlot - 1);
        this.manualAssignTypeId = id == null ? "" : id.toString();
    }

    /**
     * 把<b>当前选中的模式</b>手动指定给某个样板槽位（或清除指定）。
     * 只是登记「这个槽的样板属于哪种类型」，不动样板本身。
     *
     * <p>入口有两个：模式页上「填槽号 + 指定/清除」，以及<b>Shift + 右键/中键点样板槽</b>。
     *
     * <p>★ 0.3.2：这里读的是 <b>模式页游标</b> {@link #modeCursorIndex}（玩家在界面上看到的那个模式），
     * <b>不再</b>读 {@link #activeRecipeTypeIndex} —— 后者每 tick 被 {@link #autoSwitchTick()} 的
     * 「跟随控制器」改写，玩家点「指定」时早就变回主机当前模式了（实测症状：指定了切割机、结果写成 1 号模式）。
     *
     * <p>★ 0.3.2 后半（队长实测「钻石被吃进缓冲、机器在做钻石杆、产物不回」那个事故的收尾）：
     * 只改「类型缓存」不够 —— 主机侧「这台舱室能跑哪些配方」是从 <b>GT 配方缓存</b>学的，而
     * {@code GTRecipeLookupMixin} 的快路径是「缓存集非空就取一条、不校验谓词」。于是「上次用错模式时
     * 被主机配上过的配方」（比如切割机·钻石杆）会一直留着 ⇒ 玩家把模式改对了它仍旧喂那条跑不动的配方。
     * 这里连同<b>该槽</b>的 GT 配方缓存一起清掉（见 {@link #clearSlotRecipeCache(int)}），逼主机按新模式重新搜一次。
     */
    public void assignManualType(int slotNumber, boolean clear) {
        List<GTRecipeType> types = getAvailableRecipeTypes();
        int slot = slotNumber - 1;
        if (slot < 0 || slot >= slotCount()) {
            return;
        }
        if (clear) {
            manualSlotTypes.remove(slot);
        } else {
            if (types.isEmpty()) {
                return;
            }
            // ★ 0.3.2：读**模式页游标**（玩家在界面上看到的那个模式）
            int index = Math.max(0, Math.min(types.size() - 1, modeCursorIndex));
            GTRecipeType type = types.get(index);
            if (type == null || type.registryName == null) {
                return;
            }
            manualSlotTypes.put(slot, type.registryName);
        }
        // 指定变了 → 该槽的识别缓存与拉黑表都作废
        if (slotRecipeType != null && slot < slotRecipeType.length) {
            slotRecipeType[slot] = TYPE_NOT_TRIED;
        }
        // ★ 0.3.2 后半：该槽"主机已经配上过的旧模式配方"必须一起忘掉（只清该槽，不全清）
        if (clearSlotRecipeCache(slot)) {
            GTLModular.LOGGER.info("[gtlmodular] 手动指定/清除 槽 {} ⇒ 已作废该槽的 GT 配方缓存，主机将按新模式重搜 @{}",
                    slot + 1, getPos());
        }
        autoSwitchBlockedUntil.clear();
        refreshManualAssignDisplay();
        rememberBoundModeType();                 // ★ 绑定模式持久化：指定动作顺带刷新一次绑定 id（只在玩家拨过时生效）
        syncCustomNameToMode(modeCursorIndex);   // ★ 名字跟随模式（队长 2026-09-21；0.3.2 起跟随游标）
    }

    /**
     * ★ 0.3.2 后半：清掉<b>该槽</b>「主机已经配上过的 GT 配方缓存」。
     *
     * <p><b>为什么不是照搬惰性机的实现</b>（惰性机调的是它自己的
     * {@code ((LazyMEPatternTrait) getMETrait()).getSlot2RecipesCache().clear()}）：
     * 现役机 {@code getMETrait()} 返回的是 gtlcore 具体类的 {@code MEPatternBufferPartMachine$MEPatternTrait}
     * （<b>另一个类</b>），强转会 {@code ClassCastException}。
     *
     * <p>现役机的等价物是具体类 protected 的 {@link MEPatternBufferPartMachine#removeSlotFromGTRecipeCache(int)}
     * （javap 逐指令：{@code cacheRecipe[slot]=false} + {@code recipeMultipleCacheMap.remove(slot)}
     * + <b>主机侧</b> {@code removeSlotFromMap.accept(slot)} + 镜像通知 {@code notifyProxySlotRemoved(slot)}）
     * ⇒ 直接调它，绝不自己拼 {@code recipeMultipleCacheMap.clear()}（那样会漏掉主机侧的遗忘与镜像通知）。
     *
     * <p><b>只清该槽</b>：绝不逐槽全清 640 次（那会在一次点击里打 640 次主机遗忘 + 镜像通知，纯浪费）。
     *
     * @return 该槽原本是否已有一份配方缓存（true = 真的作废了一份；调用方据此打**有界**的取证日志）
     */
    private boolean clearSlotRecipeCache(int slot) {
        try {
            if (slot < 0 || slot >= getInternalSlotCount()) {
                return false;
            }
            boolean hadCached = hasRecipeCacheInSlot(slot);   // 具体类实现 = cacheRecipe[slot]
            removeSlotFromGTRecipeCache(slot);
            return hadCached;
        } catch (Throwable failure) {
            // 清缓存失败只是"少清一次"，绝不影响指定本身
            GTLModular.LOGGER.debug("[gtlmodular] 清槽 {} 的 GT 配方缓存失败（已忽略）：{}", slot, failure.toString());
            return false;
        }
    }

    /**
     * ★ 0.3.2 后半 / 0.3.4：<b>模式真的换了</b>时，把「属于该模式的样板槽」的 GT 配方缓存逐槽清掉。
     *
     * <p>与 {@link #clearSlotRecipeCache(int)} 同理由：缓存里可能留着"上一个模式被配上过的配方"，
     * 而快路径不校验谓词 ⇒ 切了模式却还在跑旧模式的配方。切模式本来就意味着主机要重新搜一遍配方，
     * 清掉不会带来额外代价。
     *
     * <p><b>只走「非空且类型等于该模式」的槽</b>（按需清），不逐槽全清 640 次；代价与
     * {@link #findModeWithWaitingStock(List, int, long)} 里那两遍扫描同量级，只发生在真的换模式的那一次。
     */
    private void clearRecipeCacheForMode(int modeIndex) {
        try {
            if (modeIndex < 0) {
                return;
            }
            ItemStackTransfer inventory = getPatternInventory();
            if (inventory == null) {
                return;
            }
            ensureSlotRecipeTypeArray();
            int slots = Math.min(inventory.getSlots(), slotRecipeType.length);
            int cleared = 0;
            for (int slot = 0; slot < slots; slot++) {
                if (inventory.getStackInSlot(slot).isEmpty()) {
                    continue;   // 空槽没有缓存可言，跳过（省掉类型识别）
                }
                if (recipeTypeOfSlot(slot) != modeIndex) {
                    continue;
                }
                if (clearSlotRecipeCache(slot)) {
                    cleared++;
                }
            }
            if (cleared > 0) {
                // 每次换模式最多一条（不逐槽刷），用来在验收时一眼确认"旧模式的配方真的被忘掉了"
                GTLModular.LOGGER.info("[gtlmodular] 换模式清 GT 配方缓存: 模式 {} 的 {} 个槽已作废 @{}",
                        modeIndex, cleared, getPos());
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.debug("[gtlmodular] 清模式 {} 的 GT 配方缓存失败（已忽略）：{}", modeIndex, failure.toString());
        }
    }

    /** 该槽里有没有料等着（{@code getItemStackInputMap()/getFluidStackInputMap()} 有一侧非空）。原样保留现役机的读法。 */
    private boolean hasSlotWaitingStock(int slot) {
        try {
            var internal = getInternalSlot(slot);
            if (internal == null) {
                return false;
            }
            return !internal.getItemStackInputMap().isEmpty() || !internal.getFluidStackInputMap().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * ★ 2026-10-15（本轮重做）：<b>总成的名字 = 本总成"绑定的那个模式"的名字</b>，
     * 按「本总成 + 模式」记在 {@link #modeNames} 里并<b>落盘</b>。
     *
     * <p>「总成的名字」= gtlcore 的「重命名样板总成」那个输入框（字段在
     * {@code MEPatternBufferPartMachineBase.customName}，我们继承它 ⇒ {@code setCustomName/getCustomName} 直接可用）。
     *
     * <p><b>规则（用户口径逐条落地）</b>：
     * <ol>
     *   <li><b>名字跟着"模式"走，不跟着控制器当前模式走</b> ✗：命名用的模式 = 本总成自己的模式
     *       （{@link #modeCursorIndex}，绑定持久化在 {@code gtlmodular$bound_mode_type}）
     *       ⇒ 同一控制器下两台总成各绑一个模式 ⇒ <b>各叫各的</b>，主机在跑什么与名字无关 ✓；</li>
     *   <li><b>该模式已经有名字</b>（我们设过 / 玩家改过）⇒ <b>继续用那个名字</b>（"延续"）；</li>
     *   <li><b>该模式还没有名字</b> ⇒ 用该模式的<b>显示名</b>（{@link #modeNameForNaming}，真翻译）写一次；</li>
     *   <li><b>玩家手动改名 ⇒ 永久让位</b>（{@link #lockedName}，落盘）：此后换模式、改绑定、
     *       主机切模式、重进游戏、拆装重成型都<b>不再改写</b> ✗（唯一例外：名字被清空时把玩家那个名字写回来）；</li>
     *   <li>取不到显示名（连原文都取不到）⇒ <b>保持原名不动</b> ✗（绝不写空）。</li>
     * </ol>
     *
     * <p><b>★ 为什么删掉了旧的"白名单"判据</b>：旧版允许"名字逐字等于本控制器某个模式显示名"
     * （{@code isModeDisplayNameOfController}）时改写 —— 玩家把总成命名成某个模式的名字
     * （队长实测：「纳米芯片换算」）就会被当成"我们自己写的"而<b>被改回自动名</b> ✗；
     * 而且它只认<b>当前控制器</b>的模式名 ⇒ 总成换挂到另一台机器后，旧自动名不在新模式的名单里
     * ⇒ 从此一个字都不写（队长实测：「接在电路组装机上的总成没有起名」✗）。
     * 该判据整条删除，本方法<b>不再做任何"猜这名字是谁写的"的判断</b> ✓。
     *
     * <p><b>纪律</b>：起名纯粹是显示层的事，任何异常都不许影响机器逻辑 ⇒ 整个方法包在 try 里、
     * 失败就静默跳过（连日志都不打，免得刷屏）。
     */
    private void syncCustomNameToMode(int modeIndexHint) {
        // ★ 只在服务端做（与 Enhancedcore 的 `SuperBufferNaming.applyNames` 同口径：它第一句就是
        //   `if (machine.isRemote()) return;`）。名字随后由 LDLib 的同步字段机制发到客户端 ✓。
        if (isRemote()) {
            return;
        }
        try {
            List<GTRecipeType> types = getAvailableRecipeTypes();
            String current = getCustomName();
            // ① <b>识别"别人改了名字"</b>（玩家在界面里改、或上游/别的代码写进来）：
            //    与我们上次看到的那一个串不同 ⇒ 记为"玩家改名"（{@link #lockedName} 永久让位），
            //    并把它归到"当时显示的那个模式"名下（{@link #appliedModeKey}）。
            //    · 上游兜底串「未命名加工模式」不算名字（它不是任何人的选择）⇒ 不锁，按"没名字"处理；
            //    · 名字被清空不算改名（读档/掉落的中间态，见 ④）。
            //    代价：每 tick 一次 `String.equals`，**零对象构造** ✓。
            if (observedName != null && current != null && !current.isEmpty()
                    && !current.equals(observedName) && !isUpstreamPlaceholderName(current)) {
                this.lockedName = current;
                if (appliedModeKey != null) {
                    modeNames.put(appliedModeKey, current);
                }
                markDirty();
            }
            // ② 开关关掉 ⇒ 逐字回到改动前的行为（**唯一允许写的情况 = 名字为空**），
            //    不写模式名字表、不记让位、不落盘新键 —— 这就是本补丁的一键回退语义 ✓。
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
            // ③ 绑定模式还在"待解析"（读档后类型表还没就绪）⇒ 本 tick 什么都不做：
            //    否则会把名字记到"下标 0 的默认模式"名下（错位），等下一 tick 解析出来再记 ✓。
            if (boundModePendingResolve) {
                this.observedName = current;
                return;
            }
            boolean bound = this.modeCursorTouched;   // 玩家在这台总成上选定过模式 ⇒ 名字归"它绑定的那个模式"
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
            // ④ 玩家改过名 ⇒ 名字永久归玩家：只在"名字为空"（某些读档/掉落路径会把 customName 丢掉）
            //    时把玩家那个名字写回来，绝不改成别的模式的名字 ✓
            //    —— 这就是"手动改名 ⇒ 重进游戏 / 切主机模式 / 改绑定 / 拆装重成型都不变"。
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
            // ⑤ 目标名字 = 该模式已经记着的名字（第 ② 条"延续"）；没有 ⇒ 用该模式的显示名并记下来
            String target = modeNames.get(key);
            if (target == null || target.isEmpty()) {
                if (!bound && current != null && !current.isEmpty() && !movedToAnotherController) {
                    // 未绑定（游标还在跟随控制器）+ 名字已经有主 + 没换过控制器 ⇒ 一个字都不写：
                    // **绝不因为主机换了模式就改名字** ✓（这就是"名字不跟着机器模式变"的落点）。
                    this.observedName = current;
                    this.appliedModeKey = key;
                    return;
                }
                // ★ 0.3.38（队长要求，<b>保留</b>）：通用厂（通用加工厂/组装厂/分离厂/混合厂）的身份由
                //   **结构里塞入的机器**决定 ⇒ 只有塞入机器与"本总成的命名模式"一致时才起名，
                //   不一致就保持原名（乱起名会让玩家按错模式）。
                com.gregtechceu.gtceu.api.machine.MetaMachine inserted = findInsertedMachine(controller);
                if (inserted != null && !insertedSupportsType(inserted, types.get(mode))) {
                    this.observedName = current;
                    return;
                }
                String display = modeNameForNaming(types.get(mode));
                if (display == null) {
                    // 取不到显示名 ⇒ 保持当前名字不动 ✗（绝不写空、绝不写 lang 键）
                    this.observedName = current;
                    return;
                }
                target = display;
                modeNames.put(key, target);
                markDirty();
            }
            // ⑥ 幂等写：值已经对了就一个 setter 都不调（也就不会每 tick 刷脏）
            if (!target.equals(current)) {
                // 这一写就是本补丁的全部动作：走公开方法
                // {@code MEPatternBufferPartMachineBase.setCustomName(String)} ⇒ Enhancedcore 的
                // `SuperBufferNamePersistenceMixin:54-66` 会把它标成 manual ⇒ 上游自动命名永久让位 ✓。
                setCustomName(target);
            }
            this.observedName = target;
            this.appliedModeKey = key;
        } catch (RuntimeException | Error ignored) {
            // 起名失败绝不影响机器
        }
    }

    /**
     * ★ 2026-10-15：<b>玩家在界面里确认了改名</b>（{@code AETextInputButtonWidget.setOnConfirm} 的落点）。
     *
     * <p>它把"这是玩家亲手改的"当场记下来（{@link #lockedName} ⇒ 落盘 ⇒ 永久让位）—— 这是"名字永久归玩家"
     * 最可靠的一手，不依赖"下一 tick 对比名字"那条兜底判据（见 {@link #syncCustomNameToMode(int)} ①）。
     * LDLib 的控件回调两端都会跑：客户端那次只更新本地字段（不写盘、不 markDirty），权威落盘在服务端那次 ✓。
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
     * ★ 2026-10-15：<b>命名用哪一个模式</b> —— 绑定模式优先，未绑定才跟随控制器的当前模式。
     *
     * <p>顺序：① 已绑定（{@link #modeCursorTouched}）且下标合法 ⇒ 本总成的模式（<b>权威</b>，
     * 主机切模式不影响它 ✓）；② 未绑定 ⇒ 控制器的当前模式（= 这台总成此刻真正服务的那个模式，
     * 也是"新放到某台机器上"时该用的那个）；③ 调用点给的兜底下标；④ 本机缓存下标；
     * ⑤ 全不成立 ⇒ {@code -1}（<b>不命名</b>，保持原名不动）。
     *
     * <p><b>为什么不再无条件用 {@code modeCursorIndex}</b>：未绑定时它是"跟随控制器"的镜像，
     * 而每 tick 的跟随发生在命名<b>之后</b> ⇒ 直接用它会慢一拍（刚放下/刚读档时按上一个模式命名）。
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

    /** ★ 2026-10-15：模式类型 ⇒ 名字表的键（模式 id）。取不到 id ⇒ {@code null}（调用方放弃本次命名）。 */
    private static String modeKeyOf(GTRecipeType type) {
        return type == null || type.registryName == null ? null : type.registryName.toString();
    }

    // ★ 2026-10-15：旧的"能不能由我们改名"白名单判据（canAutoWriteCustomName / isModeDisplayNameOfController /
    //   modeDisplayNames + 它们的缓存）**整段删除** —— 那一套正是"玩家手动名被改回自动名"的根因
    //   （名字逐字等于某个模式显示名 ⇒ 被当成我们自己写的 ✗）。现在"要不要写"由上面那套
    //   「按模式记名字 + 玩家改名永久让位」的规则决定，不再有任何"猜名字归属"的判据 ✓。

    /**
     * ★ 2026-10-14：**"本总成自己负责的模式"的权威取值** —— 自动命名只认这一个来源。
     *
     * <p><b>为什么权威从控制器换成本总成</b>（用户口径原话：「<b>每个总成绑定一个固定模式</b>」）：
     * 一台主机有两（多）个模式时，玩家放两（多）个样板总成、<b>各自负责一种模式</b>，
     * 名字必须**固定显示自己负责的那个模式**、<b>不随主机当前在跑什么而变</b> ✗。
     * 上一版读控制器 {@code getActiveRecipeType()} ⇒ 同一主机下所有总成必然显示同一个名字
     * （队长实测：「车床（大型切割机）」两台撞车 ✗）。
     *
     * <p>取值顺序（逐条都有依据）：
     * <ol>
     *   <li><b>{@link #modeCursorIndex} = 权威</b> —— 它就是"<b>这台总成自己的模式</b>"：
     *       模式页（{@code RecipeModeConfigurator}/{@code ModeScaleConfigurator}）上 ◀/▶ 选中的那一个，
     *       也是「指定」按钮真正写下去的那个（{@link #assignManualType(int, boolean)} 读的就是它，
     *       :1273）；{@link #syncCustomNameToMode(int)} 的另一个调用点给的也是它（:1291）。
     *       ⇒ "用户在总成上选定、并用于决定这台总成匹配/推送哪个模式"的那个就是它 ✓。
     *       <b>已知边界</b>：它带 {@code @DescSynced}、<b>无</b> {@code @Persisted}（:285），
     *       既不在 {@link #saveCustomPersistedData} 也不在 {@link #loadCustomPersistedData} 里
     *       ⇒ <b>读档后回默认值 0</b>（随后由 tick 里的"游标跟随控制器"改写，见 :2289）。
     *       这一条<b>本轮不动</b>（不新增/不改任何 {@code @Persisted} 字段 ⇒ 存档格式零变化是硬纪律 ✗），
     *       所以"重启后名字要重新跟随玩家在本总成上选的那次操作"这一点<b>未取证/未解决</b>，
     *       详见 _census/命名绑定自有模式_执行记录.md 的「未取证项」；</li>
     *   <li>调用点给的 {@code modeIndexHint} —— 只在 {@link #modeCursorIndex} <b>非法</b>时兜底
     *       （换控制器导致类型表变短、下标越界等）。调用点：
     *       {@link #assignManualType(int, boolean)} 给 {@link #modeCursorIndex}；
     *       tick / 读档路径给本类缓存下标 {@link #activeRecipeTypeIndex}；</li>
     *   <li>{@link #activeRecipeTypeIndex}（同样要求下标合法）—— 再退一档的本机缓存；</li>
     *   <li><b>控制器的 {@code getActiveRecipeType()} 只作最后兜底</b>（★ 2026-10-14 从"权威"降级）：
     *       本机模式完全不可得时（例如刚成形、类型表刚换实例）宁可显示"主机现在的模式"，
     *       也不要空着/不动 —— 这是<b>兜底</b>、不是权威，正常路径永不走到它 ✓；</li>
     *   <li>以上全不成立 ⇒ 回落 <b>{@code 0}</b>（= "机器自己的第 0 个类型"，★ 注释义务：
     *       类型表非空时下标 0 恒合法，所以"类型表非空"必然能给出一个合法下标；
     *       这是最保守的兜底 —— 宁可显示第 1 个模式名，也好过完全不起名）。</li>
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
        // ④ ★ 2026-10-14 降级为**最后兜底**：控制器（主机）当前的模式。
        //    正常路径永不走到这里（①-③ 至少有一条成立）；只在"本机模式完全不可得"时兜底，
        //    确保"类型表非空 ⇒ 一定有名字"，绝不因为本机模式暂缺而让总成一直无名 ✓。
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
     * ★ 2026-10-04b：一个配方类型 ⇒ **可以写进自定义名的显示名**；<b>取不到就返回 {@code null}</b>
     * （调用方立刻放弃本次命名 ⇒ 保持当前名字不动 ✗）。
     *
     * <p>与 {@link #tableNameOf(GTRecipeType)} 的区别**只有一处**、但很关键：
     * {@code tableNameOf} 是**日志/界面**口径 —— 查不到译文就退回路径名（如 {@code nanochip_conversion}），
     * 那在日志里很好读；而**自定义名是显示给玩家看的**，写一个英文路径名比不写更糟
     * （任务纪律：取不到 ⇒ 保持当前名字不动；不要写空、不要写英文键名 ✗）⇒ 这里返回 {@code null}。
     *
     * <p>判定顺序：① {@link #nameOverride}（我们自己的覆盖表，例如 {@code gtceu:circuit_assembly}
     * ⇒「电路组装」，**不依赖语言文件**）；②
     * {@code Component.translatable(registryName.toLanguageKey())} 真翻译 —— 译文为空、或
     * <b>等于键名本身</b>（= 语言文件里没有这个键，{@code translatable} 回落成原文）⇒ 返回 {@code null}；
     * ③ 抛异常 ⇒ 同样返回 {@code null}。<b>不硬编码任何中文</b> ✓。
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
            String translated = net.minecraft.network.chat.Component.translatable(key).getString();
            if (translated == null || translated.isEmpty() || translated.equals(key)) {
                // ★ 2026-10-15：**语言文件里连英文都没有这个键**时不再"整条不生效" ✗——
                //   用户口径「取不到中文就退回英文/原文，不允许因为取不到就整条不生效」。
                //   退回**原文路径名**（如 `circuit_assembler`）：可读、且不含命名空间；
                //   绝不写空、也绝不把 `gtceu.xxx` 这种 lang 键写进玩家看到的名字 ✗。
                //   （正常路径不会走到这里：`Language` 自带 en_us 兜底 ⇒ 有英文就有名字 ✓。）
                String path = type.registryName.getPath();
                return path == null || path.isEmpty() ? null : path;
            }
            return translated;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * ★ 2026-10-04：这个字符串是不是上游 Enhancedcore 写的兜底串「未命名加工模式」。
     *
     * <p><b>为什么在我们自己的代码里用 Unicode 转义字面量</b>（而不是直接写那四个汉字）：
     * 与上游 {@code SuperBufferChineseNames:13} 的写法**逐字同款**，好处是
     * ① 一眼能看出"这就是上游那个常量"，② 不受本仓库文件编码/工具链的任何影响，
     * ③ 用中文字面 grep 本仓库时不会把它当成我们的文案。
     * 判据是**全等**（{@code String.equals}），**不含"是否含汉字"这种猜测** ✓。
     */
    private static boolean isUpstreamPlaceholderName(String name) {
        return name != null && AUTO_NAME_FALLBACK_UPSTREAM.equals(name);
    }

    /**
     * ★ 0.3.38：自动命名的**名字覆盖表**（队长要求）。
     *
     * <p>只列需要特殊处理的模式，其余一律走 {@code tableNameOf} 给的默认中文名。
     * <b>刻意不做"统一去掉末尾的『机』"</b> —— 那会把「线材轧机」变成「线材轧」✗。
     *
     * <p>现有条目：
     * <ul>
     *   <li>{@code gtceu:circuit_assembly} ⇒ 「**电路组装**」（队长：不叫「电路组装机」）</li>
     * </ul>
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
     * ★ 0.3.38：找出**通用厂结构里"塞入的那台机器"**（通用加工厂/组装厂/分离厂/混合厂靠塞机器定身份）。
     *
     * <p>依据（GTCEu 字节码实证）：
     * <ul>
     *   <li>这四台的结构里有一个 {@code Predicates.blocks(IMachineBlock...)} 的**机器方块槽**；</li>
     *   <li>{@code MultiblockControllerMachine.getMultiblockState()} 的 {@code cache} 字段
     *       存着**本次已匹配的全部结构位置**（{@code LongOpenHashSet}，公开字段）；</li>
     *   <li>逐位 {@code MetaMachine.getMachine(world, pos)}：**不是部件（{@code IMultiPart}）、也不是自己**
     *       的那台，就是"塞入的机器"。</li>
     * </ul>
     * 找不到（普通非通用主机、结构未成型、扫描异常）就返回 {@code null} ⇒ 命名退回原来的"只按模式"行为 ✓。
     */
    private com.gregtechceu.gtceu.api.machine.MetaMachine findInsertedMachine(IRecipeLogicMachine controller) {
        try {
            if (!(controller instanceof com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController mc)) {
                return null;
            }
            var state = mc.getMultiblockState();
            if (state == null || state.cache == null || state.world == null) {
                return null;
            }
            var it = state.cache.longIterator();
            while (it.hasNext()) {
                com.gregtechceu.gtceu.api.machine.MetaMachine candidate = com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(state.world, net.minecraft.core.BlockPos.of(it.nextLong()));
                if (candidate == null || candidate == this) {
                    continue;
                }
                if (candidate instanceof com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart) {
                    continue;   // 部件（仓/总线）跳过
                }
                return candidate;   // ★ 塞入的整台机器
            }
        } catch (Throwable ignored) {
            // 拿不到就当没有：命名退回"只按模式"
        }
        return null;
    }

    /** 塞入的机器是否支持这个配方模式（＝队长说的"塞入机器和当前机器模式一样"）。 */
    private static boolean insertedSupportsType(com.gregtechceu.gtceu.api.machine.MetaMachine machine, GTRecipeType type) {
        if (machine == null || type == null) {
            return false;
        }
        try {
            if (machine instanceof IRecipeLogicMachine logic) {
                for (GTRecipeType t : logic.getRecipeTypes()) {
                    if (t == type) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 判定失败 ⇒ 视为不一致（宁可不命名，也不乱命名）
        }
        return false;
    }

    /** 塞入机器的显示名（用于拼进样板总成的名字）。 */
    private static String insertedName(com.gregtechceu.gtceu.api.machine.MetaMachine machine) {
        try {
            var definition = machine.getDefinition();
            if (definition != null) {
                return definition.asStack().getHoverName().getString();
            }
        } catch (Throwable ignored) {
            // 取不到名字就不拼
        }
        return "";
    }

    /** 这个名字是不是已经被「**同一控制器下别的样板总成**」用了（队长口径里的「已经有了」）。 */
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

    /** 这个槽有没有被手动指定过（UI 提示用）。 */
    public boolean hasManualType(int slot) {
        return manualSlotTypes.containsKey(slot);
    }

    /**
     * 「读样板的配方类型」：把样板解成 AE 样板，然后在每一种配方类型的配方里找**身份匹配**的那一条
     * （{@link #patternIdentityMatches}：非电路输入被样板输入覆盖 + 配方主产物落在样板输出里）。
     *
     * <p>为什么不用"产物集合完全相等"：gtlcore 的样板身份在 {@code keepByProduct=false} 时**会裁掉副产物**，
     * 拿它去和"配方全部产物集合"比相等会永远不相等 → 识别全失败（实测就是这么翻车的）。
     * 用身份匹配口径和扣料时的身份闸完全一致，且**多类型命中就判为歧义、返回 -1 不猜**。
     */
    private int detectRecipeType(int slot, List<GTRecipeType> types) {
        // ★ 2026-10-27（配合【①】的新口径，**必需的安全垫**）：本字段是「**本次调用**唯一命中」的中转，
        //   必须在入口先清空。否则本方法那几条前置早退（level/仓读不到、空槽、解析失败、异常）
        //   会把**上一个槽**留下的配方原样带出去 —— 而 ① 的新口径只看"配方在不在手上"
        //   ⇒ 会**跨槽误喂**一条别的槽的配方 ✗（这是绝不允许的）。
        //   对"开关置 true 的旧口径"本行逐字无害：旧口径下 detectedTypeIndex < 0 已经把全部早退路径挡住，
        //   而唯一命中路径 scanTypesForMatch 本来就会先清一次 ⇒ 行为完全相同 ✓
        this.lastMatchedSlotRecipe = null;
        Level level = getLevel();
        ItemStackTransfer inventory = getPatternInventory();
        if (level == null || inventory == null || slot >= inventory.getSlots()) {
            return -1;
        }
        ItemStack stack = inventory.getStackInSlot(slot);
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
        // ★ 2026-09-21 修（队长报「主机不知道为什么，会自动切换成纳米芯片组装复合体的模式」）：
        //   主机表 = NanochipRecipeTypes.ASSEMBLY_COMPLEX（`proxiedType` 代理给 GTCEu「电路组装机」，
        //   实机日志里那 141 条）⇒ 本判据（输入只比身份/忽略数量、输出只看首个主产物）会让
        //   **任何产物是电路的样板**都命中主机表那一格 ⇒ 被误判成下标 0（= 主机自己的模式）。
        //   后果两条：① 自动切模式把主机切回自己的表；② 与换算表同时命中 ⇒ 判成"歧义"返回 -1，
        //   于是实机日志里大量 `类型已知=[]`（识别不出来）。
        //   改法：**两遍扫描** —— 第一遍**跳过主机表**（唯一命中就直接采用，不再被它搅成歧义）；
        //   只有第一遍一个都不命中时，第二遍才回落到主机表（"本该主机表跑的样板"照样能识别）。
        //   第一遍内部若两个非主机表都命中，仍按既有口径返回 -1（不猜）。
        int withoutHost = scanTypesForMatch(types, pattern, level, true);
        if (withoutHost != MATCH_NONE) {
            // ★ 2026-10-27【③】：唯一命中 ⇒ 原样；**歧义（-1）⇒ 先按该槽已解出的配方定类型**
            //   （定不下来就保持 -1，并走 noteAmbiguousSlotType 的可见告警）
            int resolvedFirst = resolveAmbiguousTypeFromSlotCache(slot, types, withoutHost, pattern);
            supplyResolvedRecipeToMachine(slot, resolvedFirst);   // ★ 2026-10-27【直供配方】
            return resolvedFirst;   // 唯一命中（>=0）／歧义被 ③ 解开（>=0）／仍是歧义（-1）
        }
        int hostTableOnly = scanTypesForMatch(types, pattern, level, false);
        int resolvedSecond = resolveAmbiguousTypeFromSlotCache(slot, types, hostTableOnly, pattern);   // ★ ③
        supplyResolvedRecipeToMachine(slot, resolvedSecond);     // ★ 2026-10-27【直供配方】第二遍（只看主机表）
        return resolvedSecond;
    }

    /** 扫描结果：本遍**一个都没命中**。 */
    private static final int MATCH_NONE = -2;

    /**
     * 在 {@code types} 里逐类型找「身份匹配」的配方。
     *
     * @param skipHostTable {@code true} = **跳过**主机表（{@code NanochipRecipeTypes.ASSEMBLY_COMPLEX}）；
     *                      {@code false} = **只看**主机表（两遍扫描的第二遍）。
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

    /**
     * ★ <b>「歧义时按该槽已解出的配方定类型」开关</b>（★ 2026-10-27 新增，<b>默认 = 新行为</b>）。
     *
     * <p>{@code true}（<b>默认</b>）⇒ {@link #detectRecipeType(int, List)} 两遍扫描得到
     * {@code -1}（= <b>歧义</b>：≥2 种类型都身份命中）时，改用
     * {@link #resolveAmbiguousTypeFromSlotCache(int, List, int, IPatternDetails)}：
     * 从该槽**已经解出来的**配方缓存里，挑出与这张样板身份相符的那一条，
     * 用它自带的 {@code recipe.recipeType} 定类型；定位不到 ⇒ 仍保持 {@code -1}，
     * 但走 {@link #noteAmbiguousSlotType(int)} 的**可见告警**。
     * {@code false} ⇒ 逐字退回旧口径（歧义 ⇒ 直接 -1，什么都不做）。
     *
     * <p><b>为什么不是"读样板自带的类型 id"（这条必须写清楚，因为山海是那么做的）</b>：
     * 山海把类型 id **权威写进样板 NBT**（{@code PatternRecipeTypeHelper.writeAuthoritativeRecipeType}，
     * {@code RecipeTypePatternBufferPartMachine.java:62-70}），运行时直接**读**它、不再重推断
     * （同文件 {@code :661-679}）。但我们这条链上<b>没有人往样板里写过类型 id</b> ——
     * 全模块取证结论：唯一带类型 id 的物品 NBT 是**归档工具**上那一个档案戳
     * （{@code LazyPatternArchiveCodec.java:104} 的 {@code NBT_STAMP = "gtlmodular$archive"}、
     * {@code :343-345} 的 {@code K_MANUAL_TYPE}），它是写在**工具**上的；落进机器槽里的样板
     * 只是被 {@code stack.copy()} 原样搬进档案（{@code :337-339} 把样板序列化进档案条目），
     * 样板自身的 NBT 一个字节都没被写过；粘贴时类型 id 是落到**机器侧**的
     * {@code manualSlotTypes}（{@code LazyPatternArchiveCodec.java:568} 读出 →
     * {@code writeArchivedManualType} → {@code MoltenPatternAssemblyPartMachine.java:4076-4086}）。
     * ⇒ 我们**没有**"样板→类型"的权威来源，**且本任务禁止**顺手新增样板编码/写入逻辑
     * （那会动样板编码路径，风险大）⇒ 按任务给定的第二支：<b>用该槽缓存里已解出的配方定类型</b> ✓
     * （手动指定那条优先级更高的来源本来就已经在 {@link #recipeTypeOfSlot(int)} 的 ① 里，
     * 走到本方法说明手动也没指定 / 指定的 id 不在当前类型表里）。
     *
     * <p><b>一行回退</b>：把本行置 {@code false}（⇒ 与加本行之前<b>逐字一致</b>）。
     */
    public static final boolean AMBIGUITY_USE_SLOT_CACHED_RECIPE = true;

    /** {@link #noteAmbiguousSlotType(int)} 台账上限（超级总成 2048 槽，不能让台账无限长）。 */
    private static final int AMBIGUOUS_NOTE_LIMIT = 64;

    /** {@link #noteAmbiguousSlotType(int)} 台账：已经报过"歧义且定位不到"的槽（每个槽只报一次）。 */
    private final java.util.Set<Integer> ambiguousSlotNotes = new java.util.HashSet<>();

    /**
     * ★ 2026-10-27【③】：<b>歧义</b>（多种类型同时身份命中）时，按**该槽已经解出来的配方**定类型。
     *
     * <p>它是「样板里没有类型 id」这一事实下的替代口径（依据与取证见
     * {@link #AMBIGUITY_USE_SLOT_CACHED_RECIPE} 的注释）。
     * 用的来源是 {@link #slotCachedRecipe(int, IPatternDetails)}（gtlcore 官方槽级配方缓存），
     * 并且**带身份过滤**（{@code patternIdentityMatches}）⇒ 只可能挑出"本来就是这张样板的候选"
     * 里面的那一条，绝不会跨样板取料，也绝不会把猜出来的配方交给机器。
     *
     * @param detected {@link #scanTypesForMatch} 的结论（{@code -1} = 歧义；其它值原样返回，本方法不动）
     * @return 定下来的类型下标；定位不到 ⇒ {@code -1}（<b>保持旧口径的"不猜"</b>）
     */
    private int resolveAmbiguousTypeFromSlotCache(int slot, List<GTRecipeType> types, int detected,
                                                  IPatternDetails pattern) {
        if (detected != -1) {
            return detected;   // 唯一命中（>=0）/ 一个都没命中（MATCH_NONE）⇒ 都不是"歧义"，原样返回
        }
        if (!AMBIGUITY_USE_SLOT_CACHED_RECIPE) {
            return detected;   // 开关关 ⇒ 逐字退回旧口径（歧义 ⇒ -1，什么都不做、也不打新告警）
        }
        GTRecipe cached = slotCachedRecipe(slot, pattern);
        if (cached == null) {
            noteAmbiguousSlotType(slot);   // 仍无法定位 ⇒ 保持 -1，但**必须有可见告警**
            return -1;
        }
        int index = typeIndexOfRecipeType(types, cached.recipeType);
        if (index < 0) {
            // 该槽确实有一条已解出的配方，但它的类型**不在本次控制器的类型表里**（换控制器 / 模组更新）。
            // 类型下标给不出来 ⇒ 保持 -1；**但配方在手**，按 ① 的新口径它照样会被直供给机器
            // （这正是山海"校验配方身份即可、不看类型下标"的形状）⇒ 这里把它记进中转。
            this.lastMatchedSlotRecipe = cached;
            noteAmbiguousSlotType(slot);
            return -1;
        }
        this.lastMatchedSlotRecipe = cached;   // ★ 配方在手 ⇒ ① 的直供通道能吃下它
        return index;
    }

    /**
     * ★ 2026-10-27【③】：<b>可见告警</b> —— 这个槽的样板身份同时命中多种配方表（歧义），
     * 且该槽的配方缓存也定位不出类型 ⇒ 类型保持 -1。
     *
     * <p><b>为什么必须"可见"（而不是 debug 级）</b>：类型 -1 的两个直接后果玩家都能看到 ——
     * <ol>
     *   <li>{@link #slotParticipatesInLookup(int)} 对"识别不出来"的槽一律<b>放行</b>
     *       （fail-open，绝不误挡）⇒ 该槽的料本轮<b>不参与按模式收窄</b>；</li>
     *   <li>{@link #lockRecipeTypeForSlot(int)} 在 -1 上<b>绝不动控制器</b>
     *       ⇒ 主机停在上一次的模式、搜不到这条配方时，表现就是"料进了槽、机器不干活"。</li>
     * </ol>
     * 这正是本任务要消掉的那一类症状 ⇒ 必须能在日志里一眼看到<b>是哪一个槽</b>、以及<b>怎么办</b>
     * （Shift+右键该槽 / 模式页填槽号手动指定）。
     *
     * <p><b>纪律</b>：只读 + 只打印；每个槽<b>只报一次</b>（{@link #ambiguousSlotNotes} 台账，
     * 上限 {@value #AMBIGUOUS_NOTE_LIMIT} 条）；整体 {@code try/catch(Throwable)} 吞掉
     * —— 诊断代码绝不允许影响游戏、更不允许影响扣料。
     */
    private void noteAmbiguousSlotType(int slot) {
        try {
            if (ambiguousSlotNotes.size() >= AMBIGUOUS_NOTE_LIMIT) {
                return;
            }
            if (!ambiguousSlotNotes.add(slot)) {
                return;   // 这个槽报过了
            }
            GTLModular.LOGGER.warn("[gtlmodular] 【样板标识歧义·可见告警】槽位[{}] 的样板身份同时命中"
                            + "**多种**配方表，且该槽的配方缓存也定位不出类型 ⇒ 本次类型保持 -1"
                            + "（该槽不参与按模式收窄、也不会自动切主机模式）。"
                            + "若这台机器本该跑它：Shift+右键该样板槽手动指定正确标识（模式页也能填槽号指定）。"
                            + "本行只读、不改任何判定 @{}",
                    slot + 1, getPos());
        } catch (Throwable ignored) {
            // 诊断代码绝不允许影响游戏
        }
    }

    // ================= ★ 2026-10-27【直供配方】：把「该槽对应的那条配方」直接交给机器 =================
    // 依据：_census/直供配方_实施记录.md（§②/§③ 逐行取证）。
    // 通道是**上游官方的、且已经挂载好的**：
    //   gtlcore `IMEPatternTrait.getCachedGTRecipe()`（我们这台机的 ME trait 就是它）
    //   → gtladditions `GTRecipeLookupMixin.getRecipeIterator`（@Overwrite，在 gtladditions jar 里，无需我方 mixin）
    //   → `IAdditionalRecipeIterator.setAdditionalRecipes(...)`（`_enh_extract/gtladditions-…mixins_javap.txt:2215-2302`）
    //   ⇒ 该配方**作为附加候选**进入机器本轮候选集 ⇒ 机器**不必**靠「类型 → 原料树」把它搜出来；
    //     而且这一路的判据是 `cacheRecipe[slot] && internalSlot.isActive()`
    //     （`_javap_simple_gtlcore_buffer.txt:25-75`）——**不看 `getActiveSlots()` ⇒ 不受查找侧收窄影响**。
    // 我们一个字都不改机器侧（NAC 的覆写、`NAC_MULTI_TYPE_RECIPES`、去总池、饱和乘法、预蓄电、无线电网全部不动）：
    // 这条通道机器本来就在消费，我们只是**往里喂**（纯 API 调用，零 mixin、零 @Persisted、零同步字段）。

    /**
     * ★ <b>「直供配方」总开关</b>（★ 2026-10-27 新增）：{@code true}（<b>默认开启</b>）⇒ 样板进槽、
     * 类型被<b>唯一</b>识别出来时，把那条 {@code GTRecipe} 写进该槽的配方缓存
     * （{@link #supplyResolvedRecipeToMachine}）；{@code false} ⇒ 本功能整体不执行，
     * 行为<b>逐字退回</b>加这段代码之前（= 机器照旧自己遍历配方表去找）。
     *
     * <p>★ 2026-10-27【①】<b>文档订正（只改注释、上面的代码一个字未动）</b>：
     * 上面那句「类型被<b>唯一</b>识别出来时」是**旧口径**的描述 ——
     * 自 ① 起准入条件已改成「<b>这条配方确实被解出来了</b>」，不再要求类型下标；
     * 详见 {@link #DIRECT_SLOT_RECIPE_SUPPLY_REQUIRES_TYPE_INDEX}。
     *
     * <p><b>为什么默认开（不是"出厂先观察"）</b>：
     * <ol>
     *   <li>它是<b>纯增量</b>：只往上游那份「该槽能做哪些配方」的缓存里加条目 ——
     *       <b>不加候选、不减候选、不改机器侧任何判定</b>（机器侧照旧多类型遍历 + 照旧逐条 `checkRecipe`）；</li>
     *   <li>全链路 <b>fail-open</b>（见 {@link #supplyResolvedRecipeToMachine} 的五条纪律）：
     *       任何不确定（没识别出来 / 歧义 / 客户端 / 已有缓存 / 任何异常）⇒ 什么都不做，退回今天的行为；</li>
     *   <li>不做的话，「总成<b>本来就要解析</b>出配方、却把它丢掉、让机器再翻一遍配方表」这件事永远没解
     *       ⇒ 与本模组既有的一排默认开开关（{@code LOOKUP_SIDE_SLOT_NARROWING}、NAC 侧那几个）口径一致。</li>
     * </ol>
     * <b>一行回退</b>：把本行置 {@code false} ⇒ 立刻全关（两台总成各一行，见下）。
     * ⚠️ 两台总成（本类 与 {@link LazyPatternAssemblyPartMachine}）的这个开关<b>口径必须一致</b>
     * （都默认开；与 {@code LOOKUP_SIDE_SLOT_NARROWING} 那条"两台必须同时改"的纪律同源）。
     */
    public static boolean DIRECT_SLOT_RECIPE_SUPPLY = true;

    /**
     * 最近一次「唯一命中」的那条配方 —— {@link #scanTypesForMatch} 写的**只读中转**。
     * <p>无命中 / 歧义 ⇒ {@code null}。不落盘、不同步（无 {@code @Persisted} / 无 {@code @DescSynced}）。
     * <p><b>为什么要中转</b>：类型识别（{@code detectRecipeType} → {@code scanTypesForMatch}）本来就在遍历
     * 配方表，<b>配方对象当时就在手上</b>；把它丢掉、再让机器搜一遍，正是本任务要消掉的那份浪费。
     */
    private GTRecipe lastMatchedSlotRecipe;

    /**
     * ★ <b>「直供是否必须先认出类型下标」开关</b>（★ 2026-10-27 新增，<b>默认 = 新行为</b>）。
     *
     * <p>{@code false}（<b>默认</b>）⇒ 直供的准入条件只剩「<b>这条配方确实被解出来了</b>」
     * （{@link #lastMatchedSlotRecipe} 非 {@code null}）—— <b>不再要求</b>「类型下标已认出来」。
     * <p>{@code true} ⇒ 逐字退回加本行之前的口径：{@code detectedTypeIndex < 0}（没认出来 / 歧义）⇒ 一律不直供。
     *
     * <p><b>为什么改成"配方在手即供"（依据 = 山海的做法，逐字取证）</b>：山海
     * {@code _census/shanhai-src/com/dishanhai/gt_shanhai/common/machine/part/RecipeTypePatternBufferPartMachine.java:1051-1061}
     * 的 {@code gtShanhai$slotAllowsRecipe(slot, recipe)} 只做一件事 ——
     * 「<b>把手上这条 recipe 与这个槽允许的 recipe 比一比</b>」；调用侧 {@code :661-679}
     * 也是「先拿到 recipe → 校验 → 直接激活」，<b>整个过程没有"先确认类型下标"这一步</b>。
     * ⇒ 山海的准入判据是「<b>配方身份</b>」，不是「类型下标」。
     * 类型下标只是**识别过程**的副产品；把它当直供的前置条件，等于"识别不出来就宁可让机器
     * 自己再翻一遍全表"，与山海口径不一致（本任务的依据）。
     *
     * <p><b>诚实边界（今天不改变任何一次直供的结果）</b>：{@link #scanTypesForMatch} 有硬纪律
     * 「歧义 ⇒ 把 {@link #lastMatchedSlotRecipe} 清成 null」，加上 {@link #detectRecipeType} 入口
     * 也清一次 ⇒ 在**只跑 ③ 之前**的代码里，「配方非 null」与「唯一命中（下标 ≥ 0）」本来就是同一件事，
     * 所以本行默认值对今天的直供是<b>纯口径对齐、零行为增量</b>。
     * 它真正被吃到是在 ③ 落地之后：③ 在歧义上按该槽缓存解出配方时，可能「<b>配方在手、但类型下标
     * 在本次控制器类型表里定位不到</b>」（⇒ ③ 返回 -1 而配方在手）—— 这一条<b>只有新口径</b>才会直供，
     * 而且它完全符合山海"校验配方身份即可"的形状 ✓（旧口径会把它丢掉）。
     *
     * <p><b>一行回退</b>：把本行置 {@code true}。
     */
    public static final boolean DIRECT_SLOT_RECIPE_SUPPLY_REQUIRES_TYPE_INDEX = false;

    /**
     * ★ 2026-10-27【直供配方】：把「该槽对应的那条配方」写进该槽的配方缓存 ——
     * 机器随后从 {@code getCachedGTRecipe()} 把它当<b>附加候选</b>直接拿到，不必遍历配方表去找它。
     *
     * <p><b>为什么用 {@code setSlotCacheRecipe} 而不是自己再造一张表</b>：这就是 gtlcore 官方
     * 「这台舱室能做哪些配方」的唯一登记口（javap 实证：
     * {@code IMEPatternTrait.setSlotCacheRecipe(int, GTRecipe)} / {@code getCachedGTRecipe()}，
     * `_javap_simple_meiobase.txt:48-60`；消费链 {@code MEPatternRecipeHandlePart.getCachedGTRecipe()}
     * `_javap_simple_handleparts.txt:1491-1497` → 上游 mixin `setAdditionalRecipes`）。
     * 我们只<b>调用</b>：不覆写、不混入、不改上游任何方法。
     *
     * <p><b>纪律（逐条 fail-open，"宁可少喂一次，绝不喂错/绝不误挡"）</b>：
     * <ol>
     *   <li>总开关关 ⇒ 什么都不做；「必须先认出类型下标」子开关
     *       （{@link #DIRECT_SLOT_RECIPE_SUPPLY_REQUIRES_TYPE_INDEX}，<b>默认关</b>）为开时，
     *       类型下标 {@code < 0}（没识别出来 / 歧义）⇒ 什么都不做；</li>
     *   <li>没有「唯一命中的配方」（{@link #lastMatchedSlotRecipe} 为 null）⇒ 什么都不做；</li>
     *   <li>客户端 ⇒ 什么都不做（配方缓存是服务端的事，与 {@code markPatternTableStale} 同一道门）；</li>
     *   <li>该槽<b>已经有</b>配方缓存（{@code hasCacheInSlot}：gtlcore 自己学到的，或我们已经喂过）
     *       ⇒ <b>不写、不覆盖</b> —— 绝不去动上游已经记账的内容；</li>
     *   <li>任何异常（槽越界 / trait 未就绪 / 尚未成形）⇒ 吞掉并退回今天的行为
     *       （= 机器自己搜配方表），<b>绝不影响扣料</b>。</li>
     * </ol>
     *
     * <p><b>诚实边界（未做到的，见实施记录 §④/§⑤）</b>：本方法把配方送上机器的<b>候选集</b>
     * （发现层），它<b>不会</b>把配方写进 {@code MEPatternRecipeHandlePart.recipes2SlotsMap}
     * （槽级快路径，扣料层）—— 那条路的运行时入口只有 {@code restoreMachineCache}，
     * 而它只能由 {@code IRecipeCapabilityMachine.upDate()} / 结构成形触发（非 mixin 无安全触发点）。
     * 因此"被查找侧收窄挡在门外的槽"仍然要靠收窄侧自己的修复（`_census/收窄与多类型_冲突分析.md` 甲方案），
     * 本方法不替代它、也绝不与它冲突（两者作用的层不同，见实施记录 §⑤）。
     *
     * @param slot             样板槽下标
     * @param detectedTypeIndex {@link #detectRecipeType(int, List)} 的结论。
     *                          ★ 2026-10-27【①】起<b>它不再是准入条件</b>：默认开关下本方法<b>不看它</b>，
     *                          只看 {@link #lastMatchedSlotRecipe} 在不在手上；只有把
     *                          {@link #DIRECT_SLOT_RECIPE_SUPPLY_REQUIRES_TYPE_INDEX} 置 {@code true}
     *                          才恢复"必须 {@code >= 0} 才算唯一识别"的旧口径。
     */
    private void supplyResolvedRecipeToMachine(int slot, int detectedTypeIndex) {
        if (!DIRECT_SLOT_RECIPE_SUPPLY) {
            return;   // 开关关 ⇒ 本功能整体不执行（逐字退回加这段之前的机器行为）
        }
        if (DIRECT_SLOT_RECIPE_SUPPLY_REQUIRES_TYPE_INDEX && detectedTypeIndex < 0) {
            return;   // ①（回退口径）没识别出唯一类型（含歧义 -1）⇒ 不直供
        }
        // ★ 2026-10-27【①】新口径（默认）：**不再看类型下标**，只看"这条配方有没有被解出来"。
        //   依据 = 山海（`gtShanhai$slotAllowsRecipe` 只比配方身份、没有"先确认类型下标"这一步，
        //   见上面 DIRECT_SLOT_RECIPE_SUPPLY_REQUIRES_TYPE_INDEX 的取证）。
        //   安全性由下面 ②③④⑤ 四道既有纪律兜住（尤其 ④：该槽已有缓存就绝不覆盖上游记账）。
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
     * ★ <b>「认不出类型下标时，按该槽已解出的配方反查并锁模式」开关</b>
     * （★ 2026-10-27 新增，<b>默认 = 新行为</b>）。
     *
     * <p>{@code true}（<b>默认</b>）⇒ {@link #lockRecipeTypeForSlot(int)} 在
     * {@code recipeTypeOfSlot(slot) < 0}（识别不出类型 / 歧义）时，先拿
     * {@link #slotCachedRecipe(int, IPatternDetails)} 读该槽**已经解出来的**配方，
     * 用它自己的 {@code recipe.recipeType} 反查下标并锁模式；
     * {@code false} ⇒ 逐字退回旧口径：{@code index < 0} 直接 {@code return}（绝不动控制器）。
     *
     * <p><b>为什么要改（这是"料在槽里却不干活"的高发路径）</b>：旧的 fail-open 是
     * 「认不出来 ⇒ 不动控制器」（2026-09-26 为修"把玩家选好的车床模式抢回卷板机"而加，
     * 逐字取证见 {@link #lockRecipeTypeForSlot(int)} 方法体里那段注释）。但控制器因此停在
     * **上一次的模式**上，而 GT 的 {@code RecipeLogic.searchRecipe()} **只在当前激活的那一种
     * 配方类型里搜**（本类类头注释已取证）⇒ 主机若不在该样板所属类型上，就退回"搜不到/全表搜"：
     * 料进了槽、机器却不工作。
     * 旧口径把「已知的正确答案」丢掉了 —— 该槽的 GT 配方缓存里**本来就有**这台机器跑过的那条配方
     * （键就是槽下标，取证见 {@link #slotCachedRecipe(int, IPatternDetails)}），
     * 它自带 {@code recipe.recipeType} ⇒ 这是一个**免费、且已经解出来**的类型来源。
     *
     * <p><b>为什么不违反"不抢玩家模式"那条教训</b>：那条教训禁止的是**回落到我们缓存的旧下标**
     * （{@code activeRecipeTypeIndex}：一个与"这个槽"无关的全局值）；
     * 本开关用的是「**这个槽自己的**配方缓存里那条配方的类型」——
     * 它要么是主机自己配过、要么是我们刚直供过的**该槽专属**事实，不是全局旧值 ✓。
     * 而且判据是「该槽缓存里**只能有一种**类型」：多类型 ⇒ 返回 -1 ⇒ 照旧不动控制器（不猜）✓。
     *
     * <p><b>一行回退</b>：把本行置 {@code false}（⇒ 与加本行之前<b>逐字一致</b>）。
     */
    public static final boolean LOCK_TYPE_FROM_SLOT_RECIPE_CACHE = true;

    /**
     * ★ 2026-10-27【②③ 共用】：读「<b>该槽已经解出来的配方</b>」（只读，不新建第二套表）。
     *
     * <p><b>读的是什么（取证）</b>：gtlcore 官方的槽级配方缓存
     * {@code IMEPatternTrait.getSlot2RecipesCache()} —— 接口原文
     * {@code _census/gtlcore-unofficial/gtlcore-Unofficial-main/src/main/java/org/gtlcore/gtlcore/api/machine/trait/MEPart/IMEPatternTrait.java:18}
     * 「{@code @NotNull Int2ReferenceMap<ObjectSet<@NotNull GTRecipe>> getSlot2RecipesCache();}」，
     * 具体实现同仓库 {@code .../common/machine/multiblock/part/ae/MEPatternBufferPartMachine.java:990-993}
     * （返回 {@code recipeMultipleCacheMap}）。
     * 这张表的**键就是样板槽下标**、值是该槽「主机已经认过 / 我们直供过」的配方集
     * ⇒ 这正是「该槽已经解出来的配方」的**唯一权威来源**（我们只**调用**公开接口：不覆写、不混入）。
     * 本类 {@code getMETrait()} 的静态类型就是 {@code IMEPatternTrait}
     * （{@code _census/_javap_simple_gtlcore_bufferbase.txt:686}
     * 「{@code public IMEPatternTrait getMETrait();}」）⇒ <b>无需任何强转</b>
     * （惰性机那条 {@code (LazyMEPatternTrait)} 强转在本机上是 {@code ClassCastException}，
     * 见 {@link #clearSlotRecipeCache(int)} 的注释 ⇒ 这里绝不再犯）。
     *
     * <p><b>判据</b>：该槽缓存里**只能有一种** {@code recipeType} 才返回那一条；
     * 空 / 读不到 / 两种以上类型 ⇒ {@code null}（<b>不猜</b>）。
     *
     * @param patternOrNull 非 {@code null} ⇒ 再用 {@link #patternIdentityMatches} 过一遍身份
     *                      （挡掉"样板换过而缓存未清"的陈旧条目；③ 用）；
     *                      {@code null} ⇒ 不做身份过滤（② 用：②只反查类型，而 gtlcore 自己的
     *                      {@code onPatternChange} 已在样板变化时逐槽清缓存，
     *                      取证 {@code .../MEPatternBufferPartMachine.java:270-273}）。
     * @return 该槽唯一的已解出配方；无法唯一确定 ⇒ {@code null}
     */
    private GTRecipe slotCachedRecipe(int slot, IPatternDetails patternOrNull) {
        try {
            if (slot < 0) {
                return null;
            }
            it.unimi.dsi.fastutil.ints.Int2ReferenceMap<it.unimi.dsi.fastutil.objects.ObjectSet<GTRecipe>> cache =
                    getMETrait().getSlot2RecipesCache();
            if (cache == null) {
                return null;
            }
            it.unimi.dsi.fastutil.objects.ObjectSet<GTRecipe> cached = cache.get(slot);
            if (cached == null || cached.isEmpty()) {
                return null;
            }
            GTRecipe unique = null;
            for (GTRecipe recipe : cached) {
                if (recipe == null || recipe.recipeType == null) {
                    continue;
                }
                if (patternOrNull != null && !patternIdentityMatches(recipe, patternOrNull)) {
                    continue;   // 身份不符 ⇒ "样板换过而缓存未清"的陈旧条目，绝不用它定类型
                }
                if (unique == null) {
                    unique = recipe;
                } else if (unique.recipeType != recipe.recipeType) {
                    return null;   // 两种类型都在这槽的缓存里 ⇒ 分不出是哪一种，不猜
                }
            }
            return unique;
        } catch (Throwable ignored) {
            return null;   // 只读判据：任何异常都退回"没读到"（fail-open，绝不影响扣料）
        }
    }

    /** {@code recipe.recipeType} 在控制器当前类型表里的下标（{@code GTRecipeType} 是注册期单例，直接比引用）。 */
    private static int typeIndexOfRecipeType(List<GTRecipeType> types, GTRecipeType recipeType) {
        if (types == null || recipeType == null) {
            return -1;
        }
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i) == recipeType) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 把控制器的搜索端<b>锁到这个槽位的模式</b>上。
     *
     * <p>两步都不能少：切类型 + 作废 {@code lastRecipe}。只切类型的话，
     * GT 会继续优先跑上一次那条配方，照样会串。
     */
    private void lockRecipeTypeForSlot(int slot) {
        List<GTRecipeType> types = getAvailableRecipeTypes();
        if (types.size() <= 1) {
            return;
        }
        int index = recipeTypeOfSlot(slot);
        if (index < 0 && LOCK_TYPE_FROM_SLOT_RECIPE_CACHE) {
            // ★ 2026-10-27【②】识别不出类型（含歧义 -1）⇒ 不再直接放弃，而是问**这个槽自己**的
            //   GT 配方缓存「这台机器在这个槽上跑过哪条配方」，用那条配方的 recipe.recipeType 反查下标。
            //   只在**该槽缓存只有一种类型**时才采用（多类型 ⇒ typeIndexOfRecipeType 拿不到 ⇒ -1
            //   ⇒ 照旧不动控制器，不猜）。
            //   依据：山海拿到 recipe → 校验槽允许 → 直接激活（`gtShanhai$slotAllowsRecipe`
            //   :1051-1061），类型只是识别过程的副产品，不该是"能不能干活"的前提。
            //   一行回退：把 LOCK_TYPE_FROM_SLOT_RECIPE_CACHE 置 false（本 if 整块失效 ⇒ 逐字退回旧口径）。
            GTRecipe cachedForSlot = slotCachedRecipe(slot, null);
            if (cachedForSlot != null) {
                index = typeIndexOfRecipeType(types, cachedForSlot.recipeType);
                if (index >= 0) {
                    // 验证抓手（debug 级）：证明"这一次模式真的是靠该槽已有的配方定下来的"，而不是回落旧下标
                    GTLModular.LOGGER.debug("[gtlmodular] 锁模式【②按槽缓存反查】槽 {} 识别不出类型，"
                                    + "但该槽已解出的配方属于『{}』⇒ 锁到下标 {} @{}",
                            slot + 1, tableNameOf(cachedForSlot.recipeType), index, getPos());
                }
            }
        }
        if (index < 0 || index >= types.size()) {
            // ★ 2026-09-26 修（队长：「截图那个位置的样板总成，它会把我机器从车床模式调成卷板机，我这台就是车床模式的」）：
            //   以前这里会**回落到我们缓存的下标**（activeRecipeTypeIndex）并强行设置控制器 ——
            //   一旦缓存是旧值（玩家手动把主机设成车床=5、我们缓存还停在卷板机=0），
            //   任何"识别不出类型"的样板一推进来，就会把控制器**抢回旧模式**。
            //   实机日志实证：同一台 -27,64,-6（41 张全是类型 5）
            //     10:48:02 当前=5/11 … 找到目标=-1（自动切模式明确没切）
            //     10:52:08 当前=0/11 … 样板依旧是 5 ⇒ 不是自动切模式干的，就是这条回落干的。
            //   ⇒ 改成 **fail-open：认不出来就绝不动控制器**。宁可少锁一次，也不抢玩家选好的模式。
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
        // 正在跑配方时不动：配方一旦开始，输入就已经扣掉了，这时候打断（reset）会让已扣的料直接蒸发。
        // 等它做完自然会回到 WAITING，下一批样板再切。
        if (logic.isWorking()) {
            return;
        }
        controller.setActiveRecipeType(index);
        // 只切类型不够：GT 的 RecipeLogic 会优先继续跑 lastRecipe，必须 reset 掉才会按新模式重新搜
        logic.resetRecipeLogic();
        // ★ 0.3.2 后半：模式真的换了 ⇒ 清掉**该槽**「主机已经配上过的旧配方缓存」。
        //   这是推样板的热路径，所以只清这一个槽（按需、最便宜）：该槽的缓存正是"可能在上一个模式下
        //   被配上过"的那一份；其余槽会在各自被推送时（或自动切换时）按同样的规则清。
        if (clearSlotRecipeCache(slot)) {
            // 验证抓手（debug 级，避免多模式频繁互切时刷屏）
            GTLModular.LOGGER.debug("[gtlmodular] 锁模式清缓存: 槽 {} 旧配方已作废（锁到模式 {}）@{}",
                    slot + 1, index, getPos());
        }
        this.activeRecipeTypeIndex = index;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        // AE 推回来的可能是我们改写过的样板对象，先翻译回仓里那个原样板，槽位才认得出来
        IPatternDetails original = rewrittenToOriginal.getOrDefault(patternDetails, patternDetails);
        boolean aePushedOriginal = original == patternDetails;
        int mul = effectiveMul();
        int div = effectiveDiv();
        Level pushLevel = getLevel();
        boolean scalingApplies = aePushedOriginal && isRewriteEnabled() && inputHolder != null && (mul != 1 || div != 1);

        // ===== (b) 存量不足就拒收 =====
        // 解决"AE 把中间品搬进合成 CPU 交给供应器、而 gtlcore 样板总成只能从网络取料"造成的死锁：
        // 网络可用量 < 本次需求（含机器侧放大）时直接返回 false。AE 会把这次派发当作失败，
        // 释放/退回它预留的那批料，稍后重试时机器就取得到了 —— 于是大单能一批批滚动推进，
        // 不需要一次囤够料、也不需要玩家手工分批。
        // 注意：无限流体/物品元件返回 Long.MAX_VALUE，天然通过；查询失败则 fail-open 不拦。
        boolean stockShort = false;
        String shortName = "";
        long shortNeed = 0;
        long shortHave = 0;
        appeng.api.stacks.AEKey shortKey = null;
        try {
            var stockGrid = getMainNode().getGrid();
            if (stockGrid != null && inputHolder != null) {
                KeyCounter avail = stockGrid.getStorageService().getInventory().getAvailableStacks();
                outer:
                for (KeyCounter counter : inputHolder) {
                    if (counter == null) {
                        continue;
                    }
                    for (var entry : counter) {
                        // ★ 0.3.132【撤自造 · 与 (c) 同步】本格以前是 `scalingApplies ? 放大后的量 : 原量`。
                        //   机器侧已不再放大索取 ⇒ 本次**真实消费量就是 AE 推来的原量**；
                        //   若仍按放大后的量比库存，就会在"库存够做 1 倍、不够做 N 倍"时报出
                        //   与实际消费不符的"料不够"告警（伪造读数）。
                        //   一行回退：把本行改回 `scalingApplies ? Math.max(1, entry.getLongValue() * mul / div) : entry.getLongValue()`。
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
                            } catch (RuntimeException ex) {
                                shortName = entry.getKey().getId().toString();
                            }
                            break outer;
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // 查不到就不判断
        }
        if (stockShort && pushLevel != null) {
            // ★ 2026-09-21 队长反馈「这样突然卡住，真的很无奈」⇒ 本条**降噪 + 说清是哪一种卡**：
            //   · 节流：30 秒最多一条（原来 20 tick = 每秒一条，实机刷了 67 条）；`lastStuckLogTick` 独立于调试开关；
            //   · 分层：查 AE 的**在途量**（`ICraftingService.getRequestedAmount`，被合成任务预定的量）——
            //       在途 > 0 ⇒ 上游还在做，等它就好（INFO，30 秒一条）；
            //       在途 = 0 且可用 = 0 ⇒ **没有任何任务在合成它** = 真死锁占用（WARN，每个物品只喊一次），
            //         并给出可执行动作：「全部取消」再重下。
            long nowTick = pushLevel.getGameTime();
            long inFlight = 0;
            // ★★ 0.3.132【只加读数 · C1】★★ 再问一格 AE：**这个 key 现在还有没有合成任务在追踪**。
            //   `isRequesting(AEKey)` 是 **AE2 自己的方法**（gtlcore 只是 @Inject 扩展它覆盖超限 CPU，
            //   见 `CraftingServiceMixin:284-295`）⇒ 可以直接调、不需要任何自己的机制。
            //   为什么要它：`在途`（getRequestedAmount）= "被预定要产出的量"，它为 0 时**分不出**
            //     (a) 这台机器根本没在 AE 里下过单（玩家手工喂料）——那这条 WARN 是"没有 AE 任务"；
            //     (b) 有 AE 任务，但料处在"已交付未消费"的中间态 —— 那才是真被占住。
            //   `isRequesting=true` 而 `在途=0` ⇒ 属 (b) 一侧；`false` ⇒ 属 (a) 一侧。
            //   ⇒ 这一格就是判"到底该治 AE 侧还是治机器侧"的分水岭。
            //   纯只读、零副作用；查不到就保持 false（fail-safe，不自己造读数）。
            boolean aeRequesting = false;
            boolean aeRequestingAny = false;
            try {
                var stockGrid2 = getMainNode().getGrid();
                if (stockGrid2 != null && shortKey != null) {
                    inFlight = stockGrid2.getCraftingService().getRequestedAmount(shortKey);
                    aeRequesting = stockGrid2.getCraftingService().isRequesting(shortKey);
                    // ★★ 0.3.132【C1 补强】再问一格：**整个 AE 到底还有没有手工合成任务在跑**。
                    //   为什么要它：`isRequesting(key)` 只说明"这个 key 有人在等/在做"；
                    //   而真相可能是"**整条手工合成队列此刻确实一个任务都没有**"（那才是真该让玩家去处理的情况）。
                    //   两者一起看，才能把「空窗期」与「AE 侧真的没任务」分开——
                    //   前者不该催玩家「全部取消」，后者才可以提示玩家。
                    //   出处：`CraftingServiceMixin:297-308`（gtlcore @Inject，覆盖超限 CPU）+
                    //   `TransfiniteComputationArrayMachine:407-417`（聚合实现）。同样纯只读、零副作用。
                    aeRequestingAny = stockGrid2.getCraftingService().isRequestingAny();
                }
            } catch (RuntimeException ignored) {
                // 查不到就不报在途量 / 不报追踪态（绝不因为查询失败而造出一个读数）
            }
            boolean deadlock = shortHave <= 0 && inFlight <= 0;
            String stuckId = shortKey == null ? "?" : String.valueOf(shortKey.getId());
            // ★ 0.3.132【C1】去重口径改成：
            // ★★ 0.3.132【C1 · 措辞与去重口径修正】★★
            //   旧口径：`网络可用<=0 && 在途<=0` ⇒ 直接判「被占用且没有任何任务在合成它」+ 催玩家「全部取消」。
            //   实机取证（01:24 那次会话 4 条 WARN 全部命中）证明这条判据**不成立**：
            //     4 条的 `AE追踪(isRequesting(key))` **全为 true** ⇒ AE 那边**确实还有任务**在做这个 key，
            //     之前的「没有任何任务在合成它」是**误报**，而"全部取消"这个建议对空窗期毫无用处。
            //   ⇒ 新口径：**只有在「整个 AE 一个手工合成任务都没有」时才敢说是真死锁**（`isRequestingAny=false`）；
            //     只要 AE 还有任务在追踪，就按"空窗期/等它"如实说，**不催玩家做无效动作**。
            //
            //   去重口径（同步改对）：
            //     · `trueDeadlock`（AE 全局无任务，玩家确实该处理）⇒ 保留"每个物品只喊一次"永久去重（有界）；
            //     · 其余（含 `windowLikely`）⇒ **绝不永久静音**：同一物品按 STUCK_LOG_INTERVAL_TICKS 限流复报。
            //       否则第一次的误判会把后续真相永久盖掉 —— 我们这一路的排查正是被这一点带偏的。
            //   一行回退：把 STUCK_WARN_REPEAT_WHEN_TRACKED 改成 false ⇒ 逐字回到"每个物品只喊一次"。
            boolean trueDeadlock = shortHave <= 0 && inFlight <= 0 && aeRequestingAny;
            // 注：`stuckId` 在上面（节流分层那段）已经声明过，这里**不要重复声明**（编译期会报 already defined）。
            boolean firstTimeForId = stuckWarned.size() < STUCK_WARNED_LIMIT && stuckWarned.add(stuckId);
            boolean repeatAllowed = STUCK_WARN_REPEAT_WHEN_TRACKED && !trueDeadlock
                    && nowTick - stuckWarnedTick.getOrDefault(stuckId, Long.MIN_VALUE) >= STUCK_LOG_INTERVAL_TICKS;
            if (nowTick - lastStuckLogTick >= STUCK_LOG_INTERVAL_TICKS
                    && (!(shortHave <= 0 && inFlight <= 0) || firstTimeForId || repeatAllowed)) {
                lastStuckLogTick = nowTick;
                stuckWarnedTick.put(stuckId, nowTick);
                if (shortHave > 0) {
                    GTLModular.LOGGER.info("[gtlmodular] 提示：{} 存量不足（网络可用={}，在途={}，AE追踪本件={}，AE全局有任务={}，"
                                    + "本次需要={}）@{}（本条 30 秒最多一条）",
                            shortName, shortHave, inFlight, aeRequesting, aeRequestingAny, shortNeed, getPos());
                } else if (inFlight > 0) {
                    GTLModular.LOGGER.info("[gtlmodular] 提示：{} 还没进网络（可用=0，**在合成中={}**，AE追踪本件={}，"
                                    + "本次需要={}）@{} —— 上游还在做，等它就好（本条 30 秒最多一条）",
                            shortName, inFlight, aeRequesting, shortNeed, getPos());
                } else if (aeRequesting || trueDeadlock) {
                    // ★ 档③（修正后）：`AE` 仍有任务在追踪这个 key（或全局有任务）⇒ **这是空窗期**。
                    //   ★ 措辞修正的关键：**不再说"没有任何任务在合成它"、不再催「全部取消」**——
                    //     实机已证明那两句是误报，而且会把玩家引去做无效动作。
                    GTLModular.LOGGER.info("[gtlmodular] 提示：{} **料暂时取不到：网络可用=0、在途=0，但 AE 侧仍有合成任务**"
                                    + "（AE追踪本件={}，AE全局有任务={}，本次需要={}）@{} —— 这是「料已交付、还没被消费/产出」的"
                                    + "**空窗期**：请等下一轮 `AE 推送样板`（对照 `累计推来` 看是不是刚被吃光），"
                                    + "**不要**「全部取消」（那对空窗期没有帮助，只会把已排好的任务丢掉）。"
                                    + "（本条 30 秒最多一条。0.3.132【C1】修正：旧文案「被占用且没有任何任务在合成它」经实机取证是误报）",
                            shortName, aeRequesting, aeRequestingAny, shortNeed, getPos());
                } else {
                    // ★ 档④（真正该玩家处理的那一档）：**整个 AE 一个手工合成任务都没有**，而料又不在网络里。
                    //   典型成因：这些料正被**这台机器的内部槽**占着（AE 推来后没被消费掉的那一批）。
                    GTLModular.LOGGER.warn("[gtlmodular] 提示：{} **被占用，且整个 AE 当前没有任何手工合成任务**"
                                    + "（网络可用=0，在途=0，AE追踪本件={}，AE全局有任务={}，本次需要={}）@{} —— "
                                    + "这是真占用：料很可能卡在本机内部槽里（推来后没被消费掉）。"
                                    + "可先在 AE 终端「全部取消」再重新下单；若反复出现，请把本行与同一时刻的 "
                                    + "`AE 推送样板` / `累计推来` / `独立线程` 三行一起留档。（每个物品只提醒一次）",
                            shortName, aeRequesting, aeRequestingAny, shortNeed, getPos());
                }
            }
        }

        // ★★ 0.3.132【撤自造 · 机器侧二次放大】原块已删（方案 §⑤-A1 / 第 1 期）★★
        //   原行为：`if (scalingApplies) { 把 inputHolder 每项 × mul ÷ div }`。
        //   为什么撤：原初的 `pushPattern` **从不改 inputHolder**
        //   （`MEPatternBufferPartMachineBase.pushPattern` 只做「节点在线 + 找槽 + 落槽 + return true」，
        //     出处 `_census/NAC合成链路_原初对照与重构方案.md` §⑧-5 / §①-1 的 H2）；
        //   而这一块让"机器索取量 = AE 备料量 × mul/div"，AE 只按它自己那份备料
        //   ⇒ 声明量与实际索取量不一致，多要的那部分在 AE 那边没有额度（该方案 §③ D2 / §④-M2 滞留点 2）。
        //   ⚠ 一条必须并列登记的事实（方案未点透）：删掉本块后，**若 AE 仍按缓存的【原样板】推送**，
        //     倍率/神锻在机器侧就不再生效（按 AE 声明的小量做）—— 这是**自洽的回落**（与 AE 一致），
        //     而不是"修好了"。要让它重新生效，只能走 §⑤-A2（让 AE 手上只有改写样板），需队长裁决。
        //   一行回退（逐字）：把上面两行注释到 `}` 的原块原样贴回，并把下面日志的 `false` 改回 `machineSideScaled`。
        final boolean machineSideScaled = false;
        // ★ 0.3.132【撤自造】把"这次倍率/神锻没生效"如实说一次（每个实例只一次；见字段 javadoc）。
        if (scalingApplies && !this.machineScaleDisabledWarned) {
            this.machineScaleDisabledWarned = true;
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成 @{}：AE 推送的是【原样板】而机器侧二次放大已按 0.3.132 撤销 "
                            + "⇒ 本次按 AE 声明量执行，倍率/神锻未在机器侧生效（有效缩放[{}]）。"
                            + "这是与 AE 一致的回落，不是故障；根治见 _census/NAC合成链路_原初对照与重构方案.md §⑤-A2。",
                    getPos(), describeEffectiveScale());
        }
        // 诊断（啰嗦，默认关）：调试开关打开时，1 秒最多一条，记录这次推送的明细/内部槽/网络存量对比。
        // 「提示：XX 被合成 CPU 占用」与「身份闸拒绝配方」两条只在出问题时才触发，不受该开关影响。
        boolean logThisPush = false;
        if (GTLModularConfig.isPatternDebugLogsEnabled()
                && pushLevel != null && pushLevel.getGameTime() - lastPushLogTick >= 20) {
            lastPushLogTick = pushLevel.getGameTime();
            logThisPush = true;
            long pushed = 0;
            if (inputHolder != null) {
                for (KeyCounter c : inputHolder) {
                    if (c != null) {
                        for (var entry : c) {
                            pushed += entry.getLongValue();
                        }
                    }
                }
            }
            // 逐项明细（最多 5 项）：定位"配方材料不足"时，一眼看出 AE 到底推了什么、各多少
            StringBuilder detail = new StringBuilder();
            int shown = 0;
            if (inputHolder != null) {
                for (KeyCounter c : inputHolder) {
                    if (c == null) {
                        continue;
                    }
                    for (var entry : c) {
                        if (shown++ >= 5) {
                            detail.append(" …");
                            break;
                        }
                        detail.append(' ').append(entry.getKey().getId()).append('=').append(entry.getLongValue());
                    }
                    if (shown > 5) {
                        break;
                    }
                }
            }
            // ★★ 0.3.132【只加读数】把本次推来的量**先**累加进「累计收到量」，**再**打印 ★★
            //   ⚠ 修正：上一版把累加写在打印**之后** ⇒ 那格显示的是"**截至上一次**"的累计（第一条恒为 0），
            //     读起来像"没推过"。现在挪到打印之前 ⇒ 打印出来的累计**含本次**，可直接当"AE 一共推了多少"用。
            //   口径：物品与流体分开累计（不同单位不同池），都是饱和加（永不溢出，与全工程 saturatingAdd 同纪律）。
            //   纯诊断、不参与任何判定；只在 `logThisPush`（= debugPatternLogs 且 1 秒 1 条）时累加。
            //   为什么要它：能报"这一次推了多少"，但报不出"一共推了多少" ⇒ 无法与"机器一共吃掉多少"对比，
            //   也就判不出那条链断在哪：**AE 根本没推**（累计≈0）还是 **推了但机器没消费**（累计很大而槽仍空）。
            //   一行回退：把下面这两段挪回 `LOGGER.info(...)` 之后。
            pushedToMachineTotal = pushedToMachineTotal > Long.MAX_VALUE - pushed
                    ? Long.MAX_VALUE : pushedToMachineTotal + pushed;
            long pushedFluid = 0L;
            if (inputHolder != null) {
                for (KeyCounter c : inputHolder) {
                    if (c == null) {
                        continue;
                    }
                    for (var entry : c) {
                        if (entry.getKey() instanceof appeng.api.stacks.AEFluidKey) {
                            pushedFluid = pushedFluid > Long.MAX_VALUE - entry.getLongValue()
                                    ? Long.MAX_VALUE : pushedFluid + entry.getLongValue();
                        }
                    }
                }
            }
            pushedToMachineFluidTotal = pushedToMachineFluidTotal > Long.MAX_VALUE - pushedFluid
                    ? Long.MAX_VALUE : pushedToMachineFluidTotal + pushedFluid;
            GTLModular.LOGGER.info("[gtlmodular] AE 推送样板 @{}: 是否缩放样板={} 机器侧放大={} 输入总量={} 有效缩放[{}] 累计推来[物品={}；流体={}] 明细:{}",
                    getPos(), !aePushedOriginal, machineSideScaled, pushed, describeEffectiveScale(),
                    pushedToMachineTotal, pushedToMachineFluidTotal, detail);
        }
        Integer slot = getSlotIndexForPattern(original);
        if (slot != null && slot >= 0) {
            lockRecipeTypeForSlot(slot);
        }
        boolean pushedOk = super.pushPattern(original, inputHolder);
        // 诊断（临时）：推送后立刻看机器内部槽到底拿到了多少料 —— 用来区分
        // "网络里没有料（内部槽为空）" 与 "料拉进来了但配方/类型不认（内部槽有料）"。
        if (logThisPush && slot != null && slot >= 0) {
            try {
                var internal = getInternalSlot(slot);
                if (internal == null) {
                    GTLModular.LOGGER.info("[gtlmodular] 推送后内部槽[{}]: 取不到（null） 返回值={}", slot + 1, pushedOk);
                } else {
                    GTLModular.LOGGER.info("[gtlmodular] 推送后内部槽[{}]: 返回值={} 物品条目={} 流体条目={}",
                            slot + 1, pushedOk, internal.getItemStackInputMap().size(), internal.getFluidStackInputMap().size());
                }
            } catch (RuntimeException e) {
                GTLModular.LOGGER.info("[gtlmodular] 内部槽读取失败: {}", e.toString());
            }
        }
        // 诊断（临时）：把"推送要求量"与"网络实际可用量"并排打出来 —— 一眼看出缺多少。
        // 注意：合成任务可能已经把料"预留"了，此时网络可用量会显示偏低甚至 0；
        // 若显示 0 而你确认仓里有料，说明料被本任务占住 → 那就不是缺料，而是机器取不到。
        if (logThisPush) {
            try {
                var grid = getMainNode().getGrid();
                if (grid != null) {
                    KeyCounter stock = grid.getStorageService().getInventory().getAvailableStacks();
                    StringBuilder cmp = new StringBuilder();
                    int n = 0;
                    if (inputHolder != null) {
                        for (KeyCounter c : inputHolder) {
                            if (c == null) {
                                continue;
                            }
                            for (var e : c) {
                                if (n++ >= 5) {
                                    cmp.append(" …");
                                    break;
                                }
                                cmp.append(' ').append(e.getKey().getId())
                                        .append(" 需要=").append(e.getLongValue())
                                        .append(" 网络可用=").append(stock.get(e.getKey()));
                            }
                            if (n > 5) {
                                break;
                            }
                        }
                    }
                    GTLModular.LOGGER.info("[gtlmodular] 网络存量对比:{}", cmp);
                }
            } catch (RuntimeException e) {
                GTLModular.LOGGER.info("[gtlmodular] 网络存量查询失败: {}", e.toString());
            }
        }
        return pushedOk;
    }

    // ================= 自动切换配方模式（做完一种模式，自己换下一种）=================

    /**
     * 自动模式轮询：当前模式已经跑不动（既不 WORKING，处于 IDLE/WAITING —— 这一模式的料吃完或卡住）时，
     * 扫描仓里其它「有料在槽位等着」的样板槽，把控制器切到那个模式并重置配方逻辑。
     * 所以一台机器可以同时挂多种模式的样板、轮流自动跑，省一台机器。
     *
     * <p>三条安全线：<br>
     * ① 只在机器<b>没在跑配方</b>时切（跑动中打断会让已扣输入蒸发）；<br>
     * ② 切过去 40 tick 还没开跑 → 这个类型临时拉黑 200 tick，避免两个模式之间来回横跳；<br>
     * ③ 只挑「类型已识别 + 槽里真有料」的模式，识别不出来的槽一律不选。
     *
     * <p>★ 本方法只是<b>防外抛</b>的壳（订阅是 {@code subscribeServerTick}，抛出去会打断服务端 tick）；
     * 逻辑一行没动，全在 {@link #autoSwitchTickInner()} 里。
     */
    private void autoSwitchTick() {
        try {
            autoSwitchTickInner();
        } catch (Throwable failure) {
            // 同类异常最多 30 秒一条，绝不刷屏；tick 路径一律不许把异常抛出去
            long nowMs = System.currentTimeMillis();
            if (nowMs - lastAutoSwitchWarnTick >= 30_000L) {
                lastAutoSwitchWarnTick = nowMs;
                GTLModular.LOGGER.warn("[gtlmodular] 自动切模式 tick 异常（已吞掉，不影响机器本身）：pos={} {}",
                        getPos(), failure.toString());
            }
        }
    }

    private void autoSwitchTickInner() {
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
        // 成型材质补丁：重进世界后让客户端补一次烘焙（见 FormedRenderSyncNudge 的取证注释）
        if (formedNudgeTicks > 0) {
            formedNudgeTicks = FormedRenderSyncNudge.tick(this, formedNudgeTicks, "猪咪");
        }
        // ★★ 2026-09-26（队长：「我们的现役机也有这种问题」）：**兜底消费**基类的样板同步标记 ★★
        //   gtlcore 具体类在样板变化时会 `needPatternSync = true`，而消费它的 `update()` tick 由
        //   `updateSubscription()` 挂上 —— 那个方法只在 `onMainNodeStateChanged` 里被调用（节点状态**变化**），
        //   时机不对就永远挂不上 ⇒ 标记置了没人消费 ⇒ AE 一直拿着旧样板表（换样板后 AE 下单仍是旧那张）。
        //   这里在**每 tick 的同一个订阅**里合并消费一次（不新增订阅；`refreshPatternsNow` 自带防重入）：
        //   幂等、每 tick 最多一次，批量换样板也不会变成 O(n²)。
        if (needPatternSync) {
            needPatternSync = false;
            refreshPatternsNow();
        }
        // ★ 2026-10-02：读档容量修复后的**一次性**全量重扫 + 通知 AE（放在这个每 tick 的服务端订阅里，
        //   因为读档期机器还没挂到 level / 节点上，那会儿做等于白做 ✗）。
        //   afterArchiveBulkChange() = refreshAllByProduct() + 作废改写缓存 + 置 needPatternSync ⇒
        //   既让高槽样板重新进 slot2PatternMap（机器界面/AE 都能看到），也让 AE 丢掉过期的那张表 ✓
        if (archiveRescanAfterLoad) {
            archiveRescanAfterLoad = false;
            try {
                afterArchiveBulkChange();
                GTLModular.LOGGER.info("[gtlmodular] 读档后补一次样板表重扫 + AE 通知（容量修复收尾）pos={}", getPos());
            } catch (Throwable failure) {
                GTLModular.LOGGER.warn("[gtlmodular] 读档后样板表重扫失败（已忽略，下一 tick 由既有通道兜住）pos={} 原因={}",
                        getPos(), failure.toString());
            }
        }
        // ★ 2026-09-21（队长报「把舱室重新放下之后，没见它自动添上名字」）：
        //   起名**不能只挂在"模式切换"那两处**（自动切换 / 手动指定）—— 玩家把舱室重放、主机重新成形时
        //   模式往往没变 ⇒ 一次切换都没有 ⇒ 名字永远空着。这里在**每 tick 的同一订阅里**顺带补一次：
        //   · ★ 2026-10-15（本轮重做）：判据不再是任何"白名单"，而是**按「本总成 + 模式」查名字表**：
        //     该模式已有名字（我们设过 / 玩家改过）⇒ 用它；没有 ⇒ 用该模式的显示名写一次；
        //     玩家改过名（{@link #lockedName}）⇒ 永久让位 ✗。**名字与"控制器当前模式"解耦** ✗：
        //     主机在跑别的模式 ⇒ 本行不改名字 ✓；本总成换绑定 ⇒ 换成新绑定模式的名字 ✓。
        //     **这是主落点**：任何"结构成形后"的服务端 tick 都会走到这一行。
        //   · 一开始取不到控制器/类型表时静默返回，下一 tick 再试 ⇒ 结构一成形就会自动起名（自愈，不需要再切一次模式）；
        //   · 这一行在任何配置下都执行（自动切换开关关掉也照样起名 —— 起名是显示层的事，与切换策略无关）。
        syncCustomNameToMode(activeRecipeTypeIndex);
        // ★ 2026-09-26：让 activeRecipeTypeIndex **跟随控制器**（只读同步，自动切换关掉也照样执行）。
        //   玩家在界面里手动改模式、或结构重成形把模式重置时，我们的缓存不能继续停在旧值 ——
        //   否则 lockRecipeTypeForSlot / 起名 / 模式页都会用错模式（就是"车床被抢回卷板机"的另一半原因）。
        IRecipeLogicMachine controller = firstRecipeLogicController();
        if (controller != null) {
            int live = controller.getActiveRecipeType();
            if (this.activeRecipeTypeIndex != live) {
                // ★ 0.3.2 后半：**外部换模式**也是一条"模式变了"的路径（玩家在主机自己的模式页上改、
                //   结构重成形把模式重置、或同一控制器下别的样板总成锁了模式）⇒ 同样作废旧配方缓存，
                //   否则那批槽里"上一个模式被配上过的配方"会一直留着（快路径不校验谓词）。
                //   只清「旧值 / 新值这两个模式」的已占用槽（按需），绝不全清 640 次。
                clearRecipeCacheForMode(this.activeRecipeTypeIndex);
                clearRecipeCacheForMode(live);
                this.activeRecipeTypeIndex = live;
            }
            // ★ 0.3.2（同惰性机 0.3.2 的修法）：模式页游标在玩家**还没拨过**模式页时也一起跟随控制器
            //   （打开界面看到的默认值就等于「机器现在的模式」）；玩家拨过之后游标归玩家管，
            //   **绝不被这一行改回去** —— 这一行就是「选车床、指定下去却是切割机」的根因。
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
            // ★ 0.3.4：跑动中**不算空转** —— 把宽限期清零，等它做完、真的空转满 30 秒才允许切走。
            //   （比惰性机的写法更保守一档：那边的计时从"第一次看到没料"起算，长配方跑完时可能已经过 600 tick
            //     ⇒ 一停下来就立刻切走。这里让"空转 30 秒"字面成立，方向上只会等更久、不会更早切。）
            activeModeBusySince = -1L;
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
        // 诊断日志：只在「状态摘要发生变化」时打印一条（原来每 5 秒刷一条，日志噪声大）。
        // 摘要包含：当前模式 / 目标模式 / RecipeLogic 状态 / 样板与来料情况。
        // ★ 0.3.4 追加「空转 tick + 当前模式样板/有料/未认配方」三维（同惰性机；**只诊断、不参与判据**），
        //   让"为什么还没切"能一眼读出来：空转 < 600 tick 时本来就**不该**切。
        String stock = describeAutoSwitchState();
        long idleTicks = activeModeBusySince < 0L ? 0L : now - activeModeBusySince;
        String signature = active + "|" + target + "|" + logic.getStatus() + "|" + stock + "|" + (idleTicks / 100);
        if (!signature.equals(lastAutoSwitchSignature)) {
            lastAutoSwitchSignature = signature;
            GTLModular.LOGGER.info("[gtlmodular] 自动切模式检查: 当前={}/{} 状态={} 找到目标={} {} "
                            + "空转={}tick（满 {}tick 才允许切走） 当前模式样板={} 其中有料={} 未认配方={}",
                    active, types.size(), logic.getStatus(), target, stock, idleTicks,
                    AUTO_SWITCH_STOCKLESS_TICKS, diagActivePatterns, diagActiveStock, diagActiveCold);
        }
        if (target < 0 || target == active) {
            return;
        }
        controller.setActiveRecipeType(target);
        logic.resetRecipeLogic();
        // ★ 0.3.2 后半（同惰性机）：模式真的换了 ⇒ 把主机学到的**旧模式配方缓存**作废，否则
        //   「上一个模式被配上过的配方」还在缓存里，而快路径不校验谓词 ⇒ 切了模式却还在跑旧模式的配方。
        //   清两边的槽：旧模式（离开）与新模式（进入）各自"非空且类型属于该模式"的槽，按需清、绝不全清 640 次。
        clearRecipeCacheForMode(active);
        clearRecipeCacheForMode(target);
        this.activeRecipeTypeIndex = target;
        if (!this.modeCursorTouched) {
            this.modeCursorIndex = target;   // 玩家没拨过模式页 ⇒ 游标跟着机器走（0.3.2）
        }
        syncCustomNameToMode(target);   // ★ 名字跟随模式（队长 2026-09-21）
        this.pendingAutoSwitchType = target;
        this.pendingAutoSwitchDeadline = now + AUTO_SWITCH_GRACE_TICKS;
        GTLModular.LOGGER.info("[gtlmodular] 自动切换配方模式: {} -> {}", active, target);
        lastAutoSwitchSignature = null;   // 切换后允许下一轮重新打印一次摘要
    }

    /**
     * 找一个「类型已识别 + 槽里有料等着 + 不是当前模式 + 没被拉黑」的模式下标。
     *
     * <p>★ 0.3.4（<b>同惰性机 0.3.4 的判据</b>）：第一遍从「<b>当前模式还有样板就绝不切走</b>」改成
     * 「<b>当前模式还有活（有料在等）才绝不切走</b>」，料没了先<b>空转
     * {@value #AUTO_SWITCH_STOCKLESS_TICKS} tick（30 秒）</b>再放行：
     * <ol>
     *   <li>该模式的槽里<b>还有料等着</b> ⇒ 有活，绝不走（与旧判据在这一条上一致）；</li>
     *   <li>该模式的槽里没料了 ⇒ 先空转 30 秒再放行（兜住"这一模式的料还在 AE 推送路上"；
     *       料真到了会走「推送锁模式」那条路把机器切回来，不会漏跑）；</li>
     *   <li>控制器停在<b>一个样板都没有</b>的模式上时不受这两条限制（原有的自愈场景，原样保留）。</li>
     * </ol>
     * 「主机还没认过这条配方」（{@code hasRecipeCacheInSlot}）只进<b>诊断统计</b>、<b>不作为拦截条件</b> ——
     * 拿它拦截会过头：玩家把多种模式的样板一起插进来、其中只有一种有订单时，机器会被一张认不出配方的样板摁住。
     *
     * <p>为什么必须改：最正常的用法就是<b>多种模式的样板常驻仓里</b>，旧判据下自动切换<b>永远不触发</b>
     * （队长 2026-09-26 晚实测：「他现在不会自动切换机器模式了」），与配置项自己写的
     * 「一台机器可以同时挂多种模式的样板、轮流自动跑」直接矛盾。
     *
     * <p>第二遍（找候选）与旧版逐字一致：类型必须已识别、不是当前模式、没被拉黑、槽里真有料等着。
     */
    private int findModeWithWaitingStock(List<GTRecipeType> types, int active, long now) {
        ItemStackTransfer inventory = getPatternInventory();
        if (inventory == null) {
            return -1;
        }
        ensureSlotRecipeTypeArray();
        int slots = Math.min(inventory.getSlots(), slotRecipeType.length);
        // ★★ 0.3.4 第一遍：判「当前模式还有活吗」，而不是「当前模式还有样板吗」★★
        diagActivePatterns = 0;
        diagActiveStock = 0;
        diagActiveCold = 0;
        boolean hasWaitingStock = false;
        for (int slot = 0; slot < slots; slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                continue;
            }
            if (recipeTypeOfSlot(slot) != active) {
                continue;
            }
            diagActivePatterns++;
            if (!hasRecipeCacheInSlot(slot)) {
                // 主机还没把这条配方配上过 ⇒ 大概率"料还在 AE 路上"。**只统计、不拦截**（见方法注释）
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
            // 空槽先跳过：超级总成有 640 槽，以前会先做一次类型识别（含手动指定查表）再判空，纯浪费
            if (inventory.getStackInSlot(slot).isEmpty()) {
                continue;
            }
            int type = recipeTypeOfSlot(slot);   // 手动指定的优先，其次才是自动识别
            if (type < 0 || type >= types.size() || type == active) {
                continue;
            }
            Long blocked = autoSwitchBlockedUntil.get(type);
            if (blocked != null && now < blocked) {
                continue;
            }
            if (hasSlotWaitingStock(slot)) {
                return type;   // 找到了一个有料等着、且不属于当前模式的候选
            }
        }
        return -1;
    }

    // ================= 样板身份闸（不串配方的第二道）=================

    /**
     * 换掉 gtlcore 的扣料处理器，在它的入口前加一道「样板身份闸」。
     *
     * <p>这是<b>纯 Java</b> 的做法（继承 gtlcore 的公开处理器），不需要 mixin：
     * {@code createRecipeHandler} / {@code createMEItemHandler} / {@code createMEFluidHandler}
     * 都是 gtlcore 的 protected 工厂方法，覆写它们即可只对<b>我们这台机器</b>生效，
     * 别的总成（gtlcore 的、GTL Add 超级的、星律的）行为完全不变。
     *
     * <p>注意：{@code createRecipeHandler} 是在 gtlcore 构造器里被调用的（父类 ctor → 我们的覆写 → 建处理器），
     * 所以这里只能碰父类的状态，不能碰本类自己的字段。
     */
    @Override
    protected MEPatternBufferRecipeHandlerTrait createRecipeHandler(IO io) {
        return new IdentityGatedRecipeHandler((MEPatternBufferPartMachine) this, io);
    }

    /**
     * 带身份闸的扣料 trait（物品 + 流体两侧都换）。
     *
     * <p>两个处理器类必须写在<b>本类内部</b>再继承：{@code MEItemInputHandlerBase} /
     * {@code MEFluidHandlerBase} 是 gtlcore 里 {@code ...TraitBase} 的 <b>protected 嵌套类型</b>，
     * 只有在它的子类体内用简单名引用才合法 —— 而本类是它的子类，正好满足。
     */
    static final class IdentityGatedRecipeHandler extends MEPatternBufferRecipeHandlerTrait {

        IdentityGatedRecipeHandler(MEPatternBufferPartMachine machine, IO io) {
            super(machine, io);
        }

        @Override
        protected MEItemInputHandlerBase createMEItemHandler(IO io) {
            return new GatedItemHandler((MEPatternBufferPartMachine) getMachine(), io);
        }

        @Override
        protected MEFluidHandlerBase createMEFluidHandler(IO io) {
            return new GatedFluidHandler((MEPatternBufferPartMachine) getMachine(), io);
        }

        /** 物品侧：这条配方不属于该槽的样板 → 直接返回 false（该槽不参与这条配方），其余原样交给 gtlcore。 */
        static final class GatedItemHandler extends MEItemInputHandlerBase {

            private final MoltenPatternAssemblyPartMachine machine;

            GatedItemHandler(MEPatternBufferPartMachine machine, IO io) {
                super(machine, io);
                this.machine = (MoltenPatternAssemblyPartMachine) machine;
            }

            @Override
            public boolean meHandleRecipeInner(GTRecipe recipe, Object2LongMap<Ingredient> left,
                                               boolean simulate, int slot) {
                if (!machine.slotAllowsRecipe(slot, recipe)) {
                    machine.logGateReject(slot, recipe, "物品");
                    return false;
                }
                return super.meHandleRecipeInner(recipe, left, simulate, slot);
            }
        }

        /** 流体侧：同物品侧。 */
        static final class GatedFluidHandler extends MEFluidHandlerBase {

            private final MoltenPatternAssemblyPartMachine machine;

            GatedFluidHandler(MEPatternBufferPartMachine machine, IO io) {
                super(machine, io);
                this.machine = (MoltenPatternAssemblyPartMachine) machine;
            }

            @Override
            public boolean meHandleRecipeInner(GTRecipe recipe, Object2LongMap<FluidIngredient> left,
                                               boolean simulate, int slot) {
                if (!machine.slotAllowsRecipe(slot, recipe)) {
                    machine.logGateReject(slot, recipe, "流体");
                    return false;
                }
                return super.meHandleRecipeInner(recipe, left, simulate, slot);
            }
        }
    }

    /**
     * 这个槽的样板能不能执行这条配方。<b>两道闸</b>：
     * <ol>
     *   <li><b>类型闸</b>：该槽识别出的配方类型必须与候选配方的类型一致
     *       （只在识别出来时生效；识别不出来就跳过这层）——
     *       挡住"两种类型的输入输出长得一样"那种（例如 0.144B 液态铁+1 铁粉，分子解构/提取机都能匹配）；</li>
     *   <li><b>身份闸</b>：见 {@link #patternIdentityMatches}。</li>
     * </ol>
     *
     * <p><b>一律 fail-open</b>：开关关掉、样板为空、解不出来、出任何异常 → 放行（退回 gtlcore 原生行为）。
     * 宁可漏挡一次，也绝不让机器因为闸门而卡死。
     */
    /**
     * 身份闸拒绝时的诊断日志（2 秒最多一条）。
     *
     * <p>拒绝是"静默 return false"，机器会表现为<b>握着料却不动</b>（配方取不到输入）。
     * 所以一旦闸门挡了正确的配方，必须能看出来：这里把槽位/识别类型/配方类型都打出来。
     *
     * <p>★★ 2026-09-21（<b>只加一行"只读判读"，不改任何行为</b>）★★
     * 背景：队长「我更新了add，还有core现在我们的模块都不做东西了」＋「查」；本模组的
     * {@code NanochipProxyTableByTypeAlias} 给主机表在 {@code byType} 里挂了 141 条电路组装机
     * 配方之后，实机 {@code latest.log} 里出现
     * {@code 身份闸拒绝配方（物品侧）槽位[8]: 该槽识别类型下标=0 配方类型=gtlmodular:nanochip_cutting_chamber}。
     * 这条日志单看无法区分两种可能：
     * <ol>
     *   <li><b>正常拒绝</b>：这个槽的样板本来就属于别的模块，主机表不该跑它（闸门在干正事）；</li>
     *   <li><b>误识别后果</b>：这个槽的样板本该识别成"某个模块那台机器"，却被
     *       {@code detectRecipeType} 的过近似判据（输入只比身份、忽略数量；输出只看首个主产物）
     *       在主机表那一格里匹配到了某条电路组装机配方 ⇒ 误判成下标 0（主机表）。</li>
     * </ol>
     * 下面这一行只在 {@code patternAssembly.debugPatternLogs=true} 时打（<b>默认关</b>，不进热路径），
     * 且每个"（槽位 → 配方类型）"组合<b>只打一次</b>（台账在实例字段里，不刷屏），
     * 用来把上面两种可能当场分开 —— <b>它只读、只打印，不改判定、不改返回值</b>。
     */
    /**
     * 配方表 → **中文名**（与模式页同一套口径：{@code registryName.toLanguageKey()} 查语言文件；
     * 查不到就退回路径名）。
     *
     * <p>★ 2026-09-21 队长：「那个识别会看配方表里面，那个配方表到底叫什么名字」——
     * 于是所有"标识/拦截"相关的日志一律用中文表名打印，玩家一眼就能看出是"标识判错了"还是"闸门在干正事"。
     */
    private static String tableNameOf(GTRecipeType type) {
        if (type == null || type.registryName == null) {
            return "?";
        }
        try {
            String key = type.registryName.toLanguageKey();
            String translated = net.minecraft.network.chat.Component.translatable(key).getString();
            return (!translated.isEmpty() && !translated.equals(key)) ? translated : type.registryName.getPath();
        } catch (RuntimeException ignored) {
            return type.registryName.getPath();
        }
    }

    void logGateReject(int slot, GTRecipe recipe, String side) {
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
    }

    /**
     * ★ 2026-09-21 <b>只读判读</b>：识别到"主机表"但配方类型是别的表时，记一条（默认关，每个组合只记一次）。
     *
     * <p>为什么需要它：主机表在 {@code byType} 里有了 141 条电路组装机配方之后，
     * {@code detectRecipeType} 的过近似判据**有能力**把非主机表的样板判成下标 0；而实机日志里
     * 「识别类型下标=0 + 配方类型=另一个模块的类型」这个组合<b>确实出现过</b>。
     * 光看现有日志分不清那是"该槽本就不属于这条配方"（闸门干正事）还是"样板被误判成主机表"（潜在缺陷）。
     *
     * <p><b>本方法的纪律（不许改坏）</b>：
     * <ul>
     *   <li><b>零行为影响</b>：不 return、不改 {@code slotAllowsRecipe} 的结论、不动闸门开关、
     *       不动 {@code slotRecipeType} 缓存 —— 它只在"已经决定拒绝之后"被调用，只写日志；</li>
     *   <li><b>★ 2026-09-21 起默认就开</b>（原来只在 {@code patternAssembly.debugPatternLogs=true} 时打）：
     *       队长这次卡住时正因为那个开关默认为关，日志里**什么都看不到**，只能靠猜 ——
     *       判读本身只读、且**每个（槽位 → 配方类型）组合只打一次**（{@link #hostTableMisidentifyNotes} 台账），
     *       调用点还被 {@code logGateReject} 的 40 tick 节流挡着 ⇒ 代价可忽略；</li>
     *   <li><b>台账有界</b>：上限 {@value #MISIDENTIFY_NOTE_LIMIT} 条，满了就不再新增
     *       （超级总成有 2048 槽，不能让它无限长）。</li>
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
            ItemStackTransfer inventory = getPatternInventory();
            if (inventory == null || slot >= inventory.getSlots()) {
                return true;
            }
            ItemStack stack = inventory.getStackInSlot(slot);
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
     * 样板身份匹配（语义对齐 gtlcore 的样板裁剪口径；本仓库自写实现）。
     * <ol>
     *   <li><b>输出</b>：配方首个物品（或首个流体）主产物要能在<b>样板输出</b>里找到；</li>
     *   <li><b>输入</b>：配方每一路硬性输入（{@code chance > 0}）都要被<b>样板输入</b>覆盖，
     *       其中<b>编程电路那一路跳过</b>（gtlcore 的样板身份已把电路剥掉，不跳过会大面积误判）。</li>
     * </ol>
     * 指纹用 {@link Item} / {@link Fluid} 作 key（忽略数量与 NBT）。
     *
     * <p>★ 2026-09-22：从 package-private 提升为 {@code public static}（当时是为了让本模组自制的
     * 通配符样板总成复用同一套配方身份判据）。<b>当天晚些时候那台机器已整体退役</b>
     * （改用第三方 mod {@code wildcard_pattern} + gtlcore 官方兼容机器，见
     * {@code GTLModularMachines} 里那段退役注释）⇒ 现在本方法只被本类使用；
     * 保留 {@code public} 不动，避免无谓改动。
     */
    public static boolean patternIdentityMatches(GTRecipe recipe, IPatternDetails pattern) {
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

    // ================= 视图倍率（倍率 / 神锻）=================

    /**
     * 改写缓存是否<b>已构建</b>。
     *
     * <p>以前用 {@code rewrittenPatterns == null} 兼作"未构建"哨兵，但"构建后发现所有样板都没有实质变化"
     * 也会把它写回 null —— 于是开着倍率/神锻而样板本身没变化时，<b>每次取样板都会把全部样板重新缩放+重编码</b>
     * （超级总成 2048 槽时是每槽一次 NBT encode+decode）。拆成独立标志位即可区分这两种情况。
     */
    private boolean rewriteCacheBuilt;

    /** 「未改写」取样板日志的节流签名（只在状态变化时打印一次，避免刷屏）。 */
    private String lastQuerySignature;

    /** 防重入：通知网络刷新样板时，gtlcore 可能回调 onPatternChange → invalidateRewriteCache。 */
    private boolean refreshingPatterns;

    /** 「AE 推送样板」诊断日志的最后记录 tick（1 秒最多一条）。 */
    private long lastPushLogTick = -1000;

    /**
     * ★★ 0.3.132【只加读数 · 累计收到量】★★ 本机<b>自载入以来</b>从 AE 累计收到的
     * 「料量」（把所有 {@code pushPattern} 的 {@code inputHolder} 逐项相加；物品按个数、流体按 mB，同池相加）。
     *
     * <p><b>为什么要这一格</b>：现在能报「这一次推了多少」，但报不出「一共推了多少」——
     * 而没有它就<b>无法与"机器一共吃掉多少"对比</b>，也就判不出那条死锁链到底断在哪：
     * 是 <b>AE 根本没推</b>（累计量≈0 而进程在推进），还是 <b>推了但机器没消费</b>（累计量很大而内部槽仍空）。
     *
     * <p><b>纯诊断</b>：只在本类字段上加，<b>不参与任何判定</b>、不进存档、不进同步包。
     */
    private long pushedToMachineTotal;

    /** 见 {@link #pushedToMachineTotal}：流体侧的累计量（mB）。 */
    private long pushedToMachineFluidTotal;

    /**
     * ★ 0.3.132【只加读数】读本机累计从 AE 收到的量（物品池 / 流体池）。
     * <p><b>纯只读</b>：给「独立线程」那行诊断用，用来与「机器累计吃掉多少」对比。
     * 两个数一起给 ⇒ 调用方一眼看出那条链断在哪一侧。不进存档、不进同步包。
     */
    public long[] gtlmodular$pushedTotals() {
        return new long[] { pushedToMachineTotal, pushedToMachineFluidTotal };
    }

    /**
     * ★ 0.3.132【撤自造 · 机器侧二次放大】是否已经就"倍率/神锻这次没生效"提醒过一次。
     *
     * <p>撤掉机器侧放大之后，{@code scalingApplies == true} 的含义变了：它不再表示"我们要放大"，
     * 而表示<b>"AE 推的是原样板 ⇒ 本次按 AE 声明的小量做，倍率/神锻在机器侧没有生效"</b>。
     * 这是一次<b>功能静默失效</b>，不能无声无息 ⇒ 每个机器实例只 WARN 一次（有界，不刷屏、不进存档）。
     * 要根治须走方案 §⑤-A2（让 AE 手上只有改写样板），需队长裁决。
     */
    private boolean machineScaleDisabledWarned;

    /** 「身份闸拒绝」诊断日志的最后记录 tick（2 秒最多一条）。 */
    private long lastGateRejectLogTick = -1000;

    /**
     * ★ 2026-09-21 <b>只读判读台账</b>：已经记过的「槽位 → 该槽被误判成主机表时正在跑的那个配方类型」组合。
     *
     * <p>用途见 {@link #noteIfHostTableMisidentified}。只在
     * {@code patternAssembly.debugPatternLogs=true}（默认关）时才会被写入 ⇒ 默认完全不占内存/不花代价。
     */
    private final java.util.Set<String> hostTableMisidentifyNotes = new java.util.HashSet<>();

    /** 上面那本台账的上限（超级总成 2048 槽，不能让台账无限长）。 */
    private static final int MISIDENTIFY_NOTE_LIMIT = 64;

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        // ★★ 0.3.132【倍增交给 AE】开关打开时：直接返回仓里的原样板，绝不把改写样板报给 AE。★★
        //   原版 gtlcore 的 getAvailablePatterns() 就是直接返回槽表（MEPatternBufferPartMachine:776-778），
        //   倍增由 AE 的自动翻倍承担（CraftingCpuLogicMixin @Overwrite executeCrafting + CraftingPatternAutoExpand）。
        //   为什么必须这样：我们改写样板会让"样板声明量"自身乘 N，与 AE 的 operations 放大重复且在相乘
        //   ⇒ 需求被推到天上去 ⇒ AE 提取全有或全无 ⇒ 本 tick 零推送 ⇒「被占用、没有任务」。
        //   一行回退：把 PATTERN_REWRITE_DISABLED 改成 false。
        if (PATTERN_REWRITE_DISABLED) {
            return super.getAvailablePatterns();
        }
        if (rewriteCacheBuilt && rewrittenPatterns != null) {
            return rewrittenPatterns;   // 缓存有效且有改写结果：不必再让父类造一遍 Stream + List
        }
        List<IPatternDetails> base = super.getAvailablePatterns();
        if (base.isEmpty() || !isRewriteEnabled()) {
            invalidateRewriteCache();
            // 诊断（临时）：只在“仓里/样板表里真有样板”时打印一次；完全空的机器静默，
            // 否则会被 AE 网络每秒轮询刷屏（实测那台空机器刷了 1300+ 行）。
            String sig = base.size() + "|" + slotCount() + "|" + isRewriteEnabled() + "|" + patternScale + "|"
                    + patternDivScale + "|" + foaMode + "|" + isOutputMode;
            if (!sig.equals(lastQuerySignature)) {
                lastQuerySignature = sig;
                int patterns = 0;
                ItemStackTransfer inv = getPatternInventory();
                if (inv != null) {
                    for (int i = 0; i < inv.getSlots(); i++) {
                        if (!inv.getStackInSlot(i).isEmpty()) {
                            patterns++;
                        }
                    }
                }
                if (base.size() > 0 || patterns > 0) {
                    GTLModular.LOGGER.info("[gtlmodular] 取样板(未改写): @{} {} 原始={} 库存槽={} 仓内样板={} 开关={} 倍率={} 除率={} 神锻={}({}) 输出模式={}",
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

    private boolean isRewriteEnabled() {
        return foaMode || patternScale > 1 || patternDivScale > 1;
    }

    /** 有效倍率（神锻 × 倍率，两侧同倍）；与 {@link #rewritePattern} 同一套公式。 */
    private int effectiveMul() {
        int bothMul = foaMode ? Math.max(MIN_FOA_MULTIPLIER, Math.min(MAX_FOA_MULTIPLIER, foaMultiplier)) : 1;
        int sideMul = Math.max(MIN_SCALE, Math.min(MAX_SCALE, patternScale));
        return bothMul * sideMul;
    }

    /** 有效除率。 */
    private int effectiveDiv() {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, patternDivScale));
    }

    /**
     * 当前设置下的<b>有效</b>缩放（仅供日志判读）。
     *
     * <p>倍率/除率与神锻一样<b>两侧同倍</b>（旧版"输出模式"只放大产物侧，已废弃）：
     * 例如神锻 15 + 倍率 2 → 两侧 ×30；除率 3 → 两侧 ÷3。总量不变、合成次数按倍率下降。
     */
    private String describeEffectiveScale() {
        return "两侧×" + effectiveMul() + "÷" + effectiveDiv();
    }

    /**
     * 设置变了就让缓存失效（下一条要样板时重建）。
     *
     * <p><b>关键：还要通知网络重算/重取样板表。</b>改写是"懒计算"——只在
     * {@link #getAvailablePatterns()} 里做。实测发现：改了倍率/神锻之后，AE **不会**主动再来取一次样板表
     * （日志里那台机器整局只在打开时被取过一次），于是缩放永远不生效。gtlcore 提供了
     * {@code reCalculatePatternSlotMap()} 来做"重算样板表 + 通知"，这里调用它。
     */
    public void invalidateRewriteCache() {
        rewrittenPatterns = null;
        rewrittenToOriginal.clear();
        rewriteCacheBuilt = false;
        notifyPatternsChanged();
    }

    // ================= 片 D：gtlcore 复制/剪切工具的批量写入路径 =================
    //
    // 现场取证（`_census/_gto_wildcard/javap_gtlcore/GTL_Buffer_full.txt` 逐指令）：
    //   · `pasteFromTag(tag)` :1100–1315 —— 逐槽 `internalPatternInventory.setItemDirect`(:1187) 之后
    //     **在 :1311 调了 `refreshAllByProduct()`** ⇒ 基类自己会置 `needPatternSync`（偏移 60–66）
    //     ⇒ **这条路本来就会通知 AE**。这里只补一句**幂等**的置位（万一将来的 gtlcore 版本改掉那个调用，
    //     我们不会静默失灵；置位是幂等的，多置一次代价为零）。
    //   · `copyFromTag(tag, player)` :935–1027 —— 逐槽 `setItemDirect`(:1003) 后**直接 return**（:1027）
    //     ⇒ **不置标记、也不刷样板表**。
    //   · `cutToTag(tag)` :1317–1569 —— 逐槽 `setItemDirect(EMPTY)`(:1407) 清空全仓后
    //     **直接 markDirty + return**（:1567）⇒ **不置标记**。
    //
    // ⇒ 两条"批量改一大批样板"的路（剪切清空 / 归档覆盖写）在现役机上**从来不捅 AE**
    //   ⇒ 合成服务的可用样板表一直是旧的。这里各补**一句**置位，**只在批量路径的收尾置一次**
    //   （绝不逐槽置位、绝不在这里同步重读全部样板 ⇒ 不违反"防 O(n²)"纪律）。
    //   消费仍然是每 tick 那一个既有消费点（`:1265`），**没有新增订阅**。

    /**
     * 片 D：批量粘贴（gtlcore 自带复制/剪切工具的"粘贴"侧）。
     *
     * <p>语义**一个字节都不动**（容量不够仍然整体返回 false、`patterns` 顺序仍然按第一个空槽落位、
     * 催化剂/副产/代理绑定仍然照原样恢复）；只在真发生了写入（返回 true）时补**一次**样板表过期置位。
     */
    @Override
    public boolean pasteFromTag(net.minecraft.nbt.CompoundTag tag) {
        boolean ok = super.pasteFromTag(tag);
        if (ok) {
            // 批量路径：整批只置**一次**（置位本身廉价且幂等；消费在每 tick 的既有通道里）
            markPatternTableStale("批量粘贴pasteFromTag");
        }
        return ok;
    }

    /**
     * 片 D：批量剪切（gtlcore 自带剪切工具把全仓搬进 tag 并**清空本仓**）。
     *
     * <p>语义不变：仍然写进传入的 tag 并把它返回（gtlcore 的 `copyToTag` 同款契约），
     * 仍然连催化剂/共享催化剂/共享电路/代理一起清干净；只在收尾补**一次**置位
     * （全仓样板真的没了，AE 必须马上重读，否则"拆下来的机器还能下单"）。
     */
    @Override
    public CompoundTag cutToTag(CompoundTag tag) {
        CompoundTag result = super.cutToTag(tag);
        // 批量路径：整批只置**一次**
        markPatternTableStale("批量剪切cutToTag");
        return result;
    }

    /**
     * 片 D：批量归档覆盖写（gtlcore 自带复制工具把 tag 里的样板倒进本仓的空槽）。
     *
     * <p>语义不变（按 patterns 顺序逐张尝试塞进第一个空槽、按张扣 {@code ae2:blank_pattern}、
     * 塞不下就停在那张且**不**回滚前面已写的）；基类实现**不置标记**，这里补**一次**置位。
     *
     * <p>注意：这个方法返回 {@code void}（字节码 :1027 直接 return）⇒ 无法判读"有没有真写进去"，
     * 所以**无条件**置位。代价为零（置位幂等；消费点在每 tick 的既有通道里，最多多通知一次）。
     */
    @Override
    public void copyFromTag(net.minecraft.nbt.CompoundTag tag, net.minecraft.server.level.ServerPlayer player) {
        super.copyFromTag(tag, player);
        // 批量路径：整批只置**一次**
        markPatternTableStale("批量覆盖copyFromTag");
    }

    /**
     * 设置变了就让缓存失效，并通知 AE 重新取样板表（下一条要样板时重建）。
     *
     * <p><b>关键：{@link #refreshPatternsNow()} 里的 {@code ICraftingProvider.requestUpdate(mainNode)}。</b>
     * 实测：只调 {@code reCalculatePatternSlotMap()}、或只重写样板槽、或只在 AE 里下单，
     * AE 都不会重新取样板表（缩放永远不生效）；只有 requestUpdate 才会让合成服务当秒重取。
     */
    private void notifyPatternsChanged() {
        refreshPatternsNow();
    }

    /** 真正执行一次「重算样板表 + 通知 AE 重新取样板」（防重入；远端/异常一律放过）。 */
    private void refreshPatternsNow() {
        if (isRemote() || refreshingPatterns) {
            return;
        }
        refreshingPatterns = true;
        try {
            reCalculatePatternSlotMap();
            // AE2 官方入口一：直接让合成服务"重读这个节点的样板"。
            // 实测：只调 reCalculatePatternSlotMap()、或只重写样板槽、或只在 AE 里下单，
            // 合成服务都不会重读（AE 仍拿原样板去下单：日志里 是否缩放样板=false、输入总量不变）。
            appeng.api.networking.IManagedGridNode mainNode = getMainNode();
            appeng.api.networking.IGrid grid = mainNode.getGrid();
            if (grid != null) {
                appeng.api.networking.crafting.ICraftingService crafting = grid.getCraftingService();
                appeng.api.networking.IGridNode node = mainNode.getNode();
                if (crafting != null && node != null) {
                    crafting.refreshNodeCraftingProvider(node);
                }
            }
            // AE2 官方入口二：通用的"样板表变了"通知（gtlcore 基类自己用的也是它）。
            appeng.api.networking.crafting.ICraftingProvider.requestUpdate(mainNode);
        } catch (RuntimeException ignored) {
            // 拿不到就算了：缓存已失效，下次取样板仍会重建
        } finally {
            refreshingPatterns = false;
        }
    }

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
        // 诊断（临时）：改倍率/神锻后若这行不出现 = 缓存没失效（我的问题）；出现但"已改写=0" = 改写逻辑本身没产出。
        GTLModular.LOGGER.info("[gtlmodular] 缩放缓存重建 @{}: 样板={} 已改写={} 有效缩放[{}] 倍率={} 除率={} 神锻={}({})",
                getPos(), base.size(), changedCount, describeEffectiveScale(), patternScale, patternDivScale, foaMode, foaMultiplier);
    }

    /**
     * 单个样板的缩放。
     *
     * <p><b>两侧同倍</b>（与神锻一致）：输入和输出用<b>同一个</b>倍率/除率。
     * 原因：AE 只会把"样板声明里要的输入量"推给机器，机器就按推来的量做 ——
     * 如果只放大某一侧（旧的"输出模式"），AE 以为能拿到 N 倍产出、机器实际只跑 1 倍，
     * 声明与实际执行就对不上了。两侧同倍则永远自洽（N 倍输出必然配 N 倍输入）。
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

    /** 把一组 stack 的量 ×mul÷div（向下取整但至少留 1，避免出现 0 数量的 stack）。 */
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

    // ---------- 倍率 / 神锻 的字段读写 ----------

    public int getPatternScale() {
        return patternScale;
    }

    public void setPatternScale(int scale) {
        int next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        if (next != this.patternScale) {
            GTLModular.LOGGER.info("[gtlmodular] 缩放设置: 倍率 {} -> {}（{}）",
                    this.patternScale, next, isRemote() ? "客户端" : "服务端");
        }
        this.patternScale = next;
        invalidateRewriteCache();
    }

    public int getPatternDivScale() {
        return patternDivScale;
    }

    public void setPatternDivScale(int scale) {
        int next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        if (next != this.patternDivScale) {
            GTLModular.LOGGER.info("[gtlmodular] 缩放设置: 除率 {} -> {}（{}）",
                    this.patternDivScale, next, isRemote() ? "客户端" : "服务端");
        }
        this.patternDivScale = next;
        invalidateRewriteCache();
    }

    public boolean isOutputMode() {
        return isOutputMode;
    }

    /**
     * @deprecated 旧版"输出模式（只放大产物侧）"。两侧同倍之后不再参与缩放计算，
     * 保留字段/读写仅为兼容旧存档与同步布局；界面上已移除该按钮。
     */
    @Deprecated
    public void toggleOutputMode() {
        this.isOutputMode = !isOutputMode;
        invalidateRewriteCache();
        GTLModular.LOGGER.info("[gtlmodular] 缩放设置: 输出模式={}（{}，已废弃：不再影响缩放）",
                isOutputMode, isRemote() ? "客户端" : "服务端");
    }

    public boolean isFoaModeEnabled() {
        return foaMode;
    }

    public void setFoaModeEnabled(boolean enabled) {
        if (enabled != this.foaMode) {
            GTLModular.LOGGER.info("[gtlmodular] 缩放设置: 神锻模式={}（{}）", enabled, isRemote() ? "客户端" : "服务端");
        }
        this.foaMode = enabled;
        invalidateRewriteCache();
    }

    public int getFoaPatternOutputMultiplier() {
        return foaMultiplier;
    }

    public void setFoaPatternOutputMultiplier(int multiplier) {
        this.foaMultiplier = Math.max(MIN_FOA_MULTIPLIER, Math.min(MAX_FOA_MULTIPLIER, multiplier));
        invalidateRewriteCache();
    }

    // ---------- 催化剂页字段 ----------

    public int getCatalystViewSlot() {
        return catalystViewSlot;
    }

    public void setCatalystViewSlot(int slot) {
        this.catalystViewSlot = Math.max(1, Math.min(getCatalystSlotCount(), slot));
    }

    public boolean isCatalystPageOpen() {
        return catalystPageOpen;
    }

    public void setCatalystPageOpen(boolean open) {
        this.catalystPageOpen = open;
    }

    /** 样板槽位总数。 */
    public int getCatalystSlotCount() {
        return Math.max(1, slotCount());
    }

    // ================= 片 C：样板仓归档（复制 / 剪切 / 粘贴）=================
    //
    // ── 这一段是什么 ──
    // 把惰性机那条**已被队长验收**的「样板仓归档」接到现役机上：同一套引擎
    // （{@link LazyPatternArchiveCodec#apply(PatternArchiveTarget, LazyPatternArchiveCodec.Payload,
    // boolean, boolean, LazyPatternArchiveCodec.PlaceMode)}）、同一套承诺
    // （**先算后写** / **装不下整体拒绝** / **绝不覆盖已有样板**），
    // 通过 {@link MoltenPatternArchiveTarget} 这个适配器转调本类的方法。
    //
    // ── 现役机与惰性机的三条硬差异（逐条都在下面有落点）──
    //   ① **容量"起步 + 按需增长"**（★ 0.3.48 惰性 A 档第一步起；此前是构造期一次建满）：
    //      硬上限仍是 `slotsForTier(tier)` 注册期定死（1~5 页 × 128 = 128/256/384/512/640），
    //      但样板仓那张 `ItemStackTransfer` 现在**构造期收到** {@link #LAZY_INITIAL_CAPACITY}、
    //      写前按需增长（{@link #growCapacityFor(int)} / {@link #growCapacityForBatchInsert(int)}），
    //      读档后按"已用最高槽 + {@value #LAZY_RESERVE}"回填（{@link #backfillCapacityAfterLoad()}）。
    //      六个并行数组（internalInventory / catalystItems / catalystFluids / cacheRecipeCount /
    //      hasPatternArray / cacheRecipe）**仍按 `maxPatternCount` 建** ⇒ 这只是"部分惰性"，
    //      要连槽对象一起惰性必须换父类，而换父类会失去 Enhancedcore 的 IV 样板仓兼容（见评估报告 §四-1）✗。
    //      落点：`growForBatchInsert` / `growToCapacity` 在适配器里**仍然是 no-op** ——
    //      归档的"装得下装不下"继续按硬上限 `maxPatternCount` 判（{@link #archiveCanFit(int)}），
    //      但落地写入时会按需扩容（{@link #archiveWritePattern(int, ItemStack)}）。
    //      客户端容量同步**仍未引入**（方案 §2.3 判"照搬有害"，留到 B3 阶段）。
    //   ② **没有 `getCapacity()`/`onPatternInventoryChanged()`**：本机的容量就是
    //      `maxPatternCount`，仓就是 {@code getPatternInventory()}（public，槽数 == maxPatternCount）；
    //      收尾走本机既有通道 {@link #afterArchiveBulkChange()}。
    //   ③ **坐标系固定 128/640**：档案里的槽下标就是本机真实槽下标（与惰性机的动态容量不同，
    //      所以两边的档案**不能**喂给 gtlcore 的 pasteFromTag/copyToTag —— 反之亦然，见方案 §4.4）。
    //
    // ── 与 gtlcore 自带四方法的关系（**不碰**它们，方案 §4.4）──
    //   本机**已经有** `copyToTag/copyFromTag/pasteFromTag/cutToTag`（片 D 还给后三个补了
    //   `markPatternTableStale`），那套的 NBT 格式写在 `tag`/`cut` 键里、**不带逐槽元数据**
    //   （只有 pattern + cacheCount + 催化剂，且**完全不搬手动指定 / 配方类型缓存**）。
    //   我们这套写在 `gtlmodular$archive` 里、把逐槽元数据一并搬走；两套**并存、格式不互通**，
    //   读侧**只读兼容** gtlcore 的两个键（见 LazyPatternArchiveCodec 的 LEGACY_*）。
    //   ⇒ 本类这四个 gtlcore 方法**一个字都不动**。

    /**
     * ★ 现役机归档：<b>本机容量 = 硬上限</b>（构造期定死）。
     * <p>惰性机的 `getCapacity()` 是"会长的当前容量"，现役机没有这一档 ——
     * 它俩在这里是同一个值，写成两个方法只是为了让接口两侧语义对齐。
     */
    public int archiveCapacity() {
        ItemStackTransfer inventory = getPatternInventory();
        // 构造期（super() 内部）字段还没赋值 ⇒ 容错返回 0（与惰性机 getCapacity() 同口径）
        return inventory == null ? 0 : inventory.getSlots();
    }

    /** ★ 现役机归档：硬上限（gtlcore 具体类的 {@code protected final int maxPatternCount}，子类可直接读）。 */
    public int archiveMaxCapacity() {
        return maxPatternCount;
    }

    /** ★ 现役机归档：没有"预留空位"概念 ⇒ 恒 0（`needed` 语义 = 最高目标槽 + 1）。 */
    public int archiveReserve() {
        return 0;
    }

    /**
     * ★★ <b>容量预检</b>：本次落地需要 {@code needed} 个槽位（= 计划里最高目标槽 + 1 + 预留）时，
     * 本机装不装得下？<b>只读、零副作用</b>。
     *
     * <p>引擎（{@code LazyPatternArchiveCodec.apply} 第 ② 步）在**写第一个槽之前**调它：
     * 返回 false ⇒ 立刻以 {@code gtlmodular.pattern_assembly.archive.result.reject} 整体拒绝，
     * 此刻既没扩容（也不可能扩）、也没碰任何槽 —— 这就是现役机上"扩容"退化成
     * "**预检 + 拒绝**"的那一处落点。
     *
     * <p>判据就是"不许超过注册期定死的硬上限"。现役机 {@code archiveCapacity() == archiveMaxCapacity()}
     * ⇒ 这个分支会比惰性机更早、更频繁地命中（128 槽的 MV 档装 200 张档案 ⇒ 直接整体拒绝），
     * 这是**正确行为**（设计稿第四条：容量装不下整体拒绝）。
     */
    public boolean archiveCanFit(int needed) {
        return needed <= maxPatternCount;
    }

    /** ★ 现役机归档：本机当前已用最高槽（0 起；空仓 -1）。追加模式的起点 = 它 + 1。 */
    public int archiveHighestUsedSlot() {
        ItemStackTransfer inventory = getPatternInventory();
        if (inventory == null) {
            return -1;
        }
        for (int i = inventory.getSlots() - 1; i >= 0; i--) {
            try {
                ItemStack stack = inventory.getStackInSlot(i);
                if (stack != null && !stack.isEmpty()) {
                    return i;
                }
            } catch (Throwable ignored) {
                // 单槽读不出来 ⇒ 跳过它（绝不因此丢掉别的槽）
            }
        }
        return -1;
    }

    /**
     * ★ 现役机归档：只读一个槽的原始内容。
     *
     * <p>★ 读异常<b>照原样外抛</b>（这里不吞）：引擎会把"读不出来"当成"这一槽有东西"
     * ⇒ 少写一条、绝不覆盖玩家已有的样板。
     */
    public ItemStack archivePatternAt(int slot) {
        ItemStackTransfer inventory = getPatternInventory();
        return inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(slot);
    }

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

    /** 粘贴落位是否用"按原槽对齐"（{@code false} = 追加，默认）。★ 与惰性机同名同语义。 */
    public boolean isArchiveAlignSlots() {
        return archiveAlignSlots;
    }

    public void setArchiveAlignSlots(boolean value) {
        this.archiveAlignSlots = value;
    }

    /** 粘贴落位模式（物品右键 / 面板 / 引擎共用同一个判据）：只读、不动任何数据。 */
    public LazyPatternArchiveCodec.PlaceMode archivePlaceMode() {
        return archiveAlignSlots
                ? LazyPatternArchiveCodec.PlaceMode.ALIGN
                : LazyPatternArchiveCodec.PlaceMode.APPEND;
    }

    /**
     * ★ <b>归档的只读摘要</b>：{@code [0] = 样板张数, [1] = 最低槽（1 起，0 = 无）, [2] = 最高槽（1 起，0 = 无）}。
     * 给「归档」页的实时文本用（面板每帧都会问 ⇒ 只扫一遍仓、不做任何解码）。
     */
    public int[] archiveSummary() {
        int count = 0;
        int min = -1;
        int max = -1;
        ItemStackTransfer inventory = getPatternInventory();
        int capacity = archiveCapacity();
        for (int i = 0; i < capacity; i++) {
            ItemStack stack;
            try {
                stack = inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(i);
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

    // ---------- 档案格式里的键名（★ 与惰性机的写侧**逐字一致**，档案才能两边互通）----------
    //
    // 催化剂三键与惰性机的 NBT_CATALYST_ITEMS/FLUIDS/COUNT 完全相同（"items"/"fluids"/"count"）。
    // 设置类五键用的是**惰性机的落盘键名**（`gtlmodular$lazy_*`）—— 因为档案里那个
    // `gtlmodular$archive_settings` 子标签的格式定义**已经在惰性机那边发布了**
    // （LazyPatternAssemblyPartMachine.java:621-625），惰性机的读侧只认这五个键名；
    // 现役机若改用自己的键名（`gtlmodular$pattern_scale`…），
    // "现役机 → 惰性机"这条迁移路就会**静默丢掉全部设置**。
    // ★ 这是"档案内部的格式键"，不是任何机器的落盘键名（本机自己的 5 个落盘键是
    //   `gtlmodular$pattern_scale`…，见 save/loadCustomPersistedData，两者互不影响）。
    private static final String ARCHIVE_KEY_PATTERN_SCALE = "gtlmodular$lazy_pattern_scale";
    private static final String ARCHIVE_KEY_PATTERN_DIV_SCALE = "gtlmodular$lazy_pattern_div_scale";
    private static final String ARCHIVE_KEY_FOA_MODE = "gtlmodular$lazy_foa_mode";
    private static final String ARCHIVE_KEY_FOA_MULTIPLIER = "gtlmodular$lazy_foa_multiplier";
    private static final String ARCHIVE_KEY_OUTPUT_MODE = "gtlmodular$lazy_output_mode";
    /** 本机自己的落盘键名（只作为**读侧兜底**：手改过的/将来版本写出来的档案也不至于全丢）。 */
    private static final String MOLTEN_KEY_PATTERN_SCALE = "gtlmodular$pattern_scale";
    private static final String MOLTEN_KEY_PATTERN_DIV_SCALE = "gtlmodular$pattern_div_scale";
    private static final String MOLTEN_KEY_FOA_MODE = "gtlmodular$foa_mode";
    private static final String MOLTEN_KEY_FOA_MULTIPLIER = "gtlmodular$foa_multiplier";
    private static final String MOLTEN_KEY_OUTPUT_MODE = "gtlmodular$output_mode";
    /** 逐槽催化剂的三个子键（与 LazyPatternAssemblyPartMachine 的 NBT_CATALYST_* 逐字一致）。 */
    private static final String ARCHIVE_KEY_CATALYST_ITEMS = "items";
    private static final String ARCHIVE_KEY_CATALYST_FLUIDS = "fluids";
    private static final String ARCHIVE_KEY_CATALYST_COUNT = "count";
    /** 阈值默认值（gtlcore 具体类构造期 `Arrays.fill(cacheRecipeCount, 1)` ⇒ 默认就是 1）。 */
    private static final int DEFAULT_CACHE_RECIPE_COUNT = 1;

    /**
     * ★ <b>取出全仓</b>（复制 / 剪切语义的读侧；<b>不动本机一个字节</b>）。
     *
     * <p>逐条搬走：样板本体 + 逐槽配方类型缓存（{@link #slotRecipeType}，
     * {@code -1} = 没试过不写，与惰性机同口径）+ 手动指定（{@link #manualSlotTypes} 的 id）
     * + 逐槽催化剂（items / fluids / 阈值）+（可选）设置类 +（可选）名字。
     *
     * <p><b>搬不走 / 有意不搬</b>（对应物在惰性机上根本不存在，列表写清免得被当成漏做）：
     * gtlcore 的 {@code keepByProduct}、共享催化剂仓 {@code sharedCatalystInventory} /
     * {@code sharedCatalystTank}、共享电路 {@code sharedCircuitInventory}、镜像绑定
     * {@code proxies}、以及"按顺序填空槽"那套流水账语义。
     */
    public LazyPatternArchiveCodec.Payload buildArchivePayload(boolean includeSettings, boolean includeName) {
        LazyPatternArchiveCodec.Payload payload = new LazyPatternArchiveCodec.Payload();
        payload.capacity = archiveCapacity();
        payload.includeSettings = includeSettings;
        payload.includeName = includeName;
        payload.name = getCustomName() == null ? "" : getCustomName();
        payload.dimension = archiveDimensionId();
        payload.pos = getPos() == null ? 0L : getPos().asLong();
        if (includeSettings) {
            CompoundTag settings = new CompoundTag();
            settings.putInt(ARCHIVE_KEY_PATTERN_SCALE, patternScale);
            settings.putInt(ARCHIVE_KEY_PATTERN_DIV_SCALE, patternDivScale);
            settings.putBoolean(ARCHIVE_KEY_FOA_MODE, foaMode);
            settings.putInt(ARCHIVE_KEY_FOA_MULTIPLIER, foaMultiplier);
            settings.putBoolean(ARCHIVE_KEY_OUTPUT_MODE, isOutputMode);
            payload.settings = settings;
        }
        ensureSlotRecipeTypeArray();
        ItemStackTransfer inventory = getPatternInventory();
        int capacity = archiveCapacity();
        int highest = -1;
        for (int i = 0; i < capacity; i++) {
            ItemStack stack;
            try {
                stack = inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(i);
            } catch (Throwable ignored) {
                continue;   // 这一槽读不出来 ⇒ 跳过它（绝不因此丢掉别的槽）
            }
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            Integer recipeType = null;
            if (slotRecipeType != null && i < slotRecipeType.length && slotRecipeType[i] != TYPE_NOT_TRIED) {
                recipeType = slotRecipeType[i];
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
     * <p><b>只搬内容、不搬"空的仓"</b>：没有内容的槽返回 {@code null}，空催化剂仓不会被写进档案，
     * 也就不会在粘贴时被实体化（与惰性机同口径）。
     */
    private CompoundTag buildArchivedCatalyst(int slot) {
        try {
            CompoundTag entry = new CompoundTag();
            if (slot >= 0 && slot < catalystItems.length && hasAnyCatalystItem(catalystItems[slot])) {
                entry.put(ARCHIVE_KEY_CATALYST_ITEMS, catalystItems[slot].serializeNBT());
            }
            if (slot >= 0 && slot < catalystFluids.length && hasAnyCatalystFluid(catalystFluids[slot])) {
                entry.put(ARCHIVE_KEY_CATALYST_FLUIDS, catalystFluids[slot].serializeNBT());
            }
            if (slot >= 0 && slot < cacheRecipeCount.length) {
                // 阈值语义与惰性机 cacheRecipeCountAt(slot) 同口径（夹到 >=1，避免 byte 里的 0/负数被当成阈值）
                int count = Math.max(1, (int) cacheRecipeCount[slot]);
                if (count != DEFAULT_CACHE_RECIPE_COUNT) {
                    entry.putByte(ARCHIVE_KEY_CATALYST_COUNT, (byte) count);
                }
            }
            return entry.isEmpty() ? null : entry;
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成归档：第 {} 槽催化剂打包失败（该槽只搬样板）pos={} 原因={}",
                    slot + 1, getPos(), failure.toString());
            return null;
        }
    }

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

    /** 源机所在维度的 id（诊断用；拿不到返回空串）。 */
    private String archiveDimensionId() {
        try {
            Level level = getLevel();
            return level == null ? "" : level.dimension().location().toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * ★ <b>落盘一个槽的样板</b>（归档引擎逐槽调用；引擎已保证"这一槽在写之前是空的"）。
     *
     * <p><b>为什么用 {@code getPatternInventory().setStackInSlot} 而不是
     * {@code getTerminalPatternInventory().setItemDirect}（gtlcore 自己 pasteFromTag 用的那个）</b>：
     * 现场取证（{@code LazyPatternAssemblyPartMachine.java:320-327} 对
     * {@code MEPatternBufferPartMachine$1.setItemDirect} 的判读）—— 后者会显式补
     * {@code patternInventory.onContentsChanged(slot)} + {@code onPatternChange(slot)}，
     * 而本类的 {@code onPatternChange} 覆写会 {@code invalidateRewriteCache()}
     * → {@code refreshPatternsNow()}（AE 当场重读全部样板）⇒ <b>逐槽调 = 640 次 AE 刷新</b>、
     * 正是片 D/§25 纪律禁止的 O(n²)。LDLib 的 {@code ItemStackTransfer.setStackInSlot}
     * 自身只是"校验下标 + 写列表"（**不**触发 onContentsChanged），
     * ⇒ 批量落盘只走它，收尾由 {@link #afterArchiveBulkChange()} 统一做**一次**。
     */
    void archiveWritePattern(int slot, ItemStack stack) {
        ItemStackTransfer inventory = getPatternInventory();
        if (inventory == null) {
            return;
        }
        // ★ 0.3.48（惰性 A 档第一步）：归档是**批量**落地 ⇒ 用批量扩容（一次到位 + RESERVE），
        //   语义等同惰性机 growCapacityForBatchInsert（避免逐槽 +16 的抖动）。
        if (slot >= 0 && slot < maxPatternCount && slot >= patternCapacity()) {
            growCapacityForBatchInsert(slot + 1);
        }
        // ★★ 2026-10-02 回归修复（用户实测「粘贴回去后 AE 里看不到」）★★
        //   gtlcore 具体类维护**两张平行存储**：AE/终端面的 internalPatternInventory
        //   （= getTerminalPatternInventory()）与 LDLib 面的 patternInventory（= getPatternInventory()）。
        //   它自己的 pasteFromTag / cutToTag 是**两张都写**的（字节码实证见类顶部 archiveBulkPatternWrite 的注释：
        //   paste :1162 setItemDirect、cut :1582 setStackInSlot + :1607 onContentsChanged）。
        //   我们以前只写 LDLib 面 ⇒ 机器自己的 640 槽界面（读 patternInventory）看得到，
        //   而 AE / 样板访问终端（读 internalPatternInventory）看不到 ✗ —— 正是用户现象。
        //   顺序与 gtlcore 一致：先 AE 面（顺带触发逐槽 onPatternChange ⇒ 父类记账），再 LDLib 面。
        try {
            appeng.api.inventories.InternalInventory terminal = getTerminalPatternInventory();
            if (terminal != null) {
                terminal.setItemDirect(slot, stack);
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成归档：第 {} 槽 AE/终端面写入失败"
                            + "（该槽 AE 里可能看不到，存档面仍会写入）pos={} 原因={}",
                    slot + 1, getPos(), failure.toString());
        }
        inventory.setStackInSlot(slot, stack);
        // 记下"这一槽是归档落地的" ⇒ 给 hasPatternInSlot 的只读兜底用（见那边的注释）
        archiveLandedSlots.add(slot);
    }

    /**
     * ★ 写一槽的配方类型缓存（归档粘贴用）。
     * <p>只走本机既有的那张 {@link #slotRecipeType} 表（与 {@code onPatternChange} 复位的是同一张），
     * 不碰任何缓存/网络路径。
     */
    void writeArchivedRecipeType(int slot, int recipeType) {
        try {
            ensureSlotRecipeTypeArray();
            if (slotRecipeType != null && slot >= 0 && slot < slotRecipeType.length) {
                slotRecipeType[slot] = recipeType;
            }
        } catch (Throwable ignored) {
            // 类型缓存写不进去只是"少记一条"，样板的正确性不受影响
        }
    }

    /** ★ 写一槽的手动指定（与存档同口径：存 id，不存下标）。 */
    void writeArchivedManualType(int slot, ResourceLocation typeId) {
        try {
            if (slot < 0 || slot >= maxPatternCount || typeId == null) {
                return;
            }
            manualSlotTypes.put(slot, typeId);
            refreshManualAssignDisplay();
        } catch (Throwable ignored) {
            // 手动指定写不进去只是"少一条指定"，样板本身已经写好
        }
    }

    /**
     * ★ 写一槽的催化剂内容（含阈值）。
     *
     * <p><b>解完必须补一次 {@code onContentsChanged()}</b> —— 与 gtlcore 的 {@code pasteFromTag}
     * 同款（{@code _javap_p2_api.txt}【2.3】：{@code catalystItems[slot].deserializeNBT(...)} 之后
     * {@code catalystItems[slot].onContentsChanged()}）：LDLib 的 {@code deserializeNBT} 走内部列表、
     * <b>不触发</b>内容变化回调，而本机在 {@code onLoad} 里把那个回调接到了
     * {@code reCalculateCatalystItemMap(slot)} / {@code reCalculateCatalystFluidMap(slot)} 上
     * （那两个是 gtlcore 的 private 方法，子类调不到 ⇒ 只能走这条既有回调）。
     *
     * @return 是否真的写了东西
     */
    boolean writeArchivedCatalyst(int slot, CompoundTag catalyst) {
        if (catalyst == null || catalyst.isEmpty() || slot < 0 || slot >= maxPatternCount) {
            return false;
        }
        boolean wrote = false;
        try {
            if (catalyst.contains(ARCHIVE_KEY_CATALYST_ITEMS, Tag.TAG_COMPOUND)
                    && slot < catalystItems.length && catalystItems[slot] != null) {
                ItemStackTransfer items = catalystItems[slot];
                items.deserializeNBT(catalyst.getCompound(ARCHIVE_KEY_CATALYST_ITEMS));
                try {
                    items.onContentsChanged();
                } catch (Throwable ignored) {
                    // 回调失败不影响仓内容本身（内容已解进去，下一次内容变化还会重算）
                }
                wrote = true;
            }
            if (catalyst.contains(ARCHIVE_KEY_CATALYST_FLUIDS, Tag.TAG_COMPOUND)
                    && slot < catalystFluids.length && catalystFluids[slot] != null) {
                FluidTransferList fluids = catalystFluids[slot];
                fluids.deserializeNBT(catalyst.getCompound(ARCHIVE_KEY_CATALYST_FLUIDS));
                try {
                    fluids.onContentsChanged();
                } catch (Throwable ignored) {
                    // 同上
                }
                wrote = true;
            }
            if (catalyst.contains(ARCHIVE_KEY_CATALYST_COUNT, Tag.TAG_BYTE)
                    && slot < cacheRecipeCount.length) {
                byte count = (byte) Math.max(1, (int) catalyst.getByte(ARCHIVE_KEY_CATALYST_COUNT));
                if (cacheRecipeCount[slot] != count) {
                    cacheRecipeCount[slot] = count;
                    // 阈值变了 ⇒ 这一槽"已缓存的配方"作废。
                    // ★ 只在**确实有缓存**时才动 map / 镜像通知（`removeSlotFromGTRecipeCache` 会
                    //   顺带 removeSlotFromMap + notifyProxySlotRemoved），不让空槽产生空通知。
                    if (hasRecipeCacheInSlot(slot)) {
                        removeSlotFromGTRecipeCache(slot);
                    }
                }
                wrote = true;
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成归档：第 {} 槽催化剂写入失败（该槽催化剂保持原样）pos={} 原因={}",
                    slot + 1, getPos(), failure.toString());
        }
        return wrote;
    }

    /**
     * ★ 写归档里的设置类（倍率 / 除率 / 神锻开关 / 神锻倍率 / 输出模式）。
     *
     * <p>与片 E 读档同口径：<b>直接用字段赋值</b>（不走 setter —— 本机每个 setter 末尾都调
     * {@code invalidateRewriteCache()} → AE 当场重读，5 个字段就是 5 次白通知），
     * 读侧一律夹到合法区间（档案可能来自改过配置 / 手改 NBT 的档），
     * 末尾静默作废改写缓存（AE 通知由 {@link #afterArchiveBulkChange()} 那一次合并负责）。
     */
    void writeArchivedSettings(CompoundTag settings) {
        if (settings == null || settings.isEmpty()) {
            return;
        }
        try {
            Integer scale = archiveSettingInt(settings, ARCHIVE_KEY_PATTERN_SCALE, MOLTEN_KEY_PATTERN_SCALE);
            if (scale != null) {
                patternScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
            }
            Integer div = archiveSettingInt(settings, ARCHIVE_KEY_PATTERN_DIV_SCALE, MOLTEN_KEY_PATTERN_DIV_SCALE);
            if (div != null) {
                patternDivScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, div));
            }
            Integer foaMulti = archiveSettingInt(settings, ARCHIVE_KEY_FOA_MULTIPLIER, MOLTEN_KEY_FOA_MULTIPLIER);
            if (foaMulti != null) {
                foaMultiplier = Math.max(MIN_FOA_MULTIPLIER, Math.min(MAX_FOA_MULTIPLIER, foaMulti));
            }
            Boolean foa = archiveSettingBoolean(settings, ARCHIVE_KEY_FOA_MODE, MOLTEN_KEY_FOA_MODE);
            if (foa != null) {
                foaMode = foa;
            }
            Boolean output = archiveSettingBoolean(settings, ARCHIVE_KEY_OUTPUT_MODE, MOLTEN_KEY_OUTPUT_MODE);
            if (output != null) {
                isOutputMode = output;
            }
            // 静默作废"改写样板"缓存（收尾那一次 markPatternTableStale 会让 AE 重取一次样板表）
            rewrittenPatterns = null;
            rewrittenToOriginal.clear();
            rewriteCacheBuilt = false;
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成归档：设置类写入失败（已忽略，按原设置）pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    /** 从档案的设置子标签里读一个 int（先认档案格式键，再认本机自己的落盘键；都没有 ⇒ null）。 */
    private static Integer archiveSettingInt(CompoundTag settings, String primary, String fallback) {
        if (settings.contains(primary, Tag.TAG_INT)) {
            return settings.getInt(primary);
        }
        if (settings.contains(fallback, Tag.TAG_INT)) {
            return settings.getInt(fallback);
        }
        return null;
    }

    /** 同上，布尔（NBT 里是 byte）。 */
    private static Boolean archiveSettingBoolean(CompoundTag settings, String primary, String fallback) {
        if (settings.contains(primary, Tag.TAG_BYTE)) {
            return settings.getBoolean(primary);
        }
        if (settings.contains(fallback, Tag.TAG_BYTE)) {
            return settings.getBoolean(fallback);
        }
        return null;
    }

    /** ★ 写归档里的机器名字（只有调用方确认"档案带了 + 玩家勾了"才会走到这里）。 */
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
     * ★ <b>清空本仓的归档内容</b>（剪切语义的第二半；只有调用方确认"档案已写进工具"之后才许调）。
     *
     * <p>清空的内容与 {@link #buildArchivePayload} 搬走的内容<b>一一对应</b>：
     * 样板 + 逐槽配方类型缓存 + 手动指定 + 逐槽催化剂（含阈值）。
     *
     * <p><b>有意不碰</b>（它们不在本档案的范围内，"没搬走却删掉"= 数据事故）：
     * 容量/槽位结构、gtlcore 的共享催化剂仓 / 共享催化剂罐 / 共享电路仓、
     * {@code keepByProduct}、镜像绑定（{@code proxies}）。
     * <p>收尾<b>不在这里</b>：由调用方随后调一次 {@link #afterArchiveBulkChange()}。
     */
    void clearArchiveContent() {
        ItemStackTransfer inventory = getPatternInventory();
        int capacity = archiveCapacity();
        // ★ 2026-10-02：清源也要写**两张存储**（AE/终端面 + LDLib 面），与 gtlcore 的 cutToTag 同款
        //   （字节码实证 _javap_simple_gtlcore_buffer.txt:1566 setItemDirect(EMPTY) + :1582 setStackInSlot(EMPTY)）。
        //   只清 LDLib 面的话，AE / 样板访问终端里会残留"鬼影样板"✗（用户实测「剪切了 AE 里还有」）。
        //   窗口守卫：逐槽 setItemDirect 会回调 onPatternChange，窗口内降级为"只做父类记账"，
        //   通知统一由随后那一次 afterArchiveBulkChange() 负责 ✓
        beginArchiveBulkWrite();
        try {
            for (int i = 0; i < capacity; i++) {
                try {
                    if (inventory != null) {
                        inventory.setStackInSlot(i, ItemStack.EMPTY);
                    }
                } catch (Throwable ignored) {
                    // 单槽清不掉不影响别的槽
                }
                try {
                    appeng.api.inventories.InternalInventory terminal = getTerminalPatternInventory();
                    if (terminal != null && i < terminal.size()) {
                        terminal.setItemDirect(i, ItemStack.EMPTY);
                    }
                } catch (Throwable ignored) {
                    // AE 面单槽清不掉：不影响 LDLib 面与其它槽
                }
                try {
                    if (slotRecipeType != null && i < slotRecipeType.length) {
                        slotRecipeType[i] = TYPE_NOT_TRIED;
                    }
                } catch (Throwable ignored) {
                    // 同上
                }
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成归档：清空样板失败（已尽力而为）pos={} 原因={}",
                    getPos(), failure.toString());
        }
        try {
            manualSlotTypes.clear();
            refreshManualAssignDisplay();
        } catch (Throwable ignored) {
            // 手动指定表清不掉只是"多留了一张表"
        }
        try {
            for (int i = 0; i < capacity; i++) {
                ItemStackTransfer items = i < catalystItems.length ? catalystItems[i] : null;
                if (items != null) {
                    for (int s = 0; s < items.getSlots(); s++) {
                        items.setStackInSlot(s, ItemStack.EMPTY);
                    }
                    // ★ 本机 onLoad 把这个回调接到了 gtlcore 的 reCalculateCatalystItemMap(i)
                    //   （private，子类调不到）⇒ 清完必须补一次，催化剂表才会跟着空。
                    try {
                        items.onContentsChanged();
                    } catch (Throwable ignored) {
                        // 回调失败不影响仓内容本身
                    }
                }
                FluidTransferList fluids = i < catalystFluids.length ? catalystFluids[i] : null;
                if (fluids != null) {
                    for (int t = 0; t < fluids.getTanks(); t++) {
                        fluids.setFluidInTank(t, FluidStack.empty());
                    }
                    try {
                        fluids.onContentsChanged();
                    } catch (Throwable ignored) {
                        // 同上
                    }
                }
                if (i < cacheRecipeCount.length) {
                    cacheRecipeCount[i] = (byte) DEFAULT_CACHE_RECIPE_COUNT;
                }
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成归档：清空催化剂失败（已忽略）pos={} 原因={}",
                    getPos(), failure.toString());
        }
        // 全仓已空 ⇒ 归档落地槽的兜底标记也跟着作废（免得留下"幽灵样板槽"）
        try {
            archiveLandedSlots.clear();
        } catch (Throwable ignored) {
            // 清不掉最多只是 hasPatternInSlot 的兜底多认一个空槽（下一段还会按"仓里有没有东西"再判一次）
        }
    }

    /**
     * ★★ <b>归档批量改动之后的唯一收尾入口</b>（粘贴 / 剪切清源都只调它一次）。
     *
     * <h3>为什么必须是 {@code refreshAllByProduct()}，而不是"只调 reCalculatePatternSlotMap + 置标记"</h3>
     * 方案 §4.3 的推荐形状是"现役机实现 = 重算 + 置标记，只调一次"。<b>但按现场字节码，只调
     * {@code reCalculatePatternSlotMap()} 是不工作的</b>：它是
     * {@code patternSlotMap.clear()} + 遍历 {@code slot2PatternMap} 重建
     * （{@code _census/_javap_simple_gtlcore_buffer.txt:683-730}），
     * <b>它不读仓</b> ⇒ 直接往 {@code patternInventory} 写完样板后只调它，
     * {@code slot2PatternMap} 仍然是旧的 ⇒ {@code getAvailablePatterns()}
     * （= {@code patternSlotMap.keySet()}，:2130-2137）里<b>看不到新样板</b> ⇒ AE 不会来下单。
     *
     * <p>正确的"单趟重扫全仓"入口就是 gtlcore 自己在 {@code pasteFromTag} 收尾时调的那个
     * {@code refreshAllByProduct()}（:753-790）：清 {@code slot2PatternMap} → 逐槽
     * {@code getRealPattern(i, stack)} → {@code reCalculatePatternSlotMap()} → {@code needPatternSync = true}。
     * 它<b>只扫一趟</b>（O(n)），既满足"只调一次"，又满足"绝不逐槽 {@code onPatternChange}"
     * （后者会逐槽 {@code invalidateRewriteCache()} → AE 当场重读 = 640 次，片 D 明令禁止的 O(n²)）。
     *
     * <p>除此之外还要**静默作废"改写样板"缓存**：本机开了倍率/神锻时
     * {@code getAvailablePatterns()} 会先命中 {@code rewriteCacheBuilt && rewrittenPatterns != null}
     * 那个快路径（:2003）⇒ 不作废的话 AE 拿到的还是旧的那一份改写结果。
     * 做法与片 E 读档完全一致（只置空三个字段，不在这里同步捅 AE）。
     */
    public void afterArchiveBulkChange() {
        // ★ 2026-10-02：批量窗口在任何情况下都要关掉（客户端也要关：客户端也会走 clearArchiveContent）
        archiveBulkPatternWrite = false;
        if (isRemote()) {
            return;
        }
        try {
            // ① 单趟重扫全仓（gtlcore 自己的批量粘贴收尾通道）：slot2PatternMap + 样板表 + needPatternSync
            refreshAllByProduct();
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成归档：收尾重扫样板表失败"
                            + "（样板已写好，缓存最迟由下一 tick 的既有通道兜住）pos={} 原因={}",
                    getPos(), failure.toString());
        }
        try {
            // ② 静默作废"改写样板"缓存（与片 E 读档同款：只置空字段）
            rewrittenPatterns = null;
            rewrittenToOriginal.clear();
            rewriteCacheBuilt = false;
            // ③ 置一次「样板表过期」（片 D 既有入口：只置 needPatternSync，由每 tick 的既有消费点合并通知 AE）
            markPatternTableStale("归档批量收尾(片C)");
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成归档：收尾置位失败（已忽略）pos={} 原因={}",
                    getPos(), failure.toString());
        }
        // ★ 2026-10-02 诊断（每次批量收尾一行，用来核对"机器界面 / AE 终端"是否一致 ✓）：
        //   两张存储的非空槽数 + AE 可见样板数 + 当前容量，一眼就能看出问题在哪一侧 ✗
        try {
            int ldlNonEmpty = 0;
            ItemStackTransfer inv = getPatternInventory();
            if (inv != null) {
                for (int i = 0; i < inv.getSlots(); i++) {
                    ItemStack stack = inv.getStackInSlot(i);
                    if (stack != null && !stack.isEmpty()) {
                        ldlNonEmpty++;
                    }
                }
            }
            int aeNonEmpty = 0;
            appeng.api.inventories.InternalInventory term = getTerminalPatternInventory();
            if (term != null) {
                for (int i = 0; i < term.size(); i++) {
                    ItemStack stack = term.getStackInSlot(i);
                    if (stack != null && !stack.isEmpty()) {
                        aeNonEmpty++;
                    }
                }
            }
            GTLModular.LOGGER.info("[gtlmodular] 归档批量收尾：AE 可见样板={} LDLib 面非空={} AE 面非空={} 容量={} pos={}",
                    getAvailablePatterns().size(), ldlNonEmpty, aeNonEmpty, patternCapacity(), getPos());
        } catch (Throwable ignored) {
            // 诊断日志失败绝不影响归档结果
        }
        try { scheduleRenderUpdate(); } catch (Throwable ignored) { }   // ★ 0.3.60：批量清空/写入后让客户端重读（否则界面显示"鬼影"✗ 需关掉重开才刷 ✓）
    }

    /**
     * ★ <b>「槽激活门是否带上自造兜底 {@link #archiveLandedSlots}」开关</b>
     * （★ 2026-10-27 新增，<b>默认 = 新行为 = 逐字交回父类</b>）。
     *
     * <p>{@code false}（<b>默认</b>）⇒ {@link #hasPatternInSlot(int)} <b>逐字交回</b>
     * {@code super.hasPatternInSlot(slot)}：槽"有没有样板"只认 gtlcore 那一个真值来源
     * （{@code MEPatternBufferPartMachine.hasPatternInSlot} → 它的私有 {@code hasPatternArray}），
     * <b>不自造兜底、也不吞异常</b>。
     * <p>{@code true} ⇒ 退回旧口径：父类判据 {@code ||} "这一槽是归档落地的"
     * （{@code archiveLandedSlots.contains(slot)}），且外面套一层 {@code catch (Throwable)} 兜底。
     *
     * <p><b>为什么要改</b>：依据 = 山海的做法 —— 山海这一层<b>从不覆写</b>槽激活门
     * （{@code _census/shanhai-src/com/dishanhai/gt_shanhai/common/machine/part/RecipeTypePatternBufferPartMachine.java}
     * 只额外把通配符槽算进去，其余原样 {@code super}）⇒ <b>槽激活门只应有一个真值来源</b>；
     * 我们多一个来源，就多一类"AE 认为有样板 / 机器认为没有"（或反过来）的分歧。
     *
     * <p><b>⚠ {@code archiveLandedSlots} 原本是修什么问题的（【已取证·留档】⇒ 所以只 gate、不删）</b>：
     * 它是"片 C"的只读兜底，见字段自身注释 {@link #archiveLandedSlots}。当时的理由是：
     * gtlcore 的 {@code hasPatternArray} 是 {@code private}，只在<b>逐槽</b>
     * {@code onPatternChange(int)} / onLoad 异步补齐里被置位，而归档批量落盘"按纪律不逐槽调
     * {@code onPatternChange}"⇒ 这批新写进去的槽在 gtlcore 自己的数组里会一直是 {@code false}
     * ⇒ 粘进去的样板<b>会跑不起来</b>。
     * 另见留档 {@code _census/样板与AE不同步_排查.md:22}
     * （原文：「{@code setStackInSlot} <b>不保证</b>回调 ⇒ 必须靠下一跳」）。
     * <p><b>但那条前提在 2026-10-02 的修复之后已经不成立</b>：归档落盘现在会先写 AE/终端面
     * {@code terminal.setItemDirect(slot, stack)}（本类归档落盘那一段，见注释「★★ 2026-10-02 回归修复」），
     * 而 gtlcore 的 {@code setItemDirect} 是
     * 「{@code patternInventory.setStackInSlot} + {@code patternInventory.onContentsChanged} +
     * {@code onPatternChange}」（取证：{@code _census/gtlcore-unofficial/gtlcore-Unofficial-main/src/main/java/org/gtlcore/gtlcore/common/machine/multiblock/part/ae/MEPatternBufferPartMachine.java:101-106}）
     * ⇒ <b>逐槽 {@code onPatternChange} 一定会被调到</b>；而本类的覆写无论如何都会先调
     * {@code super.onPatternChange(index)}（{@code :1204-1209}）⇒ gtlcore 的
     * {@code hasPatternArray} 会被正常置位 ⇒ 这个兜底<b>在正常路径上已经没有对象</b>。
     * <p><b>【未取证】</b>：本沙箱无 {@code pwsh}、无法编译、更无法实机运行 ⇒
     * "兜底已冗余"这个结论<b>没有跑过实机验证</b>。残余风险：若那次 AE 面写入抛异常
     * （被本类归档落盘的 {@code catch} 吞掉），就只剩 LDLib 面写入 ⇒ {@code hasPatternArray}
     * 可能不被置位 ⇒ <b>粘贴进去的样板可能不参与匹配</b>（= 片 C 当初那个症状）。
     * 真出现该症状 ⇒ 把本行置 {@code true} 立刻回到旧行为（一行回退）。
     *
     * <p><b>一行回退</b>：把本行置 {@code true}。
     */
    public static final boolean HAS_PATTERN_SLOT_ARCHIVE_FALLBACK = true;

    /**
     * ★ <b>片 C 的只读兜底</b>：这个槽算不算"有样板"？
     *
     * <p>gtlcore 具体类的实现是读它自己的 {@code private boolean[] hasPatternArray} ——
     * 那个数组只在<b>逐槽</b> {@code onPatternChange(int)}（:438-523）与 onLoad 异步补齐
     * （{@code lambda$onLoad$6}，:2476-2515）里被置位；而它又是
     * {@code InternalSlot.isActive()} 的门（{@code _javap_simple_internalslot.txt:122-141}），
     * {@code isActive()} 又决定 {@code getActiveSlots()} / {@code getActiveAndUnCachedSlots()}
     * （配方匹配与 AE 推送看的就是这两个）⇒ 归档按纪律**不逐槽调 onPatternChange** 时，
     * 必须自己把"这批新写进去的槽"补上，否则粘进去的样板<b>会跑不起来</b>。
     *
     * <p>本覆写做两件事（都只读、都 O(1)）：
     * <ol>
     *   <li><b>仓里是空的 ⇒ 一定没有样板</b>：顺手纠正"批量清空（我们的剪切）之后
     *       gtlcore 那个缓存数组还是 true"的残留（空槽不可能有样板，这是恒真的）；</li>
     *   <li>否则 = 父类判据 <b>或</b> "这一槽是归档落地的"（{@link #archiveLandedSlots}）。</li>
     * </ol>
     * <p>不会放松任何东西：它只在"仓里真有东西"时才可能返回 true；
     * 而"是不是真的样板"由 {@code refreshAllByProduct()} → {@code getRealPattern()} 把关
     * （解不出来的栈根本进不了 {@code slot2PatternMap}，AE 也就永远不会往那一槽推送）。
     */
    @Override
    protected boolean hasPatternInSlot(int slot) {
        // ★ 2026-10-27【④】默认口径：**逐字交回父类**（去掉自造兜底 archiveLandedSlots 与吞异常的兜底）。
        //   父类实现自带越界保护、且不抛异常 —— 取证：
        //   `_census/gtlcore-unofficial/.../ae/MEPatternBufferPartMachine.java:389-392`
        //   「return slotIndex >= 0 && slotIndex < hasPatternArray.length && hasPatternArray[slotIndex];」
        //   ⇒ 原先那层 catch (Throwable) 已无对象；去掉它 + 去掉自造兜底，才能保证
        //   "槽激活门只有一个真值来源"（依据与留档见 HAS_PATTERN_SLOT_ARCHIVE_FALLBACK 的注释）。
        //   一行回退：把 HAS_PATTERN_SLOT_ARCHIVE_FALLBACK 置 true（⇒ 逐字退回旧实现）。
        if (!HAS_PATTERN_SLOT_ARCHIVE_FALLBACK) {
            return super.hasPatternInSlot(slot);
        }
        try {
            ItemStackTransfer inventory = getPatternInventory();
            if (inventory != null && slot >= 0 && slot < inventory.getSlots()) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack == null || stack.isEmpty()) {
                    return false;
                }
            }
            return super.hasPatternInSlot(slot) || archiveLandedSlots.contains(slot);
        } catch (Throwable ignored) {
            // 只读判据绝不许把调用方（AE 推送 / 配方匹配 / Jade）掀翻：异常时保守地交回父类口径
            try {
                return super.hasPatternInSlot(slot);
            } catch (Throwable ignoredAgain) {
                return false;
            }
        }
    }

    // ================= 查找侧逐槽隔离（★ 2026-10-15 新增：纯覆写，零 mixin） =================
    // 依据：_census/逐槽隔离_查找侧方案.md（§1.2 / §3 / §4）。
    // 上游两个隔离实现（Enhancedcore 的 IvBufferLifecycleMixin、星律的
    // RecipeTypePatternBufferPartMachine）选的都是基类的 getActiveSlots / getActiveAndUnCachedSlots
    // —— 它们就是「哪些槽的料参与本次配方查找/匹配」的**唯一口径入口**。
    // 我们这台机是**自己的子类**，所以直接覆写即可：不写 mixin、不登记 gtlmodular.mixins.json。

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
     * ⚠️ 两台总成（本类 与 {@link LazyPatternAssemblyPartMachine}）的这个开关<b>必须同时改</b>，
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
     * ★ <b>只读</b>解析「本总成自己负责的模式」，<b>只认本总成自己的三档指示，绝不回落到控制器当权威</b>。
     *
     * <p>判据顺序（与 {@link #currentModeIndexForNaming(int)} 的 ① ② ③ <b>逐条同源</b>，
     * 保证「界面显示哪个模式」与「实际限哪个模式」是同一个下标 ✓）：
     * <ol>
     *   <li>{@link #modeCursorIndex}（模式页游标 = 本总成自己的模式，权威）；</li>
     *   <li>调用点给的 hint（本类传的是 {@link #activeRecipeTypeIndex}）；</li>
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
            return -1;   // ③ 都取不到 ⇒ fail-open
        } catch (Throwable failure) {
            return -1;   // 只读解析也不许把查找路径掀翻
        }
    }

    /**
     * ★ <b>R6 兜底闸：本次查找「该不该按本总成的模式收窄」</b>。
     *
     * <p><b>R6 是什么</b>（方案 §7-R6）：控制器的自动切模式
     * （{@code autoSwitchTick()} → {@code findModeWithWaitingStock()} → {@code lockRecipeTypeForSlot()}）
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
                //   不改这一条会怎样：非激活类型的**冷**样板槽（没有 recipes2SlotsMap 快路径）被摘出查找面，
                //   而冷槽只能走 MEPatternRecipeHandlePart.handleRecipe 的「遍历 getActiveSlots()」回退分支
                //   ⇒ 拿不到第一次匹配 ⇒ 该类冷样板永久饥饿（热样板仍走快路径，故表现为「看似两类、只吃一类」）✗。
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
     * 与逐槽识别缓存 {@link #slotRecipeType}。
     * <b>绝不</b>调用 {@link #recipeTypeOfSlot(int)} / {@link #detectRecipeType(int, List)} /
     * {@code scanTypesForMatch} 这些**会触发表扫描**（逐类型 × 该类型全部配方 × {@code patternIdentityMatches}）的解析方法 ✗
     * —— 本方法在每个槽上都跑，一旦误调就是「每 tick × 每槽 全表扫描」✗（比不收窄还糟）。
     * 缓存是"只写不主动读"的：{@link #onPatternChange(int)} / 推送路径已经填好，这里只**读**，绝不改写它 ✓。
     *
     * <p><b>哨兵放行清单（方案 §4 步骤 2 / §7-R4：宁可少收窄，绝不误挡 ✗）</b>：
     * {@code -1}（{@link #TYPE_NOT_TRIED}，还没试过识别）、{@link #TYPE_UNKNOWN}（试过但识别不出来）、
     * 任何不在 {@code [0, size)} 里的越界值、缓存表未初始化 / 长度不匹配 / 下标越界 ——
     * <b>一律「放行」（不参与收窄）</b>。
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
        final int mode = lookupNarrowingModeIndex;   // 供逐槽判据读（见字段注释）
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
     * → ② {@link #slotRecipeType}。**绝不**触发表扫描 ✗。
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
            final int[] cache = slotRecipeType;
            if (cache == null || slot < 0 || slot >= cache.length) {
                return true;   // 还没建表 / 下标越界 ⇒ 放行（方案 §4 步骤 2 的防御性要求）
            }
            int cached = cache[slot];
            if (cached == TYPE_UNKNOWN) {
                return true;   // 试过但识别不出来 ⇒ 放行（R4：绝不把认不出的槽误锁在门外）
            }
            if (cached == TYPE_NOT_TRIED) {
                return true;   // 还没试过识别 ⇒ 放行（**这里绝不顺手去触发识别** ✗）
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
     * <p>⚠ 反向代价（方案 §6.3，必须写明）：{@code super} 的遍历本身仍是 {@code O(容量)}，本覆写**不降低**这一层；
     * 它省的是「根本没被喂进原料树的槽」那部分的候选配方条数与逐槽尝试次数。
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
     * —— 本仓库既有注释已写明（{@code LazyPatternAssemblyPartMachine.java:1413-1416}）⇒ 这条链就是查找侧本身 ✓。
     */
    @Override
    protected int[] getActiveAndUnCachedSlots() {
        return narrowSlotsToCurrentMode(super.getActiveAndUnCachedSlots());
    }
    // ================= 查找侧逐槽隔离（新增段结束） =================

    // ---------- 存档 ----------

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (slotRecipeType != null) {
            // 稀疏写：只存「已识别（>=0）」与「识别不出（-2）」的槽位，跳过绝大多数默认的 -1。
            // 超级总成 2048 槽时能从 8KB 降到 0；读侧仍兼容旧的整份数组格式。
            int count = 0;
            for (int v : slotRecipeType) {
                if (v != TYPE_NOT_TRIED) {
                    count++;
                }
            }
            if (count > 0) {
                int[] sparse = new int[count * 2];
                int k = 0;
                for (int i = 0; i < slotRecipeType.length; i++) {
                    if (slotRecipeType[i] != TYPE_NOT_TRIED) {
                        sparse[k++] = i;
                        sparse[k++] = slotRecipeType[i];
                    }
                }
                tag.putIntArray("gtlmodular$slot_recipe_type_sparse", sparse);
            }
        }
        if (!manualSlotTypes.isEmpty()) {
            CompoundTag manual = new CompoundTag();
            manualSlotTypes.forEach((slot, id) -> manual.putString("s" + slot, id.toString()));
            tag.put("gtlmodular$manual_slot_types", manual);
        }
        // ★★ 片 E（2026-09-27）：五个「视图改写设置」落盘 ★★
        //   【为什么必须补】这五个字段只带 LDLib 的 @DescSynced、**不带 @Persisted**，而
        //   `ManagedFieldUtils.createKey` 只在带 @Persisted 时才把 isPersist 置真
        //   ⇒ 托管机制既不写世界存档、也不写掉落物 ⇒ 重启/重进世界后回默认值（功能 #6）。
        //   【键名】用本机自己的 `gtlmodular$` 前缀、**不带 lazy_**（惰性机同名键是
        //   `gtlmodular$lazy_pattern_scale`…，`LazyPatternAssemblyPartMachine.java:621-625`）。
        //   已 grep 全仓确认这 5 个键名此前无人使用 ⇒ 与本方法既有键（`gtlmodular$slot_recipe_type_sparse`
        //   / `gtlmodular$manual_slot_types`）以及归档键（`gtlmodular$archive_*`）都不撞车。
        //   【forDrop 口径】**掉落也写**（设置类字段）——依据三条：
        //     ① 本方法既有约定：两个既有键组都**没有**任何 forDrop 分支；
        //     ② gtlcore 具体类 `saveCustomPersistedData`（_census/_gto_wildcard/javap_gtlcore/
        //        GTL_Buffer_full.txt:684-757）同样忽略 forDrop；
        //     ③ 惰性机同款注释（LazyPatternAssemblyPartMachine.java:222-225 + :3726-3728）：
        //        「这五个都是**设置类**字段（不带任何物品/流体内容，抄不掉东西）⇒ 与 customName 同口径，
        //          **掉落时也写**（机器被拎起来再放下不该丢倍率）」，只有「按槽下标对齐的内容」才不写。
        tag.putInt("gtlmodular$pattern_scale", patternScale);
        tag.putInt("gtlmodular$pattern_div_scale", patternDivScale);
        tag.putBoolean("gtlmodular$foa_mode", foaMode);
        tag.putInt("gtlmodular$foa_multiplier", foaMultiplier);
        tag.putBoolean("gtlmodular$output_mode", this.isOutputMode);
        // ★★ 2026-10-14（本轮新增）：**绑定模式持久化** —— 本总成负责的模式的**类型 id** ★★
        //   【为什么需要】modeCursorIndex 只带 @DescSynced、**无 @Persisted**，也不在既有任何键里
        //     ⇒ 读档后归 0、modeCursorTouched=false ⇒ 每 tick 的「游标跟随控制器」把它改回主机模式
        //     ⇒ 重启后绑定失效（本键要修的就是这一条）。
        //   【键名】**gtlmodular$bound_mode_type**：沿用本机 gtlmodular$ 前缀（不带 lazy_，那是惰性机的键位）；
        //     已 grep 全仓确认此前无人使用，与既有键 gtlmodular$slot_recipe_type_sparse / gtlmodular$slot_recipe_type
        //     / gtlmodular$manual_slot_types 以及归档键 gtlmodular$archive_* 都不撞车。
        //   【格式】单个字符串：ResourceLocation#toString()（例 `gtceu:lathe`）——
        //     存 id 不存下标：类型表的顺序/长度随控制器与配置变化，存下标读档后会指到别的模式；
        //     存 id 与既有 gtlmodular$manual_slot_types 同口径（那边也是 `s<槽>` → id 字符串）。
        //   【未绑定 ⇒ 一个字节都不写】键缺失的语义就是"未绑定/跟随控制器"（= 老档的默认行为）✓；
        //     玩家选定模式时由 rememberBoundModeType() 记进 field 并 markDirty，这里只做序列化。
        //   【forDrop 口径】与本方法既有两个键组、以及上面 5 个设置类键同口径（**没有** forDrop 分支）：
        //     它不带任何物品/流体内容、抄不掉东西 ⇒ 掉落也写（机器拎起来再放下不该丢绑定）。
        if (boundModeType != null) {
            tag.putString("gtlmodular$bound_mode_type", boundModeType.toString());
        }
        // ★★ 2026-10-15（本轮新增）：**名字按「本总成 + 模式」落盘** —— "重进游戏名字被改回去"的根治点 ★★
        //   【键名】`gtlmodular$mode_names`（CompoundTag：模式 id → 名字）与 `gtlmodular$locked_name`
        //     （String：玩家亲手改过的名字，空 = 没改过）。已 grep 全仓确认这两个键名此前无人使用，
        //     与 gtlmodular$bound_mode_type / slot_recipe_type* / manual_slot_types / archive_* 都不撞车；
        //     惰性机用带 lazy_ 前缀的同名键（NBT_MODE_NAMES / NBT_LOCKED_NAME），两边格式独立、互不干扰。
        //   【向后兼容】键缺失 ⇒ 名字表为空 + 未锁定 = 改动前行为（该模式还没有名字 ⇒ 用显示名写一次）✓；
        //     既有键语义一个都没动（**只新增**）✓。
        //   【上界】名字表 ≤ {@value #MAX_SAVED_MODE_NAMES} 条、单条 ≤ {@value #MAX_SAVED_NAME_LENGTH} 字符。
        //   【forDrop】与上面 boundModeType / 5 个设置类键同口径：**掉落也写**（机器拎起来再放下不该丢名字）。
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
                tag.put("gtlmodular$mode_names", names);
            }
        }
        if (!lockedName.isEmpty()) {
            tag.putString("gtlmodular$locked_name", lockedName.length() > MAX_SAVED_NAME_LENGTH
                    ? lockedName.substring(0, MAX_SAVED_NAME_LENGTH) : lockedName);
        }
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        // ★ 0.3.48（惰性 A 档第一步）：父类（含 Base）已经把样板仓反序列化完了 ⇒ 立刻按"已用最高槽"回填容量。
        //   这是防丢样板的关键一步：起步容量 32 < 存档里已有的样板数时，不回到足够容量就会被截断 ✗。
        backfillCapacityAfterLoad();
        int[] sparse = tag.getIntArray("gtlmodular$slot_recipe_type_sparse");
        if (sparse.length > 0) {
            int slots = slotCount();
            if (slots > 0) {
                int[] fresh = new int[slots];
                Arrays.fill(fresh, TYPE_NOT_TRIED);
                for (int i = 0; i + 1 < sparse.length; i += 2) {
                    int slot = sparse[i];
                    if (slot >= 0 && slot < slots) {
                        fresh[slot] = sparse[i + 1];
                    }
                }
                slotRecipeType = fresh;
            }
        } else {
            // 旧格式（整份数组）
            int[] saved = tag.getIntArray("gtlmodular$slot_recipe_type");
            if (saved.length == slotCount()) {
                slotRecipeType = saved;
            }
        }
        manualSlotTypes.clear();
        CompoundTag manual = tag.getCompound("gtlmodular$manual_slot_types");
        for (String key : manual.getAllKeys()) {
            if (!key.startsWith("s")) {
                continue;
            }
            try {
                int slot = Integer.parseInt(key.substring(1));
                net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(manual.getString(key));
                if (id != null) {
                    manualSlotTypes.put(slot, id);
                }
            } catch (RuntimeException ignored) {
                // 坏条目直接跳过
            }
        }
        // ★★ 2026-10-14（本轮新增）：绑定模式读档（与 saveCustomPersistedData 的 gtlmodular$bound_mode_type 一一对应）★★
        //   【读侧纪律】
        //   ① contains 守卫（带类型）：老存档没有这个键 ⇒ boundModeType 保持 null = 未绑定 ⇒
        //      modeCursorTouched 保持 false ⇒ **完全走现有默认行为**（游标跟随控制器）✓ 既有键一个都没碰 ✓；
        //   ② 坏值（空串 / 不是合法的 ResourceLocation）⇒ tryParse 返回 null ⇒ 同"未绑定"处理，
        //      **不报错、不抛异常**（与上面 manualSlotTypes 的坏条目同口径）✓；
        //   ③ 解析下标**不在这里做**：读档期机器还没挂到 level / 控制器上（getControllers() 为空）⇒
        //      getAvailableRecipeTypes() 为空 ⇒ 此刻无法区分"类型被移除"与"控制器还没就绪"（见
        //      applyBoundModeTypeIfPossible 的注释）⇒ 这里只记 id + 置"待解析"，由每 tick 的服务端 tick 补解析；
        //      顺手调一次是给"控制器已经挂上"的场合（热重载/结构已成形）当场生效，不成就等 tick ✓；
        //   ④ 整体 try/catch(Throwable) 吞掉：一个绑定键坏掉绝不许把整台机器读挂（同片 E 那条纪律）。
        try {
            // 【服务端持久化】客户端那份 BE 的 NBT（区块包/同步）里同样会有这个键，但那不是权威状态
            //   ⇒ 客户端不读回落盘状态；模式页显示靠既有的 @DescSynced modeCursorIndex 同步回来 ✓。
            if (!isRemote() && tag.contains("gtlmodular$bound_mode_type", Tag.TAG_STRING)) {
                ResourceLocation bound = ResourceLocation.tryParse(tag.getString("gtlmodular$bound_mode_type"));
                if (bound != null) {
                    this.boundModeType = bound;
                    this.boundModePendingResolve = true;
                    applyBoundModeTypeIfPossible();
                }
            }
        } catch (Throwable failure) {
            this.boundModeType = null;
            this.boundModePendingResolve = false;
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成：绑定模式读档失败（已忽略，按未绑定处理）pos={} 原因={}",
                    getPos(), failure.toString());
        }
        // ★★ 2026-10-15（本轮新增）：**名字表 + "玩家改过名"标记读档** ★★
        //   （与 saveCustomPersistedData 的 gtlmodular$mode_names / gtlmodular$locked_name 一一对应）
        //   【读侧纪律】① contains 守卫（带类型）：老存档没有这两个键 ⇒ 名字表为空 + 未锁定 =
        //     改动前的行为，既有键一个都没碰 ✓；② 坏条目（空键/空值/超长）一律跳过，整体
        //     try/catch(Throwable) 吞掉 —— 一个坏键绝不许把整台机器读挂（与 manualSlotTypes 同口径）；
        //   ③ 读完必须把"上次观察到的名字"清空：否则读档恢复出来的名字会被当成"玩家改名"而误锁 ✗。
        try {
            if (!isRemote()) {
                modeNames.clear();
                this.lockedName = "";
                this.observedName = null;
                this.appliedModeKey = null;
                this.lastNamingController = null;
                if (tag.contains("gtlmodular$mode_names", Tag.TAG_COMPOUND)) {
                    CompoundTag names = tag.getCompound("gtlmodular$mode_names");
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
                if (tag.contains("gtlmodular$locked_name", Tag.TAG_STRING)) {
                    String locked = tag.getString("gtlmodular$locked_name");
                    this.lockedName = locked.length() > MAX_SAVED_NAME_LENGTH ? "" : locked;
                }
            }
        } catch (Throwable failure) {
            modeNames.clear();
            this.lockedName = "";
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成：名字表读档失败（已忽略，按「该模式还没有名字」处理）pos={} 原因={}",
                    getPos(), failure.toString());
        }
        // ★★ 片 E（2026-09-27）：五个「视图改写设置」读档（与 saveCustomPersistedData 那 5 个键一一对应）★★
        //   【读侧纪律】
        //   ① 一律 `contains` 守卫（带类型）—— 老存档没有这些键 ⇒ **保持字段初值（默认）**，绝不改默认值：
        //      patternScale/patternDivScale=1、isOutputMode=true、foaMode=false、foaMultiplier=15（:345-362）；
        //   ② 一律夹到合法区间：倍率/除率 1..100、神锻倍率 1..30（与惰性机读侧同口径，
        //      LazyPatternAssemblyPartMachine.java:3825-3840）；
        //   ③ 整体 try/catch(Throwable) 吞掉：一段设置坏掉绝不许把整台机器读挂；
        //   ④ **只读赋值、不调 setter**：本机三个 setter 末尾都调 `invalidateRewriteCache()`
        //      → `notifyPatternsChanged()` → `refreshPatternsNow()`（reCalculatePatternSlotMap +
        //      AE refreshNodeCraftingProvider/requestUpdate，:2262/:2276/:2306/:2318 + :2152-2183）——
        //      读档期节点多半还没上线，那是白通知（一次读档最多白发 3 次）；惰性机读档同口径
        //      （Lazy…:3819-3823「直接用字段赋值、不用 setter」）；
        //   ⑤ 收尾**只做一次**：静默作废改写缓存（只置空三个字段、不通知 AE，同惰性机
        //      invalidateRewriteCacheQuietly，Lazy…:1709-1720）+ 置一次「样板表过期」（片 D 既有入口
        //      markPatternTableStale，:628；它只置 needPatternSync，由 autoSwitchTickInner :1329 每 tick
        //      合并消费一次 ⇒ AE 上线后自己重取一次样板表，缩放立刻生效；gtlcore 自己 onLoad 也这么做，
        //      见 _census/_gto_wildcard/javap_gtlcore/GTL_Buffer_full.txt:2355 `putfield needPatternSync`）。
        try {
            if (tag.contains("gtlmodular$pattern_scale", Tag.TAG_INT)) {
                this.patternScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, tag.getInt("gtlmodular$pattern_scale")));
            }
            if (tag.contains("gtlmodular$pattern_div_scale", Tag.TAG_INT)) {
                this.patternDivScale = Math.max(MIN_SCALE,
                        Math.min(MAX_SCALE, tag.getInt("gtlmodular$pattern_div_scale")));
            }
            if (tag.contains("gtlmodular$foa_mode", Tag.TAG_BYTE)) {
                this.foaMode = tag.getBoolean("gtlmodular$foa_mode");
            }
            if (tag.contains("gtlmodular$foa_multiplier", Tag.TAG_INT)) {
                this.foaMultiplier = Math.max(MIN_FOA_MULTIPLIER,
                        Math.min(MAX_FOA_MULTIPLIER, tag.getInt("gtlmodular$foa_multiplier")));
            }
            if (tag.contains("gtlmodular$output_mode", Tag.TAG_BYTE)) {
                this.isOutputMode = tag.getBoolean("gtlmodular$output_mode");
            }
            rewrittenPatterns = null;
            rewrittenToOriginal.clear();
            rewriteCacheBuilt = false;
            markPatternTableStale("读档设置落盘(片E)");
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成：视图改写设置读档失败（已忽略，按默认值）pos={} 原因={}",
                    getPos(), failure.toString());
        }
        // ★ 2026-09-21（队长）：名字按模式 —— 读档/放置后也补一次，省得等一次模式切换才起名。
        //   判据与 tick 主落点**同一套**（{@link #syncCustomNameToMode(int)}：查「本总成 + 模式」的名字表，
        //   玩家改过名则永久让位 ✗）。读档期控制器还没挂上 ⇒ 方法内部解析不到模式、什么都不写；
        //   真正的起名由读档后第一次服务端 tick 完成 ✓。
        //   ★ 2026-10-04b：这一行**不再**自带判空/兜底串守卫（那种守卫会让"写一次之后再也不进"✗），
        //   但读档期机器还没挂到 level / 控制器上（{@code getControllers()} 为空）⇒ 方法内部解析不到
        //   "当前机器模式" ⇒ 自动**保持原名不动** ✗；真正的起名由读档后第一次服务端 tick 完成
        //   （本类 autoSwitchTickInner 的主落点）。保留这一行只为"自愈"语义兜底：
        //   控制器已经挂上时（例如热重载）能当场补名 ✓。
        syncCustomNameToMode(activeRecipeTypeIndex);
    }

    // ================= UI =================

    @Override
    public Widget createUIWidget() {
        int width = paginationUIManager.getUiWidth();
        int height = paginationUIManager.getUiHeight();
        WidgetGroup group = new WidgetGroup(0, 0, width, height);
        group.addWidget(new LabelWidget(8, 2,
                () -> Component.translatable(isOnline()
                        ? "gtceu.gui.me_network.online"
                        : "gtceu.gui.me_network.offline").getString()));
        group.addWidget(new AETextInputButtonWidget(width - 78, 2, 70, 10)
                .setText(getCustomName())
                .setOnConfirm(this::onPlayerConfirmedCustomName)
                .setButtonTooltips(Component.translatable("gui.gtceu.rename.desc")));
        // 中键点样板槽 → 打开该槽的专属催化剂槽（下面那层透明控件是主要入口，这里是兜底）
        group.addWidget(paginationUIManager.createPaginationUI(slot -> {
            setCatalystViewSlot(slot + 1);
            setCatalystPageOpen(true);
        }));
        // 透明右键/中键层：必须加在最后（LDLib 从子控件列表末尾往前派点击，末尾 = 最上层）
        group.addWidget(new SlotRightClickOverlay(this, group, width, height));
        // ★ 0.3.25（加固之一）：界面重建之后再确认一次面板下移（幂等；还没拿到面板时是 no-op）。
        //   为什么放在这里：createUIWidget() 是"主页面被 GTCEu 拉起来"的那一刻，
        //   而 GTCEu 自己也可能在 setupFancyUI/initWidget 里给面板 setSelfPosition
        //   （FancyMachineUIWidget 的常量池里同时有 Position.<init>(II) 与 setSelfPosition(Position)，
        //   见 gtl-dev/inspect/gtceu/.../FancyMachineUIWidget.class 常量池第 6-7 行）
        //   ⇒ 多一个"晚一点"的落点，就多一次纠偏机会。
        return group;
    }

    // ================= 配置器面板：整体下移（抵掉片 C 多加的那个 tab） =================

    /**
     * ★ 0.3.25：把<b>本机</b>的配置器面板（{@code ConfiguratorPanel}）整体<b>向下</b>移这么多。
     *
     * <p><b>单位 = LDLib 的逻辑像素</b>（= GUI 坐标，<b>不是</b>物理像素）。队长这台机
     * {@code options.txt: guiScale=3, fullscreen=true}、显示器 1920×1080
     * （与本模组既有的实测口径一致：见 {@code GTLModularConfigScreen} 第 180-182 行的注释
     * 「guiScale=3、fullscreen=true ⇒ 界面只有 640×360」）⇒ 26 逻辑像素在屏幕上 = <b>78 物理像素</b>。
     *
     * <h4>为什么正好是 26（从 GTCEu 自己的公式推出来，不是拍脑袋）</h4>
     * <ol>
     *   <li>一个 tab 的占位 = {@code getTabSize() + 2}；而
     *       {@code ConfiguratorPanel.getTabSize()} 的字节码就是 {@code return getSize().width}，
     *       面板宽度在构造器里恒为 {@code 24}
     *       （{@code gtl-dev/api-dump/configurator_panel.javap.txt:11-30} 构造器
     *       {@code WidgetGroup.<init>(x, y, bipush 24, iconst_0)}、{@code :44-49} getTabSize）</li>
     *   <li>面板自身的净高 = {@code tabs * (getTabSize() + 2) - 2}
     *       （{@code configurator_panel.javap.txt:103-122}，{@code Math.max(0, ...)}）；</li>
     *   <li>tab 的纵向步进也是 {@code i * (getTabSize() + 2)}
     *       （{@code configurator_panel.javap.txt:145-155} expandTab / {@code :166-187} collapseTab
     *       逐条 {@code collapseTo(0, i * (getTabSize() + 2))}）；</li>
     * </ol>
     * ⇒ 24 + 2 = <b>26</b>。片 C 给本机多加的「归档」页正好<b>一个</b> tab ⇒ 面板净高 +26
     * （{@code ConfiguratorPanel.attachConfigurators} 里就是 {@code Math.max(0, tabs*(tabSize+2)-2)}）。
     *
     * <p><b>⚠ 0.3.25 订正：下面这条旧推断已被自己推翻 ✗</b>——原注释写"gtlcore 按 tab 数量重排<b>面板</b>位置"，
     * 用 gtlcore 的**类文件常量池**复核后不成立：{@code ConfiguratorPanelMixin} 是
     * {@code @Inject(method="attachConfigurators", at=TAIL)} → {@code MEStorageConfiguratorTabLayout.arrange(panel)}，
     * 外加 {@code @Redirect} 掉 {@code collapseTab}/{@code expandTab} 里的
     * {@code ConfiguratorPanel$Tab.collapseTo(II)V} → {@code positionFor(panel, i)}；
     * 而 {@code MEStorageConfiguratorTabLayout} 的局部变量就是 {@code tab / tabIndex / tabs / expandedTab}
     * （全是 {@code ConfiguratorPanel$Tab}），逐个 {@code setSelfPosition(Position)}。
     * ⇒ **gtlcore 只摆 tab（含展开的那个），从不碰面板自身 Y**。
     * 所以"展开页顶边 = 面板自身 Y"这个前提本身还没被证实，本常量也就没能生效（见下面的实测状态）。
     * 这一段留在这里是为了让人一眼看到"我们曾经按哪个假设改过、后来怎么推翻的"。
     *
     * <p><b>为什么下移 26 不会把最下面的 tab 挤出屏幕下沿</b>：Y 方向的越界 GTCEu 自己会夹
     * （{@code configurator_tab.javap.txt:130-181}：{@code getParentPosition().y + size.height > screenHeight}
     * ⇒ {@code dragOffsetY -= 超出量}，把展开页的底边<b>顶回屏幕内</b>）；唯一没有夹的是<b>上</b>越界。
     * 也就是说：往下移只可能触发「自动上夹」这条已有的保护路径，不可能造出新的越界。
     *
     * <p><b>★ 实测状态（0.3.25 队长实机）</b>：本改动**被证明无效** ✗ —— 子页照旧顶出。
     * 而且已用字节码排除"被 gtlcore 覆盖"这条解释（gtlcore 那 4 个 tab 排布 mixin 只摆 **tab**、
     * 从不碰面板自身 Y，见 {@code _census/_gtlcompat/core_plain/.../ConfiguratorPanelMixin.class} 与
     * {@code MEStorageConfiguratorTabLayout.class} 的常量池）⇒ 结论是**"面板 Y"可能根本不是展开页 Y 的杠杆**。
     * ⇒ 保留本改动只作**方案 C 的兜底**（无害、可 0 关闭），真正的根治是**方案 B（给 GTCEu 的
     * {@code ConfiguratorPanel$Tab.expand()} 补"上越界夹取"，一个客户端 mixin，全模组生效，含原版机器）**。
     *
     * <p><b>★ 下移量的算式（不是拍脑袋的 26）</b>：
     * <ol>
     *   <li>展开视图的高度 = {@code content.height + button.height + border}
     *       （{@code configurator_tab.javap.txt:153-201} 的 {@code view.setSize(...)}）：
     *       {@code button} = {@code new ButtonWidget(0,0,tabSize,tabSize)} ⇒ 24、{@code border} = 4
     *       ⇒ 归档页 = <b>134 + 24 + 4 = 162</b>；</li>
     *   <li>它必须整块落在屏幕里 ⇒ {@code 4 ≤ 锚点屏幕Y} 且 {@code 锚点屏幕Y + 162 ≤ 360 − 2}
     *       ⇒ <b>锚点屏幕Y ∈ [4, 196]</b>（逻辑屏 640×360 = 1920×1080 ÷ guiScale 3 ✓ 队长机实测口径）；</li>
     *   <li>需要的下移量 = {@code max(0, 4 − 锚点屏幕Y)}；</li>
     *   <li>现在能定量的只有"片 C 那一个 tab"：面板净高 = {@code tabs*(getTabSize()+2) − 2}
     *       （GTCEu 自己的公式，{@code configurator_panel.javap.txt:103-122}）⇒ 4 tab = 102、5 tab = 128
     *       ⇒ 加「归档」页把锚点抬高了 <b>{@code getTabSize()+2 = 24+2 = 26}</b>
     *       ⇒ 把这 26 还回去 = 本常量 <b>26</b>（下锚模型下的第一步；够不够由日志判定）。</li>
     * </ol>
     * <b>日志到手后的重算规则</b>：把诊断日志里的 <i>绝对Y</i> 换成屏幕坐标
     * （{@code 锚点屏幕Y = (360 − 整机高)/2 + 绝对Y}，整机高可由"页高 + 固定 chrome"估、或由
     * 改一次 {@link #UI_HEIGHT_EXTRA} 前后的绝对Y 差反解），再把本常量设成 {@code max(0, 4 − 锚点屏幕Y)}。
     *
     * <p><b>可调序列（过渡用）</b>：26（一个 tab）→ 39（1.5 个）→ 52（两个）；**0 = 关闭**。
     *
     * <p><b>一键回退</b>：把本常量改成 {@code 0} 即可（{@link #shiftConfiguratorPanelDown(ConfiguratorPanel)}
     * 会在第一行早退，零开销）；彻底删干净就再删掉 {@link #attachConfigurators(ConfiguratorPanel)}
     * 末尾那一行 {@code shiftConfiguratorPanelDown(panel);}。
     *
     * <p><b>与方案 A 的关系（可叠加、不打架）</b>：A 是把机器界面加高（把锚点压低），C 是把面板自己下移，
     * 两者都只是"让展开页更靠下"。若 A 生效 ⇒ 建议把本常量改成 0（少一处魔数）；
     * 若 A 也不够 ⇒ 两者可同时留着，按 26/39/52 逐档试。
     */
    private static final int PANEL_DOWN_SHIFT = 0; // 0.3.27 关闭：0.3.26 的"面板下移 26"把配置器面板推出屏幕、所有子页一起消失 ✗（队长实机）

    /**
     * 每个面板的<b>原始</b>自身 Y（{@code selfPosition.y}）+ "被外人改回去"的计数。
     *
     * <p>用途 = 幂等 + 诊断：{@link #shiftConfiguratorPanelDown(ConfiguratorPanel)} 每次都把面板
     * <b>设回</b> {@code 基准 + PANEL_DOWN_SHIFT}，而不是"在原值上再加一次"
     * ⇒ 界面反复打开 / 反复 setup 也<b>绝不累加</b>。
     * 键用弱引用（{@link WeakHashMap}）：界面关掉后条目自动消失，不留内存。
     */
    private static final Map<ConfiguratorPanel, int[]> PANEL_SHIFT_STATE = new WeakHashMap<>();

    /**
     * 上一次 {@code attachConfigurators} 拿到的那个面板（屏幕上显示的就是它 ——
     * {@code FancyMachineUIWidget.configuratorPanel} 是 {@code protected final}，
     * 只在构造器 new 一次、永不替换：{@code _census/_gtceu_api_dump4.txt:132}）。
     *
     * <p><b>普通字段</b>：不带任何同步注解 ⇒ 不进 NBT、不进网络包、不参与 {@code ManagedFieldHolder}，
     * 纯客户端界面的临时引用（界面关掉后它指向的那个面板不再被画，本字段只是留着不碍事）。
     */
    private ConfiguratorPanel lastConfiguratorPanel;

    /**
     * 把<b>本机这一个</b>配置器面板向下移 {@link #PANEL_DOWN_SHIFT} 像素
     * （幂等 + "值不对才写" + 异常绝不外泄 + 一条可读诊断）。
     *
     * <p>只动 Y、<b>不动 X</b>（面板恒为 {@code x = -26}，见 GTCEu
     * {@code FancyMachineUIWidget} 里的 {@code new ConfiguratorPanel(-26, ...)}）——
     * 左侧图标列与主界面一格都不挪。
     *
     * <p>APIs 逐个核过签名（{@code gtl-dev/api-dump/ldd_slotwidget.javap.txt:135-149} 是 LDLib
     * {@code Widget} 的成员表，{@code ConfiguratorPanel} 继承它）：
     * {@code public final int getSelfPositionY()}、{@code public final void setSelfPositionY(int)}、
     * {@code public final int getPositionY()}（绝对 Y，缓存值）、
     * {@code public ModularUI getGui()}（取缩放后的屏幕高，与 GTCEu 自己在 expand() 里用的是同一个量）。
     *
     * <p>移动父面板会把偏移传给子控件：{@code WidgetGroup.onPositionUpdate()} 会把
     * {@code getPosition()} 逐个 {@code setParentPosition(...)} 给全部子控件
     * （{@code ldd_widgetgroup.javap.txt:327-350}）⇒ 5 个 tab 连同展开的子页一起下移。
     *
     * <p><b>诊断（队长可读，节流：每面板一次）</b>：打出
     * {@code 原 Y → 目标 Y（+shift）设后自身Y 绝对Y 屏幕高 本机页高}。读法：
     * <ul>
     *   <li><b>自身Y 变了、绝对Y 没变</b> ⇒ LDLib 的绝对坐标缓存没重算（本改动必然无效）；</li>
     *   <li>之后出现"被改回去了" ⇒ 真有别的代码在写面板位置；</li>
     *   <li><b>两者都 = 基准+shift 但画面没变</b> ⇒ 实锤"面板自身 Y 不是展开页的杠杆"；
     *       这两条正是 0.3.25 的实际情形。</li>
     * </ul>
     * 另一半是"给方案 A 标定"用的：把 <b>本机页高</b> 与 <b>绝对Y</b> 一起记下来，
     * 改一次 {@code UI_HEIGHT_EXTRA} 再跑一次，就能直接算出
     * {@code d(面板绝对Y)/d(界面高)}（+0.5 = 下锚（加高有用）/ 0 = 常量（加高没用）/−0.5 = 只有居中（加高反而更糟））
     * ——这样"加高到底有没有用"是量出来的，不是猜的。
     */
    private void shiftConfiguratorPanelDown(ConfiguratorPanel panel) {
        if (panel == null || PANEL_DOWN_SHIFT == 0) {
            return; // PANEL_DOWN_SHIFT = 0 即"一键关闭本改动"（早退，零开销）
        }
        try {
            synchronized (PANEL_SHIFT_STATE) {
                int[] state = PANEL_SHIFT_STATE.get(panel);
                boolean first = (state == null);
                if (first) {
                    // 第一次拿到这个面板：把 GTCEu 给它的原始 Y 记下来当基准（只记一次）
                    state = new int[] { panel.getSelfPositionY(), 0 };
                    PANEL_SHIFT_STATE.put(panel, state);
                }
                int target = state[0] + PANEL_DOWN_SHIFT;
                if (panel.getSelfPositionY() == target) {
                    return; // ★ 值已经对了：不写 ⇒ 不触发 onPositionUpdate（不给子控件做无谓的 setParentPosition）
                }
                panel.setSelfPositionY(target);
                if (first) {
                    GTLModular.LOGGER.info("[gtlmodular] 配置器面板下移: 原 Y={} → {}（+{}）设后自身Y={} 绝对Y={}"
                                    + " 屏幕高={} 本机页高={}",
                            state[0], target, PANEL_DOWN_SHIFT, panel.getSelfPositionY(), panel.getPositionY(),
                            screenHeightOf(panel), pageHeight());
                } else if (state[1] < 3) {
                    state[1]++;
                    GTLModular.LOGGER.info("[gtlmodular] 配置器面板下移被改回去了，已重设: 基准={} 目标={} 现值={}（第 {} 次）",
                            state[0], target, panel.getSelfPositionY(), state[1]);
                }
            }
        } catch (Throwable failure) {
            // 拿不到 API / 面板被 GTCEu 换了实现 —— 一律不影响界面，只留一条 debug
            GTLModular.LOGGER.debug("[gtlmodular] 猪咪样板总成：配置器面板下移失败（已忽略）面板={} 原因={}",
                    panel, failure.toString());
        }
    }

    /** 面板所在界面的"缩放后屏幕高"（= GTCEu 自己在 {@code expand()} 里用的同一个量）；取不到给 -1。 */
    private static int screenHeightOf(ConfiguratorPanel panel) {
        try {
            return panel.getGui() == null ? -1 : panel.getGui().getScreenHeight();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /** 本机主界面（page container）当前的高度 —— 只用于诊断日志。 */
    private int pageHeight() {
        try {
            return paginationUIManager == null ? -1 : paginationUIManager.getUiHeight();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel panel) {
        super.attachConfigurators(panel);
        List<IFancyConfigurator> pages = new ArrayList<>();
        // ★ 0.3.46（队长要求）：把「配方模式（= 队长口中的机器模式）」页与「样板倍率」页**合并成一页**
        //   —— 见 ModeScaleConfigurator（它同时把 0.3.36 就并进倍率页的神锻那四个控件重新排了一遍）。
        //   对界面的实际好处（沿用 0.3.33/0.3.36 已实测的规律，见本方法末尾那段页签数—面板高—页眉记录）：
        //     面板高 = 26 × 页签数 ⇒ 少一个页签 = 面板矮 26px ⇒ 面板顶回落 26px
        //     ⇒ 展开页的页眉（画在"面板顶"之上那 24px）更不容易被屏幕上沿切掉 ✓
        //   ⇒ 多配方类型时：我方页签 4 → 3（模式页 + 倍率页 → 合并页）；单配方类型时 3 → 3。
        //   ⚠ 这里**不能再**用 "可识别配方类型 > 1" 决定挂不挂这一页：合并页现在同时承载
        //     倍率 / 除率 / 重新应用 / 神锻开关与神锻倍率 —— 这些与配方类型数量无关，
        //     合并前就挂在"恒定存在"的倍率页上 ⇒ 合并页**恒定挂载**，
        //     "只有多模式才有意义"的那几个控件由 ModeScaleConfigurator 自己按条件 setVisible ✓
        pages.add(new ModeScaleConfigurator(this));
        CatalystAccessConfigurator catalystPage = new CatalystAccessConfigurator(this);
        this.activeCatalystPage = catalystPage;
        pages.add(catalystPage);
        panel.attachConfigurators(pages.toArray(new IFancyConfigurator[0]));
        int before = panel.getTabs().size() - pages.size();
        List<ConfiguratorPanel.Tab> tabs = panel.getTabs();
        int catalystTabIndex = before + pages.size() - 1;
        catalystPage.bindPanel(panel, tabs.size() > catalystTabIndex ? tabs.get(catalystTabIndex) : null);
        // ★★ 片 C：「归档（复制 / 剪切 / 粘贴）」页 ★★
        //   【页序】**追加在最后一页之后**（模式页 / 倍率页 / 神锻页 / 催化剂页之后）。
        //   为什么必须"追加在最后、并且在催化剂页**绑好 tab 之后**才挂"：
        //   上面那批页是一次性 `panel.attachConfigurators(pages)` 挂完的，催化剂页的 tab 下标
        //   按 `before + pages.size() - 1` 算出来再 `bindPanel`（现役机原本就是这套算法）；
        //   归档页在这句**之后**才挂 ⇒ 既不改任何已有页的下标、也不会把催化剂页绑到别的 tab 上。
        //   （惰性机那条线因为"催化剂页 tab 下标"踩过坑，它现在的写法是"挂之前抓一次
        //     panel.getTabs().size()"，见 Lazy…:5062-5068；这里沿用同一条纪律：**先抓再挂**。）
        try {
            int archiveTabBefore = panel.getTabs().size();   // 挂之前抓一次：新页的下标必然 = 这个数
            ArchiveConfigurator archivePage = new ArchiveConfigurator(this);
            panel.attachConfigurators(archivePage);
            List<ConfiguratorPanel.Tab> afterTabs = panel.getTabs();
            // ★ 0.3.28：与催化剂页（上面 3463 那行）**同一套绑定** —— 把本页自己的 tab 交给它，
            //   它才能在"真被展开"时把根控件从 44 高长到 134 高。这是它不再顶出屏幕上沿的关键。
            archivePage.bindPanel(panel, afterTabs.size() > archiveTabBefore ? afterTabs.get(archiveTabBefore) : null);
            GTLModular.LOGGER.debug("[gtlmodular] 猪咪样板总成：归档页已挂载 pos={} 下标={}（挂之前 {} 个 tab）",
                    getPos(), afterTabs.size() > archiveTabBefore ? archiveTabBefore : -1, archiveTabBefore);
        } catch (Throwable failure) {
            // 挂页失败绝不影响机器本身（顶多是玩家看不到这一页）
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成：归档页挂载失败 pos={} 原因={}",
                    getPos(), failure.toString());
        }
        // ★ 0.3.25：所有页都挂完之后，把本机这一个面板整体下移 PANEL_DOWN_SHIFT 像素（第一落点）。
        //   为什么放在"全部 attach 完"之后：GTCEu 在 FancyMachineUIWidget 构造器里就把面板 new 出来了
        //   （`new ConfiguratorPanel(-26, ...)`），而 attachConfigurators 是之后由 setupFancyUI(...)
        //   回调进来的（gtlcore 的 BatchConfiguratorMixin 就注入在 setupFancyUI 上、方法体里调
        //   IFancyUIProvider.attachConfigurators(ConfiguratorPanel)，
        //   见 _census/_probe_nacmat_evidence/strings_gtlcore-1.2.3.2-fix1.txt:46549-46555）
        //   ⇒ 我们此刻设的值在 GTCEu 的"面板创建"之后 ✓。
        //   但 **GTCEu 会不会在更晚（setupFancyUI/initWidget 内部）再给面板 setSelfPosition，本机无法证明**
        //   （FancyMachineUIWidget 的常量池里同时有 Position.<init>(II) 与 setSelfPosition(Position)，
        //   而它整个构造器/方法体的字节码本机没有留档 ✗）⇒ 所以本类另有两处幂等兜底：
        //   createUIWidget() 末尾 + clientTick()（见 shiftConfiguratorPanelDown 的注释）。
        //   ConfiguratorPanel 自己则绝不会动位置：整个类里只有给**子控件**设位，
        //   连 clear()（面板会被反复 setup）也只清 widgets/tabs/expanded（configurator_panel.javap.txt:32-42）。
        this.lastConfiguratorPanel = panel;

        // ★ 0.3.33：把本机面板的页签改成「每列 4 个」的多列排布（gtlcore 自己的公开工具，
        //   它家 ME 存储页就是这么排的：MEStorageConfiguratorTabLayout.setEnabled/arrange）。
        //
        //   为什么必须这么做（全部实机实测）：
        //     面板高 = 26 × 页签数（12 个 ⇒ 面板顶 −1；13 个 ⇒ −27，两次实测面板底边都固定 = 311）；
        //     而展开视图的**页眉（我们提供的标题+图标，高 24px）画在"面板顶"之上**：
        //       12 页签 ⇒ 页眉落在 −1～23 ⇒ 可见 ✓
        //       13 页签 ⇒ 页眉落在 −27～−3 ⇒ **整条出屏** ✗（＝队长看到的"页眉消失"）
        //     把页签排成多列后高度 ≈ 26×4 ⇒ 面板顶回落到屏幕内 ⇒ **页眉自己回来** ✓。
        //
        //   代价与安全：
        //     · 不删任何页、不改任何页面内容/尺寸、不碰 GTCEu 与 gtlcore 的代码（只是采用人家已提供的排布）；
        //     · gtlcore 的 MEStorageConfiguratorTabBoundsMixin 已把"溢出到右侧列"的页签鼠标判定补上，多列照样能点；
        //     · 这是客户端专用类 ⇒ 服务端跳过；gtlcore 若改内部类名，最坏是退回单列外观，机器本身不受影响。
        try {
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
                org.gtlcore.gtlcore.client.gui.MEStorageConfiguratorTabLayout.setEnabled(panel, true);
                org.gtlcore.gtlcore.client.gui.MEStorageConfiguratorTabLayout.arrange(panel);
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 猪咪样板总成：页签多列排布未生效 pos={} 原因={}",
                    getPos(), failure.toString());
        }
    }

    /**
     * ★ 0.3.25（加固之二）：客户端每 tick 幂等地"确认"一次面板下移还在不在。
     *
     * <p>为什么要它：队长实测 0.3.25 那版"没变化"，而我们现在既排除了"对象不对"
     * （面板是 {@code final} 字段、我们拿到的就是屏幕上那一个），也排除了"gtlcore 覆盖"
     * （gtlcore 那 4 个 mixin 只摆 tab），**唯一还没排除的是 GTCEu 自己在 setupFancyUI/initWidget
     * 里晚于我们给面板设位** ⇒ 用一个"每 tick 兜一次"的落点把它兜住
     * （幂等：只用基准重设，绝不累加；值对了就只做一次比较、不写）。
     *
     * <p>成本：界面没打开过（{@code lastConfiguratorPanel == null}）⇒ 第一行就 return，零开销；
     * 界面开着时 = 一次字段读 + 一次 map 查询 + 一次 int 比较，正确时<b>不写、不打日志</b>。
     */
    // ★ 0.3.27 回退：0.3.26 在这里覆写 clientTick()、每 tick 往配置器面板写一次位置，
    //   实机结果是"所有子UI都弄没了" ✗ ⇒ 整个覆写删除，面板位置交回 GTCEu 自己管（我们不再碰）。

    /**
     * ★ 0.3.29 <b>纯诊断</b>（不改任何行为、不设任何位置）：把展开页的几何量打进日志，
     * 让"顶边越界多少像素"从猜测变成实测数字。
     *
     * <p>为什么要它：到 0.3.28 为止已经试过两条"从我们这侧改位置/改高度"的路
     * （面板下移 26 ✗、44 高创建再长高 ✗）都无效 ⇒ 必须先拿到
     * <b>屏幕高 / 页绝对Y / 页selfY / 页高 / 面板Y / tab 数 / 本页是否展开</b> 这七个数，
     * 才能判定 GTCEu 到底按什么给展开页定位、以及需要让页面矮到多少才进屏幕。
     *
     * <p>只在数值变化时打印一行（同一台机器同一页只会在开界面/切页/改 tab 数时出现），不会刷屏。
     */
    private static volatile String gtlmodular$lastPageGeometry = "";

    static void logPageGeometry(String tag, Widget page, ConfiguratorPanel panel, ConfiguratorPanel.Tab tab) {
        try {
            int screenH = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
            int absY = page.getPositionY();
            int selfY = page.getSelfPositionY();
            int sizeH = page.getSizeHeight();
            int panelY = panel == null ? -9999 : panel.getPositionY();
            // ★ 0.3.31 新增：展开页**父级层**的 Y —— 这才是 expand() 里"底边夹取"用的那个量
            //   （dragOffsetY 由 view父级Y + 页高 与 屏高 比较得出），是定位公式里唯一还没实测的输入。
            Widget parent = page.getParent();
            int parentY = parent == null ? -9999 : parent.getPositionY();
            int tabs = panel == null ? -1 : panel.getTabs().size();
            boolean mine = panel != null && tab != null && panel.getExpanded() == tab;
            String line = String.format(
                    "%s 屏幕高=%d 页绝对Y=%d 页selfY=%d 页高=%d 上越界=%d 父层Y=%d 面板Y=%d tab数=%d 本页展开=%s",
                    tag, screenH, absY, selfY, sizeH, Math.max(0, -absY), parentY, panelY, tabs, mine);
            if (!line.equals(gtlmodular$lastPageGeometry)) {
                gtlmodular$lastPageGeometry = line;
                GTLModular.LOGGER.info("[gtlmodular] 展开页几何 {}", line);
            }
        } catch (Throwable ignored) {
            // 诊断绝不许影响任何东西
        }
    }

    // ================= 机器模式 + 样板倍率（★ 0.3.46 合并并重排）=================

    /**
     * ★ 0.3.46（队长要求）<b>合并页</b>：把原来的两个页签并成一页，并重新排版。
     *
     * <h3>并掉了什么（原有控件一个不少）</h3>
     * <ul>
     *   <li>原 {@code RecipeModeConfigurator}（页名「配方模式」= 队长口中「机器模式」页）：
     *       ◀ / 模式名 x/y / ▶、样板槽位输入框、指定 / 清除、该槽手动指定状态行；</li>
     *   <li>原 {@code ScaleConfigurator}（页名「样板倍率」）：乘法倍率 / 除法倍率 / 重新应用，
     *       以及 0.3.36 起就并进该页的神锻开关与神锻倍率（原 {@code FoaConfigurator} 的四个控件）。</li>
     * </ul>
     * 每件控件的绑定关系、上下界、悬停文案与"服务端才动作"的守卫都与合并前逐字一致
     * （见 {@link #createConfigurator()} 里每行末尾注释；<b>没有复制任何状态</b>：全部走机器上原有的字段/方法）。
     *
     * <h3>版式设计（像素级；只用本文件已有先例的控件与 API）</h3>
     * <p><b>★ 0.3.47 统一垂直节奏</b>：所有 y 由 {@code PAD_Y=3 / ROW_GAP=3 / SECTION_GAP=6} 三档推导
     * （常量区有"为什么是这个值"的说明）⇒ 板内紧密、板间疏松，页眉不再贴着第一块板 ✗
     * <pre>
     *  ┌──────────────────────────────────────────────┐ 172 × 161
     *  │ [◀]  切割机 · 2/5                      [▶]   │ 页眉 y=4..20（HEADER_Y + HEADER_H）
     *  │                                              │ ← SECTION_GAP 6（0.3.46 只有 4 ⇒ 挤 ✗）
     *  │ ▍槽位指定                                     │ 底板一 y=26..76（164×50，色 0x33000000）
     *  │   槽位 [  12 ]   [指定] [清除]                │ 标题 29..39 ／ 控件行 42..60
     *  │   该槽当前：已指定 切割机                      │ 状态行 63..73（活文本）
     *  │                                              │ ← SECTION_GAP 6
     *  │ ▍样板倍率 视图缩放                             │ 底板二 y=82..155（164×73）
     *  │   乘法倍率      除法倍率                       │ 标题 85..95 ／ 说明行 98..108
     *  │   [ 1 ]        [ 1 ]      [重新应用]          │ 控件行 111..129
     *  │   [⏻] 神锻模式：关闭   倍率 [ 15 ]             │ 神锻行 132..152
     *  └──────────────────────────────────────────────┘ 底部留白 155..161
     * </pre>
     * <ul>
     *   <li><b>宽 172</b>：与 0.3.37 收窄后的模式页 / 归档页同宽；内容右边界 164（右留 8px）⇒ 展开后不越出主界面；</li>
     *   <li><b>高 161</b>：比 0.3.46 的 142 高 19px —— 这正是队长"把槽位指定往下挪一点"的代价
     *       （页眉↔板 +2、板内三行各 +3、板间 +2），比"两页相加"（182）仍矮 21px；</li>
     *   <li><b>分组与描边不用贴图</b>：底板 = {@link ColorRectTexture} 半透明黑（20%），左侧 2px 青色高亮条
     *       当"分组标识/描边"（{@code ColorRectTexture} 在本工程已有先例：NanochipModuleMachine:1992 当按钮底、
     *       本文件催化页 hover 层 0x40FFFFFF）✓ 图标沿用原两页同一个 {@code portable_debug_scanner} ✓ 零新资源；</li>
     *   <li><b>"当前值"怎么被强调</b>：模式名走 {@code §b§l}（青色加粗）、序号走 {@code §8·}（灰）、
     *       神锻状态开 = {@code §a}（绿）关 = {@code §8}（灰）⇒ 一眼看到"现在是什么"。
     *       颜色只靠 {@code §} 码，不引入本工程没有先例的 API（例如 {@code TextTexture.setColor} 全工程零使用 ✗）；</li>
     *   <li><b>尺寸恒定、永不 setSize</b>：创建即 {@code PAGE_WIDTH×PAGE_HEIGHT}（0.3.47 = 172×161，
     *       由节奏常量推导）（0.3.31 纪律 —— 任何尺寸变化都会触发
     *       {@code Tab.onChildSizeUpdate} 重启展开动画、把已到位的页面半路重置 ✗）；</li>
     *   <li><b>上下界与守卫照旧</b>：{@code IntInputWidget(…).setMin(MIN_SCALE).setMax(MAX_SCALE)} 等一行未改；
     *       「指定 / 清除」仍是 {@code if (!machine.isRemote())} 才动作；◀ / ▶ 仍走 {@link #cycle}（Shift 跳 5 个）✓</li>
     * </ul>
     *
     * <h3>单配方类型（合并前根本不挂模式页）怎么办</h3>
     * 合并页**恒定挂载**（它同时承载倍率 / 神锻 ⇒ 不能像以前那样按"配方类型 &gt; 1"决定挂不挂 ✗）。
     * 当可识别配方类型 ≤ 1 时：◀ / ▶、槽位标签与输入框、指定 / 清除、状态行一律 {@code setVisible(false)}
     * （= 精确回到"原来没有模式页"的观感），第 1 行显示既有的 {@code mode.none} 文案，倍率 / 神锻区不受影响。
     * 安全性：{@code cycleRecipeType}/{@code assignManualType} 本来就有越界与空表守卫（:638-647 / :825-830）⇒ 藏起来也不会误动作 ✓
     */
    static final class ModeScaleConfigurator implements IFancyConfigurator {

        // ============================================================
        //  版式常量（★ 0.3.47：改成"统一垂直节奏"）
        //  改坐标请只改下面的【节奏值】，其余行/板位置全部由它们推导 ⇒ 以后微调不会到处漏改 ✓
        // ============================================================

        // ---------- 节奏值（队长反馈"槽位指定那里还是有点挤" ⇒ 定死这几档）----------
        /**
         * 底板内上下内边距：标题行上边缘、最后一行下边缘与底板边缘的距离。
         * <p>取 3 的理由：GT 面板本身有 1px 描边视觉重量，留 2 会"贴住"、留 4 会让 172 宽的窄页显得散；
         * 3 在 0.3.46 的实机观感（底板 0x33000000 与深色面板对比度低）下最容易读成"一块板" ✓
         */
        private static final int PAD_Y = 3;
        /**
         * 页眉（第 1 行模式游标）与首个底板之间、两块底板之间的间距。
         * <p>取 6 的理由：0.3.46 是 4（页眉底 20 → 底板顶 24），队长反馈"挤" ⇒ 拉开到 6；
         * 再多（8）会让整页变高且 172 宽下显得空 ⇒ 6 是"看得出分组、又不浪费纵向"的折中 ✓
         */
        private static final int SECTION_GAP = 6;
        /**
         * 同一块底板内相邻两行之间的距离。
         * <p>取 3 的理由：底板内已有 PAD_Y=3 的边界留白，行距同为 3 ⇒ 整页只有"3 / 6"两档节奏，
         * 视觉上"板内紧密、板间疏松"，一眼能分出两块板 ✓（这也让"标题贴住控件"的毛病消失 ✗）
         */
        private static final int ROW_GAP = 3;
        /** 页眉行高（◀ / ▶ 按钮高）。 */
        private static final int HEADER_H = 16;
        /** 页眉行 y（顶部留白）。 */
        private static final int HEADER_Y = 4;
        /** 一行文字（LabelWidget）的高度。 */
        private static final int LABEL_H = 10;
        /** 一行控件（输入框 / 动作按钮）的高。 */
        private static final int ROW_H = 18;
        // ---------- 模式芯片行（0.3.49：用户反馈"游标 + 3/3 不够明显" ⇒ 改成芯片行）----------
        /** 相邻芯片之间的横向间隙。取 2：172 宽面板要放下最多 5~6 枚芯片，间隙再大就放不下 ✓ */
        private static final int CHIP_GAP = 2;
        /** 芯片最小宽度（放得下「切割机」三个汉字 + 左右各 2px 留白 ✓） */
        private static final int CHIP_MIN_W = 26;
        /** 芯片最大宽度（再宽就浪费横向空间；模式名最长的「合金冶炼炉」5 字约 45px ✓） */
        private static final int CHIP_MAX_W = 54;

        // ---------- 0.3.51 缺陷修复：槽位/神锻输入框的数字看不见 ----------
        /**
         * 输入框**可读宽度下限**的推导依据（取证：GTCEu {@code NumberInputWidget#buildUI} 的字节码，
         * 见 {@code gtl-dev/api-dump/number_input.javap.txt:166-186}）：
         * <pre>
         *   buttonW = Mth.clamp(width / 5, 15, 40);      // 两侧 −/+ 按钮固定按此宽绘制
         *   textW   = width - 2 * buttonW - 4;           // 中间文本框的**实际**宽度
         * </pre>
         * ⇒ 传进来的 width 不等于文本框宽度 ✗：width=46 ⇒ textW 只有 12、width=42 ⇒ 只有 8
         * ⇒ 文本框窄到放不下字（EditBox 还有内边距）⇒ 玩家看到"一片黑、没有数字"✗。
         * <p>工程内"数字看得见"的既有先例：{@code CatalystAccessConfigurator} 的
         * {@code new IntInputWidget(6, 18, 74, 20, ...)}（本文件 :4444）⇒ textW = 74-30-4 = 40 ✓；
         * 惰性机模式页 {@code new IntInputWidget(46, 26, 92, 16, ...)}（Lazy :6500）⇒ textW = 52 ✓。
         * <p>故本页两个输入框按 textW ≥ 28 布置（width ≥ 62）✓ —— 见下面 SLOT_INPUT_W / FOA_INPUT_W。
         */
        private static final int INPUT_MIN_READABLE_W = 62;
        /** 底板一：槽位输入框（x/宽）——原来 30/46（textW 仅 12 ✗）现在是 28/62 ⇒ textW 28 ✓。 */
        private static final int SLOT_INPUT_X = 28;
        private static final int SLOT_INPUT_W = INPUT_MIN_READABLE_W;
        /** 底板一：「指定」「清除」按钮（各自 2 个汉字，34 宽足够 ✓ 英文 Assign/Clear 也放得下 ✓）。 */
        private static final int SLOT_ASSIGN_X = SLOT_INPUT_X + SLOT_INPUT_W + 2;      // 92
        private static final int SLOT_CLEAR_X = SLOT_ASSIGN_X + 34 + 2;                 // 128
        private static final int SLOT_BTN_W = 34;
        /** 底板二神锻行：电源开关 x、短标签 x、状态文字 x、倍率输入框 x/宽。 */
        private static final int FOA_TOGGLE_X = 60;   // v2：三连回到第 1 列后，开关挪到三连右侧
        private static final int FOA_TOGGLE_W = 20;
        private static final int FOA_LABEL_X = 84;    // 「神锻」标签跟在开关后
        private static final int FOA_STATE_X = 112;   // ★ 0.3.58：与"重新应用"按钮同列 ✓ 让神锻行也按三列对齐 ✓
        /** 神锻倍率输入框：94 起、80 宽 ⇒ 94..174 超界 ✗，故改 82 起、80 宽 ⇒ 82..162 ✓ textW = 46 ✓。 */
        private static final int FOA_INPUT_X = 8;     // v2：输入框回到第 1 列（与乘法倍率同列 ✓）   // ★ 0.3.58：与"除法倍率"输入框同列（三列对齐 ✓）
        private static final int FOA_INPUT_W = 48;   // ★ 0.3.58：与"除法倍率"输入框同列同宽（60..108 ✓ textW=14 可读 ✓ 先例：乘法/除法 48 宽在实机可见 ✓）
        /** 神锻那一行的高（电源开关本体 20 高，比普通行高 2）。 */
        private static final int FOA_ROW_H = ROW_H;
        /** 行内文字相对该行顶部的垂直居中偏移（普通行）。 */
        private static final int ROW_TEXT_OFFSET = (ROW_H - LABEL_H) / 2;
        /** 行内文字相对该行顶部的垂直居中偏移（神锻行）。 */
        private static final int FOA_TEXT_OFFSET = (FOA_ROW_H - LABEL_H) / 2;
        /** 页眉文字的垂直居中偏移（相对 HEADER_H）。 */
        private static final int HEADER_TEXT_OFFSET = (HEADER_H - LABEL_H) / 2;
        /** 底板内内容的左内边距（相对底板左边界 BAND_X）。 */
        private static final int BAND_PAD_X = 4;

        // ---------- 由节奏推导尺寸（下面这些**不要手改**，改上面的节奏值 ✓）----------
        /** 页宽：与 0.3.37 收窄后的模式页 / 归档页一致。 */
        private static final int PAGE_WIDTH = 172;
        /** 分组底板：左边界 4、宽 164（右留 8px）。 */
        private static final int BAND_X = 4;
        private static final int BAND_W = PAGE_WIDTH - 8;
        /** 底板底色：20% 黑，只把控件"托"起来（GT 面板本身是深色，不能再压暗一层）。 */
        private static final int BAND_BG = 0x33000000;
        /** 左侧高亮条：青色（AE/GT 系深色面板上的高亮色），同时充当分组描边。 */
        private static final int BAND_ACCENT = 0xCC00D5FF;
        private static final int ACCENT_W = 2;
        /** 底板内文字/控件的左边界（= 底板左边 + 内边距）。 */
        private static final int BAND_TEXT_X = BAND_X + BAND_PAD_X;
        /** 底板标题的左边界（比内容再进 2px：标题要与左侧高亮条拉开一点）。 */
        private static final int BAND_TITLE_X = BAND_TEXT_X + 2;

        // ---- 底板一：槽位指定 ----
        /** 底板一 y = 页眉底 + 分组间距。 */
        private static final int BAND_MODE_Y = HEADER_Y + HEADER_H + SECTION_GAP;
        /** 底板一内部三行的 y（标题 / 控件行 / 状态行）。 */
        private static final int BAND_MODE_TITLE_Y = BAND_MODE_Y + PAD_Y;
        private static final int BAND_MODE_ROW_Y = BAND_MODE_TITLE_Y + LABEL_H + ROW_GAP;
        private static final int BAND_MODE_STATE_Y = BAND_MODE_ROW_Y + ROW_H + ROW_GAP;
        /** 底板一高 = PAD + 标题 + ROW_GAP + 控件行 + ROW_GAP + 状态行 + PAD。 */
        private static final int BAND_MODE_H = PAD_Y + LABEL_H + ROW_GAP + ROW_H + ROW_GAP + LABEL_H + PAD_Y;

        // ---- 底板二：样板倍率 + 神锻 ----
        /** 底板二 y = 底板一底 + 分组间距。 */
        private static final int BAND_SCALE_Y = BAND_MODE_Y + BAND_MODE_H + SECTION_GAP;
        /** 底板二内部四行的 y（标题 / 说明行 / 倍率控件行 / 神锻行）。 */
        private static final int BAND_SCALE_TITLE_Y = BAND_SCALE_Y + PAD_Y;
        private static final int BAND_SCALE_LABEL_Y = BAND_SCALE_TITLE_Y + LABEL_H + ROW_GAP;
        private static final int BAND_SCALE_ROW_Y = BAND_SCALE_LABEL_Y + LABEL_H + ROW_GAP;
        private static final int BAND_SCALE_FOA_Y = BAND_SCALE_ROW_Y + ROW_H + ROW_GAP;
        /** 底板二高 = PAD + 标题 + ROW_GAP + 说明行 + ROW_GAP + 控件行 + ROW_GAP + 神锻行 + PAD。 */
        private static final int BAND_SCALE_H = PAD_Y + LABEL_H + ROW_GAP + LABEL_H + ROW_GAP + ROW_H + ROW_GAP
                + FOA_ROW_H + PAD_Y;

        /**
         * 页高：**由节奏推导**（底板二底 + 一个分组间距当底部留白），不再是手写的 142 ✗。
         * <p>0.3.47 数值 = 26 + 50 + 6 + 73 + 6 = 161（比 0.3.46 的 142 高 19px：这是队长
         * "把槽位指定往下挪一点"的直接代价 —— 页眉与底板之间 +2、板内三行各 +3、板间 +2）。
         * <p>为什么不担心溢出：本页在**展开时**显示，底板基准高由 26×页签数 决定，
         * 161 仍低于本工程既有展开页（归档页 84 / 催化页由 PAGE_OPEN_HEIGHT 决定）与屏幕高度；
         * 若将来嫌高，只改 PAD_Y/ROW_GAP/SECTION_GAP 三档即可整体压缩 ✓
         */
        private static final int PAGE_HEIGHT = BAND_SCALE_Y + BAND_SCALE_H + SECTION_GAP;

        // ★ 0.3.61：记住芯片控件 ⇒ 点击后立刻重建其文字颜色（否则"当前项青色加粗"要重开界面才变 ✗）
        private final java.util.List<ButtonWidget> chipWidgets = new java.util.ArrayList<>();

        /** 点击切换后立刻刷新芯片文字：当前项 §b§l 青加粗，其余 §7 灰 ✓ */
        private void refreshChipLabels() {
            for (int i = 0; i < chipWidgets.size(); i++) {
                String lb = (i == machine.getModeCursorIndex() ? "§b§l" : "§7") + modeName(i);
                try {
                    chipWidgets.get(i).setButtonTexture(
                            new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(lb)));
                } catch (Throwable ignored) {
                    // 刷新失败不影响功能（最迟重开界面也会对 ✓）
                }
            }
        }

        private final MoltenPatternAssemblyPartMachine machine;

        ModeScaleConfigurator(MoltenPatternAssemblyPartMachine machine) {
            this.machine = machine;
        }

        @Override
        public Component getTitle() {
            // 合并页的页名（新键）：既是"机器模式"也是"样板倍率"，所以两个都提一下
            return Component.translatable("gtlmodular.pattern_assembly.mode_scale.title");
        }

        @Override
        public IGuiTexture getIcon() {
            // 原「配方模式」页与「样板倍率」页用的是同一个图标 ⇒ 合并后沿用，不新增贴图 ✓
            return new ResourceTexture("gtceu:textures/item/portable_debug_scanner.png");
        }

        @Override
        public List<Component> getTooltips() {
            // 悬停四行：① 当前"要指定下去"的模式（与合并前模式页的悬停口径逐字一致 ✓）
            //           ② 本页是合并页的说明（新键）③ 倍率口径 ④ 神锻口径（③④ 是既有键，语义不动）
            List<Component> tips = new ArrayList<>(4);
            List<GTRecipeType> types = machine.getAvailableRecipeTypes();
            if (types.isEmpty()) {
                tips.add(Component.translatable("gtlmodular.pattern_assembly.mode.none"));
            } else {
                int index = Math.max(0, Math.min(types.size() - 1, machine.getModeCursorIndex()));
                // ★ 0.3.2 起读的是**模式页游标**（玩家拨到、会「指定」下去的那个模式）
                tips.add(modeComponent(types.get(index)));
            }
            tips.add(Component.translatable("gtlmodular.pattern_assembly.mode_scale.tooltip.merge"));
            tips.add(Component.translatable("gtlmodular.pattern_assembly.mode_scale.tooltip.scale"));   // 2026-10-02 专用键：合并页文案与倍率页/惰性机解耦 ✓
            tips.add(Component.translatable("gtlmodular.pattern_assembly.foa.tooltip"));
            return tips;
        }

        @Override
        public Widget createConfigurator() {
            // ★ 0.3.46 合并版式、0.3.47 改"统一垂直节奏"（设计稿见类注释的 ASCII 图）：
            //   页眉(4..20) 模式游标 ｜ 底板一(26..76) 槽位指定 ｜ 底板二(82..155) 倍率 + 神锻
            //   纵向节奏：PAD_Y=3（板内边界）· ROW_GAP=3（板内行距）· SECTION_GAP=6（页眉↔板、板↔板）
            //   横向：内容 4..164（右留 8px）；底板左边界 4、宽 164，左侧 2px 青色高亮条
            //   尺寸恒定：创建即 PAGE_WIDTH×PAGE_HEIGHT，之后**永不 setSize**（0.3.31 纪律）✓
            boolean multi = machine.getAvailableRecipeTypes().size() > 1;
            WidgetGroup group = new WidgetGroup(0, 0, PAGE_WIDTH, PAGE_HEIGHT);

            // ================= 第 1 行：模式游标（◀ 模式名 x/y ▶）=================
            // 原「配方模式」页第一行，功能一字不差：
            //   ◀ / ▶ = cycle()（走 machine.cycleRecipeType，Shift 点击一次跳 5 个）；
            //   中间是活文本，读的仍是 machine.getModeCursorIndex()（= 会「指定」下去的那个模式）。
            // ★ 0.3.49：模式行改成「芯片行」(Segmented / Chip) —— 用户反馈"游标 + 3/3 不够明显" ✗
            //   视觉：每个可选模式一枚芯片；当前模式＝§b§l 青加粗 + 其下 2px 青色高亮条，其余＝§7 灰
            //         底板与 2px 青竖条沿用下面两块板同一套语言 ⇒ 零新贴图 ✓
            //   交互：点芯片 ⇒ selectMode(...)（内部仍调用 machine.cycleRecipeType ⇒ 守卫/同步/服务端逻辑一字未变 ✓）
            //   兜底：模式太多导致最小宽度也放不下时，退回「◀ 模式名 x/y ▶」游标行 ✓
            List<GTRecipeType> modeTypes = machine.getAvailableRecipeTypes();
            group.addWidget(new ImageWidget(BAND_X, HEADER_Y, BAND_W, HEADER_H,
                    new ColorRectTexture(BAND_BG)));
            group.addWidget(new ImageWidget(BAND_X, HEADER_Y, ACCENT_W, HEADER_H,
                    new ColorRectTexture(BAND_ACCENT)));
            if (modeTypes.size() <= 1) {
                // 单配方类型：保持既有语义 ⇒ 只显示 mode.none 文案，没有芯片可选 ✓
                group.addWidget(new LabelWidget(BAND_X + 6, HEADER_Y + HEADER_TEXT_OFFSET, this::modeNameLabel));
            } else {
                int chipCount = modeTypes.size();
                int usableW = BAND_W - 4 - (chipCount - 1) * CHIP_GAP;
                int chipW = Math.max(CHIP_MIN_W, Math.min(CHIP_MAX_W, usableW / chipCount));
                if (chipW * chipCount + CHIP_GAP * (chipCount - 1) > BAND_W - 4) {
                    // ---- 兜底：放不下 ⇒ 游标行（左 ◀ / 右 ▶ / 中间模式名 / 右端序号）----
                    ButtonWidget prevBtn = new ButtonWidget(BAND_X + 2, HEADER_Y + 1, 20, HEADER_H - 2,
                            new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture("\u25c0")),
                            data -> cycle(data, false));
                    ButtonWidget nextBtn = new ButtonWidget(BAND_X + BAND_W - 22, HEADER_Y + 1, 20, HEADER_H - 2,
                            new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture("\u25b6")),
                            data -> cycle(data, true));
                    group.addWidget(new LabelWidget(BAND_X + 26, HEADER_Y + HEADER_TEXT_OFFSET, this::modeNameLabel));
                    group.addWidget(new LabelWidget(BAND_X + BAND_W - 66, HEADER_Y + HEADER_TEXT_OFFSET, this::modeIndexLabel));
                    group.addWidget(prevBtn);
                    group.addWidget(nextBtn);
                } else {
                    // ---- 主路径：芯片行（当前模式高亮 + 高亮条）----
                    // ★ 0.3.51：每枚芯片补上悬停提示（模式全名 + "点击切换"口径 ✓ 设计规范"悬停必写"✓）。
                    //   点击回调仍在下面 selectMode（0.3.51 已改成"分侧执行 + 服务端权威"✓）。
                    int active = Math.max(0, Math.min(chipCount - 1, machine.getModeCursorIndex()));
                    for (int i = 0; i < chipCount; i++) {
                        int chipX = BAND_X + 2 + i * (chipW + CHIP_GAP);
                        boolean isActive = i == active;
                        String label = (isActive ? "§b§l" : "§7") + modeName(i);
                        final int target = i;
                        ButtonWidget chip = new ButtonWidget(chipX, HEADER_Y + 1, chipW, HEADER_H - 2,
                                new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(label)),
                                data -> selectMode(data, target));
                        chip.setHoverTooltips(Component.translatable(
                                "gtlmodular.pattern_assembly.mode_scale.chip.tip", modeName(i)));
                        chipWidgets.add(chip);
                        group.addWidget(chip);
                        {
                            // ★ 2026-10-02：每枚芯片都挂一条青条，只用 setVisible 切换 ⇒ 点击后立刻跟手 ✓
                            //   旧写法只给"当前项"加一次 ✗ ⇒ 高亮永远不会移动、必须关掉界面重开才看到 ✗
                            // ★ 0.3.51：这条青条是在芯片**之后**加的，而 WidgetGroup.mouseClicked 是
                            //   **倒序**遍历、命中即返回（取证 ldd_widgetgroup.javap.txt:1640-1677）⇒
                            //   它会比芯片先被问到 ✗。虽然只有 2px 高、通常也不吞点击，但既然同一个
                            //   循环里有"当前项"这一枚芯片最常被点，就**显式 setActive(false)**：
                            //   倒序遍历里 `isActive()==false` 的子控件会被直接跳过（同文件 :1660-1661 ✓）
                            //   ⇒ 这 2px 永远不会挡住任何点击 ✓（这是 0.3.51 修"芯片点不动"的一道保险）。
                            group.addWidget(chipUnderline(chipX, chipW).setVisible(isActive));
                        }
                    }
                }
            }

            // ================= 底板一：槽位指定（原模式页第 2~4 行）=================
            // 底板 = 半透明黑 + 左侧 2px 青色条（分组 + 描边，不用贴图 ⇒ 零新资源 ✓）
            group.addWidget(new ImageWidget(BAND_X, BAND_MODE_Y, BAND_W, BAND_MODE_H,
                    new ColorRectTexture(BAND_BG)));
            group.addWidget(new ImageWidget(BAND_X, BAND_MODE_Y, ACCENT_W, BAND_MODE_H,
                    new ColorRectTexture(BAND_ACCENT)));
            LabelWidget bandModeHeader = new LabelWidget(BAND_TITLE_X, BAND_MODE_TITLE_Y,
                    Component.translatable("gtlmodular.pattern_assembly.mode_scale.section.mode").getString());
            group.addWidget(bandModeHeader);
            //  ↑ 这条分组标题在"单配方类型"时会被一起藏掉（见方法末尾）：
            //    没有模式可选时这一栏只剩一个空底板，标题写「槽位指定」会误导 ✗，
            //    真正的原因由第 1 行那句 mode.none 文案说明 ✓

            // 槽位号：短标签「槽位」（新键；完整说法「样板槽位」放进输入框的悬停，信息不丢 ✓）
            // 上下界与合并前逐字一致：1 .. machine.getCatalystSlotCount()（= 本机样板槽总数）
            // ★ 0.3.47：三行 y 全部改由节奏常量推导（BAND_MODE_TITLE_Y / ROW_Y / STATE_Y），
            //   文字再用 ROW_TEXT_OFFSET 垂直居中 ⇒ 不再出现"标签比输入框高半行"的错位 ✓
            LabelWidget slotLabel = new LabelWidget(BAND_TEXT_X, BAND_MODE_ROW_Y + ROW_TEXT_OFFSET,
                    Component.translatable("gtlmodular.pattern_assembly.mode_scale.slot.label").getString());
            // ★ 变量类型必须是 Widget：setHoverTooltips(...) 的返回类型是 Widget（LDLib 里定义在 Widget 上）
            //   ⇒ `IntInputWidget x = new IntInputWidget(...).setHoverTooltips(...)` 会编译不过 ✗
            //   （本工程既有先例 NanochipModuleMachine:1987-1990 是直接把它塞进 addWidget(Widget) ✓）
            // ★ 0.3.51 缺陷修复（②）：原来宽 46 ⇒ 按 NumberInputWidget#buildUI 的算法，中间文本框
            //   只有 46-2*15-4 = 12px（宽 42 的神锻那个只有 8px）⇒ 数字被挤没了、玩家看到"一片黑"✗。
            //   现在宽 62 ⇒ textW = 62-30-4 = 28 ✓（先例 CatalystAccessConfigurator :4444 是 74⇒40 ✓）。
            Widget slotInput = new IntInputWidget(SLOT_INPUT_X, BAND_MODE_ROW_Y, SLOT_INPUT_W, ROW_H,
                    machine::getManualAssignSlot, machine::setManualAssignSlot)
                    .setMin(1).setMax(machine.getCatalystSlotCount())
                    .setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.mode.manual_slot"));
            group.addWidget(slotLabel);
            group.addWidget(slotInput);

            // 「指定」= 把当前游标模式写进该槽（优先于自动识别，专治产物歧义）；
            // 「清除」= 撤销该槽的手动指定。
            // 两者都只在服务端动作（沿用 !machine.isRemote() 守卫），悬停文案沿用合并前那三条既有 tip 键 ✓
            Widget assignBtn = new ButtonWidget(SLOT_ASSIGN_X, BAND_MODE_ROW_Y, SLOT_BTN_W, ROW_H,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(
                            Component.translatable("gtlmodular.pattern_assembly.mode.manual_assign").getString())),
                    data -> {
                        if (!machine.isRemote()) {
                            machine.assignManualType(machine.getManualAssignSlot(), false);
                        }
                    }).setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.mode.manual_assign.tip"),
                            Component.translatable("gtlmodular.pattern_assembly.mode.manual_rc.tip"));
            Widget clearBtn = new ButtonWidget(SLOT_CLEAR_X, BAND_MODE_ROW_Y, SLOT_BTN_W, ROW_H,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(
                            Component.translatable("gtlmodular.pattern_assembly.mode.manual_clear").getString())),
                    data -> {
                        if (!machine.isRemote()) {
                            machine.assignManualType(machine.getManualAssignSlot(), true);
                        }
                    }).setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.mode.manual_clear.tip"));
            group.addWidget(assignBtn);
            group.addWidget(clearBtn);

            // 该槽当前的手动指定状态（活文本：未指定 / 已指定 <模式名>）—— 原模式页最后一行
            LabelWidget stateLabel = new LabelWidget(BAND_TEXT_X, BAND_MODE_STATE_Y, this::assignStateText);
            group.addWidget(stateLabel);

            // ================= 底板二：样板倍率 + 神锻 =================
            group.addWidget(new ImageWidget(BAND_X, BAND_SCALE_Y, BAND_W, BAND_SCALE_H,
                    new ColorRectTexture(BAND_BG)));
            group.addWidget(new ImageWidget(BAND_X, BAND_SCALE_Y, ACCENT_W, BAND_SCALE_H,
                    new ColorRectTexture(BAND_ACCENT)));
            // 分组标题沿用既有 scale.title（「样板倍率」），后面跟一句"视图缩放"（新键）点明口径：
            //   只改 AE 看到的样板、仓里的样板一个字节不动（完整说明在页签悬停的 scale.tooltip 里 ✓）
            group.addWidget(new LabelWidget(BAND_TITLE_X, BAND_SCALE_TITLE_Y,
                    Component.translatable("gtlmodular.pattern_assembly.scale.title").getString()));
            group.addWidget(new LabelWidget(58, BAND_SCALE_TITLE_Y,
                    Component.translatable("gtlmodular.pattern_assembly.mode_scale.scale_hint").getString()));

            // 乘法倍率（1..100）—— 原「样板倍率」页左列
            group.addWidget(new LabelWidget(8, BAND_SCALE_LABEL_Y,
                    Component.translatable("gtlmodular.pattern_assembly.scale.multiply.label").getString()));
            group.addWidget(new IntInputWidget(8, BAND_SCALE_ROW_Y, 48, ROW_H,
                    machine::getPatternScale, machine::setPatternScale)
                    .setMin(MIN_SCALE).setMax(MAX_SCALE));
            // 除法倍率（1..100）—— 原「样板倍率」页右列
            group.addWidget(new LabelWidget(60, BAND_SCALE_LABEL_Y,
                    Component.translatable("gtlmodular.pattern_assembly.scale.divide.label").getString()));
            group.addWidget(new IntInputWidget(60, BAND_SCALE_ROW_Y, 48, ROW_H,
                    machine::getPatternDivScale, machine::setPatternDivScale)
                    .setMin(MIN_SCALE).setMax(MAX_SCALE));
            // 重新应用：清掉改写缓存、让 AE 重取样板表（沿用既有文案键与 tip 键 ✓）
            group.addWidget(new ButtonWidget(112, BAND_SCALE_ROW_Y, 50, ROW_H,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(
                            Component.translatable("gtlmodular.pattern_assembly.scale.apply").getString())),
                    data -> machine.invalidateRewriteCache())
                    .setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.scale.apply.tip")));

            // 神锻（0.3.36 并进来的那四个控件，这里改成**一行排布**）：
            //   电源开关（原 ToggleButtonWidget，tooltipText 仍是原来的键 —— 它自动拼 .enabled/.disabled，
            //   这两个键 zh_cn/en_us 都在）+ 状态文字（活文本，开=§a 关=§8）+ 倍率输入（1..30）
            // ★ 0.3.47：整行改由 BAND_SCALE_FOA_Y 推导，文字用 FOA_TEXT_OFFSET 与 20 高的开关对齐 ✓
            // ★ 0.3.51 缺陷修复（③）：原来这行是「[⏻] 神锻模式：关闭 倍率[42宽的输入框]」
            //   输入框宽 42 ⇒ 中间文本框只有 42-30-4 = 8px ⇒ 数字完全看不见 ✗（玩家反馈"黑框无数字"✓）。
            //   现在：电源开关 20 + 短标签「神锻」+ 短状态「开启/关闭」+ 输入框 82..162（textW 46 ✓）。
            //   「神锻模式：开启/关闭」的完整说法仍在开关自身的悬停里（ToggleButtonWidget 会按
            //   setTooltipText("…foa") 自动拼 .enabled/.disabled —— 既有行为，一个字没改 ✓）。
            group.addWidget(new ToggleButtonWidget(FOA_TOGGLE_X, BAND_SCALE_FOA_Y, FOA_TOGGLE_W, FOA_ROW_H,
                    GuiTextures.BUTTON_POWER, machine::isFoaModeEnabled, machine::setFoaModeEnabled)
                    .setTooltipText("gtlmodular.pattern_assembly.foa"));
            group.addWidget(new LabelWidget(FOA_LABEL_X, BAND_SCALE_FOA_Y + FOA_TEXT_OFFSET,
                    Component.translatable("gtlmodular.pattern_assembly.mode_scale.foa.label").getString()));
            group.addWidget(new LabelWidget(FOA_STATE_X, BAND_SCALE_FOA_Y + FOA_TEXT_OFFSET, this::foaStateText));
            group.addWidget(new IntInputWidget(FOA_INPUT_X, BAND_SCALE_FOA_Y, FOA_INPUT_W, FOA_ROW_H,
                    machine::getFoaPatternOutputMultiplier, machine::setFoaPatternOutputMultiplier)
                    .setMin(MIN_FOA_MULTIPLIER).setMax(MAX_FOA_MULTIPLIER)
                    .setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.mode_scale.foa.multiplier.tip")));

            // ================= 单配方类型：把"模式相关"的控件藏起来 =================
            // 合并前这一页根本不挂（页面条件 = 可识别配方类型 > 1）；合并后本页**恒定挂载**
            // （它同时承载倍率/神锻）⇒ 用 setVisible 精确复现"原来没有模式页"的观感，
            // 第 1 行留 mode.none 文案说明原因，倍率/神锻区照常可用 ✓
            if (!multi) {
                bandModeHeader.setVisible(false);
                slotLabel.setVisible(false);
                slotInput.setVisible(false);
                assignBtn.setVisible(false);
                clearBtn.setVisible(false);
                stateLabel.setVisible(false);
            }
            return group;
        }

        /**
         * 当前模式芯片下面的 2px 青色"选中条"（0.3.49 起的视觉线索）。
         *
         * <p>★ 0.3.51：单独抽出来是因为它必须 {@code setActive(false)} —— 它是在芯片**之后**加入
         * {@code WidgetGroup} 的，而 {@code WidgetGroup.mouseClicked} **倒序**遍历子控件、命中即返回
         * （取证 {@code gtl-dev/api-dump/ldd_widgetgroup.javap.txt:1640-1677}）⇒ 它会比芯片先被问到 ✗。
         * 倒序遍历会跳过 {@code isActive()==false} 的子控件（同文件 :1660-1661 ✓）⇒ 置成非激活后，
         * 这 2px 永远不可能挡住"当前模式芯片"的点击 ✓（修"芯片点不动"的一道保险）。
         *
         * <p>写法上刻意**不**链式调用 {@code setActive}：把 {@code setActive(false);} 作为独立语句，
         * 避免依赖它的返回类型（0.3.49 那次 {@code cannot find symbol} 的教训 ✗：链式赋值要把返回类型
         * 完全对上才行 ✓）。
         */
        private static Widget chipUnderline(int x, int w) {
            ImageWidget underline = new ImageWidget(x, HEADER_Y + HEADER_H - 3, w, 2,
                    new ColorRectTexture(BAND_ACCENT));
            underline.setActive(false);   // 只做视觉，不吃任何点击 ✓
            return underline;
        }

        /**
         * 神锻开关的状态文字（活文本）。
         *
         * <p>★ 0.3.51：改读**短文案**（{@code mode_scale.foa.state.on/off} = 「开启」/「关闭」）——
         * 原来拼的是 {@code foa.enabled}/{@code foa.disabled}（"神锻模式：开启" 7 个字 ≈63px），
         * 在 172 宽的窄页里把神锻倍率输入框挤到只剩 8px 可见宽度（数字看不见 ✗）。
         * 完整说法没丢：电源开关自己的悬停仍是 {@code …foa} → 自动拼 {@code .enabled/.disabled} ✓
         * （那一套键一个字没动 ✓ 语义不变 ✓）。
         */
        private String foaStateText() {
            boolean on = machine.isFoaModeEnabled();
            return Component.translatable(on
                    ? "gtlmodular.pattern_assembly.mode_scale.foa.state.on"
                    : "gtlmodular.pattern_assembly.mode_scale.foa.state.off").getString();
        }

        /**
         * 游标按钮（兜底行的 ◀ / ▶）点击。
         *
         * <p>★ 0.3.51 修复：加**分侧守卫**。取证 LDLib {@code ButtonWidget}：
         * {@code mouseClicked}（{@code gtl-dev/api-dump/ldd_button.javap.txt:120-150}）会先
         * {@code writeClientAction(1, …)}，再**立即在客户端**调用一次 onPressCallback；
         * 服务端则由 {@code handleClientAction}（同文件 :152-171）再调用一次 ⇒ **回调两侧各跑一次** ✗。
         * 而 {@code cycleRecipeType} 改的是 {@code @DescSynced modeCursorIndex}（本文件 :224）——
         * 客户端那次是改会被同步覆盖的镜像 ✗ ⇒ 原来 Shift 点击两侧各推 5 次 = 10 次 ✗。
         * 现在只在服务端跑 ✓：一次点击 = 一次推进 ✓（与 assignBtn 同一守卫口径 ✓）。
         */
        private void cycle(com.lowdragmc.lowdraglib.gui.util.ClickData data, boolean forward) {
            if (machine.isRemote()) {
                return;   // 客户端不推进：服务端推进后会同步回来（避免两侧各推一次 ✗）
            }
            int times = data.isShiftClick ? 5 : 1;
            for (int i = 0; i < times; i++) {
                machine.cycleRecipeType(forward);
            }
        }

        /**
         * 芯片行点击：把模式游标**直接**拨到第 {@code target} 个（0.3.49 新增，0.3.51 重写）。
         *
         * <p><b>0.3.49 版为什么"点了没反应"</b>（取证）：旧实现是
         * {@code while (machine.getModeCursorIndex() != target) machine.cycleRecipeType(…)} 的循环 ✗。
         * {@code modeCursorIndex} 是 {@code @DescSynced}（:224）＝**服务端权威**字段，而 LDLib 的回调
         * **两侧都会跑**（取证见 {@link #cycle}）⇒ 客户端那次改的是会被同步覆盖的镜像 ✗：
         * 一旦这一拍读到的镜像还没同步回来，循环就会空转到 guard 上限 ✗ ⇒ 玩家看到"毫无反应" ✗。
         *
         * <p><b>现在</b>分侧执行、服务端权威：
         * <ul>
         *   <li><b>服务端</b>：直接 {@code machine.setRecipeType(target)}（既有公开 API :781，
         *       {@code cycleRecipeType} 内部走的也是它 ✓）—— 一步落位、不循环，并顺带置
         *       {@code modeCursorTouched}（把控制权从"跟随控制器"接过来 ✓ :1681）。</li>
         *   <li><b>客户端</b>：{@code machine.applyModeCursorLocally(target)}（0.3.51 新增）——
         *       只写本地游标与"玩家拨过"标记，**不发包、不等同步** ⇒ 高亮立刻跟手 ✓
         *       （客户端本来就有可用的配方类型表：同页"当前模式名"活文本就是靠它显示的 ✓）。</li>
         * </ul>
         */
        private void selectMode(com.lowdragmc.lowdraglib.gui.util.ClickData data, int target) {
            List<GTRecipeType> types = machine.getAvailableRecipeTypes();
            if (types.size() <= 1 || target < 0 || target >= types.size()) {
                return;
            }
            if (machine.isRemote()) {
                machine.applyModeCursorLocally(target);
                machine.applyModeCursorLocally(target);
        refreshChipLabels();   // ★ 0.3.61：点击后立刻刷新芯片文字颜色 ✓
            } else {
                machine.setRecipeType(target);
            }
        }

        /**
         * 配方类型 → 组件。用 {@code registryName.toLanguageKey()}（即 {@code gtceu.cutter} 这种）
         * 去查语言文件，所以显示的是「切割机」这种中文名；没翻译时才退回路径名。
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

        /** 该槽当前的手动指定状态（活文本，跟着同步字段走）。 */
        private String assignStateText() {
            String id = machine.getManualAssignTypeId();
            if (id == null || id.isEmpty()) {
                return Component.translatable("gtlmodular.pattern_assembly.mode.manual_state.none").getString();
            }
            return Component.translatable("gtlmodular.pattern_assembly.mode.manual_state.assigned",
                    recipeTypeName(id)).getString();
        }

        /** 类型 id → 中文模式名（和模式页同一套口径）。 */
        private static String recipeTypeName(String typeId) {
            try {
                net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(typeId);
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
                net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(typeId);
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
         * 第 1 行的模式文本（活文本）。
         *
         * <p>取数口径与合并前**完全一致**（{@code getModeCursorIndex()} + 缓存里的类型数量）；
         * 重排后只做了两件事：
         * <ol>
         *   <li>把"当前值"强调出来 —— 模式名走 {@code §b§l}（青色加粗）、序号走 {@code §8·§7}（灰）；
         *       颜色只靠 {@code §} 码（本文件既有页的 {@code mode.manual_state.*} 等键就是这么用的 ✓）；</li>
         *   <li>把兜底条件从 {@code types.isEmpty()} 放宽到 {@code types.size() <= 1} ——
         *       单配方类型时这一页现在也会挂着（它还要显示倍率/神锻），
         *       于是第一行正好用既有的 {@code mode.none} 文案（原文案就是"未检测到控制器或不支持多配方模式"，
         *       语义与新情况完全吻合 ✓ 不需要新键、也没有改任何既有键的语义）。</li>
         * </ol>
         */
        /** 当前模式名（§b§l 青加粗 ＝ 强调"当前值"）；单配方类型时沿用既有 mode.none 文案（语义未变 ✓）。 */
        private String modeNameLabel() {
            List<GTRecipeType> types = machine.getAvailableRecipeTypes();
            if (types.size() <= 1) {
                return "§8" + Component.translatable("gtlmodular.pattern_assembly.mode.none").getString();
            }
            int index = Math.max(0, Math.min(types.size() - 1, machine.getModeCursorIndex()));
            return "§b§l" + modeName(index);
        }

        /**
         * 模式序号 {@code x/y}（§8 灰色 ＝ 次要信息，靠右端显示）；
         * 单配方类型时返回空串 ⇒ 不会在"模式名"右边多出一截无意义文字 ✓。
         */
        private String modeIndexLabel() {
            List<GTRecipeType> types = machine.getAvailableRecipeTypes();
            if (types.size() <= 1) {
                return "";
            }
            int index = Math.max(0, Math.min(types.size() - 1, machine.getModeCursorIndex()));
            return "§8" + (index + 1) + "/" + types.size();
        }
    }

    // ================= 样板倍率页 / 神锻页：已并入上面的合并页 ✗ =================
    //
    //  ★ 0.3.46：原 ScaleConfigurator（页名「样板倍率」）与 FoaConfigurator（页名「神锻样板模式」，
    //    0.3.36 起就已不单独占页签、只作为"倍率页里的一个嵌入块")**整体删除**，
    //    它们承载的每一个控件都被搬进 ModeScaleConfigurator（见上面那个类的类注释与 createConfigurator
    //    里逐行的绑定说明：乘法/除法倍率 + 重新应用 + 神锻电源开关 + 神锻状态文字 + 神锻倍率 1..30）。
    //  · 删除前已 grep 全工程：这两个类名在 molten 里只有"定义处 + ScaleConfigurator 里那一处嵌入调用"
    //    （本文件内 ✓）；LazyPatternAssemblyPartMachine 有**同名但各自独立**的嵌套类，不受影响 ✓。
    //  · 文案键一个都没删：scale.* / foa.* 仍在用（分组标题、说明行、悬停、神锻开关的
    //    .enabled/.disabled）⇒ 语言文件里既有键的语义保持原样 ✓

    // ================= 专属催化剂槽页 =================

    /**
     * 「专属催化剂槽」页：槽位状态一律走机器上的同步字段
     * （{@code catalystViewSlot} / {@code catalystPageOpen}），两侧各自用每 tick 的
     * {@code updateScreen}（客户端）/ {@code detectAndSendChanges}（服务器）跟着字段重建控件。
     *
     * <p>注意 LDLib 的数值框文本回调<b>只在服务器侧执行</b>，所以界面绝不能直接由数值框驱动，
     * 否则会出现「服务器换了槽、客户端还画着旧槽」。
     */
    static final class CatalystAccessConfigurator implements IFancyConfigurator {
        private final MoltenPatternAssemblyPartMachine machine;

        /** 本侧已经建好控件的槽位（1 起；-1 = 还没建）。 */
        private int shownSlot = -1;

        private WidgetGroup root;
        private MEPatternCatalystUIManager catalystUI;

        private ConfiguratorPanel panel;
        private ConfiguratorPanel.Tab tab;

        /** 面板固定尺寸：不随内容动态变大，避免展开视图顶出屏幕被切掉。 */
        private static final int PAGE_WIDTH = 202;
        private static final int PAGE_CLOSED_HEIGHT = 44;
        /** 展开高度 = 顶部那一行(42) + 槽位容器高(3×18+24=78) + 余量。 */
        private static final int PAGE_OPEN_HEIGHT = 128;

        CatalystAccessConfigurator(MoltenPatternAssemblyPartMachine machine) {
            this.machine = machine;
        }

        void bindPanel(ConfiguratorPanel panel, ConfiguratorPanel.Tab tab) {
            this.panel = panel;
            this.tab = tab;
        }

        private int slotCount() {
            return machine.getCatalystSlotCount();
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
            // 悬停只显示页名
            return List.of(Component.translatable("gtlmodular.pattern_assembly.catalyst.title"));
        }

        @Override
        public Widget createConfigurator() {
            int slots = slotCount();
            shownSlot = -1;
            root = new WidgetGroup(0, 0, PAGE_WIDTH, PAGE_OPEN_HEIGHT) {

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

            catalystUI = new MEPatternCatalystUIManager(0,
                    machine.catalystItems, machine.catalystFluids, machine.cacheRecipeCount,
                    machine::removeSlotFromGTRecipeCache);
            catalystUI.setSelfPosition(2, 42);
            catalystUI.setSize(PAGE_WIDTH - 8, PAGE_OPEN_HEIGHT - 48);
            catalystUI.setVisible(false);
            catalystUI.setActive(false);
            root.waitToAdded(catalystUI);
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

        private void followMachineState() {
            // ★ 0.3.30 诊断提到最前：原来放在方法末尾，而本方法第一行就会因 catalystUI == null
            //   提前返回 ⇒ 展开页明明开着，却从来没打出过几何行 ✗。挪到最前面 = 界面一开必有一条。
            logPageGeometry("催化剂页@" + machine.getPos().toShortString(), root, panel, tab);
            if (catalystUI == null) {
                return;
            }
            if (!machine.isCatalystPageOpen()) {
                if (catalystUI.isVisible()) {
                    catalystUI.setVisible(false);
                    catalystUI.setActive(false);
                }
                // ★ 0.3.31：删掉"收起时缩回 44"这句。尺寸一变就会重启 GTCEu 的定位动画，
                //   而 Tab.onChildSizeUpdate(configurator_tab.javap.txt:237-294) 是**沿用旧 dragOffsetY、
                //   只按新宽度重算 X** ⇒ 位置被反复"半路重置"。"人家的页"正是靠**创建后尺寸恒定**、
                //   只走一次动画才停在对的位置 ✓（这也是 0.3.24/0.3.25/0.3.28 三次改高度都没用的原因）。
                return;
            }
            int want = machine.getCatalystViewSlot();
            if (want != shownSlot) {
                shownSlot = want;
                catalystUI.toggleFor(Math.max(0, want - 1));
                layoutSideBySide();
            }
            if (!catalystUI.isVisible()) {
                catalystUI.setVisible(true);
                catalystUI.setActive(true);
            }
            if (root != null && root.getSizeHeight() != PAGE_OPEN_HEIGHT) {
                root.setSize(PAGE_WIDTH, PAGE_OPEN_HEIGHT);
            }
            // ★ 0.3.29 诊断（只打日志，不设位置）
            if (root != null) {
                logPageGeometry("催化剂页@" + machine.getPos().toShortString(), root, panel, tab);
            }
        }

        /**
         * 摘掉 gtlcore 的「实际配方数量」，把物品催化剂槽与流体催化剂槽左右并排。
         * 只挪容器组本身（内层槽位组在容器里，跟着走）；用 self 坐标，别用含父级的绝对坐标。
         */
        private void layoutSideBySide() {
            List<Widget> children = catalystUI.widgets;
            removeCacheCountWidget();
            Widget itemBox = findContainer(children, SlotWidget.class, null);
            Widget fluidBox = findContainer(children, TankWidget.class, itemBox);
            if (itemBox == null || fluidBox == null) {
                return;
            }
            itemBox.setSelfPosition(4, 0);
            fluidBox.setSelfPosition(4 + itemBox.getSizeWidth() + 8, 0);
            // ★ 0.3.36：只在尺寸真的需要变时才 setSize。
            //   原来每切一次槽位都无条件 setSize ⇒ LDLib 会按新尺寸**重新缩放内部文字**
            //   ⇒ 队长看到的"物品/流体催化剂槽字体在切换槽位时变化"就是这个 ✗。
            if (catalystUI.getSizeWidth() != PAGE_WIDTH - 8 || catalystUI.getSizeHeight() != PAGE_OPEN_HEIGHT - 48) {
                catalystUI.setSize(PAGE_WIDTH - 8, PAGE_OPEN_HEIGHT - 48);
            }
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

    // ================= 样板槽右键/中键层 =================

    /**
     * 样板槽上的透明点击层：<b>右键/中键</b>点样板槽 → 打开该槽的专属催化剂槽。
     * 只接管右键和中键，左键返回 false 透传；手上拿着东西时右键也透传。
     * 客户端只发 client action，槽位/展开状态写在服务器侧的同步字段上。
     */
    private static final class SlotRightClickOverlay extends WidgetGroup {
        private static final int ACTION_OPEN_CATALYST_SLOT = 20;
        private static final int ACTION_ASSIGN_MODE_SLOT = 21;

        private final MoltenPatternAssemblyPartMachine machine;
        private final WidgetGroup base;

        SlotRightClickOverlay(MoltenPatternAssemblyPartMachine machine, WidgetGroup base, int width, int height) {
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

    /** 供 UI/日志显示的等级名（MV/HV/.../UIV）。 */
    public static String tierName(int tier) {
        return switch (tier) {
            case 2 -> "MV";
            case 3 -> "HV";
            case 4 -> "EV";
            case 5 -> "IV";
            case 6 -> "LuV";
            case 7 -> "ZPM";
            case 8 -> "UV";
            case 9 -> "UHV";
            case 10 -> "UEV";
            case 11 -> "UIV";
            default -> String.valueOf(tier);
        };
    }

    // ==================== 片 C 归档（复制 / 剪切 / 粘贴）页 ====================

    /**
     * ★ <b>「归档」页</b>：复制 / 剪切 / 粘贴 / 清空工具 + 「含设置」「含名字」「落位模式」三个开关
     * + 本机仓与手上工具档案的<b>只读</b>张数展示。
     *
     * <p>与惰性机那个页面<b>同款版式、同一批语言键</b>（惰性机那条线已被队长验收 ⇒ 直接沿用，
     * 片 C <b>零新增语言键</b>）；差别只有机器类型与"机器侧那一半"
     * （现役机容量<b>注册期固定</b> ⇒ 装不下时整体拒绝，页面与提示里都没有任何"扩容"字样）。
     *
     * <h3>页序</h3>
     * 挂在<b>最后一页之后</b>（见 {@link #attachConfigurators(ConfiguratorPanel)}）：只是追加一个 tab，
     * 既不动任何已有页的下标，也不碰催化剂页那套"挂它之前抓 {@code getTabs().size()}"的绑定。
     *
     * <h3>三个动作的落点（全部走同一个引擎）</h3>
     * <ul>
     *   <li><b>复制</b>：本机 → 手上的工具（本机一个字节都不动；按张扣 {@code ae2:blank_pattern}）；</li>
     *   <li><b>剪切</b>：本机 → 工具，<b>写成功之后才</b>清空本机（"先写后清"的唯一落点）；</li>
     *   <li><b>粘贴</b>：工具 → 本机（容量装不下 ⇒ <b>整体拒绝</b>，本机一个字节都不动；落位方式看本页开关）；</li>
     *   <li><b>清空工具</b>：只清工具里的档案数据（物品本身留在手上）。</li>
     * </ul>
     *
     * <p><b>玩家是谁</b>：配置面板的构造只给得到机器，而"手上的工具"必须有玩家才能拿到 ——
     * 用与 gtlcore {@code PatternTestBehavior} 同一套口径（按坐标找最近的玩家，拿不到就退回
     * "最近的玩家"），再拿不到就只是静默不动作，绝不猜一个别人的背包。
     *
     * <p><b>控件只用已验证的三种</b>（{@code ButtonWidget} / {@code GuiTextures.BUTTON} /
     * {@code LabelWidget}）：刻意不引入 {@code ToggleButtonWidget}（它会把 {@code tooltipText}
     * 拼上 {@code .enabled/.disabled} 当语言键用 ⇒ 会需要新键）。
     */
    static final class ArchiveConfigurator implements IFancyConfigurator {

        /** 面板尺寸（与模式页/催化剂页同宽 240，保证展开后不越出主界面）。 */
        /** ★ 0.3.37：按队长要求收窄（240 → 172）—— 内容最宽只到 156（两列 74 按钮），右边全是空白。 */
        private static final int PAGE_WIDTH = 172;
        /**
         * ★ 0.3.25 回滚：<b>改回 134（与惰性机 {@code LazyPatternAssemblyPartMachine} 的归档页同版式）</b>
         * —— 上一轮（0.3.24）把它压到 118，已被队长实机证明<b>无效</b>。
         *
         * <h4>为什么"压页高"这条路是错的（现读字节码，不是推断）</h4>
         * {@code ConfiguratorPanel$Tab.expand()} 给展开视图定的目标位置只有两个分量
         * {@code (dragOffsetX, dragOffsetY)}（{@code gtl-dev/api-dump/configurator_tab.javap.txt:434-455}：
         * {@code 184: new Position} … {@code 226: getfield dragOffsetY} … {@code 230: <init>(II)}），
         * 初始都是 0，<b>只有两种越界才会改它们</b>：
         * <ul>
         *   <li>X 方向左越界 —— {@code configurator_tab.javap.txt:26-127}；</li>
         *   <li>Y 方向<b>下</b>越界 —— {@code :130-181}，判据
         *       {@code getParentPosition().y + size.height > gui.getScreenHeight()}。</li>
         * </ul>
         * <b>这条公式里既没有"子页高度"这一项，也没有"上越界"这一支</b>
         * ⇒ 页高与"顶边位置"无关（这解释了为什么压页高无效）。
         *
         * <p><b>⚠ 0.3.25 订正</b>：本节原先接着断言"展开视图顶边恒等于<b>面板锚点</b>，
         * 面板被 gtlcore 按 tab 数量重排 ⇒ 多一个 tab 就上移 26"。**后半段已用字节码推翻** ✗：
         * gtlcore 的 {@code ConfiguratorPanelMixin}（{@code @Inject(attachConfigurators, TAIL)} →
         * {@code MEStorageConfiguratorTabLayout.arrange(panel)}；{@code @Redirect}
         * {@code collapseTab}/{@code expandTab} 里的 {@code ConfiguratorPanel$Tab.collapseTo(II)V}）
         * 与 {@code MEStorageConfiguratorTabLayout}（局部变量 {@code tab/tabIndex/tabs/expandedTab}，
         * 逐个 {@code setSelfPosition(Position)}）**只摆 tab、从不碰面板自身 Y**；
         * 而队长实机也证明"把面板下移 26"（{@link #PANEL_DOWN_SHIFT}）**没有效果** ✗。
         * ⇒ 目前唯一确证的是"**页高与顶边无关**"；"顶边由谁决定"仍未确证。
         * 队长最终诉求是"**整个界面都在屏幕里**"，且**原版 gtlcore 机器同样越界**（我们改不动它）
         * ⇒ 正解是方案 B：给 GTCEu 的 {@code ConfiguratorPanel$Tab.expand()} 补"上越界夹取"的**客户端 mixin**
         * （注入点/风险/需要的脚手架见本轮报告，本轮未写 ✗）。
         */
        /** ★ 0.3.37：页高相应收短（134 → 84）—— 最后一行文字在 y=64，下面不再留大片空白。 */
        private static final int PAGE_HEIGHT = 84;

        /**
         * ★ 0.3.28：本页的**初始高度** —— 逐条照搬同文件里「专属催化剂槽」页的
         * {@code PAGE_CLOSED_HEIGHT}（那一页的展开位置队长已实机确认正确 ✓）。
         *
         * <p><b>为什么这条能治"顶出屏幕上沿"</b>：GTCEu 给展开页定位发生在 widget
         * 刚被创建的那一刻，用的是**当下高度**。以 44 高被摆进屏幕、之后再长到
         * {@link #PAGE_HEIGHT}，效果是"只往下延伸、顶边留在原地" ⇒ 顶边不再越过屏幕上沿。
         * 此前"一上来就 134 高"正是被摆到屏幕外、只剩把手露出来的原因 ✗。
         */
        private static final int PAGE_CLOSED_HEIGHT = 44;

        /** 找玩家的半径（玩家就站在机器旁边开界面）。 */
        private static final double FIND_PLAYER_RADIUS = 8.0D;

        private final MoltenPatternAssemblyPartMachine machine;

        /** 本页的根控件（初始 44 高，展开后长到 {@link #PAGE_HEIGHT}）。 */
        private WidgetGroup root;

        /** 本页绑定的面板与它自己的 tab（照搬「专属催化剂槽」页的 {@code bindPanel}）。 */
        private ConfiguratorPanel panel;
        private ConfiguratorPanel.Tab tab;

        ArchiveConfigurator(MoltenPatternAssemblyPartMachine machine) {
            this.machine = machine;
        }

        /** 挂页时把面板与本页的 tab 交给本页（没绑上也不会坏事：见 {@link #followExpanded()}）。 */
        void bindPanel(ConfiguratorPanel panel, ConfiguratorPanel.Tab tab) {
            this.panel = panel;
            this.tab = tab;
        }

        /**
         * 与本页 tab 的展开状态对齐根控件高度 —— <b>照搬「专属催化剂槽」页的同名做法</b>：
         * <ul>
         *   <li>没被展开 ⇒ 回到 {@link #PAGE_CLOSED_HEIGHT}(44)（下次展开才能"先被摆进屏幕再长高"）；</li>
         *   <li>被展开 ⇒ 长到 {@link #PAGE_HEIGHT}（只往下延伸，顶边不动）。</li>
         * </ul>
         * <p><b>兜底</b>：万一 {@code bindPanel} 没被调到（panel/tab 为 null），一律按"展开"处理
         * ⇒ 退化成加这一改动之前的样子（恒定 {@link #PAGE_HEIGHT}），绝不会只剩一条 44 高的缝 ✗。
         */
        private void followExpanded() {
            if (root == null) {
                return;
            }
            // ★ 0.3.31：**彻底不再 setSize** —— 尺寸恒定 ⇒ GTCEu 的定位动画只走一次、停在该停的位置。
            //   任何尺寸变化都会触发 Tab.onChildSizeUpdate 动画重启（它沿用旧 dragOffsetY、只重算 X），
            //   把已经到位的位置半路重置 ✗。
            // （旧写法：want = PAGE_HEIGHT; if (root.getSizeHeight() != want) root.setSize(PAGE_WIDTH, want);）
            // ★ 0.3.29 诊断（只打日志，不设位置）
            logPageGeometry("归档页@" + machine.getPos().toShortString(), root, panel, tab);
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.pattern_assembly.archive.title");
        }

        @Override
        public IGuiTexture getIcon() {
            // 直接复用 gtlcore 那件工具物品的贴图（语义一致：都是"把样板总成整仓搬走"），不额外造图。
            return new ResourceTexture("gtlcore:textures/item/me_pattern_buffer_copy.png");
        }

        @Override
        public List<Component> getTooltips() {
            return List.of(Component.translatable("gtlmodular.pattern_assembly.archive.title"));
        }

        @Override
        public Widget createConfigurator() {
            // ★ 0.3.28：照搬同文件「专属催化剂槽」页（其实机位置已确认正确 ✓）的做法 ——
            //   本页**以 PAGE_CLOSED_HEIGHT(44) 创建**，等它真被展开后才长到 PAGE_HEIGHT。
            //   机制：GTCEu 定位展开页用的是"创建当下高度"，44 高先被摆进屏幕，
            //   随后我们只把它往下长 ⇒ 顶边留在原地、不再顶出屏幕上沿。
            WidgetGroup group = new WidgetGroup(0, 0, PAGE_WIDTH, PAGE_HEIGHT) {

                @Override
                public void updateScreen() {
                    super.updateScreen();
                    followExpanded();
                }

                @Override
                public void detectAndSendChanges() {
                    super.detectAndSendChanges();
                    followExpanded();
                }
            };
            this.root = group;
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
            // ★ 0.3.36（队长要求）：去掉「手上没有归档工具」那行红字 —— 判定已改成"背包/快捷栏里有就行"，
            //   不再需要这行提示（见 doCopy/doCut/doPaste 的工具查找）。


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
            group.addWidget(new ButtonWidget(4, 46, 74, 16,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(this::settingsToggleText)),
                    data -> toggleSettings())
                    .setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.archive.include_settings.tip")));
            group.addWidget(new ButtonWidget(82, 46, 74, 16,
                    new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(this::nameToggleText)),
                    data -> toggleName())
                    .setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.archive.include_name.tip")));

            // ★ 0.3.37（队长要求）：**「放置」按钮整行删除** —— 落位方式不再让玩家选，
            //   粘贴恒为"追加到后面"（默认值就是追加；togglePlaceMode 已改成 no-op）。

            // ---- 第五 / 六 / 七行：只读展示（本机读的是活仓，工具读的是手上那份 NBT）----
            //   ★ 0.3.25：随 PAGE_HEIGHT 回滚到 134，本三行也回到与惰性机归档页逐字相同的
            //   86 / 100 / 114（惰性机：LazyPatternAssemblyPartMachine 的
            //   `new LabelWidget(4, 86/100/114, ...)`；0.3.24 曾为 118 的页高把它们改成
            //   84/96/108，那次改动随本轮回滚一并作废）。
            group.addWidget(new LabelWidget(4, 64, this::machineLineText));
            group.addWidget(new LabelWidget(4, 100, this::toolLineText));
            // ★ 0.3.36（队长要求）：去掉「右键机器：…」那行提示。
            return group;
        }

        // ---------- 动作（全部服务端权威；客户端点了会被 findPlayer() 拦掉，绝不半执行） ----------

        private void doCopy() {
            try {
                ServerPlayer player = findPlayer();
                ItemStack stack = heldTool(player);
                if (stack == null || player == null) {
                    GTLModular.LOGGER.info("[gtlmodular] 猪咪归档页：复制未执行 —— 没找到在场玩家/工具 pos={}", machine.getPos());
                    return;
                }
                // 复制按张扣空白样板（不够整体拒绝）在 LazyPatternArchiveApi.copyFrom 里
                LazyPatternArchiveApi.copyFrom(machine, stack, player).tell(player);
            } catch (Throwable failure) {
                GTLModular.LOGGER.warn("[gtlmodular] 猪咪归档页：复制异常（已忽略）pos={} 原因={}",
                        machine.getPos(), failure.toString());
            }
        }

        private void doCut() {
            try {
                ServerPlayer player = findPlayer();
                ItemStack stack = heldTool(player);
                if (stack == null || player == null) {
                    GTLModular.LOGGER.info("[gtlmodular] 猪咪归档页：剪切未执行 —— 没找到在场玩家/工具 pos={}", machine.getPos());
                    return;
                }
                // ★ 先写成功再清源：整个顺序在 LazyPatternArchiveApi.cutFrom 里（见那边的三步注释）
                LazyPatternArchiveApi.cutFrom(machine, stack, player).tell(player);
            } catch (Throwable failure) {
                GTLModular.LOGGER.warn("[gtlmodular] 猪咪归档页：剪切异常（已忽略）pos={} 原因={}",
                        machine.getPos(), failure.toString());
            }
        }

        private void doPaste() {
            try {
                ServerPlayer player = findPlayer();
                ItemStack stack = heldTool(player);
                if (stack == null || player == null) {
                    GTLModular.LOGGER.info("[gtlmodular] 猪咪归档页：粘贴未执行 —— 没找到在场玩家/工具 pos={}", machine.getPos());
                    return;
                }
                // 落位模式/容量预检/整体拒绝全在引擎里（LazyPatternArchiveCodec.apply）
                LazyPatternArchiveApi.pasteInto(machine, stack, player).tell(player);
            } catch (Throwable failure) {
                GTLModular.LOGGER.warn("[gtlmodular] 猪咪归档页：粘贴异常（已忽略）pos={} 原因={}",
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
                GTLModular.LOGGER.warn("[gtlmodular] 猪咪归档页：清空工具异常（已忽略）pos={} 原因={}",
                        machine.getPos(), failure.toString());
            }
        }

        private void toggleSettings() {
            if (machine.isRemote()) {
                // 客户端只发按钮事件、不改本侧字段（服务端权威，与惰性机同口径）
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
         * ★ 0.3.37（队长要求）：落位方式**固定为"追加到后面"**，不再让玩家切换。
         * <p>原实现会在"追加 ⇄ 按原槽对齐"之间切；其中"按原槽对齐"会覆盖已有样板，玩家容易误触，
         * 队长要求去掉这个选择 ⇒ 这里改成 no-op，`archiveAlignSlots` 保持默认值 false（＝追加）。
         * 按钮已在 createConfigurator 里整行删除；paste 判定的"对齐"分支因此不会再被选中。
         */
        private void togglePlaceMode() {
            // 故意留空：落位方式固定 = 追加
        }

        // ---------- 只读展示 ----------

        /** 第一行右半：手上工具的状态（有没有档案 / 几张 / 没拿工具）。 */
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

        /** 「落位模式」按钮上的文字（随状态变）：默认追加、打开则按原槽对齐。 */
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
         * 与 gtlcore {@code PatternTestBehavior} 同口径；拿不到就返回 {@code null}（调用方静默，不猜别人）。
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

        /**
         * ★ 0.3.37（队长要求）：归档工具**不必拿在手上** —— 依次 主手 → 副手 → 背包/快捷栏 找，
         * 找到第一个就算（调用方静默，绝不改别的物品）。原来只认主手，队长要求放宽。
         */
        private ItemStack heldTool(ServerPlayer player) {
            if (player == null) {
                return null;
            }
            try {
                if (isArchiveTool(player.getMainHandItem())) {
                    return player.getMainHandItem();
                }
                if (isArchiveTool(player.getOffhandItem())) {
                    return player.getOffhandItem();
                }
                // Inventory.items 含快捷栏（前 9 格）与主背包
                for (ItemStack stack : player.getInventory().items) {
                    if (isArchiveTool(stack)) {
                        return stack;
                    }
                }
                return null;
            } catch (Throwable ignored) {
                return null;
            }
        }

        /** 是不是「样板仓归档」工具。 */
        private static boolean isArchiveTool(ItemStack stack) {
            return stack != null && !stack.isEmpty()
                    && stack.getItem() instanceof com.gtlmodular.common.item.PatternArchiveItem;
        }
    }
}
