package ecoaegtnh.ae2;

import static thaumicenergistics.common.storage.AEEssentiaStackType.ESSENTIA_STACK_TYPE;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.exceptions.AppEngException;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.data.IAEStackType;
import appeng.me.storage.CellInventory;
import thaumicenergistics.common.storage.AEEssentiaStack;

/**
 * Essentia cell inventory: {@link CellInventory} over {@link AEEssentiaStack}, copied from TE4's
 * {@code thaumicenergistics.common.inventory.EssentiaCellInventory} (1.7.60-GTNH, which fixed the
 * saveChanges cleanup upper bound to {@code getMaxTypes()} — "Legacy cells can store stacks
 * sparsely, so storedTypes is not a valid upper bound for cleanup").
 * <p>
 * {@link #getStackType()} returns the static TE4 constant, so the base {@code CellInventory}
 * constructor's virtual {@code getStackType().createPrimitiveList()} call is safe (no t8-style
 * construction NPE: nothing here depends on a field assigned after {@code super()}).
 */
public class EcoStorageCellInventoryEssentia extends CellInventory<AEEssentiaStack> {

    private static final String NBT_ESSENTIA_NUMBER_KEY = "Essentia#";

    public EcoStorageCellInventoryEssentia(ItemStack cell, ISaveProvider provider) throws AppEngException {
        super(cell, provider);
    }

    @Override
    protected AEEssentiaStack readStack(NBTTagCompound tag) {
        return AEEssentiaStack.loadStackFromNBT(tag);
    }

    @Override
    protected String getStackTypeTag() {
        return "et";
    }

    @Override
    protected String getStackCountTag() {
        return "ec";
    }

    @Override
    protected void saveChanges() {
        long count = 0;

        int index = 0;
        for (AEEssentiaStack stack : this.cellStacks) {
            count += stack.getStackSize();
            NBTTagCompound stackTag = new NBTTagCompound();
            stack.writeToNBT(stackTag);
            this.tagCompound.setTag(NBT_ESSENTIA_NUMBER_KEY + index, stackTag);

            index++;
        }

        // Legacy cells can store stacks sparsely, so storedTypes is not a valid upper bound for
        // cleanup (TE4 1.7.60 bugfix).
        for (int i = index; i < this.getMaxTypes(); i++) {
            this.tagCompound.removeTag(NBT_ESSENTIA_NUMBER_KEY + i);
        }

        this.storedTypes = (short) this.cellStacks.size();
        if (this.cellStacks.isEmpty()) {
            this.tagCompound.removeTag(getStackTypeTag());
        } else {
            this.tagCompound.setShort(getStackTypeTag(), this.storedTypes);
        }

        this.storedCount = count;
        if (count == 0) {
            this.tagCompound.removeTag(getStackCountTag());
        } else {
            this.tagCompound.setLong(getStackCountTag(), count);
        }

        if (this.container != null) {
            this.container.saveChanges(this);
        }
    }

    @Override
    protected void loadCellStacks() {
        long count = 0;
        for (int index = 0; index < this.getMaxTypes(); index++) {
            if (!this.tagCompound.hasKey(NBT_ESSENTIA_NUMBER_KEY + index)) continue;

            AEEssentiaStack aes = AEEssentiaStack
                .loadStackFromNBT(this.tagCompound.getCompoundTag(NBT_ESSENTIA_NUMBER_KEY + index));
            if (aes != null) {
                this.cellStacks.add(aes);
                count += aes.getStackSize();
            }
        }
        this.storedTypes = (short) this.cellStacks.size();
        this.storedCount = count;

        this.tagCompound.setShort(this.getStackTypeTag(), this.storedTypes);
        this.tagCompound.setLong(this.getStackCountTag(), count);
    }

    @Nonnull
    @Override
    public IAEStackType<?> getStackType() {
        return ESSENTIA_STACK_TYPE;
    }
}
