/**
 * Kodo Kojo - API frontend which dispatch REST event to Http services or publish event on EvetnBus.
 * Copyright © 2016 Kodo Kojo (infos@kodokojo.io)
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.kodokojo.brickmanager;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.google.inject.*;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.kodokojo.brickmanager.config.module.*;
import io.kodokojo.brickmanager.service.BrickManager;
import io.kodokojo.brickmanager.service.actor.EndpointActor;
import io.kodokojo.commons.config.ApplicationConfig;
import io.kodokojo.commons.config.MicroServiceConfig;
import io.kodokojo.commons.config.module.*;
import io.kodokojo.commons.event.EventBus;
import io.kodokojo.commons.model.*;
import io.kodokojo.commons.service.healthcheck.HttpHealthCheckEndpoint;
import io.kodokojo.commons.service.ProjectConfigurationException;
import io.kodokojo.commons.service.actor.EventToEndpointGateway;
import io.kodokojo.commons.service.lifecycle.ApplicationLifeCycleManager;
import org.apache.commons.collections4.IteratorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class Launcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(Launcher.class);

    public static final String MOCK = "mock";

    public static void main(String[] args) {


        Injector propertyInjector = Guice.createInjector(new CommonsPropertyModule(args), new PropertyModule());
        MicroServiceConfig microServiceConfig = propertyInjector.getInstance(MicroServiceConfig.class);
        LOGGER.info("Starting Kodo Kojo {}.", microServiceConfig.name());
        Injector servicesInjector = propertyInjector.createChildInjector(new UtilityServiceModule(), new EventBusModule(), new DatabaseModule(), new SecurityModule(), new ServiceModule(), new CommonsHealthCheckModule());
        Module orchestratorModule = new MarathonModule();
        OrchestratorConfig orchestratorConfig = propertyInjector.getInstance(OrchestratorConfig.class);
        if (MOCK.equals(orchestratorConfig.orchestrator())) {
            LOGGER.warn("Mocking orchestrator");
            orchestratorModule = new AbstractModule() {
                @Override
                protected void configure() {
                    //
                }

                @Provides
                @Singleton
                BrickManager provideBrickManager() {
                    return new BrickManager() {
                        @Override
                        public Set<Service> start(ProjectConfiguration projectConfiguration, StackConfiguration stackConfiguration, BrickConfiguration brickConfiguration) throws BrickAlreadyExist {
                            if (projectConfiguration.getName().endsWith("fail-start")) {
                                return new HashSet<>();
                            }

                            Set<Service> res = new HashSet<>();
                            res.add(new Service(brickConfiguration.getName(), "localhost", new PortDefinition(80)));
                            return res;
                        }

                        @Override
                        public BrickConfigurerData configure(ProjectConfiguration projectConfiguration, StackConfiguration stackConfiguration, BrickConfiguration brickConfiguration) throws ProjectConfigurationException {
                            if (projectConfiguration.getName().endsWith("fail-configure")) {
                                throw new ProjectConfigurationException("Failing name pattern.");
                            }
                            String domaine = projectConfiguration.getName() + "-" + stackConfiguration.getName() + ".kodokojo.dev";
                            return new BrickConfigurerData(projectConfiguration.getName(), projectConfiguration.getIdentifier(), stackConfiguration.getName(), brickConfiguration.getName(), "http://" + domaine, domaine, IteratorUtils.toList(projectConfiguration.getAdmins()), IteratorUtils.toList(projectConfiguration.getUsers()));
                        }

                        @Override
                        public boolean stop(BrickConfiguration brickDeploymentState) {
                            return false;
                        }
                    };
                }
            };
        }
        Injector orchestratorInjector = servicesInjector.createChildInjector(orchestratorModule);
        Injector akkaInjector = orchestratorInjector.createChildInjector(new AkkaModule());
        ActorSystem actorSystem = akkaInjector.getInstance(ActorSystem.class);
        ActorRef endpointActor = actorSystem.actorOf(EndpointActor.PROPS(akkaInjector), "endpoint");
        akkaInjector = akkaInjector.createChildInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ActorRef.class).annotatedWith(Names.named(EndpointActor.NAME)).toInstance(endpointActor);
            }
        });

        Injector injector = akkaInjector.createChildInjector(new AbstractModule() {
            @Override
            protected void configure() {
                //
            }

            @Provides
            @Singleton
            EventToEndpointGateway provideEventEventToEndpointGateway(@Named(EndpointActor.NAME) ActorRef akkaEndpoint) {
                return new EventToEndpointGateway(akkaEndpoint);
            }

        });

        EventBus eventBus = injector.getInstance(EventBus.class);
        EventToEndpointGateway eventToActorGateway = injector.getInstance(EventToEndpointGateway.class);
        eventBus.addEventListener(eventToActorGateway);

        ApplicationLifeCycleManager applicationLifeCycleManager = servicesInjector.getInstance(ApplicationLifeCycleManager.class);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                super.run();
                LOGGER.info("Stopping services.");
                applicationLifeCycleManager.stop();
                LOGGER.info("All services stopped.");
            }
        });
        eventBus.connect();

        HttpHealthCheckEndpoint httpHealthCheckEndpoint = injector.getInstance(HttpHealthCheckEndpoint.class);
        httpHealthCheckEndpoint.start();

        LOGGER.info("Kodo Kojo {} started.", microServiceConfig.name());

    }


}
