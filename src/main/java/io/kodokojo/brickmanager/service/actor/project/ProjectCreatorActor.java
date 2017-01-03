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
package io.kodokojo.brickmanager.service.actor.project;

import akka.actor.Props;
import akka.event.LoggingAdapter;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.event.EventBuilderFactory;
import io.kodokojo.commons.event.EventBus;
import io.kodokojo.commons.model.Project;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.service.actor.AbstractEventRequestGatewayActor;
import io.kodokojo.commons.service.actor.message.EventBusMsg;
import io.kodokojo.commons.service.actor.message.EventUserReplyMessage;

import java.io.Serializable;

import static akka.event.Logging.getLogger;
import static java.util.Objects.requireNonNull;

public class ProjectCreatorActor extends AbstractEventRequestGatewayActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    public static Props PROPS(EventBus eventBus, EventBuilderFactory eventBuilderFactory) {
        requireNonNull(eventBus, "eventBus must be defined.");
        requireNonNull(eventBuilderFactory, "eventBuilderFactory must be defined.");
        return Props.create(ProjectCreatorActor.class, eventBus, eventBuilderFactory);
    }

    public ProjectCreatorActor(EventBus eventBus, EventBuilderFactory eventBuilderFactory) {
        super(eventBus, eventBuilderFactory);
    }

    @Override
    protected void receiveReply(EventBusMsg.EventBusMsgResult eventBusMsgResult) {
        LOGGER.debug("Receive a project created result, sending it to actor {}.", originalSender);
        ProjectCreateMsg initialMsg = (ProjectCreateMsg) eventBusMsgResult.getOriginalEventBusMsg();
        originalSender.tell(new ProjectCreateResultMsg(initialMsg.requester, initialMsg.initialEvent, eventBusMsgResult.getReply().getPayload(Project.class)), self());
    }

    public static class ProjectCreateMsg implements EventBusMsg {

        private final User requester;

        private final Event initialEvent;
        private final Project project;

        public ProjectCreateMsg(User requester, Event initialEvent, Project project, String projectConfigurationIdentifier) {

            this.requester = requester;
            this.initialEvent = initialEvent;
            this.project = project;
        }

        public Project getProject() {
            return project;
        }

        @Override
        public String eventType() {
            return Event.PROJECT_CREATION_REQUEST;
        }

        @Override
        public Serializable payload() {
            return project;
        }
    }

    public static class ProjectCreateResultMsg extends EventUserReplyMessage {

        private User requester;

        private final Project project;

        public ProjectCreateResultMsg(User requester, Event request, Project project) {
            super(requester, request, Event.PROJECT_CREATION_REPLY, project.getIdentifier());
            requireNonNull(project, "project must be defined.");
            this.requester = requester;
            this.project = project;
        }

        public User getRequester() {
            return requester;
        }

        public Project getProject() {
            return project;
        }
    }
}
