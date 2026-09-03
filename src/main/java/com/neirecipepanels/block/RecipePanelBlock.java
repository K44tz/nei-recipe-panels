package com.neirecipepanels.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.neirecipepanels.NeiRecipePanels;
import com.neirecipepanels.item.ItemRecipePanel;

/** A hung recipe panel. Invisible block; everything visual is drawn by {@link RecipePanelRenderer}. */
public class RecipePanelBlock extends BlockContainer {

    private static final float DEPTH = 1F / 16F;

    public RecipePanelBlock() {
        super(Material.wood);
        setBlockName("recipePanel");
        setHardness(0.3F);
        setStepSound(soundTypeWood);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new RecipePanelTile();
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        setBoundsForFace(world.getBlockMetadata(x, y, z));
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null;
    }

    private void setBoundsForFace(int meta) {
        switch (meta) {
            case 0:
                setBlockBounds(0F, 1F - DEPTH, 0F, 1F, 1F, 1F);
                break;
            case 1:
                setBlockBounds(0F, 0F, 0F, 1F, DEPTH, 1F);
                break;
            case 2:
                setBlockBounds(0F, 0F, 1F - DEPTH, 1F, 1F, 1F);
                break;
            case 3:
                setBlockBounds(0F, 0F, 0F, 1F, 1F, DEPTH);
                break;
            case 4:
                setBlockBounds(1F - DEPTH, 0F, 0F, 1F, 1F, 1F);
                break;
            case 5:
                setBlockBounds(0F, 0F, 0F, DEPTH, 1F, 1F);
                break;
            default:
                setBlockBounds(0F, 0F, 0F, 1F, 1F, 1F);
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof RecipePanelTile) {
                NBTTagCompound snapshot = ((RecipePanelTile) te).getSnapshot();
                if (snapshot != null) {
                    if (player.isSneaking()) {
                        NeiRecipePanels.proxy.openPanelConfig(x, y, z);
                    } else {
                        NeiRecipePanels.proxy.openPanelRecipe(snapshot, false);
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if (world.isRemote) {
            return;
        }
        ForgeDirection face = ForgeDirection.getOrientation(world.getBlockMetadata(x, y, z));
        if (!world.isSideSolid(x - face.offsetX, y - face.offsetY, z - face.offsetZ, face, true)) {
            world.setBlockToAir(x, y, z);
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote) {
            ItemStack panel = panelFor(world, x, y, z);
            if (panel != null) {
                EntityItem entity = new EntityItem(world, x + 0.5D, y + 0.5D, z + 0.5D, panel);
                entity.delayBeforeCanPickup = 10;
                world.spawnEntityInWorld(entity);
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z) {
        return panelFor(world, x, y, z);
    }

    @Override
    public Item getItemDropped(int meta, Random random, int fortune) {
        return null;
    }

    private static ItemStack panelFor(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof RecipePanelTile) {
            NBTTagCompound snapshot = ((RecipePanelTile) te).getSnapshot();
            NBTTagCompound settings = ((RecipePanelTile) te).getSettings();
            if (snapshot != null) {
                return ItemRecipePanel.withData(
                    (NBTTagCompound) snapshot.copy(),
                    settings == null ? null : (NBTTagCompound) settings.copy());
            }
        }
        return null;
    }
}
