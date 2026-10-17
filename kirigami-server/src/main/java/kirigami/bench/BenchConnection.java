package kirigami.bench;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class BenchConnection extends Connection {

    private static final float DESIRED_CHUNKS_PER_TICK = 64.0F;

    private final Queue<Runnable> responses = new ConcurrentLinkedQueue<>();

    BenchConnection(final int index) {
        super(PacketFlow.SERVERBOUND);
        this.address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 40000 + index);
        this.channel = new EmbeddedChannel(new ClientSink());
    }

    @Override
    public synchronized void send(final net.minecraft.network.protocol.Packet<?> packet, final io.netty.channel.@org.jspecify.annotations.Nullable ChannelFutureListener listener, final boolean flush) {
        super.send(packet, listener, flush);
    }

    synchronized void runPendingTasks() {
        ((EmbeddedChannel) this.channel).runPendingTasks();
        for (int pending = this.responses.size(); pending > 0; pending--) {
            final Runnable response = this.responses.poll();
            if (response == null) {
                return;
            }
            response.run();
        }
    }

    private final class ClientSink extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
            try {
                this.respond(msg);
            } finally {
                ReferenceCountUtil.release(msg);
                promise.trySuccess();
            }
        }

        private void respond(final Object msg) {
            final ServerPlayer player = BenchConnection.this.getPlayer();
            if (player == null) {
                return;
            }
            final Queue<Runnable> responses = BenchConnection.this.responses;
            switch (msg) {
                case ClientboundKeepAlivePacket keepAlive ->
                    responses.add(() -> player.connection.handleKeepAlive(new ServerboundKeepAlivePacket(keepAlive.getId())));
                case ClientboundPlayerPositionPacket position -> responses.add(() -> player.connection.handleAcceptTeleportPacket(
                    new ServerboundAcceptTeleportationPacket(
                        position.id(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()
                    )
                ));
                case ClientboundChunkBatchFinishedPacket ignored ->
                    responses.add(() -> player.connection.handleChunkBatchReceived(new ServerboundChunkBatchReceivedPacket(DESIRED_CHUNKS_PER_TICK)));
                default -> {
                }
            }
        }
    }
}
