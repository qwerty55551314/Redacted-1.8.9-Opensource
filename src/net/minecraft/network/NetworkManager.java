package net.minecraft.network;

import redacted.client.Client;
import redacted.client.alt.AltManager;
import redacted.client.alt.ProxyConfig;
import redacted.client.event.impl.PacketEvent;
import redacted.client.util.debug.DebugUtil;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.crypto.SecretKey;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.login.server.S00PacketDisconnect;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.CryptManager;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ITickable;
import net.minecraft.util.LazyLoadBase;
import net.minecraft.util.MessageDeserializer;
import net.minecraft.util.MessageDeserializer2;
import net.minecraft.util.MessageSerializer;
import net.minecraft.util.MessageSerializer2;

public class NetworkManager extends SimpleChannelInboundHandler<Packet> implements com.replaymod.recording.mixin.NetworkManagerAccessor {
	private static final Logger logger = LogManager.getLogger();
	private static final int CONNECT_TIMEOUT_MILLIS = 10000;
	private static final int PROXY_TIMEOUT_MILLIS = 10000;
	private static final long CONNECT_WAIT_TIMEOUT_MILLIS = CONNECT_TIMEOUT_MILLIS + PROXY_TIMEOUT_MILLIS + 1000L;
	private static final long CHANNEL_READY_TIMEOUT_MILLIS = 5000L;
	public static final Marker logMarkerNetwork = MarkerManager.getMarker("NETWORK");
	public static final Marker logMarkerPackets = MarkerManager.getMarker("NETWORK_PACKETS", logMarkerNetwork);
	public static final AttributeKey<EnumConnectionState> attrKeyConnectionState = AttributeKey.<EnumConnectionState>valueOf("protocol");
	public static final LazyLoadBase<NioEventLoopGroup> CLIENT_NIO_EVENTLOOP = new LazyLoadBase<NioEventLoopGroup>() {
		@Override
		protected NioEventLoopGroup load() { return new NioEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Client IO #%d").setDaemon(true).build()); }
	};
	public static final LazyLoadBase<EpollEventLoopGroup> CLIENT_EPOLL_EVENTLOOP = new LazyLoadBase<EpollEventLoopGroup>() {
		@Override
		protected EpollEventLoopGroup load() { return new EpollEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Epoll Client IO #%d").setDaemon(true).build()); }
	};
	public static final LazyLoadBase<LocalEventLoopGroup> CLIENT_LOCAL_EVENTLOOP = new LazyLoadBase<LocalEventLoopGroup>() {
		@Override
		protected LocalEventLoopGroup load() { return new LocalEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Local Client IO #%d").setDaemon(true).build()); }
	};
	private final EnumPacketDirection direction;
	private final Queue<InboundHandlerTuplePacketListener> outboundPacketsQueue = Queues.<InboundHandlerTuplePacketListener>newConcurrentLinkedQueue();
	private final Queue<InboundHandlerTuplePacketListener> inboundPacketsQueue = Queues.<InboundHandlerTuplePacketListener>newConcurrentLinkedQueue();
	private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
	/** The active channel */
	private volatile Channel channel;
	/** The address of the remote party */
	private SocketAddress socketAddress;
	/** The INetHandler instance responsible for processing received packets */
	private INetHandler packetListener;
	/** A String indicating why the network has shutdown. */
	private IChatComponent terminationReason;
	private volatile Throwable connectionFailure;
	private boolean isEncrypted;
	private boolean disconnected;

	public NetworkManager(final EnumPacketDirection packetDirection)
	{ this.direction = packetDirection; }

	@Override
	public Channel getChannel() { return this.channel; }

	public void channelActive(final ChannelHandlerContext p_channelActive_1_) throws Exception {
		super.channelActive(p_channelActive_1_);
		this.channel = p_channelActive_1_.channel();
		this.socketAddress = this.channel.remoteAddress();
		try {
			this.setConnectionState(EnumConnectionState.HANDSHAKING);
		} catch (final Throwable throwable) {
			logger.fatal(throwable);
		}
	}

	/**
	 * Sets the new connection state and registers which packets this channel may send and receive
	 */
	public void setConnectionState(final EnumConnectionState newState) {
		this.channel.attr(attrKeyConnectionState).set(newState);
		// FIXME: Logging in fix. May never work as intended.
		this.channel.read();
		this.channel.config().setAutoRead(true);
		logger.debug("Enabled auto read");
	}

	@Override
	public void channelInactive(final ChannelHandlerContext p_channelInactive_1_) throws Exception {
		if (this.terminationReason == null) {
			this.closeChannel(new ChatComponentTranslation("disconnect.endOfStream"));
		}
	}

	@Override
	public void exceptionCaught(final ChannelHandlerContext p_exceptionCaught_1_, final Throwable p_exceptionCaught_2_) throws Exception {
		ChatComponentTranslation chatcomponenttranslation;
		if (p_exceptionCaught_2_ instanceof TimeoutException) chatcomponenttranslation = new ChatComponentTranslation("disconnect.timeout");
		else chatcomponenttranslation = new ChatComponentTranslation("disconnect.genericReason", "Internal Exception: " + p_exceptionCaught_2_);
		this.closeChannel(chatcomponenttranslation);
	}

	@Override
	protected void channelRead0(final ChannelHandlerContext p_channelRead0_1_, final Packet p_channelRead0_2_) throws Exception {
		if (this.channel.isOpen()) try {
			if (this.direction == EnumPacketDirection.CLIENTBOUND) {
				final PacketEvent packetEvent = Client.getInstance().getEventManager().call(new PacketEvent(p_channelRead0_2_, PacketEvent.Direction.INBOUND));
				DebugUtil.log("IN ", p_channelRead0_2_);
				if (packetEvent.isCancelled() && !this.isDisconnectPacket(p_channelRead0_2_)) return;
			}
			p_channelRead0_2_.processPacket(this.packetListener);
		} catch (final ThreadQuickExitException var4) {}
	}

	private boolean isDisconnectPacket(final Packet packet) {
		return packet instanceof S00PacketDisconnect || packet instanceof S40PacketDisconnect;
	}

	/**
	 * Sets the NetHandler for this NetworkManager, no checks are made if this handler is suitable for
	 * the particular connection state (protocol)
	 */
	public void setNetHandler(final INetHandler handler) {
		Validate.notNull(handler, "packetListener");
		logger.debug("Set listener of {} to {}", this, handler);
		this.packetListener = handler;
	}

	public void sendPacket(final Packet packetIn) {
		if (this.isChannelOpen()) {
			this.flushOutboundQueue();
			this.dispatchPacket(packetIn, (GenericFutureListener<? extends Future<? super Void>>[]) null);
		} else {
			this.readWriteLock.writeLock().lock();
			try {
				this.outboundPacketsQueue.add(new InboundHandlerTuplePacketListener(packetIn, (GenericFutureListener[]) null));
			} finally {
				this.readWriteLock.writeLock().unlock();
			}
		}
	}

	public void sendPacket(final Packet packetIn, final GenericFutureListener<? extends Future<? super Void>> listener, final GenericFutureListener<? extends Future<? super Void>>... listeners) {
		if (this.isChannelOpen()) {
			this.flushOutboundQueue();
			this.dispatchPacket(packetIn, ArrayUtils.add(listeners, 0, listener));
		} else {
			this.readWriteLock.writeLock().lock();
			try {
				this.outboundPacketsQueue.add(new InboundHandlerTuplePacketListener(packetIn, ArrayUtils.add(listeners, 0, listener)));
			} finally {
				this.readWriteLock.writeLock().unlock();
			}
		}
	}

	/**
	 * Sends a packet silently without calling event handlers
	 */
	public void sendSilentPacket(final Packet packetIn) {
		if (this.isChannelOpen()) {
			this.flushOutboundQueue();
			this.dispatchPacket(packetIn, (GenericFutureListener<? extends Future<? super Void>>[]) null);
		} else {
			this.readWriteLock.writeLock().lock();
			try {
				this.outboundPacketsQueue.add(new InboundHandlerTuplePacketListener(packetIn, (GenericFutureListener[]) null));
			} finally {
				this.readWriteLock.writeLock().unlock();
			}
		}
	}

	/**
	 * Receives a packet
	 */
	public void receivePacket(final Packet packetIn) {
		if (this.isChannelOpen()) {
			this.dispatchPacket(packetIn, (GenericFutureListener<? extends Future<? super Void>>[]) null);
		} else {
			this.readWriteLock.writeLock().lock();
			try {
				this.inboundPacketsQueue.add(new InboundHandlerTuplePacketListener(packetIn, (GenericFutureListener[]) null));
			} finally {
				this.readWriteLock.writeLock().unlock();
			}
		}
	}

	/**
	 * Receives a packet without registering it normally
	 */
	public void receiveUnregisteredPacket(final Packet packetIn) {
		if (this.isChannelOpen()) {
			this.dispatchPacket(packetIn, (GenericFutureListener<? extends Future<? super Void>>[]) null);
		} else {
			this.readWriteLock.writeLock().lock();
			try {
				this.inboundPacketsQueue.add(new InboundHandlerTuplePacketListener(packetIn, (GenericFutureListener[]) null));
			} finally {
				this.readWriteLock.writeLock().unlock();
			}
		}
	}

	/**
	 * Will commit the packet to the channel. If the current thread 'owns' the channel it will write and
	 * flush the packet, otherwise it will add a task for the channel eventloop thread to do that.
	 */
	private void dispatchPacket(final Packet inPacket, final GenericFutureListener<? extends Future<? super Void>>[] futureListeners) {
		if (this.direction == EnumPacketDirection.CLIENTBOUND) {
			DebugUtil.log("OUT", inPacket);
			final PacketEvent packetEvent = Client.getInstance().getEventManager().call(new PacketEvent(inPacket, PacketEvent.Direction.OUTBOUND));
			if (packetEvent.isCancelled()) return;
		}
		final EnumConnectionState enumconnectionstate = EnumConnectionState.getFromPacket(inPacket);
		final EnumConnectionState enumconnectionstate1 = this.channel.attr(attrKeyConnectionState).get();
		if (enumconnectionstate1 != enumconnectionstate) {
			logger.debug("Disabled auto read");
			this.channel.config().setAutoRead(false);
		}
		if (this.channel.eventLoop().inEventLoop()) {
			if (enumconnectionstate != enumconnectionstate1) this.setConnectionState(enumconnectionstate);
			final ChannelFuture channelfuture = this.channel.writeAndFlush(inPacket);
			if (futureListeners != null) channelfuture.addListeners(futureListeners);
			channelfuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		} else this.channel.eventLoop().execute(() -> {
			if (enumconnectionstate != enumconnectionstate1) NetworkManager.this.setConnectionState(enumconnectionstate);
			final ChannelFuture channelfuture1 = NetworkManager.this.channel.writeAndFlush(inPacket);
			if (futureListeners != null) channelfuture1.addListeners(futureListeners);
			channelfuture1.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		});
	}

	/**
	 * Will iterate through the outboundPacketQueue and dispatch all Packets
	 */
	private void flushOutboundQueue() {
		if (this.channel != null && this.channel.isOpen()) {
			this.readWriteLock.readLock().lock();
			try {
				while (!this.outboundPacketsQueue.isEmpty()) {
					final InboundHandlerTuplePacketListener networkmanager$inboundhandlertuplepacketlistener = this.outboundPacketsQueue.poll();
					this.dispatchPacket(networkmanager$inboundhandlertuplepacketlistener.packet, networkmanager$inboundhandlertuplepacketlistener.futureListeners);
				}
			} finally {
				this.readWriteLock.readLock().unlock();
			}
		}
	}

	/**
	 * Checks timeouts and processes all packets received
	 */
	public void processReceivedPackets() {
		this.flushOutboundQueue();
		if (this.packetListener instanceof ITickable) ((ITickable) this.packetListener).update();
		this.channel.flush();
	}

	/**
	 * Returns the socket address of the remote side. Server-only.
	 */
	public SocketAddress getRemoteAddress() { return this.socketAddress; }

	/**
	 * Closes the channel, the parameter can be used for an exit message (not certain how it gets sent)
	 */
	public void closeChannel(final IChatComponent message) {
		this.terminationReason = message;
		if (this.channel != null && this.channel.isOpen()) {
			this.channel.close().awaitUninterruptibly();
		}
	}

	/**
	 * True if this NetworkManager uses a memory connection (single player game). False may imply both
	 * an active TCP connection or simply no active connection at all
	 */
	public boolean isLocalChannel() { return this.channel instanceof LocalChannel || this.channel instanceof LocalServerChannel; }

	/**
	 * Create a new NetworkManager from the server host and connect it to the server
	 *
	 * @param address            The address of the server
	 * @param serverPort         The server port
	 * @param useNativeTransport True if the client use the native transport system
	 */
	public static NetworkManager createNetworkManagerAndConnect(final InetAddress address, final int serverPort, final boolean useNativeTransport) {
		return createNetworkManagerAndConnect(address.getHostAddress(), address, serverPort, useNativeTransport, null);
	}

	public static NetworkManager createNetworkManagerAndConnect(final String host, final int serverPort, final boolean useNativeTransport) {
		return createNetworkManagerAndConnect(host, null, serverPort, useNativeTransport, AltManager.getInstance().getActiveProxy());
	}

	public static NetworkManager createNetworkManagerAndConnect(final String host, final int serverPort, final boolean useNativeTransport, final ProxyConfig proxyConfig) {
		return createNetworkManagerAndConnect(host, null, serverPort, useNativeTransport, proxyConfig);
	}

	private static NetworkManager createNetworkManagerAndConnect(final String host, final InetAddress address, final int serverPort, final boolean useNativeTransport, final ProxyConfig proxyConfig) {
		final NetworkManager networkmanager = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
		try {
			Class<? extends SocketChannel> oclass;
			LazyLoadBase<? extends EventLoopGroup> lazyloadbase;
			if (Epoll.isAvailable() && useNativeTransport) {
				oclass = EpollSocketChannel.class;
				lazyloadbase = CLIENT_EPOLL_EVENTLOOP;
			} else {
				oclass = NioSocketChannel.class;
				lazyloadbase = CLIENT_NIO_EVENTLOOP;
			}
			final SocketAddress remoteAddress = proxyConfig == null
					? new InetSocketAddress(address == null ? InetAddress.getByName(host) : address, serverPort)
					: InetSocketAddress.createUnresolved(host, serverPort);
			final ChannelFuture connectFuture = (new Bootstrap()).group(lazyloadbase.getValue()).handler(new ChannelInitializer<Channel>() {
				@Override
				protected void initChannel(final Channel p_initChannel_1_) throws Exception {
					try {
						p_initChannel_1_.config().setOption(ChannelOption.TCP_NODELAY, true);
					} catch (final ChannelException var3) {}
					if (proxyConfig != null) {
						p_initChannel_1_.pipeline().addLast("proxy", createProxyHandler(proxyConfig));
					}
					p_initChannel_1_.pipeline().addLast("timeout", (new ReadTimeoutHandler(30))).addLast("splitter", (new MessageDeserializer2())).addLast("decoder", (new MessageDeserializer(EnumPacketDirection.CLIENTBOUND)))
							.addLast("prepender", (new MessageSerializer2())).addLast("encoder", (new MessageSerializer(EnumPacketDirection.SERVERBOUND))).addLast("packet_handler", networkmanager);
				}
			}).channel(oclass).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS).connect(remoteAddress);

			if (!connectFuture.awaitUninterruptibly(CONNECT_WAIT_TIMEOUT_MILLIS)) {
				final IOException timeoutException = new IOException("Connection timed out.");
				networkmanager.setConnectionFailure(timeoutException);
				connectFuture.cancel(true);
				if (connectFuture.channel() != null) {
					connectFuture.channel().close();
				}
				return networkmanager;
			}

			if (!connectFuture.isSuccess()) {
				networkmanager.setConnectionFailure(connectFuture.cause());
				if (connectFuture.channel() != null) {
					connectFuture.channel().close();
				}
				return networkmanager;
			}

			if (!networkmanager.waitForChannel(CHANNEL_READY_TIMEOUT_MILLIS)) {
				networkmanager.setConnectionFailure(new IOException("Timed out opening network channel."));
				if (connectFuture.channel() != null) {
					connectFuture.channel().close();
				}
			}
			return networkmanager;
		} catch (final Exception e) {
			networkmanager.setConnectionFailure(e);
			logger.error("Could not create network manager for " + host + ":" + serverPort, e);
			return networkmanager;
		}
	}

	private static Socks5ProxyHandler createProxyHandler(final ProxyConfig proxyConfig) {
		final Socks5ProxyHandler proxyHandler = new Socks5ProxyHandler(proxyConfig.getSocketAddress(), proxyConfig.getUsername(), proxyConfig.getPassword());
		proxyHandler.setConnectTimeoutMillis(PROXY_TIMEOUT_MILLIS);
		return proxyHandler;
	}

	/**
	 * Prepares a clientside NetworkManager: establishes a connection to the socket supplied and
	 * configures the channel pipeline. Returns the newly created instance.
	 */
	public static NetworkManager provideLocalClient(final SocketAddress address) {
		final NetworkManager networkmanager = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
		(new Bootstrap()).group(CLIENT_LOCAL_EVENTLOOP.getValue()).handler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(final Channel p_initChannel_1_) throws Exception { p_initChannel_1_.pipeline().addLast("packet_handler", networkmanager); }
		}).channel(LocalChannel.class).connect(address).syncUninterruptibly();
		return networkmanager;
	}

