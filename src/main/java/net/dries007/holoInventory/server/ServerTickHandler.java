/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Dries K. Aka Dries007
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.dries007.holoInventory.server;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.network.Player;
import net.dries007.holoInventory.HoloInventory;
import net.dries007.holoInventory.util.Coord;
import net.dries007.holoInventory.util.FakeInventory;
import net.dries007.holoInventory.util.Helper;
import net.dries007.holoInventory.util.InventoryData;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.tileentity.TileEntityRecordPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.world.WorldServer;

import java.util.EnumSet;
import java.util.HashMap;

import static net.dries007.holoInventory.util.Data.MODID;

public class ServerTickHandler implements ITickHandler
{
    public HashMap<Integer, InventoryData> blockMap = new HashMap<Integer, InventoryData>();

    @Override
    public void tickStart(EnumSet<TickType> type, Object... tickData)
    {

    }

    @Override
    public void tickEnd(EnumSet<TickType> type, Object... tickData)
    {
        EntityPlayerMP player = (EntityPlayerMP) tickData[0];
        WorldServer world = player.getServerForPlayer();
        if (world == null) return;
        MovingObjectPosition mo = Helper.getPlayerLookingSpot(player);

        if (mo != null)
        {
            switch (mo.typeOfHit)
            {
                case TILE:
                    Coord coord = new Coord(world.provider.dimensionId, mo);
                    TileEntity te = world.getBlockTileEntity((int) coord.x, (int) coord.y, (int) coord.z);

                    if (Helper.weWant(te))
                    {
                        checkForChangedType(coord.hashCode(), te);
                        if (HoloInventory.getConfig().bannedTiles.contains(te.getClass().getCanonicalName()))
                        {
                            // BANNED THING
                            cleanup(coord, player);
                        }
                        else if (te instanceof IInventory)
                        {
                            doStuff(coord.hashCode(), player, (IInventory) te);
                        }
                        else if (te instanceof TileEntityEnderChest)
                        {
                            doStuff(coord.hashCode(), player, player.getInventoryEnderChest());
                        }
                        else if (te instanceof TileEntityRecordPlayer)
                        {
                            TileEntityRecordPlayer realTe = ((TileEntityRecordPlayer) te);
                            Block b = realTe.blockType != null ? realTe.blockType : Block.blocksList[world.getBlockId((int) coord.x, (int) coord.y, (int) coord.z)];
                            String name = b == null ? "" : b.getLocalizedName();
                            doStuff(coord.hashCode(), player, name, realTe.func_96097_a());
                        }
                        else
                        {
                            cleanup(coord, player);
                        }
                    }
                    break;
                case ENTITY:
                    if (Helper.weWant(mo.entityHit))
                    {
                        doStuff(mo.entityHit.entityId, player, (IInventory) mo.entityHit);
                    }
                    break;
            }
        }
    }

    private void checkForChangedType(int id, TileEntity te)
    {
        if (blockMap.containsKey(id))
        {
            InventoryData data = blockMap.get(id);
            if (!te.getClass().getCanonicalName().equals(data.getType())) blockMap.remove(id);
        }
    }

    private void cleanup(Coord coord, EntityPlayerMP player)
    {
        if (blockMap.containsKey(coord.hashCode()))
        {
            InventoryData inventoryData = blockMap.get(coord.hashCode());
            inventoryData.playerSet.remove(player);
            if (inventoryData.playerSet.isEmpty()) blockMap.remove(coord.hashCode());
            ServerPacketHandler.INSTANCE.sendRemove((Player) player, (byte) 0, coord.hashCode());
        }
    }

    private void doStuff(int id, EntityPlayerMP player, String name, ItemStack... itemStacks)
    {
        doStuff(id, player, new FakeInventory(name, itemStacks));
    }

    private void doStuff(int id, EntityPlayerMP player, IInventory inventory)
    {
        InventoryData inventoryData = blockMap.get(id);
        if (inventoryData == null) inventoryData = new InventoryData(inventory, id);
        else inventoryData.update(inventory);
        inventoryData.sendIfOld(player);
        blockMap.put(id, inventoryData);
    }

    @Override
    public EnumSet<TickType> ticks()
    {
        return EnumSet.of(TickType.PLAYER);
    }

    @Override
    public String getLabel()
    {
        return MODID + "_ServerTickHandler";
    }

    public void clear()
    {
        blockMap.clear();
    }
}