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
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.recipe.Scanning;

/**
 * ECO 闁诲孩绋掗敋闁稿绉瑰濂告濞戞浠氶梺鎸庣☉濠€?Storage闂佹寧绋戦ˇ鍗炩枔?GT 闂備焦婢樼粔鐢稿蓟閻斿皝鏋栭柕濞垮劚閺傗偓缂備緡鍋呴惇褰掑焵?
 * <p>
 * 闂備焦婢樼粔鐢稿蓟閻旂厧绀冩繛鍡樺姉閵嗗﹪鏌ｉ妸銉ヮ仼闁伙綆鍓熷顐も偓娑櫳戝▓宀勬倵濞戝磭鐣虫い?{@code docs/RECIPE_WRITING_GUIDE.md}闂佹寧绋戦悧鍛存嚈閹达箑妫橀柛銉╊棑缁€鍡涙煏?
 * <p>
 * t114o闂佹寧绋戦悧蹇涘极閵堝绠ｉ柛鎴欏€楃粈鍡涙煥濞戞瑥鍝洪柛妯稿€濆顒勬焻濞戞氨歇缂備緡鍨伴…鐑藉闯閹间礁纾绘慨姗堢岛閸嬫捇寮悰鈥充壕闁哄倽娉曢幗鐘绘煕鐏炶濡挎繛?= 婵犮垼鍩栭悧鏇㈡晬?+
 * 缂傚倷绀佺€氼亜鈻庨姀銈嗗剭闁告洦鍋傜槐锝吤归敐鍡欑煀鐟滅増鎸冲顕€鎮╅懠顒傤暡闂佸憡鑹鹃悧濠囧垂濮樿埖鏅?56k 缂傚倷绀佺€氼亜鈻?+ L4 婵犮垼鍩栭悧鏇㈡晬?
 * 闂佸憡鑹剧€氼厼锕㈡笟鈧幃褔鍩℃担瑙勫剬/濠电偟绻濆鎺旂礊?濠电姍鍕鐎?6
 * 闂佸搫顧€缁辨洜鍒掑鍥ㄥ晳闁告侗鍠楃花姘舵⒑閺夎法校闁哄瞼鍠栭弫宥呪槈濡櫣绋愭繛锝呮处缁诲嫰寮抽埀顒勬煟椤剙濡介柛鈺傜洴閺屽懘骞囬鐔绘嫬闂備焦褰冪粔瀛樼濞戙垺鍎?6
 * 婵炴垶鎼╂禍顏堝储閵堝妫橀柣褍鎽滅粈鍕熆閼哥數澧柨?婵＄偟鎳撳畷顒佹叏閳哄懎闂?闂佹眹鍨藉褔顢?
 * ME闂佽鍓濆畷鐢稿吹?闂備緡鍋呭畝鎼佀夐幘璇茬煑闁挎繂娲ㄩ惌瀣偡娴ｅ憡鍣烘繝褉鍋?+ 闂佺鐭囬崘銊у幀闂侀潻绲婚崝灞惧閹邦厽濯存繝濠傚暙闁伴亶鏌涘顒傚闁搞劍宀搁弫宥嗗緞閸艾浜惧〒?Calculator
 * 闂佺绻堥崝鎴﹀磿閹绢喗鐓€鐎广儱妫欓悡娆撴煥濞戞ɑ缍奵al.*闂佹寧绋戦ˇ顖炲礄閿熺姴绀嗛柣妯肩帛閻濈喖鏌?
 * 缂備焦绋戦ˇ顖滄閻斿吋鍋ㄩ柕濠忕畱閻撴洟姊洪幓鎺斝ｉ柡灞斤功閹峰骞戦幇闈涙倎闂?
 * <p>
 * 缂備礁鑻幖顐﹀焵椤掑倸甯堕柣锝冨姂瀹曟鈥﹂幒鏃傤槱t7闂佹寧绋戦¨鈧紒杈╂E2/ae2fc/TE4/dreamcraft 闂佺绻愰悿鍥ㄧ閸儲鍋嬮柍杞扮劍閹倿鏌?FML init
 * 闂佸搫鐗忛崰鏍涢崸妤€鐭楁い鏍ㄧ箓閸樻挳鎮樿箛鏃傤暡婵犫偓椤撶偐鏋栭柕濞垮劚閺傗偓闂佹寧绋戦張顒佹櫠瀹ュ棛顩烽柕澶涘濡层劌鈽?
 * 闂備焦婢樼粔鐢稿蓟閻斿吋鐒鹃柦妯侯槷缁?null 濠碘槅鍋€閸嬫捇鏌＄仦璇插姉闁逞屽墯閺岋繝鍩€椤掍焦鐓ｉ柟骞垮灪缁嬪鍩€椤掍焦缍囬柟鎯у暱瀵?闁哄鐗婇幐鎼佸吹椤撶喓鈻?null
 * 闂佸搫鍟崕濂告倻閿旇姤浜ら柛銉戝棗鐝梺鐟扮仛閹逛線顢楅悢鐓庡窛濠电姴绻掔粈澶岀磽娴ｅ湱绠戠紒妤€顦甸獮?null 婵炵鍋愭慨椋庡垝娴煎瓨鐓€鐎广儱妫欓悡娆撴煛鐎ｎ亜顏╃紓鍌涙崌瀹曟娊濡搁敐鍌氫壕?
 */
public final class Recipes {

    private static final Logger LOG = LogManager.getLogger("ECOAEGTNH");

    private Recipes() {}

    /** 闁诲繐绻愰幖顐︻敋椤撶姵顫曢柕蹇曞Х缁屽灝顪冮妶鍛儓缂?== CellSize.values() 闂佹眹鍔岀€氫即濡存惔銏″劅闊洤顑傞崑? */
    private static final CellSize[] SIZES = CellSize.values();

