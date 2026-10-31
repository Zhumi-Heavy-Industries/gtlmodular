package com.gtlmodular.common.data.host;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern;
import com.gtlmodular.common.data.nanochip.NanochipMachines;
import com.gtlmodular.common.machine.nanochip.NanochipModuleKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.utils.datastructure.ModuleRenderInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <b>「点击显示机器模块」（{@code M:ON}）预览数据的唯一装配点</b> —— 给
 * {@link org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineHost#getModulesForRendering()}
 * 供数据。本类<b>只产出数据，不碰任何机器机制</b>（不写模块集合、不写同步字段、不动结构检查）。
 *
 * <h2>一、为什么需要它（根因）</h2>
 * gtlcore 的 {@code IModularMachineHost.getModulesForRendering()} 是<b>默认方法</b>，
 * 默认实现就是 {@code return Collections.emptyList();}
 * （字节码依据：{@code _enh2_extract/gtlcore_module_api.txt:1419-1422}
 * {@code 0: invokestatic Collections.emptyList / 3: areturn}）。
 * 预览侧的唯一消费点是 {@code PatternPreviewWidget$MBPattern.initializeModules(...)}
 * （同文件 {@code :3230-3246}）：它<b>第一件事</b>就是调 {@code getModulesForRendering()}，
 * 空表直接 {@code return List.of()} ⇒ 点 {@code M:OFF→M:ON} 画面不会有任何变化。
 *
 * <p>我方两台宿主（{@code MoltenModuleHostMachine} / {@code NanochipAssemblyComplexMachine}）
 * 原先都没有覆写它 ⇒ 列表恒空。本类把「真实模块位点」算出来交给那两个覆写点。
 *
 * <h2>二、{@link ModuleRenderInfo} 六个参数的语义（字节码依据，见类内逐处注释）</h2>
 * <table border="1">
 *   <tr><th>参数</th><th>字段</th><th>语义</th></tr>
 *   <tr><td>1</td><td>{@code worldOffset}</td>
 *       <td><b>模块图案原点（= 模块控制器那一格）相对宿主控制器的偏移</b>，用<b>第 2/3 参声明的
 *           宿主参照系</b>表达（我方 = 那台主在<b>实机里的真实朝向</b>；地心枢纽 = {@code NORTH}/{@code UP}）；
 *           预览时按幽灵宿主的实际朝向（= 预览外壳的 NORTH/UP）旋转</td></tr>
 *   <tr><td>2</td><td>{@code hostFront}</td>
 *       <td>「本偏移是在<b>宿主的哪个朝向参照系</b>里量的」。<b>★ 2026-10 修正：不是"必须 NORTH"</b>
 *           —— 必须与本机在<b>世界里的真实正脸</b>一致。我方两台宿主各填"实机/存档里那台主的真实正脸"：
 *           地心枢纽 <b>{@link Direction#NORTH}</b>（★★ 2026-10 实机修正，玩家原话「主机它面朝的是机器的背后」
 *           + 三台模块全 {@code facing=south}；旧值 {@code SOUTH} 就是"模块被摆到楼外"的根因）、
 *           NAC {@link Direction#NORTH}（施工记录 §50 实测，依据见
 *           {@link #MOLTEN_HOST_FRONT} 与下方「第 2/3 参的正确语义」）</td></tr>
 *   <tr><td>3</td><td>{@code hostUp}</td>
 *       <td>同上，取宿主真实「上」；两台宿主都是 {@link Direction#UP}（世界 Y 轴朝上）</td></tr>
 *   <tr><td>4</td><td>{@code moduleFront}</td>
 *       <td>该位点上<b>模块自己的真实正脸</b>（世界方向，与第 1 参的参照系无关；地心枢纽 = {@code SOUTH}）。
 *           <b>逐位点取值不同</b>，不是常量</td></tr>
 *   <tr><td>5</td><td>{@code moduleUp}</td>
 *       <td>模块自己的上方，同上</td></tr>
 *   <tr><td>6</td><td>{@code moduleDefinition}</td>
 *       <td>模块的 {@link MultiblockMachineDefinition}；预览用它取 {@code getMatchingShapes()}
 *           （空则跳过该条并打 WARN）与 {@code BlockInfo}/{@code BlockEntity} 造幽灵机器</td></tr>
 * </table>
 *
 * <h3>第 2/3 参（{@code hostFront}/{@code hostUp}）的正确语义 —— ★ 2026-10 修正</h3>
 *
 * <p><b>结论</b>：这两个字段<b>不是"必须 NORTH/UP"</b>，而是「第 1/4/5 参是在<b>哪个朝向参照系</b>里量的」。
 * gtlcore 的 {@code calculateModuleOffset(hostFront, hostUp)} 会把它们从「本字段声明的参照系」
 * 换算到「预览里那台<b>幽灵宿主</b>的实际朝向」⇒ <b>必须与本机在世界里的真实朝向同一口径</b>：
 * 我方两台宿主都填"<b>实机里那台主的真实正脸</b>"：
 * <ul>
 *   <li>地心枢纽：<b>★★ 2026-10 实机修正 { SOUTH → NORTH }</b> ⇒ 填 {@code NORTH}/{@code UP}。
 *       证据链（三条同向）：①玩家原话「<b>主机它面朝的是机器的背后</b>」；②三台模块实机全
 *       {@code facing=south}（blast/stripper/space，F3 逐台读数）⇒ 主机正脸与其相反 = {@code NORTH}；
 *       ③JEI 预览的幽灵宿主外壳由 {@code BlockPattern.getPreview} 固定按
 *       {@code NORTH}/{@code UP} 铺（{@code BlockPattern.txt:1211-1218}）⇒ {@code NORTH} 才与本预览同口径。
 *       <br>旧值 {@code SOUTH} 的来源是"{@code states_old.txt:212} 投影里那台主的方块状态写死
 *       {@code facing=south}"——那条<b>已被实机否掉</b>（玩家看到的是主机正对模块背面）：投影里那台主的
 *       {@code facing} 只反映"投影作者当时摆的方向"，<b>不等于实机存档里那台主的朝向</b>。
 *       <b>严禁</b>再由投影 {@code facing} 反推 {@code hostFront}。</li>
 *   <li>NAC：现役那台主是 {@code facing=north}（施工记录 §50 实测）⇒ 填 {@code NORTH}/{@code UP}
 *       （★ 与地心枢纽同向；若 NAC 那 12 台在实机里也是整体反向，须按同一口径重新取证 NAC 主机的
 *       {@code facing}，见交付报告 §⑤）。</li>
 * </ul>
 * <p><b>★ 两条"必须"（取代旧注释里"契约必须 NORTH/UP"那段误读）</b>：
 * {@code hostFront} 必须与本机在世界里的真实朝向一致（本机 = {@code NORTH}）；
 * {@code moduleFront} 必须与模块在世界里的真实朝向一致（= {@code SOUTH}）。
 * <b>两者允许互为相反方向</b>——它们各管一件事（偏移量的参照系 / 模块图案的摆放），
 * 旧注释把它们绑成"必须同值"是误读，也是本次 bug 的思想来源。</p>
 *
 * <p><b>反证（gtlcore 生态里的现成用法）</b>：GTLAdditions 的
 * {@code SubspaceCorridorHubIndustrialArray} 硬编码 12 条时第 2 参写的是 <b>{@code EAST}</b>，
 * 不是 {@code NORTH}（{@code _Enhancedcore兼容排查_CTM与模块.md:230-236} 引
 * {@code gtdadd_SubspaceHub.javap.txt:197-218}）⇒ "必须 NORTH" 这条"契约"从来不成立。
 *
 * <p><b>下面这段旧推导保留原文备查</b>：它摘录的字节码（{@code :3244-3315}、{@code :159-204}）
 * 都是真的；错的是由它们推出的两条结论：①"必须填 NORTH/UP"；②在 2026-10 上半轮里被用来把
 * 地心枢纽的 {@code hostFront} 改成 {@code SOUTH}（= 本次实机 bug 的直接根因）。
 * 注意 {@code :3268}/{@code :3271} 真正喂进去的是<b>预览里那台幽灵宿主的真实朝向</b>
 * （{@code getFrontFacing()}/{@code getUpwardsFacing()}），而预览外壳由
 * {@code BlockPattern.getPreview} 固定按 {@code NORTH}/{@code UP} 铺（{@code BlockPattern.txt:1211-1218}）
 * ⇒ 幽灵宿主的实际朝向就是 {@code NORTH}/{@code UP}，故声明 {@code NORTH} 才对齐。
 * <p><b>旧推导原文（备查，勿再据此改代码）</b>：
 *
 * <p>{@code initializeModules} 读的是<b>幽灵宿主机器实例</b>的朝向：
 * <pre>
 *   :3244  getstatic Direction.NORTH → astore 7      // 默认 hostFront = NORTH
 *   :3246  getstatic Direction.UP    → astore 8      // 默认 hostUp    = UP
 *   :3250  LEVEL.getBlockEntity(hostPos) instanceof IMachineBlockEntity
 *   :3268    MultiblockControllerMachine.getFrontFacing()   → astore 7
 *   :3271    MultiblockControllerMachine.getUpwardsFacing() → astore 8
 * </pre>
 * 随后对每一条：
 * <pre>
 *   :3301  info.calculateModuleOffset(hostFront, hostUp)      // 本字段参照系 → 幽灵宿主真实朝向
 *   :3305  hostPos.offset(那个偏移)                            // ⇒ 得到模块控制器的世界坐标
 *   :3310  info.calculateModuleFront(hostFront, hostUp)
 *   :3315  info.calculateModuleUp(hostFront, hostUp)
 * </pre>
 * 而 {@code calculateModuleOffset} 的<b>第一个分支</b>（{@code ModuleRenderInfo} 同文件 {@code :159-204}）：
 * <pre>
 *   :161  if (hostFront == this.hostFront &amp;&amp; hostUp == this.hostUp) return this.worldOffset;
 * </pre>
 * ⇒ 这一段只说明「第一个分支」的短路条件。<b>★ 不能</b>由此推出"必须填 NORTH/UP"：填别的值时
 * 走的是后面的通用旋转链（{@code reverseHorizontalRotation → applyHorizontalRotation →
 * rotateByFrontAxis}），照样能把「本字段参照系」换算到幽灵宿主的朝向。
 * 真正的要求是：<b>{@code hostFront}/{@code hostUp} 必须与 {@code worldOffset} 的测量口径一致</b>
 * —— 即"这些数字是在哪种朝向的世界里量的"。
 * （另一个 3 参构造器 {@code :123-133} 与此 6 参构造器<b>完全等价</b>：
 * 它把 {@code NORTH}/{@code UP} 直接喂给第 2/3 参、把两个方向参数复制给第 4/5 参。
 * 本类用 6 参重载，把四个方向都显式写出来。）
 *
 * <h3>第 4/5 参（{@code moduleFront}/{@code moduleUp}）的填法 —— ★ 2026-10 修正</h3>
 *
 * <p><b>结论</b>：填<b>该位点上模块自己的真实正脸</b>（世界方向——与第 2/3 参描述的"主机真实正脸"
 * 各管一件事，<b>允许与主机相反</b>，地心枢纽就是 主机 {@code NORTH} / 模块 {@code SOUTH}）。
 * <b>它不是常量</b>：
 * <ul>
 *   <li>地心枢纽三个模块<b>实机</b>全是 {@code facing=south}（★ 2026-10 F3 逐台实测；投影
 *       {@code states_old.txt:46,168,207} 同值）⇒ 三条都填 {@link Direction#SOUTH}（本次<b>未改</b>）；</li>
 *   <li>NAC 的 12 条<b>逐位点不同</b>：控制器落在基座哪条边、就朝哪边"脸朝外"
 *       （−z 边中点 → {@code NORTH}、+z → {@code SOUTH}、−x → {@code WEST}、+x → {@code EAST}）——
 *       依据 {@code _census/expect_table.md:39} + {@code _census/module_base_vs_host.md:11-61}
 *       的 48/48「模块底座逐格对齐主机基座」穷举，见
 *       {@link NanochipComplexPattern#moduleControllerOffsets()}。</li>
 * </ul>
 * <p>{@code moduleUp} 两台宿主都是 {@link Direction#UP}。
 *
 * <p><b>下面这段旧推导保留原文备查</b>：它论证的是"幽灵宿主参照系下的模块正脸摆放"，
 * 方向推得没错，但漏了"宿主自己的真实朝向"这一层，于是把 {@code moduleFront} 误当成恒 {@code NORTH}。
 *
 * <p>{@code calculateModuleFront} 最终调 {@code convertDirection}（{@code :226-280}），
 * 在 {@code hostFront==NORTH &amp;&amp; hostUp==UP} 时它<b>原样返回</b>第 3 个实参。
 * 于是真正决定朝向的是 {@code initializeModule} 往 dummy world 里放格子的方式
 * （{@code :3462-3474}）：
 * <pre>
 *   :260  local = 形状下标(i, row, j) - 控制器下标        // i=第 3 维、j=第 2 维（见 :3391-3396 构造 BlockPos(i,row,j)）
 *   :272  offset = ModuleRenderInfo.applyGTTransform(local, moduleFront, moduleUp)
 *   :280  world  = 模块原点 + offset
 * </pre>
 * 也就是说：<b>模块图案的内部坐标轴由 {@code applyGTTransform} 按我们给的
 * {@code moduleFront/moduleUp} 摆放</b>（{@code NORTH} = 原样、{@code SOUTH} = 转 180°、
 * {@code EAST/WEST} = 各转 90°，见 {@code applyGTTransform} 字节码 {@code :753-827}）。
 * 因此 {@code moduleFront} 必须填「该位点上模块在<b>真实世界</b>里的正脸」——
 * 地心枢纽三条是 {@code SOUTH}（投影实测）、NAC 十二台按"控制器坐哪条边就朝哪边"逐条不同；
 * {@code moduleUp} 两台宿主都是 {@code UP}。
 * （旧注释在这里断言"我方模块图案都在 facing=north 那一套、恒填 NORTH"，那是把
 * {@code ModuleRenderInfo} 的参照系语义与图案自身的编排混为一谈；投影实测已否掉它。）
 *
 * <h2>三、数据来源（不许编坐标）</h2>
 * <ul>
 *   <li><b>NAC</b>：⭐ 2026-10 修正 —— 已经改成<b>写死的实测表</b>，见
 *       {@link NanochipComplexPattern#moduleControllerOffsets()}（= 该类的
 *       {@code NAC_MODULE_PREVIEW_SLOTS}）。表里 12 条的来源 = <b>玩家存档实测</b>
 *       {@code _census/expect_table.md} §1 的「4 个 F 位(偏移)」列，
 *       并以「模块底座 7×3×7 与宿主基座 7×7 逐格对齐」的 48/48 穷举选出每基座唯一正确的那一条
 *       （{@code _census/module_base_vs_host.md}）。
 *       <br><b>为什么不再"从图案现算"</b>：旧实现那套「一个 F 格的 4 个轴向 ±3 邻居也都是 F ⇒
 *       它就是基座中心」的运行期判据在现役图案上<b>恒命中 0 个</b>（基座中心那格在 {@code row=1}
 *       且是 {@code 'C'}、4 个 F 位在 {@code row=2}），每次都抛
 *       {@code IllegalStateException: 基座中心判据命中 0 个（期望 12）} ⇒ 恒返回空表
 *       ⇒ 点 {@code M:ON} 无反应。该反推已停用（{@code @Deprecated} 死代码，禁止再调用）；
 *       写死的表是常量 ⇒ <b>恒 12 条、不会失败</b>。</li>
 *   <li><b>地心枢纽</b>：三个模块控制器坐标取自<b>现役投影 {@code 地心涌动能量转换站.litematic}
 *       的解码转储</b> {@code gtl-dev/api-dump/dump_full.txt}（该文件头 {@code REGION Unnamed
 *       size=97,-48,99} 与现役 {@code HostPatternLayers} 的 {@code X=97,Y=48,Z=99} 逐一吻合，
 *       且其坐标索引口径与 {@code HostPatternLayers} 的生成器 {@code HostFromLitematic} 完全一致）。
 *       三个坐标与「宿主控制器 (48,8,8)」的映射关系已用两处独立证据交叉核对，见
 *       {@link #MOLTEN_MODULE_CONTROLLERS}。</li>
 * </ul>
 *
 * <h2>四、性能与失效（为什么不会每帧重算，为什么有界）</h2>
 * <ul>
 *   <li>预览确实会<b>反复</b>调用（每开一次界面 / 每建一次 {@code MBPattern}）：每次调用只做
 *       「一次 {@code BlockPos} 相等比较」+ 返回缓存引用；</li>
 *   <li>每台宿主<b>至多缓存一份不可变列表</b>（{@code volatile} 字段 + 双检锁），
 *       缓存键 = 宿主自身坐标（控制器被拆/换位置 ⇒ 键变化 ⇒ 自动重算，旧列表被替换、不累积）；</li>
 *   <li>NAC 那张表与机器实例无关（只读图案常量），所以放<b>静态</b>缓存，全局一份（≤ 48 条）；</li>
 *   <li>诊断日志走「每台宿主只成功打一次」的<b>硬上限</b>集合（容量 {@value #MAX_LOGGED_KEYS}，
 *       满了只按次数抽样），不存在无界增长。</li>
 * </ul>
 *
 * <h2>五、失败姿态</h2>
 * 全程 {@code try/catch (Throwable)}：拿不到定义 / 图案异常 / 未成形 / 任何意外
 * ⇒ <b>返回空表</b>（等于回到 gtlcore 的默认行为，最多"点了没模块"，绝不外抛打断预览或服务端）。
 **/
public final class ModulePreviewSlots {

    private ModulePreviewSlots() {
    }

    // ------------------------------------------------------------------
    // 公共入口
    // ------------------------------------------------------------------

    /**
     * 地心涌动能量转换枢纽（{@code MoltenModuleHostMachine}）的模块预览列表。
     *
     * @param hostPos 宿主控制器坐标（{@code null} 时返回空表）
     * @param cache   宿主自己的缓存槽（宿主类里一个 {@code final} 的 {@link Cache} 实例）
     */
    public static List<ModuleRenderInfo> moltenHostModules(BlockPos hostPos, Cache cache) {
        if (hostPos == null || cache == null) {
            return List.of();
        }
        List<ModuleRenderInfo> cached = cache.get(hostPos);
        if (cached != null) {
            return cached;
        }
        List<ModuleRenderInfo> built;
        try {
            built = buildMoltenHost();
        } catch (Throwable t) {
            GTLModular.LOGGER.debug("[gtlmodular] 模块预览: 地心枢纽模块表构建失败，返回空表: {}", t.toString());
            built = List.of();
        }
        cache.put(hostPos, built);
        logOnce("molten@" + hostPos.asLong(), "地心涌动能量转换枢纽", hostPos, built);
        return built;
    }

    /**
     * 纳米芯片组装复合体（{@code NanochipAssemblyComplexMachine}）的模块预览列表。
     *
     * <p>与 {@link #moltenHostModules} 同款缓存纪律，但数据表是<b>静态</b>的
     * （只依赖图案常量，与具体机器实例无关）⇒ 全局只算一次。
     *
     * @param hostPos 宿主控制器坐标（只用于日志；{@code null} 时返回空表）
     */
    public static List<ModuleRenderInfo> nacModules(BlockPos hostPos) {
        if (hostPos == null) {
            return List.of();
        }
        List<ModuleRenderInfo> cached = NAC_MODULES;
        if (cached == null) {
            synchronized (ModulePreviewSlots.class) {
                if (NAC_MODULES == null) {
                    List<ModuleRenderInfo> built;
                    try {
                        built = buildNac();
                    } catch (Throwable t) {
                        GTLModular.LOGGER.debug("[gtlmodular] 模块预览: NAC 模块表构建失败，返回空表: {}", t.toString());
                        built = List.of();
                    }
                    NAC_MODULES = built;
                    cached = built;
                } else {
                    cached = NAC_MODULES;
                }
            }
        }
        logOnce("nac@" + hostPos.asLong(), "纳米芯片组装复合体", hostPos, cached);
        return cached;
    }

    // ------------------------------------------------------------------
    // 每台宿主一份的有界缓存
    // ------------------------------------------------------------------

    /** 每台宿主至多一份不可变列表；键 = 宿主坐标，变了就整体替换（不累积）。 */
    public static final class Cache {
        private volatile BlockPos key;
        private volatile List<ModuleRenderInfo> value;

        /** 命中返回列表，未命中返回 {@code null}。 */
        public List<ModuleRenderInfo> get(BlockPos hostPos) {
            List<ModuleRenderInfo> v = value;
            return v != null && hostPos.equals(key) ? v : null;
        }

        /** 幂等写入（同一键重复写同一份列表，无副作用）。 */
        public void put(BlockPos hostPos, List<ModuleRenderInfo> list) {
            this.key = hostPos.immutable();
            this.value = list;
        }
    }

    // ------------------------------------------------------------------
    // NAC：从图案现算
    // ------------------------------------------------------------------

    /** NAC 模块表（只依赖图案常量 ⇒ 静态缓存一份；容量 ≤ 48）。 */
    private static volatile List<ModuleRenderInfo> NAC_MODULES;

    private static List<ModuleRenderInfo> buildNac() {
        List<NanochipComplexPattern.ModuleControllerSlot> slots =
                NanochipComplexPattern.moduleControllerOffsets();
        if (slots.isEmpty()) {
            // ★ 2026-10：写死的实测表是常量 ⇒ 这里**正常永远不会命中**（旧版"运行时反推失败"
            //   才会走到这条）。保留它纯属防御（万一有人把 moduleControllerOffsets() 改坏）。
            return List.of();
        }
        // ★ 2026-10：**保证条数 = 位点数（12）**。原实现遇到"该模块种类没注册"就 continue，
        //   于是 cfg/加载顺序一变就会少几条（队长看到的"12 个基座只显示 9 台"那类问题）。
        //   现在改成"该种类拿不到就退到任意一个已注册的 NAC 模块"——预览只要摆得出塔身即可，
        //   模块种类的对应关系不影响"位点是否正确"这一验收点。
        //   （模块 id 由 NanochipComplexPattern.moduleControllerOffsets() 按位点轮转
        //    NanochipModuleKind.values() 合成；某个 kind 没注册 ⇒ nacDefinition 返回 null ⇒ 退到 anyDef。）
        MultiblockMachineDefinition anyDef = firstNacDefinition();
        if (anyDef == null) {
            return List.of();
        }
        List<ModuleRenderInfo> out = new ArrayList<>(slots.size());
        for (NanochipComplexPattern.ModuleControllerSlot slot : slots) {
            MultiblockMachineDefinition def = nacDefinition(slot.moduleId());
            if (def == null) {
                def = anyDef;
            }
            // 第 2/3 参 = 宿主真实朝向参照系：现役 NAC 那台主是 facing=north（施工记录 §50 实测）
            //   ⇒ NORTH/UP（与 slot.offset() 的来源口径一致，见类注释「第 2/3 参的正确语义」）。
            // 第 4 参 slot.front()：模块控制器坐在自己基座「朝外」那条边的边中点上，
            //   GTCEu 要求该位点上模块正脸朝外 ⇒ 12 条里四个朝向都可能出现（不是恒 NORTH），
            //   依据 _census/expect_table.md:39 与 _census/module_base_vs_host.md:11-61 的 48/48 穷举。
            out.add(new ModuleRenderInfo(slot.offset(), Direction.NORTH, Direction.UP,
                    slot.front(), Direction.UP, def));
        }
        return List.copyOf(out);
    }

    /** 任意一个已注册的 NAC 模块定义（{@link #buildNac()} 的条数兜底用）。 */
    private static MultiblockMachineDefinition firstNacDefinition() {
        for (NanochipModuleKind kind : NanochipModuleKind.values()) {
            MultiblockMachineDefinition def = NanochipMachines.NANOCHIP_MODULES.get(kind);
            if (def != null) {
                return def;
            }
        }
        return null;
    }

    /**
     * 由 {@code NANOCHIP_MODULES} 的 id 反查定义。
     *
     * <p>先查已注册表（{@link NanochipMachines#NANOCHIP_MODULES}，注册窗口结束后必然已填），
     * 查不到再走 GTCEu 注册表兜底；两条都拿不到返回 {@code null}（调用方跳过该条）。
     */
    private static MultiblockMachineDefinition nacDefinition(String moduleId) {
        if (moduleId == null) {
            return null;
        }
        for (NanochipModuleKind kind : NanochipModuleKind.values()) {
            if (moduleId.equals(kind.machineId())) {
                MultiblockMachineDefinition def = NanochipMachines.NANOCHIP_MODULES.get(kind);
                if (def != null) {
                    return def;
                }
                break;
            }
        }
        return lookupDefinition("gtlmodular", moduleId);
    }

    // ------------------------------------------------------------------
    // 地心枢纽：三个模块控制器的实坐标（投影解码，非手抄）
    // ------------------------------------------------------------------

    /**
     * 三个模块控制器在<b>宿主图案坐标系</b>里的实坐标（单位 = 格）。
     *
     * <h4>坐标是怎么来的（不许编坐标 —— 逐条给依据）</h4>
     * 1. <b>源</b>：{@code gtl-dev/api-dump/dump_full.txt}（现役投影
     *    {@code ...\schematics\地心涌动能量转换站.litematic} 的逐格解码转储）。
     *    该文件头现读为 {@code REGION Unnamed size=97,-48,99 pos=0,47,0 palette=208}
     *    ⇒ 与现役 {@code HostPatternLayers}（{@code X=97, Y=48, Z=99}）同为一份数据、同一索引口径。
     *    三条 {@code BLOCK} 记录：
     *    <pre>
     *      dump_full.txt:43885  BLOCK gtlmodular:molten_blast_module      x=70 y=4  z=55
     *      dump_full.txt:43841  BLOCK gtlmodular:molten_stripper_module  x=26 y=4  z=55
     *      dump_full.txt:217008 BLOCK gtlmodular:molten_space_ore_module x=48 y=22 z=58
     *    </pre>
     * 2. <b>索引口径（★★ 2026-10 修正：<u>任何轴都不要再做 row/y 换算</u>）</b>：
     *    {@code HostPatternLayers} 由 {@code gtl-dev/api-dump/HostFromLitematic.java} 生成：
     *    {@code grid[zz][yy][xx]} 直取、按 {@code y=0..47} 顺序逐行拼进 {@code RAW_zzz}，
     *    而 {@code MoltenModuleHostPattern.build()} 把 {@code RAW_zzz.split("\n")} 原样喂给
     *    {@code FactoryBlockPattern.aisle(...)}。<b>GTCEu 的 aisle 第 0 个字符串 = 最底层</b>
     *    （{@code NAC_进游戏验证清单.md} §11.2 原话：「GTCEu 的行序实际是<b>自下而上</b>（第 0 个字符串 =
     *    最底层）」，同处还列了本工程第三个生成器 {@code SpaceOrePatternLayers} 的约定
     *    {@code row = y（第 0 行最底，+Y 方向）} 作为旁证）
     *    ⇒ <b>投影索引 {@code y} 本身就是"自底向上"的行号，{@code y} 越大越高</b>；
     *    它和 {@code x}(+X)、{@code z}(+Z) 一起构成与现役图案<b>逐轴同向</b>的坐标系。
     *    <br>旧注释里那条「{@code patternRow = 47 − y}」是<b>错</b>的（把本来就向上的 y 又翻了一次），
     *    正是"模块跑到天上"的根因：{@code y=4} 被换算成 {@code row=43}，比控制器（{@code y=8}）高 39 格。
     *    <br>★ <b>再补一条独立硬证据（朝向自洽）</b>：现役图案 = 投影原样索引，而 GTCEu 只有
     *    {@code facing=south} 才是"原样铺"（{@code facing=north} = 绕控制器转 180°）⇒
     *    能成形的那台主<b>必定</b>是 {@code facing=south}；投影里那台主正是
     *    {@code gtlmodular:molten_module_host[…,facing=south]}
     *    （{@code states_old.txt:212}，与现役 {@code HostPatternLayers} 同源：同一 {@code REGION
     *    size=97,-48,99 pos=0,47,0 palette=208} 头）⇒ <b>投影索引差 = 世界坐标差</b>（★ 2026-10：此句仅供旧口径参考 —— <u>不成立</u>，见第 7 条实机偏差），无需任何换算。
     * 3. <b>结果</b>（★ <b>2026-10 实机 F3 实测，权威</b>）：表内<b>直接就是"模块相对主机的偏移"</b>，即实测的
     *    <b>世界坐标 − 主机世界坐标 = (22, 2, 51) / (−22, 2, 51) / (0, 20, 54)</b>（三台逐条对照见第 7 条），
     *    真正在 <b>构建时不需要任何减法</b>（{@link #buildMoltenHost()} 直接取用；旧口径"投影绝对坐标 − 主机基准 (48,8,8)"
     *    已作废 —— 那条基准算出的偏移三台一致地少给 (0, +6, +4)，正是预览里模块飘在主体外的根因）。
     *    <b>下面这段投影推算过程保留原文备查</b>（它算出的数值已全部作废，<b>不要拿它去改数组</b>）：
     *    <pre>
     *      旧 blast    : (70−48,  4−8, 55−8) = (  22, −4, 47)
     *      旧 stripper : (26−48,  4−8, 55−8) = ( −22, −4, 47)
     *      旧 space    : (48−48, 22−8, 58−8) = (   0, 14, 50)
     *    </pre>
     *    <br>★ <b>★★ 2026-10 实机修正：第 2 参（{@code hostFront}）必须填 {@code NORTH}，不是 {@code SOUTH}</b>
     *    —— 这一处就是"模块被摆到楼外"的根因（旧值 {@code SOUTH} 把整套偏移按 180° 整体换算）。
     *    证据链（三条同向，详见 {@link #MOLTEN_HOST_FRONT}）：
     *    <ol>
     *      <li><b>玩家实机原话</b>：「<b>主机它面朝的是机器的背后</b>」；</li>
     *      <li>三台模块实机<b>全部</b> {@code facing=south}（blast/stripper/space，F3 逐台读数，见第 7 条）
     *          ⇒ 模块朝南、主机正对模块背面 ⇒ <b>主机真实正脸 = {@code NORTH}</b>；</li>
     *      <li>JEI 预览的幽灵宿主外壳由 {@code BlockPattern.getPreview(...)} <b>固定</b>按
     *          {@code setActualRelativeOffset(palm, thumb, aisle, Direction.NORTH, Direction.UP, false)}
     *          铺格子（{@code gtl-dev/api-dump/BlockPattern.txt:1211-1218}）⇒ 幽灵宿主的实际朝向是
     *          {@code NORTH}/{@code UP} ⇒ <b>声明 {@code NORTH} 才与本预览的参照系同口径</b>。</li>
     *    </ol>
     *    <b>数值与参照系必须成对自洽</b>：三个偏移量 = 实机世界坐标差（第 3 条 / 第 7 条），
     *    配合 {@code hostFront = NORTH}（现值）成立；若换成 {@code hostFront = SOUTH}，
     *    则偏移 x/z 必须同时取负，否则 {@code calculateModuleOffset} 会把整套偏移整体转 180°
     *    ⇒ 模块落到楼的另一侧 / 外面（= 本次 0.3.18 实机现象）。<b>不允许只改一半</b>
     *    （回退法见第 6 条，两处必须成对改）。
     *    <br>旧注释在这里写的是"偏移 + hostFront=SOUTH 两边正好对上"——它的推理前提
     *    "画出来的外壳 = 图案整体转 180° 那一份 / 主机必定是 south"已被实机否掉：实机那台主的真实正脸是
     *    {@code NORTH}（玩家原话 + 模块全 south）。第 7 条"投影基准 (48,8,8) 不可靠"的结论<b>继续有效、未被本次修正推翻</b>。
     * 4. <b>交叉核对（模块几何自洽）</b>：本模组模块图案里控制器在局部下标
     *    {@code (MOD_CTRL_X, MOD_CTRL_ROW, MOD_CTRL_Z) = (3, 2, 6)}，底座环圆心在局部 {@code (3, ·, 3)}
     *    ⇒ <b>底座中心 = 控制器朝 {@code −z} 方向退 3 格</b>（模块图案常量 {@code MOD_D = 7}）。
     *    于是三台的底座中心落在 {@code (70,4,52)} / {@code (26,4,52)} / {@code (48,22,55)}（★ 2026-10：这三个是旧投影位点，已作废；实测底座中心见第 7 条），
     *    即"控制器各自坐在自己基座朝外那条边上"——与 {@code NanochipComplexPattern} 的
     *    {@code moduleControllerOffsets()}（★ 2026-10 起 = 写死的实测表，同样按
     *    {@code expect_table.md}:39 的"坐哪条边就朝哪边"配对）用的是同一条几何判据（两台宿主同构）。
     * 5. <b>第 4/5 参（{@code moduleFront}/{@code moduleUp}）的依据 —— ★ 2026-10 修正</b>：
     *    投影里三个模块方块的状态都是 {@code facing=south}
     *    （{@code states_old.txt:46} 烧矿机 / {@code :168} 炼矿机 / {@code :207} 挖矿机，
     *    三行原文 {@code gtlmodular:molten_*_module[upwards_facing=north,server_tick=false,facing=south]}），
     *    与实机一致 ⇒ 三条都填 {@code SOUTH}/{@code UP}；★ 2026-10 实机 F3 实测三台模块同样全是 {@code facing=south} ⇒ 本条<b>维持不改</b>
     *    （即 {@link #MOLTEN_MODULE_FRONT}）。
     *    <br>旧注释把这里写成"恒 {@code NORTH}"，是把"幽灵宿主参照系"和"宿主真实朝向"混为一谈。
     *    与本次修正的关系（一句话）：{@code moduleFront} = 模块自己的真实朝向（{@code SOUTH}），
     *    {@code hostFront} = 主机自己的真实朝向（{@code NORTH}）——<b>两者本来就可以相反</b>；
     *    旧版把"偏移的参照系"错填成 {@code SOUTH} 才是"模块被摆到楼外"的病根，
     *    <b>本次只改 {@code hostFront} 这一处</b>，{@code moduleFront} 维持 {@code SOUTH} 不动。
     * 6. <b>未取证（如实登记）</b>：这一版<b>没有</b>编译、也<b>没有</b>实机跑过（沙箱 ACL 故障，本会话只能读写源码）；
     *    三条世界坐标与朝向来自玩家实机 F3 逐台读数（第 7 条），参照系/朝向的推导来自 GTCEu 字节码
     *    （{@code BlockPattern.getPreview} 的 NORTH/UP、{@code ModuleRenderInfo.calculateModuleOffset} 的旋转链），
     *    真伪最终由队长的实机存档裁定。已知可交叉验证的字段（玩家已提供）：主机世界 {@code (-218,66,375)} +
     *    三台模块世界坐标与 {@code facing=south}；<b>仍缺</b>的一项：主机自身的方块状态 {@code facing}
     *    （玩家语言描述为"面朝机器的背后" ⇒ 已据此认定为 {@code NORTH}）——见交付报告 §⑤。
     *    <br><b>★ 回退说明（本次认领的回退法；只在"改成 {@code NORTH} 后实机看模块仍在外侧"时才用）</b>：
     *    把 {@link #MOLTEN_HOST_FRONT} 改回 {@link Direction#SOUTH}，<b>同时</b>把
     *    {@link #buildMoltenHost()} 里 {@code offset} 的 <b>x、z 两项取负</b>
     *    （该行代码下方有注释写明位置）—— 即 {@code SOUTH} + {@code (−22,2,−51)/(22,2,−51)/(0,20,−54)}。
     *    —— 这两件事<b>必须同时做</b>：只改一半会把模块摆到 180° 对面去（= 本次要修的同一个病）。
     *    <br><b>为什么是"取负"而不是别的</b>：{@code calculateModuleOffset} 在"声明参照系 → 幽灵宿主朝向"
     *    之间只做水平 180° / ±90° 换算（{@code reverseHorizontalRotation → applyHorizontalRotation →
     *    rotateByFrontAxis}，判据见类注释），而 180° 恰是 {@code (x, z) -> (−x, −z)}。
     *    <br>{@link #MOLTEN_MODULE_FRONT} <b>不要</b>跟着改（实机三台模块就是 {@code facing=south}）。
     * 7. <b>★★ 2026-10 实机 F3 实测修正（权威，覆盖第 3 条的投影推算值）</b>：玩家进游戏后按 F3 逐台读到的<b>世界坐标</b>：
     *    <pre>
     *      主机   gtlmodular:molten_module_host     世界 (-218, 66, 375)
     *      blast  gtlmodular:molten_blast_module    世界 (-196, 68, 426)  facing=south upwards_facing=north
     *      stripper gtlmodular:molten_stripper_module 世界 (-240, 68, 426)  facing=south upwards_facing=north
     *      space  gtlmodular:molten_space_ore_module 世界 (-218, 86, 429)  facing=south upwards_facing=north
     *    </pre>
     *    ⇒ <b>真实相对偏移（世界坐标 − 主机）</b>：blst <b>(+22, +2, +51)</b> / stripper <b>(−22, +2, +51)</b> /
     *    space <b>(0, +20, +54)</b>；三台<b>一致</b>比旧"投影基准 (48,8,8)"推算值偏 <b>(0, +6, +4)</b>
     *    ⇒ <b>偏差证明投影基准 (48,8,8) 不可靠</b>（y 一项差 +6、z 一项差 +4，三台同向 ⇒ 不是单台测量误差），
     *    故 {@link #MOLTEN_MODULE_CONTROLLERS} <b>改为直接存这组实测偏移</b>，
     *    {@link #buildMoltenHost()} <b>不再做任何减法</b>（旧值 blast(22,−4,47) / stripper(−22,−4,47) /
     *    space(0,14,50) 及第 3 条那台 (70,4,55)/(26,4,55)/(48,22,58) 全部作废，<b>不要改回投影值</b>）。
     *    朝向：实机三台模块与旧投影结论<b>一致</b>（全 {@code facing=south}）⇒ {@link #MOLTEN_MODULE_FRONT}
     *    维持 {@code SOUTH} 未动；而<b>主机真实正脸 = {@code NORTH}</b>（玩家原话「主机它面朝的是机器的背后」）
     *    ⇒ {@link #MOLTEN_HOST_FRONT} 由 {@code SOUTH} 修正为 {@code NORTH}（★ 本次唯一的语义改动，
     *    见新增的第 3 条修正段与 {@link #MOLTEN_HOST_FRONT} 的完整证据链）。
     *    <b>三个偏移数值一个字都没有动</b>：它们就是第 7 条这组世界坐标差。
     */
    private static final int[][] MOLTEN_MODULE_CONTROLLERS = {
            // ★★ {实机 F3 实测偏移：模块控制器 x, y, z} = 模块世界坐标 − 主机世界坐标 —— 定义由 MOLTEN_MODULE_IDS 同序给出
            // ★★ 权威值：主机 gtlmodular:molten_module_host 世界坐标 = (-218, 66, 375)；三台模块全 facing=south / upwards_facing=north。
            // ★★ 旧"投影基准 (48,8,8)"推算值系统性偏 (0,+6,+4) ⇒ 已作废，**不要再用投影坐标反推**：
            //     旧 blast(22,-4,47) vs 实测(22,2,51)；旧 stripper(-22,-4,47) vs 实测(-22,2,51)；旧 space(0,14,50) vs 实测(0,20,54)。
            // ★ 逐字取自 dump_full.txt（= 现役投影 states_old 的同一 REGION）：
            //   :43885  molten_blast_module      x=70 y=4  z=55
            //   :43841  molten_stripper_module  x=26 y=4  z=55
            //   :217008 molten_space_ore_module x=48 y=22 z=58
            // ★ 这三轴的 y 是"自底向上"的行号（见上面 javadoc 第 2 条），**不要再减 47**。
            {22, 2, 51},    // 猪咪愤怒烧矿机（= 实机 (-196,68,426) − 主机 (-218,66,375)）  molten_blast_module
            {-22, 2, 51},   // 猪咪黑奴挖矿机（= 实机 (-240,68,426) − 主机 (-218,66,375)）  molten_stripper_module
            {0, 20, 54},    // 猪咪撕咬炼矿机（= 实机 (-218,86,429) − 主机 (-218,66,375)）  molten_space_ore_module
    };

    /** 与 {@link #MOLTEN_MODULE_CONTROLLERS} <b>同序</b>的模块定义 id（本模组自己的注册 id）。 */
    private static final String[] MOLTEN_MODULE_IDS = {
            "molten_blast_module",
            "molten_stripper_module",
            "molten_space_ore_module",
    };

    /** 宿主控制器在图案/投影坐标系里的位置（现役 {@code HostPatternLayers} 文件头现读）。 */
    // ★★ 2026-10：投影主机位点 (48,8,8) —— 仅作历史存档，**已不参与偏移推导**：
    //    实机 F3 实测主机世界坐标 = (-218,66,375)，该投影基准推算结果系统性偏 (0,+6,+4)（详见下方 javadoc 第 7 条）
    //    ⇒ 现由 MOLTEN_MODULE_CONTROLLERS 直接存 F3 实测偏移，本字段已不被代码使用（仅作历史存档 / javadoc 引用）。
    @SuppressWarnings("unused")
    private static final BlockPos MOLTEN_HOST_CONTROLLER = new BlockPos(48, 8, 8);

    /**
     * 地心枢纽<b>宿主</b>在真实世界里的正脸 ⇒ {@code ModuleRenderInfo} 第 2 参（{@code hostFront}）。
     * <b>★★ 2026-10 实机修正：{@code SOUTH} → {@code NORTH}</b>（这就是"模块被摆到楼外"的那一处）。
     *
     * <p><b>为什么是 {@code NORTH}（三条证据，同向指认）</b>：
     * <ol>
     *   <li><b>玩家实机原话</b>：「<b>主机它面朝的是机器的背后</b>」——而三台模块的 {@code facing} 全是
     *       {@code south}（见第 2 条）⇒ 模块朝南、主机正对模块的背面 ⇒ <b>主机的正脸 = {@code NORTH}</b>。
     *       （旧版填 {@code SOUTH}，等于宣称"主机和模块脸对脸"，与实机观察相反。）</li>
     *   <li><b>三台模块的实测状态一致</b>：玩家 F3 逐台读到 blast {@code (-196,68,426)} / stripper
     *       {@code (-240,68,426)} / space {@code (-218,86,429)} <b>全部</b>
     *       {@code facing=south} / {@code upwards_facing=north}，而主机世界坐标 {@code (-218,66,375)}
     *       ⇒ 模块的正脸是 {@code SOUTH}、主机的正脸只能是 {@code NORTH}（同一世界坐标系里，两者相反）。</li>
     *   <li><b>JEI 预览的幽灵宿主外壳固定按 {@code NORTH}/{@code UP} 铺</b>：
     *       {@code BlockPattern.getPreview(...)} 里硬编码
     *       {@code setActualRelativeOffset(palm, thumb, aisle, Direction.NORTH, Direction.UP, false)}
     *       （{@code gtl-dev/api-dump/BlockPattern.txt:1211-1218}，上一轮已反汇编取证 ⇒ 见 javadoc 第 3 条）。
     *       声明 {@code NORTH} 才与本预览的参照系同口径；旧值 {@code SOUTH} 令
     *       {@code calculateModuleOffset} 把整套偏移按 180° 换算
     *       （{@code reverseHorizontalRotation(SOUTH) = CLOCKWISE_180}）⇒ 三台模块一起被摆到楼的另一侧 / 外面。</li>
     * </ol>
     *
     * <p><b>通用口径（取代旧注释"必须 NORTH/UP"那条误读）</b>：
     * {@code hostFront} 必须与本机在<b>世界里的真实朝向</b>一致（本机 = {@code NORTH}），
     * {@code moduleFront} 必须与模块在<b>世界里的真实朝向</b>一致（= {@code SOUTH}，见
     * {@link #MOLTEN_MODULE_FRONT}）；<b>两者可以是互为相反的两个方向</b>——"脸对脸"从来不成立。
     * 数值本身（三个偏移）就是世界坐标差，<b>一个字都没有改</b>（见 {@link #MOLTEN_MODULE_CONTROLLERS} 第 7 条）。
     *
     * <p><b>回退说明（只在"实机看模块仍在外侧"时才用，两处必须成对改）</b>：
     * 若改成本条 {@code NORTH} + 偏移原样后模块依旧在外侧，则回退为
     * 「{@code MOLTEN_HOST_FRONT = SOUTH} + {@link #buildMoltenHost()} 里 {@code offset} 的 x、z 两项取负」
     * —— <b>这两件事必须同时做</b>，只改一半会把模块摆到 180° 对面去（= 本次要修的同一个病）。
     * 对照（两对都必须自洽，现在的 bug 就是配错了对）：
     * <pre>
     *   ① 现值（本次修正）    ：hostFront = NORTH + 偏移 = 世界差 (22,2,51) / (−22,2,51) / (0,20,54)
     *   ② 回退值（成对改）    ：hostFront = SOUTH + 偏移 x/z 取负 = (−22,2,−51) / (22,2,−51) / (0,20,−54)
     *   ✗ 旧版（本次要修的 bug）：hostFront = SOUTH + 偏移 = 世界差 —— 声明与偏移不同口径 ⇒ 整体转 180°
     * </pre>
     */
    private static final Direction MOLTEN_HOST_FRONT = Direction.NORTH;

    /** 宿主真实「上」：世界 Y 轴朝上 ⇒ 恒 {@link Direction#UP}。 */
    private static final Direction MOLTEN_HOST_UP = Direction.UP;

    /**
     * 地心枢纽三个<b>模块</b>在世界里的真实正脸 ⇒ {@code ModuleRenderInfo} 第 4 参（{@code moduleFront}）。
     *
     * <p>三条同为 {@code SOUTH}，两条独立证据：
     * <ol>
     *   <li><b>投影实测</b>：{@code states_old.txt:46/168/207}（烧矿机 / 炼矿机 / 挖矿机）全是
     *       {@code facing=south}；</li>
     *   <li><b>★ 2026-10 实机 F3（权威）</b>：三台模块在世界里同样全 {@code facing=south} /
     *       {@code upwards_facing=north}（blast {@code (-196,68,426)}、stripper {@code (-240,68,426)}、
     *       space {@code (-218,86,429)}）⇒ <b>本条维持 {@code SOUTH} 不动</b>。</li>
     * </ol>
     * <p>★ 与 {@link #MOLTEN_HOST_FRONT}（= {@code NORTH}）<b>相反是正常的</b>：
     * 一条描述"偏移量是在哪个参照系里量的"（主机），一条描述"模块图案按哪个世界朝向摆放"（模块），
     * 两者各管一件事。<b>本次修正只动 {@code hostFront}，本条一个字都没动。</b>
     */
    private static final Direction MOLTEN_MODULE_FRONT = Direction.SOUTH;

    /** 模块真实「上」：恒 {@link Direction#UP}。 */
    private static final Direction MOLTEN_MODULE_UP = Direction.UP;

    private static List<ModuleRenderInfo> buildMoltenHost() {
        List<ModuleRenderInfo> out = new ArrayList<>(MOLTEN_MODULE_IDS.length);
        // 表内直接就是"相对主机的偏移"（实机 F3 实测）：无需任何主机基准坐标（见 javadoc 第 7 条）
        for (int i = 0; i < MOLTEN_MODULE_IDS.length; i++) {
            int[] c = MOLTEN_MODULE_CONTROLLERS[i];
            MultiblockMachineDefinition def = moltenDefinition(MOLTEN_MODULE_IDS[i]);
            if (def == null) {
                continue;   // 该模块没注册（例如配方表缺失时 registerModuleB/C 会跳过）⇒ 略过
            }
            // ★ 表内即"模块相对主机的偏移"（实机 F3 实测值）：直接采用，任何轴都不再做换算/减法
            //   （旧实现是"投影绝对坐标 − MOLTEN_HOST_CONTROLLER(48,8,8)"，那条口径已被 F3 实测否掉，见 javadoc 第 7 条）
            BlockPos offset = new BlockPos(c[0], c[1], c[2]);
            // ★★ 回退说明的唯一落点：只有当 MOLTEN_HOST_FRONT 被改回 SOUTH 时，才把上行的 x、z 两项
            //    同时取负（= new BlockPos(-c[0], c[1], -c[2])），且必须与 MOLTEN_HOST_FRONT 成对改；
            //    现值是 hostFront = NORTH + 偏移 = 世界差（正确口径），**本行不要取负**。
            //    MOLTEN_MODULE_FRONT 任何情况下都不跟着改（实机三台模块就是 facing=south）。
            out.add(new ModuleRenderInfo(offset, MOLTEN_HOST_FRONT, MOLTEN_HOST_UP,
                    MOLTEN_MODULE_FRONT, MOLTEN_MODULE_UP, def));
        }
        return List.copyOf(out);
    }

    /** 地心枢纽三个模块定义：优先读注册窗口填好的静态字段，兜底走 GTCEu 注册表。 */
    private static MultiblockMachineDefinition moltenDefinition(String moduleId) {
        MultiblockMachineDefinition direct = switch (moduleId) {
            case "molten_blast_module" -> GTLModularMachines.MOLTEN_BLAST_MODULE;
            case "molten_stripper_module" -> GTLModularMachines.MOLTEN_STRIPPER_MODULE;
            case "molten_space_ore_module" -> GTLModularMachines.MOLTEN_SPACE_ORE_MODULE;
            default -> null;
        };
        return direct != null ? direct : lookupDefinition("gtlmodular", moduleId);
    }

    /** GTCEu 机器注册表兜底查询；拿不到 / 类型不符一律 {@code null}（绝不外抛）。 */
    private static MultiblockMachineDefinition lookupDefinition(String namespace, String path) {
        try {
            MachineDefinition def = GTRegistries.MACHINES.get(new ResourceLocation(namespace, path));
            return def instanceof MultiblockMachineDefinition multi ? multi : null;
        } catch (Throwable t) {
            // 注册窗口内/未冻结时查表、或注册表尚未就绪 —— 静默跳过，不影响预览
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 诊断日志（限流：每台宿主成功打一次；键集合有硬上限，永不无界增长）
    // ------------------------------------------------------------------

    /** 已打过日志的宿主键上限（超过后只按次数抽样，集合本身不再增长）。 */
    private static final int MAX_LOGGED_KEYS = 64;

    private static final Set<String> LOGGED_KEYS = new LinkedHashSet<>(16);
    private static final AtomicInteger DROPPED = new AtomicInteger();

    /**
     * 埋点（验收抓手）：同一台宿主第一次调用打一条 INFO，之后静默。
     *
     * <p>样例（本模组日志前缀统一为 {@code [gtlmodular]}）：
     * <pre>
     *   [gtlmodular] 模块预览: getModulesForRendering 被调用 主机=纳米芯片组装复合体@(x=…, y=…, z=…) 返回=12
     *   [gtlmodular] 模块预览: getModulesForRendering 被调用 主机=地心涌动能量转换枢纽@(x=…, y=…, z=…) 返回=3
     * </pre>
     * 返回 0 时也同样记录（那样一眼就能区分"没被调用"与"被调用了但表是空的"）。
     */
    private static void logOnce(String key, String hostName, BlockPos hostPos, List<ModuleRenderInfo> list) {
        boolean emit;
        synchronized (LOGGED_KEYS) {
            if (LOGGED_KEYS.contains(key)) {
                emit = false;
            } else if (LOGGED_KEYS.size() < MAX_LOGGED_KEYS) {
                LOGGED_KEYS.add(key);
                emit = true;
            } else {
                // 键空间已满（异常场景：大量不同坐标的宿主）：不再新增键，按次数抽样打一条
                emit = (DROPPED.incrementAndGet() & 0x3F) == 0;
            }
        }
        if (emit) {
            GTLModular.LOGGER.info("[gtlmodular] 模块预览: getModulesForRendering 被调用 主机={}@{} 返回={}",
                    hostName, hostPos.toShortString(), list.size());
        }
    }
}
