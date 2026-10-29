package net.alpaka.addons.features.dungeon;

import net.alpaka.addons.features.dungeon.room.RoomFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/**
 * What a solver's tick gets: the game, the world, the player, the frame of the room the player is
 * in, and the time.
 *
 * @param frame {@code DungeonRooms.current()}: null outside a room with a frame, on its roof, and
 *              always for the device solvers (the F7/M7 boss uses absolute coordinates)
 * @param nowMs {@code Util.getMillis()}, a monotonic clock; compare it only with itself
 */
public record SolverContext(Minecraft mc, ClientLevel level, LocalPlayer player, RoomFrame frame, long nowMs) {}
