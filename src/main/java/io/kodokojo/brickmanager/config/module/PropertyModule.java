/**
 * Kodo Kojo - API frontend which dispatch REST event to Http services or publish event on EvetnBus.
 * Copyright © 2016 Kodo Kojo (infos@kodokojo.io)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.kodokojo.brickmanager.config.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.kodokojo.commons.config.*;
import io.kodokojo.commons.config.properties.PropertyConfig;
import io.kodokojo.commons.config.properties.PropertyResolver;
import io.kodokojo.commons.config.properties.provider.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertyModule extends AbstractModule {

    @Override
    protected void configure() {
        // Nothing to do.
    }

    @Provides
    @Singleton
    MarathonConfig provideMarathonConfig(PropertyValueProvider valueProvider) {
        return createConfig(MarathonConfig.class, valueProvider);
    }

    @Provides
    @Singleton
    AwsConfig provideAwsConfig(PropertyValueProvider valueProvider) {
        return createConfig(AwsConfig.class, valueProvider);
    }

    @Provides
    @Singleton
    OrchestratorConfig provideOrchestratorConfig(PropertyValueProvider valueProvider) {
        return createConfig(OrchestratorConfig.class, valueProvider);
    }


    private <T extends PropertyConfig> T createConfig(Class<T> configClass, PropertyValueProvider valueProvider) {
        PropertyResolver resolver = new PropertyResolver(valueProvider);
        return resolver.createProxy(configClass);
    }

}
