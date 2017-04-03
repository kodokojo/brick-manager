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
package io.kodokojo.brickmanager.service.actor.project;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.brickmanager.BrickAlreadyExist;
import io.kodokojo.brickmanager.BrickStartContext;
import io.kodokojo.brickmanager.service.BrickManager;
import io.kodokojo.brickmanager.service.actor.EndpointActor;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.event.EventBuilder;
import io.kodokojo.commons.event.EventBuilderFactory;
import io.kodokojo.commons.event.EventBus;
import io.kodokojo.commons.model.*;
import io.kodokojo.commons.service.BrickUrlFactory;
import io.kodokojo.commons.service.actor.message.BrickStateEvent;
import javaslang.control.Try;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static akka.event.Logging.getLogger;
import static java.util.Objects.requireNonNull;

//  TODO Refacto
public class BrickConfigurationStarterActor extends AbstractActor {


    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    public static Props PROPS(BrickManager brickManager, BrickUrlFactory brickUrlFactory, EventBus eventBus, EventBuilderFactory eventBuilderFactory) {
        requireNonNull(brickManager, "brickManager must be defined.");
        requireNonNull(brickUrlFactory, "brickUrlFactory must be defined.");
        requireNonNull(eventBus, "eventBus must be defined.");
        requireNonNull(eventBuilderFactory, "eventBuilderFactory must be defined.");
        return Props.create(BrickConfigurationStarterActor.class, brickManager, brickUrlFactory, eventBus, eventBuilderFactory);
    }

    private final BrickManager brickManager;

    private final BrickUrlFactory brickUrlFactory;

    private final EventBus eventBus;

    private final EventBuilderFactory eventBuilderFactory;

    @Inject
    public BrickConfigurationStarterActor(BrickManager brickManager, BrickUrlFactory brickUrlFactory, EventBus eventBus, EventBuilderFactory eventBuilderFactory) {
        this.brickManager = brickManager;
        this.brickUrlFactory = brickUrlFactory;
        this.eventBus = eventBus;
        this.eventBuilderFactory = eventBuilderFactory;

        receive(
                ReceiveBuilder
                        .match(BrickStartContext.class, this::start)
                        .matchAny(this::unhandled)
                        .build()
        );

    }

    protected final void start(BrickStartContext brickStartContext) {
        //ActorRef sender = sender();
        ProjectConfiguration projectConfiguration = brickStartContext.getProjectConfiguration();
        StackConfiguration stackConfiguration = brickStartContext.getStackConfiguration();
        BrickConfiguration brickConfiguration = brickStartContext.getBrickConfiguration();
        BrickType brickType = brickConfiguration.getType();
        String projectName = projectConfiguration.getName();
        String url = brickUrlFactory.forgeUrl(projectConfiguration, projectConfiguration.getDefaultStackConfiguration().getName(), brickConfiguration);
        String httpsUrl = "https://" + url;

        try {
            generateMsgAndSend(brickStartContext, httpsUrl, null, BrickStateEvent.State.STARTING);

            Future<Set<Service>> futureStart = Futures.future(() -> brickManager.start(projectConfiguration, stackConfiguration, brickConfiguration), getContext().dispatcher());

            Try<Set<Service>> servicesTry = Try.of(() -> Await.result(futureStart, Duration.create(15, TimeUnit.MINUTES))).onFailure(throwable -> {
                LOGGER.error("Unable to start brick '{}' for project '{}': {}", brickConfiguration.getName(), projectName, throwable);
                if (throwable instanceof BrickAlreadyExist) {
                    BrickAlreadyExist brickAlreadyExist = (BrickAlreadyExist) throwable;
                    LOGGER.error("BrickConfiguration {} already exist for project {}, not reconfigure it.", brickAlreadyExist.getBrickName(), brickAlreadyExist.getProjectName());
                    generateMsgAndSend(brickStartContext, httpsUrl, null, BrickStateEvent.State.ALREADYEXIST);
                    //  sender.tell(Futures.failed(brickAlreadyExist), self());
                }
            });

            Set<Service> services = servicesTry.get();
            if (CollectionUtils.isNotEmpty(services)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("{} for project {} started : {}", brickType, projectName, StringUtils.join(services, ","));
                }
                generateMsgAndSend(brickStartContext, httpsUrl, BrickStateEvent.State.STARTING, BrickStateEvent.State.CONFIGURING);

                BrickConfigurerData brickConfigurerData = null;

                Future<BrickConfigurerData> futureConfigure = Futures.future(() -> brickManager.configure(projectConfiguration, stackConfiguration, brickConfiguration), getContext().dispatcher());
                Try<BrickConfigurerData> brickConfigurerDataTry = Try.of(() -> Await.result(futureConfigure, Duration.apply(5, TimeUnit.MINUTES))).onFailure(throwable -> {
                    LOGGER.error("An error occur while trying to configure project {}: {}", projectName, throwable);
                    generateMsgAndSend(brickStartContext, httpsUrl, null, BrickStateEvent.State.ONFAILURE, throwable.getMessage());
                });

                brickConfigurerData = brickConfigurerDataTry.get();
                if (brickConfigurerDataTry.isSuccess()) {
                    EventBuilder eventBuilder = eventBuilderFactory.create();
                    eventBuilder.setEventType(Event.BRICK_PROPERTY_UPDATE_REQUEST);
                    eventBuilder.setPayload(brickConfigurerData);
                    eventBus.send(eventBuilder.build());

                    generateMsgAndSend(brickStartContext, url, BrickStateEvent.State.CONFIGURING, BrickStateEvent.State.RUNNING);

                }
            } else {
                generateMsgAndSend(brickStartContext, httpsUrl, null, BrickStateEvent.State.UNKNOWN, "We are not able to determinate the brick state from brick manager.");
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Start on brick '{}' for project '{}' returned an empty service.", brickConfiguration.getName(), projectName);
                }
            }
        } catch (RuntimeException e) {
            LOGGER.error("An error occurred while trying to start brick {} for project {}: {}", brickType, projectName, e);
            generateMsgAndSend(brickStartContext, httpsUrl, null, BrickStateEvent.State.ONFAILURE, e.getMessage());
        }
        getContext().stop(self());

    }

    private void generateMsgAndSend(BrickStartContext context, String url, BrickStateEvent.State oldState, BrickStateEvent.State newState, String messageStr) {
        ProjectConfiguration projectConfiguration = context.getProjectConfiguration();
        StackConfiguration stackConfiguration = context.getStackConfiguration();
        BrickConfiguration brickConfiguration = context.getBrickConfiguration();
        BrickType brickType = brickConfiguration.getType();
        String brickName = brickConfiguration.getName();
        BrickStateEvent message = new BrickStateEvent(projectConfiguration.getIdentifier(), stackConfiguration.getName(), brickType.name(), brickName, oldState, newState, url, messageStr, brickConfiguration.getVersion());
        getContext().actorFor(EndpointActor.ACTOR_PATH).tell(message, self());
    }

    private void generateMsgAndSend(BrickStartContext context, String url, BrickStateEvent.State oldState, BrickStateEvent.State newState) {
        generateMsgAndSend(context, url, oldState, newState, "");
    }

}
