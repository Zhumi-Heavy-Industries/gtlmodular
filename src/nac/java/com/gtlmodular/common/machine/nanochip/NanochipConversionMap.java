package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtlmodular.common.data.nanochip.NanochipCircuitComponents;
import com.gtlmodular.common.data.nanochip.NanochipRecipeTypes;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从换算配方构建真件与 CC 映射，避免手工维护第二份物品对应表；反向映射以 CC 身份为准。 */
public final class NanochipConversionMap {

    private NanochipConversionMap() {}

    private static volatile RecipeManager builtFor = null;

    private static volatile Map<Item, ItemStack> realToCc = Map.of();

    private static volatile Map<Item, ItemStack> ccToReal = Map.of();

    private static volatile int accepted = 0;

    private static volatile int realSideCollisions = 0;
    private static volatile int ccSideCollisions = 0;

    private static volatile int disputedResolved = 0;

    private static volatile int disputedUnresolved = 0;

    private static volatile Map<Item, ItemStack> disputedPicks = Map.of();

    public static void ensure(MinecraftServer server) {
        if (server == null) {
            return;
        }
        RecipeManager rm = server.getRecipeManager();
        if (rm == builtFor) {
            return;
        }
        synchronized (NanochipConversionMap.class) {
            if (rm == builtFor) {
                return;
            }
            Map<Item, ItemStack> fwd = new LinkedHashMap<>();
            Map<Item, ItemStack> rev = new LinkedHashMap<>();
            Map<Item, java.util.List<ItemStack>> fwdAll = new LinkedHashMap<>();
            int n = 0;
            int dupCc = 0;
            for (Recipe<?> recipe : rm.getRecipes()) {
                if (recipe.getType() != NanochipRecipeTypes.CONVERSION) {
                    continue;
                }
                if (!(recipe instanceof GTRecipe gt)) {
                    continue;
                }
                List<ItemStack> in = RecipeHelper.getInputItems(gt);
                List<ItemStack> out = RecipeHelper.getOutputItems(gt);
                if (in.size() != 1 || out.size() != 1) {
                    continue;
                }
                ItemStack real = in.get(0);
                ItemStack cc = out.get(0);
                if (real.isEmpty() || cc.isEmpty()) {
                    continue;
                }
                n++;
                fwdAll.computeIfAbsent(real.getItem(), k -> new java.util.ArrayList<>(1)).add(cc.copyWithCount(1));
                if (rev.put(cc.getItem(), real.copyWithCount(1)) != null) {
                    dupCc++;
                }
            }
            int disputed = 0;
            int unresolvedDisputed = 0;
            Map<Item, ItemStack> picks = new LinkedHashMap<>();
            for (Map.Entry<Item, java.util.List<ItemStack>> e : fwdAll.entrySet()) {
                java.util.List<ItemStack> cands = e.getValue();
                if (cands.size() == 1) {
                    fwd.put(e.getKey(), cands.get(0));
                    continue;
                }
                disputed++;
                String realPath = realPathOf(e.getKey());
                ItemStack pick = preferByMaterialName(realPath, cands);
                if (pick == null) {
                    unresolvedDisputed++; // 材料名也无法消除歧义时拒绝封装，避免把真件转换成错误的 CC。
                    continue;
                }
                fwd.put(e.getKey(), pick);
                picks.put(e.getKey(), pick);
            }
            realToCc = Map.copyOf(fwd);
            ccToReal = Map.copyOf(rev);
            accepted = n;
            realSideCollisions = disputed;
            ccSideCollisions = dupCc;
            disputedResolved = picks.size();
            disputedUnresolved = unresolvedDisputed;
            disputedPicks = Map.copyOf(picks);
            builtFor = rm;
        }
    }

    private static String realPathOf(Item item) {
        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
        return id == null ? "" : id.toString();
    }

    private static ItemStack preferByMaterialName(String realPath, List<ItemStack> cands) {
        int colon = realPath.indexOf(':');
        String path = colon < 0 ? realPath : realPath.substring(colon + 1);
        if (path.isEmpty()) {
            return null;
        }
        int underscore = path.lastIndexOf('_');
        while (underscore > 0) {
            String token = path.substring(0, underscore);
            ItemStack hit = null;
            int hits = 0;
            for (ItemStack cand : cands) {
                String ccPath = realPathOf(cand.getItem());
                int c2 = ccPath.indexOf(':');
                String ccBody = c2 < 0 ? ccPath : ccPath.substring(c2 + 1);
                if (ccBody.contains(token)) {
                    hit = cand;
                    hits++;
                }
            }
            if (hits == 1) {
                return hit;
            }
            if (hits > 1) {
                return null;
            }
            underscore = path.lastIndexOf('_', underscore - 1);
        }
        return null;
    }

    public static ItemStack encapsulate(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (isCc(stack)) {
            return stack.copy(); // 已经是 CC 时保持原样，避免重复换算。
        }
        ItemStack cc = realToCc.get(stack.getItem());
        return cc == null ? ItemStack.EMPTY : cc.copyWithCount(stack.getCount());
    }

    public static ItemStack materialize(ItemStack stack) {
        if (stack.isEmpty() || !isCc(stack)) {
            // 网络旧存档可能直接保存真件，原样交回才能兼容这类库存。
            return stack;
        }
        ItemStack real = ccToReal.get(stack.getItem());
        return real == null ? stack : real.copyWithCount(stack.getCount());
    }

    public static Boolean materializableFlag(ItemStack stack) {
        if (!isCc(stack)) {
            return null;
        }
        return !((NanochipCircuitComponents.CcItem) stack.getItem()).circuitComponent().processed;
    }

    public static boolean isCc(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof NanochipCircuitComponents.CcItem;
    }

    public static int size() {
        return accepted;
    }

    public static int realSideCollisions() {
        return realSideCollisions;
    }

    public static int ccSideCollisions() {
        return ccSideCollisions;
    }

    public static int resolvedDisputes() {
        return disputedResolved;
    }

    public static int unresolvedDisputes() {
        return disputedUnresolved;
    }

    public static Map<Item, ItemStack> resolvedPicks() {
        return disputedPicks;
    }
}
