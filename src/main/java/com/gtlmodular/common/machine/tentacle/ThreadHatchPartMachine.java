package com.gtlmodular.common.machine.tentacle;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;
import com.gtlmodular.GTLModular;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ★ 2026-11-01（0.3.282）<b>线程仓</b>（{@code gtlmodular:thread_hatch_<tier>}）——
 * GTO {@code gtocore:thread_hatch_<tier>}（部件类 {@code com.gtolib.api.machine.impl.part.ThreadPartMachine}）
 * 的<b>交互级</b>移植件。
 *
 * <p>★ 0.3.285：界面按 GTO 取证重做 —— 数量面板回到<b>部件主页</b>
 * （GTO 的 {@code AmountConfigurationPartMachine#createUIWidget()} 位置），左列配置页
 * <b>只留两个开关</b>（GTO 的 {@code attachConfigurators} 也只有这两个），并去掉「重 / 独」单字图标。
 *
 * <h2>它是什么</h2>
 * <p>一台 GTCEu 的<b>部件仓（PartMachine）</b>：贴着多方块外壳放，给控制器提供<b>跨配方线程数</b>。
 * GTO 原机注册 UV / UHV / UEV / UIV / UXV / OpV / MAX 共 7 档（{@code GTOMachines.java:281}）。
 *
 * <h2>GTO 原机长什么样（本工程取证）</h2>
 * <p>取证源：{@code _census/gto-server/mods/gtocore-forge-1.20.1-26.10.1.jar} 内
 * {@code com/gtolib/api/machine/impl/part/ThreadPartMachine.class}（2,092 B 壳类）与父类
 * {@code com/gtolib/api/machine/part/AmountConfigurationPartMachine.class}。用
 * {@code javap -p -c -constants -v} 现读：
 * <pre>
 * public final class ThreadPartMachine extends AmountConfigurationPartMachine {
 *   public static final String INDEPENDENT_THREAD;   // "gtocore.machine.independent_thread"
 *   private static final String REPEATED_RECIPES;    // "gtocore.machine.repeated_recipes"
 *   private static final String WIRELESS;            // "gtocore.machine.wireless_enable"
 *   private boolean repeatedRecipes;   // @SaveToDisk(defaultValue = "true")
 *   private boolean iThread;           // @SaveToDisk(defaultValue = "true")
 *   public native int  getCurrentThread();
 *   public native void attachConfigurators(ConfiguratorPanel);
 *   public native boolean isRepeatedRecipes();
 *   public native boolean isIThread();
 *   // attachConfigurators 的 6 个 lambda 声明 —— 恰好两组 Toggle：
 *   //   $0 ()Z / $1 (ClickData,Boolean)V / $2 (Boolean)List
 *   //   $3 ()Z / $4 (ClickData,Boolean)V / $5 (Boolean)List
 * }
 * </pre>
 * 三个语言键的原文案（同一 class 的 {@code @RegisterLanguage} 注解）：
 * <ul>
 *   <li>{@code gtocore.machine.independent_thread} = 「独立线程[%s]」</li>
 *   <li>{@code gtocore.machine.repeated_recipes} = 「并行重复配方[%s]」</li>
 *   <li>{@code gtocore.machine.wireless_enable} = 「独立线程需要安装无线能源仓才能启用」</li>
 * </ul>
 * 父类的数量面板（{@code javap -p -constants} 逐条现读）：
 * <pre>
 * protected AmountConfigurationPartMachine(MetaMachineBlockEntity, int, long min, long max);
 * public    native Widget    createUIWidget();     // ← 「数量配置」在这里（部件主页）
 * protected native Component getSettingTitle();    // ← 主页标题
 * protected native long      getCurrent();
 * private   native void      setCurrent(long);
 * protected native void      onAmountChange(long);
 * public    native boolean   canShared();
 * </pre>
 * 结论（★ 0.3.285 改正上一版的错误认知）：
 * <b>GTO 线程仓确实有数量配置面板，但它不是左列配置页，而是部件自带的 main UI 一页</b>
 * （{@code AmountConfigurationPartMachine#createUIWidget()}，min/max/current 三元组）；
 * {@link #attachConfigurators(ConfiguratorPanel)} 只挂<b>两个 Toggle</b>，<b>没有</b>数量页。
 * 两个开关默认都是<b>开</b>。★ 0.3.286：GTO 额外有一条「独立线程需要安装无线能源仓」的前置
 * （{@code gtocore.machine.wireless_enable}），<b>本模组按用户要求不保留</b> —— 见下。
 *
 * <p><b>为什么 attachConfigurators 的图标/顺序不可逐字取证</b>：这两个方法在 jar 里是
 * {@code native}（真码在 {@code native0/native/.../ThreadPartMachine.prod.bin}，加密），壳类常量池
 * 只有 Toggle 类与 3 个 lang key，没有任何贴图常量；GTOCore jar 也没有线程专用 gui 贴图。
 * 所以图标用 GTCEu 公共开关底图，顺序按 lambda 组（$0 组在前 = 并行重复配方）。
 *
 * <h2>本类怎么落（GTCEu 1.4.4 实际 API）</h2>
 * <p>GTCEu 1.4.4 <b>没有</b> {@code ToggleConfigurator} / {@code IntConfigurator} /
 * {@code IConfigurable}（{@code unzip -l gtceu-1.20.1-1.4.4.jar} 现查：{@code api/gui/fancy/} 下只有
 * {@code IFancyConfigurator}、{@code IFancyConfiguratorButton$Toggle}、
 * {@code ConfiguratorPanel$Tab} 这几个；{@code IConfigurable} 全库只有 AE2 的
 * {@code IConfigurableSlot}）。所以按「等价方案」落：
 * <ul>
 *   <li><b>数量配置</b>：{@link IntInputWidget}（{@code com.gregtechceu.gtceu.api.gui.widget}，
 *       自带 {@code setMin/setMax} 与 +/− 按钮、Ctrl/Shift 加速）放在部件主页
 *       {@link #createUIWidget()} —— 与 GTO 的
 *       {@code AmountConfigurationPartMachine.createUIWidget()} <b>同一位置、同一职责</b>；</li>
 *   <li><b>两个开关</b>：{@link IFancyConfiguratorButton#Toggle}（GTO 的字节码里用的就是它：
 *       {@code ThreadPartMachine.class} 常量池 #7/#8 = {@code IFancyConfiguratorButton$Toggle}），
 *       文案用 GTO 的 lang key 原文（「并行重复配方[%s]」/「独立线程[%s]」；
 *       第三句「独立线程需要安装无线能源仓才能启用」按用户要求 0.3.286 起不再使用）；</li>
 *   <li><b>界面入口</b>：GTO 走 gtolib 给部件加的 {@code createUIWidget()}；本模组走 GTCEu 自带的
 *       {@code IMultiPart → IFancyUIMachine}：右手空手右键这台仓就能开界面，主页 = 数量输入框，
 *       左列就是 {@link ConfiguratorPanel}，{@link #attachConfigurators(ConfiguratorPanel)}
 *       只挂两个开关页（与 {@code NanochipPortPartMachine} 同一条已验证的路）。</li>
 * </ul>
 *
 * <h2>数值存哪（GTCEu 1.4.4 的同步/落盘口径）</h2>
 * <ul>
 *   <li>三个字段带 LDLib 的 {@code @DescSynced}（服务端权威 → 客户端显示；控件回调在服务端执行）；</li>
 *   <li>落盘走 {@link #saveCustomPersistedData}/{@link #loadCustomPersistedData} 的<b>追加键</b>
 *       （{@code GTLModularThreadCount} / {@code GTLModularRepeatedRecipes} /
 *       {@code GTLModularIndependentThread}），老存档没有这些键 ⇒ 回落成「上限 / 开 / 开」，与 GTO 默认一致；</li>
 *   <li>读档夹区间 {@code 1..上限}（上限由电压档位决定，见 {@link #threadsForTier(int)}）。</li>
 * </ul>
 *
 * <h2>线程数口径</h2>
 * <p>上限与 GTO 逐字一致：{@code 1 << (tier - LuV)}
 * ⇒ <b>UV=4 / UHV=8 / UEV=16 / UIV=32 / UXV=64 / OpV=128 / MAX=256</b>；
 * {@link #getThreads()} 返回的是<b>当前设定值</b>（不是满档），控制器按它求和。
 *
 * <h2>两个开关到底改了什么（★ 2026-11-07 按 GTO 语义改回）</h2>
 * <ul>
 *   <li><b>并行重复配方</b>（{@code repeatedRecipes}，默认开）：
 *       开 = <b>同一条配方可以被多条线程同时跑</b>（吞吐优先；只有一种配方时也能吃满线程）；
 *       关 = 同一配方只占一条线程，多余线程去跑别的配方（<b>只有一种配方时这些线程会空转</b>）。</li>
 *   <li><b>独立线程</b>（{@code independentThread}，默认开）：
 *       开 = 每条线程<b>各有一份完整的并行/功率额度</b>（GTO：「每个线程都能独立拉取机器最大功率」）；
 *       关 = 所有线程<b>共用同一份额度</b>，整机并行不再随线程数翻倍。
 *       不再要求无线能源仓（0.3.286 起，{@code gtocore.machine.wireless_enable} 前置已删）。</li>
 * </ul>
 * 落地位置：两开关都由引擎统一从部件现读（{@code IThreadedRecipeMachine#threadHatchRepeatedRecipes()} /
 * {@code threadHatchIndependent()}）—— 机器只要在结构里允许线程仓就直接生效，不需要任何额外适配。
 * 「用哪张配方表」不再归独立线程管：多表机器默认全部扫。
 */
public class ThreadHatchPartMachine extends TieredPartMachine implements IMultiPart, IThreadHatchPart {

    /**
     * LDLib 托管字段表：本类新挂的 {@code @DescSynced} 字段要靠它登记，
     * 父级 holder 取 {@link MultiblockPartMachine} 那一层（{@code TieredPartMachine} 自己没有）。
     */
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(ThreadHatchPartMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ---------------- 存档键（追加键，老存档没有就回落默认值） ----------------

    private static final String TAG_THREADS = "GTLModularThreadCount";
    private static final String TAG_REPEATED = "GTLModularRepeatedRecipes";
    private static final String TAG_INDEPENDENT = "GTLModularIndependentThread";

    /** 当前设定的线程数（1 ~ {@link #getMaxThreads()}）。服务端权威，客户端只读。 */
    @DescSynced
    private int threadCount;

    /** 「重复配方」开关（GTO {@code repeatedRecipes}，默认开）。 */
    @DescSynced
    private boolean repeatedRecipes = true;

    /** 「独立线程」开关（GTO {@code iThread}，默认开；0.3.286 起不再有无线能源仓前置）。 */
    @DescSynced
    private boolean independentThread = true;

    /**
     * @param holder 机器方块实体（GTCEu 标准）
     * @param tier   电压档位（{@code GTValues.UV..MAX}），决定线程数上限
     */
    public ThreadHatchPartMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier);
        // 默认 = 本档上限（与 0.3.281 的固定值逐字一致，老存档不掉线程）
        this.threadCount = threadsForTier(tier);
    }

    // ---------------- IThreadHatchPart ----------------

    /** 当前设定值（不是满档）；越界一律夹回 {@code 1..上限}，恒 ≥ 1。 */
    @Override
    public int getThreads() {
        int max = getMaxThreads();
        return Math.max(1, Math.min(max, threadCount));
    }

    @Override
    public int getMaxThreads() {
        return threadsForTier(getTier());
    }

    @Override
    public boolean isRepeatedRecipes() {
        return repeatedRecipes;
    }

    @Override
    public boolean isIndependentThread() {
        return independentThread;
    }

    /**
     * 独立线程的<b>有效</b>状态 = 开关本身。
     *
     * <p>★ 0.3.286：上一版这里是 {@code independentThread && controllerHasWirelessEnergy()}
     * （GTO 的 WIRELESS 前置）；用户要求去掉 ⇒ 现在开关打开即生效，
     * 控制器侧（{@link NyarlathotepsTentacleMachine#isThreadDedupeActive()}）也不再问无线能源仓。
     */
    public boolean isIndependentThreadEffective() {
        return independentThread;
    }

    // ---------------- 设值（只在服务端真正改） ----------------

    /** 设置线程数；越界自动夹到 {@code 1..本档上限}。 */
    public void setThreadCount(int value) {
        int clamped = Math.max(1, Math.min(getMaxThreads(), value));
        if (clamped == threadCount) {
            return;
        }
        this.threadCount = clamped;
        if (!isRemote()) {
            markDirty();
        }
    }

    public void setRepeatedRecipes(boolean value) {
        if (value == repeatedRecipes) {
            return;
        }
        this.repeatedRecipes = value;
        if (!isRemote()) {
            markDirty();
            GTLModular.LOGGER.info("[gtlmodular] 线程仓 @{} 并行重复配方 → {}（{}）",
                    getPos(), value, value ? "同一配方可占多条线程" : "同一配方只占一条线程");
        }
    }

    public void setIndependentThread(boolean value) {
        if (value == independentThread) {
            return;
        }
        this.independentThread = value;
        if (!isRemote()) {
            markDirty();
            GTLModular.LOGGER.info("[gtlmodular] 线程仓 @{} 独立线程 → {}（{}）",
                    getPos(), value, value ? "全部配方表里独立找配方" : "只用当前选中的配方表");
        }
    }

    // ---------------- 存读档 ----------------

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        tag.putInt(TAG_THREADS, getThreads());
        tag.putBoolean(TAG_REPEATED, repeatedRecipes);
        tag.putBoolean(TAG_INDEPENDENT, independentThread);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        int max = getMaxThreads();
        // 缺键 ⇒ 保持构造器给的「本档上限」；有键 ⇒ 夹回 1..上限（档位变了也不会越界）
        this.threadCount = tag.contains(TAG_THREADS)
                ? Math.max(1, Math.min(max, tag.getInt(TAG_THREADS)))
                : max;
        this.repeatedRecipes = !tag.contains(TAG_REPEATED) || tag.getBoolean(TAG_REPEATED);
        this.independentThread = !tag.contains(TAG_INDEPENDENT) || tag.getBoolean(TAG_INDEPENDENT);
    }

    // ---------------- 控制器界面里的补行（IThreadHatchPart 的信息口径） ----------------

    /**
     * 控制器界面里显示：GTO 原话「可为机器提供 %s 线程的并行」+ 当前设定值。
     *
     * <p>★ 0.3.285：<b>只留这两行</b>。上一版这里还补了「重复配方[开] · 独立线程[开]」和
     * 无线能源仓状态，而控制器 {@code NyarlathotepsTentacleMachine#addDisplayText} 又各补一遍
     * ⇒ 面板上同一句出现两次（用户截图）。两个开关的实况现在只在<b>本仓界面左列的配置页</b>里，
     * 控制器面板不再重复。用 {@code IMultiPart#addMultiText}（部件给控制器的补行），
     * <b>不写</b> {@code IMultiPart.super.addMultiText(...)}（历史坑：编译失败）。
     */
    @Override
    public void addMultiText(List<Component> textList) {
        textList.add(Component.translatable("gtlmodular.thread_hatch.tooltip", getThreads())
                .withStyle(ChatFormatting.GRAY));
        textList.add(Component.translatable("gtlmodular.thread_hatch.setting",
                        getThreads(), getMaxThreads())
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 开关文字的「开 / 关」组件（两个配置页的标题、tooltip 共用）。 */
    public static Component onOff(boolean value) {
        return Component.translatable(value ? "gtlmodular.thread_hatch.on" : "gtlmodular.thread_hatch.off");
    }

    // ---------------- 界面 ----------------

    /**
     * 部件自带的 fancy UI 主页（右手空手右键本仓打开）—— GTO 的
     * {@code AmountConfigurationPartMachine#createUIWidget()} 同款：
     * 一页「标题 + 数量输入框（1 ~ 本档上限）」，没有别的说明行。
     *
     * <p>取证（{@code javap -p -constants} 现读 GTOCore 26.10.1 的
     * {@code com/gtolib/api/machine/part/AmountConfigurationPartMachine.class}）：
     * <pre>
     * protected AmountConfigurationPartMachine(MetaMachineBlockEntity, int, long min, long max);
     * public    native Widget createUIWidget();      // ← 数量面板就挂在部件主页
     * protected native Component getSettingTitle();   // ← 主页标题
     * protected native long getCurrent();
     * private   native void setCurrent(long);
     * protected native void onAmountChange(long);
     * public    native boolean canShared();
     * </pre>
     * 也就是说 GTO 的「数量配置」<b>不是</b>左列配置页，而是部件自带的这一页；
     * 左列配置页只有下面 {@link #attachConfigurators} 的两个开关。
     */
    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 176, 40);
        // 本 LDLib 版本的 LabelWidget 只收 String / Component / Supplier&lt;String&gt;（javap 现读）。
        group.addWidget(new LabelWidget(6, 4,
                Component.translatable("gtlmodular.thread_hatch.config.title").getString()));
        // GTO 同职责控件：1 ~ 本档上限 的数量输入框（+/− 按钮 + Ctrl/Shift 加速由控件自带）。
        group.addWidget(new IntInputWidget(6, 18, 150, 18,
                this::getThreads, this::setThreadCount)
                .setMin(1).setMax(getMaxThreads()));
        return group;
    }

    /**
     * 往左列配置面板挂页 —— <b>与 GTO 逐条对齐：只有两个开关，没有数量页</b>。
     *
     * <p>取证：{@code javap -p -constants ThreadPartMachine.class} 里
     * {@code attachConfigurators(ConfiguratorPanel)} 是 native（真码在
     * {@code native0/native/.../ThreadPartMachine.prod.bin}，加密不可反编译），
     * 但壳类把它的 <b>lambda 声明</b>全留下来了：
     * <pre>
     * lambda$attachConfigurators$0 ()Z                     ← 开关 1 getter
     * lambda$attachConfigurators$1 (ClickData, Boolean)V   ← 开关 1 setter
     * lambda$attachConfigurators$2 (Boolean)List           ← 开关 1 tooltip（static）
     * lambda$attachConfigurators$3 ()Z                     ← 开关 2 getter
     * lambda$attachConfigurators$4 (ClickData, Boolean)V   ← 开关 2 setter
     * lambda$attachConfigurators$5 (Boolean)List           ← 开关 2 tooltip（static）
     * </pre>
     * ⇒ <b>恰好两页 {@code IFancyConfiguratorButton$Toggle}</b>（常量池 #7/#8 直接引用该类），
     * <b>没有</b>数量配置页；tooltip lambda 都是 static（不带 this）⇒ 只回「标题[开/关]」一行，
     * 不按结构状态动态追加。
     *
     * <p>★ 0.3.286：GTO 的独立线程页还带一句 {@code gtocore.machine.wireless_enable}
     * （「独立线程需要安装无线能源仓才能启用」）；用户要求去掉这个前置，
     * 所以本模组的独立线程页<b>不再</b>追加那句 tooltip，也不再有对应的 lang key。
     *
     * <p>{@code super.attachConfigurators} 保留（与本工程 {@code NanochipVacuumHatchMachine} 同写法）：
     * GTCEu 的默认实现在部件上只会补「电源开关 / 超频」两种页，本仓两种情况都不命中 ⇒ 实际无副作用。
     */
    @Override
    public void attachConfigurators(ConfiguratorPanel panel) {
        super.attachConfigurators(panel);
        // 顺序：GTO 的 lambda 是两组（$0..$2 先挂、$3..$5 后挂），但哪一组是哪个开关
        // 在加密的 prod.bin 里读不到 ⇒ 按 GTO 的字段声明顺序（repeatedRecipes → iThread）摆。
        panel.attachConfigurators(createToggle("gtlmodular.thread_hatch.repeated", true,
                this::isRepeatedRecipes, this::setRepeatedRecipes));
        panel.attachConfigurators(createToggle("gtlmodular.thread_hatch.independent", false,
                this::isIndependentThread, this::setIndependentThread));
    }

    /**
     * 造一个 GTO 同款开关页（{@code IFancyConfiguratorButton$Toggle}）。
     *
     * <p><b>图标</b>：GTO 的这两个开关贴图存在 {@code ThreadPartMachine.prod.bin}（加密原生码）里，
     * {@code javap} 读不到任何贴图常量（壳类常量池 #1–#71 只有 Toggle 类与三个 lang key，
     * 没有 {@code ResourceTexture} / {@code GuiTextures} 引用），GTOCore jar 里也没有对应的
     * 线程专用 gui 贴图（{@code unzip -l ... | grep -i thread} 只有方块 overlay）⇒ 无法逐字复刻。
     * 这里用 GTCEu 的公共开关底图 {@link GuiTextures#TOGGLE_BUTTON_BACK}（18×36，上下两态），
     * <b>不再叠「重/独」单字</b>（0.3.284 及以前的做法，用户要求去掉）；两页靠 tooltip 区分。
     *
     * <p>tooltip 回调里的 {@code clickData.isRemote} 判断照抄 GTCEu 自己（客户端那次点击只做视觉反馈）。
     */
    private IFancyConfiguratorButton.Toggle createToggle(String titleKey, boolean gear,
                                                         java.util.function.BooleanSupplier getter,
                                                         java.util.function.Consumer<Boolean> setter) {
        IFancyConfiguratorButton.Toggle toggle = new IFancyConfiguratorButton.Toggle(
                toggleIcon(false, gear), toggleIcon(true, gear), getter,
                (ClickData clickData, Boolean pressed) -> {
                    if (!clickData.isRemote) {
                        setter.accept(pressed);
                    }
                });
        return toggle.setTooltipsSupplier(pressed -> {
            List<Component> lines = new ArrayList<>(2);
            lines.add(Component.translatable(titleKey, onOff(pressed)));
            // ★ 2026-11-03：补一行「它到底影响什么」，免得玩家点了看不出区别。
            lines.add(Component.translatable(titleKey + ".desc").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        });
    }

    /** GTCEu 公共开关底图：上半 = 开，下半 = 关（与 GTCEu 自己的 {@code Toggle} 用法一致）。 */
    /** 开关页贴图：GTCEu 底图 + 自绘图标（关态叠红叉）。★ 2026-11-02 自绘 gear/letter_c/cross。 */
    private static IGuiTexture toggleIcon(boolean on, boolean gear) {
        IGuiTexture base = GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0.0F, on ? 0.5F : 0.0F, 1.0F, 0.5F);
        IGuiTexture iconTex = new ResourceTexture("gtlmodular:textures/gui/thread_hatch/"
                + (gear ? "gear" : "letter_c") + ".png");
        if (!on) {
            iconTex = new GuiTextureGroup(iconTex,
                    new ResourceTexture("gtlmodular:textures/gui/thread_hatch/cross.png"));
        }
        return new GuiTextureGroup(base, iconTex);
    }

    /**
     * GTO 线程数公式：{@code 1 << (tier - LuV)}，恒 &ge; 1。
     *
     * <p>档位低于 LuV（正常注册不会出现）时按 1 条处理，避免负数移位；
     * {@code steps >= 30} 时夹到 int 上限，免得以后接进 {@code MAX} 之后的新档位时移位炸掉。
     */
    public static int threadsForTier(int tier) {
        int steps = Math.max(0, tier - GTValues.LuV);
        if (steps >= 30) {
            return Integer.MAX_VALUE;
        }
        return 1 << steps;
    }
}
