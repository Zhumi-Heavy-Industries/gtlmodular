package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * <b>真空传送带管道</b> —— 原版 NAC 那条"看得见的线路"的移植版（外观 + 定向）。
 *
 * <p>原版的管道是 {@code MTEVacuumConveyorPipe}（{@code extends MTEBaseFactoryPipe extends MetaPipeEntity}）：
 * 玩家整片铺在 NAC 上、再喷成各种颜色。<b>外观是"细管"，不是整方块</b> —— 原版
 * {@code MetaPipeEntity.renderInWorld} 按 {@code mConnections}（六向连接位掩码）画"中心细芯 + 朝每个已连接方向
 * 伸出的管臂"，{@code getTexture} 只负责给贴图。移植版照这个语义做：
 *
 * <table border="1">
 *   <tr><th>层</th><th>贴图</th><th>吃染料</th></tr>
 *   <tr><td>管身</td><td>{@code vacuum_pipe}（= 原版 {@code CC_PIPE} 逐字节拷贝）</td>
 *       <td><b>不吃</b>：原版 {@code TextureFactory.of(CCPipe)} 是单参重载、没有任何 RGBA 调制</td></tr>
 *   <tr><td>叠加条</td><td>{@code vacuum_pipe_bar}（= 原版 {@code CC_BAR_OVERLAY}）</td>
 *       <td><b>吃</b>：原版 {@code TextureFactory.of(CC_BAR_OVERLAY, Dyes.getModulation(aColor, ...))}；
 *           本移植版走 {@link #TINT_BAR}（= 3）→ {@link #tintColor(int)}</td></tr>
 * </table>
 *
 * <p><b>几何</b>由 {@link com.gtlmodular.client.renderer.NanochipPipeRenderer} 在渲染时按
 * {@link #connectionMask()} 现算：管芯与六个管臂<b>同一个粗细</b>（{@code 5..11} = 6 像素 = 0.375 格，
 * 见 {@code NanochipPipeRenderer.PIPE_CORE_MIN/PIPE_ARM_MIN} 的 NH 取证），管臂<b>只朝已连接的方向伸</b>。
 * 所以背后的结构能透出来，也不会出现"一整块被染绿"（玩家明确要求的那两条）。
 *
 * <h2>★ 2026-09-17 第六轮：连接机制改成与 NH 逐条一致</h2>
 * <p>玩家的第 2 条抱怨是「未染色也能相互连接，这跟 NH 不一样」。NH 的字节码（
 * {@code MTEVacuumConveyorPipe.checkConnections()}）是：
 * <pre>
 * 0: aload_0
 * 1: iconst_0
 * 2: putfield  mConnections:B      // 先清空
 * ...
 * 23: aload_1
 * 24: invokeinterface IGregTechTileEntity.getColorization:()B
 * 29: iconst_m1
 * 30: if_icmpne 34                 // 自己 colorization == -1 ⇒ 直接 return
 * 33: return                       //   ⇒ mConnections 保持 0 ⇒ 一根管臂都不画
 * 34: ... 对六个方向：
 * 83: 邻居.getColorization()
 * 88: 自己.getColorization()
 * 94: if_icmpne 151                // 邻居颜色 != 自己颜色 ⇒ 跳过这个方向
 * 106: 邻居的 metaTileEntity instanceof VacuumFactoryElement?
 * 128: 邻居.canConnectOnSide(dir.getOpposite())?
 * 146: mConnections |= dir.flag     // 三条全过才连
 * </pre>
 * ⇒ 移植版现在照抄这三条：①<b>自己未染色 → 一个方向都不连</b>；
 * ②<b>必须同色</b>（且两边都已染色）；③对面必须是"真空元素"（只有真空端口/管道算，
 * <b>NAC 机壳、控制器、模块都不算</b> —— NH 里只有 {@code MTEVacuumConveyorPipe} 与
 * {@code MTEHatchVacuumConveyor} 实现了 {@code VacuumFactoryElement}，已逐个核对类声明）。
 *
 * <h2>颜色从哪来</h2>
 * NH 只有一个来源：<b>GT 的喷漆罐</b>（{@code BaseMetaTileEntity.setColorization}）；
 * <b>NH 里没有任何"从邻居自动取色"</b> —— 移植版早期自己发明的
 * {@code autoColorFromNeighbour()} 已在第六轮<b>整段删除</b>（它正是玩家看到的
 * "两根没染过的管子自己连起来"的成因之一：默认色 0 = 白色被当成了一种真颜色）。
 * 现在改色只有两条路：螺丝刀右键循环、喷漆罐（{@code NanochipSprayCanHandler}）。
 */
public class NanochipPipeMachine extends MetaMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(NanochipPipeMachine.class, MetaMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /**
     * <b>叠加条</b>那一层的 tintindex（与真空端口的前脸层 {@link NanochipVacuumHatchMachine#TINT_OVERLAY} = 2 区分开）。
     *
     * <p><b>与原版逐条对齐</b>：原版 {@code MTEVacuumConveyorPipe.getTexture} 返回两层 ——
     * {@code [0] = TextureFactory.of(CCPipe)}（<b>单参</b>，没有任何 RGBA 调制 = 管身不染）、
     * {@code [1] = TextureFactory.of(CC_BAR_OVERLAY[_ACTIVE], Dyes.getModulation(aColor, ...))}（<b>吃染料</b>）。
     * 移植版的分层与此完全一致：管身用 {@code vacuum_pipe} 且 tintindex = -1（不染），
     * 叠加条用 {@code vacuum_pipe_bar} 且 tintindex = 3（由本类的 {@link #tintColor(int)} 给色）。
     *
     * <p>之所以取 <b>3</b> 这个下标：GTCEu 自己的管道渲染器
     * （{@code PipeBlockRenderer.renderModel} 里的 {@code lambda$renderModel$0}）把
     * {@code PipeModel.bakeQuads} 产出的 quads 的 tintIndex 统一 <b>+3</b>，也就是 GTCEu 的管道层
     * 用的就是 0/1（原位）与 3/4（+3 后）这几个下标，见 {@code StaticFaceBakery} 与
     * {@code IMachineBlock.colorTinted} 的实现。
     */
    public static final int TINT_BAR = 3;

    /**
     * 当前颜色序号；{@link NanochipColor#INDEX_UNPAINTED}（= -1）= <b>未染色</b>。
     *
     * <p><b>默认值就是未染色</b>（不是白色）—— 与 NH 一致：GT5U 的
     * {@code BaseMetaTileEntity.mColorRaw} 默认 0，而 {@code getColorization()} 是
     * {@code colorRaw - 1}，所以没被喷漆罐碰过的管道 {@code colorization == -1}。
     */
    @DescSynced
    private int colorIndex = NanochipColor.INDEX_UNPAINTED;

    public NanochipPipeMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    /**
     * 当前颜色；<b>未染色时返回 {@code null}</b>（= NH 的 {@code colorization == -1}）。
     *
     * <p>返回 null 而不是"兜底成白色"是刻意的：白色是一种<b>真颜色</b>（玩家可以主动染成白色），
     * 而未染色必须能与它区分开 —— NH 里 {@code -1} 与 {@code 0} 是两种不同的 colorization。
     */
    public NanochipColor color() {
        return NanochipColor.fromIndex(colorIndex);
    }

    /** 是否已被染色（{@code false} = NH 的 {@code colorization == -1}）。 */
    public boolean isPainted() {
        return colorIndex >= 0;
    }

    public void setColor(NanochipColor color) {
        setColor(color, true);
    }

    /**
     * 设置颜色；{@code color == null} = <b>清成未染色</b>（对应原版"复原溶剂喷漆罐"把
     * {@code colorization} 打回 -1 的效果）。
     *
     * @param manual 保留只为源码兼容（喷漆罐调用点传 {@code true}）。第六轮删掉自动取色之后
     *               这个参数<b>已经没有语义作用</b> —— 颜色不是"玩家染的"就是"没染"，
     *               不再存在第三种"被自动刷上去的"颜色。
     */
    public void setColor(NanochipColor color, boolean manual) {
        int before = colorIndex;
        int next = color == null ? NanochipColor.INDEX_UNPAINTED : color.ordinal();
        this.colorIndex = next;
        // ★【2026-09-15 第二轮】颜色变了就同步 GT 的"喷漆色"（只用于**显示**，见 syncPaintingColor）。
        syncPaintingColor();
        if (before != next) {
            scheduleRenderUpdate();
            // ★【2026-09-16 双模式路由】换色会改变"同色连通分量"⇒ 让管网图重算（见 NanochipPipeGraph 的纪元说明）。
            NanochipPipeGraph.bumpTopologyEpoch();
        }
    }

    /** 只有叠加条那一层吃染料；管身层返回 -1 = 保持本色（原版 {@code CC_PIPE} 的语义）。 */
    @Override
    public int tintColor(int index) {
        if (index == TINT_BAR) {
            NanochipColor c = color();
            // 未染色 ⇒ 不吃染料。NH 侧对应 evidence：MTEBaseFactoryPipe.getTexture 把 aColor 交给
            // Dyes.getModulation(aColor, MACHINE_METAL.getRGBA())，而 Dyes.isDyeIndex(-1) == false
            //（isDyeIndex 的字节码是 `0 <= i && i <= 15`）⇒ 落到 fallback = MACHINE_METAL
            //（= 中性"机器金属"调制，配置默认即不变色）。所以未染色在视觉上就是"贴图原色"。
            return c == null ? -1 : c.paintArgb();
        }
        return -1;
    }

    // ---------- 六向连接判定（细管几何用） ----------

    /** 方向位掩码里 {@code side} 那一位：与原版 {@code MetaPipeEntity} 的 {@code mConnections} 同构。 */
    public static int bit(Direction side) {
        return 1 << side.ordinal();
    }

    /**
     * 六向连接位掩码 —— {@link com.gtlmodular.client.renderer.NanochipPipeRenderer} 用它决定往哪几个方向伸管臂。
     *
     * <p>这个值<b>不存方块状态、也不存 NBT</b>：几何与邻居强相关，与其维护 6 个 boolean 属性 + 邻居更新，
     * 不如像 GTCEu 自己的 {@code PipeBlockRenderer}（读 {@code IPipeNode.getVisualConnections()}）那样
     * 在渲染时按当前世界现算。代价是每次区块重建多 6 次邻居查询，换掉的是"状态不同步/漏更新"这一类 bug。
     */
    public int connectionMask() {
        int mask = 0;
        for (Direction side : Direction.values()) {
            if (connectsTo(side)) {
                mask |= bit(side);
            }
        }
        return mask;
    }

    /**
     * 本管道是否朝 {@code side} 伸管臂 —— <b>逐条对应 NH {@code MTEVacuumConveyorPipe.checkConnections()}</b>：
     * <ol>
     *   <li><b>自己未染色（{@link #isPainted()} == false）⇒ 任何方向都不连</b>。
     *       NH 字节码：{@code colorization == -1} 时 {@code mConnections} 保持 0 并直接 return
     *       （偏移 23/24/29/30/33）；</li>
     *   <li>相邻是<b>同色且都已染色</b>的真空传送管道 → 连。
     *       NH：邻居 {@code getColorization()} 必须 {@code if_icmpne} 不跳（= 完全相等，偏移 83..94）；</li>
     *   <li>相邻是<b>同色且都已染色</b>的真空端口（VCI/VCO），<b>且本管道正对着它的正面</b> → 连。
     *       NH 除了要求颜色相等，还要求邻居的 {@code canConnectOnSide(dir.getOpposite())} 为真
     *       （偏移 128）；而 {@code MTEHatchVacuumConveyorInput/Output.canConnectOnSide(dir)}
     *       的字节码是 {@code return dir == getFrontFacing()} ⇒ <b>只有仓的正面能接管</b>；</li>
     *   <li><b>其它一律不连</b>（NAC 机壳 / 玻璃 / 复合体控制器 / 模块控制器）。
     *       NH 的第三条门槛是 {@code instanceof VacuumFactoryElement}（偏移 106）；全 NH 里只有
     *       {@code MTEVacuumConveyorPipe} 与 {@code MTEHatchVacuumConveyor}（及 Input/Output 子类）
     *       实现了它 —— 三处类声明已用 javap 逐个核对。</li>
     * </ol>
     *
     * <p><b>★ 与移植版旧行为的差别（2026-09-17 第六轮按 NH 改）</b>：旧版还有两条分支
     * "相邻是其它 NAC 机器方块就连"、"相邻是 NAC 结构方块就连" —— <b>NH 没有这两条</b>，
     * 属于移植版自己发明的，已删除。可见后果：贴在复合体外壳/控制器/模块旁边的管道
     * <b>不再朝它们伸管臂</b>（这正是 NH 的行为）。
     *
     * <p><b>客户端也成立</b>：GTCEu 的机器在客户端同样有 {@code MetaMachine} 实例（渲染器就是这么拿到机器的），
     * 所以渲染线程能直接算这个掩码，不需要额外的同步字段。
     */
    public boolean connectsTo(Direction side) {
        Level level = getLevel();
        if (level == null) {
            return false;
        }
        // ① NH：未染色 → 不建任何连接（世界里只剩一根无臂的管芯）
        if (!isPainted()) {
            return false;
        }
        BlockPos neighbour = getPos().relative(side);
        // 【2026-09-15 P0 加固】渲染/连接掩码也会走到这里，而邻居可能在未加载（或未到 FULL）的区块里：
        //   getBlockEntity / getBlockState 都会**加载**区块，未加载时直接判"不连接"最安全，
        //   也符合语义（看不到的邻居本来就画不出管臂）。
        if (!level.isLoaded(neighbour)) {
            return false;
        }
        NanochipColor mine = color();
        if (level.getBlockEntity(neighbour) instanceof MetaMachineBlockEntity be) {
            MetaMachine other = be.getMetaMachine();
            if (other instanceof NanochipPipeMachine pipe) {
                return pipe.isPainted() && pipe.color() == mine;      // ② 同色（都已染色）
            }
            if (other instanceof NanochipVacuumHatchMachine hatch) {
                // ③ 同色 + 正对仓的正面（NH：canConnectOnSide(dir.getOpposite()) == dir == frontFacing）
                return hatch.isPainted() && hatch.color() == mine
                        && hatch.canConnectOnSide(side.getOpposite());
            }
            // ④ 其它机器（NAC 控制器 / 模块）不是 VacuumFactoryElement ⇒ 不连
            return false;
        }
        // ⑤ 结构方块（机壳 / 玻璃）也不是 VacuumFactoryElement ⇒ 不连
        return false;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        Level level = getLevel();
        if (level != null && !level.isClientSide) {
            // 【2026-09-17 第六轮】这里**不再有任何 tick 订阅**：NH 没有"自动取色"，
            // 移植版那条 subscribeServerTick(tickAutoColor) 已整段删除（见类注释）。
            // 连带好处：§18 那条"加载期查邻居 → 爆栈"的隐患连入口都不存在了。
            // 于是 onLoad 只剩两件"不查邻居、不查注册表"的纯字段操作，加载期绝对安全。
            //
            // 【2026-09-15】把 GT 的"喷漆色"同步成本机颜色（老存档里也可能是别的值）。
            // 只写一个字段，不查注册表、不查邻居 ⇒ 在 BE 加载期调用是安全的（不与 §18 的 P0 纪律冲突）。
            syncPaintingColor();
            // ★【2026-09-16 双模式路由】管道上线 ⇒ 管网拓扑变了（只自增一个静态计数器，
            //   不查邻居、不查注册表 ⇒ 加载期安全）。
            NanochipPipeGraph.bumpTopologyEpoch();
        }
    }

    /**
     * ★【2026-09-15 第二轮】把 <b>GT 的"喷漆色"</b>同步成本机当前通道颜色（{@link #color()}）。
     *
     * <h3>为什么（玩家实测）</h3>
     * 提示里那行 {@code 颜色: #xxxxxxxx} 读的是 GT 的 {@code getPaintingColor()}，
     * 而我们自己的颜色只存在 {@link #colorIndex} 里、渲染只走 {@link #tintColor(int)}
     * ⇒ 不写 GT 的字段，那行就永远是默认白 {@code #FFFFFF}（玩家：「染成绿色，它这里的颜色也不改变」）。
     * 现在<b>每一条改色路径</b>都经 {@link #setColor(NanochipColor, boolean)}，由它统一同步，
     * 因此那行会跟着真实颜色走。
     *
     * <h3>为什么不会改变渲染</h3>
     * {@link #tintColor(int)} 只读 {@link #color()}（= {@code colorIndex}），<b>从不</b>读
     * {@code getPaintingColor()}；GT 的"喷漆"在 GTCEu 里只被 Jade/TOP 的
     * {@code StainedColorProvider} 与 NBT 使用。所以这里写这个字段<b>纯显示</b>，碰不到外观。
     *
     * <p>值用 {@link NanochipColor#paintArgb()}（不透明 ARGB，{@code 0xFFxxxxxx}）而不是 RGB：
     * GTCEu 打印时用 {@code Integer.toHexString(...).toUpperCase()} 且<b>不补前导零</b>，
     * 传 RGB 的话绿色 {@code 0x00FF00} 会显示成 {@code #FF00}（难看且容易被误读）；
     * 传 ARGB 恒为 8 位，例如绿 = {@code #FF00FF00}，至少是完整、可对上的值。
     */
    private void syncPaintingColor() {
        try {
            NanochipColor c = color();
            // 未染色 ⇒ 复位成 GTCEu 自己的默认喷漆色，别在提示里显示一个假颜色名。
            super.setPaintingColor(c == null ? 0xFFFFFFFF : c.paintArgb());
        } catch (Throwable ignored) {
            // 老版本 API 差异，忽略
        }
    }

    @Override
    public void onUnload() {
        // ★【2026-09-16 双模式路由】管网拓扑可能变了 ⇒ 让 NAC 的管网图重算一次。
        //
        // 为什么挂在 onUnload：GTCEu 的 `MetaMachineBlockEntity.setRemoved()`（SRG `m_7651_`）
        // 会调 `MetaMachine.onUnload()`（`javap -c` 实测：`invokevirtual MetaMachine.onUnload`）——
        // 也就是说 **方块被拆掉** 与 **区块被卸载** 都会走到这里。
        // 后者（区块卸载）其实没改拓扑，会让下一次查询白算一次 BFS，代价可忽略；
        // 换来的是"拆掉管道后一定重算"这条硬保证（否则会留下一条幽灵通路 ✗）。
        // `MetaMachine` 上没有 setRemoved()（javap 查过），所以这里就是能挂的最靠近"拆掉"的钩子。
        NanochipPipeGraph.bumpTopologyEpoch();
        super.onUnload();
    }

    // ---------- 交互：螺丝刀循环换色 ----------

    /**
     * 螺丝刀右键循环换色。顺序：<b>未染色 → 白 → 橙 → … → 黑 → 白 → …</b>
     * （未染色是"没有颜色"，所以第一次点击必然变成白色，而不是从黑色开始）。
     */
    @Override
    public InteractionResult onScrewdriverClick(Player player, InteractionHand hand,
                                               Direction gridSide, BlockHitResult hitResult) {
        Level level = getLevel();
        if (level != null && !level.isClientSide) {
            NanochipColor current = color();
            NanochipColor next = current == null ? NanochipColor.WHITE : current.next();
            setColor(next, true);
            if (player != null) {
                player.displayClientMessage(
                        Component.literal("[真空传送管道] 颜色 → ")
                                .append(Component.literal(next.nameZh())
                                        .withStyle(style -> style.withColor(next.textColor()))),
                        true);
            }
        }
        return InteractionResult.sidedSuccess(level != null && level.isClientSide);
    }

    // ---------- 存档 ----------

    /**
     * 存档键：{@code GTLModularPipeColor} = 颜色序号（<b>-1 = 未染色</b>），
     * {@code GTLModularPipeManual} = "这根管子是<b>玩家亲手染的</b>色"。
     *
     * <h3>★ 老存档兼容口径（2026-09-17 第六轮，语义变了）</h3>
     * 旧版本：{@code colorIndex} 默认 0（白色）、字段无条件写盘；而且还存在
     * {@code autoColorFromNeighbour()} 会自动把颜色刷上去（此时 {@code manual = false}）。
     * ⇒ <b>光凭 {@code GTLModularPipeColor} 存在与否，无法区分"玩家染的白"与"默认/自动刷的白"</b>。
     * 新版本因此按 {@code GTLModularPipeManual} 判定：
     * <ul>
     *   <li>{@code true} ⇒ 保留那个颜色（玩家染的，语义没变）；</li>
     *   <li>{@code false} 或键不存在 ⇒ <b>读成未染色</b>（= NH 的 {@code colorization == -1}）。</li>
     * </ul>
     * <b>直接后果（要告诉玩家）</b>：老存档里那些<b>没被玩家亲手染过</b>的管道会变成"未染色" ⇒
     * 它们不再与任何东西连接。这是 NH 的行为（NH 里从没被喷漆罐碰过的管道同样 {@code colorization == -1}），
     * 也正是玩家第 2 条抱怨要的结果；重新喷一次漆即可恢复通路。
     */
    @Override
    public void saveCustomPersistedData(net.minecraft.nbt.CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        tag.putInt("GTLModularPipeColor", colorIndex);
        tag.putBoolean("GTLModularPipeManual", isPainted());
    }

    @Override
    public void loadCustomPersistedData(net.minecraft.nbt.CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        // ★【2026-09-17 第六轮】缺字段/没被玩家染过 ⇒ 未染色（见 saveCustomPersistedData 的兼容口径）。
        boolean painted = tag.getBoolean("GTLModularPipeManual");
        int stored = tag.getInt("GTLModularPipeColor");
        colorIndex = (painted && stored >= 0 && stored < NanochipColor.values().length)
                ? stored
                : NanochipColor.INDEX_UNPAINTED;
        // 【2026-09-15 第二轮】读档后把 GT 的"喷漆色"同步成本机颜色（老存档里那个字段可能是
        // 早期版本留下的值，也可能压根没有）—— 这样 Jade/TOP 那行 `颜色:` 与真实颜色一致。
        syncPaintingColor();
    }

    // ---------- 喷漆状态：只用于"显示"，不参与染色 ----------

    /**
     * GT 的"喷漆色"：<b>本模组把它当作纯粹的"显示字段"</b> —— 由 {@link #syncPaintingColor()}
     * 在每次改色时同步成 {@link #color()}，好让 Jade/TOP 的 {@code StainedColorProvider}
     * 打出那一行 {@code 颜色: #xxxxxxxx}（玩家要求"这行要跟着真实颜色变"）。
     *
     * <p><b>不覆写 {@code getPaintingColor()}</b>：以前为了"让那行不出现"覆写成恒返回默认值，
     * 结果是那行照样出现、而且永远显示默认白（玩家实测：染成绿色后还是 {@code #FFFFFF}）。
     * 现在改成"老老实实维护这个字段"，行为反而简单：字段 = {@code color().paintArgb()}。
     *
     * <p><b>仍然不参与染色</b>：渲染只看 {@link #tintColor(int)}（读 {@link #color()}）。
     *
     * <p>GT 的"喷漆"入口（{@code IPaintable#setPaintingColor}）：把颜色映射进我们的 16 色通道
     * （{@link NanochipColor#ofArgb(int)}，完全相等 → 最近且 ≤ 容差 → 原版染料色兜底 → 认不出就不染），
     * 映射成功就 {@link #setColor(NanochipColor, boolean) setColor(mapped, true)}。
     *
     * <p>最后<b>无条件把 GT 字段复位成本机颜色</b>：无论映射成功与否，这个字段都不允许与
     * {@link #color()} 不一致（否则那行 `颜色:` 会撒谎）。注意不能递归调用自己。
     *
     * <p>⚠️ <b>GTCEu 1.4.4 的喷漆罐不走这里</b>（{@code ColorSprayBehaviour} 只改方块状态）——
     * 实际链路是 {@code NanochipSprayCanHandler}。本方法把 API 那一侧也接上。
     */
    @Override
    public void setPaintingColor(int argb) {
        NanochipColor mapped = NanochipColor.ofArgb(argb);
        if (mapped != null) {
            setColor(mapped, true);        // 内部会 syncPaintingColor()
        }
        syncPaintingColor();               // 认不出来时也要把字段拉回本机颜色
    }
}