	/**
	 * Adds an encoder+decoder to the channel pipeline. The parameter is the secret key used for
	 * encrypted communication
	 */
	public void enableEncryption(final SecretKey key) {
		this.isEncrypted = true;
		this.channel.pipeline().addBefore("splitter", "decrypt", new NettyEncryptingDecoder(CryptManager.createNetCipherInstance(2, key)));
		this.channel.pipeline().addBefore("prepender", "encrypt", new NettyEncryptingEncoder(CryptManager.createNetCipherInstance(1, key)));
	}

	public boolean getIsencrypted() { return this.isEncrypted; }

	/**
	 * Returns true if this NetworkManager has an active channel, false otherwise
	 */
	public boolean isChannelOpen() { return this.channel != null && this.channel.isOpen(); }

	public boolean hasNoChannel() { return this.channel == null; }

	public String getConnectionFailureMessage() { return compactFailureMessage(this.connectionFailure); }

	/**
	 * Gets the current handler for processing packets
	 */
	public INetHandler getNetHandler() { return this.packetListener; }

	/**
	 * If this channel is closed, returns the exit message, null otherwise.
	 */
	public IChatComponent getExitMessage() { return this.terminationReason; }

	private void setConnectionFailure(final Throwable failure) {
		this.connectionFailure = failure;
		final String message = compactFailureMessage(failure);
		if (!message.isEmpty()) {
			this.terminationReason = new ChatComponentText(message);
		}
	}

