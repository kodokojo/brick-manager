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

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.DeadLetter;
import com.google.inject.AbstractModule;
import io.kodokojo.commons.service.actor.DeadLetterActor;


public class AkkaModule extends AbstractModule {

    @Override
    protected void configure() {
        ActorSystem actorSystem = ActorSystem.apply("kodokojo");
        ActorRef deadletterlistener = actorSystem.actorOf(DeadLetterActor.PROPS(), "deadletterlistener");
        actorSystem.eventStream().subscribe(deadletterlistener, DeadLetter.class);
        bind(ActorSystem.class).toInstance(actorSystem);
    }
/*
    @Provides
    @Named(ProjectEndpointActor.NAME)
    Props provideProjectEndpointProps(ProjectRepository projectRepository, BrickFactory brickFactory, BootstrapConfigurationProvider bootstrapConfigurationProvider, ConfigurationStore configurationStore) {
        return ProjectEndpointActor.PROPS(projectRepository, brickFactory, bootstrapConfigurationProvider, configurationStore);
    }
*/
}
