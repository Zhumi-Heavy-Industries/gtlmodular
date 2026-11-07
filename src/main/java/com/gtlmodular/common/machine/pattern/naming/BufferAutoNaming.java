package com.gtlmodular.common.machine.pattern.naming;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtlmodular.common.compat.EnhancedcoreIvModeCompat;
import com.gtlmodular.common.compat.SupbMutableMachineCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 猪咪样板总成（现役机 {@code MoltenPatternAssemblyPartMachine} / 惰性机
 * {@code LazyPatternAssemblyPartMachine}）的<b>自动命名</b>：本仓自研实现，两台总成共用这一份，
 * 不再各自维护一份逐行照抄的副本。
 *
 * <h2>一句话规则</h2>
 * <b>一台总成显示的名字 = 它自己负责的那个模式的显示名</b>。总成自己负责的模式 =
 * 落盘的绑定（{@code boundModeType} → {@code modeCursorIndex}）；没绑定时跟随控制器当前模式。
 * 同一控制器下两台总成各绑一个模式 ⇒ 各叫各的，主机在跑什么与名字无关。
 *
 * <h2>写名字的四条硬规则（顺序即优先级）</h2>
 * <ol>
 *   <li><b>玩家亲手改过名 ⇒ 永久让位</b>（{@link State#lockedName}，落盘）：换模式、改绑定、
 *       主机切模式、重进游戏、拆装重成型都不再改写；唯一例外是名字被清空时把它写回来；</li>
 *   <li><b>名字被别人改了</b>（与本机上次观察到的不同）⇒ 当场认作玩家改名，转 ①；</li>
 *   <li><b>该模式已经记着名字</b>（{@link State#modeNames}，落盘）⇒ 继续用那个名字（"延续"）；</li>
 *   <li><b>该模式还没有名字</b> ⇒ 用它的显示名写一次，并记进名字表。</li>
 * </ol>
 *
 * <h2>不该碰的场合（一律一个字都不写）</h2>
 * <ul>
 *   <li>结构未成形 / 控制器还没挂上 / 类型表为空 ⇒ 这一 tick 什么都不做，下一 tick 再试（自愈）；</li>
 *   <li>SUPB 可变配方机器（只认 {@code IPatternAssemblyHatch}，没有"当前模式"）；
 *       GTL-Enhancedcore 的跨配方 / 隔离 IV 机器（名字归增强版统一分配）—— 两处判据与
 *       {@code autoSwitchTickInner} 里那两条既有守卫同源；</li>
 *   <li>通用加工厂 / 组装厂 / 分离厂 / 混合厂：身份由<b>结构里塞入的机器</b>决定，
 *       塞入机器不支持本总成负责的模式时保持原名（乱起名会让玩家按错模式）；</li>
 *   <li>未绑定且名字已经有主、又没换过控制器 ⇒ 名字不跟着主机换模式变（既有口径）；</li>
 *   <li>{@link State#boundPending}（读档拿到绑定 id 但类型表还没就绪）⇒ 不命名，
 *       免得把名字记到"下标 0 的默认模式"名下。</li>
 * </ul>
 *
 * <h2>名字从哪里来</h2>
 * ① 本模组的少量覆盖表（{@code circuit_assembly}/{@code circuit_assembler} ⇒「电路组装」、
 * {@code disposable_tool_printing} ⇒「3D打印：一次性工具」）；
 * ② {@code Component.translatable(registryName.toLanguageKey())} 真翻译（与 JEI 分类标题、模式页同一来源）；
 * ③ 连原文都取不到 ⇒ 退回注册路径名（可读、不含命名空间）。
 * <b>绝不写空、绝不把 {@code gtceu.xxx} 这类 lang 键写进玩家看到的名字。</b>
 *
 * <h2>纪律</h2>
 * 起名纯粹是显示层的事：整个流程包在 try 里，任何异常都静默跳过，绝不影响机器逻辑
 * （不打日志，免得每 tick 刷屏）。
 */
public final class BufferAutoNaming {
    private BufferAutoNaming() {
    }

    /** GTL-Enhancedcore 写进 {@code customName} 的硬编码兜底串「未命名加工模式」（= 它没认出类型，不算名字）。 */
    private static final String UPSTREAM_FALLBACK = "\u672a\u547d\u540d\u52a0\u5de5\u6a21\u5f0f";

    /**
     * 每台总成一份的命名状态。
     *
     * <p>带 {@code 落盘} 注释的三项由总成自己的 {@code saveCustomPersistedData} /
     * {@code loadCustomPersistedData} 序列化，键名沿用旧版（不迁移存档）；其余是纯内存态。
     */
    public static final class State {
        /** 落盘：模式 id（{@code gtceu:lathe} 这种）→ 本总成给该模式起的名字。 */
        public final Map<String, String> modeNames = new LinkedHashMap<>();

        /** 落盘：玩家亲手改过的名字（{@code ""} = 没改过）。非空 ⇒ 永久让位。 */
        public String lockedName = "";

        /**
         * 落盘：这个锁定名是不是<b>玩家亲手确认过</b>的。
         *
         * <p>用来区分"玩家真的起了一个和模式同名的名字"与"旧版把上游自动写的模式名误锁成玩家改名"：
         * 只有玩家经界面确认（或归档还原）才置真；误锁自愈（见 {@code apply} 开头）只看未置真的条目。
         * 老存档没有这个键 ⇒ 默认 {@code false}。
         */
        public boolean playerLocked;

        /** 落盘：上一次自动写下的显示名。读档后用它认"这名字是不是我们自己写的"。 */
        public String lastAutomaticName = "";

        /** 内存：上一次观察到的 {@code customName}，用来识别"名字被别人改了"。 */
        public String observedName;

        /** 内存：上一次命名用的模式 id，把玩家的改名归到"当时显示的那个模式"名下。 */
        public String appliedModeKey;

        /** 内存：上一次命名时挂着的控制器；本总成被搬到别的机器上 ⇒ 允许按新模式重新起名一次。 */
        public IRecipeLogicMachine lastController;
    }

    /**
     * 一次命名所需的机器上下文（总成每 tick 现算，避免本类反向依赖具体总成类型）。
     *
     * @param types        控制器暴露的配方类型表
     * @param controller   控制器（可能为 null：还没挂上）
     * @param bound        本总成是否已绑定模式（{@code modeCursorTouched}）
     * @param boundIndex   绑定的模式下标（{@code modeCursorIndex}）
     * @param boundPending 读档拿到了绑定 id 但还没解析成下标
     * @param enabled      {@code enableActiveCustomNaming} 总开关
     * @param hint         调用点想优先用的下标（仅作兜底）
     */
    public record Context(List<GTRecipeType> types, IRecipeLogicMachine controller,
                          boolean bound, int boundIndex, boolean boundPending,
                          boolean enabled, int hint) {
    }

    /** 服务端每 tick 调一次（幂等：名字已经对了就一个 setter 都不调）。 */
    public static void tick(MEPatternBufferPartMachineBase buffer, State state, Context context) {
        if (buffer.isRemote()) {
            return;
        }
        IRecipeLogicMachine controller = context.controller();
        if (controller != null) {
            Level level = buffer.getLevel();
            long now = level == null ? 0L : level.getGameTime();
            // 增强版跨配方 / 隔离 IV 机器：名字归增强版按「配方表/序号」统一分配，我们再写只会互相覆盖。
            if (EnhancedcoreIvModeCompat.suppressesModeSwitch(controller, now)) {
                return;
            }
            // SUPB 可变配方机器：只认 IPatternAssemblyHatch，本仓连配方都绑不到 ⇒ 不命名。
            if (SupbMutableMachineCompat.suppressesModeSwitch(controller)) {
                return;
            }
        }
        try {
            apply(buffer, state, context);
        } catch (RuntimeException | Error ignored) {
            // 起名失败绝不影响机器
        }
    }

    private static void apply(MEPatternBufferPartMachineBase buffer, State state, Context context) {
        List<GTRecipeType> types = context.types();
        String current = buffer.getCustomName();
        // ★ 2026-11-03 修：旧版把"上游自动写上的模式名"误判成玩家改名并永久锁定（判据把空名字也当成"曾有名"），
        //   于是同一主机的两台总成都锁在上游写的同一个名字上。这里做一次自愈迁移：
        //   锁定名恰好是某个模式的显示名、且不是玩家亲手确认的 ⇒ 拆掉这个锁，按本总成自己的模式重新命名。
        if (!state.lockedName.isEmpty() && !state.playerLocked && isModeDisplayNameOfHost(state.lockedName, types)) {
            state.lockedName = "";
            state.observedName = current;
            buffer.markDirty();
        }
        // ① 名字被别人改了（玩家在界面里改、或别的代码写进来）⇒ 认作玩家改名，永久让位。
        //    「未命名加工模式」是上游的兜底串，不是任何人的选择 ⇒ 不算名字；名字被清空也不算改名。
        //    ★ 2026-11-03：① 之前必须真有名字（空串不算观察值，否则上游首次写名会被当成玩家改名），
        //    ② 新模式名不算玩家改名（上游 Enhancedcore 成形时会给每台总成写同一个"当前模式名"）。
        if (state.observedName != null && !state.observedName.isEmpty()
                && current != null && !current.isEmpty()
                && !current.equals(state.observedName) && !isPlaceholder(current)
                && !isModeDisplayNameOfHost(current, types)) {
            state.lockedName = current;
            state.playerLocked = false;   // 兜底判据不是"玩家亲手确认"，允许误锁自愈
            if (state.appliedModeKey != null) {
                state.modeNames.put(state.appliedModeKey, current);
            }
            buffer.markDirty();
        }
        // ② 总开关关掉 ⇒ 只保留"名字空着才写一次"的最小行为（诊断/回退用，默认开）。
        if (!context.enabled()) {
            if (current == null || current.isEmpty()) {
                int mode = legacyIndex(context, types);
                if (mode >= 0 && mode < types.size()) {
                    String legacyName = displayName(types.get(mode));
                    if (legacyName != null) {
                        buffer.setCustomName(legacyName);
                    }
                }
            }
            state.observedName = buffer.getCustomName();
            return;
        }
        // ③ 绑定待解析 ⇒ 本 tick 什么都不做，等类型表就绪。
        if (context.boundPending()) {
            state.observedName = current;
            return;
        }
        int mode = indexOfMode(context, types);
        IRecipeLogicMachine controller = context.controller();
        boolean movedToAnotherController = controller != null && state.lastController != null
                && state.lastController != controller;
        state.lastController = controller;
        String key = mode < 0 ? null : modeKey(types.get(mode));
        if (key == null) {
            state.observedName = current;
            return;
        }
        // ④ 玩家改过名 ⇒ 名字永久归玩家：只在名字被清空时把玩家那个名字写回来，绝不改成模式名。
        if (!state.lockedName.isEmpty()) {
            if (current == null || current.isEmpty()) {
                buffer.setCustomName(state.lockedName);
                state.observedName = state.lockedName;
            } else {
                state.observedName = current;
            }
            state.appliedModeKey = key;
            return;
        }
        // ⑤ 目标名字 = 该模式已经记着的名字（延续）；没有 ⇒ 用该模式的显示名并记下来。
        String target = state.modeNames.get(key);
        if (target == null || target.isEmpty()) {
            // 未绑定 + 名字已经有主 + 没换过控制器 ⇒ 一个字都不写（绝不因为主机换模式就改名）。
            if (!context.bound() && current != null && !current.isEmpty()
                    && !movedToAnotherController && !isPlaceholder(current)) {
                state.observedName = current;
                state.appliedModeKey = key;
                return;
            }
            // 通用厂：身份由结构里塞入的机器决定 ⇒ 塞入机器不支持本总成的模式时保持原名。
            MetaMachine inserted = findInsertedMachine(buffer, controller);
            if (inserted != null && !insertedSupportsType(inserted, types.get(mode))) {
                state.observedName = current;
                return;
            }
            String display = displayName(types.get(mode));
            if (display == null) {
                state.observedName = current;
                return;
            }
            target = display;
            state.modeNames.put(key, target);
            buffer.markDirty();
        }
        // ⑥ 幂等写：值已经对了就一个 setter 都不调（也就不会每 tick 刷脏）。
        if (!target.equals(current)) {
            buffer.setCustomName(target);
            state.lastAutomaticName = target;
            buffer.markDirty();
        }
        state.observedName = target;
        state.appliedModeKey = key;
    }

    /** 玩家在界面里确认了改名（{@code AETextInputButtonWidget.setOnConfirm} 的落点）。 */
    public static void onManualRename(MEPatternBufferPartMachineBase buffer, State state, Context context, String name) {
        if (name == null) {
            return;
        }
        if (!buffer.isRemote()) {
            state.lockedName = name;
            state.playerLocked = true;   // 玩家亲手确认 ⇒ 即使与某个模式同名也永久保留
            String key = state.appliedModeKey;
            if (key == null) {
                int mode = indexOfMode(context, context.types());
                key = mode < 0 ? null : modeKey(context.types().get(mode));
            }
            if (key != null) {
                state.modeNames.put(key, name);
            }
            buffer.markDirty();
        }
        buffer.setCustomName(name);
        state.observedName = name;
    }

    /**
     * 外部（归档还原等）把一个<b>玩家指定</b>的名字写进来：记成玩家锁定，别被自动命名覆盖。
     *
     * <p>与 {@link #onManualRename} 的区别：没有界面回调、也不归到某个模式下 —— 只认"这名字归玩家"。
     */
    public static void adoptPlayerName(MEPatternBufferPartMachineBase buffer, State state, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        if (!buffer.isRemote()) {
            state.lockedName = name;
            state.playerLocked = true;
            buffer.markDirty();
        }
        buffer.setCustomName(name);
        state.observedName = name;
    }

    /**
     * 命名用哪一个模式（绑定优先，未绑定才跟随控制器的当前模式）。
     *
     * <p>顺序：① 绑定下标合法 ⇒ 本总成的模式（权威，主机切模式不影响它）；② 控制器的当前模式
     * （= 未绑定时这台总成此刻真正服务的那个）；③ 调用点给的兜底下标；④ 绑定的下标（未绑定也再夹一次）；
     * 全不成立 ⇒ {@code -1}（不命名）。
     */
    private static int indexOfMode(Context context, List<GTRecipeType> types) {
        int size = types.size();
        if (size <= 0) {
            return -1;
        }
        int boundIndex = context.boundIndex();
        if (context.bound() && boundIndex >= 0 && boundIndex < size) {
            return boundIndex;
        }
        IRecipeLogicMachine controller = context.controller();
        if (controller != null) {
            int live = controller.getActiveRecipeType();
            if (live >= 0 && live < size) {
                return live;
            }
        }
        int hint = context.hint();
        if (hint >= 0 && hint < size) {
            return hint;
        }
        if (boundIndex >= 0 && boundIndex < size) {
            return boundIndex;
        }
        return -1;
    }

    /** 总开关关掉时的旧口径：游标优先 → 调用点下标 → 控制器当前模式 → 第 0 个（类型表非空时恒合法）。 */
    private static int legacyIndex(Context context, List<GTRecipeType> types) {
        int size = types.size();
        if (size <= 0) {
            return -1;
        }
        if (context.boundIndex() >= 0 && context.boundIndex() < size) {
            return context.boundIndex();
        }
        if (context.hint() >= 0 && context.hint() < size) {
            return context.hint();
        }
        IRecipeLogicMachine controller = context.controller();
        if (controller != null) {
            int live = controller.getActiveRecipeType();
            if (live >= 0 && live < size) {
                return live;
            }
        }
        return 0;
    }

    /** 模式类型 ⇒ 名字表的键（完整 id）；取不到 id ⇒ {@code null}（调用方放弃本次命名）。 */
    private static String modeKey(GTRecipeType type) {
        return type == null || type.registryName == null ? null : type.registryName.toString();
    }

    /**
     * 一个配方类型 ⇒ 可以写进自定义名的显示名；取不到就返回 {@code null}（调用方保持原名不动）。
     *
     * <p>与"日志用的表名"的唯一区别：日志允许退回路径名，显示名这里也退回路径名（用户口径
     * 「取不到中文就退回英文/原文，不允许因为取不到就整条不生效」），但绝不写空、绝不写 lang 键。
     */
    public static String displayName(GTRecipeType type) {
        if (type == null || type.registryName == null) {
            return null;
        }
        String path = type.registryName.getPath();
        // 覆盖表：只列需要特殊处理的模式；刻意不做"统一去掉末尾的『机』"（会把「线材轧机」变成「线材轧」）。
        if ("circuit_assembly".equals(path) || "circuit_assembler".equals(path)) {
            return "电路组装";
        }
        if ("disposable_tool_printing".equals(path)) {
            // 本模组运行期注册的配方表：不依赖语言文件（服务端命名时译文不一定取得到）。
            return "3D打印：一次性工具";
        }
        try {
            String key = type.registryName.toLanguageKey();
            String translated = Component.translatable(key).getString();
            if (translated != null && !translated.isEmpty() && !translated.equals(key)) {
                return translated;
            }
        } catch (RuntimeException ignored) {
            // 翻译层出问题 ⇒ 退回路径名
        }
        return path.isEmpty() ? null : path;
    }

    /** 这个字符串是不是上游 Enhancedcore 写的兜底串「未命名加工模式」（全等判据，不做模糊匹配）。 */
    public static boolean isPlaceholder(String name) {
        return name != null && UPSTREAM_FALLBACK.equals(name);
    }

    /**
     * 这个名字是不是<b>当前控制器某个模式的显示名</b>（= 自动命名会写出来的名字，不是玩家的选择）。
     *
     * <p>用途有两个：① 不把上游 Enhancedcore 成形时写的"当前模式名"误当成玩家改名；
     * ② 自愈旧版误锁（锁定名恰好是模式名 ⇒ 拆锁重新命名）。
     */
    private static boolean isModeDisplayNameOfHost(String name, List<GTRecipeType> types) {
        if (name == null || name.isEmpty() || types == null) {
            return false;
        }
        for (GTRecipeType type : types) {
            String display = displayName(type);
            if (display != null && !display.isEmpty() && name.equals(display)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 找出通用厂结构里"塞入的那台机器"（通用加工厂/组装厂/分离厂/混合厂靠塞机器定身份）。
     *
     * <p>依据：这四台的结构里有一个 {@code Predicates.blocks(IMachineBlock...)} 的机器方块槽；
     * {@code MultiblockControllerMachine.getMultiblockState()} 的 {@code cache} 字段存着本次已匹配的
     * 全部结构位置 —— 逐位 {@code MetaMachine.getMachine}：不是部件、也不是本总成的那台，就是塞入的机器。
     * 找不到（普通多方块、未成形、扫描异常）⇒ {@code null}（命名退回"只按模式"）。
     */
    private static MetaMachine findInsertedMachine(MEPatternBufferPartMachineBase buffer, IRecipeLogicMachine controller) {
        try {
            if (!(controller instanceof IMultiController multiController)) {
                return null;
            }
            var state = multiController.getMultiblockState();
            if (state == null || state.cache == null || state.world == null) {
                return null;
            }
            var iterator = state.cache.longIterator();
            while (iterator.hasNext()) {
                MetaMachine candidate = MetaMachine.getMachine(state.world, BlockPos.of(iterator.nextLong()));
                if (candidate == null || candidate == buffer) {
                    continue;
                }
                if (candidate instanceof IMultiPart) {
                    continue;   // 部件（仓/总线）跳过
                }
                return candidate;   // 塞入的整台机器
            }
        } catch (Throwable ignored) {
            // 拿不到就当没有：命名退回"只按模式"
        }
        return null;
    }

    /** 塞入的机器是否支持这个配方模式（＝"塞入机器和本总成负责的模式一样"）。 */
    private static boolean insertedSupportsType(MetaMachine machine, GTRecipeType type) {
        if (machine == null || type == null) {
            return false;
        }
        try {
            if (machine instanceof IRecipeLogicMachine logic) {
                for (GTRecipeType candidate : logic.getRecipeTypes()) {
                    if (candidate == type) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 判定失败 ⇒ 视为不一致（宁可不命名，也不乱命名）
        }
        return false;
    }
}
