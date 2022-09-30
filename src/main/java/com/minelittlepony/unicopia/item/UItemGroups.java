package com.minelittlepony.unicopia.item;

import java.util.function.Supplier;

import com.minelittlepony.unicopia.UTags;
import com.minelittlepony.unicopia.Unicopia;
import com.minelittlepony.unicopia.item.toxin.Toxic;
import com.minelittlepony.unicopia.item.toxin.ToxicHolder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.tag.TagKey;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.registry.Registry;

public interface UItemGroups {
    ItemGroup ALL_ITEMS = FabricItemGroupBuilder.create(Unicopia.id("items")).appendItems(list -> {
        list.add(Items.APPLE.getDefaultStack());

        DefaultedList<ItemStack> defs = DefaultedList.of();
        UItems.ITEMS.stream()
                .filter(item -> !(item instanceof ChameleonItem) || ((ChameleonItem)item).isFullyDisguised())
                .forEach(item -> item.appendStacks(ItemGroup.SEARCH, defs));
        list.addAll(defs);
    }).icon(UItems.EMPTY_JAR::getDefaultStack).build();
    ItemGroup HORSE_FEED = FabricItemGroupBuilder.create(Unicopia.id("horsefeed")).appendItems(list -> {
        list.addAll(Registry.ITEM.stream()
                .map(Item::getDefaultStack)
                .filter(stack -> ((ToxicHolder)stack.getItem()).getToxic(stack) != Toxic.EMPTY)
                .toList());
    }).icon(UItems.ZAP_APPLE::getDefaultStack).build();

    ItemGroup EARTH_PONY_ITEMS = forTag("earth_pony", UItems.APPLE_PIE::getDefaultStack);
    ItemGroup UNICORN_ITEMS = forTag("unicorn", UItems.SPELLBOOK::getDefaultStack);
    ItemGroup PEGASUS_ITEMS = forTag("pegasus", UItems.PEGASUS_FEATHER::getDefaultStack);
    ItemGroup BAT_PONY_ITEMS = forTag("bat_pony", UItems.SUNGLASSES::getDefaultStack);
    ItemGroup CHANGELING_ITEMS = forTag("changeling", Items.SCULK_SHRIEKER::getDefaultStack);

    static ItemGroup forTag(String name, Supplier<ItemStack> icon) {
        TagKey<Item> key = UTags.item("groups/" + name);
        return FabricItemGroupBuilder.create(Unicopia.id(name)).appendItems(list -> {
            list.addAll(Registry.ITEM.getEntryList(key)
                    .stream()
                    .flatMap(named -> named.stream())
                    .map(entry -> entry.value())
                    .map(Item::getDefaultStack)
                    .toList());
        }).icon(icon).build();
    }

    static void bootstrap() {}
}
