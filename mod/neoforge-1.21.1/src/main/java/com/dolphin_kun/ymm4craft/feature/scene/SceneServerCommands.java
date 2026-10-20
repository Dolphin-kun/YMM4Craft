package com.dolphin_kun.ymm4craft.feature.scene;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class SceneServerCommands {
    private static final CommandSource FAILURE_LOGGER = new CommandSource() {
        @Override
        public void sendSystemMessage(Component message) {
            Ymm4Craft.LOGGER.warn("シーンのコマンドが失敗しました: {}", message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return false;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    };

    private SceneServerCommands() {
    }

    static CompletableFuture<Void> run(IntegratedServer server, UUID playerId, List<String> commands) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) player.setGameMode(GameType.SPECTATOR);

                CommandSourceStack source = server.createCommandSourceStack().withSource(FAILURE_LOGGER);
                for (String command : commands) {
                    server.getCommands().performPrefixedCommand(source, command);
                }
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }
}
