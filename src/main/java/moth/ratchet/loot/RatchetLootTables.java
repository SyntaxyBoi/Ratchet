package moth.ratchet.loot;

import moth.ratchet.item.ModItems;
import net.fabricmc.fabric.api.loot.v2.FabricLootTableBuilder;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.util.Identifier;

public final class RatchetLootTables {
    private static final Identifier END_CITY_TREASURE_ID = new Identifier("minecraft", "chests/end_city_treasure");
    private static final Identifier TRAIL_RUINS_COMMON_ID = new Identifier("minecraft", "archaeology/trail_ruins_common");
    private static final Identifier TRAIL_RUINS_RARE_ID = new Identifier("minecraft", "archaeology/trail_ruins_rare");

    private RatchetLootTables() {}

    public static void init() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (!source.isBuiltin()) {
                return;
            }

            if (TRAIL_RUINS_COMMON_ID.equals(id)) {
                addToExistingPools(tableBuilder, 2);
                return;
            }

            if (TRAIL_RUINS_RARE_ID.equals(id)) {
                addToExistingPools(tableBuilder, 1);
                return;
            }

            if (END_CITY_TREASURE_ID.equals(id)) {
                ((FabricLootTableBuilder) tableBuilder).pool(
                        LootPool.builder()
                                .rolls(ConstantLootNumberProvider.create(1))
                                .conditionally(RandomChanceLootCondition.builder(0.3F))
                                .with(ItemEntry.builder(ModItems.LATCHET_BARREL))
                                .build()
                );
            }
        });
    }

    private static void addToExistingPools(net.minecraft.loot.LootTable.Builder tableBuilder, int weight) {
        ((FabricLootTableBuilder) tableBuilder).modifyPools(poolBuilder ->
                poolBuilder.with(ItemEntry.builder(ModItems.LATCHET_BARREL).weight(weight))
        );
    }
}
