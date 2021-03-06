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
package io.kodokojo.bdd.stage;

import com.tngtech.jgiven.Stage;

import io.kodokojo.test.DockerTestApplicationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrickStateNotificationGiven<SELF extends BrickStateNotificationGiven<?>> extends Stage<SELF> implements DockerTestApplicationBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrickStateNotificationGiven.class);
/*
    @ProvidedScenarioState
    public DockerTestSupport dockerTestSupport;


    //@ProvidedScenarioState
    //HttpEndpoint httpEndpoint;

    @ProvidedScenarioState
    ConfigurationStore configurationStore;

    @ProvidedScenarioState
    BootstrapConfigurationProvider bootstrapProvider;

    @ProvidedScenarioState
    String entryPointUrl;

    @ProvidedScenarioState
    UserInfo currentUser;

    @ProvidedScenarioState
    BrickManager brickManager;

    @ProvidedScenarioState
    DnsManager dnsManager;

    @ProvidedScenarioState
    HttpUserSupport httpUserSupport;

    public SELF kodokojo_is_started(@Hidden DockerTestSupport dockerTestSupport) {
        if (this.dockerTestSupport != null) {
            this.dockerTestSupport.stopAndRemoveContainer();
        }
        this.dockerTestSupport = dockerTestSupport;
        LOGGER.info("Pulling docker image redis:latest");
        this.dockerTestSupport.pullImage("redis:latest");
        DockerService service = startRedis(dockerTestSupport).get();

        brickManager = mock(BrickManager.class);
        bootstrapProvider = mock(BootstrapConfigurationProvider.class);
        dnsManager = mock(DnsManager.class);
        configurationStore = mock(ConfigurationStore.class);

        Mockito.when(bootstrapProvider.provideTcpPortEntrypoint(anyString(), anyString())).thenReturn(10022);

        try {
            HashSet<Service> services = new HashSet<>();
            services.add(new Service("test", "localhost", new PortDefinition(8080)));
            Mockito.when(brickManager.start(any(ProjectConfiguration.class), any(StackConfiguration.class), any(BrickConfiguration.class))).thenReturn(services);
            List<User> users = new ArrayList<>();
            users.add(new User("1234", "5678", "Jean-Pascal THIEYR", "jpthiery", "jpthiery@xebia.fr", "password", "sshKey"));
            Mockito.when(brickManager.configure(any(ProjectConfiguration.class), any(StackConfiguration.class), any(BrickConfiguration.class))).thenReturn(new BrickConfigurerData("test", "test", "localhost", "kodokojo.dev", users, users));
        } catch (BrickAlreadyExist | ProjectConfigurationException brickAlreadyExist) {
            fail(brickAlreadyExist.getMessage());
        }

        SecretKey tmpKey = null;
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            tmpKey = kg.generateKey();
        } catch (NoSuchAlgorithmException e) {
            fail(e.getMessage());
        }
        final SecretKey secreteKey = tmpKey;

        int port = TestUtils.getEphemeralPort();

        RedisUserRepository redisUserManager = new RedisUserRepository(secreteKey, service.getHost(), service.getPort());
        RedisProjectStore redisProjectStore = new RedisProjectStore(secreteKey, service.getHost(), service.getPort());
        RedisEntityStore redisEntityStore = new RedisEntityStore(secreteKey, service.getHost(), service.getPort());
        KeyPair keyPair = null;
        try {
            keyPair = RSAUtils.generateRsaKeyPair();
        } catch (NoSuchAlgorithmException e) {
            fail(e.getMessage());
        }
        BootstrapConfigurationProvider bootstrapConfigurationProvider = mock(BootstrapConfigurationProvider.class);
        Mockito.when(bootstrapConfigurationProvider.provideTcpPortEntrypoint(anyString(),anyString())).thenReturn(10022);

        SSLKeyPair caKey = SSLUtils.createSelfSignedSSLKeyPair("Fake CA", (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());
        Repository repository = new Repository(redisUserManager, redisUserManager, redisEntityStore, redisProjectStore);
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(UserRepository.class).toInstance(redisUserManager);
                bind(ProjectStore.class).toInstance(redisProjectStore);
                bind(EntityStore.class).toInstance(redisEntityStore);
                bind(Repository.class).toInstance(repository);
                bind(ProjectRepository.class).toInstance(repository);
                bind(EntityRepository.class).toInstance(repository);
                bind(BrickStateEventDispatcher.class).toInstance(new BrickStateEventDispatcher());
                bind(BrickManager.class).toInstance(brickManager);
                bind(DnsManager.class).toInstance(dnsManager);
                bind(ConfigurationStore.class).toInstance(configurationStore);
                bind(BrickFactory.class).toInstance(new DefaultBrickFactory());
                bind(EmailSender.class).toInstance(new NoopEmailSender());
                bind(BootstrapConfigurationProvider.class).toInstance(bootstrapConfigurationProvider);
              ////})).toInstance(new SimpleUserAuthenticator(redisUserManager));
                DefaultBrickUrlFactory brickUrlFactory = new DefaultBrickUrlFactory("kodokojo.dev");
                bind(BrickConfigurerProvider.class).toInstance(new DefaultBrickConfigurerProvider(brickUrlFactory, new OkHttpClient()));
                bind(ApplicationConfig.class).toInstance(new ApplicationConfig() {
                    @Override
                    public int port() {
                        return port;
                    }

                    @Override
                    public String domain() {
                        return "kodokojo.dev";
                    }

                    @Override
                    public String loadbalancerHost() {
                        return "192.168.22.3";
                    }

                    @Override
                    public int initialSshPort() {
                        return 10022;
                    }

                    @Override
                    public long sslCaDuration() {
                        return -1;
                    }

                    @Override
                    public Boolean userCreationRoutedInWaitingList() {
                        return false;
                    }
                });
                bind(EmailConfig.class).toInstance(new EmailConfig() {
                    @Override
                    public String smtpHost() {
                        return null;
                    }

                    @Override
                    public int smtpPort() {
                        return 0;
                    }

                    @Override
                    public String smtpUsername() {
                        return null;
                    }

                    @Override
                    public String smtpPassword() {
                        return null;
                    }

                    @Override
                    public String smtpFrom() {
                        return null;
                    }
                });
                bind(VersionConfig.class).toInstance(new VersionConfig() {
                    @Override
                    public String version() {
                        return "1.0.0";
                    }

                    @Override
                    public String gitSha1() {
                        return "123456";
                    }

                    @Override
                    public String branch() {
                        return "dev";
                    }
                });
                bind(SSLCertificatProvider.class).toInstance(new WildcardSSLCertificatProvider(caKey));
                bind(BrickUrlFactory.class).toInstance(brickUrlFactory);
            }



        });
        Injector akkaInjector = injector.createChildInjector(new AkkaModule());
        ActorSystem actorSystem = akkaInjector.getInstance(ActorSystem.class);
        ActorRef endpointActor = actorSystem.actorOf(EndpointActor.PROPS(akkaInjector), "endpoint");
        Launcher.INJECTOR = akkaInjector.createChildInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ActorRef.class).annotatedWith(Names.named(EndpointActor.NAME)).toInstance(endpointActor);
            }


        }, new HttpModule(), new UserEndpointModule(), new ProjectEndpointModule(), new BrickEndpointModule());
        entryPointUrl = "localhost:" + port;
        httpEndpoint = Launcher.INJECTOR.getInstance(HttpEndpoint.class);
        httpUserSupport = new HttpUserSupport(new OkHttpClient(), entryPointUrl);
        httpEndpoint.start();
        return self();
    }

    public SELF i_am_user_$(@Quoted String username) {
        //currentUser = StageUtils.createUser(username, Launcher.INJECTOR.getInstance(UserRepository.class), Launcher.INJECTOR.getInstance(EntityRepository.class));
        currentUser = httpUserSupport.createUser(null, username + "@kodokojo.dev");
        return self();
    }

    @AfterScenario
    public void tear_down() {
        if (httpEndpoint != null) {
            httpEndpoint.stop();
            httpEndpoint = null;
        }
    }
    */
}
