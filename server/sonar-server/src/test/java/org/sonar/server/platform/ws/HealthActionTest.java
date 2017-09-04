/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.platform.ws;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Test;
import org.sonar.api.server.ws.WebService;
import org.sonar.cluster.health.NodeDetails;
import org.sonar.cluster.health.NodeHealth;
import org.sonar.server.health.ClusterHealth;
import org.sonar.server.health.Health;
import org.sonar.server.health.HealthChecker;
import org.sonar.server.platform.WebServer;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.TestResponse;
import org.sonar.server.ws.WsActionTester;
import org.sonarqube.ws.WsSystem;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.api.utils.DateUtils.formatDateTime;
import static org.sonar.api.utils.DateUtils.parseDateTime;
import static org.sonar.cluster.health.NodeDetails.newNodeDetailsBuilder;
import static org.sonar.cluster.health.NodeHealth.newNodeHealthBuilder;
import static org.sonar.server.health.Health.GREEN;
import static org.sonar.server.health.Health.newHealthCheckBuilder;
import static org.sonar.test.JsonAssert.assertJson;

public class HealthActionTest {
  private final Random random = new Random();
  private HealthChecker healthChecker = mock(HealthChecker.class);
  private WebServer webServer = mock(WebServer.class);
  private WsActionTester underTest = new WsActionTester(new HealthAction(webServer, new HealthActionSupport(healthChecker)));

  @Test
  public void verify_definition() {
    WebService.Action definition = underTest.getDef();

    assertThat(definition.key()).isEqualTo("health");
    assertThat(definition.isPost()).isFalse();
    assertThat(definition.description()).isNotEmpty();
    assertThat(definition.since()).isEqualTo("6.6");
    assertThat(definition.isInternal()).isFalse();
    assertThat(definition.responseExample()).isNotNull();
    assertThat(definition.params()).isEmpty();
  }

  @Test
  public void verify_response_example() {
    when(webServer.isStandalone()).thenReturn(false);
    long time = parseDateTime("2015-08-13T23:34:59+0200").getTime();
    when(healthChecker.checkCluster())
      .thenReturn(
        new ClusterHealth(newHealthCheckBuilder()
          .setStatus(Health.Status.RED)
          .addCause("Application node app-1 is RED")
          .build(),
          ImmutableSet.of(
            newNodeHealthBuilder()
              .setStatus(NodeHealth.Status.RED)
              .addCause("foo")
              .setDetails(
                newNodeDetailsBuilder()
                  .setName("app-1")
                  .setType(NodeDetails.Type.APPLICATION)
                  .setHost("192.168.1.1")
                  .setPort(999)
                  .setStarted(time)
                  .build())
              .build(),
            newNodeHealthBuilder()
              .setStatus(NodeHealth.Status.YELLOW)
              .addCause("bar")
              .setDetails(
                newNodeDetailsBuilder()
                  .setName("app-2")
                  .setType(NodeDetails.Type.APPLICATION)
                  .setHost("192.168.1.2")
                  .setPort(999)
                  .setStarted(time)
                  .build())
              .build(),
            newNodeHealthBuilder()
              .setStatus(NodeHealth.Status.GREEN)
              .setDetails(
                newNodeDetailsBuilder()
                  .setName("es-1")
                  .setType(NodeDetails.Type.SEARCH)
                  .setHost("192.168.1.3")
                  .setPort(999)
                  .setStarted(time)
                  .build())
              .build(),
            newNodeHealthBuilder()
              .setStatus(NodeHealth.Status.GREEN)
              .setDetails(
                newNodeDetailsBuilder()
                  .setName("es-2")
                  .setType(NodeDetails.Type.SEARCH)
                  .setHost("192.168.1.4")
                  .setPort(999)
                  .setStarted(time)
                  .build())
              .build(),
            newNodeHealthBuilder()
              .setStatus(NodeHealth.Status.GREEN)
              .setDetails(
                newNodeDetailsBuilder()
                  .setName("es-3")
                  .setType(NodeDetails.Type.SEARCH)
                  .setHost("192.168.1.5")
                  .setPort(999)
                  .setStarted(time)
                  .build())
              .build())));

    TestResponse response = underTest.newRequest().execute();

    assertJson(response.getInput())
      .isSimilarTo(underTest.getDef().responseExampleAsString());
  }

