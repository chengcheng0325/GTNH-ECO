package ecoaegtnh.recipe;

import static gregtech.api.enums.GTValues.RA;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.MINUTES;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeConstants.AssemblyLine;
import static gregtech.api.util.GTRecipeConstants.RESEARCH_ITEM;
import static gregtech.api.util.GTRecipeConstants.SCANNING;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ecoaegtnh.block.estorage.BlockEcoStorageCapacitance;
import ecoaegtnh.block.estorage.BlockEcoStorageCasing;
import ecoaegtnh.block.estorage.BlockEcoStorageDrive;
import ecoaegtnh.block.estorage.BlockEcoStorageMEBus;
import ecoaegtnh.block.estorage.BlockEcoStorageVent;
import ecoaegtnh.item.estorage.CellSize;
import ecoaegtnh.item.estorage.StorageType;
import ecoaegtnh.registry.RegistryEcal;
import ecoaegtnh.registry.RegistryItems;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.objects.OreDictItemStack;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.recipe.Scanning;

/**
 * ECO 存储阵列（E-Storage）的 GT 配方注册类。
 * <p>
 * 配方写法的完整教学见 {@code docs/RECIPE_WRITING_GUIDE.md}（中文）。
 * <p>
 * t114o（用户）：配方体系重做——存储盘 = 外壳 + 组件的工作台无序合成；256k 组件 + L4 外壳
 * 各有物品/流体/源质 6 条组装机配方；另保留用户逐条重做的 6 个配方（外壳/驱动器/电容/
 * ME总线/通风口组装机 + 控制器工作台合成）。E-Calculator 全部配方（ecal.*）已删除，
 * 等待用户重新设计。
 * <p>
 * 空值安全（t7）：AE2/ae2fc/TE4/dreamcraft 关联物品在 FML init 期间可能尚未注册，所以每个
 * 配方都做 null 检查——任一输入/输出为 null 时跳过并打警告，绝不把 null 传给配方构建器。
 */
public final class Recipes {

    private static final Logger LOG = LogManager.getLogger("ECOAEGTNH");

    private Recipes() {}

    /** 尺寸索引顺序 == CellSize.values() 的顺序。 */
    private static final CellSize[] SIZES = CellSize.values();

    /** t102/t105：注册计数器——服务端日志每次启动都会报告总数。 */
    private static int registeredAssemblerRecipes = 0;
    private static int registeredALRecipes = 0;
    private static int skippedRecipes = 0;

    public static void register() {
        registerCells();
        registerComponentsAndHousings();
        registerComponentChain();
        registerEcal();
        registerEcalParallelCores();
        registerEcalThreadCores();
        registerEcalFlashCells();
        registerPartsAndControllers();
        registerCraftingRecipes();
        LOG.info(
            "ECO recipes registered: {} assembler + {} assembly-line (estorage: cells=27 shapeless, components/housings="
                + "6 assembler, component chain=24 assembler/assembly-line + 2 space-assembler, parts/controllers=5 "
                + "assembler + 1 workbench, ecalculator=6 assembler + 9 parallel cores + 6 thread cores + 3 flash "
                + "cells + 1 workbench), skipped={}",
            registeredAssemblerRecipes,
            registeredALRecipes,
            skippedRecipes);
    }

    // ------------------------------------------------------------------
    // 存储盘（27 个）——外壳(类型,等级) + 组件(类型,尺寸) 的工作台无序合成。
    // t114o（用户）：盘配方从组装机/装配线改为"组件 + 外壳"无序合成（ShapelessOreRecipe）。
    // 外壳等级对应：k 级 256k/1024k/4096k → L4 外壳、M 级 16M/64M/256M → L6 外壳、
    // 大 M 级 1024M/4096M/16384M → L9 外壳。
    // ------------------------------------------------------------------
    private static void registerCells() {
        for (StorageType type : StorageType.values()) {
            for (int i = 0; i < 9; i++) {
                CellSize size = SIZES[i];
                int tier = i < 3 ? 0 : i < 6 ? 1 : 2;
                ItemStack out = cell(type, size);
                ItemStack h = housing(type, tier);
                ItemStack c = component(type, size);
                String name = "estorage.cell_" + type.label + "_" + size.label;
                if (out == null || h == null || c == null) {
                    // t7 空值安全：源质系列在 ThaumicEnergistics 缺席时为 null，跳过 + 警告。
                    skippedRecipes++;
                    LOG.warn("Skipping ECO crafting recipe '{}': a material is not registered yet (null).", name);
                    continue;
                }
                cpw.mods.fml.common.registry.GameRegistry
                    .addRecipe(new net.minecraftforge.oredict.ShapelessOreRecipe(out, h, c));
            }
        }
    }

