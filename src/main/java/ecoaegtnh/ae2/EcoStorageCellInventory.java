package ecoaegtnh.ae2;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.exceptions.AppEngException;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.me.storage.CellInventory;
import ecoaegtnh.item.estorage.ItemEcoStorageCell;

/**
 * Custom cell inventory for ECO E-Storage cells.
 * <p>
 * t68: the byte accounting is restored to the OLD ECO design (mirrors the 1.12.2 reference
 * EStorageCellInventory): used = types*bytesPerType + (storedCount+unused) / (typeWeight *
 * byteMultiplier), remaining = freeBytes * (typeWeight * byteMultiplier) + unused, unused =
 * (typeWeight * byteMultiplier) - storedCount % (typeWeight * byteMultiplier), where typeWeight =
 * stackType.getAmountPerByte() (8 for items). t49 had deleted these overrides to delegate to the
 * AE2U base (per-type cost totalBytes/128), which the user found too expensive. The declared type
 * count (item 315 / fluid 25) is exposed via {@link #getTotalItemTypes()} so the old "Types: N /
 * 315" display and up-to-315-type storage are restored (AE2U's base clamps its internal field to
 * 63; the base only enforces the limit through getTotalItemTypes, which we override).
 */
public class EcoStorageCellInventory<StackType extends IAEStack<StackType>> extends CellInventory<StackType> {

    public static final String ITEM_TYPE_TAG = "it";
    public static final String ITEM_COUNT_TAG = "ic";

    private final IAEStackType<StackType> stackType;
    private final ItemEcoStorageCell cellType;
    private final ItemStack cellStack;

    @SuppressWarnings("unchecked")
    public EcoStorageCellInventory(ItemStack o, ISaveProvider container) throws AppEngException {
        super(o, container);
        if (!(o.getItem() instanceof ItemEcoStorageCell)) {
            throw new AppEngException("ItemStack was used as a cell, but was not an ECO cell!");
        }
        this.cellType = (ItemEcoStorageCell) o.getItem();
        this.cellStack = o;
        this.stackType = (IAEStackType<StackType>) this.cellType.getStackType();
    }

    // ------------------------------------------------------------------
    // t68: old ECO byte math (typeWeight x byteMultiplier)
    // ------------------------------------------------------------------

    /** Old-design item/fluid weight: stack-type amount per byte (8 for item cells). */
    private long getTypeWeight() {
        return getStackType().getAmountPerByte();
    }

    @Override
    public long getUsedBytes() {
        final long weight = getTypeWeight() * cellType.getByteMultiplier();
        final long bytesForItemCount = (this.getStoredItemCount() + this.getUnusedItemCount()) / weight;
        return this.getStoredItemTypes() * this.getBytesPerType() + bytesForItemCount;
    }

    @Override
    public long getRemainingItemCount() {
        final long weight = getTypeWeight() * cellType.getByteMultiplier();
        final long freeBytes = this.getFreeBytes();
        // H1 (audit): freeBytes can be 2^59-1 (UNIVERSE) and weight is ~4096+ → freeBytes*weight
        // overflows long → negative → clamped to 0 → AE2U addItems permanently rejects the cell
        // ("always full"). Saturate the multiplication instead.
        final long remaining;
        if (weight <= 0) {
            remaining = freeBytes;
        } else if (freeBytes > (Long.MAX_VALUE - this.getUnusedItemCount()) / weight) {
            remaining = Long.MAX_VALUE;
        } else {
            remaining = freeBytes * weight + this.getUnusedItemCount();
        }
        return remaining > 0 ? remaining : 0;
    }

    @Override
    public int getUnusedItemCount() {
        final long weight = getTypeWeight() * cellType.getByteMultiplier();
        final int div = (int) (this.getStoredItemCount() % weight);
        if (div == 0) {
            return 0;
        }
        return (int) (weight - div);
    }

    /**
     * t68: expose the cell's declared type count (item 315 / fluid 25) instead of AE2U's clamped
     * 63 — restores the old "Types: N / 315" display and allows up to 315 item types. (Sticky-card
     * restrictionTypes is private in the base and never set for ECO cells, so it is not mirrored.)
     */
    @Override
    public long getTotalItemTypes() {
        return cellType.getTotalTypes(cellStack);
    }

    // ------------------------------------------------------------------
    // Stack-type plumbing (unchanged from t46/t49)
    // ------------------------------------------------------------------

    @Override
    protected StackType readStack(NBTTagCompound tag) {
        // t46: never read the raw `stackType` field here — the base CellInventory constructor
        // calls loadCellStacks() -> readStack() BEFORE our field is assigned (it is set after
        // super() returns), so cells with stored content NPE'd ("this.stackType is null") when
        // re-inserted. getStackType() carries the t8 construction-safe fallback to the base
        // implementation (resolves the type from the cell item, which the base sets before
        // loadCellStacks runs).
        return getStackType().loadStackFromNBT(tag);
    }

    @Override
    protected String getStackTypeTag() {
        return ITEM_TYPE_TAG;
    }

    @Override
    protected String getStackCountTag() {
        return ITEM_COUNT_TAG;
    }

    @Override
    @SuppressWarnings("unchecked")
    public IAEStackType<StackType> getStackType() {
        // Construction-safety (t8): the base CellInventory constructor calls this.getStackType()
        // (CellInventory.java:98) before our `stackType` field is assigned (it is set after
        // super() returns). Fall back to the base implementation (which resolves the type from
        // the cell item) while our field is still null, so no NPE occurs during construction.
        if (stackType != null) {
            return stackType;
        }
        return (IAEStackType<StackType>) super.getStackType();
    }
}