    /**
     * t102/t105闂佹寧绋掔喊宥夊极閻愬搫绀冪€光偓閸愭儳鎮侀梺杞扮劍濠㈡ê鈻嶉幒妤€鐏抽柡鍌濄€€閸嬫捇寮埀顒€锕㈤崶顒€绀夐柨娑樺娴煎倿鏌￠崘锕€鍔氱紒缁樻煥琚欓煫鍥ㄦ閸嬔囨煕濮樼厧鐏犲┑顔规櫊閺屽牓骞嬮幒鏃傜崶闂佺缈伴崕閬嶅箟閿熺姴绠戠紓浣股戝▓鍫曟煏?
     */
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
    // 闁诲孩绋掗敋闁稿绉归幆鍕潨閸垻顦?7 婵炴垶鎼╂禍椋庢濮樿泛鐏抽柡鍌濄€€閸嬫捇寮▎鐐枎婵?缂備緡鍋夐褔鎮?缂備焦绋戦ˇ杈殽? + 缂傚倷绀佺€氼亜鈻?缂備緡鍋夐褔鎮?闁诲繐绻愰幖顐︻敋?
    // 闂佹眹鍔岀€氼剚瀵奸幇顓熷婵犲﹤鍟柊閬嶆煛閸愵亜孝缂侇煈鍙冨畷銉╁醇閻旈浜ｉ梺?
    // t114o闂佹寧绋戦悧蹇涘极閵堝绠ｉ柛鎴欏€楃粈鍡涙煥濞戞瑦鐨戞繛鎻掔箻閺屽﹤顓奸崱妯煎幍婵炲濮村ù椋庡垝瀹ュ洦鍟戦柛娑卞枟缁?闁荤喍绀侀幊姗€宕㈤妶鍥╂／闁圭瀛╅弳顓炩槈?缂傚倷绀佺€氼亜鈻?+
    // 婵犮垼鍩栭悧鏇㈡晬?闂佸搫鍟版慨瀵歌姳椤撱垹瑙﹂柛顐ゅ枎閻忓洭鏌ㄥ☉妯绘睘hapelessOreRecipe闂佹寧绋戦ˇ顓㈠焵?
    // 婵犮垼鍩栭悧鏇㈡晬閹惧墎椹冲璺虹焸閻涙捇鎮楅悽鍨殌缂併劍鐓￠弫宥咁潰?缂?256k/1024k/4096k 闂?L4 婵犮垼鍩栭悧鏇㈡晬閹捐违濞?缂?16M/64M/256M 闂?L6 婵犮垼鍩栭悧鏇㈡晬閹捐违?
    // 婵?M 缂?1024M/4096M/16384M 闂?L9 婵犮垼鍩栭悧鏇㈡晬閹捐违?
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
                    // t7 缂備礁鑻幖顐﹀焵椤掑倸甯堕柣锝冨姂瀹曟鈥﹂幒鏃傜崶濠电姍鍕鐎瑰憡绻勯崠鏍嫚閹绘帞浠氶梺?ThaumicEnergistics
                    // 缂傚倸鍊搁幖顐︽嚈閹达箑绫嶉柡鍫滅祷缁€?null闂佹寧绋戦惌渚€鎮滈敂鑺ヤ氦?+ 闁荤姭鍋撻柨鏇楀亾闁硅绻濇俊?
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
    // t114o闂佹寧绋戦悧蹇涘极閵堝绠ｉ柛鎴欏€楃粈鍡涙煥?56k 缂傚倷绀佺€氼亜鈻庨姀銈嗘櫖闁割偁鍨归埛鏍煕?濠电偟绻濆鎺旂礊?濠电姍鍕鐎瑰憡绻堥弫? L4
    // 婵犮垼鍩栭悧鏇㈡晬閹剧粯鏅柛顐犲灩閳锋牠鏌?濠电偟绻濆鎺旂礊?濠电姍鍕鐎瑰憡绻堥弫宥嗗緞婵犲倸缍?6 闂佸搫顧€缁绘繈宕㈤妶澶婃闁稿繐顦崑鎾诲棘鐞涒€充壕?
    // 缂傚倷鐒﹂悷銈囨崲濮樿埖鍋╂繛鍡樺灦閻ｉ亶鎮楀☉娅亪宕戝澶嬪剮婵せ鍋撻柛妯稿€濆顒勬儌閸濄儳顦﹔egisterCells闂佹寧绋戦ˇ鎷屻亹娴ｅ湱鐟规繛鎴烇供濡棗顭?缂傚倷绀佺€氼亜鈻庨姀鈩冪秶闁规儳鍟垮鎶芥煏閸℃鈧悂宕ｈ箛娑欑劸?EV
    // 1920 EU/t闂?0 缂備礁顦扮敮鍥焵?
    // 闂佺粯甯掗敃顏堝极?144mb闂佹寧绋掔粙鎴λ囩紒妯肩彾闁规儼妫勭敮鎶芥煛閸屾粍鍤€闁汇埄鍋嗙槐鎾诲冀椤愮喐鐓犻梺娲绘線缁插鎯屾ィ鍐╂櫖?/2/3闂佹寧绋戦¨鈧紒杈ㄧ箘缁辨帡宕熼鍜佸仺闂備焦婢樼粔鐢稿蓟閻旂厧绫嶉柣妯虹仛閺嗏晠鎮规笟顖氱仜闁?
    // 闁哄鐗婇幐鎼佸矗閸℃稒鍋嬮柍杞扮劍閹倿鏌涜濞诧絿鎷归悢鍏煎仺闁靛绠戦悡鏇灻归悩鎻掝劉闁绘牕鐖奸獮瀣疀閵壯咁槱NEI闂佹寧绋戦ˇ顖炈囬弻銉ョ闁告挷鐒﹂悾?id闂佹寧绋戦惉鑲╁垝?findItemStack
    // 闂佸湱顭堥ˇ浼村极閻愬搫绀冮悘鐐跺Г閸婃娊寮堕埡鍌氬妞ゃ垺鍨垮顕€骞嗛棃鑸靛浮瀵悂骞囬埞鎯т壕?
    // ------------------------------------------------------------------
    private static void registerComponentsAndHousings() {
        FluidStack solder144 = Materials.SolderingAlloy.getMolten(144);

        // 256k 闂佺粯銇涢弲娑㈠箹瑜忕槐鎺楀礋椤忓拋鍋ㄩ梺鎸庣☉閻楀﹤螞閵堝鍋ㄥù锝呭暟閻斿懘鏌ㄥ☉姗嗘缂佽京妾癊2 256k 闁诲孩绋掗敋闁稿绉剁槐鎺楀礋椤忓拋鍋?+
        // 闂佺粯銇涢弲娑㈠箹瑜庡鍕礋椤撶喎鈧偤鏌?III闂佹寧绋戝鎭焑amcraft闂?
        // 閻庤鎮堕崕鎶藉煝閸忕厧绶為柛鏇ㄥ幗閸婄偤鏌?+ 闂佽桨鑳舵晶妤€鐣垫笟鈧幃鑺ユ媴閸愵亞鍞?闁? + 闁哄鏅滈惄顖毼熸笟鈧幃鑺ユ媴閸愵亞鍞?闁?闂?
        // t114x闂佹寧绋戦悧蹇涘极閵堝绠ｉ柛鎴欏€楃粈鍡涙煥濞戞瑦鐨戦柡浣告憸閹瑰嫰顢涘☉娆戝嚒闁哄鐗婇幐鎼佸矗閸℃稒鍋ㄩ柕濞у嫬鐒稿┑顔界箰缁插灝鈻撻幋锔藉剹闁割煈鍠栭幃?OreDictItemStack闂佹寧绋戝鐒mInputs(Object...)
        // 闁诲繒鍋炲ú鏍閹寸姵瀚氶柕澹懎鏁搁梺绋跨箳椤牓宕ｈ箛娑欑劸闁靛ě鍐ｆ寘闂佸憡绻€缁躲倗妲愰惄姗砄reDictUnificator.get
        // 闂佸憡鐟禍锝囨崲閹达箑鐐婇柣鎰暩閸╃姴鈽夐幘顖氫壕闂佸憡鑹惧ù宄扳枔閹达箑纭€闁哄洦宀搁崵瀣煟濡炵粯娅呴柟浣冲洤鐏抽柡鍌濄€€閸嬫捇寮ㄩ崓鐜禼uitAdvanced
        // 缂傚倷鑳堕崰宥囩博閹绢喖绀?IC2 婵°倕鍊归…鍥殽閸ヮ剚鍋ㄥù锝呭暟閻斿懘鏌℃径濠傜殹缂佽鲸绱慐I 闁诲繐绻楁ご绋课熸径宀€鐭嗛柣鎴灻悘?IC2:itemPartCircuitAdv闂佹寧绋戦ˇ顓㈠焵?
        tryAddAssemblerNoCircuit(
            "estorage.component_item_256k",
            new Object[] { appeng.api.AEApi.instance()
                .definitions()
                .materials()
                .cell256kPart()
                .maybeStack(1)
                .orNull(), findItemStack("dreamcraft", "item.EngineeringProcessorItemEmeraldCore", 0, 1),
                appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .engProcessor()
                    .maybeStack(1)
                    .orNull(),
                GTOreDictUnificator.get("circuitData", 2), new Object[] { "circuitAdvanced", 4 } },
            new FluidStack[] { solder144 },
            RegistryItems.itemComponent(CellSize.K_256),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // 256k 濠电偟绻濆鎺旂礊鐎ｎ剛纾奸柛鏇ㄤ簼椤愪粙鏌ㄥ☉妯煎婵☆偁鍊濋幃鑺ユ媴閸愵亞鍞撮梺鎸庣☉椤р偓缂佽京娅榚2fc 256k
        // 濠电偟绻濆鎺旂礊鐎ｎ兘鍋撳☉娅亪宕戝鍥╃＜闁告洦浜濋浠嬫煥濞戞ɑ缍抣uid_part/4闂? 濠电偟绻濆鎺旂礊鐎ｎ偄绶為柛鏇ㄥ幗閸婄偤鏌?II
        // 闂佹寧绋戝鎭焑amcraft闂? 閻庤鎮堕崕鎶藉煝閸忕厧绶為柛鏇ㄥ幗閸婄偤鏌?闁? + 闂佽桨鑳舵晶妤€鐣垫笟鈧幃鑺ユ媴閸愵亞鍞?闁? + 闁哄鏅滈惄顖毼熸笟鈧幃鑺ユ媴閸愵亞鍞?闁?闂?
        tryAddAssemblerNoCircuit(
            "estorage.component_fluid_256k",
            new Object[] { findItemStack("ae2fc", "fluid_part", 4, 1),
                findItemStack("dreamcraft", "item.EngineeringProcessorFluidEmeraldCore", 0, 1),
                appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .engProcessor()
                    .maybeStack(8)
                    .orNull(),
                GTOreDictUnificator.get("circuitData", 2), new Object[] { "circuitAdvanced", 4 } },
            new FluidStack[] { solder144 },
            RegistryItems.fluidComponent(CellSize.K_256),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // 256k 濠电姍鍕鐎瑰憡绻勭槐鎺楀礋椤忓拋鍋ㄩ梺鎸庣☉閻楀﹤螞閵堝鍋ㄥù锝呭暟閻斿懘鏌ㄥ☉姗嗘缂佽京娅塃4
        // 濠电姍鍕鐎瑰憡绻勯埀顒佺⊕閿氶柛瀣Ф缁辨帡宕熼鍜佸仺闂佹寧绋戝鍍紀rage.component/5闂? 濠电姍鍕鐎瑰憡绻冨鍕礋椤撶喎鈧偤鏌?I
        // 闂佹寧绋戝鎭焑amcraft闂? 閻庤鎮堕崕鎶藉煝閸忕厧绶為柛鏇ㄥ幗閸婄偤鏌?闁? + 闂佽桨鑳舵晶妤€鐣垫笟鈧幃鑺ユ媴閸愵亞鍞?闁? + 闁哄鏅滈惄顖毼熸笟鈧幃鑺ユ媴閸愵亞鍞?闁?闂?
        tryAddAssemblerNoCircuit(
            "estorage.component_essentia_256k",
            new Object[] { findItemStack("thaumicenergistics", "storage.component", 5, 1),
                findItemStack("dreamcraft", "item.EngineeringProcessorEssentiaPulsatingCore", 0, 1),
                appeng.api.AEApi.instance()
                    .definitions()
                    .materials()
                    .engProcessor()
                    .maybeStack(4)
                    .orNull(),
                GTOreDictUnificator.get("circuitData", 2), new Object[] { "circuitAdvanced", 4 } },
            new FluidStack[] { solder144 },
            RegistryItems.essentiaComponent(CellSize.K_256),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // L4 闂佺粯銇涢弲娑㈠箹瑜庡鍕冀瑜濈槐锕傛煥濞戞澧㈤柡浣告憸閹?1闂佹寧绋戦¨鈧紒杈╁濞艰鈽夐姀鈾€鏋嗛梺娲绘線缁插鎯?闁? + gt.metaitem.01/17030 闁? + /17516 闁? +
        // /27516 闁? + 闂備緡鍋呭Σ鎺旀椤愶絽绶為柛鏇ㄥ幗閸婄偤鏌?+ 闁荤姳绶ょ槐鏇㈡偩缂佹ê绶為柛鏇ㄥ幗閸婄偤鏌涢敐鍐ㄥΩ闁?
        tryAddAssembler(
            "estorage.housing_item_l4",
            new Object[] { new Object[] { "circuitAdvanced", 2 }, findItemStack("gregtech", "gt.metaitem.01", 17030, 3),
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

        // L4 濠电偟绻濆鎺旂礊鐎ｎ偄绶為柡宓秶澶勯梺鎸庣☉閻楀繘寮崫銉﹀磯?2闂佹寧绋戦¨鈧紒杈╁濞煎繘骞橀崘鎻掓辈闂佸憡鑹鹃惉鍏兼櫠閸ф浼犲ù锝呭濡棗顭块崷顓у姕缂?7030闁? / 17516 /
        // 27516闁? + 闂備緡鍋呭Σ鎺旀?闁荤姳绶ょ槐鏇㈡偩缂佹ê绶為柛鏇ㄥ幗閸婄偤鏌涢敐搴ｅ帨缂佽鲸宀搁弫?
        // 婵炶揪绲藉Λ妤佸閹邦喚鐭欓悗锝庝憾濡查亶鏌ｉ悙鍙夘棞婵炲懏甯￠獮鎴﹀閳ュ磭浜?24
        // 闂佸憡鐟ラ崵鏍濞嗗浚鍟呴柕澹偓閺屻倕顭跨捄鍝勵伀闁诡喖锕畷鎶解€﹂幒鏃傤槴闁?闂侀潧妫斿ù鍥敇閸濄儳涓嶆俊銈傚亾妞わ腹鏅犻幃鍫曞幢濡や胶褰?23 闁?闂?
        tryAddAssembler(
            "estorage.housing_fluid_l4",
            new Object[] { new Object[] { "circuitAdvanced", 2 }, findItemStack("gregtech", "gt.metaitem.01", 17030, 3),
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

        // L4 濠电姍鍕鐎瑰憡绻冨鍕冀瑜濈槐锕傛煥濞戞澧㈤柡浣告憸閹?3闂佹寧绋戦¨鈧紒杈╁濞艰鈽夐姀鈾€鏋嗛梺娲绘線缁插鎯?闁? + 17030 闁? + 17516 + 27516 闁? +
        // 閻庤鎮堕崕鎶藉煝閸忕厧绶為柛鏇ㄥ幗閸婄偤鏌?
        // + 闁荤姳绶ょ槐鏇㈡偩缂佹ê绶為柛鏇ㄥ幗閸婄偤鏌涢敐鍐ㄥΩ闁?
        tryAddAssembler(
            "estorage.housing_essentia_l4",
            new Object[] { new Object[] { "circuitAdvanced", 2 }, findItemStack("gregtech", "gt.metaitem.01", 17030, 3),
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

        // ---------- L6 婵犮垼鍩栭悧鏇㈡晬閹剧粯鏅柛锔绢€楳闂?s闂佹寧绋戦張顒€螞閵堝悿瑙勬媴妞嬪海歇闂佹寧绋戦惉濂稿极閸濄儲宕?1/2/3闂?----------
        // 闁哄鐗婇幐鎼佸矗閸℃稒鏅慨妯夸含閻帡鏌＄€ｂ晞鍏岄柡浣告憸閹瑰嫰顢? + metaitem.01/17317闁? + /17516 + /27516闁? +
        // 闂備緡鍋呭Σ鎺旀椤愶絽绶為柛鏇ㄥ幗閸婄偤鏌涢敐搴ｅ帒? + 闁荤姳绶ょ槐鏇㈡偩缂佹ê绶為柛鏇ㄥ幗閸婄偤鏌涢敐搴ｅ帒?
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

        // ---------- L9 婵犮垼鍩栭悧鏇㈡晬閹剧粯鏅柛锔炬緯闂?s闂佹寧绋戦張顒€螞閵堝悿瑙勬媴妞嬪海歇闂佹寧绋戦惉濂稿极閸濄儲宕?1/2/3闂?----------
        // 闁哄鐗婇幐鎼佸矗閸℃稒鏅慨妯虹－瀛濋柣搴濈祷閸嬫劙寮崫銉﹀磯妞? + metaitem.01/17129闁? + /17516 + /27516闁? + 闂備緡鍋呭Σ鎺旀椤愶絽绶為柛鏇ㄥ幗閸婄偤鏌涢敐搴ｅ帒?
        // + 闁荤姳绶ょ槐鏇㈡偩缂佹ê绶為柛鏇ㄥ幗閸婄偤鏌涢敐搴ｅ帒?
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
    // t114p闂佹寧绋戦悧蹇涘极閵堝绠ｉ柛鎴欏€楃粈鍡涙煥濞戞瑨澹橀柣掳鍔戝畷鎺楀Ω瑜忛惌瀣归悩鐑樼【闁告瑥绻橀弻?1024k 闂?16384m +
    // 闁诲海鎳撳ú銈夋偩娴犲鏅柛顐犲灩閳锋牠鏌?濠电偟绻濆鎺旂礊?濠电姍鍕鐎瑰憡绻堝畷锝呂熺喊杈ㄧ稈闂佹寧绋戦ˇ顓㈠焵?
    // 闁哄鐗婇幐鎼佸矗閸℃稒鍋嬮柍杞扮劍閹倿鏌涜濞诧絿鎷归悢鍏煎仺闁靛绠戦悡鏇灻归悩鎻掝劉闁绘牕鐖奸獮瀣疀閵壯咁槱NEI闂佹寧绋戦ˇ顖炈囬弻銉ョ闁告挷鐒﹂悾?id闂佹寧绋戦惉鑲╁垝?findItemStack
    // 闂佸湱顭堥ˇ浼村极閻愬搫绀冮悘鐐跺Г閸?damage 闁哄鏅滈崝姗€銆侀幋锕€绫嶉柟顖炴緩閹烘鍑犻柟鎵虫杹閸?
    // - 1024k/4096k闂佹寧绋掗鎬?缂傚倷绀佺€氼垶藟婵犲洤瀚夋繛宸簼閹崇娀鏌ㄥ☉妯肩伇闁告挶鍔戦弻?144mb闂?
    // - 16m..16384m闂佹寧绋掓穱娲夋繝鍥ㄧ厐鐎广儱娲ゅ▓鐘绘煥濞戞澧㈤柣鏍缁岸宕滄担绯曟寘闂?= 闂佸憡鑹鹃惉鑲╂偖椤愶箑鍨傞悗锝傛櫇缁夐潧鈽夐幘顖氫壕濠碘剝顨愮徊璺ㄥ垝瀹ュ棛顩烽悹浣告贡缁€濉員NH
    // 闂佽鍨奸崹顖滄閵夆晜鏅鑸电〒缁€澶嬬箾缂堢姵顦风紓?1080/432 闂佽桨鐒﹀姗€鎮?id闂?
    // - 闁诲海鎳撳ú銈夋偩娴犲鏅柛顐犲灩閳锋牠鏌?濠电偟绻濆鎺旂礊鐎ｎ喗鏅鑸电〒缁愭顭挎０婵呯敖闁宠鐗滅槐鎺楀礋椤曞懏顥婂┑鈽嗗灙閳ь剝娅曢崑?MK-III闂佹寧绋戝娉僴hintergalactic
    // spaceAssembler闂佹寧绋戦鐕―ULE_TIER=3闂佹寧绋戦ˇ顓㈠焵?
    // 闂佸憡鐟﹂…鍥綖閹烘梹鍠嗛柛鏇ㄥ亜閻忕喖鏌ㄥ☉妯煎ⅱ闁轰降鍊濋獮瀣礄閵堝洨顦梺鎸庣⊕濮樸劍鏅堕崸妤€浼犲ù锝囧劋閺?1 婵?dreamcraft 婵犮垼娉涚€氼噣骞冩繝鍥ч棷?闂?濠电偟绻濆鎺旂礊?2
    // 婵?闂?濠电姍鍕鐎?4 婵?
    // 闂佹寧绋戦悧鍡浰囬埡鍛仩闁糕剝顨嗛悵?III 缂備緡鍨槐顔炬濮樿埖鏅繛鎴烇供濡查亶鏌ｉ悙鍙夘棞婵?IV
    // 缂備緡鍨甸濠勬嫻?1:4:8闂侀潧妫楅崐鎼佸矗閻愵剚濯存繛鍡樺笧缂堝鏌涜箛瀣姷缂佽鲸顭篍2/ae2fc/TE4 缂傚倷绀佺€氼亜鈻庨姀銈呂ュù锝夋櫜缁憋絿绱掔€ｎ亶鍎忔い锔规櫊閹爼宕卞Δ浣哄綔闂?
    // 闂佹椿婢€缁插鎯屾ィ鍐ㄎュ☉鎾跺厳 闂備緡鍠撻崝瀣枎閵忋倕违濞达綀顫夌花姘舵煕閿濆啫濡介柡宀€鍠栧畷绋款渻鐏忔牕浜惧ù锝呮贡閵堬箑霉閿濆棙鎯堢紒杈ㄧ缁嬪鎯旈敐鍛寘闂佸憡绻€閼宠埖鏅跺Δ鍛剮缂佸瀵ч崐閬嶆煏?
    // ------------------------------------------------------------------
    private static void registerComponentChain() {
        FluidStack solder144 = Materials.SolderingAlloy.getMolten(144);
        // dreamcraft 婵犮垼娉涚€氼噣骞冩繝鍥ч棷妞ゎ厽甯炵粈鍕煟濡炵粯娅呴柟?III / 闂佺粯銇涢弲娑㈠箹?IV / 濠电偟绻濆鎺旂礊?II / 濠电姍鍕鐎?I闂佹寧绋戦ˇ顓㈠焵?
        ItemStack procItemIII = findItemStack("dreamcraft", "item.EngineeringProcessorItemEmeraldCore", 0, 1);
        ItemStack procItemIV = findItemStack("dreamcraft", "item.EngineeringProcessorItemAdvEmeraldCore", 0, 1);
        // AE2 閻庤鎮堕崕鎶藉煝閸忕厧绶為柛鏇ㄥ幗閸婄偤鏌涢敐搴ｅ帨缂佽鲸鐛揳mage 24闂佹寧绋戦ˇ顐﹀焵椤掍焦鐓涢柍褜鍓氬褰掑汲閻斿吋鐓傞煫鍥ㄦ煥閻﹀鏌ｅ缁樻珔闁逛匠鍥ㄥ亱闁割偁鍎辩敮鎶芥煛閸屾氨鐣柍?
        java.util.function.IntFunction<ItemStack> eng = n -> appeng.api.AEApi.instance()
            .definitions()
            .materials()
            .engProcessor()
            .maybeStack(n)
            .orNull();

        // ---------- 1024k 缂傚倷绀佺€氼亜鈻庨姀銈嗘櫖闁割偁鍨婚惌瀣偡娴ｅ憡鍣烘繝褉鍋?IV闂?0s闂佹寧绋戦張顒€螞閵堝鍋ㄥù锝呭暟閻斿懘鏌ㄥ☉妯肩伇闁告挶鍔戦弻?144闂?----------
        // 闂佺粯銇涢弲娑㈠箹瑜旈弫宥咁潰濞?/58 + 婵犮垼娉涚€氼噣骞冩繝鍥ч棷闁愁偅婀廔 + 閻庤鎮堕崕鎶藉煝閸忕厧绶為柛鏇ㄥ幗閸婄偤鏌涢敐搴ｅ帒?6 + 缂備緡鍠楀畷妯尖偓姘▕閹姤鎷呴崘顏嗗敶闁? +
        // 闂佽桨鑳舵晶妤€鐣垫笟鈧幃鑺ユ媴閸愵亞鍞撮柤?
        tryAddAssemblerNoCircuit(
            "estorage.component_item_1024k",
            new ItemStack[] { findItemStack("appliedenergistics2", "item.ItemMultiMaterial", 58, 1), procItemIII,
                eng.apply(16), GTOreDictUnificator.get("circuitElite", 2), GTOreDictUnificator.get("circuitData", 4) },
            new FluidStack[] { solder144 },
            RegistryItems.itemComponent(CellSize.K_1024),
            TierEU.RECIPE_IV,
            10 * SECONDS);
        // 濠电偟绻濆鎺旂礊鐎ｎ喗鏅慨婵囩煚2fc fluid_part/5 + 濠电偟绻濆鎺旂礊鐎ｎ偄绶為柛鏇ㄥ幗閸婄偤鏌涢敐鍌氭櫛I闁?闂佹寧绋戦懟顖炲矗閻愵剚濯存繛鍡楃箲閸婇亶鏌ｅ缁樻珔闁逛匠鍥ㄥ亱?
        tryAddAssemblerNoCircuit(
            "estorage.component_fluid_1024k",
            new ItemStack[] { findItemStack("ae2fc", "fluid_part", 5, 1),
                findItemStack("dreamcraft", "item.EngineeringProcessorFluidEmeraldCore", 0, 2), eng.apply(16),
                GTOreDictUnificator.get("circuitElite", 2), GTOreDictUnificator.get("circuitData", 4) },
            new FluidStack[] { solder144 },
            RegistryItems.fluidComponent(CellSize.K_1024),
            TierEU.RECIPE_IV,
            10 * SECONDS);
        // 濠电姍鍕鐎瑰憡绻堥弫宥咁潰瀹€? storage.component/6 + 濠电姍鍕鐎瑰憡绻冨鍕礋椤撶喎鈧偤鏌涢敐鍌氭櫛闁?闂佹寧绋戦懟顖炲矗閻愵剚濯存繛鍡楃箲閸婇亶鏌ｅ缁樻珔闁逛匠鍥ㄥ亱?
        tryAddAssemblerNoCircuit(
            "estorage.component_essentia_1024k",
            new ItemStack[] { findItemStack("thaumicenergistics", "storage.component", 6, 1),
                findItemStack("dreamcraft", "item.EngineeringProcessorEssentiaPulsatingCore", 0, 4), eng.apply(16),
                GTOreDictUnificator.get("circuitElite", 2), GTOreDictUnificator.get("circuitData", 4) },
            new FluidStack[] { solder144 },
            RegistryItems.essentiaComponent(CellSize.K_1024),
            TierEU.RECIPE_IV,
            10 * SECONDS);

        // ---------- 4096k 缂傚倷绀佺€氼亜鈻庨姀銈嗘櫖闁割偁鍨婚惌瀣偡娴ｅ憡鍣烘繝褉鍋?LuV闂?0s闂佹寧绋戦張顒€螞閵堝鍋ㄥù锝呭暟閻斿懘鏌ㄥ☉妯肩伇闁告挶鍔戦弻?144闂?----------
        // 闂佺粯銇涢弲娑㈠箹瑜旈弫宥咁潰濞?/59 + 婵犮垼娉涚€氼噣骞冩繝鍥ч棷闁愁偅婀?+ 閻庤鎮堕崕鎶藉煝閸忕厧绶為柛鏇ㄥ幗閸婄偤鏌涢敐搴ｅ帒?2 + 婵犮垹鐖㈤崘顏嗘啣闂佹椿婢€缁插鎯岄幑鎰Б2 +
        // 缂備緡鍠楀畷妯尖偓姘▕閹姤鎷呴崘顏嗗敶闁?
        tryAddAssemblerNoCircuit(
            "estorage.component_item_4096k",
            new ItemStack[] { findItemStack("appliedenergistics2", "item.ItemMultiMaterial", 59, 1), procItemIV,
                eng.apply(32), GTOreDictUnificator.get("circuitMaster", 2),
                GTOreDictUnificator.get("circuitElite", 4) },
            new FluidStack[] { solder144 },
            RegistryItems.itemComponent(CellSize.K_4096),
            TierEU.RECIPE_LuV,
            10 * SECONDS);
        // 濠电偟绻濆鎺旂礊鐎ｎ喗鏅慨婵囩煚2fc fluid_part/6 + 濠电偟绻濆鎺旂礊鐎ｎ偄绶為柛鏇ㄥ幗閸婄偤鏌涢敐鍌氭櫛I闁?
        tryAddAssemblerNoCircuit(
            "estorage.component_fluid_4096k",
            new ItemStack[] { findItemStack("ae2fc", "fluid_part", 6, 1),
                findItemStack("dreamcraft", "item.EngineeringProcessorFluidEmeraldCore", 0, 4), eng.apply(32),
                GTOreDictUnificator.get("circuitMaster", 2), GTOreDictUnificator.get("circuitElite", 4) },
            new FluidStack[] { solder144 },
            RegistryItems.fluidComponent(CellSize.K_4096),
            TierEU.RECIPE_LuV,
            10 * SECONDS);
        // 濠电姍鍕鐎瑰憡绻堥弫宥咁潰瀹€? storage.component/7 + 濠电姍鍕鐎瑰憡绻冨鍕礋椤撶喎鈧偤鏌涢敐鍌氭櫛闁?
        tryAddAssemblerNoCircuit(
            "estorage.component_essentia_4096k",
            new ItemStack[] { findItemStack("thaumicenergistics", "storage.component", 7, 1),
                findItemStack("dreamcraft", "item.EngineeringProcessorEssentiaPulsatingCore", 0, 8), eng.apply(32),
                GTOreDictUnificator.get("circuitMaster", 2), GTOreDictUnificator.get("circuitElite", 4) },
            new FluidStack[] { solder144 },
            RegistryItems.essentiaComponent(CellSize.K_4096),
            TierEU.RECIPE_LuV,
            10 * SECONDS);

        // ---------- 16m 缂傚倷绀佺€氼亜鈻庨姀銈嗘櫖闁割偓缍嗗Λ鍛存⒑閺夎法肖闁?ZPM闂?0s闂佹寧绋戦惉濂告偉閾忓湱鐭?= 4096k 缂傚倷绀佺€氼亜鈻庨姀銈嗘櫖?----------
        ItemStack gt32675 = findItemStack("gregtech", "gt.metaitem.01", 32675, 1); // 闂佸憡姊圭粙鎴濃攦閳ь剟鏌涘▎鎰仼闁轰焦鎹囧畷?LuV
        ItemStack bm1766 = findItemStack("gregtech", "gt.blockmachines", 1766, 4);
        tryAddAL(
            "estorage.component_item_16m",
            RegistryItems.itemComponent(CellSize.K_4096),
            new ItemStack[] { findItemStack("appliedenergistics2", "item.ItemMultiMaterial", 60, 1), procItemIV,
                eng.apply(64), GTOreDictUnificator.get("circuitUltimate", 4), gt32675, bm1766 },
            new FluidStack[] { gtFluid(1073, 576) },
            RegistryItems.itemComponent(CellSize.M_16),
            TierEU.RECIPE_ZPM,
            TierEU.RECIPE_ZPM,
            60 * SECONDS);
        tryAddAL(
            "estorage.component_fluid_16m",
            RegistryItems.fluidComponent(CellSize.K_4096),
            new ItemStack[] { findItemStack("ae2fc", "fluid_part", 7, 1),
                findItemStack("dreamcraft", "item.EngineeringProcessorFluidEmeraldCore", 0, 4), eng.apply(64),
                GTOreDictUnificator.get("circuitUltimate", 4), gt32675, bm1766 },
            new FluidStack[] { gtFluid(1073, 576) },
            RegistryItems.fluidComponent(CellSize.M_16),
            TierEU.RECIPE_ZPM,
            TierEU.RECIPE_ZPM,
            60 * SECONDS);
        tryAddAL(
            "estorage.component_essentia_16m",
            RegistryItems.essentiaComponent(CellSize.K_4096),
            new ItemStack[] { findItemStack("thaumicenergistics", "storage.component", 8, 1),
                findItemStack("dreamcraft", "item.EngineeringProcessorEssentiaPulsatingCore", 0, 8), eng.apply(64),
                GTOreDictUnificator.get("circuitUltimate", 4), gt32675, bm1766 },
            new FluidStack[] { gtFluid(1073, 576) },
            RegistryItems.essentiaComponent(CellSize.M_16),
            TierEU.RECIPE_ZPM,
            TierEU.RECIPE_ZPM,
            60 * SECONDS);

        // ---------- 64m 缂傚倷绀佺€氼亜鈻庨姀銈嗘櫖闁割偓缍嗗Λ鍛存⒑閺夎法肖闁?ZPM闂?20s闂佹寧绋戦惉濂告偉閾忓湱鐭?= 16m闂?----------
        tryAddAL(
            "estorage.component_item_64m",
            RegistryItems.itemComponent(CellSize.M_16),
            new ItemStack[] { findItemStack("appliedenergistics2", "item.ItemMultiMaterial", 60, 4),
                findItemStack("dreamcraft", "item.EngineeringProcessorItemAdvEmeraldCore", 0, 4), eng.apply(64),
                GTOreDictUnificator.get("circuitUltimate", 6), findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.blockmachines", 1766, 8) },
            new FluidStack[] { gtFluid(1073, 8 * 144) },
            RegistryItems.itemComponent(CellSize.M_64),
            TierEU.RECIPE_ZPM,
            TierEU.RECIPE_ZPM,
            120 * SECONDS);
        tryAddAL(
            "estorage.component_fluid_64m",
            RegistryItems.fluidComponent(CellSize.M_16),
            new ItemStack[] { findItemStack("ae2fc", "fluid_part", 7, 4),
                findItemStack("dreamcraft", "item.EngineeringProcessorFluidEmeraldCore", 0, 16), eng.apply(64),
                GTOreDictUnificator.get("circuitUltimate", 6), findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.blockmachines", 1766, 8) },
            new FluidStack[] { gtFluid(1073, 8 * 144) },
            RegistryItems.fluidComponent(CellSize.M_64),
            TierEU.RECIPE_ZPM,
            TierEU.RECIPE_ZPM,
            120 * SECONDS);
        tryAddAL(
            "estorage.component_essentia_64m",
            RegistryItems.essentiaComponent(CellSize.M_16),
            new ItemStack[] { findItemStack("thaumicenergistics", "storage.component", 8, 4),
                findItemStack("dreamcraft", "item.EngineeringProcessorEssentiaPulsatingCore", 0, 32), eng.apply(64),
                GTOreDictUnificator.get("circuitUltimate", 6), findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.blockmachines", 1766, 8) },
            new FluidStack[] { gtFluid(1073, 8 * 144) },
            RegistryItems.essentiaComponent(CellSize.M_64),
            TierEU.RECIPE_ZPM,
            TierEU.RECIPE_ZPM,
            120 * SECONDS);

        // ---------- 256m 缂傚倷绀佺€氼亜鈻庨姀銈嗘櫖闁割偓缍嗗Λ鍛存⒑閺夎法肖闁?UV闂?0s闂佹寧绋戦惉濂告偉閾忓湱鐭?= 64m闂?----------
        ItemStack oc103 = findItemStack("OpenComputers", "item", 103, 1);
        ItemStack gt32676 = findItemStack("gregtech", "gt.metaitem.01", 32676, 1); // 闂佸憡姊圭粙鎴濃攦閳ь剟鏌涘▎鎰仼闁轰焦鎹囧畷?ZPM
        ItemStack bm1748 = findItemStack("gregtech", "gt.blockmachines", 1748, 4);
        tryAddAL(
            "estorage.component_item_256m",
            RegistryItems.itemComponent(CellSize.M_64),
            new ItemStack[] { findItemStack("appliedenergistics2", "item.ItemMultiMaterial", 60, 16), oc103,
                GTOreDictUnificator.get("circuitSuperconductor", 2), GTOreDictUnificator.get("circuitUltimate", 4),
                gt32676, bm1748 },
            new FluidStack[] { gtFluid(1073, 16 * 144) },
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
            new FluidStack[] { gtFluid(1073, 16 * 144) },
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
            new FluidStack[] { gtFluid(1073, 16 * 144) },
            RegistryItems.essentiaComponent(CellSize.M_256),
            TierEU.RECIPE_UV,
            TierEU.RECIPE_UV,
            60 * SECONDS);

        // ---------- 1024m 缂傚倷绀佺€氼亜鈻庨姀銈嗘櫖闁割偓缍嗗Λ鍛存⒑閺夎法肖闁?UV闂?20s闂佹寧绋戦惉濂告偉閾忓湱鐭?= 256m闂?----------
        tryAddAL(
            "estorage.component_item_1024m",
            RegistryItems.itemComponent(CellSize.M_256),
            new ItemStack[] { findItemStack("appliedenergistics2", "item.ItemMultiMaterial", 60, 64),
                findItemStack("OpenComputers", "item", 103, 1), findItemStack("OpenComputers", "item", 103, 1),
                GTOreDictUnificator.get("circuitSuperconductor", 4),
                findItemStack("gregtech", "gt.metaitem.01", 32676, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32676, 1),
                findItemStack("gregtech", "gt.blockmachines", 1748, 8) },
            new FluidStack[] { gtFluid(1073, 18 * 144) },
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
            new FluidStack[] { gtFluid(1073, 18 * 144) },
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
            new FluidStack[] { gtFluid(1073, 18 * 144) },
            RegistryItems.essentiaComponent(CellSize.M_1024),
            TierEU.RECIPE_UV,
            TierEU.RECIPE_UV,
            120 * SECONDS);

        // ---------- 4096m 缂傚倷绀佺€氼亜鈻庨姀銈嗘櫖闁割偓缍嗗Λ鍛存⒑閺夎法肖闁?UHV闂?0s闂佹寧绋戦惉濂告偉閾忓湱鐭?= 1024m闂佹寧绋掔粙鎺旂矈閿旇姤濯?432 + 1080
        // 闂佸憡鐟ラ惌浣烘椤撱垹绀傞柕澶樺灣缁€?----------
        ItemStack gt32677 = findItemStack("gregtech", "gt.metaitem.01", 32677, 1); // 闂佸憡姊圭粙鎴濃攦閳ь剟鏌涘▎鎰仼闁轰焦鎹囧畷?UV
        ItemStack bm1808 = findItemStack("gregtech", "gt.blockmachines", 1808, 4);
        tryAddAL(
            "estorage.component_item_4096m",
            RegistryItems.itemComponent(CellSize.M_1024),
            new ItemStack[] { findItemStack("appliedenergistics2", "item.ItemMultiMaterial", 60, 64),
                findItemStack("OpenComputers", "item", 103, 4), findItemStack("OpenComputers", "item", 103, 4),
                GTOreDictUnificator.get("circuitInfinite", 4), gt32677, bm1808 },
            new FluidStack[] { gtFluid(430, 6 * 144), gtFluid(1073, 12 * 144) },
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
            new FluidStack[] { gtFluid(430, 6 * 144), gtFluid(1073, 12 * 144) },
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
            new FluidStack[] { gtFluid(430, 6 * 144), gtFluid(1073, 12 * 144) },
            RegistryItems.essentiaComponent(CellSize.M_4096),
            TierEU.RECIPE_UHV,
            TierEU.RECIPE_UHV,
            60 * SECONDS);

        // ---------- 16384m 缂傚倷绀佺€氼亜鈻庨姀銈嗘櫖闁割偓缍嗗Λ鍛存⒑閺夎法肖闁?UHV闂?20s闂佹寧绋戦惉濂告偉閾忓湱鐭?= 4096m闂佹寧绋掔粙鎺旂矈閿旇姤濯?432 + 1080
        // 闂佸憡鐟ラ惌浣烘椤撱垹绀傞柕澶樺灣缁€?----------
        tryAddAL(
            "estorage.component_item_16384m",
            RegistryItems.itemComponent(CellSize.M_4096),
            new ItemStack[] { findItemStack("appliedenergistics2", "item.ItemMultiMaterial", 60, 64),
                findItemStack("OpenComputers", "item", 103, 12), findItemStack("miscutils", "MU-metaitem.01", 32105, 1),
                GTOreDictUnificator.get("circuitInfinite", 8), findItemStack("gregtech", "gt.metaitem.01", 32677, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32677, 1),
                findItemStack("gregtech", "gt.blockmachines", 1808, 8) },
            new FluidStack[] { gtFluid(430, 12 * 144), gtFluid(1073, 16 * 144) },
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
            new FluidStack[] { gtFluid(430, 12 * 144), gtFluid(1073, 16 * 144) },
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
            new FluidStack[] { gtFluid(430, 12 * 144), gtFluid(1073, 16 * 144) },
            RegistryItems.essentiaComponent(CellSize.M_16384),
            TierEU.RECIPE_UHV,
            TierEU.RECIPE_UHV,
            120 * SECONDS);

        // ---------- 闁诲海鎳撳ú銈夋偩閻ｅ瞼纾奸柛鏇ㄤ簼椤愪粙鏌ㄥ☉妯煎闁靛洤锕︾划姘舵惞閸︻厾鐓侀柣鐔剁閹冲繗鍟梺?MK-III闂佹寧绋戦鐤縑闂?20s闂?----------
        // 闂佺粯銇涢弲娑㈠箹瑜旈弫宥呪枎閻? 婵炲瓨绮岄幖顐ｅ閹邦喒鍋撻悷鐗堟悙闁伙絼绮欓幆鍕潨閸垻顦㊣Items.cellUniverse API闂? metaitem.03/6581闁?4 +
        // metaitem.01/32047闁? + tectech 闂佸搫鍟晶搴ㄥ煘閺嶎厼鍌ㄩ悗锝庡墰缁辨岸鏌涢敃鈧幖顐ャ亹閸岀偞鍋ㄩ柣鏃堟敱閻?8闁?2 +
        // 缂備礁顑呴崯鍧楁偩妤ｅ啫鎹堕柛婵嗗缁叉椽鏌ｉ姀銏犳瀻婵?8闁?2 +
        // metaitem.03/4143闁? + /4141闁?闂佹寧绋掔粙鎺旂矈閿旇姤濯?818闁?6864闂?
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
            new FluidStack[] { gtFluid(806, 36864) },
            RegistryItems.itemCell(CellSize.UNIVERSE),
            TierEU.RECIPE_UXV,
            120 * SECONDS,
            3); // MK-III
        // 濠电偟绻濆鎺旂礊鐎ｎ喗鏅慨婵囩煚2fc 闁诲海鎳撳ú銈夋偩閽樺瑙勬媴妞嬪海歇闂佺儵鏅滈敃顐ゆ濮楊満emAndBlockHolder.ARTIFICIAL_UNIVERSE_CELL闂? bartworks
        // 闁烩剝甯掗幊搴ㄦ儊閹达箑绾?10112闁?4 + metaitem.01/32047闁? + GoodGenerator yotta 濠电偟绻濆鎺旂礊鐎ｎ剛纾?9闁? +
        // kekztech TFFT/10闁? + tectech 闁?2闁? + metaitem.03/4143闁? + /4141闁?闂佹寧绋掔粙鎺旂矈閿旇姤濯?818闁?6864闂?
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
            new FluidStack[] { gtFluid(806, 36864) },
            RegistryItems.fluidCell(CellSize.UNIVERSE),
            TierEU.RECIPE_UXV,
            120 * SECONDS,
            3); // MK-III

        // ---------- 婵犻潧鍊稿ú銊╁磻閿濆鈷掓い蹇撳閹界娀鏌￠崨顓犲煟婵☆垪鍋撻梺鎸庣☉閻楀棝濡垫繝鍕煔闁惧繐婀遍惌瀣偡娴ｅ憡鍣洪懚鈺呮煕?MK-II闂佹寧绋戦鐤榁闂?20s闂?----------
        // AE2 婵犻潧鍊稿ú銊╁磻閿濆瑙﹂柛顐ゅ枎閻忓洭鎮楀☉娅亪宕戝澶婇棷?+ OC 103闁?4 + metaitem.03/4581闁? + 婵犻潧鍊稿ú銈囨閹剧粯鍋ㄥù锝呭暟閻斿懘鎳? +
        // metaitem.01/32045闁? + gt.blockmachines/2606闁?4闂佹寧绋掔粙鎺旂矈閿旇姤濯?1126闁?304 + 3闁?4000闂?
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
            new FluidStack[] { gtFluid(1115, 16 * 144), gtFluid(7, 24000) },
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.SINGULARITY),
                1),
            TierEU.RECIPE_UIV,
            120 * SECONDS,
            2); // MK-II
    }

    // ------------------------------------------------------------------
    // E-Calculator 闂備緡鍠撻崝瀣枎閵忋倖鐓€鐎广儱妫欓悡娆撴煥濞戞ɑ绶?14s 闂佹椿娼块崝宥夊春濞戙垺鐓傜€广儱鎳嶇划鐢告煥濞戞﹩鍟?14n
    // 闂佸憡甯炴繛鈧繛鍛叄瀹曘儲鎯旈妸銉︻仧闂佸搫鍊规刊浠嬵敊閺囩姵濯奸柍钘夋噽缁€鍡涙煥?
    // 婵犮垼鍩栭悧鏇㈡晬?濡ょ姷鍋為崕濂搞€侀幋鐐粴闁告鍋涜闂?缂備焦宕樺▔鏇㈠煝閸忕浠氶柛妤冨仜琚熼梺?闂佸搫鎳庨悥鐓幬熼埀顒€顪冮悷鏉胯埞濠殿喒鏅犲畷?闂佸憡鐟﹂崹鐢告儍閻樿绠戦柤濮愬€曞▓?ME
    // 闂備緡鍋呭畝鍛婄?6 闂佸搫顧€缁辨洜鍒掑鍥ㄥ晳闁告侗鍠楃花姘舵煥?
    // 闂佺绻堥崝鎴﹀磿?EV 1920 EU/t闂?0
    // 缂備礁顦扮敮鍥焵椤戣法鍔嶆俊顐犲€濋幃鑺ユ媴閸愵亞鍞撮梺闈涙閼冲爼宕滈妸鈺傜叆?576mb闂佹寧绋掔粙鎺旀暜閸洖绀嗛悹铏瑰劋閻濄倕鈽夐幘铏儓濞村吋鍔栭幏鍛煥閸愩劑鍙?3闁?闂佹寧绋戝涔猤isterCraftingRecipes闂佹寧绋戦ˇ顓㈠焵?
    // 闁哄鐗婇幐鎼佸矗閸℃稒鍋嬮柍杞扮劍閹倿鏌涜濞诧絿鎷归悢鍏煎仺闁靛绠戦悡鏇灻归悩鎻掝劉闁绘牕鐖奸獮瀣疀閹垮啯些闂佸憡甯掓晶搴♀枔?id闂佹寧绋戝姝﹏dItemStack
    // 闂佽桨鐒﹀姗€鎮鸿瀵剛鎲撮崟鍨暤闂佹寧绋戦ˇ浼村垂?AE2 definitions API闂?
    // ------------------------------------------------------------------
    private static void registerEcal() {
        FluidStack solder576 = Materials.SolderingAlloy.getMolten(576);
        // AE2
        // 闂佸搫鍊婚幊鎾愁焽閿熺姵鏅慨姗嗗墯閸娿倝鏌熺€涙ê濮囩€规洜鍠栧畷妤呭礃鐠恒劎顦〣lockCraftingUnit/0闂佹寧绋戦ˇ顓㈠焵椤戣法顦﹂柟顔奸叄楠炲骞囬鈧～锝夋⒑椤愩倕鏋庢繛鍛浮閺?1闂佹寧绋戦ˇ顓㈠焵椤戝灝绨籈
        // 闂佽浜介崕杈亹濞戙垹违濞戞搩鎽?缂備焦妫忛崹鎷屻亹濞戙垹违?
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

        // 婵犮垼鍩栭悧鏇㈡晬?闁?闂佹寧绋掗姝?blockframes/28 + metaitem.01/17028闁? + 缂備緡鍠楀畷妯尖偓姘▕閹姤鎷呴崘顏嗗敶闁? +
        // 闂佽桨鑳舵晶妤€鐣垫笟鈧幃鑺ユ媴閸愵亞鍞撮柤?闂佹寧绋戦悧鍡涘垂鎼淬垻鈻旈柕鍫濇婢规劙鏌?
        // + 婵炵鍋愭慨鐢稿礉閸涙潙闂柍銉珕 + 闂佸憡鐟﹂崹鐢告儍閻樿闂柍銉珕闂侀潧妫楅崑濠勬濡?14aa 闂佹椿娼块崝宥夊春濞戙垺鏅慨姗€纭搁崕鎴澝瑰鍐殭闂?闁?
        // 闂佹椿婢€缁插鎯屾ィ鍐ㄧ骇濠㈣泛鎽滈惌銈囩磼椤旇棄绀冮悗姘▕閹姤鎷呴崘顏嗗敶闂佽 鍋撻梺顐ｇ缁€瀣煛娴ｅ搫顣肩€规挷绶氶幃鑺ユ媴閸愵亞鍞撮梺闈涙閸嬪﹦妲?
        tryAddAssemblerNoCircuit(
            "ecal.casing",
            new Object[] { findItemStack("gregtech", "gt.blockframes", 28, 1),
                findItemStack("gregtech", "gt.metaitem.01", 17028, 6), new Object[] { "circuitElite", 4 },
                new Object[] { "circuitData", 8 }, findItemStack("gregtech", "gt.metaitem.01", 32693, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32683, 1) },
            new FluidStack[] { solder576 },
            new ItemStack(RegistryEcal.casing, 1),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // 濡ょ姷鍋為崕濂搞€侀幋鐐粴闁告鍋涜闂?闁?闂佹寧绋掗懝楣兯囩紒妯肩彾?+ AE2 闂佸憡鑹鹃悧濠囧垂濮樿泛绀夐柣妯煎劦閸嬫捇鎮㈤柨瀣綔闂?1闂? 缂備緡鍠楀畷妯尖偓姘▕閹姤鎷呴崘顏嗗敶闁? +
        // gt.blockmachines/2360闁?闂?
        tryAddAssemblerNoCircuit(
            "ecal.parallel_drive",
            new ItemStack[] { new ItemStack(RegistryEcal.casing, 1), aeAccel,
                GTOreDictUnificator.get("circuitElite", 2), findItemStack("gregtech", "gt.blockmachines", 2360, 4) },
            new FluidStack[] { solder576 },
            new ItemStack(RegistryEcal.parallelDrive, 1),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // 缂備焦宕樺▔鏇㈠煝閸忕浠氶柛妤冨仜琚熼梺?闁?闂佹寧绋掗懝楣兯囩紒妯肩彾?+ AE2 ME 闂佽浜介崕杈亹?+ 閻庤鎮堕崕鎶藉煝閸忕厧绶為柛鏇ㄥ幗閸婄偤鏌涢敐搴ｅ帒? + 婵炵鍋愭慨鐢稿礉閸涙潙闂柍銉珕
        // + 闂佸憡鐟﹂崹鐢告儍閻樿闂柍銉珕
        // + gt.blockmachines/2360闁?闂?
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

        // 闂佸搫鎳庨悥鐓幬熼埀顒€顪冮悷鏉胯埞濠殿喒鏅犲畷?闁?闂佹寧绋掗懝楣兯囩紒妯肩彾?+ AE2 闂佸憡鑹鹃悧濠囧垂濮樿泛纭€闁哄洨鍋涚敮妤呮煥?0闂? 闂佸憡鑹鹃悧濠囧垂濮樿泛绀夐柣妯煎劦閸嬫捇鎮㈤柨瀣綔闂?1闂?
        // 閻庤鎮堕崕鎶藉煝閸忕厧绶為柛鏇ㄥ幗閸婄偤鏌涢敐搴ｅ帒?
        // + 闂佸憡姊圭粙鎴濃攦閳ь剟鏌涘▎鎰仼闁轰焦鎹囧畷鎶藉灳椤ф粓鏌?
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

        // 闂佸憡鐟﹂崹鐢告儍閻樿绠戦柤濮愬€曞▓?闁?闂佹寧绋掗懝楣兯囩紒妯肩彾?+ 闂佸憡鐟﹂崹鐢告儍閻樿闂柍銉珕闁? + 婵炵鍋愭慨鐢稿礉閸涙潙闂柍銉珕闁? +
        // 闂佸憡姊圭粙鎴濃攦閳ь剟鏌涘▎鎰仼闁轰焦鎹囧畷鎶藉灳椤?+ gt.blockmachines/5153闁?
        // + /2365闁?闂?
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

        // ME 闂備緡鍋呭畝鍛婄?闁?闂佹寧绋掗懝楣兯囩紒妯肩彾?+ AE2 IO 缂備焦妫忛崹鎷屻亹?+ 婵炵鍋愭慨鐢稿礉閸涙潙闂柍銉珕 + 闂佸憡鐟﹂崹鐢告儍閻樿闂柍銉珕 +
        // 婵犮垹鐖㈤崘顏嗘啣闂佹椿婢€缁插鎯?+ gt.blockmachines/2360闁?闂?
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
    // 濡ょ姷鍋為崕濂搞€侀幋锕€鍐€缂佸娉曟俊?9 濠碘剝顨愮紞鍥╂濡?14z 闂佹椿娼块崝宥夊春濞戙垹鏄ラ柛婵嗗濞呮瑩姊洪弶璺ㄐｉ柡?+ 闂備緡鍋呯敮妤冩暜瑜版帗鏅悗?14ab
    // 婵烇絽娴傞崰妤咁敆濠婂牊鏅慨姗嗗亯閳峰牆鈽夐幙鍐х敖闁轰礁鎽滈幑鍕敍濞戞瑧鍑￠柡澶婄墛閹告悂宕ｉ崱娑欑劸濞寸姴顑傞崑鎾诲箛椤撴壕鍋撻崒鐐茬闁搞儮鏅犻悰鎾绘煥濞戞﹩妾х紒?
    // 闂佺硶鏅涢幖顐﹀闯?= AE2 闂佸憡鑹鹃悧濠囧垂濮樿泛绀夐柣妯煎劦閸嬫捇鎮㈤柨瀣綔闂佹寧绋戝﹢鎭杘ckCraftingUnit/1闂? 缂備緡鍠楀畷妯尖偓姘▕閹姤鎷呴崘顏嗗敶闁? +
    // 闂佽桨鑳舵晶妤€鐣垫笟鈧幃鑺ユ媴閸愵亞鍞撮柤? + 婵炵鍋愭慨鐢稿礉閸涙潙闂柍顓熸(32691)
    // + 闂佸憡鐟﹂崹鐢告儍閻樿闂柍顓熸(32681) 闂?闂佸搫绉堕…鍫㈢紦?闂佹寧绋戝﹢姝忛梺鎸庣☉椤р偓缂佸崬宕闊洦鏌ｉ埀?闂佹椿婢€缁插鎯屾ィ鍐ㄧ骇?+1 缂備胶瀚忓鍥╊槱闁?
    // 闂?Elite..Cosmic闂侀潧妫旈幀? 闂?
    // Data..Exotic 闂備緡鍠涘Λ鍕暦瀹€鍕櫖濠㈣泛鐗冮崑鎾存媴閻戞ɑ姣勯梺?+1 缂備胶瀚忔担鎻掍壕濞达綁顥撻悙濠囨煙閹殿喖鏋庢繛?闂佸憡鐟﹂崹鐢告儍閻樿闂?+1
    // 缂備胶瀚忓鍥╊槱婵犳鍠栭鍥╁垝閹炬壙鎺楀籍閸屾稒姣勯梺鍛娒鍕礊?1 缂備胶瀚忓鍥╊槴闂侀潧妫斿ù鍥╂椤撱垹绀?+1 缂?
    // 闂?闂?闂?6闂佹剚鍋呭濠氬焵椤掑﹨鍚傞柛?5536闂佹寧绋戦ˇ顓㈠焵椤掆偓娴? 闂佹椿婢€缁插鎯屾ィ鍐ㄧ骇闁秆勵殕閹崇姴霉?Data
    // 闁?+1闂佹寧绋掗寤皌a闂佹剚鍋呮晶鐢絠te闂佹剚鍋呮晶绌塻ter闂佹剚鍋呮竟寮唗imate闂?
    // Superconductor闂佹剚鍋呮晶纭乫inite闂佹剚鍋呮晶鐚闂佹剚鍋呮晶绨唗ical闂佹剚鍋呮晶鐣憃tic闂侀潧妫楅崐鐟拔涢妶鍚よ鎷呮搴Ｐ梺?0
    // 缂備礁顦扮敮鍥焵椤戣法鍔嶆俊顐犲€楃槐鎾诲冀椤愮喐鐓犻梺娲绘線缁插鎯屾ィ鍐ㄎ?
    // 闂備緡鍠撻崝瀣枎?damage闂佹寧绋戝﹢娣?32000 闁诲骸婀遍崑鐘绘儊婢舵劖鏅鑸电〒缁愭鏌涘▎鎰仴闁汇劎濞€瀹?MV..UEV =
    // 32681..32689闂佹寧绋戞總鏃傛閸洖绠涢柣鏃堟敱閻?32691..32699闂?
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
        // t114z闂佹寧绋戦悧蹇涘极閵堝绠ｉ柛鎴欏€楃粈鍡涙煥濞戞瑧鐓紒鍗炵埣楠炲洭鎮㈤柨瀣綔/闂佸憡鐟﹂崹鐢告儍閻樿闂柕濞垮€楅惌?MV 闁荤姍鍥ㄦ暠闁伙腹鈧剚娴?+1
        // 缂備胶瀚忓鍥╊槱1:MV闂?:HV闂?6:EV闂?4:IV闂?56:LuV闂?
        // 1024:ZPM闂?096:UV闂?6384:UHV闂?5536:UEV闂佹寧绋戦¨鈧紒杈ㄧ箖閹便劎鈧綆鍓涢惌鎺撴叏閿濆棙鈷掗柡浣规倐瀹曘垻鈧絺鏅濈粔?1 缂備胶瀚忔担鎻掍壕?
        int[] sensors = { 32691, 32692, 32693, 32694, 32695, 32696, 32697, 32698, 32699 };
        int[] emitters = { 32681, 32682, 32683, 32684, 32685, 32686, 32687, 32688, 32689 };
        for (int i = 0; i < cores.length; i++) {
            tryAddAssemblerNoCircuit(
                "ecal.parallel_core_" + cores[i],
                new Object[] { aeAccel, new Object[] { circuits[i], 2 }, new Object[] { circuits4[i], 4 },
                    findItemStack("gregtech", "gt.metaitem.01", sensors[i], 1),
                    findItemStack("gregtech", "gt.metaitem.01", emitters[i], 1) },
                new FluidStack[0],
                new ItemStack(ecoaegtnh.registry.RegistryEcal.PARALLEL_CORES.get(cores[i]), 1),
                euts[i],
                10 * SECONDS);
        }
    }

    // ------------------------------------------------------------------
    // 缂備焦宕樺▔鏇㈠煝婵傜鍐€缂佸娉曟俊?6 濠碘剝顨愮紞鍥╂濡?14t 闂佹椿娼块崝宥夊春濞戞碍鍏滄い鏃€顑欓崥鍥煛閸屾繍娼愭い銏犵Ч閺佸秶鈧?14u/t114v
    // 闂備緡鍋呯敮妤冩暜閸︻厽鍠嗛柛鏇ㄥ亜閻忕喖鏌ㄥ☉娆戭暡闁伙腹鈧剚娴栭柨婵嗘处閺嗏晠鏌?+2 缂備胶瀚忔担鎻掍壕濞达絿鍎ら弳鈺呮偣娓氼垰鐏℃繝?+2 缂備胶瀚忔担鎻掍壕?
    // 婵炵鍋愭慨鐢稿礉閸涙潙闂?闂佸憡鐟﹂崹鐢告儍閻樿闂?+2
    // 缂備胶瀚忛崒婊呮噸婵炴垶鎸稿ù鐑藉极閹间礁鍌ㄩ悗锝庝簼閸婅京绱掗悪娆忓暙閻栭亶姊洪弶璺ㄢ枌缂佽鲸宀搁弫宥咁潩椤撶喓褰梻渚囧亝濮樸劑宕垫惔锝囩煓閻庯綆鍋嗘竟瀣叓?1/4/16 闂佺硶鏅涢幖顐﹀闯?= AE2
    // 閻庤鎮堕崕鎶藉煝閸忕厧绶為柛鏇ㄥ幗閸婄偤鏌涢敐搴ｅ帒? +
    // 闂佽桨鑳舵晶妤€鐣垫笟鈧幃鑺ユ媴閸愵亞鍞撮柤?
    // 闂?闂佸搫绉堕…鍫㈢紦?闂佹寧绋戝﹢姝?缂備緡鍠楀畷妯尖偓?闂備緡鍠撻崝瀣枎椤㈢攼闂佹寧绋戦¨鈧紒?:HV/缂備緡鍠楀畷妯尖偓?闂備緡鍠撻崝瀣枎椤㈢攼闂?:IV/缂傚倷绀侀悧濠囨倵?闂備緡鍠撻崝瀣枎椤㈢柡闂?2闂佹寧绋戦ˇ顓㈠焵?
    // 16:ZPM/闂佸搫鍟版繛鈧俊?闂備緡鍠撻崝瀣枎椤ｂ偓PM闂?2闂佹寧绋戦惌渚€鎮滈敂鑺ヤ氦?EV/LuV闂佹寧绋戦ˇ顓㈠焵椤掆偓閸婄懓螞閵堝悿瑙勬媴妞嬪海歇闂?0
    // 缂備礁顦扮敮鍥焵椤戣法鍔嶆俊顐犲€楃槐鎾诲冀椤愮喐鐓犻梺娲绘線缁插鎯屾ィ鍐ㄎ?
    // 闁烩剝甯掗幊鎰板吹鎼达絿鐭欓悗锝庡亞婢瑰鐓?hyper_2/4/8闂?+4/4+8/8+16闂佹寧绋戦ˇ鎵偓鍨矒濮婂顢旈崟顓熸喖闂佸搫鎳庨悥鐓幬熼埀顒€顪冪€ｎ亜顒㈤柣妤佹倐閸ㄩ箖寮悰鈥充壕闁哄倽娉曢崬銊╂煕閹存繃顥滄い锔藉缁嬪鍩€椤掍胶鈻旀い蹇撳閸娿倝鏌熺€涙ê濮囧┑顔惧仱閺屽懘鎮㈤柨瀣綔
    // 闂佹寧绋戦悧鍡涙嚐閻旂儤鍋樼€光偓閸曘劍鏁甸梺鍛婃煟閸斿苯鈻嶉幒妤€瑙﹂悘鐐佃檸閸庛儱霉閻樹警鍟囩紒杈ㄥ哺婵″瓨鎷呴梹鎰煑缂備礁顑呴鍛此囬埡鍛仩闁糕剝顨嗛悵銈夋嚇?6闂佹寧绋戦悧鍡楊嚕婵犳艾纾圭€广儱绻掔粈鍡涙煏閸℃洝鍏岄柛鎾卞姂閺?576mb闂佹寧绋掔€规紜/婵犮垹鐖㈤崘顏嗘啣/闂備緡鍠撻崝瀣枎椤㈢帊
    // 闂?
    // LuV/闁烩剝甯掗幊搴敋?闂備緡鍠撻崝瀣枎椤㈢灝V闂?2闂佹寧绋戦ˇ顐﹀疮?UV/闂佹眹鍨婚崰鎾存櫠?闂備緡鍠撻崝瀣枎椤㈢睔闂?2闂佹寧绋戦ˇ顓㈠焵?
    // 闂備緡鍠撻崝瀣枎?damage闂佹寧绋戝﹢娣?32000 闁诲骸婀遍崑鐘绘儊婢舵劖鏅鑸电〒缁愭鏌涘▎鎰仴闁汇劎濞€瀹?HV/EV/IV/LuV/ZPM/UV = 32682/32683/32684/32685/
    // 32686/32687闂佹寧绋戞總鏃傛閸洖绠涢柣鏃堟敱閻濄倝鏌涘顒冨缂?= 32692/32693/32694/32695/32696/32697闂?
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

        // 闂佸搫鎷嬮崳锝夊焵椤掍焦鐨戦柛銈呮捣缁瑧鈧綆鍋嗘竟瀣叓?1/4/16闂佹寧绋戝﹢姝?IV/ZPM闂佹寧绋戦惉鑲╄姳閸ф鍤?缂傚倷绀侀悧濠囨倵?闂佸搫鍟版繛鈧俊鐐そ閹姤鎷呴崘顏嗗敶闁? +
        // 闂佽桨鑳舵晶妤€鐣垫笟鈧幃鑺ユ媴閸愵亞鍞撮柤? +
        // 婵炵鍋愭慨鐢稿礉閸涙潙闂?HV/IV/ZPM闂?2692/32694/32696闂? 闂佸憡鐟﹂崹鐢告儍閻樿闂?HV/IV/ZPM闂?2682/32684/32686闂佹寧绋戦¨鈧紒杈ㄥ哺婵?
        int[] threads = { 1, 4, 16 };
        String[] circuits = { "circuitElite", "circuitUltimate", "circuitInfinite" };
        long[] euts = { TierEU.RECIPE_HV, TierEU.RECIPE_IV, TierEU.RECIPE_ZPM };
        int[] sensors = { 32692, 32694, 32696 };
        int[] emitters = { 32682, 32684, 32686 };
        for (int i = 0; i < threads.length; i++) {
            tryAddAssemblerNoCircuit(
                "ecal.thread_core_" + threads[i],
                new Object[] { engProc8, new Object[] { circuits[i], 2 }, new Object[] { "circuitData", 4 },
                    findItemStack("gregtech", "gt.metaitem.01", sensors[i], 1),
                    findItemStack("gregtech", "gt.metaitem.01", emitters[i], 1) },
                new FluidStack[0],
                new ItemStack(
                    ecoaegtnh.registry.RegistryEcal.THREAD_CORES_BY_SUFFIX.get(String.valueOf(threads[i])),
                    1),
                euts[i],
                10 * SECONDS);
        }

        // 闁烩剝甯掗幊鎰板吹鎼达絿鐭欓悗锝庡亞婢瑰鐓?hyper_2/4/8闂佹寧绋戝﹢鏄?LuV/UV闂佹寧绋戦懟顖炲Φ閸モ晜鏆?闁烩剝甯掗幊搴敋?闂佹眹鍨婚崰鎾存櫠閸ф鍋ㄥù锝呭暟閻斿懘鎳? +
        // 闂佽桨鑳舵晶妤€鐣垫笟鈧幃鑺ユ媴閸愵亞鍞撮柤? +
        // 婵炵鍋愭慨鐢稿礉閸涙潙闂?EV/LuV/UV闂?2693/32695/32697闂? 闂佸憡鐟﹂崹鐢告儍閻樿闂?EV/LuV/UV闂?2683/32685/32687闂?
        // + 闂佸憡鑹鹃悧濠囧垂濮樿泛绀夐柣妯煎劦閸嬫捇鎮㈤柨瀣綔 + 閻庤鎮堕崕鎶藉煝閸忕厧绶為柛鏇ㄥ幗閸婄偤鏌涢敐搴ｅ帒?6 + 闂佺粯甯掗敃顏堝极?576mb闂佹寧绋戦ˇ顓㈠焵?
        String[] hyperSuffixes = { "hyper_2", "hyper_4", "hyper_8" };
        String[] hyperCircuits = { "circuitMaster", "circuitSuperconductor", "circuitBio" };
        long[] hyperEuts = { TierEU.RECIPE_EV, TierEU.RECIPE_LuV, TierEU.RECIPE_UV };
        int[] hyperSensors = { 32693, 32695, 32697 };
        int[] hyperEmitters = { 32683, 32685, 32687 };
        for (int i = 0; i < hyperSuffixes.length; i++) {
            tryAddAssemblerNoCircuit(
                "ecal.thread_core_" + hyperSuffixes[i],
                new Object[] { aeAccel, engProc16, new Object[] { hyperCircuits[i], 2 },
                    new Object[] { "circuitData", 4 }, findItemStack("gregtech", "gt.metaitem.01", hyperSensors[i], 1),
                    findItemStack("gregtech", "gt.metaitem.01", hyperEmitters[i], 1) },
                new FluidStack[] { solder576 },
                new ItemStack(ecoaegtnh.registry.RegistryEcal.THREAD_CORES_BY_SUFFIX.get(hyperSuffixes[i]), 1),
                hyperEuts[i],
                10 * SECONDS);
        }
    }

    // ------------------------------------------------------------------
    // 闂傚倸鍋嗘禍婊堟偤閵娾晛鐤鹃柛顐ｆ礃閳?256k/1024k/4096k闂佹寧绋戝?14s 闂佹椿娼块崝宥夊春濞戞鐔煎焺閸愨晝鍑￠梻渚囧亝鐢鏁ぐ鎺撴櫖濠㈣埖绋撶粣妤呮煕閳轰焦鎯堥柛?= AE2
    // 闂佸憡鑹鹃悧濠囧垂濮樿泛纭€闁哄洨鍋涚敮?+
    // ECO 闂佺粯銇涢弲娑㈠箹?256k 闁诲孩绋掗敋闁稿绉剁槐鎺楀礋椤忓拋鍋?+ 缂備緡鍠楀畷妯尖偓姘▕閹姤鎷呴崘顏嗗敶闁? + 缂備緡鍠楀畷妯尖偓姘▕閹姤鎷呴崘顏嗗敶闁?闂佹寧绋戦悧鍡涘垂鎼淬垻鈻旈柕鍫濇婢规劙鏌?
    // 闁荤姳绶ょ槐鏇㈡偩缂佹ê绶為柛鏇ㄥ幗閸婄偤鏌涢敐搴ｅ帒? 闂?
    // ecalculator_cell_256k闂佹寧绋戝﹢鏄栭梺鎸庣☉椤р偓缂佸崬宕闊洦鏌ｉ埀?ECO 缂傚倷绀佺€氼亜鈻?+1 缂備胶瀚忔担鎻掍壕濞达絿鍎ら弳鈺呮偣娓氼垰鐏℃繝?+1
    // 缂備胶瀚忔担鎻掍壕濞达絿鍎ら弳鈺呮煕?+1 缂?闂?
    // 闂佺粯甯掗敃顏堝极?576mb闂?0 缂備礁顦扮敮鍥焵椤戣法鍔嶆俊顐犲€楃槐鎾诲冀椤愮喐鐓犻梺娲绘線缁插鎯屾ィ鍐ㄎラ柛宀嬪缁€?6M
    // 闂佸憡鐟ラ敃锝嗙閹烘挾鈻斿┑鐘叉搐鐢娊鏌￠崒姘础闁轰降鍊濋獮瀣暋閻楀牆鈧數绱撴笟鍥у箺鐟滈鐒︾粭鐔封槈閺嶃倕浜鹃柛宀嬪缁€?
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

        // 16m..16384m闂佹寧绋戦悧鎰八夋繝鍥ㄧ厐鐎广儱娲ゅ▓鐘绘煥濞戞鐏遍柣鏍缁?= 闂佸憡鑹鹃惉鑲╂偖椤愶箑鍨傞悗锝傛櫇缁夐潧鈽夐幘顖氫壕濠碘剝顨愮徊鎯р枍閻樼粯鈷撶痪鏉款槺缁€濉員NH
        // 闂佽鍨奸崹顖滄閵夆晜鏅璺烘閸嬫捇寮悰鈥充壕闁哄啫鍊归弳蹇涙煙鐎电鍘撮柍褜鍓氶崝鏍ь焽椤栫偛绠甸柟閭︿簽鏉╂棃鏌?
        ItemStack calcProc64 = appeng.api.AEApi.instance()
            .definitions()
            .materials()
            .calcProcessor()
            .maybeStack(64)
            .orNull();
        ItemStack oc103 = findItemStack("OpenComputers", "item", 103, 1);

        // 16m闂佹寧绋戝﹢绯歁闂?0s闂?080闁?76闂佹寧绋戦¨鈧紒鍙樺嵆瀹曘儵宕奸悢椋庝海闂佸憡顨嗗ú鏍储?+ 16m 缂傚倷绀佺€氼亜鈻?+ 闁荤姳绶ょ槐鏇㈡偩缂佹ê绶為柛鏇ㄥ幗閸婄偤鏌涢敐搴ｅ帒?4 +
        // 缂傚倷绀侀悧濠囨倵椤掑嫭鍋ㄥù锝呭暟閻斿懘鎳?
        // + 闂佸憡姊圭粙鎴濃攦閳ь剟鏌涘▎鎰仼闁轰焦鎹囧畷鎶藉焼濡炬钒 + gt.blockmachines/1766闁?闂?
        tryAddAL(
            "ecal.cell_16m",
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.K_4096),
                1),
            new ItemStack[] { aeUnit, RegistryItems.itemComponent(ecoaegtnh.item.estorage.CellSize.M_16), calcProc64,
                GTOreDictUnificator.get("circuitUltimate", 4), findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.blockmachines", 1766, 4) },
            new FluidStack[] { gtFluid(1073, 4 * 144) },
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_16),
                1),
            TierEU.RECIPE_ZPM,
            TierEU.RECIPE_ZPM,
            60 * SECONDS);

        // 64m闂佹寧绋戝﹢绯歁闂?20s闂?080闁?152闂佹寧绋戦¨鈧紒杈ㄥ閹峰鏁嶉崟顓熸瘓婵犮垼娉涚€氼噣骞冩繝鍥ч棷妞?4 + 缂傚倷绀侀悧濠囨倵椤掑嫭鍋ㄥù锝呭暟閻斿懘鎳? +
        // 闂佸憡姊圭粙鎴濃攦閳ь剟鏌涘▎鎰仼闁轰焦鎹囧畷鎶藉焼濡炬钒闁?闂?
        // + gt.blockmachines/1766闁?闂?
        tryAddAL(
            "ecal.cell_64m",
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_16),
                1),
            new ItemStack[] { aeUnit, RegistryItems.itemComponent(ecoaegtnh.item.estorage.CellSize.M_64), calcProc64,
                GTOreDictUnificator.get("circuitUltimate", 6), findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.metaitem.01", 32675, 1),
                findItemStack("gregtech", "gt.blockmachines", 1766, 8) },
            new FluidStack[] { gtFluid(1073, 8 * 144) },
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_64),
                1),
            TierEU.RECIPE_ZPM,
            TierEU.RECIPE_ZPM,
            120 * SECONDS);

        // 256m闂佹寧绋戝﹢绂梺?0s闂?080闁?304闂佹寧绋戦¨鈧紒杈╂珎C 103闁? + 闁烩剝甯掗幊搴敋闁秵鍋ㄥù锝呭暟閻斿懘鎳? + 缂傚倷绀侀悧濠囨倵椤掑嫭鍋ㄥù锝呭暟閻斿懘鎳? +
        // 闂佸憡姊圭粙鎴濃攦閳ь剟鏌涘▎鎰仼闁轰焦鎹囧畷鎶藉煛閺堢嚖
        // + gt.blockmachines/1748闁?闂侀潧妫楅崑濠勬濞嗘挸绫嶉柣妯硅閸氣偓缂備胶濮甸〃鍛此囬埡鍛仩闁糕剝顨嗛悵銈夋煥?
        tryAddAL(
            "ecal.cell_256m",
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_64),
                1),
            new ItemStack[] { aeUnit, RegistryItems.itemComponent(ecoaegtnh.item.estorage.CellSize.M_256), oc103,
                GTOreDictUnificator.get("circuitSuperconductor", 2), GTOreDictUnificator.get("circuitUltimate", 4),
                findItemStack("gregtech", "gt.metaitem.01", 32676, 1),
                findItemStack("gregtech", "gt.blockmachines", 1748, 4) },
            new FluidStack[] { gtFluid(1073, 16 * 144) },
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_256),
                1),
            TierEU.RECIPE_UV,
            TierEU.RECIPE_UV,
            60 * SECONDS);

        // 1024m闂佹寧绋戝﹢绂梺?20s闂?080闁?592闂佹寧绋戦¨鈧紒杈╂珎C 103闁?闂?+ 闁烩剝甯掗幊搴敋闁秵鍋ㄥù锝呭暟閻斿懘鎳? +
        // 闂佸憡姊圭粙鎴濃攦閳ь剟鏌涘▎鎰仼闁轰焦鎹囧畷鎶藉煛閺堢嚖闁?闂?
        // + gt.blockmachines/1748闁?闂?
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
            new FluidStack[] { gtFluid(1073, 18 * 144) },
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_1024),
                1),
            TierEU.RECIPE_UV,
            TierEU.RECIPE_UV,
            120 * SECONDS);

        // 4096m闂佹寧绋戝﹢绂淰闂?0s闂?32闁?64 + 1080闁?728闂佹寧绋戦¨鈧紒杈╂珎C 103闁?+4闂?+ 闂佸憡姊圭粙鎴濃攦閳ь剟鏌涘▎鎰仼闁轰焦鎹囧畷鎶藉煛閹?
        // + gt.blockmachines/1808闁?闂侀潧妫楅崑濠勬濞嗘挻鍋ㄩ柕濠忕畱閻撴洟姊洪弶璺ㄐｉ柡宀€鍠栧顕€鎮╅崹顐ｆ瘎闁荤姳璀﹂崹鐢垫椤撱垹绀傞柕澶樺灣缁€?
        tryAddAL(
            "ecal.cell_4096m",
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_1024),
                1),
            new ItemStack[] { aeUnit, RegistryItems.itemComponent(ecoaegtnh.item.estorage.CellSize.M_4096),
                findItemStack("OpenComputers", "item", 103, 4), findItemStack("OpenComputers", "item", 103, 4),
                findItemStack("gregtech", "gt.metaitem.01", 32677, 1),
                findItemStack("gregtech", "gt.blockmachines", 1808, 4) },
            new FluidStack[] { gtFluid(430, 6 * 144), gtFluid(1073, 12 * 144) },
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_4096),
                1),
            TierEU.RECIPE_UHV,
            TierEU.RECIPE_UHV,
            60 * SECONDS);

        // 16384m闂佹寧绋戝﹢绂淰闂?20s闂?32闁?728 + 1080闁?304闂佹寧绋戦¨鈧紒杈╂珎C 103闁?2 + miscutils 32105
        // + 闂佸搫鍟版繛鈧俊鐐そ閹姤鎷呴崘顏嗗敶闁? + 闂佸憡姊圭粙鎴濃攦閳ь剟鏌涘▎鎰仼闁轰焦鎹囧畷鎶藉煛閹便儵鎳?闂?+ gt.blockmachines/1808闁?闂?
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
            new FluidStack[] { gtFluid(430, 12 * 144), gtFluid(1073, 16 * 144) },
            new ItemStack(
                ecoaegtnh.registry.RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_16384),
                1),
            TierEU.RECIPE_UHV,
            TierEU.RECIPE_UHV,
            120 * SECONDS);
    }

    // ------------------------------------------------------------------
    // 闂備緡鍠撻崝瀣枎?+ 闂佺鐭囬崘銊у幀闂侀潻缍嗛悡澶屾濡?14m 闂佹椿娼块崝宥夊春濞戙垺鐒婚柟閭﹀幗閽傚姊洪幓鎺斝㈠ù鐘崇洴閺佸秴顫濋鈧鍧楁⒑?EV 1920 EU/t闂?0
    // 缂備礁顦扮敮鍥焵椤戣法鍔嶆俊顐犲€楃槐鎾诲冀椤愮喐鐓犻梺娲绘線缁插鎯屾ィ鍐ㄎ?
    // 闂佺粯甯掗敃顏堝极?576mb闂佹寧绋掔粙鎺旀暜閸洖绀嗛悹铏瑰劋閻濄倝鏌￠埀顒勬焻濞戞粎顦伴悗瑙勬偠閸庢壆绱為弮鍫濈煑?3闁?
    // 闂佸憡鑹鹃悧濠囧垂濮樿埖鏅€光偓閸愮偓缍?registerCraftingRecipes闂佹寧绋戦ˇ顓㈠焵?
    // t114n闂佹寧绋戦悧蹇涘极閵堝绠ｉ柛鎴欏€楃粈鍡涙煥濞戞瑧顣叉俊顐亰閹?EV
    // 缂傚倷绀佺€氼垶藟婵犲洤瀚夐柣鎴灻禒姗€鏌涢幒鏇熺【婵炲懏甯￠弻濠傤吋閸℃鍘甸梺鎸庣☉濞兼姱torage.controller_l4闂佹寧绋戦ˇ顖炲礄閿熺姴绀嗛柣妯肩帛閻濈喖鏌?
    // ------------------------------------------------------------------
    private static void registerPartsAndControllers() {

        // R1闂佹寧绋掗懝楣冩偤閵娾晛纾奸柕濞炬櫆閳诲牓鏌涢幒鎿冩當妞わ妇绮粩?闁?闂佹寧绋戝﹢鏄?缂傚倷绀佺€氼垶藟婵犲洤瀚夐柣姘嚟缁€濉?14m
        // 闂佹椿娼块崝宥夊春濞戙垺鐓€鐎广儱妫欓悡娆撴煥濞戞﹩妾х紒鍙樺嵆閺岋箑鈽夊▎妯绘暠闂?闂備浇濮ょ粙鎺戭熆?+ 缂備緡鍠楀畷妯尖偓姘▕閹姤鎷呴崘顏嗗敶 +
        // AE2 婵炴垶鎸搁ˇ铏珶婵犲啫绶為柛鏇ㄥ幗閸婄偤鏌涢敐搴ｅ帨缂佽鲸鐟╅弻鍛村及韫囨洖绔?闁荤姳绶ょ槐鏇㈡偩?閻庤鎮堕崕鎶藉煝婵傜瑙?8闂佹寧绋戦¨鈧紒杈ㄧ箞閹偞绻濋崶銊︽
        // 576mb闂佹寧绋戦張顒€螞閵堝鍋ㄥù锝呭暟閻斿懘鏌?0 缂?@ EV闂?
        // 闂佸搫娲︾€笛冪暦閺屻儱绫嶇憸蹇撯枔?t98b 闂備焦婢樼粔鐢稿蓟閻斿吋鏅柛顐悼缂堝鏌?闁?闂?0 缂備礁顦扮敮鍥焵椤戣儻鍏岄柡浣告憸閹?1闂佹寧绋戦ˇ顓㈠焵?
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

        // R2闂佹寧绋掑畝鎼佸煘瀹ュ绀夐柕濞垮劜閻?闁?闂佹寧绋戝﹢鏄?缂傚倷绀佺€氼垶藟婵犲洤瀚夐柣姘嚟缁€濉?14m 闂佹椿娼块崝宥夊春濞戙垺鐓€鐎广儱妫欓悡娆撴煥濞戞﹩妾х紒杈╁瀵板嫰寮借缁?+ AE2 ME
        // 婵＄偟鎳撳畷顒佹叏閳哄懎闂?+ 闂佸憡姊圭粙鎴濃攦閳ь剟鏌涘▎鎰仼闁轰焦鎹囧畷?EV
        // + 婵犮垹鐖㈤崘顏嗘啣闂佹椿婢€缁插鎯?+ 缂備緡鍠楀畷妯尖偓姘▕閹姤鎷呴崘顏嗗敶 闁? + 婵炵鍋愭慨鐢稿礉閸涙潙闂?EV + 闂佸憡鐟﹂崹鐢告儍閻樿闂?EV +
        // 閻庤鎮堕崕鎶藉煝閸忕厧绶為柛鏇ㄥ幗閸婄偤鏌?闁?闂?
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

        // R3闂佹寧绋掑銊╁极閸濄儮鍋?A 闁?闂佹寧绋戝﹢鏄?缂傚倷绀佺€氼垶藟婵犲洤瀚夐柣姘嚟缁€濉?14m 闂佹椿娼块崝宥夊春濞戙垺鐓€鐎广儱妫欓悡娆撴煥濞戞﹩妾х紒杈╁瀵板嫰寮借缁?+
        // 闂佽桨鑳舵晶妤€鐣垫笟鈧幃鑺ユ媴閸愵亞鍞?闁? + 闂佽桨鑳舵晶妤€鐣垫笟鈧幃浠嬪垂椤愩倖寤洪梺鎸庣☉閻楀繘鎮㈤崱娆愬珰?
        // batteryData闂? GT 闂佸搫鐗嗛幖顐⑩枍閹烘妫橀柣鐔哄閸嬨儵鏌ㄥ☉妯荤稉t.blockmachines/2360闂佹寧绋戦ˉ?6闂?
        tryAddAssemblerNoCircuit(
            "estorage.capacitance_a",
            new ItemStack[] { new ItemStack(BlockEcoStorageCasing.INSTANCE, 1),
                GTOreDictUnificator.get("circuitData", 4), GTOreDictUnificator.get("batteryData", 1),
                gtMachineBlockStack(2360, 16) },
            new FluidStack[] { Materials.SolderingAlloy.getMolten(576) },
            new ItemStack(BlockEcoStorageCapacitance.INSTANCE, 1, BlockEcoStorageCapacitance.META_A),
            TierEU.RECIPE_EV,
            10 * SECONDS);

        // R4闂佹寧绋掗悺濉?闂佽鍓濆畷鐢稿吹?闁?闂佹寧绋戝﹢鏄?缂傚倷绀佺€氼垶藟婵犲洤瀚夐柣姘嚟缁€濉?14m 闂佹椿娼块崝宥夊春濞戙垺鐓€鐎广儱妫欓悡娆撴煥濞戞﹩妾х紒杈╁瀵板嫰寮借缁?+ AE2 IO
        // 缂備焦妫忛崹鎷屻亹?+ 婵炵鍋愭慨鐢稿礉閸涙潙闂?EV
        // + 闂佸憡鐟﹂崹鐢告儍閻樿闂?EV + 婵犮垹鐖㈤崘顏嗘啣闂佹椿婢€缁插鎯屾ィ鍐ㄎ?
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

        // 闂備緡鍋呭畝鎼佀夐幘璇茬煑?闁?闂佹寧绋戝﹢鏄?缂傚倷绀佺€氼垶藟婵犲洤瀚夐柣姘嚟缁€濉?14m 闂佹椿娼块崝宥夊春濞戙垺鐓€鐎广儱妫欓悡娆撴煥濞戞﹩妾х紒杈╁瀵板嫰寮借缁?+ GT
        // 闂佸搫鐗嗛幖顐⑩枍閹烘妫橀柣鐔哄閸嬨儵鏌ㄥ☉妯荤稉t.blockmachines/5153闂?
        // + metaitem.02闂佹寧绋戝娉?metaitem.02/21028闂? 闂佹眹鍨藉褎鎱ㄩ埡鍌樹粴妞ゆ帒鍟ぐ?EV闂侀潧妫楅崑濠勬濞嗘挸绫嶇憸鏃堝储閵堝妫樼痪顓炴噽缂堝鏌?闁?闂侀潧妫楅崑濠勬?
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
    // t114m闂佹寧绋掗懝鍓ф暜椤愶附鍋嬮柛顐ｇ箑缁憋絽霉閿濆棛鐭婄憸鐗堟尦閺屽﹤顓奸崱妯煎幍闂佹寧绋戦悧蹇涘极閵堝绠ｉ梺鍨儏缁茬懓銆掑顓犮€掓繛?3闁? 闂佸搫鐗嗛ˇ顖炴倵閻戣棄瑙﹂柛顐ゅ枎閻忓洭鏌ㄥ☉姗嗘Ф闁?
    // 闂佸憡鍔栭悷锔炬兜閸洖鏋佹繛鍡楁禋閸斿懘鏌ㄥ☉娆樺劄ameRegistry.addRecipe(new ShapedOreRecipe(闁哄鐗婇幐鎼佸吹? "闁?", "闁?", "闁?",
    // 闁诲孩绋掗〃鍫ヮ敄? 闂佺粯銇涢弲娑㈠箹?闂佹椿鍙€閸庡鎯佸┑鍫氬亾濞戞瑯娈樻い鎴滅劍缁?
    // ...))闂佺偨鍎查弻锟犲焵椤掍焦鐓ラ柣鈯欏懐绠旈柨鏇楀亾鐟滄澘寮剁粋鎺楀Ψ閿斿彨?ItemStack闂佹寧绋戦悧蹇曡姳鐠恒劍鍏滄い鏃傚帶閻栭亶姊洪弶璺ㄢ枌缂佽鲸宀搁獮瀣冀椤愩倕鏁搁柣鐘叉搐缁夋挳鎮鸿缁參鏁傞懗顖ｆ船
    // 闂佹寧绋戦悧鍡涖€?"circuitMaster"闂佹寧绋戦惌渚€宕靛鍫濈闁靛鍎遍悥閬嶆⒑閺夎法校濠⒀冪Ч瀵灚寰勭€ｎ偅娈橀梺鍛婂姇閻線顢氬鑸靛剹闁兼剚鍨冲Σ銈夋煟閵娿儱顏褍娼″畷顐ｇ瑹婵犲嫮顦梺?
    // ------------------------------------------------------------------
    private static void registerCraftingRecipes() {
        // E-Storage 闂佺鐭囬崘銊у幀闂侀潻缍嗛悡澶屾濡惧.blockmachines/32030 = MTE 32030闂佹寧绋戦鈺istryMTE.L4闂佹寧绋戦¨鈧紒?
        // C A C C = circuitMaster闂佹寧绋戦悧蹇涙偄閸℃瑦瀚氱€广儱绻掔粈鍡涙煏閸℃洜顏?= AE2 ME 闂佺鐭囬崘銊у幀闂侀潻绲婚崝濠囧焵?
        // F S F F = 闂佸憡姊圭粙鎴濃攦閳ь剟鏌涘▎鎰仼闁轰焦鎹囧畷?EV闂侀潧妫旂拋?= storage_array_casing闂?
        // C D C D = AE2 闂佸ジ鏀卞娆撴儊閹达附鍤勯柤鎭掑劤閻栭亶鏌涜箛鎾虫殨婵炲棎鍨芥俊?
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

        // E-Calculator 闂佺鐭囬崘銊у幀闂侀潻缍嗛悡澶屾濡惧.blockmachines/32033 = MTE 32033闂佹寧绋戦鈺istryEcal.ARRAY闂佹寧绋戦¨鈧紒?
        // C A C C = circuitMaster闂佹寧绋戦悧蹇涙偄閸℃瑦瀚氱€广儱绻掔粈鍡涙煏閸℃洜顏?= AE2 闂佸憡鑹鹃悧濠囧垂濮樿泛纭€闁哄洨鍋涚敮妤呮煏?
        // F S F F = 闂佸憡姊圭粙鎴濃攦閳ь剟鏌涘▎鎰仼闁轰焦鎹囧畷?EV闂侀潧妫旂拋?= ecalculator_casing闂?
        // C D C D = AE2 闂佸憡鑹鹃悧濠囧垂濮樿泛绀夐柣妯煎劦閸嬫捇鎮㈤柨瀣綔闂?
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
    // ItemStack 闁哄鐗嗛幊搴㈡叏椤忓牆妫橀悷娆忓閵嗗﹪鏌?
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
     * t114o闂佹寧绋掔喊宥団偓鍨矊閳绘棃濡搁妷銉︽澑闂?+ damage 婵炲濮寸€涒晝鈧灚姘ㄩ埀?mod 闂佸憡鐟﹂悧婊勬櫠閸ф浼犲〒姘功缁€鍑珽I
     * 婵犮垼娉涚粔鎾春濡ゅ懎鍐€闁绘挸娴风涵鈧?{@code modid:name/damage}闂?
     * 婵炴挻鑹鹃鍛淬€?dreamcraft:EngineeringProcessorItemEmeraldCore闂侀潧妫斿顧?fc:fluid_part/4闂?
     * thaumicenergistics:storage.component/5闂侀潧妫旂€圭幎egtech:gt.metaitem.01/17030闂佹寧绋戦ˇ顓㈠焵?
     * 闁哄鏅滈崝姗€銆侀幋锕€绫嶉柛顐ｆ磵閸嬫挸顫濆畷鍥╃暫 {@code GameRegistry.findItem} 闁荤喐鐟辩徊楣冩倵娴犲鏅繛鎴炵懃椤ユ繂鈽夐幘宕囆㈤柛鈺佹湰濞煎寮幐搴ｎ槬
     * null闂佹寧绋戦悧鎾诲储閵堝妫樼痪顓炴噽閸庢煡寮?+ 闁荤姭鍋撻柨鏇楀亾闁硅绻濋弫宥嗗緞閸艾浜?
     */
    private static ItemStack findItemStack(String modid, String name, int damage, int count) {
        net.minecraft.item.Item item = cpw.mods.fml.common.registry.GameRegistry.findItem(modid, name);
        return item == null ? null : new ItemStack(item, count, damage);
    }

    /**
     * t114m闂佹寧绋掔喊宥団偓鍨矒瀹曘垽鎮㈡總澶嬬稄闂佸搫鍊婚幊鎾愁焽?meta 闂?GT
     * 闂佸搫鐗嗛幖顐⑩枍閹烘妫橀柣鐔哄閸嬨儵鏌涢銏☆棞鐟滅増妫冮弫宥夊锤閹?blockmachines/2360闂?5153闂佺偨鍎查弻锟犲焵椤掍焦顥嗙紒缁樼墬缁傚秴鈽夊▎鎰畼闂佸憡鍔曢惌鍌炲Υ?
     * 闂佸搫顦崕鎾吹濠婂牆绀傞悹楦挎閺?GTNH mod闂佹寧绋戦張顒勬儉閸涙潙瀚?ItemList
     * 闁汇埄鍨遍幃鍌炲闯濞差亝鏅璺虹墐閸嬫捇宕掑┑鍫㈩吋闁荤偞绋戦張顒€顪冮崒鐐寸劵婵ê纾粻?{@code GameRegistry.findBlock}
     * 闁荤喐鐟辩徊楣冩倵娴犲鏅慨鍦竸 缂傚倸鍊搁幖顐︽嚈閹达箑绫嶉柟顖涘缁犳煡鏌?null闂佹寧绋戦悧蹇曞垝瀹ュ洦鍟戦柛娑卞枟缁ㄦ岸寮堕崼婵囧櫣濠殿噮浜顒傛喆閸曨厹鈧﹤霉閸忛棿浜㈤柣鎴檮濞?+
     * 闁荤姭鍋撻柨鏇楀亾闁硅绻濋弫宥嗗緞閸艾浜?
     */
    private static ItemStack gtMachineBlockStack(int meta, int count) {
        net.minecraft.block.Block block = cpw.mods.fml.common.registry.GameRegistry
            .findBlock("gregtech", "gt.blockmachines");
        return block == null ? null : new ItemStack(block, count, meta);
    }

    /**
     * t114p闂佹寧绋掔喊宥団偓鍨矊闇夊ù锝夘棑缁夊吋绻涙径鍫濆闁?ID
     * 闂?FluidStack闂佹寧绋戦悧蹇涘极閵堝绠ｉ柣鎴ｆ鐢娊鏌￠崒姘辩Ш闁革絿鍏橀幆?GregTech_FluidDisplay/1080闂?432闂?818
     * 闁诲繐绻楁ご绋课?FluidRegistry 闂佹眹鍔岀€氼厾绮婇敂鑺ュ?ID闂佹寧绋戦ˇ顓㈠焵椤戣法妫凞 闂佸搫鍟版慨鐢稿疾閵夆晛绫嶉柟顖涘缁犳煡鏌?null闂佹寧绋戦悧鎾诲储閵堝妫樼痪顓炴噽閸庢煡寮?+
     * 闁荤姭鍋撻柨鏇楀亾闁硅绻濋弫宥嗗緞閸艾浜?
     */
    private static FluidStack gtFluid(int id, int amount) {
        net.minecraftforge.fluids.Fluid f = net.minecraftforge.fluids.FluidRegistry.getFluid(id);
        return f == null ? null : new FluidStack(f, amount);
    }

    /**
     * 濠电偛顦崝宀勫船閼恒儳鈻旈柍褜鍓熷鍫曞灳閹颁焦些闂佸搫顦崐鑽ゅ垝瀹ュ洦鍟戦柛娑卞枟缁ㄦ岸姊洪弶璺ㄐｉ柡宀€鍠栭弫宥夊醇閵忕姭鎸呴梺?+
     * 濠电偟绻濆鎺旂礊鐎ｎ偅缍囬柟鎯у暱瀵娊鏌ㄥ☉妯绘拱婵☆偁鍊濋幆宥夊籍閸屾鏅欓梺鎸庣☉椤р偓缂佽鲸绻冪粋鎺旀媼瀹曞浂浼囬柡澶婄墛閹告悂宕?闁哄鐗婇幐鎼佸吹椤撶喓鈻?null 闂佸搫鍟崕濂告倻閿旇姤浜ら柛銉戝棗鐝?
     * 闂佺懓鐏氶幑渚€顢楅悢鐓庡窛濠电姴绻掔粈鍓? 缂備礁鑻幖顐﹀焵椤掑倸甯堕柣锝冨姂瀹曟鈥﹂幒鏃傤槴闂?
     */
    private static void tryAddAssembler(String name, Object[] inputs, FluidStack[] fluids, ItemStack output,
        int circuit, long eut, int duration) {
        addAssembler(name, inputs, fluids, output, circuit, eut, duration);
    }

    /**
     * t114m闂佹寧绋掗惌顔剧箔婢跺本鏆滈柨鏂垮⒔濡炵晫绱掔€ｎ亶鍎戦柡浣告憸閹瑰嫰顢涘鍕殸缂傚倷绀佺€氼垶藟婵犲洤瀚夋繛宸簻鐢娊鏌￠崒婊勵仧缂佽鲸鐟╅幃浠嬪Ω閿曗偓閻撴洟鏌″鍛┛妞?闂佹椿婢€缁插鎯屾ィ鍐ㄧ骇?闂?闂佹眹鍔岀€氫即宕㈤妶澶婃闁谎冩憸缁€鍡涙煏?
     * circuit(0) 濡ょ姷鍋炲﹢鍦箔婢跺瞼椹冲璺侯槺楠炲棝鏌嶉妷锔界厸闁逞屽墯閹芥翻RecipeBuilder.circuit()
     * 闂佽鍓濋濠勬娴煎瓨鐒绘慨妯虹－缁?GTUtility.getIntegratedCircuit
     * 婵犻潧顦遍崑妯肩博鐎涙鈻旀い蹇撶墛閼茬娀鏌熺€涙ê濮夐柡浣告憸閹瑰嫰顢涘鍐ｆ寘闂佸憡绻€濞村洨鎹㈠鈧畷銏ゆ倻閳规儳浜鹃柡鍌濄€€閸嬫捇寮埀顒佹櫠瀹ュ棛顩烽柕澶堝妿缁犵懓鈽夐幙鍐ч偗闁革絽鎲″顏堟寠婢跺﹤褰欓梺鍏兼綑濡梻绮径灞惧闁告劦浜濋弳蹇涙倵閻熸澘鏆旈柍?
     */
    private static void tryAddAssemblerNoCircuit(String name, Object[] inputs, FluidStack[] fluids, ItemStack output,
        long eut, int duration) {
        addAssembler(name, inputs, fluids, output, 0, eut, duration);
    }

    /**
     * t114x闂佹寧绋戦悧蹇涘极閵堝绠ｉ柛鎴欏€楃粈鍡涙煥濞戞瑥鍝洪柛妯稿€濆顒傛兜閸涱垳鐐曢梺绋跨箞閸庢煡寮鈧獮鎰媴閾忕懓鏁搁梺绋跨箳椤牓顢氶鐐闁抽敮鍋撻柍褜鍓氶弻锟犲焵椤掍焦鏁痭puts
     * 闂佺绻愰崯鎵矆瀹€鍕煑妞ゆ牗鐟ょ花浼存煛?ItemStack 闂?
     * OreDictItemStack("闂佹椿鍘奸悘婵嬪触閳ь剟鏌?, 闂佽桨妞掗崡鎶藉闯?闂侀潧妫楅崐濠氬礄?ItemStack
     * 闁?itemInputs(ItemStack...)闂佹寧绋戦悧鍡欐暜椤愩倖鍋橀悘鐐跺缁€瀣煥濞戞﹩妾х紒?
     * 闂?OreDictItemStack
     * 闂佸搫鍟崕濂告憘?itemInputs(Object...)闂佹寧绋戦悺妤?婵炴潙鍚嬬喊宥嗕繆閸濄儲瀚氶柕澹懎鏁搁梺绋跨箳椤牓鎯堝鍜佸殨闁逞屽墴楠炲骞囬鈧鍧楁⒑椤斿搫濡介柡浣哄仱瀹曟ê鐣濋埀顒佹櫠閸ф浼犲ù锝囧劋閻?
     * 闂佸搫娲ら妵姗€宕鍕厐鐎广儱妫欓悡娆撴煥濞戞ɑ宸I 闂佸搫瀚晶浠嬪Φ濮樿埖鍎楅柛顭戝枛閹礁顭块懜鍨殤濠⒀冩健瀹曨偅绗熸繝鍕槷婵炲濮风划顖炲礈娴兼潙绀岀憸鐗堝笒鐢娊鏌ㄥ☉姗嗘Ф闁?
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
        // 284（t6）：5.09.51.482 的默认 recipeEmitter 把 {矿典名,数量} 对展开的 alts 折叠成
        // 第一个成员（build() 而非 buildWithAlt()，见 移植报告 §0.2）——把"单矿典对"配方在
        // 注册期展开成每个矿典成员一条配方（每个成员作独立 ItemStack 输入），恢复 2.9.0 的
        // 矿典多物品行为（NEI 多行 + 任意成员可合成）。多矿典对配方返回 null 保持原写法。
        java.util.List<Object[]> variants = expandSingleOreDictPair(inputs);
        if (variants == null) {
            variants = java.util.Collections.singletonList(inputs);
        }
        int addedCount = 0;
        for (Object[] variantInputs : variants) {
            if (circuit > 0) {
                // 284：GT5U 5.09.51.482 没有 GTRecipeBuilder.circuit()（5.09.54 新增）——按 2.8.4
                // 时代标准写法（bartworks 等 2.8.4 模组同款，见 移植方案 §3）把编程电路作为普通
                // 输入追加；GT 组装机对集成电路输入做不消耗处理。
                Object[] withCircuit = java.util.Arrays.copyOf(variantInputs, variantInputs.length + 1);
                withCircuit[variantInputs.length] = gregtech.api.util.GTUtility.getIntegratedCircuit(circuit);
                variantInputs = withCircuit;
            }
            gregtech.api.util.GTRecipeBuilder builder = RA.stdBuilder()
                .fluidInputs(fluids)
                .itemOutputs(output)
                .eut(eut)
                .duration(duration);
            setItemInputs(builder, variantInputs);
            addedCount += builder.addTo(assemblerRecipes)
                .size();
        }
        if (addedCount == 0) {
            // t105：映射会静默丢弃非法配方（比如 validateInputCount）——记日志，避免"缺配方"
            // 被成功计数器掩盖。
            skippedRecipes++;
            LOG.warn("ECO recipe '{}' was NOT added to the assembler map (input validation or duplicate).", name);
        } else {
            registeredAssemblerRecipes += addedCount;
        }
    }

    /**
     * 284（t6）：把"恰好一个 {矿典名, 数量} 对元素"的配方展开成每个矿典成员一条配方的输入
     * 变体。5.09.51.482 的组装机配方映射默认 recipeEmitter 走 build()（非 buildWithAlt()），
     * 矿典 alts 会被折叠成第一个注册成员——NEI 只显示一个物品、且只有该成员能匹配（GT 输入
     * 总线统一化只认识 GT5U 自己的矿典名，circuitAdvanced 不在其中，不会归一化其他成员）。
     * 本方法在注册期遍历 OreDictionary.getOres(矿典名)，为每个成员生成一个把矿典对替换成
     * 该成员 ItemStack 的输入变体。返回 null 表示无需展开（无矿典对 / 多个矿典对 / 矿典未注册）。
     */
    private static java.util.List<Object[]> expandSingleOreDictPair(Object[] inputs) {
        int pairIndex = -1;
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] instanceof Object[]inner && inner.length == 2
                && inner[0] instanceof String
                && inner[1] instanceof Number) {
                if (pairIndex >= 0) return null; // 多个矿典对 → 不展开（保持原写法）
                pairIndex = i;
            }
        }
        if (pairIndex < 0) return null;
        Object[] inner = (Object[]) inputs[pairIndex];
        String oreName = (String) inner[0];
        int count = ((Number) inner[1]).intValue();
        java.util.List<net.minecraft.item.ItemStack> ores = net.minecraftforge.oredict.OreDictionary.getOres(oreName);
        if (ores.isEmpty()) return null; // 矿典未注册 → 保持原对（itemInputs 自行处理）
        java.util.List<Object[]> variants = new java.util.ArrayList<>();
        for (net.minecraft.item.ItemStack ore : ores) {
            Object[] variant = inputs.clone();
            variant[pairIndex] = gregtech.api.util.GTUtility.copyAmount(count, ore);
            variants.add(variant);
        }
        return variants;
    }

    /**
     * 濠电偛顦崝宀勫船閼恒儳鈻旈柍褜鍓熷鍫曟妞ゃ儱锕弻濠傤吋閸パ勭枃闂備焦婢樼粔鐢稿蓟閻斿吋鏅慨妯夸含閸╋紕绱掔仦钘夘暢濠⒀冩健瀹曨偅鎷呯喊妯轰壕濞达絿顭堝鍧楁⒑椤斿搫濡垮褍娼″畷顐ｆ媴閻ｅ瞼鐐曢梺绋跨箞閸庮垶鍩€椤戣法顦﹂柛娆忕箻閺屽牓濡搁敂淇卞亰婵炶揪绲鹃幑浣烘椤撱垹绀傞柕澶涚岛閸嬫挻鎷呴悾宀€鐐曢梺鍛婂灥閹碱偄顭囨惔銊︻棃?null
     * 闂佸搫鍟抽崺鏍极閻愬搫绀冪€光偓鐎ｎ剙鑰?
     * 闂佸憡鐔粻鎴﹀垂椤栫偛绠ラ柟鎹愵嚃閸斿懘鏌涘☉姗堝伐闁艰崵鍠撻幑鍕偓鍦Х缁犳牠鏌ㄥ☉妯荤窡7 缂備礁鑻幖顐﹀焵椤掑倸甯堕柣锝冨姂瀹曟鈥﹂幒鏃傤槴闂?
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
            // t105闂佹寧绋掓穱娲夋繝鍥ㄧ厐鐎广儱娲ゅ▓鐘绘煛閸曨厼孝闁汇劎濮撮锝夊传閸曨偆鍘?validateInputCount(4,16)
            // 婵炴垶鎸诲Λ鍐焽閵堝棭娓舵俊顖濐潐娑撱垻鈧鍠栭崯鏉戭焽椤忓嫧鏋栭柡鍥ュ灩鐢娊鏌￠崒姘辅闁逞屽墯閺岋繝鍩€椤掍焦顥嗘い鏂挎喘瀵噣濡烽妷褏顔嶉梺?
            // 闂備緡鍓欓悘婵嬪储?缂傚倸鍊瑰浠嬪储閵堝妫?闁荤偞鍑归崑鍛村垂濮樿泛绀夐柣鏃€妞块崥鈧梺杞扮劍濠㈡ê鈻嶉幒妤€绠抽柍鍝勫暞绾句即鏌?
            skippedRecipes++;
            LOG.warn("ECO recipe '{}' was NOT added to the AssemblyLine map (input validation or duplicate).", name);
        } else {
            registeredALRecipes += added.size();
        }
    }

    /**
     * t114p闂佹寧绋掔喊宥夊极閻愬搫绀冮悘鐐跺亹椤忛亶鏌℃径鍡忓亾閻愭垝鍠婄紓浣哥焷濞咃絿鍒掑鍥ㄥ晳闁告侗鍠楃花姘舵⒑閺夎法校闁哄瞼鍠栭弫宥夊锤閹兼樆hintergalactic
     * 闂?婵犮垽顤傛禍鐐哄煘閺嶎偆纾奸柛鏇ㄥ櫘濡懏淇婇妞诲亾閾忣偄浠?MK-I/II/III"闂佹寧绋戦ˇ顓㈠焵?
     * 婵炲濮鹃濠勭博鐎涙ɑ缍囬柟鎯у暱瀵?闁哄鐗婇幐鎼佸吹椤撶喓鈻?null 闂佸搫鍟崕濂告倻閿旇姤浜ら柛銉戝棗鐝梺鐟扮仛閹逛線顢楅悢鐓庡窛濠电姴绻掔粈鍓? 缂備礁鑻幖顐﹀焵椤掑倸甯堕柣锝冨姂瀹曟鈥﹂幒鏃傤槴闂?
     * t114r闂佹寧绋戦悧蹇涘极閵堝绠ｉ柛鎴欏€楃粈鍡涙煥濞戞瑯鍎_RecipeAdder.addSpaceAssemblerRecipe 婵炴垶鎸哥粔鐑筋敊閺囩姷纾?MODULE_TIER metadata
     * 闂佹寧绋戦悧鎾跺垝椤栨粍濯?1 = MK-I闂佹寧绋戞總鏃堝箲閵忊剝濯撮柡鍥╁斀娓氣偓瀹曠螖閸涱厼骞嬮梺鐓庡暱閳ь剛鍠撻悰鎾绘煥濞戞﹩妾х紒杈ㄧ箞瀵劑宕烽鐔告 GTRecipeBuilder
     * 闂佺儵鏅涢悺銊ф暜閺夋垟鏋栭柕濞垮劚閺傗偓濡ょ姷鍋犻崺鏍熸径濠庡殨?
     * MODULE_TIER闂佹寧绋戦悧濠傗攦閳ь剟鏌?validateRecipe 闂?tModuleTier >= 闂備焦婢樼粔鐢稿蓟?MODULE_TIER 闂佸憡甯囬崐鏍蓟閸ヮ剚鏅?
     * T1=1/T2=2/T3=3 闁诲孩绋掗〃澶嬩繆椤撱垺鍎樺ù锝呮憸閺変粙鎮归崶锔剧瓘缂佽京婀?14s闂佹寧绋掗悺濉?II 闂備焦婢樼粔鐢稿蓟?= 2闂佹寧绋戦ˇ顓㈠焵?
     */
    private static void tryAddSpaceAssembler(String name, Object[] inputs, FluidStack[] fluids, ItemStack output,
        long eut, int duration, int moduleTier) {
        for (Object input : inputs) {
            if (input == null) {
                skippedRecipes++;
                LOG.warn(
                    "Skipping ECO recipe '{}': an input material is not registered yet (null) 闂?inputs were: {}",
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