  @Test
  public void request_returns_status_and_causes_from_HealthChecker_checkNode_method_when_standalone() {
    Health.Status randomStatus = Health.Status.values()[new Random().nextInt(Health.Status.values().length)];
    Health.Builder builder = newHealthCheckBuilder()
      .setStatus(randomStatus);
    IntStream.range(0, new Random().nextInt(5)).mapToObj(i -> RandomStringUtils.randomAlphanumeric(3)).forEach(builder::addCause);
    Health health = builder.build();
    when(healthChecker.checkNode()).thenReturn(health);
    when(webServer.isStandalone()).thenReturn(true);
    TestRequest request = underTest.newRequest();

    WsSystem.HealthResponse healthResponse = request.executeProtobuf(WsSystem.HealthResponse.class);
    assertThat(healthResponse.getHealth().name()).isEqualTo(randomStatus.name());
    assertThat(health.getCauses()).isEqualTo(health.getCauses());
  }

  @Test
  public void response_contains_status_and_causes_from_HealthChecker_checkCluster_when_standalone() {
    Health.Status randomStatus = Health.Status.values()[random.nextInt(Health.Status.values().length)];
    String[] causes = IntStream.range(0, random.nextInt(33)).mapToObj(i -> randomAlphanumeric(4)).toArray(String[]::new);
    Health.Builder healthBuilder = newHealthCheckBuilder()
      .setStatus(randomStatus);
    Arrays.stream(causes).forEach(healthBuilder::addCause);
    when(webServer.isStandalone()).thenReturn(false);
    when(healthChecker.checkCluster()).thenReturn(new ClusterHealth(healthBuilder.build(), emptySet()));

    WsSystem.HealthResponse clusterHealthResponse = underTest.newRequest().executeProtobuf(WsSystem.HealthResponse.class);
    assertThat(clusterHealthResponse.getHealth().name()).isEqualTo(randomStatus.name());
    assertThat(clusterHealthResponse.getCausesList())
      .extracting(WsSystem.Cause::getMessage)
      .containsOnly(causes);
  }

  @Test
  public void response_contains_information_of_nodes_when_clustered() {
    NodeHealth nodeHealth = randomNodeHealth();
    when(webServer.isStandalone()).thenReturn(false);
    when(healthChecker.checkCluster()).thenReturn(new ClusterHealth(GREEN, singleton(nodeHealth)));

    WsSystem.HealthResponse response = underTest.newRequest().executeProtobuf(WsSystem.HealthResponse.class);

    assertThat(response.getNodes().getNodesList())
      .hasSize(1);
    WsSystem.Node node = response.getNodes().getNodesList().iterator().next();
    assertThat(node.getHealth().name()).isEqualTo(nodeHealth.getStatus().name());
    assertThat(node.getCausesList())
      .extracting(WsSystem.Cause::getMessage)
      .containsOnly(nodeHealth.getCauses().stream().toArray(String[]::new));
    assertThat(node.getName()).isEqualTo(nodeHealth.getDetails().getName());
    assertThat(node.getHost()).isEqualTo(nodeHealth.getDetails().getHost());
    assertThat(node.getPort()).isEqualTo(String.valueOf(nodeHealth.getDetails().getPort()));
    assertThat(node.getStarted()).isEqualTo(formatDateTime(nodeHealth.getDetails().getStarted()));
    assertThat(node.getType().name()).isEqualTo(nodeHealth.getDetails().getType().name());
  }

