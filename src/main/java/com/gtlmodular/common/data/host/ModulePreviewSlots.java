package com.gtlmodular.common.data.host;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.data.nanochip.NanochipModulePreview;
import com.gtlmodular.common.data.nanochip.ModulePreviewSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.gtlcore.gtlcore.utils.datastructure.ModuleRenderInfo;

import java.util.ArrayList;
import java.util.List;

/** 地心预览与 NAC 兼容入口共存，避免旧调用方丢失原类及缓存类型。 */
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
        return NanochipModulePreview.nacModules(hostPos);
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
        return ModulePreviewSupport.lookupDefinition(namespace, path);
    }

    // ------------------------------------------------------------------
    // 诊断日志（限流：每台宿主成功打一次；键集合有硬上限，永不无界增长）
    // ------------------------------------------------------------------

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
        ModulePreviewSupport.logOnce(key, hostName, hostPos, list);
    }
}
