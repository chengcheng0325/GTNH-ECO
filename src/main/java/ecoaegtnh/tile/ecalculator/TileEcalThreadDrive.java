package ecoaegtnh.tile.ecalculator;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import ecoaegtnh.ecalculator.ECPUCluster;

/**
 * t35: E-Calculator thread-core drive tile (线程核心驱动器) — 1 slot holding an
 * {@code ItemEcalThreadCore}; the inserted core defines the thread slots AND this tile is the
 * vCPU container (the slot concepts of the old TileEcalThreadCore migrate here). Normal cores
 * (1/4/16) provide normal slots only; hyper cores provide normal+hyper slots (0+4 / 4+8 / 8+16,
 * t114s doubling); hyper assignments keep the +10% extra-storage path. No tier gate (全档自由).
 * In-flight tasks are NOT persisted (user decision); the inserted core item IS persisted.
 */
public class TileEcalThreadDrive extends TileEcalPart implements IInventory {

    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager
        .getLogger("ECOAEGTNH");

    /** Assigned clusters (in flight). */
    protected final List<CraftingCPUCluster> cpus = new ArrayList<>();

    private ItemStack coreStack = null;

    public ItemStack getCoreStack() {
        return coreStack;
    }

    /** Normal thread slots from the inserted core (0 when empty). */
    public int getThreads() {
        if (coreStack == null || !(coreStack.getItem() instanceof ecoaegtnh.item.ecalculator.ItemEcalThreadCore core)) {
            return 0;
        }
        return core.getThreads();
    }

    /** Hyper-thread slots from the inserted core (0 for normal cores / empty). */
    public int getHyperThreads() {
        if (coreStack == null || !(coreStack.getItem() instanceof ecoaegtnh.item.ecalculator.ItemEcalThreadCore core)) {
            return 0;
        }
        return core.getHyperThreads();
    }

    public List<CraftingCPUCluster> getCPUs() {
        return cpus;
    }

    public boolean canAddCPU() {
        return cpus.size() < getThreads();
    }

    public boolean canAddHyperThread() {
        return cpus.size() < getThreads() + getHyperThreads();
    }

    /**
     * Assigns a cluster to a free slot (normal first, hyper on demand; ported from the old thread
     * core). Hyper assignment charges +10% extra storage via
     * {@link ECPUCluster#ecoaegtnh$setUsedExtraStorage(long)} (onVirtualCPUSubmitJob side) and
     * records the slot kind on the cluster (t33).
     */
    public boolean addCPU(CraftingCPUCluster cluster, boolean hyperThread) {
        if (hyperThread) {
            if (!canAddHyperThread()) return false;
        } else {
            if (!canAddCPU()) return false;
        }
        cpus.add(cluster);
        ECPUCluster.from(cluster)
            .ecoaegtnh$setThreadCore(this);
        ECPUCluster.from(cluster)
            .ecoaegtnh$setHyperAssigned(hyperThread);
        return true;
    }

    /**
     * Sum of assigned clusters' REAL task bytes (plan §7.4 — {@code getAvailableBytes} basis).
     * M2 (audit): uses ecoaegtnh$getUsedStorage() (AE2U usedStorage) instead of availableStorage,
     * so the virtual hyper +10% reserve does not overdraw the shared pool.
     */
    public long getUsedStorage() {
        long used = 0;
        for (CraftingCPUCluster cpu : cpus) {
            used += ecoaegtnh.ecalculator.ECPUCluster.from(cpu)
                .ecoaegtnh$getUsedStorage();
        }
        return used;
    }

    /**
     * Called by the M1 destroy mixin when an owned cluster is destroyed (task done / canceled /
     * teardown). Removes it from this drive and notifies the controller (which posts the grid CPU
     * change event and replenishes the standby vCPU).
     */
    public void onCPUDestroyed(CraftingCPUCluster cluster) {
        if (!cpus.remove(cluster)) return;
        ECPUCluster.from(cluster)
            .ecoaegtnh$markDestroyed();
        // t33: the cluster leaves the drive — clear its slot-kind flag (also covers normal slots).
        ECPUCluster.from(cluster)
            .ecoaegtnh$setHyperAssigned(false);
        if (controller != null) {
            controller.onClusterChanged();
        }
        LOG.info("Ecal cluster destroyed: threadDrive=({},{},{}), remaining={}", xCoord, yCoord, zCoord, cpus.size());
    }

