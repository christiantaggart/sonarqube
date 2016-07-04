/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonar.server.platform;

import java.io.File;
import java.io.IOException;
import javax.annotation.CheckForNull;
import org.apache.commons.io.FileUtils;
import org.picocontainer.Startable;
import org.sonar.api.config.Settings;
import org.sonar.api.platform.Server;
import org.sonar.api.platform.ServerFileSystem;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.process.ProcessProperties;

/**
 * Introspect the filesystem and the classloader to get extension files at startup.
 *
 * @since 2.2
 */
public class DefaultServerFileSystem implements ServerFileSystem, Startable {

  private static final Logger LOGGER = Loggers.get(DefaultServerFileSystem.class);

  private final Server server;
  private final File homeDir;
  private final File tempDir;

  public DefaultServerFileSystem(Settings settings, Server server) {
    this.server = server;
    this.homeDir = new File(settings.getString(ProcessProperties.PATH_HOME));
    this.tempDir = new File(settings.getString(ProcessProperties.PATH_TEMP));
  }

  /**
   * for unit tests
   */
  public DefaultServerFileSystem(File homeDir, File tempDir, Server server) {
    this.homeDir = homeDir;
    this.tempDir = tempDir;
    this.server = server;
  }

  @Override
  public void start() {
    LOGGER.info("SonarQube home: " + homeDir.getAbsolutePath());

    File deployDir = getDeployDir();
    if (deployDir == null) {
      throw new IllegalArgumentException("Web app directory does not exist");
    }

    File deprecated = getDeprecatedPluginsDir();
    try {
      FileUtils.forceMkdir(deprecated);
      org.sonar.core.util.FileUtils.cleanDirectory(deprecated);
    } catch (IOException e) {
      throw new IllegalStateException("The following directory can not be created: " + deprecated.getAbsolutePath(), e);
    }
  }

  @Override
  public void stop() {
    // do nothing
  }

  @Override
  public File getHomeDir() {
    return homeDir;
  }

  @Override
  public File getTempDir() {
    return tempDir;
  }

  @CheckForNull
  public File getDeployDir() {
    return server.getDeployDir();
  }

  public File getDeployedPluginsDir() {
    return new File(getDeployDir(), "plugins");
  }

  public File getDownloadedPluginsDir() {
    return new File(getHomeDir(), "extensions/downloads");
  }

  public File getInstalledPluginsDir() {
    return new File(getHomeDir(), "extensions/plugins");
  }

  public File getBundledPluginsDir() {
    return new File(getHomeDir(), "lib/bundled-plugins");
  }

  public File getDeprecatedPluginsDir() {
    return new File(getHomeDir(), "extensions/deprecated");
  }

  public File getPluginIndex() {
    return new File(getDeployDir(), "plugins/index.txt");
  }

}
