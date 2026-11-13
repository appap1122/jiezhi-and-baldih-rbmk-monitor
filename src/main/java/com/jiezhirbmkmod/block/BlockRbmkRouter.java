package com.jiezhirbmkmod.block;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;

/**
 * Simple RBMK network router. It does not hold any tile entity: the terminal
 * 'list' command scans the screens/consoles around every router block.
 */
public class BlockRbmkRouter extends Block {

    public BlockRbmkRouter() {
        super(Material.IRON);
        this.setRegistryName("jiezhirbmkmod", "rbmk_router");
        this.setTranslationKey("rbmk_router");
        this.setHardness(2.0F);
        this.setResistance(12.0F);
        this.setSoundType(SoundType.METAL);
        this.setHarvestLevel("pickaxe", 0);
        this.setLightLevel(0.15F);
        this.setCreativeTab(ModBlocks.TAB_SCREEN);
    }
}
