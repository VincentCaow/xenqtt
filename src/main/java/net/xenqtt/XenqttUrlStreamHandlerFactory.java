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
package net.xenqtt;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

/**
 * Factory to support custom {@link URLStreamHandler}s for "xenqtt" and "log4j" protocols
 */
final class XenqttUrlStreamHandlerFactory implements URLStreamHandlerFactory {

	private final LoggingLevels loggingLevels;
	private final String appName;
	private final boolean consoleLogger;

	public XenqttUrlStreamHandlerFactory(LoggingLevels loggingLevels, String appName, boolean consoleLogger) {
		this.loggingLevels = loggingLevels;
		this.appName = appName;
		this.consoleLogger = consoleLogger;
	}

	/**
	 * @see java.net.URLStreamHandlerFactory#createURLStreamHandler(java.lang.String)
	 */
	@Override
	public URLStreamHandler createURLStreamHandler(String protocol) {

		if ("xenqtt".equals(protocol)) {
			return new XenqttUrlStreamHandler();
		} else if ("log4j".equals(protocol)) {
			return new Log4jUrlStreamHandler(loggingLevels, appName, consoleLogger);
		}

		return null;
	}

}
