package io.kodokojo.brickmanager.service.marathon;

import io.kodokojo.brickmanager.BrickDataBuilder;
import io.kodokojo.commons.config.MarathonConfig;
import io.kodokojo.commons.model.PortDefinition;
import io.kodokojo.commons.model.Service;
import io.kodokojo.test.DataBuilder;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Ignore// WIP
public class MarathonServiceLocatorTest implements BrickDataBuilder, DataBuilder {


    private MarathonConfig marathonConfig;


    private MarathonServiceLocatorRestApi marathonServiceLocatorRestApiMocked;

    @Before
    public void setup() {
        marathonConfig = aMarathonConfig();
        marathonServiceLocatorRestApiMocked = mock(MarathonServiceLocatorRestApi.class);
    }

    @Test
    public void acceptance() {

        when(marathonServiceLocatorRestApiMocked.getAllApplications()).thenAnswer(invocation -> new MockedMarathonCall(this::fetchAllMarathonAppsResponse));
        when(marathonServiceLocatorRestApiMocked.getApplicationConfiguration(anyString())).thenAnswer(invocation -> new MockedMarathonCall(this::fetchTestbNexusMarathonAppsResponse));

        MarathonServiceLocator marathonServiceLocator = new MarathonServiceLocator(marathonConfig) {
            @Override
            protected MarathonServiceLocatorRestApi provideMarathonRestApi(MarathonConfig marathonConfig) {
                return marathonServiceLocatorRestApiMocked;
            }
        };

        Set<Service> services = marathonServiceLocator.getService("jenkins", "testb");
        assertThat(services).isNotEmpty();
        Service service = services.iterator().next();
        assertThat(service.getHost()).isEqualTo("10.100.75.227");
        assertThat(service.getName()).isEqualTo("testb-jenkins-8081");
        PortDefinition portDefinition = service.getPortDefinition();
        assertThat(portDefinition.getType()).isEqualTo(PortDefinition.Type.HTTPS);
        assertThat(portDefinition.getHostPort()).isEqualTo(35030);
        assertThat(portDefinition.getContainerPort()).isEqualTo(8081);
        assertThat(portDefinition.getServicePort()).isEqualTo(0);

    }


}
