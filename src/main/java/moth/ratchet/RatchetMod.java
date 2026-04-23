package moth.ratchet;

import moth.ratchet.enchant.ModEnchantments;
import moth.ratchet.entity.ModEntities;
import moth.ratchet.item.ModItems;
import moth.ratchet.loot.RatchetLootTables;
import moth.ratchet.network.RatchetNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RatchetMod implements ModInitializer {
    public static final String MOD_ID = "ratchet";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[Ratchet] Initializing mechanical armaments...");
        ModEnchantments.init();
        ModEntities.init();
        ModItems.init();
        RatchetLootTables.init();
        RatchetNetworking.init();
        LOGGER.info("[Ratchet] Initialization complete");
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
