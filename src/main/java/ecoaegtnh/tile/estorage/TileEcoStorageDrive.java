package ecoaegtnh.tile.estorage;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import ecoaegtnh.ae2.EcoCellDriveWatcher;
import ecoaegtnh.ae2.EcoStorageCellHandler;

/**
 * E-Storage drive bay tile: holds a single storage cell and exposes its IMEInventoryHandler(s) to
 * the ME bus. Writes/extracts are tracked so the grid is notified (mirrors ECellDriveWatcher).
 */
public class TileEcoStorageDrive extends TileEcoStoragePart implements IInventory, ISaveProvider {

    private ItemStack cellStack = null;
    private IMEInventoryHandler<?> cachedHandlerItem = null;
    private IMEInventoryHandler<?> cachedHandlerFluid = null;
    /** Cache for any additional stack type (e.g. TE4 essentia); rebuilt on cell change. */
    private IMEInventoryHandler<?> cachedHandlerOther = null;
    private long lastWriteTick = 0;

    public ItemStack getCellStack() {
        return cellStack;
    }

    @SuppressWarnings("unchecked")
    public <T extends IAEStack<T>> IMEInventoryHandler<T> getHandler(IAEStackType<T> type) {
        if (cellStack == null) return null;
        IMEInventoryHandler<?> cached = type == ITEM_STACK_TYPE ? cachedHandlerItem
            : type == FLUID_STACK_TYPE ? cachedHandlerFluid : cachedHandlerOther;
        if (cached != null) return (IMEInventoryHandler<T>) cached;
        IMEInventoryHandler<T> handler = buildHandler(type);
        if (type == ITEM_STACK_TYPE) cachedHandlerItem = handler;
        else if (type == FLUID_STACK_TYPE) cachedHandlerFluid = handler;
        else cachedHandlerOther = handler;
        return handler;
    }

    @SuppressWarnings("unchecked")
    private <T extends IAEStack<T>> IMEInventoryHandler<T> buildHandler(IAEStackType<T> type) {
        IMEInventoryHandler<T> raw = EcoStorageCellHandler.INSTANCE.getCellInventory(cellStack, this, type);
        if (raw == null) return null;
        return new EcoCellDriveWatcher<>(raw, type, this);
    }

    public void onWriting() {
        lastWriteTick = worldObj.getTotalWorldTime();
    }

    public boolean isWriting() {
        return worldObj.getTotalWorldTime() - lastWriteTick < 40;
    }

    public void invalidateHandlers() {
        cachedHandlerItem = null;
        cachedHandlerFluid = null;
        cachedHandlerOther = null;
    }

    @Override
    public void saveChanges(IMEInventory cellInventory) {
        markDirty();
    }

