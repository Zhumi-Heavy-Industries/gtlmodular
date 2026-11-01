package com.gtlmodular.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * <b>片 C：{@link PatternArchiveTarget} 的「现役猪咪样板总成」适配器</b>
 * （{@link MoltenPatternAssemblyPartMachine}，继承 gtlcore 具体类 {@code MEPatternBufferPartMachine}）。
 *
 * <h3>本片真正的工作量在哪</h3>
 * 惰性机那边接口方法几乎都能"一对一转发到已有方法"；现役机<b>只有四个 NBT 方法</b>
 * （{@code copyToTag/copyFromTag/pasteFromTag/cutToTag}，且坐标系是 gtlcore 的固定 128/640），
 * {@code growCapacityFor*} / {@code getCapacity()} / {@code onPatternInventoryChanged()}
 * <b>一个都没有</b>。所以：
 * <ul>
 *   <li>{@link #growForBatchInsert(int)} / {@link #growToCapacity(int)} = <b>no-op</b>
 *       （现役机容量<b>注册期固定、不可变</b>：{@code slotsForTier(tier)} 定死，
 *       六个并行数组硬绑 {@code maxPatternCount}）；</li>
 *   <li>能否装下全部落在 {@link #canFit(int)} + 引擎的"整体拒绝"那一步：
 *       {@code needed > maxCapacity} ⇒ <b>在写任何槽之前</b>返回失败（见
 *       {@link MoltenPatternAssemblyPartMachine#archiveCanFit(int)}）；</li>
 *   <li>收尾 = 现役机自己的既有通道
 *       （{@link MoltenPatternAssemblyPartMachine#afterArchiveBulkChange()}：
 *       单趟 {@code refreshAllByProduct()} + 静默作废改写缓存，<b>绝不</b>逐槽
 *       {@code onPatternChange}）。</li>
 * </ul>
 *
 * <p>本类在同一个包（{@code com.gtlmodular.common.machine}）里 ——
 * 现役机那些需要 {@code protected} 成员（{@code maxPatternCount} / {@code catalystItems} /
 * {@code catalystFluids} / {@code cacheRecipeCount} / {@code refreshAllByProduct}）的动作
 * <b>只能写在 {@link MoltenPatternAssemblyPartMachine} 这个子类里</b>
 * （本适配器不是 gtlcore 的子类，跨包访问不到 {@code protected} 成员），
 * 所以这里是"薄转发"，真正的方法体在机器类那段「片 C：样板仓归档」里。
 */
public final class MoltenPatternArchiveTarget implements PatternArchiveTarget {

    private final MoltenPatternAssemblyPartMachine machine;

    public MoltenPatternArchiveTarget(MoltenPatternAssemblyPartMachine machine) {
        this.machine = machine;
    }

    /** 被包住的机器（诊断用）。 */
    public MoltenPatternAssemblyPartMachine machine() {
        return this.machine;
    }

    // ==================================================================================
    // 一、只读判据
    // ==================================================================================

    @Override
    public int capacity() {
        // 现役机：容量 == 硬上限（构造期定死），没有"惰性增长"这一档
        return machine.archiveCapacity();
    }

    @Override
    public int maxCapacity() {
        return machine.archiveMaxCapacity();
    }

    @Override
    public int reserve() {
        // 现役机没有"预留空位"概念 ⇒ 0（needed 语义变成"要装到这格就得有这么多槽"）
        return machine.archiveReserve();
    }

    /**
     * ★★ <b>「装得下吗」的预检</b>：现役机唯一的容量判据。
     *
     * <p>落点是引擎第 ② 步（{@link LazyPatternArchiveCodec#apply}）：这一句返回 false ⇒
     * 引擎立刻以 {@code gtlmodular.pattern_assembly.archive.result.reject} 整体拒绝，
     * 此刻<b>还没扩容（不可能扩）、还没碰任何槽</b>。现役机 {@code capacity() == maxCapacity()}
     * ⇒ 这个分支比惰性机更早、更频繁地命中，这是<b>正确行为</b>（设计稿第四条）。
     */
    @Override
    public boolean canFit(int needed) {
        return machine.archiveCanFit(needed);
    }

    @Override
    public int highestUsedSlot() {
        return machine.archiveHighestUsedSlot();
    }

    /** 只读一个槽的原始内容；读异常照原样外抛（引擎按"有东西"处理 ⇒ 少写一条，绝不覆盖）。 */
    @Override
    public ItemStack patternAt(int slot) {
        return machine.archivePatternAt(slot);
    }

    @Override
    public boolean isRemote() {
        return machine.isRemote();
    }

    @Override
    public BlockPos pos() {
        return machine.getPos();
    }

    // ==================================================================================
    // 二、档案读取
    // ==================================================================================

    @Override
    public boolean includeSettingsInArchive() {
        return machine.isArchiveIncludeSettings();
    }

    @Override
    public boolean includeNameInArchive() {
        return machine.isArchiveIncludeName();
    }

    @Override
    public LazyPatternArchiveCodec.PlaceMode placeMode() {
        return machine.archivePlaceMode();
    }

    @Override
    public LazyPatternArchiveCodec.Payload buildArchive(boolean includeSettings, boolean includeName) {
        return machine.buildArchivePayload(includeSettings, includeName);
    }

    // ==================================================================================
    // 三、档案写入
    // ==================================================================================

    @Override
    public void writePattern(int slot, ItemStack stack) {
        machine.archiveWritePattern(slot, stack);
    }

    @Override
    public void writeRecipeType(int slot, int recipeType) {
        machine.writeArchivedRecipeType(slot, recipeType);
    }

    @Override
    public void writeManualType(int slot, ResourceLocation typeId) {
        machine.writeArchivedManualType(slot, typeId);
    }

    @Override
    public boolean writeCatalyst(int slot, CompoundTag catalyst) {
        return machine.writeArchivedCatalyst(slot, catalyst);
    }

    @Override
    public void writeSettings(CompoundTag settings) {
        machine.writeArchivedSettings(settings);
    }

    @Override
    public void writeName(String name) {
        machine.writeArchivedName(name);
    }

    // ==================================================================================
    // 四、容量准备 / 清源 / 收尾
    // ==================================================================================

    /**
     * <b>no-op</b>：现役机容量不可变（注册期固定），惰性机那套"批量一次扩容"在这里无事可做。
     *
     * <p>为什么这里可以安全地什么都不做：引擎在调本方法<b>之前</b>已经用
     * {@link #canFit(int)} 做过整体拒绝 ⇒ 走到这里时 {@code needed <= maxCapacity} 必然成立
     * （本机现有槽位就已经够用）。反过来，若不做那道预检就写，就会出现"写一半装不下"的
     * 半成品 —— 这正是本片必须守住的东西。
     */
    @Override
    public void growForBatchInsert(int count) {
        // 故意的 no-op（理由见上）；★ 2026-10-02：但它同时是"批量写入窗口"的开启点 ——
        // 引擎在写循环**之前**必定调到这里一次（追加与对齐两种模式各走一个），
        // 窗口内逐槽 setItemDirect 触发的 onPatternChange 会降级为"只做父类记账"，
        // AE 通知仍由收尾那一次 afterBulkChange() 负责 ✓
        machine.beginArchiveBulkWrite();
    }

    /** <b>no-op</b>：同 {@link #growForBatchInsert(int)}（现役机容量不可变）。 */
    @Override
    public void growToCapacity(int needed) {
        // 故意的 no-op（理由见上）；★ 2026-10-02：同 growForBatchInsert，开批量窗口 ✓
        machine.beginArchiveBulkWrite();
    }

    @Override
    public void clearAll() {
        machine.clearArchiveContent();
    }

    @Override
    public void afterBulkChange() {
        machine.afterArchiveBulkChange();
    }
}
