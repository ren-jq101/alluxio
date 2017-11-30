/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package alluxio.cli.validation;

import alluxio.Configuration;
import alluxio.PropertyKey;
import org.apache.commons.cli.Options;

import java.util.Map;

/**
 * Abstract class for validating HDFS-related configurations.
 */
public class HdfsValidationTask extends AbstractValidationTask {
  /** Name of the environment variable to store the path to Hadoop config directory. */
  protected static final String HADOOP_CONF_DIR_ENV_VAR = "HADOOP_CONF_DIR";

  protected final String mProcess;

  /**
   * Constructor of {@link SecureHdfsValidationTask}.
   *
   * @param process type of the process on behalf of which this validation task is run
   */
  public HdfsValidationTask(String process) {
    mProcess = process.toLowerCase();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Options getOptions() {
    return new Options().addOption(HADOOP_CONF_DIR_OPTION);
  }

  @Override
  public boolean validate(Map<String, String> optionsMap) {
    if (!validateHdfsSettingParity(optionsMap)) {
      System.err.format("Hdfs setting do not match.");
      return false;
    }
    return true;
  }

  private boolean validateHdfsSettingParity(Map<String, String> optionsMap) {
    String serverHadoopConfDirPath;
    if (optionsMap.containsKey(HADOOP_CONF_DIR_OPTION.getOpt())) {
      serverHadoopConfDirPath = optionsMap.get(HADOOP_CONF_DIR_OPTION.getOpt());
    } else {
      serverHadoopConfDirPath = System.getenv(HADOOP_CONF_DIR_ENV_VAR);
    }
    if (serverHadoopConfDirPath == null) {
      System.err.println("Path to server-side hadoop configuration unspecified,"
          + " skipping validation for HDFS properties.");
      return true;
    }
    String serverCoreSiteFilePath = serverHadoopConfDirPath + "/core-site.xml";
    String serverHdfsSiteFilePath = serverHadoopConfDirPath + "/hdfs-site.xml";

    // If Configuration does not contain the key, then a {@link RuntimeException} will be thrown
    // before calling the {@link String#split} method.
    String[] clientHaoopConfFilePaths =
        Configuration.get(PropertyKey.UNDERFS_HDFS_CONFIGURATION).split(":");
    String clientCoreSiteFilePath = clientHaoopConfFilePaths[0];
    String clientHdfsSiteFilePath = clientHaoopConfFilePaths[1];
    if (clientCoreSiteFilePath == null || clientCoreSiteFilePath.isEmpty()
        || clientHdfsSiteFilePath == null || clientHdfsSiteFilePath.isEmpty()) {
      System.out.println("Cannot locate the client-side hadoop configurations,"
          + " skipping validation for HDFS properties.");
      return true;
    }
    return compareConfigurations(clientCoreSiteFilePath, serverCoreSiteFilePath)
        && compareConfigurations(clientHdfsSiteFilePath, serverHdfsSiteFilePath);
  }

  private boolean compareConfigurations(String clientConfigFilePath, String serverConfigFilePath) {
    ConfigurationFileParser parser = new ConfigurationFileParser();
    Map<String, String> serverCoreSiteProps = parser.parseXmlConfiguration(serverConfigFilePath);
    Map<String, String> clientCoreSiteProps = parser.parseXmlConfiguration(clientConfigFilePath);
    boolean matches = true;
    for (Map.Entry<String, String> prop : clientCoreSiteProps.entrySet()) {
      if (!serverCoreSiteProps.containsKey(prop.getKey())
          || !prop.getValue().equals(serverCoreSiteProps.get(prop.getKey()))) {
        matches = false;
        System.err.format("For %s, client has %s, but server has %s.%n",
            prop.getKey(), prop.getValue(), serverCoreSiteProps.get(prop.getKey()));
      }
    }
    if (!matches) {
      return false;
    }
    for (Map.Entry<String, String> prop : serverCoreSiteProps.entrySet()) {
      if (!clientCoreSiteProps.containsKey(prop.getKey())
          || !prop.getValue().equals(clientCoreSiteProps.get(prop.getKey()))) {
        matches = false;
        System.err.format("For %s, server has %s, but client has %s.%n",
            prop.getKey(), prop.getValue(), clientCoreSiteProps.get(prop.getKey()));
      }
    }
    return matches;
  }
}
