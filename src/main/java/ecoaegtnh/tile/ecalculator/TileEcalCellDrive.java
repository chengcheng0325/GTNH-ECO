package ecoaegtnh.tile.ecalculator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * E-Calculator cell drive tile: a single flash-cell slot. Phase A skeleton — the slot is
 * unfiltered and inert; phase B adds the ItemEcalCell filter, tier gating (C4/C6/C9) and the
 * controller byte-accounting hooks (recalculateTotalBytes / createVirtualCPU).
 */
public class TileEcalCellDrive extends TileEcalPart implements IInventory {

    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager
        .getLogger("ECOAEGTNH");

    private ItemStack cellStack = null;

    public ItemStack getCellStack() {
        return cellStack;
    }

    // ------------------------------------------------------------------
    // IInventory (1 cell slot)
    // ------------------------------------------------------------------

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == 0 ? cellStack : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot != 0 || cellStack == null) return null;
        ItemStack result;
        if (cellStack.stackSize <= amount) {
            result = cellStack;
            cellStack = null;
        } else {
            result = cellStack.splitStack(amount);
            if (cellStack.stackSize == 0) cellStack = null;
        }
        onCellChanged();
        return result;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        if (slot != 0 || cellStack == null) return null;
        ItemStack result = cellStack;
        cellStack = null;
        return result;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot != 0) return;
        // Phase B: gate with isItemValidForSlot (tier + ItemEcalCell filter) on every insertion
        // path. Phase A accepts anything so the slot mechanics can be tested.
        if (stack != null && !isItemValidForSlot(slot, stack)) return;
        cellStack = stack;
        onCellChanged();
    }

    /** Phase B hook: notify the controller that the byte pool changed. */
    protected void onCellChanged() {
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        if (controller != null && controller.isStructureValid()) {
            controller.onCellDriveChanged();
        }
    }

    @Override
    public String getInventoryName() {
        return "container.ecoaegtnh.ecal_cell_drive";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj != null && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64.0;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot != 0 || stack == null) return false;
        if (!(stack.getItem() instanceof ecoaegtnh.item.ecalculator.ItemEcalCell)) return false;
        // t9 double insurance (E-Storage t62 pattern): formed controller → live upgrade-node
        // gate; not formed → allow, the formation re-check closes the pre-assembly bypass.
        // t60: the milestone level gate became the upgrade-tree node gate (docs §2).
        if (controller != null && controller.isStructureValid()) {
            return controller.getUpgradeTree()
                .isActivated(((ecoaegtnh.item.ecalculator.ItemEcalCell) stack.getItem()).getRequiredUpgradeNode());
        }
        return true;
    }

    /**
     * Bytes this drive supplies to the byte pool: the installed cell's capacity when its
     * upgrade-tree node (t128: merged group node N1..N5) is activated, else 0 (t60; replaces the
     * milestone check).
     */
    public long getSuppliedBytes() {
        if (cellStack == null || controller == null
            || !(cellStack.getItem() instanceof ecoaegtnh.item.ecalculator.ItemEcalCell cell)) {
            return 0;
        }
        return controller.getUpgradeTree()
            .isActivated(cell.getRequiredUpgradeNode()) ? cell.getTotalBytes() : 0;
    }

    // ------------------------------------------------------------------
    // Shift-right-click interaction (t13, mirrors E-Storage drive bay, t16/t25):
    // sneak + held flash cell + empty slot -> insert one cell (formed array + tier gate);
    // sneak + empty hand + occupied slot -> extract the cell into the hand. Extraction is
    // always allowed, insertion requires a formed structure (E-Storage t62 behavior).
    // ------------------------------------------------------------------

    public boolean interactWithCell(EntityPlayer player) {
        // Defensive: this must only run on the server (BlockEcalCellDrive routes it there).
        if (worldObj == null || worldObj.isRemote) {
            return false;
        }
        ItemStack inHand = player.inventory.getCurrentItem();
        if (cellStack == null) {
            // Empty slot: insert one flash cell from the hand.
            if (inHand == null || !(inHand.getItem() instanceof ecoaegtnh.item.ecalculator.ItemEcalCell)) {
                return false;
            }
            // t62 pattern: the structure must be formed before a cell can be inserted.
            if (controller == null || !controller.isStructureValid()) {
                LOG.info("Ecal cell insert rejected at ({},{},{}): structure not formed", xCoord, yCoord, zCoord);
                player
                    .addChatMessage(new net.minecraft.util.ChatComponentTranslation("ecoaegtnh.ecal.cell.not_formed"));
                return false;
            }
            // t60: upgrade-tree node gate (replaces the milestone level gate).
            String required = ((ecoaegtnh.item.ecalculator.ItemEcalCell) inHand.getItem()).getRequiredUpgradeNode();
            if (!controller.getUpgradeTree()
                .isActivated(required)) {
                LOG.info(
                    "Ecal cell insert rejected at ({},{},{}): upgrade node {} not activated",
                    xCoord,
                    yCoord,
                    zCoord,
                    required);
                player.addChatMessage(
                    new net.minecraft.util.ChatComponentTranslation(
                        "ecoaegtnh.ecal.cell.node_not_supported",
                        required));
                return false;
            }
            ItemStack cell = inHand.copy();
            cell.stackSize = 1;
            setInventorySlotContents(0, cell);
            if (inHand.stackSize > 1) {
                inHand.stackSize--;
            } else {
                player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            }
            LOG.info(
                "Ecal cell inserted at ({},{},{}): {} by {}",
                xCoord,
                yCoord,
                zCoord,
                cell.getDisplayName(),
                player.getCommandSenderName());
            player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("ecoaegtnh.ecal.cell.inserted"));
            return true;
        }
        // Occupied slot: extract the cell into the (empty) hand.
        if (inHand != null) {
            return false;
        }
        ItemStack extracted = cellStack;
        player.inventory.setInventorySlotContents(player.inventory.currentItem, extracted);
        setInventorySlotContents(0, null);
        LOG.info(
            "Ecal cell extracted at ({},{},{}): {} by {}",
            xCoord,
            yCoord,
            zCoord,
            extracted == null ? "(null)" : extracted.getDisplayName(),
            player.getCommandSenderName());
        player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("ecoaegtnh.ecal.cell.removed"));
        return true;
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (cellStack != null) {
            NBTTagCompound cell = new NBTTagCompound();
            cellStack.writeToNBT(cell);
            tag.setTag("cell", cell);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        cellStack = tag.hasKey("cell") ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag("cell")) : null;
    }
}
