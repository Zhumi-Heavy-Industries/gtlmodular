package com.gtlmodular.common.machine.nanochip;

import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;
import org.gtlcore.gtlcore.utils.datastructure.ModuleRenderInfo;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.host.ModulePreviewSlots;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * NAC 主机的<b>模块连接只读查询</b>（P4 · M5 抽取）。
 *
 * <p>只搬「读」这一半：模块计数 / 已连接判定 / 12 个模块扫描位 / 预览数据 / 结构区块加载判定 /
 * 快照条数 / 模块上限 / 扫描位朝向开关。**写集合的那一半**（{@code scanAndConnectModules}、
 * {@code refreshModulesFromStructure}、{@code adoptRestoredModuleSlots}、{@code snapshotModuleSlots}、
 * {@code consumePendingResync} 等）全部留在主机 —— 它们写 {@code modules}/{@code cachedModuleSlots}/
 * {@code lastMatchedModuleSlots} 字段，本轮一字未动。
 *
 * <p>铁律（R2 / 模块框架）：主机上 gtlcore {@code IModularMachineHost} 的 {@code @Override} 签名
 * 一个都没删，只是把实现委托到本类；{@code getModuleScanPositions()} 的 12 条偏移来源仍是
 * {@link NanochipComplexPattern#moduleControllerOffsets()}，**逐条数值与顺序原样**；
 * 旋转开关 {@link #NAC_MODULE_SCAN_ROTATE_BY_HOST} 仍为 {@code false}（纯平移）。
 */
final class NanochipModuleLinkHost {

    private NanochipModuleLinkHost() {}

    /** 已成形的指定种类模块数量。 */
    static int countModules(NanochipAssemblyComplexMachine machine, NanochipModuleKind kind) {
        int n = 0;
        for (IModularMachineModule m : machine.getModules()) {
            NanochipModuleMachine module;
            if (!(m instanceof NanochipModuleMachine) || !(module = (NanochipModuleMachine)m).isFormed() || module.getModuleKind() != kind) continue;
            ++n;
        }
        return n;
    }
    /** 全部已成形模块数。 */
    static int formedModuleCount(NanochipAssemblyComplexMachine machine) {
        int n = 0;
        for (IModularMachineModule m : machine.getModules()) {
            NanochipModuleMachine module;
            if (!(m instanceof NanochipModuleMachine) || !(module = (NanochipModuleMachine)m).isFormed()) continue;
            ++n;
        }
        return n;
    }
    /**
     * 只读：{@code module} 是否在主机权威的「已连接模块集合」里。
     *
     * <p>★ 模块侧（{@link NanochipModuleMachine#hostConnected()}）<b>只读</b>这一个判定的结果，
     * 不再自己写集合 —— 这是"连接不稳"的修法核心。刻意不返回 {@code getModules()} 的包装集合，
     * 以免每 tick 在热路径上多分配一个对象。
     */
    static boolean isModuleConnected(NanochipAssemblyComplexMachine machine, IModularMachineModule<NanochipAssemblyComplexMachine, ?> module) {
        return module != null && machine.getModuleSet().contains(module);
    }
    static boolean structureChunksLoaded(Level level, BlockPos controllerPos) {
        if (level == null || controllerPos == null) {
            return false;
        }
        try {
            for (int dx = -32; dx <= 32; dx += 16) {
                for (int dz = -32; dz <= 32; dz += 16) {
                    if (level.isLoaded(controllerPos.offset(dx, 0, dz))) continue;
                    return false;
                }
            }
            return true;
        }
        catch (Throwable t) {
            return false;
        }
    }
    /**
     * 模块上限：每种模块各 1 台 + 1 个余量（原版为 12 个模块槽，对应 NH 的 11 种模块）。
     *
     * <p>★ 2026-09-18（队长指示新增三台组织器）：模块种类由 11 → <b>14</b>
     * （{@link NanochipModuleKind#values()} 现读：原有 11 + 奇异 / 寰宇 / 超因果优化组织器）
     * ⇒ 上限同步取 <b>15</b>（14 种各 1 台 + 1 个余量），口径与原版一致。
     * 结构侧的 {@code F} 位有 48 格，装得下；本值与 {@code NanochipComplexPattern.build()}
     * （冻结面，有 sha256 钉子）无关 —— 改它不动主机图案。
     * <p>刻意写成<b>字面量</b>：{@code _census/check_nac_mechanics_parity.js} 的 B10 直接读这个数字
     * 并与「NH 只在界面文本里写 0-12」做对照（见该脚本的 X6 divergence），保持可解析。
     */
    static int getMaxModuleCount() {
        return 15;
    }
    /**
     * ★【2026-10 模块框架对齐 · A1】<b>模块扫描位（世界坐标）</b> —— gtlcore
     * {@link IModularMachineHost#scanAndConnectModules()} 的唯一数据源。
     *
     * <h3>改前 / 改后</h3>
     * 改前：{@code return new BlockPos[0];}（空表）⇒ 框架那条「遍历扫描位 → 校验 → connectToHost」
     * 的路<b>彻底空转</b>，模块连接只能靠 {@code onStructureFormed()} 那一刻的 F 位快照
     * （{@code cachedModuleSlots}）⇒ 读档后快照为空，就得干等一次整图案匹配（实测 4 分 18 秒）。
     * 改后：返回 12 个模块位的世界坐标，与官方那套同构（参照 {@code gtladditions} 的
     * {@code SubspaceCorridorHubIndustrialArray}：{@code getModuleScanPositions()} = 纯几何助手
     * {@code calculateModulePositions(getPos(), getFrontFacing())}；以及
     * {@code forge_of_the_antichrist} 的 {@code AntichristPosHelper}）。
     *
     * <h3>几何来源（唯一数据源 = 写死的实测表）</h3>
     * {@link NanochipComplexPattern#moduleControllerOffsets()} 的 12 条偏移（来源 = 玩家存档实测
     * 「4 个 F 位(偏移)」列 + 「模块底座 7×3×7 与主机基座逐格重合」的 48/48 穷举，见该类
     * {@code NAC_MODULE_PREVIEW_SLOTS} 的 javadoc）。<b>本次已逐格核对 12/12 与结构定义一致</b>：
     * 按 {@code (NAC_CTRL_X−x, row−NAC_CTRL_ROW, NAC_CTRL_Z−z) = (31−x, row−8, 32−z)} 反算成
     * {@code NAC_SLICES} 下标后，12 条全部落在 {@code 'F'}（模块位）上，且都在 {@code row=2}
     * （= 基座顶层，与模块控制器层一致）；逐条下标/字符见执行记录
     * {@code _census/模块框架对齐_执行记录.md} §②。
     *
     * <h3>朝向折算（不自己写旋转数学）</h3>
     * 这张偏移表是在「主机 {@code facing=north}」的参照系里量的（与
     * {@code ModulePreviewSlots.buildNac()} 声明 {@code hostFront=NORTH / hostUp=UP} 同口径）
     * ⇒ 这里直接复用 gtlcore 自己的
     * {@link ModuleRenderInfo#calculateModuleOffset(Direction, Direction)}：预览侧
     * {@code PatternPreviewWidget$MBPattern.initializeModules}（字节码
     * {@code _enh2_extract/gtlcore_module_api.txt:3301-3305}）正是
     * {@code hostPos.offset(info.calculateModuleOffset(hostFront, hostUp))} 得到模块控制器的世界坐标，
     * 与本法<b>同一个公式</b> ⇒ 「预览画在哪」与「主机扫哪里」永远一致，且没有我方自造的旋转约定。
     * <br>第 6 个参数（模块定义）在本调用里<b>不被读取</b>：该 record 的
     * {@code calculateModuleOffset} 只读 {@code worldOffset / hostFront / hostUp}
     * （同文件 {@code :159-204}，且 record 的规范构造器无 null 检查，{@code :135-157}）⇒ 传 {@code null}。
     *
     * <h3>线程 / 副作用</h3>
     * 纯算术 + 只读本机方块状态（{@code getPos() / getFrontFacing() / getUpwardsFacing()}）：
     * <b>不碰世界、不碰集合、不写任何字段</b> ⇒ 任意线程可调；真正写模块集合的
     * {@link NanochipAssemblyComplexMachine#scanAndConnectModules()} 仍只在服务端线程被调。
     *
     * <h3>与快照机制的分工（为什么必须保留快照路径）</h3>
     * 本表只覆盖「12 个基座的<b>边中点</b>」这 12 格（= 模块底座能与主机基座逐格重合的唯一位置）；
     * 玩家若把模块控制器放在同一基座的<b>其它</b> F 位（另外 3 条边），框架扫描扫不到它 —— 那正是
     * {@code refreshModulesFromStructure()} 那条 F 位快照路径继续存在的原因（兜底/差集补齐，
     * 见 {@link NanochipAssemblyComplexMachine#scanAndConnectModules()}）。
     *
     * <h3>★★ 2026-10 三次修正：默认改成"纯平移"，不做朝向旋转（开关见方法下方的
     * {@link #NAC_MODULE_SCAN_ROTATE_BY_HOST}）</h3>
     * <p>上面「朝向折算」那一节保留原文备查，但它有<b>两处已被本次修正推翻/降级</b>：
     * <ol>
     *   <li>它断言"预览与本法同一个公式 ⇒ 永远一致"——<b>公式相同不等于必要</b>：当
     *       {@code hostFront = NORTH} 时 {@code calculateModuleOffset} 逐字节码退化为恒等变换
     *       （{@code calculateUpRotation} 只要 {@code front} 是水平方向就返回 {@code NONE}，
     *       {@code _enh2_extract/gtlcore_module_api.txt:366-402}；{@code rotateByFrontAxis} 的
     *       "前轴 = Z + {@code NONE}" 分支即原值，同文件 {@code :374-381/:522}），
     *       而本机（{@code RotationState.NON_Y_AXIS}，数据包/存档实测 {@code facing=north}）
     *       与预览里的幽灵主机都恰好是 {@code NORTH} ⇒ 两条路线<b>逐值等价</b>，
     *       旋转是"多余的、也是唯一可能带来未取证 ±90°/180° 约定的那一环"。</li>
     *   <li>它没有说明"非 north 主机"其实<b>不可能存在</b>：现行图案只有
     *       {@code facing=north} 能成形（施工记录 §50.2 证据 2：8 种组合逐格实测，
     *       新图案 + north 8712/8712 命中，其余 6 种全不成形）⇒ 该旋转永远不会真正生效，
     *       却把"未取证面"（{@code _census/模块框架对齐_执行记录.md} §⑦-2 / §R4：
     *       该函数在 gtlcore 里只被幽灵主机路径调用过）留在主路径上。默认改成纯平移即为此。</li>
     * </ol>
     * <p><b>另注（位点表）</b>：本节上方提到的 12 条偏移现已是"各基座 −z 边中点 + north"
     * （= 数据包/存档里模块真实站着的那一组），选表开关见
     * {@link NanochipComplexPattern#NAC_MODULE_SLOTS_CANONICAL_MINUS_Z}；
     * 旧版"最朝外 F 位"表已降级为备选（12 条里 8 条在 ±x 边上，其 east/west 的 ±90° 符号
     * 从未在真实存档里验证过）。
     *
     * <p><b>失败姿态</b>：表为空 / 拿不到本机坐标或朝向 ⇒ 返回空表（等价于改前的行为，
     * 快照路径照旧兜底；绝不猜、绝不抛）。
     */
    static BlockPos[] getModuleScanPositions(NanochipAssemblyComplexMachine machine) {
        List<NanochipComplexPattern.ModuleControllerSlot> slots = NanochipComplexPattern.moduleControllerOffsets();
        if (slots.isEmpty()) {
            return new BlockPos[0];
        }
        BlockPos origin = machine.getPos();
        Direction front = machine.getFrontFacing();
        Direction up = machine.getUpwardsFacing();
        if (origin == null || front == null || up == null) {
            return new BlockPos[0];
        }
        BlockPos[] out = new BlockPos[slots.size()];
        boolean rotateByHost = false;
        for (int i = 0; i < out.length; ++i) {
            NanochipComplexPattern.ModuleControllerSlot slot = slots.get(i);
            if (!rotateByHost) {
                out[i] = origin.offset((Vec3i)slot.offset());
                continue;
            }
            ModuleRenderInfo info = new ModuleRenderInfo(slot.offset(), Direction.NORTH, Direction.UP, slot.front(), Direction.UP, null);
            out[i] = origin.offset((Vec3i)info.calculateModuleOffset(front, up));
        }
        return out;
    }
    /**
     * 「点击显示机器模块」（{@code M:OFF→M:ON}）预览数据。
     *
     * <p><b>根因</b>：gtlcore 的 {@link IModularMachineHost#getModulesForRendering()} 默认实现
     * <b>直接 {@code return Collections.emptyList()}</b>
     * （字节码：{@code _enh2_extract/gtlcore_module_api.txt:1419-1422}：{@code invokestatic
     * Collections.emptyList → areturn}）。预览侧唯一消费点是
     * {@code PatternPreviewWidget$MBPattern.initializeModules}（同文件 {@code :3230-3246}），
     * 它拿到空表立刻 {@code return List.of()} ⇒ 点按钮画面不变。我方 NAC 原先没覆写它。
     *
     * <p><b>数据从哪来（★ 2026-10 修正：已改成写死的实测表，不再运行时推导）</b>：
     * {@link ModulePreviewSlots#nacModules} 调
     * {@link NanochipComplexPattern#moduleControllerOffsets()} —— 该方法现在只是把
     * <b>12 条写死的位点</b>（来源 = 玩家存档实测 {@code _census/expect_table.md} §1 的
     * 「4 个 F 位(偏移)」列）转成列表，<b>恒返回 12 条、没有任何可能失败的路径</b>。
     * 旧实现是"从 {@code NAC_SLICES} 收齐 F 位 → 用「4 个轴向 ±3 格也都是 F」解出 12 个基座中心"
     * 的运行时反推，玩家日志实锤它<b>恒命中 0 个</b>
     * （{@code IllegalStateException: 基座中心判据命中 0 个（期望 12）}）⇒ 恒返回空表 ⇒
     * 点 {@code M:ON} 画面不变；该反推已停用（{@code @Deprecated} 死代码，禁止再调用）。
     * 表的内容、逐条自检与"为什么必须写死"全部写在 {@code NAC_MODULE_PREVIEW_SLOTS} 的 javadoc 里。
     *
     * <p><b>性能</b>：这张表只依赖图案常量、与机器实例无关 ⇒ 在 {@link ModulePreviewSlots}
     * 里做了<b>静态</b>缓存（全局一份，≤ 48 条），本方法每次调用只是一次 {@code volatile} 读 + 返回；
     * 诊断日志同一台宿主<b>只打一次</b>（键集合有硬上限）。
     *
     * <p><b>失败姿态</b>：{@code getPos()} 为空 / 推导失败 / 任何意外 ⇒ 返回<b>空表</b>。
     *
     * <p><b>红线</b>：本方法<b>只读</b>——不碰 AE 刷新（{@code refreshPatternsNow}/{@code needPatternSync}）、
     * 不碰 {@code cachedModuleSlots}/{@code lastMatchedModuleSlots}、不碰模式游标、催化剂字段、
     * 分页 UI、{@code clampCapacityToMax}/{@code getInternalSlotCount}/{@code growCapacityFor}，
     * 也不碰 {@code NanochipStructureCheckThrottle}。与"模块连接"机制无关（那是
     * {@code refreshModulesFromStructure} 的事）。
     */
    static List<ModuleRenderInfo> getModulesForRendering(NanochipAssemblyComplexMachine machine) {
        try {
            return ModulePreviewSlots.nacModules(machine.getPos());
        }
        catch (Throwable t) {
            GTLModular.LOGGER.debug("[gtlmodular] \u6a21\u5757\u9884\u89c8: NAC getModulesForRendering \u5f02\u5e38\uff0c\u8fd4\u56de\u7a7a\u8868: {}", (Object)t.toString());
            return List.of();
        }
    }
    /** 当前生效的 F 位快照条数（诊断/界面用；0 = 还没拿到过一次成功匹配）。 */
    static int moduleSlotCount(List<BlockPos> slots) {
        return slots.size();
    }
    /**
     * ★★【2026-10 三次修正 · 模块预览位置】<b>扫描位是否按主机朝向做旋转换算</b> ★★
     *
     * <ul>
     *   <li><b>{@code false}（默认 = 修复后行为）</b>⇒ {@link #getModuleScanPositions()} 走
     *       {@code origin.offset(slot.offset())}（<b>纯平移</b>）。
     *       理由：① 本机 {@code facing=north} 时 gtlcore 的
     *       {@code ModuleRenderInfo.calculateModuleOffset} 逐字节码退化为恒等变换
     *       （{@code calculateUpRotation} 在 {@code front} 为水平方向时直接返回 {@code NONE}，
     *       {@code _enh2_extract/gtlcore_module_api.txt:366-402}；{@code rotateByFrontAxis} 的
     *       "前轴 = Z + {@code NONE}" 分支就是原值，同文件 {@code :374-381/:522}）⇒ 两条路线逐值等价；
     *       ② 该换算约定在 gtlcore 里<b>只被"幽灵主机 NORTH/UP"路径调用过</b>，未在真实非 north 主机上验证
     *       （{@code _census/模块框架对齐_执行记录.md} §⑦-2 / §R4）；③ 现行 NAC 图案<b>只有 north 能成形</b>
     *       （施工记录 §50.2 证据 2：8 种组合逐格实测 8712/8712 只在新图案 + north 命中）
     *       ⇒ 那条非 north 分支永远走不到，留着只增加未取证面。</li>
     *   <li><b>{@code true}</b> ⇒ 逐字回到 2026-10 上一版（{@code calculateModuleOffset(getFrontFacing(),
     *       getUpwardsFacing())}），供进游戏做 A/B 对照。</li>
     * </ul>
     *
     * <p>本开关只影响"返回哪 12 个坐标"；{@link NanochipAssemblyComplexMachine#scanAndConnectModules()} 的顺序、连接/断开判定、
     * {@code @Persisted} 字段与快照兜底路径一律未动。改本值需<b>重新构建</b>（编译期常量）。
     */
    static final boolean NAC_MODULE_SCAN_ROTATE_BY_HOST = false;
}
