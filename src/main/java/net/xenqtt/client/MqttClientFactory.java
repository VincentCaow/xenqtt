/**
    Copyright 2013 James McClure

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package net.xenqtt.client;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.xenqtt.ConfigurableThreadFactory;
import net.xenqtt.MqttInterruptedException;
import net.xenqtt.XenqttUtil;
import net.xenqtt.message.ChannelManager;
import net.xenqtt.message.ChannelManagerImpl;

/**
 * Used to create multiple "sibling" {@link MqttClient clients} that share an {@link Executor}, broker URI, etc.
 */
public final class MqttClientFactory implements AsyncClientFactory, SyncClientFactory {

	private final MqttClientConfig config;
	private final boolean synchronous;
	private final ChannelManager manager;
	private final Executor executor;
	private final ExecutorService executorService;
	private final ScheduledExecutorService reconnectionExecutor;
	private final String brokerUri;

	/**
	 * Constructs an object to create synchronous or asynchronous {@link MqttClient clients} using an {@link Executor} owned by this class with the default
	 * {@link MqttClientConfig config}.
	 * 
	 * @param brokerUri
	 *            The URL to the broker to connect to. For example, tcp://q.m2m.io:1883
	 * @param messageHandlerThreadPoolSize
	 *            The number of threads used to handle incoming messages and invoke the {@link MqttClientListener listener's} methods
	 * @param synchronous
	 *            True to create synchronous clients, false to create asynchronous clients. If true then the synchronous clients' blockingTimeoutSeconds will be
	 *            0 (wait forever).
	 */
	public MqttClientFactory(String brokerUri, int messageHandlerThreadPoolSize, boolean synchronous) {
		this(brokerUri, messageHandlerThreadPoolSize, synchronous, new MqttClientConfig());
	}

	/**
	 * Constructs an object to create synchronous or asynchronous {@link MqttClient clients} using a user provided {@link Executor} with the default
	 * {@link MqttClientConfig config}.
	 * 
	 * @param brokerUri
	 *            The URL to the broker to connect to. For example, tcp://q.m2m.io:1883
	 * @param executor
	 *            The executor used to handle incoming messages and invoke the listener's methods. This class will NOT shut down the executor.
	 * @param synchronous
	 *            True to create synchronous clients, false to create asynchronous clients. If true then the synchronous clients' blockingTimeoutSeconds will be
	 *            0 (wait forever).
	 */
	public MqttClientFactory(String brokerUri, Executor executor, boolean synchronous) {
		this(brokerUri, executor, synchronous, new MqttClientConfig());
	}

	/**
	 * Constructs an object to create synchronous or asynchronous {@link MqttClient clients} using an {@link Executor} owned by this class with a custom
	 * {@link MqttClientConfig config}.
	 * 
	 * @param brokerUri
	 *            The URL to the broker to connect to. For example, tcp://q.m2m.io:1883
	 * @param messageHandlerThreadPoolSize
	 *            The number of threads used to handle incoming messages and invoke the {@link MqttClientListener listener's} methods
	 * @param synchronous
	 *            True to create synchronous clients, false to create asynchronous clients.
	 * @param config
	 *            The configuration for the client
	 */
	public MqttClientFactory(String brokerUri, int messageHandlerThreadPoolSize, boolean synchronous, MqttClientConfig config) {
		this( //
				XenqttUtil.validateNotEmpty("brokerUri", brokerUri), //
				XenqttUtil.validateGreaterThan("messageHandlerThreadPoolSize", messageHandlerThreadPoolSize, 0), //
				null, //
				synchronous,//
				XenqttUtil.validateNotNull("config", config));
	}

	/**
	 * Constructs an object to create synchronous or asynchronous {@link MqttClient clients} using a user provided {@link Executor} with a custom
	 * {@link MqttClientConfig config}.
	 * 
	 * @param brokerUri
	 *            The URL to the broker to connect to. For example, tcp://q.m2m.io:1883
	 * @param executor
	 *            The executor used to handle incoming messages and invoke the listener's methods. This class will NOT shut down the executor.
	 * @param synchronous
	 *            True to create synchronous clients, false to create asynchronous clients.
	 * @param config
	 *            The configuration for the client
	 */
	public MqttClientFactory(String brokerUri, Executor executor, boolean synchronous, MqttClientConfig config) {
		this(//
				XenqttUtil.validateNotEmpty("brokerUri", brokerUri), //
				0, //
				XenqttUtil.validateNotNull("executor", executor), //
				synchronous, //
				XenqttUtil.validateNotNull("config", config));
	}

	/**
	 * @see net.xenqtt.client.SyncClientFactory#shutdown()
	 * @see net.xenqtt.client.AsyncClientFactory#shutdown()
	 */
	@Override
	public void shutdown() {

		this.manager.shutdown();

		reconnectionExecutor.shutdownNow();
		try {
			reconnectionExecutor.awaitTermination(1, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			throw new MqttInterruptedException(e);
		}

		if (executorService != null) {
			executorService.shutdownNow();
			try {
				executorService.awaitTermination(1, TimeUnit.DAYS);
			} catch (InterruptedException e) {
				throw new MqttInterruptedException(e);
			}
		}
	}

	/**
	 * @see net.xenqtt.client.SyncClientFactory#newSynchronousClient(net.xenqtt.client.MqttClientListener)
	 */
	@Override
	public MqttClient newSynchronousClient(MqttClientListener mqttClientListener) throws IllegalStateException {
		XenqttUtil.validateNotNull("mqttClientListener", mqttClientListener);

		if (!synchronous) {
			throw new IllegalStateException("You may not create a synchronous client using a client factory configured to create asynchronous clients");
		}

		return new FactoryClient(mqttClientListener, null);
	}

	/**
	 * @see net.xenqtt.client.AsyncClientFactory#newAsyncClient(net.xenqtt.client.AsyncClientListener)
	 */
	@Override
	public MqttClient newAsyncClient(AsyncClientListener asyncClientListener) throws IllegalStateException {

		if (synchronous) {
			throw new IllegalStateException("You may not create aa asynchronous client using a client factory configured to create synchronous clients");
		}

		return new FactoryClient(asyncClientListener, asyncClientListener);
	}

	/**
	 * @see net.xenqtt.client.ClientFactory#getStats(boolean)
	 */
	@Override
	public MessageStats getStats(boolean reset) {
		return manager.getStats(reset);
	}

	private MqttClientFactory(String brokerUri, int messageHandlerThreadPoolSize, Executor executor, boolean synchronous, MqttClientConfig config) {

		this.config = config.clone();
		this.synchronous = synchronous;
		this.brokerUri = brokerUri;
		this.executorService = executor == null ? Executors
				.newFixedThreadPool(messageHandlerThreadPoolSize, new ConfigurableThreadFactory("MqttClient", false)) : null;
		this.executor = executor == null ? executorService : executor;
		this.reconnectionExecutor = Executors.newSingleThreadScheduledExecutor();
		int blockingTimeoutSeconds = synchronous ? config.getBlockingTimeoutSeconds() : -1;
		this.manager = new ChannelManagerImpl(config.getMessageResendIntervalSeconds(), blockingTimeoutSeconds);
		this.manager.init();
	}

	private final class FactoryClient extends AbstractMqttClient {

		FactoryClient(MqttClientListener mqttClientListener, AsyncClientListener asyncClientListener) {
			super(brokerUri, mqttClientListener, asyncClientListener, executor, manager, reconnectionExecutor, config);
		}
	};
}
