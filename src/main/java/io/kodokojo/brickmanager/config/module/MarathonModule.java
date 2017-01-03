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
import io.kodokojo.brickmanager.BrickConfigurerProvider;
import io.kodokojo.commons.service.BrickUrlFactory;
import io.kodokojo.commons.service.ServiceLocator;
import io.kodokojo.brickmanager.service.marathon.MarathonServiceLocator;
import io.kodokojo.commons.config.ApplicationConfig;
import io.kodokojo.commons.config.MarathonConfig;
import io.kodokojo.brickmanager.service.BrickManager;
import io.kodokojo.brickmanager.service.marathon.MarathonBrickManager;
import io.kodokojo.commons.service.repository.ProjectRepository;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarathonModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarathonModule.class);

    @Override
    protected void configure() {
        // Nothing to do.
    }

    @Provides
    @Singleton
    ServiceLocator provideServiceLocator(MarathonConfig marathonConfig) {
        return new MarathonServiceLocator(marathonConfig);
    }

    @Provides
    @Singleton
    BrickManager provideBrickManager(MarathonConfig marathonConfig, BrickConfigurerProvider brickConfigurerProvider, ApplicationConfig applicationConfig, ProjectRepository projectRepository, BrickUrlFactory brickUrlFactory) {
        if (StringUtils.isNotBlank(marathonConfig.login())) {
            LOGGER.info("Add Marathon with basic Authentication.");
        }
        MarathonServiceLocator marathonServiceLocator = new MarathonServiceLocator(marathonConfig);
        return new MarathonBrickManager(marathonConfig, marathonServiceLocator, brickConfigurerProvider, projectRepository, !marathonConfig.ignoreContraint(), applicationConfig.domain(), brickUrlFactory);
    }

}
