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
package io.kodokojo.brickmanager.service.actor;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.japi.pf.UnitPFBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Injector;
import io.kodokojo.brickmanager.BrickStartContext;
import io.kodokojo.brickmanager.service.BrickManager;
import io.kodokojo.brickmanager.service.actor.project.*;
import io.kodokojo.commons.config.ApplicationConfig;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.event.EventBuilder;
import io.kodokojo.commons.event.EventBuilderFactory;
import io.kodokojo.commons.event.GsonEventSerializer;
import io.kodokojo.commons.event.payload.BrickStateChanged;
import io.kodokojo.commons.event.payload.ProjectConfigurationChangeUserRequest;
import io.kodokojo.commons.event.payload.StackStarted;
import io.kodokojo.commons.event.payload.TypeChange;
import io.kodokojo.commons.model.ProjectConfiguration;
import io.kodokojo.commons.model.UpdateData;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.service.BrickUrlFactory;
import io.kodokojo.commons.service.actor.AbstractEventEndpointActor;
import io.kodokojo.commons.service.actor.message.BrickStateEvent;
import io.kodokojo.commons.service.actor.message.EventBusOriginMessage;
import io.kodokojo.commons.service.actor.message.EventReplyableMessage;
import io.kodokojo.commons.service.dns.DnsManager;
import io.kodokojo.commons.service.repository.ProjectFetcher;
import io.kodokojo.commons.service.repository.UserFetcher;
import javaslang.control.Try;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static akka.event.Logging.getLogger;
import static java.util.Objects.requireNonNull;

public class EndpointActor extends AbstractEventEndpointActor {

    public static final String ACTOR_PATH = "/user/endpoint";

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    private final ProjectFetcher projectFectcher;

    private final UserFetcher userFetcher;

    private final EventBuilderFactory eventBuilderFactory;

    private final BrickManager brickManager;

    private final BrickUrlFactory brickUrlFactory;

    private final DnsManager dnsManager;

    private final ApplicationConfig applicationConfig;

    public static Props PROPS(Injector injector) {
        requireNonNull(injector, "injector must be defined.");
        return Props.create(EndpointActor.class, injector);
    }

    public static final String NAME = "endpointAkka";

    public EndpointActor(Injector injector) {
        super(injector);
        projectFectcher = injector.getInstance(ProjectFetcher.class);
        userFetcher = injector.getInstance(UserFetcher.class);
        eventBuilderFactory = injector.getInstance(EventBuilderFactory.class);
        dnsManager = injector.getInstance(DnsManager.class);
        brickUrlFactory = injector.getInstance(BrickUrlFactory.class);
        applicationConfig = injector.getInstance(ApplicationConfig.class);
        brickManager = injector.getInstance(BrickManager.class);
    }

    @Override
    protected Try<ActorRefWithMessage> convertToActorRefWithMessage(Event event, User requester) {
        requireNonNull(event, "event must be defined.");
        EventBusOriginMessage msg = null;
        ActorRef actorRef = null;

        String eventType = event.getEventType();
        switch (eventType) {
            case Event.PROJECTCONFIG_START_REQUEST:
                actorRef = getContext().actorOf(ProjectConfigurationStarterActor.PROPS(projectFectcher));
                ProjectConfiguration projectConfiguration = projectFectcher.getProjectConfigurationById(event.getPayload());
                msg = new ProjectConfigurationStarterActor.ProjectConfigurationStartMsg(requester, event, projectConfiguration, true);
                break;
            case Event.PROJECTCONFIG_CHANGE_USER_REQUEST:
                ProjectConfigurationChangeUserRequest payload = event.getPayload(ProjectConfigurationChangeUserRequest.class);
                payload.setRequest(event);
                msg = payload;
                break;

        }
        return Try.success(new ActorRefWithMessage(actorRef, msg));
    }


