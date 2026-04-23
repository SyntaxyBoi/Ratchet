package moth.ratchet.item;

import net.minecraft.item.Item;

public class RatchetDriverItem extends AbstractRatchetWeaponItem {
    private static final RatchetWeaponProfile PROFILE = new RatchetWeaponProfile(
            7.0D,
            -2.7D,
            3,
            20,
            0.6F,
            200,
            1.5F,
            0.0F,
            0.9F,
            2.75F,
            0.75F,
            0.0F,
            0.0F,
            0xD88A29,
            "tooltip.ratchet.ratchet_driver"
    );

    public RatchetDriverItem() {
        super(PROFILE, new Item.Settings());
    }

    @Override
    protected boolean usesGroundImpactParticles() {
        return true;
    }
}