    // ------------------------------------------------------------------
    // t114o（用户）：256k 组件（物品/流体/源质）+ L4 外壳（物品/流体/源质）共 6 条配方——
    // 给保留的存储盘配方（registerCells）提供外壳/组件输入。全部 EV 1920 EU/t、10 秒、
    // 焊锡 144mb；外壳配方带编程电路（1/2/3），组件配方无电路。
    // 输入物品均为用户从游戏（NEI）复制的 id，经 findItemStack 按注册名运行时解析。
    // ------------------------------------------------------------------
    private static void registerComponentsAndHousings() {
        FluidStack solder144 = Materials.SolderingAlloy.getMolten(144);

        // 256k 物品组件（无电路）：AE2 256k 存储组件 + 物品处理器 III（dreamcraft）+
        // 工程处理器 + 数据电路 ×2 + 进阶电路 ×4。
        // t114x（用户）：电路板输入用真正的矿典 OreDictItemStack（itemInputs(Object...)
        // 展开该矿典全部物品；GTOreDictUnificator.get 只返回统一后的单个物品——circuitAdvanced
        // 统一到 IC2 高级电路板，NEI 就显示成 IC2:itemPartCircuitAdv）。
        tryAddAssemblerNoCircuit(
            "estorage.component_item_256k",
            new Object[] { appeng.api.AEApi.instance()
                .definitions()
                .materials()
                .cell256kPart()
                .maybeStack(1)
                .orNull(), findItemStack("dreamcraft", "EngineeringProcessorItemEmeraldCore", 0, 1),
                appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .engProcessor()
                    .maybeStack(1)
                    .orNull(),
                GTOreDictUnificator.get("circuitData", 2), new OreDictItemStack("circuitAdvanced", 4) },
            new FluidStack[] { solder144 },
            RegistryItems.itemComponent(CellSize.K_256),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // 256k 流体组件（无电路）：ae2fc 256k 流体存储组件（fluid_part/4）+ 流体处理器 II
        // （dreamcraft）+ 工程处理器 ×8 + 数据电路 ×2 + 进阶电路 ×4。
        tryAddAssemblerNoCircuit(
            "estorage.component_fluid_256k",
            new Object[] { findItemStack("ae2fc", "fluid_part", 4, 1),
                findItemStack("dreamcraft", "EngineeringProcessorFluidEmeraldCore", 0, 1), appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .engProcessor()
                    .maybeStack(8)
                    .orNull(),
                GTOreDictUnificator.get("circuitData", 2), new OreDictItemStack("circuitAdvanced", 4) },
            new FluidStack[] { solder144 },
            RegistryItems.fluidComponent(CellSize.K_256),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // 256k 源质组件（无电路）：TE4 源质存储组件（storage.component/5）+ 源质处理器 I
        // （dreamcraft）+ 工程处理器 ×4 + 数据电路 ×2 + 进阶电路 ×4。
        tryAddAssemblerNoCircuit(
            "estorage.component_essentia_256k",
            new Object[] { findItemStack("thaumicenergistics", "storage.component", 5, 1),
                findItemStack("dreamcraft", "EngineeringProcessorEssentiaPulsatingCore", 0, 1),
                appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .engProcessor()
                    .maybeStack(4)
                    .orNull(),
                GTOreDictUnificator.get("circuitData", 2), new OreDictItemStack("circuitAdvanced", 4) },
            new FluidStack[] { solder144 },
            RegistryItems.essentiaComponent(CellSize.K_256),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // L4 物品外壳（电路 1）：进阶电路 ×2 + gt.metaitem.01/17030 ×3 + /17516 ×1 +
        // /27516 ×2 + 逻辑处理器 + 计算处理器。
        tryAddAssembler(
            "estorage.housing_item_l4",
            new Object[] { new OreDictItemStack("circuitAdvanced", 2),
                findItemStack("gregtech", "gt.metaitem.01", 17030, 3),
                findItemStack("gregtech", "gt.metaitem.01", 17516, 1),
                findItemStack("gregtech", "gt.metaitem.01", 27516, 2), appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .logicProcessor()
                    .maybeStack(1)
                    .orNull(),
                appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .calcProcessor()
                    .maybeStack(1)
                    .orNull() },
            new FluidStack[] { solder144 },
            RegistryItems.itemHousing(0),
            1,
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // L4 流体外壳（电路 2）：输入同物品外壳（17030×3 / 17516 / 27516×2 + 逻辑/计算处理器），
        // 但工程处理器换成 24 号（工程处理器）×1、计算处理器 23 ×1。
        tryAddAssembler(
            "estorage.housing_fluid_l4",
            new Object[] { new OreDictItemStack("circuitAdvanced", 2),
                findItemStack("gregtech", "gt.metaitem.01", 17030, 3),
                findItemStack("gregtech", "gt.metaitem.01", 17516, 1),
                findItemStack("gregtech", "gt.metaitem.01", 27516, 2), appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .engProcessor()
                    .maybeStack(1)
                    .orNull(),
                appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .calcProcessor()
                    .maybeStack(1)
                    .orNull() },
            new FluidStack[] { solder144 },
            RegistryItems.fluidHousing(0),
            2,
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // L4 源质外壳（电路 3）：进阶电路 ×2 + 17030 ×3 + 17516 + 27516 ×2 + 工程处理器
        // + 计算处理器。
        tryAddAssembler(
            "estorage.housing_essentia_l4",
            new Object[] { new OreDictItemStack("circuitAdvanced", 2),
                findItemStack("gregtech", "gt.metaitem.01", 17030, 3),
                findItemStack("gregtech", "gt.metaitem.01", 17516, 1),
                findItemStack("gregtech", "gt.metaitem.01", 27516, 2), appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .engProcessor()
                    .maybeStack(1)
                    .orNull(),
                appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .calcProcessor()
                    .maybeStack(1)
                    .orNull() },
            new FluidStack[] { solder144 },
            RegistryItems.essentiaHousing(0),
            3,
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // ---------- L6 外壳（ZPM，5s，无流体，电路 1/2/3） ----------
        // 输入：终极电路×2 + metaitem.01/17317×3 + /17516 + /27516×2 + 逻辑处理器×4 + 计算处理器×4
        tryAddAssembler(
            "estorage.housing_item_l6",
            new ItemStack[] { GTOreDictUnificator.get("circuitUltimate", 2),
                findItemStack("gregtech", "gt.metaitem.01", 17317, 3),
                findItemStack("gregtech", "gt.metaitem.01", 17516, 1),
                findItemStack("gregtech", "gt.metaitem.01", 27516, 2), appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .logicProcessor()
                    .maybeStack(4)
                    .orNull(),
                appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .calcProcessor()
                    .maybeStack(4)
                    .orNull() },
            new FluidStack[0],
            RegistryItems.itemHousing(1),
            1,
            TierEU.RECIPE_ZPM,
            5 * SECONDS);
        tryAddAssembler(
            "estorage.housing_fluid_l6",
            new ItemStack[] { GTOreDictUnificator.get("circuitUltimate", 2),
                findItemStack("gregtech", "gt.metaitem.01", 17317, 3),
                findItemStack("gregtech", "gt.metaitem.01", 17516, 1),
                findItemStack("gregtech", "gt.metaitem.01", 27516, 2), appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .logicProcessor()
                    .maybeStack(4)
                    .orNull(),
                appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .calcProcessor()
                    .maybeStack(4)
                    .orNull() },
            new FluidStack[0],
            RegistryItems.fluidHousing(1),
            2,
            TierEU.RECIPE_ZPM,
            5 * SECONDS);
        tryAddAssembler(
            "estorage.housing_essentia_l6",
            new ItemStack[] { GTOreDictUnificator.get("circuitUltimate", 2),
                findItemStack("gregtech", "gt.metaitem.01", 17317, 3),
                findItemStack("gregtech", "gt.metaitem.01", 17516, 1),
                findItemStack("gregtech", "gt.metaitem.01", 27516, 2), appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .logicProcessor()
                    .maybeStack(4)
                    .orNull(),
                appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .calcProcessor()
                    .maybeStack(4)
                    .orNull() },
            new FluidStack[0],
            RegistryItems.essentiaHousing(1),
            3,
            TierEU.RECIPE_ZPM,
            5 * SECONDS);

        // ---------- L9 外壳（UV，5s，无流体，电路 1/2/3） ----------
        // 输入：超导电路×2 + metaitem.01/17129×3 + /17516 + /27516×2 + 逻辑处理器×8 + 计算处理器×8
        tryAddAssembler(
            "estorage.housing_item_l9",
            new ItemStack[] { GTOreDictUnificator.get("circuitSuperconductor", 2),
                findItemStack("gregtech", "gt.metaitem.01", 17129, 3),
                findItemStack("gregtech", "gt.metaitem.01", 17516, 1),
                findItemStack("gregtech", "gt.metaitem.01", 27516, 2), appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .logicProcessor()
                    .maybeStack(8)
                    .orNull(),
                appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .calcProcessor()
                    .maybeStack(8)
                    .orNull() },
            new FluidStack[0],
            RegistryItems.itemHousing(2),
            1,
            TierEU.RECIPE_UV,
            5 * SECONDS);
        tryAddAssembler(
            "estorage.housing_fluid_l9",
            new ItemStack[] { GTOreDictUnificator.get("circuitSuperconductor", 2),
                findItemStack("gregtech", "gt.metaitem.01", 17129, 3),
                findItemStack("gregtech", "gt.metaitem.01", 17516, 1),
                findItemStack("gregtech", "gt.metaitem.01", 27516, 2), appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .logicProcessor()
                    .maybeStack(8)
                    .orNull(),
                appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .calcProcessor()
                    .maybeStack(8)
                    .orNull() },
            new FluidStack[0],
            RegistryItems.fluidHousing(2),
            2,
            TierEU.RECIPE_UV,
            5 * SECONDS);
        tryAddAssembler(
            "estorage.housing_essentia_l9",
            new ItemStack[] { GTOreDictUnificator.get("circuitSuperconductor", 2),
                findItemStack("gregtech", "gt.metaitem.01", 17129, 3),
                findItemStack("gregtech", "gt.metaitem.01", 17516, 1),
                findItemStack("gregtech", "gt.metaitem.01", 27516, 2), appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .logicProcessor()
                    .maybeStack(8)
                    .orNull(),
                appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .calcProcessor()
                    .maybeStack(8)
                    .orNull() },
            new FluidStack[0],
            RegistryItems.essentiaHousing(2),
            3,
            TierEU.RECIPE_UV,
            5 * SECONDS);
    }

    // ------------------------------------------------------------------
    // t114p（用户）：存储组件全链 1024k → 16384m + 宇宙（物品/流体/源质变种）。
    // 输入物品均为用户从游戏（NEI）复制的 id，经 findItemStack 按注册名+damage 运行时解析。
    // - 1024k/4096k：EV 组装机链，焊锡 144mb；
    // - 16m..16384m：装配线（研究物品 = 同类型低一档组件，GTNH 惯例），流体 1080/432 数字 id；
    // - 宇宙（物品/流体）：太空组装模块 MK-III（gtnhintergalactic spaceAssembler，MODULE_TIER=3）。
    // 变种规则（用户）：物品用 1 个 dreamcraft 处理器 → 流体 2 个 → 源质 4 个
    // （处理器 III 系）；处理器 IV 系为 1:4:8。其余输入（AE2/ae2fc/TE4 组件、工程处理器、
    // 电路、GT 部件、机器方块、流体）与物品版相同。
    // ------------------------------------------------------------------
    private static void registerComponentChain() {
        FluidStack solder144 = Materials.SolderingAlloy.getMolten(144);
        // dreamcraft 处理器（物品 III / 物品 IV / 流体 II / 源质 I）。
        ItemStack procItemIII = findItemStack("dreamcraft", "EngineeringProcessorItemEmeraldCore", 0, 1);
        ItemStack procItemIV = findItemStack("dreamcraft", "EngineeringProcessorItemAdvEmeraldCore", 0, 1);
        // AE2 工程处理器（damage 24）——数量按物品版配方。
        java.util.function.IntFunction<ItemStack> eng = n -> appeng.api.AEApi.instance()
            .definitions()
            .materials()
            .engProcessor()
            .maybeStack(n)
            .orNull();

        // ---------- 1024k 组件（组装机 IV，10s，无电路，焊锡 144） ----------
        // 物品：ae2/58 + 处理器III + 工程处理器×16 + 精英电路×2 + 数据电路×4
        tryAddAssemblerNoCircuit(
            "estorage.component_item_1024k",
            new ItemStack[] { findItemStack("appliedenergistics2", "item.ItemMultiMaterial", 58, 1), procItemIII,
                eng.apply(16), GTOreDictUnificator.get("circuitElite", 2), GTOreDictUnificator.get("circuitData", 4) },
            new FluidStack[] { solder144 },
            RegistryItems.itemComponent(CellSize.K_1024),
            TierEU.RECIPE_IV,
            10 * SECONDS);
        // 流体：ae2fc fluid_part/5 + 流体处理器II×2，其余同物品版
        tryAddAssemblerNoCircuit(
            "estorage.component_fluid_1024k",
            new ItemStack[] { findItemStack("ae2fc", "fluid_part", 5, 1),
                findItemStack("dreamcraft", "EngineeringProcessorFluidEmeraldCore", 0, 2), eng.apply(16),
                GTOreDictUnificator.get("circuitElite", 2), GTOreDictUnificator.get("circuitData", 4) },
            new FluidStack[] { solder144 },
            RegistryItems.fluidComponent(CellSize.K_1024),
            TierEU.RECIPE_IV,
            10 * SECONDS);
        // 源质：TE4 storage.component/6 + 源质处理器I×4，其余同物品版
        tryAddAssemblerNoCircuit(
            "estorage.component_essentia_1024k",
            new ItemStack[] { findItemStack("thaumicenergistics", "storage.component", 6, 1),
                findItemStack("dreamcraft", "EngineeringProcessorEssentiaPulsatingCore", 0, 4), eng.apply(16),
                GTOreDictUnificator.get("circuitElite", 2), GTOreDictUnificator.get("circuitData", 4) },
            new FluidStack[] { solder144 },
            RegistryItems.essentiaComponent(CellSize.K_1024),
            TierEU.RECIPE_IV,
            10 * SECONDS);

        // ---------- 4096k 组件（组装机 LuV，10s，无电路，焊锡 144） ----------
        // 物品：ae2/59 + 处理器IV + 工程处理器×32 + 大师电路×2 + 精英电路×4
        tryAddAssemblerNoCircuit(
            "estorage.component_item_4096k",
            new ItemStack[] { findItemStack("appliedenergistics2", "item.ItemMultiMaterial", 59, 1), procItemIV,
                eng.apply(32), GTOreDictUnificator.get("circuitMaster", 2),
                GTOreDictUnificator.get("circuitElite", 4) },
            new FluidStack[] { solder144 },
            RegistryItems.itemComponent(CellSize.K_4096),
            TierEU.RECIPE_LuV,
            10 * SECONDS);
        // 流体：ae2fc fluid_part/6 + 流体处理器II×4
        tryAddAssemblerNoCircuit(
            "estorage.component_fluid_4096k",
            new ItemStack[] { findItemStack("ae2fc", "fluid_part", 6, 1),
                findItemStack("dreamcraft", "EngineeringProcessorFluidEmeraldCore", 0, 4), eng.apply(32),
                GTOreDictUnificator.get("circuitMaster", 2), GTOreDictUnificator.get("circuitElite", 4) },
            new FluidStack[] { solder144 },
            RegistryItems.fluidComponent(CellSize.K_4096),
            TierEU.RECIPE_LuV,
            10 * SECONDS);
        // 源质：TE4 storage.component/7 + 源质处理器I×8
        tryAddAssemblerNoCircuit(
            "estorage.component_essentia_4096k",
            new ItemStack[] { findItemStack("thaumicenergistics", "storage.component", 7, 1),
                findItemStack("dreamcraft", "EngineeringProcessorEssentiaPulsatingCore", 0, 8), eng.apply(32),
                GTOreDictUnificator.get("circuitMaster", 2), GTOreDictUnificator.get("circuitElite", 4) },
            new FluidStack[] { solder144 },
            RegistryItems.essentiaComponent(CellSize.K_4096),
            TierEU.RECIPE_LuV,
            10 * SECONDS);

        // ---------- 16m 组件（装配线 ZPM，60s，研究 = 4096k 组件） ----------
        ItemStack gt32675 = findItemStack("gregtech", "gt.metaitem.01", 32675, 1); // 力场发生器 LuV
        ItemStack bm1766 = findItemStack("gregtech", "gt.blockmachines", 1766, 4);
        tryAddAL(
            "estorage.component_item_16m",
            RegistryItems.itemComponent(CellSize.K_4096),
            new ItemStack[] { findItemStack("appliedenergistics2", "item.ItemMultiMaterial", 60, 1), procItemIV,
                eng.apply(64), GTOreDictUnificator.get("circuitUltimate", 4), gt32675, bm1766 },
            new FluidStack[] { gtFluid(1080, 576) },
            RegistryItems.itemComponent(CellSize.M_16),
            TierEU.RECIPE_ZPM,
            TierEU.RECIPE_ZPM,
            60 * SECONDS);
        tryAddAL(
            "estorage.component_fluid_16m",
            RegistryItems.fluidComponent(CellSize.K_4096),
            new ItemStack[] { findItemStack("ae2fc", "fluid_part", 7, 1),
                findItemStack("dreamcraft", "EngineeringProcessorFluidEmeraldCore", 0, 4), eng.apply(64),
                GTOreDictUnificator.get("circuitUltimate", 4), gt32675, bm1766 },
            new FluidStack[] { gtFluid(1080, 576) },
            RegistryItems.fluidComponent(CellSize.M_16),
            TierEU.RECIPE_ZPM,
            TierEU.RECIPE_ZPM,
            60 * SECONDS);
        tryAddAL(
            "estorage.component_essentia_16m",
            RegistryItems.essentiaComponent(CellSize.K_4096),
            new ItemStack[] { findItemStack("thaumicenergistics", "storage.component", 8, 1),
                findItemStack("dreamcraft", "EngineeringProcessorEssentiaPulsatingCore", 0, 8), eng.apply(64),
                GTOreDictUnificator.get("circuitUltimate", 4), gt32675, bm1766 },
            new FluidStack[] { gtFluid(1080, 576) },
            RegistryItems.essentiaComponent(CellSize.M_16),
            TierEU.RECIPE_ZPM,
            TierEU.RECIPE_ZPM,
            60 * SECONDS);

        // ---------- 64m 组件（装配线 ZPM，120s，研究 = 16m） ----------
        tryAddAL(
            "estorage.component_item_64m",
            RegistryItems.itemComponent(CellSize.M_16),
            new ItemStack[] { findItemStack("appliedenergistics2", "item.ItemMultiMaterial", 60, 4),
                findItemStack("dreamcraft", "EngineeringProcessorItemAdvEmeraldCore", 0, 4), eng.apply(64),
                GTOreDictUnificator.get("circuitUltimate", 6), findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.blockmachines", 1766, 8) },
            new FluidStack[] { gtFluid(1080, 8 * 144) },
            RegistryItems.itemComponent(CellSize.M_64),
            TierEU.RECIPE_ZPM,
            TierEU.RECIPE_ZPM,
            120 * SECONDS);
        tryAddAL(
            "estorage.component_fluid_64m",
            RegistryItems.fluidComponent(CellSize.M_16),
            new ItemStack[] { findItemStack("ae2fc", "fluid_part", 7, 4),
                findItemStack("dreamcraft", "EngineeringProcessorFluidEmeraldCore", 0, 16), eng.apply(64),
                GTOreDictUnificator.get("circuitUltimate", 6), findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.blockmachines", 1766, 8) },
            new FluidStack[] { gtFluid(1080, 8 * 144) },
            RegistryItems.fluidComponent(CellSize.M_64),
            TierEU.RECIPE_ZPM,
            TierEU.RECIPE_ZPM,
            120 * SECONDS);
        tryAddAL(
            "estorage.component_essentia_64m",
            RegistryItems.essentiaComponent(CellSize.M_16),
            new ItemStack[] { findItemStack("thaumicenergistics", "storage.component", 8, 4),
                findItemStack("dreamcraft", "EngineeringProcessorEssentiaPulsatingCore", 0, 32), eng.apply(64),
                GTOreDictUnificator.get("circuitUltimate", 6), findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.blockmachines", 1766, 8) },
            new FluidStack[] { gtFluid(1080, 8 * 144) },
            RegistryItems.essentiaComponent(CellSize.M_64),
            TierEU.RECIPE_ZPM,
            TierEU.RECIPE_ZPM,
            120 * SECONDS);

        // ---------- 256m 组件（装配线 UV，60s，研究 = 64m） ----------
        ItemStack oc103 = findItemStack("OpenComputers", "item", 103, 1);
        ItemStack gt32676 = findItemStack("gregtech", "gt.metaitem.01", 32676, 1); // 力场发生器 ZPM
        ItemStack bm1748 = findItemStack("gregtech", "gt.blockmachines", 1748, 4);
        tryAddAL(
            "estorage.component_item_256m",
            RegistryItems.itemComponent(CellSize.M_64),
            new ItemStack[] { findItemStack("appliedenergistics2", "item.ItemMultiMaterial", 60, 16), oc103,
                GTOreDictUnificator.get("circuitSuperconductor", 2), GTOreDictUnificator.get("circuitUltimate", 4),
                gt32676, bm1748 },
            new FluidStack[] { gtFluid(1080, 16 * 144) },
            RegistryItems.itemComponent(CellSize.M_256),
            TierEU.RECIPE_UV,
            TierEU.RECIPE_UV,
            60 * SECONDS);
        tryAddAL(
            "estorage.component_fluid_256m",
            RegistryItems.fluidComponent(CellSize.M_64),
            new ItemStack[] { findItemStack("ae2fc", "fluid_part", 7, 16), oc103,
                GTOreDictUnificator.get("circuitSuperconductor", 2), GTOreDictUnificator.get("circuitUltimate", 4),
                gt32676, bm1748 },
            new FluidStack[] { gtFluid(1080, 16 * 144) },
            RegistryItems.fluidComponent(CellSize.M_256),
            TierEU.RECIPE_UV,
            TierEU.RECIPE_UV,
            60 * SECONDS);
        tryAddAL(
            "estorage.component_essentia_256m",
            RegistryItems.essentiaComponent(CellSize.M_64),
            new ItemStack[] { findItemStack("thaumicenergistics", "storage.component", 8, 16), oc103,
                GTOreDictUnificator.get("circuitSuperconductor", 2), GTOreDictUnificator.get("circuitUltimate", 4),
                gt32676, bm1748 },
            new FluidStack[] { gtFluid(1080, 16 * 144) },
            RegistryItems.essentiaComponent(CellSize.M_256),
            TierEU.RECIPE_UV,
            TierEU.RECIPE_UV,
            60 * SECONDS);

        // ---------- 1024m 组件（装配线 UV，120s，研究 = 256m） ----------
        tryAddAL(
            "estorage.component_item_1024m",
            RegistryItems.itemComponent(CellSize.M_256),
            new ItemStack[] { findItemStack("appliedenergistics2", "item.ItemMultiMaterial", 60, 64),
                findItemStack("OpenComputers", "item", 103, 1), findItemStack("OpenComputers", "item", 103, 1),
                GTOreDictUnificator.get("circuitSuperconductor", 4),
                findItemStack("gregtech", "gt.metaitem.01", 32676, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32676, 1),
                findItemStack("gregtech", "gt.blockmachines", 1748, 8) },
            new FluidStack[] { gtFluid(1080, 18 * 144) },
            RegistryItems.itemComponent(CellSize.M_1024),
            TierEU.RECIPE_UV,
            TierEU.RECIPE_UV,
            120 * SECONDS);
        tryAddAL(
            "estorage.component_fluid_1024m",
            RegistryItems.fluidComponent(CellSize.M_256),
            new ItemStack[] { findItemStack("ae2fc", "fluid_part", 7, 64),
                findItemStack("OpenComputers", "item", 103, 1), findItemStack("OpenComputers", "item", 103, 1),
                GTOreDictUnificator.get("circuitSuperconductor", 4),
                findItemStack("gregtech", "gt.metaitem.01", 32676, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32676, 1),
                findItemStack("gregtech", "gt.blockmachines", 1748, 8) },
            new FluidStack[] { gtFluid(1080, 18 * 144) },
            RegistryItems.fluidComponent(CellSize.M_1024),
            TierEU.RECIPE_UV,
            TierEU.RECIPE_UV,
            120 * SECONDS);
        tryAddAL(
            "estorage.component_essentia_1024m",
            RegistryItems.essentiaComponent(CellSize.M_256),
            new ItemStack[] { findItemStack("thaumicenergistics", "storage.component", 8, 64),
                findItemStack("OpenComputers", "item", 103, 1), findItemStack("OpenComputers", "item", 103, 1),
                GTOreDictUnificator.get("circuitSuperconductor", 4),
                findItemStack("gregtech", "gt.metaitem.01", 32676, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32676, 1),
                findItemStack("gregtech", "gt.blockmachines", 1748, 8) },
            new FluidStack[] { gtFluid(1080, 18 * 144) },
            RegistryItems.essentiaComponent(CellSize.M_1024),
            TierEU.RECIPE_UV,
            TierEU.RECIPE_UV,
            120 * SECONDS);

        // ---------- 4096m 组件（装配线 UHV，60s，研究 = 1024m；流体 432 + 1080 双输入） ----------
        ItemStack gt32677 = findItemStack("gregtech", "gt.metaitem.01", 32677, 1); // 力场发生器 UV
        ItemStack bm1808 = findItemStack("gregtech", "gt.blockmachines", 1808, 4);
        tryAddAL(
            "estorage.component_item_4096m",
            RegistryItems.itemComponent(CellSize.M_1024),
            new ItemStack[] { findItemStack("appliedenergistics2", "item.ItemMultiMaterial", 60, 64),
                findItemStack("OpenComputers", "item", 103, 4), findItemStack("OpenComputers", "item", 103, 4),
                GTOreDictUnificator.get("circuitInfinite", 4), gt32677, bm1808 },
            new FluidStack[] { gtFluid(432, 6 * 144), gtFluid(1080, 12 * 144) },
            RegistryItems.itemComponent(CellSize.M_4096),
            TierEU.RECIPE_UHV,
            TierEU.RECIPE_UHV,
            60 * SECONDS);
        tryAddAL(
            "estorage.component_fluid_4096m",
            RegistryItems.fluidComponent(CellSize.M_1024),
            new ItemStack[] { findItemStack("ae2fc", "fluid_part", 7, 64),
                findItemStack("OpenComputers", "item", 103, 4), findItemStack("OpenComputers", "item", 103, 4),
                GTOreDictUnificator.get("circuitInfinite", 4), gt32677, bm1808 },
            new FluidStack[] { gtFluid(432, 6 * 144), gtFluid(1080, 12 * 144) },
            RegistryItems.fluidComponent(CellSize.M_4096),
            TierEU.RECIPE_UHV,
            TierEU.RECIPE_UHV,
            60 * SECONDS);
        tryAddAL(
            "estorage.component_essentia_4096m",
            RegistryItems.essentiaComponent(CellSize.M_1024),
            new ItemStack[] { findItemStack("thaumicenergistics", "storage.component", 8, 64),
                findItemStack("OpenComputers", "item", 103, 4), findItemStack("OpenComputers", "item", 103, 4),
                GTOreDictUnificator.get("circuitInfinite", 4), gt32677, bm1808 },
            new FluidStack[] { gtFluid(432, 6 * 144), gtFluid(1080, 12 * 144) },
            RegistryItems.essentiaComponent(CellSize.M_4096),
            TierEU.RECIPE_UHV,
            TierEU.RECIPE_UHV,
            60 * SECONDS);

        // ---------- 16384m 组件（装配线 UHV，120s，研究 = 4096m；流体 432 + 1080 双输入） ----------
        tryAddAL(
            "estorage.component_item_16384m",
            RegistryItems.itemComponent(CellSize.M_4096),
            new ItemStack[] { findItemStack("appliedenergistics2", "item.ItemMultiMaterial", 60, 64),
                findItemStack("OpenComputers", "item", 103, 12), findItemStack("miscutils", "MU-metaitem.01", 32105, 1),
                GTOreDictUnificator.get("circuitInfinite", 8), findItemStack("gregtech", "gt.metaitem.01", 32677, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32677, 1),
                findItemStack("gregtech", "gt.blockmachines", 1808, 8) },
            new FluidStack[] { gtFluid(432, 12 * 144), gtFluid(1080, 16 * 144) },
            RegistryItems.itemComponent(CellSize.M_16384),
            TierEU.RECIPE_UHV,
            TierEU.RECIPE_UHV,
            120 * SECONDS);
        tryAddAL(
            "estorage.component_fluid_16384m",
            RegistryItems.fluidComponent(CellSize.M_4096),
            new ItemStack[] { findItemStack("ae2fc", "fluid_part", 7, 64),
                findItemStack("OpenComputers", "item", 103, 12), findItemStack("miscutils", "MU-metaitem.01", 32105, 1),
                GTOreDictUnificator.get("circuitInfinite", 8), findItemStack("gregtech", "gt.metaitem.01", 32677, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32677, 1),
                findItemStack("gregtech", "gt.blockmachines", 1808, 8) },
            new FluidStack[] { gtFluid(432, 12 * 144), gtFluid(1080, 16 * 144) },
            RegistryItems.fluidComponent(CellSize.M_16384),
            TierEU.RECIPE_UHV,
            TierEU.RECIPE_UHV,
            120 * SECONDS);
        tryAddAL(
            "estorage.component_essentia_16384m",
            RegistryItems.essentiaComponent(CellSize.M_4096),
            new ItemStack[] { findItemStack("thaumicenergistics", "storage.component", 8, 64),
                findItemStack("OpenComputers", "item", 103, 12), findItemStack("miscutils", "MU-metaitem.01", 32105, 1),
                GTOreDictUnificator.get("circuitInfinite", 8), findItemStack("gregtech", "gt.metaitem.01", 32677, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32677, 1),
                findItemStack("gregtech", "gt.blockmachines", 1808, 8) },
            new FluidStack[] { gtFluid(432, 12 * 144), gtFluid(1080, 16 * 144) },
            RegistryItems.essentiaComponent(CellSize.M_16384),
            TierEU.RECIPE_UHV,
            TierEU.RECIPE_UHV,
            120 * SECONDS);

        // ---------- 宇宙组件（太空组装模块 MK-III，UXV，120s） ----------
        // 物品：AE2 人工宇宙盘（IItems.cellUniverse API）+ metaitem.03/6581×64 +
        // metaitem.01/32047×6 + tectech 时空压缩场发生器/8×12 + 稳定场发生器/8×12 +
        // metaitem.03/4143×2 + /4141×2；流体 818×36864。
        tryAddSpaceAssembler(
            "estorage.component_item_universe",
            new ItemStack[] { appeng.api.AEApi.instance()
                .definitions()
                .items()
                .cellUniverse()
                .maybeStack(1)
                .orNull(), findItemStack("gregtech", "gt.metaitem.03", 6581, 64),
                findItemStack("gregtech", "gt.metaitem.01", 32047, 6),
                findItemStack("tectech", "gt.spacetime_compression_field_generator", 8, 12),
                findItemStack("tectech", "gt.stabilisation_field_generator", 8, 12),
                findItemStack("gregtech", "gt.metaitem.03", 4143, 2),
                findItemStack("gregtech", "gt.metaitem.03", 4141, 2) },
            new FluidStack[] { gtFluid(818, 36864) },
            RegistryItems.itemCell(CellSize.UNIVERSE),
            TierEU.RECIPE_UXV,
            120 * SECONDS,
            3); // MK-III
        // 流体：ae2fc 宇宙流体盘（ItemAndBlockHolder.ARTIFICIAL_UNIVERSE_CELL）+ bartworks
        // 超密板/10112×64 + metaitem.01/32047×6 + GoodGenerator yotta 流体罐/9×6 +
        // kekztech TFFT/10×6 + tectech ×12×2 + metaitem.03/4143×2 + /4141×2；流体 818×36864。
        tryAddSpaceAssembler(
            "estorage.component_fluid_universe",
            new ItemStack[] { new ItemStack(com.glodblock.github.loader.ItemAndBlockHolder.ARTIFICIAL_UNIVERSE_CELL, 1),
                findItemStack("bartworks", "gt.bwMetaGeneratedplateSuperdense", 10112, 64),
                findItemStack("gregtech", "gt.metaitem.01", 32047, 6),
                findItemStack("GoodGenerator", "yottaFluidTankCells", 9, 6),
                findItemStack("kekztech", "kekztech_tfftstoragefield_block", 10, 6),
                findItemStack("tectech", "gt.spacetime_compression_field_generator", 8, 12),
                findItemStack("tectech", "gt.stabilisation_field_generator", 8, 12),
                findItemStack("gregtech", "gt.metaitem.03", 4143, 2),
                findItemStack("gregtech", "gt.metaitem.03", 4141, 2) },
            new FluidStack[] { gtFluid(818, 36864) },
            RegistryItems.fluidCell(CellSize.UNIVERSE),
            TierEU.RECIPE_UXV,
            120 * SECONDS,
            3); // MK-III

        // ---------- 奇点闪存晶阵（太空组装模块 MK-II，UIV，120s） ----------
        // AE2 奇点合成存储器 + OC 103×64 + metaitem.03/4581×2 + 奇异电路×2 +
        // metaitem.01/32045×4 + gt.blockmachines/2606×64；流体 1126×2304 + 3×24000。
        tryAddSpaceAssembler(
            "ecal.cell_singularity",
            new ItemStack[] { appeng.api.AEApi.instance()
                .definitions()
                .blocks()
                .craftingStorageSingularity()
                .maybeStack(1)
                .orNull(), findItemStack("OpenComputers", "item", 103, 64),
                findItemStack("gregtech", "gt.metaitem.03", 4581, 2), GTOreDictUnificator.get("circuitExotic", 2),
                findItemStack("gregtech", "gt.metaitem.01", 32045, 4),
                findItemStack("gregtech", "gt.blockmachines", 2606, 64) },
            new FluidStack[] { gtFluid(1126, 16 * 144), gtFluid(3, 24000) },
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.SINGULARITY),
                1),
            TierEU.RECIPE_UIV,
            120 * SECONDS,
            2); // MK-II
    }

    // ------------------------------------------------------------------
    // E-Calculator 部件配方（t114s 用户重做，t114n 删除后重新设计）：
    // 外壳/并行驱动器/线程驱动器/晶阵驱动器/发射总线/ME 通道 6 条组装机，
    // 全部 EV 1920 EU/t、10 秒、无电路、焊锡 576mb；控制器为工作台 3×3（registerCraftingRecipes）。
    // 输入物品均为用户从游戏复制的 id（findItemStack 数字方案）或 AE2 definitions API。
    // ------------------------------------------------------------------
    private static void registerEcal() {
        FluidStack solder576 = Materials.SolderingAlloy.getMolten(576);
        // AE2 方块：合成单元（BlockCraftingUnit/0）、合成加速器（/1）、ME 接口、IO 端口。
        ItemStack aeUnit = appeng.api.AEApi.instance()
            .definitions()
            .blocks()
            .craftingUnit()
            .maybeStack(1)
            .orNull();
        ItemStack aeAccel = appeng.api.AEApi.instance()
            .definitions()
            .blocks()
            .craftingAccelerator()
            .maybeStack(1)
            .orNull();
        ItemStack aeIface = appeng.api.AEApi.instance()
            .definitions()
            .blocks()
            .iface()
            .maybeStack(1)
            .orNull();
        ItemStack aeIOPort = appeng.api.AEApi.instance()
            .definitions()
            .blocks()
            .iOPort()
            .maybeStack(1)
            .orNull();

        // 外壳 ×1：gt.blockframes/28 + metaitem.01/17028×6 + 精英电路×4 + 数据电路×8（分两格）
        // + 传感器EV + 发射器EV。（t114aa 用户：第二个 ×8 电路板从精英电路改为数据电路。）
        tryAddAssemblerNoCircuit(
            "ecal.casing",
            new Object[] { findItemStack("gregtech", "gt.blockframes", 28, 1),
                findItemStack("gregtech", "gt.metaitem.01", 17028, 6), new OreDictItemStack("circuitElite", 4),
                new OreDictItemStack("circuitData", 8), findItemStack("gregtech", "gt.metaitem.01", 32693, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32683, 1) },
            new FluidStack[] { solder576 },
            new ItemStack(RegistryEcal.casing, 1),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // 并行驱动器 ×1：外壳 + AE2 合成加速器（/1）+ 精英电路×2 + gt.blockmachines/2360×4。
        tryAddAssemblerNoCircuit(
            "ecal.parallel_drive",
            new ItemStack[] { new ItemStack(RegistryEcal.casing, 1), aeAccel,
                GTOreDictUnificator.get("circuitElite", 2), findItemStack("gregtech", "gt.blockmachines", 2360, 4) },
            new FluidStack[] { solder576 },
            new ItemStack(RegistryEcal.parallelDrive, 1),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // 线程驱动器 ×1：外壳 + AE2 ME 接口 + 工程处理器×8 + 传感器EV + 发射器EV
        // + gt.blockmachines/2360×8。
        tryAddAssemblerNoCircuit(
            "ecal.thread_drive",
            new ItemStack[] { new ItemStack(RegistryEcal.casing, 1), aeIface, appeng.api.AEApi.instance()
                .definitions()
                .materials()
                .engProcessor()
                .maybeStack(8)
                .orNull(), findItemStack("gregtech", "gt.metaitem.01", 32693, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32683, 1),
                findItemStack("gregtech", "gt.blockmachines", 2360, 8) },
            new FluidStack[] { solder576 },
            new ItemStack(RegistryEcal.threadDrive, 1),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // 晶阵驱动器 ×1：外壳 + AE2 合成单元（/0）+ 合成加速器（/1）+ 工程处理器×8
        // + 力场发生器EV。
        tryAddAssemblerNoCircuit(
            "ecal.cell_drive",
            new ItemStack[] { new ItemStack(RegistryEcal.casing, 1), aeUnit, aeAccel, appeng.api.AEApi.instance()
                .definitions()
                .materials()
                .engProcessor()
                .maybeStack(8)
                .orNull(), findItemStack("gregtech", "gt.metaitem.01", 32673, 1) },
            new FluidStack[] { solder576 },
            new ItemStack(RegistryEcal.cellDrive, 1),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // 发射总线 ×1：外壳 + 发射器EV×2 + 传感器EV×2 + 力场发生器EV + gt.blockmachines/5153×2
        // + /2365×4。
        tryAddAssemblerNoCircuit(
            "ecal.transmitter_bus",
            new ItemStack[] { new ItemStack(RegistryEcal.casing, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32683, 2),
                findItemStack("gregtech", "gt.metaitem.01", 32693, 2),
                findItemStack("gregtech", "gt.metaitem.01", 32673, 1),
                findItemStack("gregtech", "gt.blockmachines", 5153, 2),
                findItemStack("gregtech", "gt.blockmachines", 2365, 4) },
            new FluidStack[] { solder576 },
            new ItemStack(RegistryEcal.transmitterBus, 1),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // ME 通道 ×1：外壳 + AE2 IO 端口 + 传感器EV + 发射器EV + 大师电路 + gt.blockmachines/2360×8。
        tryAddAssemblerNoCircuit(
            "ecal.me_channel",
            new ItemStack[] { new ItemStack(RegistryEcal.casing, 1), aeIOPort,
                findItemStack("gregtech", "gt.metaitem.01", 32693, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32683, 1), GTOreDictUnificator.get("circuitMaster", 1),
                findItemStack("gregtech", "gt.blockmachines", 2360, 8) },
            new FluidStack[] { solder576 },
            new ItemStack(RegistryEcal.meChannel, 1),
            TierEU.RECIPE_EV,
            10 * SECONDS);
    }

    // ------------------------------------------------------------------
    // 并行核心 9 档（t114z 用户基准配方 + 递推，t114ab 修正：两个电路板输入都逐档升级）：
    // 基准 = AE2 合成加速器（BlockCraftingUnit/1）+ 精英电路×2 + 数据电路×4 + 传感器MV(32691)
    // + 发射器MV(32681) → 核心1（HV）；每档"电路板 +1 级（×2 链 Elite..Cosmic、×4 链
    // Data..Exotic 都升）、电压 +1 级、传感器/发射器 +1 级（始终比电压低 1 级）、输出 +1 级"
    // （1→4→16→…→65536）。×4 电路板链从 Data 起 +1：Data→Elite→Master→Ultimate→
    // Superconductor→Infinite→Bio→Optical→Exotic。无流体、10 秒、无编程电路。
    // 部件 damage（ID+32000 实证）：发射器 MV..UEV = 32681..32689，传感器 32691..32699。
    // ------------------------------------------------------------------
    private static void registerEcalParallelCores() {
        ItemStack aeAccel = appeng.api.AEApi.instance()
            .definitions()
            .blocks()
            .craftingAccelerator()
            .maybeStack(1)
            .orNull();
        int[] cores = { 1, 4, 16, 64, 256, 1024, 4096, 16384, 65536 };
        String[] circuits = { "circuitElite", "circuitMaster", "circuitUltimate", "circuitSuperconductor",
            "circuitInfinite", "circuitBio", "circuitOptical", "circuitExotic", "circuitCosmic" };
        String[] circuits4 = { "circuitData", "circuitElite", "circuitMaster", "circuitUltimate",
            "circuitSuperconductor", "circuitInfinite", "circuitBio", "circuitOptical", "circuitExotic" };
        long[] euts = { TierEU.RECIPE_HV, TierEU.RECIPE_EV, TierEU.RECIPE_IV, TierEU.RECIPE_LuV, TierEU.RECIPE_ZPM,
            TierEU.RECIPE_UV, TierEU.RECIPE_UHV, TierEU.RECIPE_UEV, TierEU.RECIPE_UIV };
        // t114z（用户）：传感器/发射器从 MV 起每档 +1 级（1:MV、4:HV、16:EV、64:IV、256:LuV、
        // 1024:ZPM、4096:UV、16384:UHV、65536:UEV），始终比电压低 1 级。
        int[] sensors = { 32691, 32692, 32693, 32694, 32695, 32696, 32697, 32698, 32699 };
        int[] emitters = { 32681, 32682, 32683, 32684, 32685, 32686, 32687, 32688, 32689 };
        for (int i = 0; i < cores.length; i++) {
            tryAddAssemblerNoCircuit(
                "ecal.parallel_core_" + cores[i],
                new Object[] { aeAccel, new OreDictItemStack(circuits[i], 2), new OreDictItemStack(circuits4[i], 4),
                    findItemStack("gregtech", "gt.metaitem.01", sensors[i], 1),
                    findItemStack("gregtech", "gt.metaitem.01", emitters[i], 1) },
                new FluidStack[0],
                new ItemStack(ecoaegtnh.registry.RegistryEcal.PARALLEL_CORES.get(cores[i]), 1),
                euts[i],
                10 * SECONDS);
        }
    }

    // ------------------------------------------------------------------
    // 线程核心 6 档（t114t 用户确认方案，t114u/t114v 递推规则：每档电压 +2 级、电路板 +2 级、
    // 传感器/发射器 +2 级且与电压同级匹配）：普通线程核心 1/4/16 基准 = AE2 工程处理器×8 +
    // 数据电路×4 → 核心1（HV/精英/部件HV）；1:HV/精英/部件HV、4:IV/终极/部件IV（+2）、
    // 16:ZPM/无限/部件ZPM（+2，跳过 EV/LuV）。无流体、10 秒、无编程电路。
    // 超线程核心 hyper_2/4/8（0+4/4+8/8+16）按闪存晶阵风格——基准多一个合成加速器
    // （并行驱动器同款件）、工程处理器×16（双倍）、焊锡 576mb；EV/大师/部件EV →
    // LuV/超导/部件LuV（+2）→ UV/生物/部件UV（+2）。
    // 部件 damage（ID+32000 实证）：发射器 HV/EV/IV/LuV/ZPM/UV = 32682/32683/32684/32685/
    // 32686/32687，传感器同序 = 32692/32693/32694/32695/32696/32697。
    // ------------------------------------------------------------------
    private static void registerEcalThreadCores() {
        FluidStack solder576 = Materials.SolderingAlloy.getMolten(576);
        ItemStack aeAccel = appeng.api.AEApi.instance()
            .definitions()
            .blocks()
            .craftingAccelerator()
            .maybeStack(1)
            .orNull();
        ItemStack engProc8 = appeng.api.AEApi.instance()
            .definitions()
            .materials()
            .engProcessor()
            .maybeStack(8)
            .orNull();
        ItemStack engProc16 = appeng.api.AEApi.instance()
            .definitions()
            .materials()
            .engProcessor()
            .maybeStack(16)
            .orNull();

        // 普通线程核心 1/4/16（HV/IV/ZPM，精英/终极/无限电路×2 + 数据电路×4 +
        // 传感器 HV/IV/ZPM（32692/32694/32696）+ 发射器 HV/IV/ZPM（32682/32684/32686））。
        int[] threads = { 1, 4, 16 };
        String[] circuits = { "circuitElite", "circuitUltimate", "circuitInfinite" };
        long[] euts = { TierEU.RECIPE_HV, TierEU.RECIPE_IV, TierEU.RECIPE_ZPM };
        int[] sensors = { 32692, 32694, 32696 };
        int[] emitters = { 32682, 32684, 32686 };
        for (int i = 0; i < threads.length; i++) {
            tryAddAssemblerNoCircuit(
                "ecal.thread_core_" + threads[i],
                new Object[] { engProc8, new OreDictItemStack(circuits[i], 2), new OreDictItemStack("circuitData", 4),
                    findItemStack("gregtech", "gt.metaitem.01", sensors[i], 1),
                    findItemStack("gregtech", "gt.metaitem.01", emitters[i], 1) },
                new FluidStack[0],
                new ItemStack(
                    ecoaegtnh.registry.RegistryEcal.THREAD_CORES_BY_SUFFIX.get(String.valueOf(threads[i])),
                    1),
                euts[i],
                10 * SECONDS);
        }

        // 超线程核心 hyper_2/4/8（EV/LuV/UV，大师/超导/生物电路×2 + 数据电路×4 +
        // 传感器 EV/LuV/UV（32693/32695/32697）+ 发射器 EV/LuV/UV（32683/32685/32687）
        // + 合成加速器 + 工程处理器×16 + 焊锡 576mb）。
        String[] hyperSuffixes = { "hyper_2", "hyper_4", "hyper_8" };
        String[] hyperCircuits = { "circuitMaster", "circuitSuperconductor", "circuitBio" };
        long[] hyperEuts = { TierEU.RECIPE_EV, TierEU.RECIPE_LuV, TierEU.RECIPE_UV };
        int[] hyperSensors = { 32693, 32695, 32697 };
        int[] hyperEmitters = { 32683, 32685, 32687 };
        for (int i = 0; i < hyperSuffixes.length; i++) {
            tryAddAssemblerNoCircuit(
                "ecal.thread_core_" + hyperSuffixes[i],
                new Object[] { aeAccel, engProc16, new OreDictItemStack(hyperCircuits[i], 2),
                    new OreDictItemStack("circuitData", 4),
                    findItemStack("gregtech", "gt.metaitem.01", hyperSensors[i], 1),
                    findItemStack("gregtech", "gt.metaitem.01", hyperEmitters[i], 1) },
                new FluidStack[] { solder576 },
                new ItemStack(ecoaegtnh.registry.RegistryEcal.THREAD_CORES_BY_SUFFIX.get(hyperSuffixes[i]), 1),
                hyperEuts[i],
                10 * SECONDS);
        }
    }

    // ------------------------------------------------------------------
    // 闪存晶阵 256k/1024k/4096k（t114s 用户模板递推）：基准 = AE2 合成单元 +
    // ECO 物品 256k 存储组件 + 精英电路×1 + 精英电路×2（分两格）+ 计算处理器×8 →
    // ecalculator_cell_256k（EV）；每档"ECO 组件 +1 级、电路板 +1 级、电压 +1 级"。
    // 焊锡 576mb、10 秒、无编程电路。（16M 及以上配方用户后续提供。）
    // ------------------------------------------------------------------
    private static void registerEcalFlashCells() {
        FluidStack solder576 = Materials.SolderingAlloy.getMolten(576);
        ItemStack aeUnit = appeng.api.AEApi.instance()
            .definitions()
            .blocks()
            .craftingUnit()
            .maybeStack(1)
            .orNull();
        ItemStack calcProc = appeng.api.AEApi.instance()
            .definitions()
            .materials()
            .calcProcessor()
            .maybeStack(8)
            .orNull();
        ecoaegtnh.item.ecalculator.CellSize[] sizes = { ecoaegtnh.item.ecalculator.CellSize.K_256,
            ecoaegtnh.item.ecalculator.CellSize.K_1024, ecoaegtnh.item.ecalculator.CellSize.K_4096 };
        ecoaegtnh.item.estorage.CellSize[] ecoSizes = { ecoaegtnh.item.estorage.CellSize.K_256,
            ecoaegtnh.item.estorage.CellSize.K_1024, ecoaegtnh.item.estorage.CellSize.K_4096 };
        String[] circuits = { "circuitElite", "circuitMaster", "circuitUltimate" };
        long[] euts = { TierEU.RECIPE_EV, TierEU.RECIPE_IV, TierEU.RECIPE_LuV };
        for (int i = 0; i < sizes.length; i++) {
            tryAddAssemblerNoCircuit(
                "ecal.cell_" + sizes[i].label,
                new ItemStack[] { aeUnit, RegistryItems.itemComponent(ecoSizes[i]),
                    GTOreDictUnificator.get(circuits[i], 1), GTOreDictUnificator.get(circuits[i], 2), calcProc },
                new FluidStack[] { solder576 },
                new ItemStack(ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(sizes[i]), 1),
                euts[i],
                10 * SECONDS);
        }

        // 16m..16384m（装配线，研究 = 同类型低一档晶阵，GTNH 惯例）——用户逐条提供。
        ItemStack calcProc64 = appeng.api.AEApi.instance()
            .definitions()
            .materials()
            .calcProcessor()
            .maybeStack(64)
            .orNull();
        ItemStack oc103 = findItemStack("OpenComputers", "item", 103, 1);

        // 16m（ZPM，60s，1080×576）：合成单元 + 16m 组件 + 计算处理器×64 + 终极电路×4
        // + 力场发生器LuV + gt.blockmachines/1766×4。
        tryAddAL(
            "ecal.cell_16m",
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.K_4096),
                1),
            new ItemStack[] { aeUnit, RegistryItems.itemComponent(ecoaegtnh.item.estorage.CellSize.M_16), calcProc64,
                GTOreDictUnificator.get("circuitUltimate", 4), findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.blockmachines", 1766, 4) },
            new FluidStack[] { gtFluid(1080, 4 * 144) },
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_16),
                1),
            TierEU.RECIPE_ZPM,
            TierEU.RECIPE_ZPM,
            60 * SECONDS);

        // 64m（ZPM，120s，1080×1152）：计算处理器×64 + 终极电路×6 + 力场发生器LuV×2格
        // + gt.blockmachines/1766×8。
        tryAddAL(
            "ecal.cell_64m",
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_16),
                1),
            new ItemStack[] { aeUnit, RegistryItems.itemComponent(ecoaegtnh.item.estorage.CellSize.M_64), calcProc64,
                GTOreDictUnificator.get("circuitUltimate", 6), findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.blockmachines", 1766, 8) },
            new FluidStack[] { gtFluid(1080, 8 * 144) },
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_64),
                1),
            TierEU.RECIPE_ZPM,
            TierEU.RECIPE_ZPM,
            120 * SECONDS);

        // 256m（UV，60s，1080×2304）：OC 103×1 + 超导电路×2 + 终极电路×4 + 力场发生器ZPM
        // + gt.blockmachines/1748×4。（无计算处理器）
        tryAddAL(
            "ecal.cell_256m",
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_64),
                1),
            new ItemStack[] { aeUnit, RegistryItems.itemComponent(ecoaegtnh.item.estorage.CellSize.M_256), oc103,
                GTOreDictUnificator.get("circuitSuperconductor", 2), GTOreDictUnificator.get("circuitUltimate", 4),
                findItemStack("gregtech", "gt.metaitem.01", 32676, 1),
                findItemStack("gregtech", "gt.blockmachines", 1748, 4) },
            new FluidStack[] { gtFluid(1080, 16 * 144) },
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_256),
                1),
            TierEU.RECIPE_UV,
            TierEU.RECIPE_UV,
            60 * SECONDS);

        // 1024m（UV，120s，1080×2592）：OC 103×2格 + 超导电路×4 + 力场发生器ZPM×2格
        // + gt.blockmachines/1748×4。
        tryAddAL(
            "ecal.cell_1024m",
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_256),
                1),
            new ItemStack[] { aeUnit, RegistryItems.itemComponent(ecoaegtnh.item.estorage.CellSize.M_1024),
                findItemStack("OpenComputers", "item", 103, 1), findItemStack("OpenComputers", "item", 103, 1),
                GTOreDictUnificator.get("circuitSuperconductor", 4),
                findItemStack("gregtech", "gt.metaitem.01", 32676, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32676, 1),
                findItemStack("gregtech", "gt.blockmachines", 1748, 4) },
            new FluidStack[] { gtFluid(1080, 18 * 144) },
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_1024),
                1),
            TierEU.RECIPE_UV,
            TierEU.RECIPE_UV,
            120 * SECONDS);

        // 4096m（UHV，60s，432×864 + 1080×1728）：OC 103×4+4格 + 力场发生器UV
        // + gt.blockmachines/1808×4。（用户配方无电路输入）
        tryAddAL(
            "ecal.cell_4096m",
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_1024),
                1),
            new ItemStack[] { aeUnit, RegistryItems.itemComponent(ecoaegtnh.item.estorage.CellSize.M_4096),
                findItemStack("OpenComputers", "item", 103, 4), findItemStack("OpenComputers", "item", 103, 4),
                findItemStack("gregtech", "gt.metaitem.01", 32677, 1),
                findItemStack("gregtech", "gt.blockmachines", 1808, 4) },
            new FluidStack[] { gtFluid(432, 6 * 144), gtFluid(1080, 12 * 144) },
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_4096),
                1),
            TierEU.RECIPE_UHV,
            TierEU.RECIPE_UHV,
            60 * SECONDS);

        // 16384m（UHV，120s，432×1728 + 1080×2304）：OC 103×12 + miscutils 32105
        // + 无限电路×8 + 力场发生器UV×2格 + gt.blockmachines/1808×8。
        tryAddAL(
            "ecal.cell_16384m",
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_4096),
                1),
            new ItemStack[] { aeUnit, RegistryItems.itemComponent(ecoaegtnh.item.estorage.CellSize.M_16384),
                findItemStack("OpenComputers", "item", 103, 12), findItemStack("miscutils", "MU-metaitem.01", 32105, 1),
                GTOreDictUnificator.get("circuitInfinite", 8), findItemStack("gregtech", "gt.metaitem.01", 32677, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32677, 1),
                findItemStack("gregtech", "gt.blockmachines", 1808, 8) },
            new FluidStack[] { gtFluid(432, 12 * 144), gtFluid(1080, 16 * 144) },
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_16384),
                1),
            TierEU.RECIPE_UHV,
            TierEU.RECIPE_UHV,
            120 * SECONDS);
    }

    // ------------------------------------------------------------------
    // 部件 + 控制器（t114m 用户逐条重做：全部 EV 1920 EU/t、10 秒、无编程电路、
    // 焊锡 576mb；控制器改为工作台 3×3 合成，见 registerCraftingRecipes）。
    // t114n（用户）：旧的 EV 组装机控制器配方（estorage.controller_l4）已删除。
    // ------------------------------------------------------------------
    private static void registerPartsAndControllers() {

        // R1：存储阵列外壳 ×1（EV 组装机，t114m 用户配方）：钛框架/钛板 + 精英电路 +
        // AE2 三色处理器（逻辑/计算/工程各 8），焊锡 576mb，无电路，10 秒 @ EV。
        // 替换旧的 t98b 配方（输出 ×2、30 秒、电路 1）。
        tryAddAssemblerNoCircuit(
            "estorage.casing",
            new ItemStack[] { GTOreDictUnificator.get(OrePrefixes.frameGt, Materials.Titanium, 1),
                Materials.Titanium.getPlates(6), GTOreDictUnificator.get("circuitElite", 2), appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .logicProcessor()
                    .maybeStack(8)
                    .orNull(),
                appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .calcProcessor()
                    .maybeStack(8)
                    .orNull(),
                appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .engProcessor()
                    .maybeStack(8)
                    .orNull() },
            new FluidStack[] { Materials.SolderingAlloy.getMolten(576) },
            new ItemStack(BlockEcoStorageCasing.INSTANCE, 1),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // R2：驱动器 ×1（EV 组装机，t114m 用户配方）：外壳 + AE2 ME 驱动器 + 力场发生器 EV
        // + 大师电路 + 精英电路 ×2 + 传感器 EV + 发射器 EV + 工程处理器 ×4。
        tryAddAssemblerNoCircuit(
            "estorage.drive",
            new ItemStack[] { new ItemStack(BlockEcoStorageCasing.INSTANCE, 1), appeng.api.AEApi.instance()
                .definitions()
                .blocks()
                .drive()
                .maybeStack(1)
                .orNull(), ItemList.Field_Generator_EV.get(1), GTOreDictUnificator.get("circuitMaster", 1),
                GTOreDictUnificator.get("circuitElite", 2), ItemList.Sensor_EV.get(1), ItemList.Emitter_EV.get(1),
                appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .engProcessor()
                    .maybeStack(4)
                    .orNull() },
            new FluidStack[] { Materials.SolderingAlloy.getMolten(576) },
            new ItemStack(BlockEcoStorageDrive.INSTANCE, 1),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // R3：电容 A ×1（EV 组装机，t114m 用户配方）：外壳 + 数据电路 ×4 + 数据电池（矿词
        // batteryData）+ GT 机器方块（gt.blockmachines/2360）×16。
        tryAddAssemblerNoCircuit(
            "estorage.capacitance_a",
            new ItemStack[] { new ItemStack(BlockEcoStorageCasing.INSTANCE, 1),
                GTOreDictUnificator.get("circuitData", 4), GTOreDictUnificator.get("batteryData", 1),
                gtMachineBlockStack(2360, 16) },
            new FluidStack[] { Materials.SolderingAlloy.getMolten(576) },
            new ItemStack(BlockEcoStorageCapacitance.INSTANCE, 1, BlockEcoStorageCapacitance.META_A),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // R4：ME 总线 ×1（EV 组装机，t114m 用户配方）：外壳 + AE2 IO 端口 + 传感器 EV
        // + 发射器 EV + 大师电路。
        tryAddAssemblerNoCircuit(
            "estorage.me_bus",
            new ItemStack[] { new ItemStack(BlockEcoStorageCasing.INSTANCE, 1), appeng.api.AEApi.instance()
                .definitions()
                .blocks()
                .iOPort()
                .maybeStack(1)
                .orNull(), ItemList.Sensor_EV.get(1), ItemList.Emitter_EV.get(1),
                GTOreDictUnificator.get("circuitMaster", 1) },
            new FluidStack[] { Materials.SolderingAlloy.getMolten(576) },
            new ItemStack(BlockEcoStorageMEBus.INSTANCE, 1),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // 通风口 ×1（EV 组装机，t114m 用户配方）：外壳 + GT 机器方块（gt.blockmachines/5153）
        // + metaitem.02（gt.metaitem.02/21028）+ 电动马达 EV。（旧配方输出 ×2。）
        tryAddAssemblerNoCircuit(
            "estorage.vent",
            new ItemStack[] { new ItemStack(BlockEcoStorageCasing.INSTANCE, 1), gtMachineBlockStack(5153, 1),
                findItemStack("gregtech", "gt.metaitem.02", 21028, 1), ItemList.Electric_Motor_EV.get(1) },
            new FluidStack[] { Materials.SolderingAlloy.getMolten(576) },
            new ItemStack(BlockEcoStorageVent.INSTANCE, 1),
            TierEU.RECIPE_EV,
            10 * SECONDS);
    }

    // ------------------------------------------------------------------
    // t114m：原版工作台配方（用户提供的 3×3 有型合成）。
    // 写法教学：GameRegistry.addRecipe(new ShapedOreRecipe(输出, "行1", "行2", "行3",
    // 字符, 物品/矿词字符串, ...))——字符可以是 ItemStack（精确匹配）或矿词字符串
    // （如 "circuitMaster"，自动匹配所有注册该矿词的物品）。
    // ------------------------------------------------------------------
    private static void registerCraftingRecipes() {
        // E-Storage 控制器（gt.blockmachines/32030 = MTE 32030，RegistryMTE.L4）：
        // C A C C = circuitMaster（矿词）、A = AE2 ME 控制器、
        // F S F F = 力场发生器 EV、S = storage_array_casing、
        // C D C D = AE2 致密能源元件。
        ItemStack aeController = appeng.api.AEApi.instance()
            .definitions()
            .blocks()
            .controller()
            .maybeStack(1)
            .orNull();
        ItemStack aeDenseEnergy = appeng.api.AEApi.instance()
            .definitions()
            .blocks()
            .energyCellDense()
            .maybeStack(1)
            .orNull();
        if (aeController == null || aeDenseEnergy == null) {
            skippedRecipes++;
            LOG.warn(
                "Skipping ECO crafting recipe 'estorage.controller_workbench': an AE2 block is not registered yet (null).");
            return;
        }
        cpw.mods.fml.common.registry.GameRegistry.addRecipe(
            new net.minecraftforge.oredict.ShapedOreRecipe(
                ecoaegtnh.registry.RegistryMTE.L4.getStackForm(1),
                "CAC",
                "FSF",
                "CDC",
                'C',
                "circuitMaster",
                'A',
                aeController,
                'F',
                ItemList.Field_Generator_EV.get(1),
                'S',
                new ItemStack(BlockEcoStorageCasing.INSTANCE, 1),
                'D',
                aeDenseEnergy));

        // E-Calculator 控制器（gt.blockmachines/32033 = MTE 32033，RegistryEcal.ARRAY）：
        // C A C C = circuitMaster（矿词）、A = AE2 合成单元、
        // F S F F = 力场发生器 EV、S = ecalculator_casing、
        // C D C D = AE2 合成加速器。
        ItemStack aeUnit = appeng.api.AEApi.instance()
            .definitions()
            .blocks()
            .craftingUnit()
            .maybeStack(1)
            .orNull();
        ItemStack aeAccel = appeng.api.AEApi.instance()
            .definitions()
            .blocks()
            .craftingAccelerator()
            .maybeStack(1)
            .orNull();
        if (aeUnit == null || aeAccel == null) {
            skippedRecipes++;
            LOG.warn(
                "Skipping ECO crafting recipe 'ecal.controller_workbench': an AE2 block is not registered yet (null).");
            return;
        }
        cpw.mods.fml.common.registry.GameRegistry.addRecipe(
            new net.minecraftforge.oredict.ShapedOreRecipe(
                ecoaegtnh.registry.RegistryEcal.ARRAY.getStackForm(1),
                "CAC",
                "FSF",
                "CDC",
                'C',
                "circuitMaster",
                'A',
                aeUnit,
                'F',
                ItemList.Field_Generator_EV.get(1),
                'S',
                new ItemStack(ecoaegtnh.registry.RegistryEcal.casing, 1),
                'D',
                aeAccel));
    }

    // ------------------------------------------------------------------
    // ItemStack 辅助方法。
    // ------------------------------------------------------------------

    private static ItemStack component(StorageType type, CellSize size) {
        return type == StorageType.ITEM ? RegistryItems.itemComponent(size)
            : type == StorageType.FLUID ? RegistryItems.fluidComponent(size) : RegistryItems.essentiaComponent(size);
    }

    private static ItemStack housing(StorageType type, int tier) {
        return type == StorageType.ITEM ? RegistryItems.itemHousing(tier)
            : type == StorageType.FLUID ? RegistryItems.fluidHousing(tier) : RegistryItems.essentiaHousing(tier);
    }

    private static ItemStack cell(StorageType type, CellSize size) {
        return type == StorageType.ITEM ? RegistryItems.itemCell(size)
            : type == StorageType.FLUID ? RegistryItems.fluidCell(size) : RegistryItems.essentiaCell(size);
    }

    /**
     * t114o：按注册名 + damage 从指定 mod 取物品（NEI 复制格式 {@code modid:name/damage}，
     * 例如 dreamcraft:EngineeringProcessorItemEmeraldCore、ae2fc:fluid_part/4、
     * thaumicenergistics:storage.component/5、gregtech:gt.metaitem.01/17030）。
     * 运行时通过 {@code GameRegistry.findItem} 解析；找不到返回 null（配方跳过 + 警告）。
     */
    private static ItemStack findItemStack(String modid, String name, int damage, int count) {
        net.minecraft.item.Item item = cpw.mods.fml.common.registry.GameRegistry.findItem(modid, name);
        return item == null ? null : new ItemStack(item, count, damage);
    }

    /**
     * t114m：按原始方块 meta 取 GT 机器方块堆叠（gt.blockmachines/2360、/5153——这些注册项
     * 来自其它 GTNH mod，没有 ItemList 常量）。运行时通过 {@code GameRegistry.findBlock}
     * 解析；GT 缺席时返回 null（组装机辅助方法会跳过 + 警告）。
     */
    private static ItemStack gtMachineBlockStack(int meta, int count) {
        net.minecraft.block.Block block = cpw.mods.fml.common.registry.GameRegistry
            .findBlock("gregtech", "gt.blockmachines");
        return block == null ? null : new ItemStack(block, count, meta);
    }

    /**
     * t114p：按流体注册 ID 取 FluidStack（用户配方里的 GregTech_FluidDisplay/1080、/432、/818
     * 就是 FluidRegistry 的流体 ID）。ID 无效时返回 null（配方跳过 + 警告）。
     */
    private static FluidStack gtFluid(int id, int amount) {
        net.minecraftforge.fluids.Fluid f = net.minecraftforge.fluids.FluidRegistry.getFluid(id);
        return f == null ? null : new FluidStack(f, amount);
    }

    /**
     * 注册一条复杂组装机配方（物品 + 流体输入，无研究），任一输入/输出为 null 时跳过并
     * 打警告（t7 空值安全）。
     */
    private static void tryAddAssembler(String name, Object[] inputs, FluidStack[] fluids, ItemStack output,
        int circuit, long eut, int duration) {
        addAssembler(name, inputs, fluids, output, circuit, eut, duration);
    }

    /**
     * t114m：不带编程电路的组装机配方（用户标记"电路板=无"的配方）。
     * circuit(0) 并不等价——GTRecipeBuilder.circuit() 总会通过 GTUtility.getIntegratedCircuit
     * 塞一个集成电路物品进去——所以这个重载干脆不调用它。
     */
    private static void tryAddAssemblerNoCircuit(String name, Object[] inputs, FluidStack[] fluids, ItemStack output,
        long eut, int duration) {
        addAssembler(name, inputs, fluids, output, 0, eut, duration);
    }

    /**
     * t114x（用户）：配方输入支持矿典对象——inputs 元素可以是 ItemStack 或
     * OreDictItemStack("矿典名", 数量)。纯 ItemStack 走 itemInputs(ItemStack...)（原行为）；
     * 含 OreDictItemStack 时走 itemInputs(Object...)，GT 会把该矿典展开成全部注册物品的
     * 替代配方（NEI 显示矿典多物品，任意匹配）。
     */
    private static void setItemInputs(gregtech.api.util.GTRecipeBuilder builder, Object[] inputs) {
        boolean allStacks = true;
        for (Object o : inputs) {
            if (!(o instanceof ItemStack)) {
                allStacks = false;
                break;
            }
        }
        if (allStacks) {
            ItemStack[] stacks = new ItemStack[inputs.length];
            for (int i = 0; i < inputs.length; i++) {
                stacks[i] = (ItemStack) inputs[i];
            }
            builder.itemInputs(stacks);
        } else {
            builder.itemInputs(inputs);
        }
    }

    private static void addAssembler(String name, Object[] inputs, FluidStack[] fluids, ItemStack output, int circuit,
        long eut, int duration) {
        for (Object input : inputs) {
            if (input == null) {
                skippedRecipes++;
                LOG.warn("Skipping ECO recipe '{}': an input material is not registered yet (null).", name);
                return;
            }
        }
        for (FluidStack fluid : fluids) {
            if (fluid == null) {
                skippedRecipes++;
                LOG.warn("Skipping ECO recipe '{}': a fluid material is not registered yet (null).", name);
                return;
            }
        }
        if (output == null) {
            skippedRecipes++;
            LOG.warn("Skipping ECO recipe '{}': output is null.", name);
            return;
        }
        gregtech.api.util.GTRecipeBuilder builder = RA.stdBuilder()
            .fluidInputs(fluids)
            .itemOutputs(output)
            .eut(eut)
            .duration(duration);
        setItemInputs(builder, inputs);
        if (circuit > 0) {
            builder = builder.circuit(circuit);
        }
        java.util.Collection<gregtech.api.util.GTRecipe> added = builder.addTo(assemblerRecipes);
        if (added.isEmpty()) {
            // t105：映射会静默丢弃非法配方（比如 validateInputCount）——记日志，避免"缺配方"
            // 被成功计数器掩盖。
            skippedRecipes++;
            LOG.warn("ECO recipe '{}' was NOT added to the assembler map (input validation or duplicate).", name);
        } else {
            registeredAssemblerRecipes += added.size();
        }
    }

    /**
     * 注册一条装配线配方：研究物品、全部物品输入、全部流体输入、输出均非 null 时注册；
     * 否则打警告并跳过（t7 空值安全）。
     */
    private static void tryAddAL(String name, ItemStack research, Object[] inputs, FluidStack[] fluids,
        ItemStack output, long scanEut, long eut, int duration) {
        if (research == null) {
            skippedRecipes++;
            LOG.warn("Skipping ECO recipe '{}': research item is not registered yet (null).", name);
            return;
        }
        for (Object input : inputs) {
            if (input == null) {
                skippedRecipes++;
                LOG.warn("Skipping ECO recipe '{}': an input material is not registered yet (null).", name);
                return;
            }
        }
        for (FluidStack fluid : fluids) {
            if (fluid == null) {
                skippedRecipes++;
                LOG.warn("Skipping ECO recipe '{}': a fluid material is not registered yet (null).", name);
                return;
            }
        }
        if (output == null) {
            skippedRecipes++;
            LOG.warn("Skipping ECO recipe '{}': output is null.", name);
            return;
        }
        gregtech.api.util.GTRecipeBuilder builder = RA.stdBuilder()
            .metadata(RESEARCH_ITEM, research)
            .metadata(SCANNING, new Scanning(1 * MINUTES, scanEut))
            .fluidInputs(fluids)
            .itemOutputs(output)
            .eut(eut)
            .duration(duration);
        setItemInputs(builder, inputs);
        java.util.Collection<gregtech.api.util.GTRecipe> added = builder.addTo(AssemblyLine);
        if (added.isEmpty()) {
            // t105：装配线映射强制 validateInputCount(4,16) 且静默丢弃非法配方——记日志，
            // 避免"缺配方"被成功计数器掩盖。
            skippedRecipes++;
            LOG.warn("ECO recipe '{}' was NOT added to the AssemblyLine map (input validation or duplicate).", name);
        } else {
            registeredALRecipes += added.size();
        }
    }

    /**
     * t114p：注册一条太空组装机配方（gtnhintergalactic 的"太空组装模块 MK-I/II/III"）。
     * 任一输入/输出为 null 时跳过并打警告（t7 空值安全）。
     * t114r（用户）：IG_RecipeAdder.addSpaceAssemblerRecipe 不设置 MODULE_TIER metadata
     * （默认 1 = MK-I，任何模块都能跑），改用 GTRecipeBuilder 直接注册并显式
     * MODULE_TIER（机器 validateRecipe 用 tModuleTier >= 配方 MODULE_TIER 判断，
     * T1=1/T2=2/T3=3 字节码实证；t114s：MK-II 配方 = 2）。
     */
    private static void tryAddSpaceAssembler(String name, Object[] inputs, FluidStack[] fluids, ItemStack output,
        long eut, int duration, int moduleTier) {
        for (Object input : inputs) {
            if (input == null) {
                skippedRecipes++;
                LOG.warn(
                    "Skipping ECO recipe '{}': an input material is not registered yet (null) — inputs were: {}",
                    name,
                    java.util.Arrays.toString(inputs));
                return;
            }
        }
        for (FluidStack fluid : fluids) {
            if (fluid == null) {
                skippedRecipes++;
                LOG.warn("Skipping ECO recipe '{}': a fluid material is not registered yet (null).", name);
                return;
            }
        }
        if (output == null) {
            skippedRecipes++;
            LOG.warn("Skipping ECO recipe '{}': output is null.", name);
            return;
        }
        gregtech.api.util.GTRecipeBuilder builder = RA.stdBuilder()
            .metadata(gtnhintergalactic.recipe.IGRecipeMaps.MODULE_TIER, moduleTier)
            .fluidInputs(fluids)
            .itemOutputs(output)
            .eut(eut)
            .duration(duration);
        setItemInputs(builder, inputs);
        java.util.Collection<gregtech.api.util.GTRecipe> added = builder
            .addTo(gtnhintergalactic.recipe.IGRecipeMaps.spaceAssemblerRecipes);
        if (added.isEmpty()) {
            skippedRecipes++;
            LOG.warn("ECO recipe '{}' was NOT added to the spaceAssembler map (input validation or duplicate).", name);
        } else {
            LOG.info(
                "ECO space-assembler recipe '{}' registered (MK-{}, {} recipe(s)).",
                name,
                moduleTier,
                added.size());
        }
    }
}
