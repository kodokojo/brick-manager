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
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.brickmanager.service.actor.EndpointActor;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.model.ProjectConfiguration;
import io.kodokojo.commons.model.TypeChange;
import io.kodokojo.commons.model.UpdateData;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.service.actor.message.EventUserRequestMessage;
import io.kodokojo.commons.service.repository.ProjectFetcher;
import io.kodokojo.commons.service.repository.ProjectRepository;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static akka.event.Logging.getLogger;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

public class ListAndUpdateUserToProjectActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    private final ProjectFetcher projectFetcher;

    private ActorRef originalSender;

    private ProjectUpdaterMessages.ListAndUpdateUserToProjectMsg initialMsg;

    private int nbBrickReceived = 0;

    private int nbBrickExpected = 0;


    public static Props PROPS(ProjectFetcher projectFetcher) {
        requireNonNull(projectFetcher, "projectFetcher must be defined.");
        return Props.create(ListAndUpdateUserToProjectActor.class, projectFetcher);
    }

    public ListAndUpdateUserToProjectActor(ProjectRepository projectFetcher) {
        this.projectFetcher = projectFetcher;
        receive(ReceiveBuilder
                .match(ProjectUpdaterMessages.ListAndUpdateUserToProjectMsg.class, this::onListAndUpdateUser)
                .match(BrickUpdateUserActor.BrickUpdateUserResultMsg.class, this::onBrickUpdateUserResponse)
                .matchAny(this::unhandled).build());
    }


    private void onListAndUpdateUser(ProjectUpdaterMessages.ListAndUpdateUserToProjectMsg msg) {

        originalSender = sender();
        initialMsg = msg;

        LOGGER.debug("Receive Update user with id '{}' for user :{}", msg.getUserIdentifier(), msg.getUserUpdateData());

        List<UpdateData<User>> userList = Collections.singletonList(msg.getUserUpdateData());

        ActorRef endpoint = getContext().actorFor(EndpointActor.ACTOR_PATH);

        Set<String> projectConfigIds = projectFetcher.getProjectConfigIdsByUserIdentifier(msg.getUserIdentifier());
        if (isEmpty(projectConfigIds)) {
            LOGGER.info("No project configuration affected to user if '{}'.", msg.getUserIdentifier());
            ListAndUpdateUserToProjectResultMsg response = new ListAndUpdateUserToProjectResultMsg(initialMsg.getRequester(), initialMsg.originalEvent(), initialMsg, false);
            originalSender.tell(response, self());
            getContext().stop(self());
        } else {
            projectConfigIds.forEach(projectConfigId -> {
                LOGGER.debug("Fetching project for project configuration id {}", projectConfigId);
                ProjectConfiguration projectConfiguration = projectFetcher.getProjectConfigurationById(projectConfigId);
                projectConfiguration.getStackConfigurations().forEach(stackConfiguration -> {
                    stackConfiguration.getBrickConfigurations().forEach(brickConfiguration -> {
                        LOGGER.debug("Requesting update for brick '{}' on project {}.", brickConfiguration.getName(), projectConfiguration.getName());
                        BrickUpdateUserActor.BrickUpdateUserMsg brickUpdateUserMsg = new BrickUpdateUserActor.BrickUpdateUserMsg(TypeChange.UPDATE, userList, projectConfiguration, stackConfiguration, brickConfiguration);
                        endpoint.tell(brickUpdateUserMsg, self());
                        nbBrickExpected++;
                    });
                });
            });
        }
    }

    private void onBrickUpdateUserResponse(BrickUpdateUserActor.BrickUpdateUserResultMsg msg) {
        if (msg.isSuccess()) {
            nbBrickReceived++;
            if (nbBrickReceived == nbBrickExpected) {
                ListAndUpdateUserToProjectResultMsg response = new ListAndUpdateUserToProjectResultMsg(initialMsg.getRequester(), initialMsg.originalEvent(), initialMsg, true);
                originalSender.tell(response, self());
                getContext().stop(self());
            }
        } else {
            ListAndUpdateUserToProjectResultMsg response = new ListAndUpdateUserToProjectResultMsg(initialMsg.getRequester(), initialMsg.originalEvent(), initialMsg, false);
            originalSender.tell(response, self());
            getContext().stop(self());
        }
    }

    class ListAndUpdateUserToProjectResultMsg extends EventUserRequestMessage {

        private final ProjectUpdaterMessages.ListAndUpdateUserToProjectMsg request;

        private final boolean success;

        public ListAndUpdateUserToProjectResultMsg(User requester, Event eventRequest, ProjectUpdaterMessages.ListAndUpdateUserToProjectMsg request, boolean success) {
            super(requester, eventRequest);
            requireNonNull(request, "request must be defined.");
            this.request = request;
            this.success = success;
        }

        public ProjectUpdaterMessages.ListAndUpdateUserToProjectMsg getRequest() {
            return request;
        }

        public boolean isSuccess() {
            return success;
        }
    }

}
