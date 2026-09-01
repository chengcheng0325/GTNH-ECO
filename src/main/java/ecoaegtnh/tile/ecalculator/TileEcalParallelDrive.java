package ecoaegtnh.tile.ecalculator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * t35: E-Calculator parallel-core drive tile (并行核心驱动器): a single parallel-core slot. The
 * inserted {@code ItemEcalParallelCore} supplies parallelism to the host (Σ over all drives);
 * insert/extract via shift+right-click (mirrors the cell drive, t13 pattern). No tier gate —
 * any parallel core works on any controller (全档自由).
 */
public class TileEcalParallelDrive extends TileEcalPart implements IInventory {

    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager
        .getLogger("ECOAEGTNH");

    private ItemStack coreStack = null;

    public ItemStack getCoreStack() {
        return coreStack;
    }

    // ------------------------------------------------------------------
    // IInventory (1 core slot)
    // ------------------------------------------------------------------

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == 0 ? coreStack : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot != 0 || coreStack == null) return null;
        ItemStack result;
        if (coreStack.stackSize <= amount) {
            result = coreStack;
            coreStack = null;
        } else {
            result = coreStack.splitStack(amount);
            if (coreStack.stackSize == 0) coreStack = null;
        }
        onCoreChanged();
        return result;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        if (slot != 0 || coreStack == null) return null;
        ItemStack result = coreStack;
        coreStack = null;
        return result;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot != 0) return;
        if (stack != null && !isItemValidForSlot(slot, stack)) return;
        coreStack = stack;
        onCoreChanged();
    }

    /** Notify the controller that the parallelism total changed. */
    protected void onCoreChanged() {
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        if (controller != null && controller.isStructureValid()) {
            controller.onParallelDriveChanged();
        }
    }

    @Override
    public String getInventoryName() {
        return "container.ecoaegtnh.ecal_parallel_drive";
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
        if (!(stack.getItem() instanceof ecoaegtnh.item.ecalculator.ItemEcalParallelCore)) return false;
        // t9 double insurance (cell-drive pattern): formed controller → live gate; not formed →
        // allow, the formation re-check closes the pre-assembly bypass.
        // t60: the milestone branch gate became the upgrade-tree node gate (docs §2).
        if (controller != null && controller.isStructureValid()) {
            return controller.getUpgradeTree()
                .isActivated(
                    ((ecoaegtnh.item.ecalculator.ItemEcalParallelCore) stack.getItem()).getRequiredUpgradeNode());
        }
        return true;
    }

    /** Parallelism this drive supplies to the host total (inserted core's value, else 0). */
    public int getSuppliedParallelism() {
        if (coreStack == null
            || !(coreStack.getItem() instanceof ecoaegtnh.item.ecalculator.ItemEcalParallelCore core)) {
            return 0;
        }
        return core.getParallelism();
    }

    // ------------------------------------------------------------------
    // Shift-right-click interaction (t13 pattern, mirrors the cell drive):
    // sneak + held parallel core + empty slot -> insert; sneak + empty hand + occupied slot ->
    // extract. Extraction is always allowed, insertion requires a formed structure.
    // ------------------------------------------------------------------

    public boolean interactWithCore(EntityPlayer player) {
        // Defensive: this must only run on the server (BlockEcalParallelDrive routes it there).
        if (worldObj == null || worldObj.isRemote) {
            return false;
        }
        ItemStack inHand = player.inventory.getCurrentItem();
        if (coreStack == null) {
            // Empty slot: insert one parallel core from the hand.
            if (inHand == null || !(inHand.getItem() instanceof ecoaegtnh.item.ecalculator.ItemEcalParallelCore)) {
                return false;
            }
            // t62 pattern (cell drive): the structure must be formed before a core can be inserted.
            if (controller == null || !controller.isStructureValid()) {
                LOG.info(
                    "Ecal parallel core insert rejected at ({},{},{}): structure not formed",
                    xCoord,
                    yCoord,
                    zCoord);
                player
                    .addChatMessage(new net.minecraft.util.ChatComponentTranslation("ecoaegtnh.ecal.core.not_formed"));
                return false;
            }
            // t60: upgrade-tree node gate (replaces the milestone branch gate).
            String required = ((ecoaegtnh.item.ecalculator.ItemEcalParallelCore) inHand.getItem())
                .getRequiredUpgradeNode();
            if (!controller.getUpgradeTree()
                .isActivated(required)) {
                LOG.info(
                    "Ecal parallel core insert rejected at ({},{},{}): upgrade node {} not activated",
                    xCoord,
                    yCoord,
                    zCoord,
                    required);
                player.addChatMessage(
                    new net.minecraft.util.ChatComponentTranslation(
                        "ecoaegtnh.ecal.core.parallel.node_not_supported",
                        required));
                return false;
            }
            ItemStack core = inHand.copy();
            core.stackSize = 1;
            setInventorySlotContents(0, core);
            if (inHand.stackSize > 1) {
                inHand.stackSize--;
            } else {
                player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            }
            LOG.info(
                "Ecal parallel core inserted at ({},{},{}): {} by {}",
                xCoord,
                yCoord,
                zCoord,
                core.getDisplayName(),
                player.getCommandSenderName());
            player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("ecoaegtnh.ecal.core.inserted"));
            return true;
        }
        // Occupied slot: extract the core into the (empty) hand.
        if (inHand != null) {
            return false;
        }
        ItemStack extracted = coreStack;
        player.inventory.setInventorySlotContents(player.inventory.currentItem, extracted);
        setInventorySlotContents(0, null);
        LOG.info(
            "Ecal parallel core extracted at ({},{},{}): {} by {}",
            xCoord,
            yCoord,
            zCoord,
            extracted == null ? "(null)" : extracted.getDisplayName(),
            player.getCommandSenderName());
        player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("ecoaegtnh.ecal.core.removed"));
        return true;
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (coreStack != null) {
            NBTTagCompound core = new NBTTagCompound();
            coreStack.writeToNBT(core);
            tag.setTag("core", core);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        coreStack = tag.hasKey("core") ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag("core")) : null;
    }
}
