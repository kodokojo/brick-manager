/**
 * Kodo Kojo - Software factory done right
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
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.brickmanager.service.actor.EndpointActor;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.model.*;
import io.kodokojo.commons.service.ProjectAlreadyExistException;
import io.kodokojo.commons.service.actor.message.BrickStateEvent;
import io.kodokojo.commons.service.actor.message.EventUserReplyMessage;
import io.kodokojo.commons.service.actor.message.EventUserRequestMessage;
import io.kodokojo.commons.service.actor.right.RightEndpointActor;
import io.kodokojo.commons.service.repository.ProjectFetcher;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static akka.event.Logging.getLogger;
import static java.util.Objects.requireNonNull;

public class ProjectConfigurationStarterActor extends AbstractActor {


    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    public static Props PROPS(ProjectFetcher projectFetcher) {
        requireNonNull(projectFetcher, "projectFetcher must be defined.");
        return Props.create(ProjectConfigurationStarterActor.class, projectFetcher);
    }

    private final ProjectFetcher projectFetcher;

    private ActorRef originalSender;

    private ProjectConfigurationStartMsg initialMsg;

    private ProjectCreatorActor.ProjectCreateResultMsg projectResult;

    public ProjectConfigurationStarterActor(ProjectFetcher projectFetcher) {
        this.projectFetcher = projectFetcher;
        receive(ReceiveBuilder
                .match(ProjectConfigurationStartMsg.class, this::onProjectConfigurationStart)
                .match(RightEndpointActor.RightRequestResultMsg.class, this::onRightUserReply)
                .match(ProjectCreatorActor.ProjectCreateResultMsg.class, this::onProjectCreated)
                .match(StackConfigurationStarterActor.StackConfigurationStartResultMsg.class, this::onStackConfigurationStarted)
                .matchAny(this::unhandled).build());
    }

    private void onStackConfigurationStarted(StackConfigurationStarterActor.StackConfigurationStartResultMsg msg) {
        if (msg.isSuccess()) {
            LOGGER.info("Project {} successfully started.", msg.getProjectConfiguration().getName());
        } else {
            LOGGER.error("Project {} failed to start.", msg.getProjectConfiguration().getName());
        }
        originalSender.tell(new ProjectConfigurationStartResultMsg(initialMsg.getRequester(), initialMsg.originalEvent(), projectResult.getProject().getIdentifier()), self());
        getContext().stop(self());
    }

    private void onProjectCreated(ProjectCreatorActor.ProjectCreateResultMsg msg) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Receive project creation Result :{}", msg.getProject().getName());
        }
        originalSender.forward(msg, getContext());
        projectResult = msg;
        ProjectConfiguration projectConfiguration = initialMsg.projectConfiguration;
        StackConfiguration defaultStackConfiguration = projectConfiguration.getDefaultStackConfiguration();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Request a stack start for project {}.", projectConfiguration.getName());
        }
        getContext().actorFor(EndpointActor.ACTOR_PATH).tell(new StackConfigurationStarterActor.StackConfigurationStartMsg(projectConfiguration, defaultStackConfiguration), ActorRef.noSender());
    }

    private void onRightUserReply(RightEndpointActor.RightRequestResultMsg msg) {

        LOGGER.debug("Receive right response : {}.", msg.isValid());
        if (msg.isValid()) {
            ProjectConfiguration projectConfiguration = initialMsg.projectConfiguration;
            Project project = projectFetcher.getProjectByProjectConfigurationId(projectConfiguration.getIdentifier());
            if (project == null) {
                Set<Stack> stacks = new HashSet<>();

                StackConfiguration defaultStackConfiguration = projectConfiguration.getDefaultStackConfiguration();
                Stack stack = new Stack(defaultStackConfiguration.getName(), defaultStackConfiguration.getType(), new HashSet<>());
                Set<BrickStateEvent> brickStateEvents = defaultStackConfiguration.getBrickConfigurations().stream()
                        .map(b -> new BrickStateEvent(projectConfiguration.getIdentifier(),
                                stack.getName(),
                                b.getType().toString(),
                                b.getName(),
                                BrickStateEvent.State.UNKNOWN,
                                b.getVersion())
                        ).collect(Collectors.toSet());
                stack.getBrickStateEvents().addAll(brickStateEvents);
                stacks.add(stack);
                Project res = new Project(projectConfiguration.getIdentifier(), projectConfiguration.getName(), new Date(), stacks);
                getContext().actorFor(EndpointActor.ACTOR_PATH).tell(new ProjectCreatorActor.ProjectCreateMsg(initialMsg.getRequester(), initialMsg.originalEvent(), res, projectConfiguration.getIdentifier()), self());


            } else {
                LOGGER.error("Project {} already exist [{}].", project.getName(), project.getIdentifier());
                //sender().tell(new ProjectAlreadyExistException(projectConfiguration.getName()), self());
                self().tell(new ProjectCreatorActor.ProjectCreateResultMsg(msg.getRequester(), initialMsg.originalEvent(), project), self());
                //getContext().stop(self());
            }
        }
    }

    private void onProjectConfigurationStart(ProjectConfigurationStartMsg msg) {
        originalSender = sender();
        initialMsg = msg;
        getContext().actorOf(RightEndpointActor.PROPS()).tell(new RightEndpointActor.UserAdminRightRequestMsg(msg.getRequester(), msg.projectConfiguration), self());
    }


    public static class ProjectConfigurationStartMsg extends EventUserRequestMessage {

        private final ProjectConfiguration projectConfiguration;

        private final boolean initialSenderIsEventBus;

        public ProjectConfigurationStartMsg(User requester, Event request, ProjectConfiguration projectConfiguration, boolean initialSenderIsEventBus) {
            super(requester, request);
            requireNonNull(projectConfiguration, "projectConfiguration must be defined.");

            this.projectConfiguration = projectConfiguration;
            this.initialSenderIsEventBus = initialSenderIsEventBus;
        }

        @Override
        public boolean initialSenderIsEventBus() {
            return initialSenderIsEventBus;
        }
    }

    public static class ProjectConfigurationStartResultMsg extends EventUserReplyMessage {

        private final String projectId;

        public ProjectConfigurationStartResultMsg(User requester, Event event, String projectId) {
            super(requester, event, Event.PROJECTCONFIG_START_REPLY, projectId);
            this.projectId = projectId;
        }

        public String getProjectId() {
            return projectId;
        }

        @Override
        public String eventType() {
            return Event.PROJECTCONFIG_START_REQUEST;
        }

        @Override
        public Serializable payloadReply() {
            return projectId;
        }

        @Override
        public Event originalEvent() {
            return request;
        }


    }

}
