package ecoaegtnh.item.ecalculator;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import ecoaegtnh.block.ecalculator.BlockEcalCasing;
import ecoaegtnh.block.ecalculator.BlockEcalCellDrive;
import ecoaegtnh.block.ecalculator.BlockEcalMEChannel;
import ecoaegtnh.block.ecalculator.BlockEcalParallelDrive;
import ecoaegtnh.block.ecalculator.BlockEcalThreadDrive;
import ecoaegtnh.block.ecalculator.BlockEcalTransmitterBus;

/**
 * ItemBlock for the E-Calculator part blocks (t14, user feedback point 5: every functional block
 * needs a hover tooltip). {@code addInformation} appends one localized role+value line per block;
 * the dynamic values (parallelism / threads) come from the block instances, so the future C6/C9
 * registrations display their own numbers without code changes. Content mirrors the 1.12.2
 * reference lang (novaeng.ecalculator_*_proc.info.*, the parallel core supplies parallelism, the
 * thread core supplies thread slots / vCPU cap, the cell drive supplies flash-cell storage, the
 * ME channel is the protocol-compatibility layer) adapted to this project's implementation values
 * (plan §4.2 naming). Display names stay on the existing tile.* lang keys (ItemBlock's
 * unlocalized-name behavior is inherited unchanged).
 */
public class ItemBlockEcal extends ItemBlock {

    public ItemBlockEcal(Block block) {
        super(block);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        super.addInformation(stack, player, lines, advanced);
        Block block = Block.getBlockFromItem(stack.getItem());
        if (block instanceof BlockEcalParallelDrive) {
            // t35: the parallelism value now lives on the insertable core ITEMS, not the block.
            lines.add(StatCollector.translateToLocal("ecoaegtnh.tooltip.ecal.block.parallel_drive"));
        } else if (block instanceof BlockEcalThreadDrive) {
            lines.add(StatCollector.translateToLocal("ecoaegtnh.tooltip.ecal.block.thread_drive"));
        } else if (block instanceof BlockEcalCellDrive) {
            lines.add(StatCollector.translateToLocal("ecoaegtnh.tooltip.ecal.block.cell_drive"));
        } else if (block instanceof BlockEcalMEChannel) {
            lines.add(StatCollector.translateToLocal("ecoaegtnh.tooltip.ecal.block.me_channel"));
        } else if (block instanceof BlockEcalTransmitterBus) {
            lines.add(StatCollector.translateToLocal("ecoaegtnh.tooltip.ecal.block.transmitter_bus"));
        } else if (block instanceof BlockEcalCasing) {
            lines.add(StatCollector.translateToLocal("ecoaegtnh.tooltip.ecal.block.casing"));
        }
    }
}
