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

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.kodokojo.brickmanager.*;
import io.kodokojo.commons.service.BrickFactory;
import io.kodokojo.commons.service.DefaultBrickFactory;
import io.kodokojo.commons.config.ApplicationConfig;
import io.kodokojo.commons.config.AwsConfig;
import io.kodokojo.commons.config.EmailConfig;
import io.kodokojo.commons.service.*;
import io.kodokojo.brickmanager.service.aws.Route53DnsManager;
import io.kodokojo.commons.service.aws.SesEmailSender;
import io.kodokojo.commons.service.dns.DnsManager;
import io.kodokojo.commons.service.dns.NoOpDnsManager;
import io.kodokojo.commons.service.repository.ProjectRepository;
import okhttp3.OkHttpClient;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

public class ServiceModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceModule.class);

    @Override
    protected void configure() {
        //  Nothing to do.
    }

    /*
    @Provides
    @Singleton
    BrickStateEventDispatcher provideBrickStateMsgDispatcher(ProjectRepository projectRepository) {
        BrickStateEventDispatcher dispatcher = new BrickStateEventDispatcher();
        //StoreBrickStateListener storeBrickStateListener = new StoreBrickStateListener(projectRepository);
        //dispatcher.addListener(storeBrickStateListener);
        return dispatcher;
    }
    */

    @Provides
    @Singleton
    DnsManager provideDnsManager(ApplicationConfig applicationConfig, AwsConfig awsConfig) {
        AWSCredentials credentials = getAwsCredentials();
        if (StringUtils.isNotBlank(System.getenv("NO_DNS")) || credentials == null) {
            LOGGER.info("Using NoOpDnsManager as DnsManger implementation");
            return new NoOpDnsManager();
        } else {
            LOGGER.info("Using Route53DnsManager as DnsManger implementation");
            return new Route53DnsManager(applicationConfig.domain(), Region.getRegion(Regions.fromName(awsConfig.region())));
        }
    }

    @Provides
    @Singleton
    BrickConfigurerProvider provideBrickConfigurerProvider(BrickUrlFactory brickUrlFactory, OkHttpClient
            httpClient) {
        return new DefaultBrickConfigurerProvider(brickUrlFactory, httpClient);
    }


    private AWSCredentials getAwsCredentials() {
        AWSCredentials credentials = null;
        try {
            DefaultAWSCredentialsProviderChain defaultAWSCredentialsProviderChain = new DefaultAWSCredentialsProviderChain();
            credentials = defaultAWSCredentialsProviderChain.getCredentials();
            if (credentials == null) {
                InstanceProfileCredentialsProvider instanceProfileCredentialsProvider = new InstanceProfileCredentialsProvider(true);
                credentials = instanceProfileCredentialsProvider.getCredentials();
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Unable to retrieve AWS credentials.", e);
        }
        return credentials;
    }

}
