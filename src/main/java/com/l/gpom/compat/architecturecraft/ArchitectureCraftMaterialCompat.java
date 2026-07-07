package com.l.gpom.compat.architecturecraft;

import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.Locale;

public final class ArchitectureCraftMaterialCompat {
    private ArchitectureCraftMaterialCompat() {
    }

    public static boolean isAcceptableSawbenchMaterial(Block block) {
        if (block == null) {
            return false;
        }

        ResourceLocation registryName = MinecraftMappingCompat.blockRegistryName(block);
        String name = registryName == null ? "" : registryName.toString().toLowerCase(Locale.ROOT);
        Object glassValue = MinecraftMappingCompat.staticFieldValue(Blocks.class, "blocks.glass", "field_150359_w", "GLASS");
        Object glassPaneValue = MinecraftMappingCompat.staticFieldValue(Blocks.class, "blocks.glassPane", "field_150410_aZ", "GLASS_PANE");
        Block glass = glassValue instanceof Block ? (Block) glassValue : ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft", "glass"));
        Block glassPane = glassPaneValue instanceof Block ? (Block) glassPaneValue : ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft", "glass_pane"));
        if (block == glass
                || block == glassPane
                || block instanceof BlockSlab
                || name.startsWith("chisel:glass")) {
            return true;
        }

        IBlockState defaultState = MinecraftMappingCompat.blockDefaultState(block);
        if (MinecraftMappingCompat.blockStateIsOpaqueCube(defaultState) && !MinecraftMappingCompat.blockHasTileEntity(block)) {
            return true;
        }

        return !MinecraftMappingCompat.blockHasTileEntity(block) && GpomEarlyConfig.architectureCraftAdditionalSawbenchMaterialAllowlist().contains(name);
    }
}
