package com.gtlmodular.mixin.gtceu;

import com.gtlmodular.client.jade.NanochipJadeKeys;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.client.jade.NanochipJadeThreadRows;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ★★★ 2026-10-28 起【独立跨配方线程 · Jade 显示】／★ 2026-11-02 改版（自绘）／★ 本轮改回 ★★★
 *
 * <h2>现在干什么（用户原话的正解）</h2>
 * <blockquote>「那个进度条还是<b>换回我们自己的</b>吧，它<b>只会显示一个进度条</b>，我需要他<b>多线程的时候，
 * 同时显示多个进度条</b>」</blockquote>
 * ⇒ <b>GTCEu 原生那条进度条被取消</b>（它只读 {@code RecipeLogic} 基类的<b>单</b>进度字段
 * ⇒ 天生只反映一条线程），<b>每条线程一组「标签 + 整行宽进度条」由我们自己的 provider 画</b>
 * （{@code NanochipThreadedJadeProvider#appendTooltip} → {@code NanochipJadeThreadRows.appendAllRows}）。
 *
 * <h2>注入点与方式（两个注入点，按开关分流）</h2>
 * <ul>
 *   <li><b>目标</b>：{@code com.gregtechceu.gtceu.integration.jade.provider.WorkableBlockProvider#addTooltip}
 *       —— 真实类，字符串形式（{@code remap = false}；6 参签名与
 *       {@code classstr_gtceu-1.20.1-1.4.4.txt:50715-50716} 逐字一致；该类在 1.4.4 里确实自己声明了它 ——
 *       常量池里有 {@code lambda$addTooltip$1}，同 dump {@code :50928-50936}）。</li>
 *   <li><b>注入点①（默认生效）</b>：{@code @At("HEAD")} + {@code cancellable = true} ——
 *       命中我们自己的机器就 {@code ci.cancel()}，<b>原生条不再画</b>（连带它顺带输出的
 *       {@code Working Disabled} 一类单配方状态行也不画，这是本轮明确的取舍：那些状态由我们自己的
 *       「独立线程：上限 N 条 · 在用 k 条」如实表达，k=0 就是待机）。</li>
 *   <li><b>注入点②（回退形态，默认不生效）</b>：{@code @At("TAIL")} <b>只追加、不取消</b> ——
 *       {@code OUR_OWN_BARS = false} 时回到「原生条当第 0 条 + 其余线程逐行追加」，
 *       追加内容在 {@link NanochipJadeThreadRows#appendAfterNativeBar}（<b>那条路径的代码一行没删</b>）。</li>
 *   <li><b>两个注入点的分工由开关决定，绝不重叠</b>：{@code OUR_OWN_BARS = true} ⇒ 只有①生效；
 *       {@code false} ⇒ 只有②生效（且②还要 {@code NATIVE_BAR_FOR_FIRST_THREAD = true}）——
 *       ⇒ 不可能出现「一边取消、一边又追加」。两者共用同一份渲染（{@code NanochipJadeThreadRows}）。</li>
 *   <li><b>★ 为什么取消必须"可退让"</b>：本条混入<b>只负责让原生条消失</b>，玩家看到的条一律由
 *       provider 自己画 ⇒ 本混入整个失效（没织上 / 判据不命中 / 抛异常）的后果只是
 *       <b>「原生条也还在」</b>（多一条条），<b>绝不会</b>变成「什么进度都没有」。
 *       这正是上一版「原生 + 追加」的教训：那时多条条完全押在混入的注入生效上
 *       （{@code require = 0} ⇒ 失败是静默的），一旦断链就只剩原生一条。</li>
 * </ul>
 *
 * <h2>判据：只作用于我们自己的机器（绝不影响别的模组）</h2>
 * <p><b>门槛只有第 1 道</b>（它不成立就原样交回上游，原生条照旧）；第 2 道是服务端权威的自证，不当门槛：
 * <ol>
 *   <li><b>方块归属（门槛，纯客户端可靠）</b>：方块注册名（{@code BuiltInRegistries.BLOCK} 的 key）
 *       的命名空间 = 当前产物的注册命名空间，且方块实体是 GTCEu 的
 *       {@code MetaMachineBlockEntity}。注册表键是<b>同步数据</b>，与「机器逻辑对象有没有同步到客户端」
 *       无关 ⇒ 判据可靠、不会安静失效。别的模组的方块一律不是
 *       当前产物的命名空间，因而不会被此判据选中。</li>
 *   <li><b>{@code serverData} 标记</b>：Jade {@code serverData} <b>根</b>上存在
 *       {@link NanochipJadeKeys#KEY_THREAD_LIMIT}。这个键<b>只由</b>
 *       {@code NanochipThreadedJadeProvider#appendServerData} 写入，而它只在
 *       {@code getRecipeLogic() instanceof NanochipThreadedRecipeLogic} 时才写 ⇒
 *       键存在 ⟺ 这块方块跑在独立线程逻辑上（而不是旧逻辑 / 关掉开关的机器）。
 *       GTM / GCYM / gtlcore / gtladditions / enhancedcore 永远不会写这个键
 *       （名字带本模组命名空间）。第一帧 serverData 可能还没到 ⇒ 那时<b>不取消</b>
 *       （原生条照常显示，我们那边也不画条），不会闪、也不会错画。</li>
 * </ol>
 *
 * <p>为什么<b>不</b>用客户端 {@code instanceof NanochipThreadedRecipeLogic} 当判据：
 * 机器逻辑对象不保证同步到客户端，客户端那份可能是基类实例 ⇒ 会<b>永远不命中、安静失效</b>
 * （我们踩过「挂错对象导致永不生效」的坑，见 {@code 作者建议_实现路线.md:40}）。
 * 注册表键是可靠证据，所以判据落在它上面。
 *
 * <h2>纪律自证（逐条）</h2>
 * <ol>
 *   <li><b>功能本体先独立可测</b>：显示内容先在机器 UI 里落地
 *       （{@code NanochipAssemblyComplexMachine#addDisplayText} 调
 *       {@code NanochipThreadedRecipeLogic#appendThreadDisplayText}），与混入解耦；
 *       而且<b>本轮之后连 Jade 的多条条也不依赖本混入</b>（provider 自己画）。</li>
 *   <li><b>混入类里所有 {@code static} 字段都是 {@code private}</b>：本类仅有的 6 个静态字段
 *       （{@code diagBudget} / {@code diagNextAt} / {@code DIAG_INTERVAL_MS} / {@code KEY_PROGRESS} /
 *       {@code KEY_MAX_PROGRESS} / {@code OWN_NAMESPACE}）全部 {@code private}
 *       —— 非 {@code private} 的静态字段会让整包混入失败、游戏起不来
 *       （{@code InvalidMixinException: contains non-private static field}，我们连崩三次）。</li>
 *   <li><b>没有 {@code static {}} 初始化块</b>：自证信号是<b>注入方法体内</b>的<b>限频</b>日志。</li>
 *   <li><b>目标是真实类</b>：见上「目标」。</li>
 *   <li><b>{@code require = 0} + {@code remap = false} + {@code try/catch(Throwable)} 全吞</b>：
 *       织不上只静默，绝不崩游戏；异常一律走「<b>不取消</b>」⇒ 退回原生显示。</li>
 *   <li><b>登记进 {@code gtlmodular.mixins.json}</b> 的 {@code client} 段，{@code required} 保持
 *       {@code false}、{@code injectors.defaultRequire} 保持 {@code 0}（本轮<b>没碰</b>该文件）。</li>
 *   <li><b>独立开关（一行回退）</b>：{@link NanochipJadeThreadRows#OUR_OWN_BARS}
 *       （默认 {@code true} = 我们自己画、原生条取消）。置 {@code false} ⇒ 注入点①不取消、
 *       注入点②回到「原生条 + 追加其余线程」——<b>那条路径的代码与文案一字未删</b>。</li>
 * </ol>
 *
 * <h2>放 {@code client} 段的理由（客户端/服务端边界）</h2>
 * <ol>
 *   <li>被注入的 {@code addTooltip} <b>只在客户端</b>拼准星提示时执行（Jade 的方块提示是纯客户端渲染，
 *       服务端只跑 {@code appendServerData}）⇒ 服务端行为<b>逐字不变</b>，不进存档、不下发数据。</li>
 *   <li>「准星提示」整体属于客户端表现层：放 {@code client} 段后<b>专用服务器根本不应用、也不加载</b>
 *       本类（我们有过「客户端专用混入放 {@code mixins} 段」的教训）。</li>
 *   <li>与参考实现一致：GTET 的 {@code MixinWorkableProgressBar} 也放在它的 {@code client} 数组。</li>
 * </ol>
 *
 * <h2>风险与回退（逐字清单）</h2>
 * <ol>
 *   <li>{@link NanochipJadeThreadRows#OUR_OWN_BARS} 置 {@code false}
 *       ⇒ 逐字回到「原生条 + 在其下逐行追加」（= 上一版形态）。</li>
 *   <li>或把本类从 {@code gtlmodular.mixins.json} 的 {@code client} 数组里删掉 ⇒ 原生条不会被取消，
 *       我们的多条条<b>照画</b>（provider 不依赖本混入）——只是提示框里会多一条原生条。</li>
 * </ol>
 */
@Mixin(targets = "com.gregtechceu.gtceu.integration.jade.provider.WorkableBlockProvider", remap = false)
public class MixinWorkableProgressBar {

    /**
     * 诊断日志<b>总量</b>上限（有界，避免刷屏）＋ 时间闸（见 {@link #diagNextAt}）。
     *
     * <p>放在<b>注入方法体内</b>递减使用，<b>不用</b> {@code static {}} 初始化块：
     * 静态初始化不保证执行，会让人误判「没生效」（我们踩过）。
     */
    private static int diagBudget = 3;

    /**
     * 下一条诊断日志最早可以在什么时刻打（{@code System.currentTimeMillis()} 毫秒）。
     *
     * <p>时间闸是限频的<b>主闸</b>：计数闸只保证「一共没几条」，管不住「几条全挤在一秒里」。
     * 两个闸叠起来 ⇒ 开机后最多 3 条、且每条之间至少 30 秒。
     */
    private static long diagNextAt;

    /** 两次 Jade 形态日志（取消 / 追加）之间的最小间隔（毫秒）。 */
    private static final long DIAG_INTERVAL_MS = 30_000L;

    /** 原生 provider 的进度数据键（它在 {@code capData} 里读到才会画那条进度条）。 */
    private static final String KEY_PROGRESS = "Progress";

    /** 见 {@link #KEY_PROGRESS}。 */
    private static final String KEY_MAX_PROGRESS = "MaxProgress";

    /**
     * ★ <b>注入点①（默认生效）</b>：原生 provider <b>一开始就取消</b>它那条单配方进度条
     * （原生条只读基类单进度字段 ⇒ 只能反映一条线程）；条由我们自己的 provider 逐条画。
     *
     * <p><b>为什么把开关判据放在方法体最前面</b>：{@code OUR_OWN_BARS = false}（回退形态）时本条
     * 必须<b>完全不介入</b>（让原生条留下、由注入点②在其下追加）—— 所以第一件事就是读开关。
     *
     * <p><b>失败一律"不取消"</b>：任何异常 / serverData 未到 / 不是我们的机器 ⇒ 直接返回，
     * 原生条照常显示 ⇒ 绝不出现「什么都没显示」。
     */
    @Inject(method = "addTooltip", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void gtlmodular$hideSingleRecipeBar(CompoundTag capData, snownee.jade.api.ITooltip tooltip,
                                                net.minecraft.world.entity.player.Player player,
                                                snownee.jade.api.BlockAccessor accessor,
                                                BlockEntity blockEntity,
                                                snownee.jade.api.config.IPluginConfig config,
                                                CallbackInfo ci) {
        try {
            if (!NanochipJadeThreadRows.OUR_OWN_BARS || accessor == null) {
                return;   // 回退形态（原生条 + 追加）⇒ 原生条必须留下
            }
            if (!isOurThreadedMachine(accessor)) {
                return;
            }
            CompoundTag data = accessor.getServerData();
            if (data == null || !data.contains(NanochipJadeKeys.KEY_THREAD_LIMIT)) {
                return;   // 第一帧 serverData 还没到 / 不是独立线程逻辑 ⇒ 原生条照常显示
            }
            ci.cancel();   // ★ 取消原生条：条由 NanochipThreadedJadeProvider 逐条线程自己画
            diagLog("[gtlmodular] Jade 显示：自绘形态 —— 已取消 GTCEu 原生单配方进度条，"
                    + "多条进度条由 NanochipThreadedJadeProvider 逐条线程画出（原生条已取消={}）", true);
        } catch (Throwable ignored) {
            // ★ 任何异常都【不取消】⇒ 退回原生条：绝不因为显示问题让玩家什么都看不到
        }
    }

    /**
     * ★ <b>注入点②（回退形态，默认不生效）</b>：原生 provider <b>返回前</b>追加其余线程的条
     * （<b>只追加、不取消</b> ⇒ 原生条逐字保留；{@code OUR_OWN_BARS = false} 且
     * {@code NATIVE_BAR_FOR_FIRST_THREAD = true} 时才生效）。
     *
     * <p>签名与 {@code BlockInfoProvider#addTooltip} 的 6 参形态一致
     * （{@code classstr_gtceu-1.20.1-1.4.4.txt:50715-50716}）；{@code require = 0} 让「织不上」静默，
     * 因此生效信号由本方法体内的限频日志提供。
     */
    @Inject(method = "addTooltip", at = @At("TAIL"), require = 0, remap = false)
    private void gtlmodular$appendOtherThreadBars(CompoundTag capData, snownee.jade.api.ITooltip tooltip,
                                                 net.minecraft.world.entity.player.Player player,
                                                 snownee.jade.api.BlockAccessor accessor,
                                                 BlockEntity blockEntity,
                                                 snownee.jade.api.config.IPluginConfig config) {
        try {
            if (NanochipJadeThreadRows.OUR_OWN_BARS
                    || !NanochipJadeThreadRows.NATIVE_BAR_FOR_FIRST_THREAD
                    || tooltip == null || accessor == null) {
                return;   // 自绘形态由 provider 自己画全部条 ⇒ 这里一条都不追加（绝不重复画）
            }
            if (!isOurThreadedMachine(accessor)) {
                return;
            }
            CompoundTag data = accessor.getServerData();
            if (data == null || !data.contains(NanochipJadeKeys.KEY_THREAD_LIMIT)) {
                return;   // 第一帧 serverData 还没到 / 不是独立线程逻辑 ⇒ 什么都不追加
            }
            boolean nativeBarDrawn = hasProgressKeys(capData) || hasProgressKeys(data);
            NanochipJadeThreadRows.appendAfterNativeBar(tooltip, data, nativeBarDrawn);
            diagLog("[gtlmodular] Jade 显示：回退形态 —— 原生条保留，其余线程的条已逐行追加在其下方"
                    + "（原生条是否已画={}）", nativeBarDrawn);
        } catch (Throwable ignored) {
            // 任何异常都不追加 ⇒ 退回"只有原生条 + 两行总览"，绝不因为本条补丁让游戏崩
        }
    }

    /**
     * 混入生效的<b>自证信号</b>：注入方法体<b>内</b>的限频日志（<b>不用</b> {@code static {}} 块
     * —— 静态初始化不保证执行，会让人误判「没生效」，我们踩过）。
     *
     * <p>两个闸叠起来：{@code diagBudget} 限总量（一共几条）、{@code diagNextAt} 限频率
     * （每条之间至少 30 秒）。<b>限频的是"证据"，不是"行为"</b>：时间闸没过只跳过日志，
     * 取消 / 追加该做照做。
     */
    private static void diagLog(String message, boolean nativeBarDrawn) {
        if (diagBudget <= 0) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (nowMs < diagNextAt) {
            return;
        }
        diagBudget--;
        diagNextAt = nowMs + DIAG_INTERVAL_MS;
        try {
            NanochipModContext.logger().info(message, nativeBarDrawn);
        } catch (Throwable ignored) {
            // 日志失败不影响显示行为（取消 / 追加已经做完）
        }
    }

    /** 原生 provider 的进度键是否存在（两个 tag 都看一眼，任一命中即认为它会画条）。 */
    private static boolean hasProgressKeys(CompoundTag tag) {
        return tag != null && (tag.contains(KEY_PROGRESS) || tag.contains(KEY_MAX_PROGRESS));
    }

    /**
     * 这块方块是不是「本模组的、跑独立线程的机器」。
     *
     * <p><b>两道同时成立</b>：
     * <ol>
     *   <li>{@code accessor.getBlockEntity()} 是 GTCEu 的 {@code MetaMachineBlockEntity}
     *       —— 只对 GTCEu 机器族生效（我们的仓 / 模块即使同命名空间也不会走到这一步）；</li>
     *   <li>方块注册名的命名空间是我们（客户端可靠：注册表键是同步数据，与机器逻辑对象无关）。</li>
     * </ol>
     * <p><b>为什么 serverData 里的 {@code KEY_THREAD_LIMIT} 标记不当门槛</b>：Jade 的 serverData
     * 是按需请求的，玩家刚把准星甩过去的那一帧可能<b>还没到</b>（{@code getServerData()} 是空 tag）
     * ⇒ 拿它当门槛会漏掉那一帧。它仍然有用：只由我们的 provider 写入，
     * 是「服务端确实把这块方块当独立线程机器在报数」的权威自证
     * （见 {@link NanochipJadeKeys#KEY_THREAD_LIMIT}）。
     */
    private static boolean isOurThreadedMachine(snownee.jade.api.BlockAccessor accessor) {
        try {
            if (!(accessor.getBlockEntity()
                    instanceof com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity)) {
                return false;
            }
        } catch (Throwable ignored) {
            return false;
        }
        // 命名空间是本模组 ⇒ 处理；别人的方块在上一步/这一步被挡掉，原生进度条原样保留
        return isOwnBlock(accessor);
    }

    /**
     * 方块注册名的命名空间是不是本模组。
     *
     * <p>走 {@code BuiltInRegistries.BLOCK} 的静态查表（<b>不</b>经过 {@code Level#registryAccess()}）：
     * 方块注册表是静态的，查一次 key 只是 map 查表，比每帧解一层维度注册表便宜得多 ——
     * 这个判定每帧都在准星提示的路径上跑。客户端可靠：注册表键在两边一致，与机器逻辑对象无关。
     */
    private static boolean isOwnBlock(snownee.jade.api.BlockAccessor accessor) {
        try {
            BlockPos pos = accessor.getPosition();
            Level level = accessor.getLevel();
            if (pos == null || level == null) {
                return false;
            }
            ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(pos).getBlock());
            return id != null && NanochipModContext.modId().equals(id.getNamespace());
        } catch (Throwable ignored) {
            // 取不到注册名 ⇒ 按「不是我们的方块」处理（保守侧：不取消别人的进度条）
            return false;
        }
    }
}
