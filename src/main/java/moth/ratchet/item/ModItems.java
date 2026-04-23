package moth.ratchet.item;

import moth.ratchet.RatchetMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
    public static final Item RATCHET_GEAR = register("ratchet_gear",
            new Item(new Item.Settings()));

    public static final Item LATCHET_BARREL = register("latchet_barrel",
            new Item(new Item.Settings()));

    public static final Item MECHANICAL_TUNER = register("mechanical_tuner",
            new Item(new Item.Settings().maxCount(1)));

    public static final Item RATCHET_INDEXER = register("ratchet_indexer",
            new RatchetIndexerItem());

    public static final Item RATCHET_DRIVER = register("ratchet_driver",
            new RatchetDriverItem());

    public static final Item OVERCRANK_RATCHET_DRIVER = register("overcrank_ratchet_driver",
            new OvercrankRatchetDriverItem());

    public static final Item HEAVY_RATCHET_DRIVER = register("heavy_ratchet_driver",
            new HeavyRatchetDriverItem());

    public static final Item LATCHET_REBOUNDER = register("latchet_rebounder",
            new LatchetRebounderItem());

    private ModItems() {}

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
            entries.add(RATCHET_GEAR);
            entries.add(LATCHET_BARREL);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(MECHANICAL_TUNER);
            entries.add(RATCHET_INDEXER);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(RATCHET_DRIVER);
            entries.add(OVERCRANK_RATCHET_DRIVER);
            entries.add(HEAVY_RATCHET_DRIVER);
            entries.add(LATCHET_REBOUNDER);
        });
    }

    private static Item register(String path, Item item) {
        return Registry.register(Registries.ITEM, RatchetMod.id(path), item);
    }
}
