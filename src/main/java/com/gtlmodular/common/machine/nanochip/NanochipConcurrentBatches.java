package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gtlmodular.GTLModular;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * ★★★ 2026-10-28【独立跨配方线程 · 并发批次表】★★★
 * <b>按上游作者给的参考实现（{@code _census/并发批次讲解/ConcurrentBatches.java}）重写的内核本体。</b>
 *
 * <h2>一句话</h2>
 * 并发批次 = 同一台机器上同时跑好几条配方，<b>每条各有各的进度和产出，不用排队</b>。
 * 数据结构就是一张极简的 {@code List<Batch>}：{@link Batch} = 「一份已经算好的配方 + 自己的
 * {@code progress} + 开跑时钉死的 {@code duration}」；机器同时挂几条由 {@link Host#slots()} 决定，
 * 槽满了不再收新配方，有批次跑完空出位置马上补一条。
 *
 * <h2>每个 server tick 的四步顺序（★ 顺序反了会出各种怪事，照这个来）</h2>
 * <ol>
 *   <li><b>逐条推进</b>（{@link #tick} 第一个 while 循环 → {@link #tickBatch}）：能量/输入不够 ⇒
 *       <b>原地等，这条不涨进度、也不要把它摘掉</b>；</li>
 *   <li><b>涨满的批次摘掉</b>（同一次遍历里 {@code batch.finished()} 那一支）：先
 *       {@code afterWorking}／{@code postWorking}，<b>再出料</b>
 *       （这两件事都在 {@link Host#finishOutputs} 里，见
 *       {@code NanochipThreadedRecipeLogic.finishOutputs}）；</li>
 *   <li><b>有空位就补新批次</b>（{@link #tick} 第二个 while 循环）：找配方（{@link Host#findBatch}）
 *       → <b>原子扣料</b>（{@link Host#consumeInputs}）→ 进集合。
 *       <b>扣料只在这里做、一条只扣一次</b>——四个 Host 方法里只有 {@code consumeInputs} 会写世界；</li>
 *   <li><b>把第 0 条的进度写回机器的 {@code progress}/{@code duration} 展示字段</b> —— 这一步不由本类做
 *       （本类不碰 {@code RecipeLogic} 的字段），由调用方在 {@link #tick} 之后做，
 *       落点见 {@code NanochipThreadedRecipeLogic.serverTick} 的『④ 展示回写』那一段 ⇒ GUI 才看得到在动。</li>
 * </ol>
 *
 * <h2>断档处理（★ 照参考实现）</h2>
 * 找不到配方就<b>停在第三步</b>，不要回退成「整机不跑」（{@code recipe == null ⇒ break}）；
 * 扣料扣不齐就<b>不收这条</b>（{@code consumeInputs == false ⇒ break}）——宁愿少跑一条，
 * 也绝不吞料、也绝不让机器一直报缺料。下一个 tick 会重新走一遍第三步。
 *
 * <h2>展示/联动读口</h2>
 * {@link #size()} + {@link #get(int)} 遍历一遍即可拿到每条批次的 {@code progress}／{@code duration}／
 * {@code recipe}（Jade 那种「一个批次画一条进度条」直接用它）。
 *
 * <h2>存读档（见 {@link #save} / {@link #load}）</h2>
 * 配方整条用 {@code GTRecipeSerializer.CODEC} 编进 NBT，<b>配方 id 必须单独再存一份</b>
 * ——因为 CODEC 不编 id，读档后不补回去会按 null 查表崩掉（这是作者踩过的坑，逐字照抄）；
 * 进度一并存读。另有一格 {@code runs}（★ 我们比参考实现多的一格，理由写在 {@link #save} 上）。
 *
 * <h2>零混入 / 零 {@code @Persisted}</h2>
 * 本类不新增任何 {@code @Persisted} 字段、不碰 {@code MANAGED_FIELD_HOLDER}；存档只走调用方那一句
 * 「把本表写进一个<b>追加</b> NBT 键」。
 */
public final class NanochipConcurrentBatches {

    /** 一条并发批次。 */
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
     * 批次表要「机器侧」（在本模组里 = {@code NanochipThreadedRecipeLogic} 自己）回答的六件事
     * —— 与参考实现的 Host 逐个对应。
     *
     * <p>把「派活算法」与「怎么跟机器打交道」切开的好处：本类只看这六个方法，
     * 于是它能被单独看懂、单独核对，也不需要知道机器的任何具体类型。
     */
    public interface Host {

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
     * ★ <b>存档键（追加键，键名沿用上一轮未改）</b>：{@code "gtlmodular_nanochip_threads"}。
     *
     * <p><b>向后兼容</b>：老存档里没有这个键 ⇒ {@code tag.getList(...)} 返回空表 ⇒ {@link #load}
     * 只把批次表清空 ⇒ 逐字回落成「没有待恢复批次」的行为（不报错、不吞料）。
     */
    public static final String TAG_BATCHES = "gtlmodular_nanochip_threads";

    /** 批次表里「进度」的键（{@code int}）。 */
    private static final String TAG_PROGRESS = "progress";

    /** 批次表里「配方 id」的键（{@code String}；★ 必须单独存一份，见类注释）。 */
    private static final String TAG_ID = "id";

    /** 批次表里「配方本体」的键（整条配方走 {@code GTRecipeSerializer.CODEC}）。 */
    private static final String TAG_RECIPE = "recipe";

    /** ★ 批次表里「本篇运行份数」的键（{@code long}）——<b>我们比参考实现多的一格</b>，理由见 {@link #save}。 */
    private static final String TAG_RUNS = "runs";

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
     * 每个 server tick 调一次，返回这一 tick 有没有批次真的在推进。
     *
     * <p>方法体就是参考实现那四步里的<b>前三步</b>（第四步「展示回写」由调用方做，理由见类注释）：
     * ① 逐条推进（不够就原地等，不涨进度也不摘掉）→ ② 涨满的摘掉（先收尾再出料）→ ③ 有空位就补新批次。
     *
     * <p><b>不用 for-each</b>：第②步要在遍历中摘元素，用 {@link Iterator#remove()} 才安全
     * （参考实现也是这个写法）。
     */
    public boolean tick(Host host) {
        if (host == null) {
            return false;
        }
        boolean working = false;

        // ── ① 逐条推进 + ② 涨满的摘掉（同一次遍历做完：先推进这一条，再判它涨满没有）──
        Iterator<Batch> iterator = batches.iterator();
        while (iterator.hasNext()) {
            Batch batch = iterator.next();
            if (tickBatch(host, batch)) {
                working = true;
            }
            if (batch.finished()) {
                // 先摘掉（它已经跑完了，不该再占着槽位），再收尾 + 出料
                iterator.remove();
                host.finishOutputs(batch.recipe);
            }
        }

        // ── ③ 有空位就补新批次：找配方 → 原子扣料 → 进集合（原子扣料只在这里做、一条只扣一次）──
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
        return working;
    }

    /**
     * ① 推进一步：<b>输入不够 ⇒ 原地等</b>（返回 false ⇒ 这条不涨进度，也不被摘掉）。
     *
     * <p>与参考实现逐字同构：够不够由 {@link Host#tickInputs} 判（它会真扣这一 tick 的 EU/流体），
     * 本类只负责「够就 {@code progress++}」。
     */
    private static boolean tickBatch(Host host, Batch batch) {
        if (!host.tickInputs(batch.recipe)) {
            return false;
        }
        batch.progress++;
        return true;
    }

    /**
     * 存档：<b>每条批次一格 CompoundTag，里面有四个键</b>。
     *
     * <table border="1">
     *   <tr><th>键</th><th>类型</th><th>说明</th></tr>
     *   <tr><td>{@code recipe}</td><td>CompoundTag</td><td>整条配方，走 {@code GTRecipeSerializer.CODEC}
     *       （编码内容：配方类型 / 输入 / 产出 / tick 输入 / tick 产出 / 概率逻辑 / 配方条件 /
     *        物品动作 / data / duration / isFuel —— 依据见报告 ⑤ 的逐条对照）</td></tr>
     *   <tr><td>{@code id}</td><td>String</td><td>★ 配方 id（<b>单独再存一份</b>）：CODEC <b>不编 id</b>，
     *       读档后不补回去 ⇒ 后续按 id 查表会拿到 null（作者踩过的坑）</td></tr>
     *   <tr><td>{@code progress}</td><td>int</td><td>这条批次自己的进度（tick）</td></tr>
     *   <tr><td>{@code runs}</td><td>long</td><td>★ <b>我们比参考实现多的那一格</b>：
     *       gtlcore 扩展字段 {@code realParallels}（= 这条批次同时跑几份）<b>不在 CODEC 里</b>
     *       （CODEC 的 14 个分量见报告 ⑤），而 gtlcore 在给概率产出掷骰时会把它当运行份数用
     *       （{@code RecipeRunner.handle → LongChanceLogic.roll(..., getRealParallels(), ...)}，
     *       javap 现读 {@code _census/_javap_simple_reciperunner.txt:174}）⇒ 不补这一格的话，
     *       读档后的批次会在掷骰时按「0 份」算，产出（尤其是带概率的副产）会整体消失。
     *       <b>老档缺这一格 ⇒ 值为 0 ⇒ 不补写</b>（行为逐字等于参考实现，安全回退）。</td></tr>
     * </table>
     *
     * <p>空表也会写（写一个空 {@code ListTag}）：读档侧因此不必理解「键不存在」与「空表」的差别。
     */
    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Batch batch : batches) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(TAG_PROGRESS, batch.progress);
            entry.putString(TAG_ID, batch.recipe.getId() == null ? "" : batch.recipe.getId().toString());
            entry.putLong(TAG_RUNS, runsOf(batch.recipe));
            GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, batch.recipe)
                    .result()
                    .ifPresent(encoded -> entry.put(TAG_RECIPE, encoded));
            list.add(entry);
        }
        tag.put(TAG_BATCHES, list);
    }

    /**
     * 读档：把批次表整体读回来（<b>先清空再读</b>）。
     *
     * <p>三条纪律：
     * <ol>
     *   <li><b>缺键 / 空表 ⇒ 安全回退</b>：什么都不加，逐字等于「没有待恢复批次」；</li>
     *   <li><b>单条坏掉不影响整表</b>：某一条的配方本体缺失 / 解析失败 / 没 id ⇒
     *       <b>只跳过那一条</b>（并打一条 debug 说明原因），其余照常恢复；</li>
     *   <li><b>不再扣一次料</b>：料在存档前就扣过了 ⇒ 这里只拼 {@link Batch}，绝不调
     *       {@link Host#consumeInputs}。</li>
     * </ol>
     *
     * <p>恢复是<b>尽力而为</b>（与参考实现一致）：配方被数据包改掉 / 部件被拆 ⇒ 那条批次直接消失，
     * 已经扣掉的料<b>不退回</b>。
     */
    public void load(CompoundTag tag) {
        batches.clear();
        ListTag list = tag.getList(TAG_BATCHES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Tag encoded = entry.get(TAG_RECIPE);
            if (encoded == null) {
                GTLModular.LOGGER.debug("[gtlmodular] NAC 并发批次：存档第 {} 条没有配方本体键（{}），跳过该条",
                        i, TAG_RECIPE);
                continue;
            }
            GTRecipe recipe = GTRecipeSerializer.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
            if (recipe == null) {
                GTLModular.LOGGER.debug("[gtlmodular] NAC 并发批次：存档第 {} 条的配方解析失败，跳过该条", i);
                continue;
            }
            if (recipe.getId() == null) {
                // ★ CODEC 不编 id ⇒ 把单独存的那一份补回去（不补 ⇒ 后续按 id 查表会拿到 null）
                recipe.setId(restoredId(entry, i));
            }
            long runs = entry.getLong(TAG_RUNS);   // 老档缺这一格 ⇒ 0 ⇒ 不补（安全回退）
            if (runs > 0L) {
                applyRuns(recipe, runs);
            }
            Batch batch = new Batch(recipe);
            batch.progress = entry.getInt(TAG_PROGRESS);   // 进度一并恢复
            batches.add(batch);
        }
        if (!batches.isEmpty()) {
            GTLModular.LOGGER.debug("[gtlmodular] NAC 并发批次：从存档恢复 {} 条批次", batches.size());
        }
    }

    /** 读档时的配方 id：优先用单独存的那一份；取不到就给一个<b>确定唯一</b>的兜底 id。 */
    private static ResourceLocation restoredId(CompoundTag entry, int index) {
        String stored = entry.getString(TAG_ID);
        ResourceLocation parsed = stored.isEmpty() ? null : ResourceLocation.tryParse(stored);
        // 兜底 id 的目的只有一个：绝不让 setId(null)（id 为 null 的配方在下游按 id 查表时会崩）
        return parsed != null ? parsed : new ResourceLocation(GTLModular.MOD_ID, "nanochip_batch_" + index);
    }

    /** 这一条批次的「运行份数」（gtlcore 扩展字段；取不到 ⇒ 退回配方并行字段，再退回 1）。 */
    private static long runsOf(GTRecipe recipe) {
        long runs = realParallels(recipe);
        return runs > 0L ? runs : Math.max(1L, (long) recipe.parallels);
    }

    /** 读 gtlcore 扩展字段 {@code realParallels}；读不到返回 -1（调用方按「取不到」处理）。 */
    private static long realParallels(GTRecipe recipe) {
        try {
            return IGTRecipe.of(recipe).getRealParallels();
        } catch (RuntimeException e) {
            // 取不到就是「这一条没有扩展字段」⇒ 退回并行字段（保守一侧），绝不外抛进 tick
            GTLModular.LOGGER.debug("[gtlmodular] NAC 并发批次：读取 realParallels 失败（{}），改用并行字段",
                    e.toString());
            return -1L;
        }
    }

    /** 把「运行份数」写回 gtlcore 扩展字段（失败只记一行 debug，不影响恢复本身）。 */
    private static void applyRuns(GTRecipe recipe, long runs) {
        try {
            IGTRecipe.of(recipe).setRealParallels(runs);
        } catch (RuntimeException e) {
            GTLModular.LOGGER.debug("[gtlmodular] NAC 并发批次：写回 realParallels={} 失败（{}），该条批次照常恢复",
                    runs, e.toString());
        }
    }
}