	private boolean waitForChannel(final long timeoutMillis) {
		final long deadline = System.currentTimeMillis() + timeoutMillis;
		while (this.channel == null && System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(10L);
			} catch (final InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				this.setConnectionFailure(interruptedException);
				return false;
			}
		}
		return this.channel != null;
	}

	private static String compactFailureMessage(final Throwable failure) {
		Throwable current = failure;
		while (current != null) {
			final String message = current.getMessage();
			if (message != null && !message.trim().isEmpty()) {
				return message;
			}
			current = current.getCause();
		}
		return failure == null ? "" : failure.getClass().getSimpleName();
	}

	/**
	 * Switches the channel to manual reading modus
	 */
	public void disableAutoRead() { this.channel.config().setAutoRead(false); }

	public void setCompressionTreshold(final int treshold) {
		if (treshold >= 0) {
			if (this.channel.pipeline().get("decompress") instanceof NettyCompressionDecoder) ((NettyCompressionDecoder) this.channel.pipeline().get("decompress")).setCompressionTreshold(treshold);
			else this.channel.pipeline().addBefore("decoder", "decompress", new NettyCompressionDecoder(treshold));
			if (this.channel.pipeline().get("compress") instanceof NettyCompressionEncoder) ((NettyCompressionEncoder) this.channel.pipeline().get("decompress")).setCompressionTreshold(treshold);
			else this.channel.pipeline().addBefore("encoder", "compress", new NettyCompressionEncoder(treshold));
		} else {
			if (this.channel.pipeline().get("decompress") instanceof NettyCompressionDecoder) this.channel.pipeline().remove("decompress");
			if (this.channel.pipeline().get("compress") instanceof NettyCompressionEncoder) this.channel.pipeline().remove("compress");
		}
		io.netty.channel.ChannelHandler recorder = null;
		for (java.util.Map.Entry<String, io.netty.channel.ChannelHandler> entry : this.channel.pipeline()) {
			String key = entry.getKey();
			if ("replay_recorder_raw".equals(key)) {
				recorder = entry.getValue();
			}
			if ("decompress".equals(key) && recorder != null) {
				this.channel.pipeline().remove(recorder);
				this.channel.pipeline().addBefore("decoder", "replay_recorder_raw", recorder);
				return;
			}
		}
	}

	public void checkDisconnected() {
		if (this.channel != null && !this.channel.isOpen()) if (!this.disconnected) {
			this.disconnected = true;
			if (this.getExitMessage() != null) this.getNetHandler().onDisconnect(this.getExitMessage());
			else if (this.getNetHandler() != null) this.getNetHandler().onDisconnect(new ChatComponentText("Disconnected"));
		} else logger.warn("handleDisconnection() called twice");
	}

	static class InboundHandlerTuplePacketListener {
		private final Packet packet;
		private final GenericFutureListener<? extends Future<? super Void>>[] futureListeners;

		public InboundHandlerTuplePacketListener(final Packet inPacket, final GenericFutureListener<? extends Future<? super Void>>... inFutureListeners)
		{
			this.packet = inPacket;
			this.futureListeners = inFutureListeners;
		}
	}
}