    @Override
    public void markDirty() {
        if (worldObj != null) {
            worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
        }
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
        // t115 (perf): keep the handler cache event-driven (this path has no Container in practice,
        // but must not leave a stale handler behind).
        onCellChanged();
        return result;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot != 0) return;
        // Tier restriction applies to all insertion paths (player, hopper, pipes).
        if (stack != null && !isCellSupported(stack)) return;
        cellStack = stack;
        onCellChanged();
    }

    @Override
    public String getInventoryName() {
        return "ecoaegtnh.drive";
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
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot == 0 && EcoStorageCellHandler.INSTANCE.isCell(stack) && isCellSupported(stack);
    }

    /**
     * Minimum capacity band required by a cell (t76 band map, t122 naming): k-level
     * (256k/1024k/4096k) → band 0, 16M/64M/256M → band 1, 1024M/4096M/16384M → band 2.
     * Returns -1 for non-ECO items. Kept for compatibility (the milestone gate below
     * supersedes it).
     */
    public static int requiredTier(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ecoaegtnh.item.estorage.ItemEcoStorageCell cell)) {
            return -1;
        }
        return cell.getTierRequired();
    }

    /**
     * Static band gate (t62): true when the stack is an ECO cell allowed on the given controller
     * band. Kept for compatibility (the milestone gate below supersedes it).
     */
    public static boolean isSupportedByTier(ItemStack stack, int tier) {
        int required = requiredTier(stack);
        return required >= 0 && tier >= required;
    }

    /**
     * t112 (upgrade-tree gate): node id required by the cell on its storage line — ONE NODE PER
     * CELL SIZE (256k → I1/F1/E1 … 16384m → I9/F9/E9, 人造宇宙 → I10/F10/E10, see
     * {@link ecoaegtnh.item.estorage.ItemEcoStorageCell#getRequiredUpgradeNode()}). Returns null
     * for non-ECO items. Static so the multiblock formation check can gate cells without any
     * controller reference.
     */
    public static String requiredUpgradeNode(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ecoaegtnh.item.estorage.ItemEcoStorageCell cell)) {
            return null;
        }
        return cell.getRequiredUpgradeNode();
    }

    /**
     * t62: static upgrade-tree gate — true when the ECO cell is allowed on the given controller
     * (its storage-tree node is activated; item/fluid/essentia chains are selected by the cell's
     * family). No controller field is consulted, so it is safe to call during the multiblock
     * formation check (when the bay's controller reference may not be set up yet).
     */
    public static boolean isSupportedByUpgradeNode(ItemStack stack,
        ecoaegtnh.metatileentity.MTEEcoStorageArray controller) {
        String node = requiredUpgradeNode(stack);
        return node != null && controller.getUpgradeTree()
            .isActivated(node);
    }

    /**
     * t112 (upgrade-tree restriction, docs §3): each cell needs ITS OWN node on its chain —
     * 256k works from the free base node (I1/F1/E1), every other size needs the node of the same
     * index (1024k → I2/F2/E2 … 16384m → I9/F9/E9, 人造宇宙 → I10/F10/E10). When no controller is
     * assembled the check is deferred (returns true) — the multiblock formation check
     * re-validates statically (t62) so the pre-assembly bypass is closed.
     */
    public boolean isCellSupported(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ecoaegtnh.item.estorage.ItemEcoStorageCell)) {
            return false;
        }
        if (controller == null) {
            return true;
        }
        return isSupportedByUpgradeNode(stack, controller);
    }

    private void onCellChanged() {
        invalidateHandlers();
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            // A new/removed cell changes the cell array of the assembled multiblock: force the
            // AE grid to re-query the ME bus (GridStorageCache picks up/drops the handler and
            // posts the content diff for monitors).
            if (controller != null && controller.getMEBus() != null) {
                controller.getMEBus()
                    .forceCellArrayUpdate();
                // t69: recompute the idle-power usage (plan B+C: per-installed-cell charge) so a
                // cell insert/extract is reflected in the grid drain immediately.
                controller.recalculateEnergyUsage();
            }
        }
    }

    /**
     * Server-side sneak interaction (called from {@code BlockEcoStorageDrive.onBlockActivated}):
     * insert one held storage cell into the empty bay, or extract the bay's cell into the hand.
     * <p>
     * t62 (user preference): inserting requires a FORMED array — while no controller is assembled
     * (or the structure is not valid) the insert is rejected with a chat message, and once formed
     * the tier gate ({@link #isCellSupported}) rejects an oversized cell with a chat message
     * naming the required controller tier. Extraction (empty hand) is always allowed.
     */
    public boolean interactWithCell(net.minecraft.entity.player.EntityPlayer player) {
        ItemStack inHand = player.inventory.getCurrentItem();
        if (cellStack == null) {
            // Empty bay: insert one cell from the hand.
            if (inHand == null || !EcoStorageCellHandler.INSTANCE.isCell(inHand)) {
                return false;
            }
            // t62: the array must be formed before a cell can be inserted.
            if (controller == null || !controller.isStructureValid()) {
                player
                    .addChatMessage(new net.minecraft.util.ChatComponentTranslation("ecoaegtnh.drive.cell.not_formed"));
                return false;
            }
            if (!isCellSupported(inHand)) {
                // t62: upgrade-tree gate — the message names the required node.
                String nodeId = requiredUpgradeNode(inHand);
                String nodeName = nodeId == null ? "?"
                    : net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.upgrade.node." + nodeId + ".name");
                player.addChatMessage(
                    new net.minecraft.util.ChatComponentTranslation(
                        "ecoaegtnh.drive.cell.node_not_supported",
                        nodeName));
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
            player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("ecoaegtnh.drive.cell.inserted"));
            return true;
        }
        // Occupied bay: extract the cell into the (empty) hand.
        if (inHand != null) {
            return false;
        }
        player.inventory.setInventorySlotContents(player.inventory.currentItem, cellStack);
        setInventorySlotContents(0, null);
        player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("ecoaegtnh.drive.cell.removed"));
        return true;
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (cellStack != null) {
            NBTTagCompound itemTag = new NBTTagCompound();
            cellStack.writeToNBT(itemTag);
            tag.setTag("cell", itemTag);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        cellStack = tag.hasKey("cell") ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag("cell")) : null;
        invalidateHandlers();
    }
}
