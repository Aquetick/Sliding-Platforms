package mc.slidingplatforms;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class ElevatorScreenItem extends BlockItem {

    public ElevatorScreenItem(Block block, Settings settings) {
        super(block, settings);
    }

    public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipType type, List<Text> tooltip) {
        tooltip.add(Text.translatable("tooltip.slidingplatforms.screen_hint").formatted(Formatting.GRAY));
    }
}