    @Override
    protected UnitPFBuilder<Object> messageMatcherBuilder() {
        return ReceiveBuilder
                .match(ProjectConfigurationStarterActor.ProjectConfigurationStartMsg.class, msg -> {
                    dispatch(msg, sender(), getContext().actorOf(ProjectConfigurationStarterActor.PROPS(projectFectcher)));
                })
                .match(StackConfigurationStarterActor.StackConfigurationStartMsg.class, msg -> {
                    dispatch(msg, sender(), getContext().actorOf(StackConfigurationStarterActor.PROPS(dnsManager, brickUrlFactory, applicationConfig)));
                })
                .match(ProjectCreatorActor.ProjectCreateMsg.class, msg -> {
                    dispatch(msg, sender(), getContext().actorOf(ProjectCreatorActor.PROPS(eventBus, eventBuilderFactory)));
                })
                .match(BrickStartContext.class, msg -> {
                    dispatch(msg, sender(), getContext().actorOf(BrickConfigurationStarterActor.PROPS(brickManager, brickUrlFactory, eventBus, eventBuilderFactory)));
                })
                .match(BrickStateEvent.class, msg -> {
                    EventBuilder eventBuilder = eventBuilderFactory.create();
                    eventBuilder.setEventType(Event.BRICK_STATE_UPDATE);

                    BrickStateEvent.State newState = msg.getState();
                    BrickStateEvent.State oldState = msg.getOldState();

                    eventBuilder
                            .addCustomHeader(Event.PROJECTCONFIGURATION_ID_CUSTOM_HEADER, msg.getProjectConfigurationIdentifier())
                            .setPayload(new BrickStateChanged(msg.getProjectConfigurationIdentifier(), msg.getStackName(), msg.getBrickName(), newState.toString(), oldState == null ? null : oldState.name()));
                    eventBus.send(eventBuilder.build());


                })
                .match(StackConfigurationStarterActor.StackConfigurationStartMsg.class, msg -> {
                    EventBuilder eventBuilder = eventBuilderFactory.create();
                    eventBuilder.setEventType(Event.STACK_STARTED);
                    StackStarted payload = new StackStarted(msg.getProjectConfiguration().getName(), msg.getStackConfiguration().getName());
                    eventBuilder.setPayload(payload);
                }).match(ProjectUpdaterMessages.ListAndUpdateUserToProjectMsg.class, msg -> {
                    dispatch(msg, sender(), getContext().actorOf(ListAndUpdateUserToProjectActor.PROPS(projectFectcher)));
                }).match(ProjectConfigurationChangeUserRequest.class, msg -> {
                    msg.getUserIdentifiers().stream()
                            .map(userFetcher::getUserByIdentifier)
                            .map(u -> {
                                UpdateData<User> updateData = null;
                                if (msg.getTypeChange() == TypeChange.ADD) {
                                    updateData = new UpdateData<>(null, u);
                                } else {
                                    updateData = new UpdateData<>(u, null);
                                }
                                return updateData;
                            })
                            .map(u -> new ProjectUpdaterMessages.ListAndUpdateUserToProjectMsg(msg.getRequester(), msg.originalEvent(), u))
                    .forEach(m -> getContext().self().forward(m, getContext()));
                });
    }

    @Override
    protected void dispatch(Object msg, ActorRef sender, ActorRef target) {
        if (msg instanceof ProjectConfigurationStarterActor.ProjectConfigurationStartResultMsg) {
            target.tell(msg, self());
        } else {
            super.dispatch(msg, sender, target);
        }
    }

    @Override
    protected void onEventReplyableMessagePreReply(EventReplyableMessage msg, EventBuilderFactory eventBuilderFactory) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Receive reply message :{}", msg);
        }
        if (msg instanceof ProjectConfigurationStarterActor.ProjectConfigurationStartResultMsg) {
            ProjectConfigurationStarterActor.ProjectConfigurationStartResultMsg projectConfigurationStartResultMsg = (ProjectConfigurationStarterActor.ProjectConfigurationStartResultMsg) msg;
            EventBuilder eventBuilder = eventBuilderFactory.create();
            eventBuilder.setEventType(Event.PROJECTCONFIG_STARTED);
            eventBuilder.copyCustomHeader(msg.originalEvent(), Event.REQUESTER_ID_CUSTOM_HEADER);
            User requester = projectConfigurationStartResultMsg.getRequester();
            if (requester != null) {
                eventBuilder.addCustomHeader(Event.ENTITY_ID_CUSTOM_HEADER, requester.getEntityIdentifier());
            }
            String projectId = projectConfigurationStartResultMsg.getProjectId();
            eventBuilder.setJsonPayload(projectId);
            Event event = eventBuilder.build();
            eventBus.send(event);
            if (LOGGER.isDebugEnabled()) {
                Gson gson = new GsonBuilder().registerTypeAdapter(Event.class, new GsonEventSerializer()).setPrettyPrinting().create();
                LOGGER.debug("Sending following event:\n{}", gson.toJson(event));
            }
        }
    }
}
