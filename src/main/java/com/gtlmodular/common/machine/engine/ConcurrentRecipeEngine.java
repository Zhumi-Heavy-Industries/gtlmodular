package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gtlmodular.GTLModular;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * ★ <b>独立线程引擎 · 并发批次表（内核本体）</b>。
 *
 * <h2>一句话</h2>
 * 并发批次 = 同一台机器上同时跑好几条配方，<b>每条各有各的进度和产出，不用排队</b>。
 * 数据结构就是一张极简的 {@code List<Batch>}：{@link Batch} = 「一份已经算好的配方 + 自己的
 * {@code progress} + 开跑时钉死的 {@code duration}」；机器同时挂几条由 {@link Host#slots()} 决定，
 * 槽满了不再收新配方，有批次跑完空出位置马上补一条。
 *
 * <h2>设计边界（为什么它「纯」）</h2>
 * 本类<b>不依赖任何机器类、不碰任何 UI 字段</b>：它只认 {@link Host} 的六个方法。
 * 「怎么跟机器打交道」（找配方、扣料、每 tick 输入、收尾出料）全部由调用方实现，
 * 于是调度顺序可以被单独看懂、单独核对；本类永远不会自己去改
 * {@code RecipeLogic} 的 {@code progress}／{@code duration}／{@code lastRecipe}。
 *
 * <h2>每个 server tick 的三步顺序（★ 顺序反了会出各种怪事，照这个来）</h2>
 * <ol>
 *   <li><b>逐条推进</b>：对每条批次调 {@link Host#tickInputs}（它会真扣这一 tick 的 EU／流体），
 *       <b>成功才 {@code progress++}</b>；不够就<b>原地等</b> —— 不涨进度、也不把它摘掉；</li>
 *   <li><b>涨满的批次摘掉</b>：{@code batch.finished()} 为真 ⇒ <b>先从表里摘，再</b>
 *       {@link Host#finishOutputs}（{@code afterWorking}／{@code postWorking}／出料）。
 *       先摘后收尾的理由：即使收尾过程抛异常，下个 tick 也不会对同一条批次<b>重复出料</b>；</li>
 *   <li><b>有空位就补新批次</b>：{@link Host#findBatch} 找配方 → <b>原子扣料</b>
 *       （{@link Host#consumeInputs}）→ 成功才入表。
 *       <b>扣料只在这里做、一条只扣一次</b>；
 *       {@code findBatch == null}（断档）或扣料不齐就 {@code break} ——
 *       宁愿少跑一条，也绝不吞料、也绝不一直报缺料；下一个 tick 会重新走这一步。</li>
 * </ol>
 * <p><b>展示回写不归本类管</b>：{@link #tick} 不碰 {@code RecipeLogic} 的任何字段，
 * GUI 上的进度由调用方在 {@code tick} 之后把第 0 条写回基类字段（见
 * {@link ThreadedRecipeLogic#serverTick}）。
 *
 * <h2>存读档（见 {@link #save} / {@link #load}）</h2>
 * 配方整条用 {@code GTRecipeSerializer.CODEC} 编进 NBT，<b>配方 id 必须单独再存一份</b>
 * —— 因为该 CODEC 不编 id，读档后不补回去会按 null 查表崩掉；进度一并存读。
 * 单条解析失败<b>只跳过那一条</b>，绝不外抛异常。
 *
 * <p><b>零混入 / 零 {@code @Persisted}</b>：本类不新增任何 {@code @Persisted} 字段、
 * 不碰 {@code MANAGED_FIELD_HOLDER}；存档只走调用方那一句「把本表写进一个<b>追加</b> NBT 键」。
 *
 * @see IThreadedRecipeMachine
 * @see ThreadedRecipeLogic
 */
public final class ConcurrentRecipeEngine {

    /**
     * 一条并发批次。
     *
     * <p>各批次<b>互不共享配方对象</b>：{@code recipe} 是已经套过配方修改器、并行与批次判定后
     * 的那一份最终配方。
     */
    public static final class Batch {

        /** 这一条批次的配方（已套过配方修改器与<b>它自己的</b>并行；各批次互不共享对象）。 */
        public final GTRecipe recipe;

        /** 开这条批次时钉死的总时长（tick）——不每 tick 去读 {@code recipe.duration}，免得进度条乱跳。 */
        public final int duration;

        /** 这一条批次<b>自己的</b>进度（tick）——「各自计时」就是这一格。 */
        public int progress;

        private Batch(GTRecipe recipe) {
            this.recipe = recipe;
            this.duration = Math.max(1, recipe.duration);
        }

        /** 涨满了没有。 */
        public boolean finished() {
            return progress >= duration;
        }

        /** 进度百分比（0~1）。 */
        public float percent() {
            return Math.min(1.0F, (float) progress / (float) duration);
        }
    }

    /**
     * 批次表要「机器侧」（在本模组里 = {@link ThreadedRecipeLogic} 的内部类）回答的六件事。
     *
     * <p>把「派活算法」与「怎么跟机器打交道」切开的好处：本类只看这六个方法，
     * 于是它能被单独看懂、单独核对，也不需要知道机器的任何具体类型。
     */
    public interface Host {

        /** 宿主机器（供实现方取配方类型／能力仓；引擎本体不使用）。 */
        IRecipeLogicMachine machine();

        /** 同时能跑几条；1 就等于关掉并发。 */
        int slots();

        /** 找一条新配方（并行、批次都算完的那种）；没有就返回 null。 */
        GTRecipe findBatch();

        /** 原子扣料，扣得齐才算收下这条批次。 */
        boolean consumeInputs(GTRecipe recipe);

        /** 每 tick 的输入（EU、tick 流体之类），不够返回 false，这条原地等。 */
        boolean tickInputs(GTRecipe recipe);

        /** 批次收尾：afterWorking / postWorking / 出料。 */
        void finishOutputs(GTRecipe recipe);
    }

    /**
     * ★ <b>存档键（追加键）</b>：{@code "gtlmodular_print_threads"}。
     *
     * <p><b>向后兼容</b>：老存档里没有这个键 ⇒ {@code tag.getList(...)} 返回空表 ⇒ {@link #load}
     * 只把批次表清空 ⇒ 逐字回落成「没有待恢复批次」的行为（不报错、不吞料）。
     */
    public static final String TAG_BATCHES = "gtlmodular_print_threads";

    /** 批次表里「进度」的键（{@code int}）。 */
    private static final String TAG_PROGRESS = "progress";

    /** 批次表里「配方 id」的键（{@code String}；★ 必须单独存一份，见类注释）。 */
    private static final String TAG_ID = "id";

    /** 批次表里「配方本体」的键（整条配方走 {@code GTRecipeSerializer.CODEC}）。 */
    private static final String TAG_RECIPE = "recipe";

    /** 批次表本体（下标 = 位置、元素 = 一条在跑批次；跑完的会被摘掉，所以下标<b>不是</b>稳定 ID）。 */
    private final List<Batch> batches = new ArrayList<>();

    /** 批次条数（展示/联动读口之一）。 */
    public int size() {
        return batches.size();
    }

    /** 第 {@code index} 条批次（展示/联动读口之一；调用方自己保证 {@code index} 合法）。 */
    public Batch get(int index) {
        return batches.get(index);
    }

    /** 一条批次都没有。 */
    public boolean isEmpty() {
        return batches.isEmpty();
    }

    /**
     * 停机、拆机、切模式时清干净，别带着旧批次继续跑。
     *
     * <p>⚠️ 已经扣掉的料<b>不退回</b>（与 GTCEu 基类 {@code resetRecipeLogic()} 的既有行为一致）。
     */
    public void clear() {
        batches.clear();
    }

    /**
     * 每个 server tick 调一次：<b>推进 → 摘掉涨满的 → 有空位就补</b>（详见类注释的三步）。
     *
     * <p><b>不用 for-each</b>：第②步要在遍历中摘元素，用 {@link Iterator#remove()} 才安全。
     *
     * <p><b>不碰任何 UI 字段</b>：{@code progress}／{@code duration}／{@code lastRecipe}
     * 一律留给调用方回写。
     */
    public void tick(Host host) {
        if (host == null) {
            return;
        }

        // ── ① 逐条推进 + ② 涨满的摘掉（同一次遍历做完：先推进这一条，再判它涨满没有）──
        Iterator<Batch> iterator = batches.iterator();
        while (iterator.hasNext()) {
            Batch batch = iterator.next();
            // ① 输入不够 ⇒ 原地等（不涨进度、也不被摘掉）；够 ⇒ 本类只负责 progress++
            if (host.tickInputs(batch.recipe)) {
                batch.progress++;
            }
            if (batch.finished()) {
                // 先摘表再收尾：收尾即使抛异常，下个 tick 也不会对同一条批次重复出料
                iterator.remove();
                host.finishOutputs(batch.recipe);
            }
        }

        // ── ③ 有空位就补新批次：找配方 → 原子扣料 → 进集合（扣料只在这里做、一条只扣一次）──
        int slots = Math.max(1, host.slots());
        while (batches.size() < slots) {
            GTRecipe recipe = host.findBatch();
            if (recipe == null) {
                break;   // 断档：找不到配方就停在这一步，不回退成「整机不跑」
            }
            if (!host.consumeInputs(recipe)) {
                break;   // 扣料扣不齐 ⇒ 不收这条（宁可少跑一条，也绝不吞料 / 绝不一直报缺料）
            }
            batches.add(new Batch(recipe));
        }
    }

    /**
     * 存档：<b>每条批次一格 CompoundTag，里面有三个键</b>。
     *
     * <table border="1">
     *   <tr><th>键</th><th>类型</th><th>说明</th></tr>
     *   <tr><td>{@code recipe}</td><td>CompoundTag</td><td>整条配方，走 {@code GTRecipeSerializer.CODEC}
     *       （编码内容含：配方类型 / 输入 / 产出 / tick 输入 / tick 产出 / 概率逻辑 / 配方条件 /
     *        物品动作 / data / duration / isFuel）</td></tr>
     *   <tr><td>{@code id}</td><td>String</td><td>★ 配方 id（<b>单独再存一份</b>）：CODEC <b>不编 id</b>，
     *       读档后不补回去 ⇒ 后续按 id 查表会拿到 null</td></tr>
     *   <tr><td>{@code progress}</td><td>int</td><td>这条批次自己的进度（tick）</td></tr>
     * </table>
     *
     * <p>空表也会写（写一个空 {@code ListTag}）：读档侧因此不必理解「键不存在」与「空表」的差别。
     */
    public void save(CompoundTag parent) {
        if (parent == null) {
            return;
        }
        ListTag list = new ListTag();
        for (Batch batch : batches) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(TAG_PROGRESS, batch.progress);
            entry.putString(TAG_ID, batch.recipe.getId() == null ? "" : batch.recipe.getId().toString());
            GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, batch.recipe)
                    .result()
                    .ifPresent(encoded -> entry.put(TAG_RECIPE, encoded));
            list.add(entry);
        }
        parent.put(TAG_BATCHES, list);
    }

    /**
     * 读档：把批次表整体读回来（<b>先清空再读</b>）。
     *
     * <p>三条纪律：
     * <ol>
     *   <li><b>缺键 / 空表 ⇒ 安全回退</b>：什么都不加，逐字等于「没有待恢复批次」；</li>
     *   <li><b>单条坏掉不影响整表</b>：某一条的配方本体缺失 / 解析失败 / 没 id 或不合法 ⇒
     *       <b>只跳过那一条</b>（并打一条 debug 说明原因），其余照常恢复；
     *       整段解析包在 try/catch 里 ⇒ <b>绝不外抛异常</b>；</li>
     *   <li><b>不再扣一次料</b>：料在存档前就扣过了 ⇒ 这里只拼 {@link Batch}，
     *       绝不调 {@link Host#consumeInputs}。</li>
     * </ol>
     *
     * <p>恢复是<b>尽力而为</b>：配方被数据包改掉 / 部件被拆 ⇒ 那条批次直接消失，
     * 已经扣掉的料<b>不退回</b>。
     */
    public void load(CompoundTag parent) {
        batches.clear();
        if (parent == null) {
            return;
        }
        ListTag list = parent.getList(TAG_BATCHES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                CompoundTag entry = list.getCompound(i);
                Tag encoded = entry.get(TAG_RECIPE);
                if (encoded == null) {
                    GTLModular.LOGGER.debug("[gtlmodular] 线程引擎：存档第 {} 条没有配方本体键（{}），跳过该条",
                            i, TAG_RECIPE);
                    continue;
                }
                GTRecipe recipe = GTRecipeSerializer.CODEC.parse(NbtOps.INSTANCE, encoded)
                        .result()
                        .orElse(null);
                if (recipe == null) {
                    GTLModular.LOGGER.debug("[gtlmodular] 线程引擎：存档第 {} 条的配方解析失败，跳过该条", i);
                    continue;
                }
                ResourceLocation id = ResourceLocation.tryParse(entry.getString(TAG_ID));
                if (id == null) {
                    // CODEC 不编 id ⇒ 必须把单独存的那一份补回去；补不上就整条丢弃（宁可少恢复一条，
                    // 也绝不让 id 为 null 的配方进入下游按 id 查表）
                    GTLModular.LOGGER.debug("[gtlmodular] 线程引擎：存档第 {} 条的配方 id 缺失或不合法，跳过该条", i);
                    continue;
                }
                recipe.setId(id);
                Batch batch = new Batch(recipe);
                batch.progress = Math.max(0, entry.getInt(TAG_PROGRESS));   // 进度一并恢复
                batches.add(batch);
            } catch (RuntimeException e) {
                // 单条坏档绝不能把整个读档流程带崩：跳过这一条，其余照常恢复
                GTLModular.LOGGER.debug("[gtlmodular] 线程引擎：恢复存档第 {} 条时异常（{}），跳过该条",
                        i, e.toString());
            }
        }
        if (!batches.isEmpty()) {
            GTLModular.LOGGER.debug("[gtlmodular] 线程引擎：从存档恢复 {} 条批次", batches.size());
        }
    }
}