    /** Controller teardown (t5 F2): cancel + destroy every in-flight cluster. */
    public void onControllerDisassembled() {
        for (CraftingCPUCluster cpu : new ArrayList<>(cpus)) {
            try {
                cpu.cancel();
            } catch (Exception e) {
                LOG.warn("Ecal: cancel during disassembly failed for a cluster", e);
            }
            cpu.destroy(); // routes via M1 injectDestroy → onCPUDestroyed (removes + notifies)
        }
    }

    /** markDirty without a block re-render (M1 markDirty redirect target). */
    public void markNoUpdateSync() {
        markDirty();
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

    /** Notify the controller that the thread capacity / power changed. */
    protected void onCoreChanged() {
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        if (controller != null && controller.isStructureValid()) {
            controller.onThreadDriveChanged();
        }
    }

    @Override
    public String getInventoryName() {
        return "container.ecoaegtnh.ecal_thread_drive";
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
        if (!(stack.getItem() instanceof ecoaegtnh.item.ecalculator.ItemEcalThreadCore)) return false;
        // t9 double insurance (cell-drive pattern): formed controller → live gate; not formed →
        // allow, the formation re-check closes the pre-assembly bypass.
        // t60: the milestone branch gate became the upgrade-tree node gate (docs §2).
        if (controller != null && controller.isStructureValid()) {
            return controller.getUpgradeTree()
                .isActivated(
                    ((ecoaegtnh.item.ecalculator.ItemEcalThreadCore) stack.getItem()).getRequiredUpgradeNode());
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Shift-right-click interaction (t13 pattern, mirrors the cell drive)
    // ------------------------------------------------------------------

    public boolean interactWithCore(EntityPlayer player) {
        // Defensive: this must only run on the server (BlockEcalThreadDrive routes it there).
        if (worldObj == null || worldObj.isRemote) {
            return false;
        }
        ItemStack inHand = player.inventory.getCurrentItem();
        if (coreStack == null) {
            // Empty slot: insert one thread core from the hand.
            if (inHand == null || !(inHand.getItem() instanceof ecoaegtnh.item.ecalculator.ItemEcalThreadCore)) {
                return false;
            }
            // t62 pattern (cell drive): the structure must be formed before a core can be inserted.
            if (controller == null || !controller.isStructureValid()) {
                LOG.info(
                    "Ecal thread core insert rejected at ({},{},{}): structure not formed",
                    xCoord,
                    yCoord,
                    zCoord);
                player
                    .addChatMessage(new net.minecraft.util.ChatComponentTranslation("ecoaegtnh.ecal.core.not_formed"));
                return false;
            }
            // t60: upgrade-tree node gate (replaces the milestone branch gate).
            String required = ((ecoaegtnh.item.ecalculator.ItemEcalThreadCore) inHand.getItem())
                .getRequiredUpgradeNode();
            if (!controller.getUpgradeTree()
                .isActivated(required)) {
                LOG.info(
                    "Ecal thread core insert rejected at ({},{},{}): upgrade node {} not activated",
                    xCoord,
                    yCoord,
                    zCoord,
                    required);
                player.addChatMessage(
                    new net.minecraft.util.ChatComponentTranslation(
                        "ecoaegtnh.ecal.core.thread.node_not_supported",
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
                "Ecal thread core inserted at ({},{},{}): {} by {}",
                xCoord,
                yCoord,
                zCoord,
                core.getDisplayName(),
                player.getCommandSenderName());
            player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("ecoaegtnh.ecal.core.inserted"));
            return true;
        }
        // Occupied slot: extract the core into the (empty) hand. In-flight tasks keep running
        // (they stay in the cpus list; new assignments are gated by the (now empty) capacity).
        if (inHand != null) {
            return false;
        }
        ItemStack extracted = coreStack;
        player.inventory.setInventorySlotContents(player.inventory.currentItem, extracted);
        setInventorySlotContents(0, null);
        LOG.info(
            "Ecal thread core extracted at ({},{},{}): {} by {}",
            xCoord,
            yCoord,
            zCoord,
            extracted == null ? "(null)" : extracted.getDisplayName(),
            player.getCommandSenderName());
        player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("ecoaegtnh.ecal.core.removed"));
        return true;
    }

    // ------------------------------------------------------------------
    // NBT (in-flight tasks are NOT persisted — user decision; the core item IS)
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