  @Test
  public void response_sort_nodes_by_type_name_host_then_port_when_clustered() {
    // using created field as a unique identifier. pseudo random value to ensure sorting is not based on created field
    List<NodeHealth> nodeHealths = new ArrayList<>(Arrays.asList(
      randomNodeHealth(NodeDetails.Type.APPLICATION, "1_name", "1_host", 1, 99),
      randomNodeHealth(NodeDetails.Type.APPLICATION, "1_name", "2_host", 1, 85),
      randomNodeHealth(NodeDetails.Type.APPLICATION, "1_name", "2_host", 2, 12),
      randomNodeHealth(NodeDetails.Type.APPLICATION, "2_name", "1_host", 1, 6),
      randomNodeHealth(NodeDetails.Type.APPLICATION, "2_name", "1_host", 2, 30),
      randomNodeHealth(NodeDetails.Type.APPLICATION, "2_name", "2_host", 1, 75),
      randomNodeHealth(NodeDetails.Type.APPLICATION, "2_name", "2_host", 2, 258),
      randomNodeHealth(NodeDetails.Type.SEARCH, "1_name", "1_host", 1, 963),
      randomNodeHealth(NodeDetails.Type.SEARCH, "1_name", "1_host", 2, 1),
      randomNodeHealth(NodeDetails.Type.SEARCH, "1_name", "2_host", 1, 35),
      randomNodeHealth(NodeDetails.Type.SEARCH, "1_name", "2_host", 2, 45),
      randomNodeHealth(NodeDetails.Type.SEARCH, "2_name", "1_host", 1, 39),
      randomNodeHealth(NodeDetails.Type.SEARCH, "2_name", "1_host", 2, 28),
      randomNodeHealth(NodeDetails.Type.SEARCH, "2_name", "2_host", 1, 66),
      randomNodeHealth(NodeDetails.Type.SEARCH, "2_name", "2_host", 2, 77)));
    String[] expected = nodeHealths.stream().map(s -> formatDateTime(new Date(s.getDetails().getStarted()))).toArray(String[]::new);
    Collections.shuffle(nodeHealths);

    when(webServer.isStandalone()).thenReturn(false);
    when(healthChecker.checkCluster()).thenReturn(new ClusterHealth(GREEN, new HashSet<>(nodeHealths)));

    WsSystem.HealthResponse response = underTest.newRequest().executeProtobuf(WsSystem.HealthResponse.class);

    assertThat(response.getNodes().getNodesList())
      .extracting(WsSystem.Node::getStarted)
      .containsExactly(expected);
  }

  private NodeHealth randomNodeHealth() {
    NodeHealth.Builder builder = newNodeHealthBuilder()
      .setStatus(NodeHealth.Status.values()[random.nextInt(NodeHealth.Status.values().length)]);
    IntStream.range(0, random.nextInt(4)).mapToObj(i -> randomAlphabetic(5)).forEach(builder::addCause);
    return builder.setDetails(
      NodeDetails.newNodeDetailsBuilder()
        .setType(random.nextBoolean() ? NodeDetails.Type.APPLICATION : NodeDetails.Type.SEARCH)
        .setName(randomAlphanumeric(3))
        .setHost(randomAlphanumeric(4))
        .setPort(1 + random.nextInt(3))
        .setStarted(1 + random.nextInt(23))
        .build())
      .build();
  }

  private NodeHealth randomNodeHealth(NodeDetails.Type type, String name, String host, int port, long started) {
    NodeHealth.Builder builder = newNodeHealthBuilder()
      .setStatus(NodeHealth.Status.values()[random.nextInt(NodeHealth.Status.values().length)]);
    IntStream.range(0, random.nextInt(4)).mapToObj(i -> randomAlphabetic(5)).forEach(builder::addCause);
    return builder.setDetails(
      NodeDetails.newNodeDetailsBuilder()
        .setType(type)
        .setName(name)
        .setHost(host)
        .setPort(port)
        .setStarted(started)
        .build())
      .build();
  }

}
