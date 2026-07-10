package me.samuelh2005.plasmid_auto_join.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import me.samuelh2005.plasmid_auto_join.PlasmidAutoJoin;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @Inject(method = "placeNewPlayer", at = @At("HEAD"))
    private void plasmidAutoJoin$redirectInitialLevel(
            final Connection connection, final ServerPlayer player, final CommonListenerCookie cookie, CallbackInfo ci
    ) {
        ServerLevel targetLevel = PlasmidAutoJoin.getTargetLevel();
        if (targetLevel == null) {
            // Configured game isn't open - fall through to whatever vanilla decided.
            return;
        }

        player.setServerLevel(targetLevel);
    }
}