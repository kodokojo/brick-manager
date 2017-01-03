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
package io.kodokojo.bdd.feature;

import com.tngtech.jgiven.junit.ScenarioTest;
import io.kodokojo.bdd.stage.BrickStateNotificationGiven;
import io.kodokojo.bdd.stage.BrickStateNotificationThen;
import io.kodokojo.bdd.stage.BrickStateNotificationWhen;
import io.kodokojo.test.DockerIsRequire;
import io.kodokojo.test.DockerPresentMethodRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

@Ignore
public class BrickConfigurationStateEventNotificationIntTest extends ScenarioTest<BrickStateNotificationGiven<?>, BrickStateNotificationWhen<?>, BrickStateNotificationThen<?>> {

    @Rule
    public DockerPresentMethodRule dockerPresentMethodRule = new DockerPresentMethodRule();

    @Test
    @DockerIsRequire
    public void user_receive_all_notification_when_start_a_project() {
        //given().kodokojo_is_started(dockerPresentMethodRule.getDockerTestSupport())
        //.and().i_am_user_$("jpthiery");
        when().i_create_a_project_configuration_with_default_brick()
                .and().i_start_the_project();
        then().i_receive_all_notification();
    }

}
