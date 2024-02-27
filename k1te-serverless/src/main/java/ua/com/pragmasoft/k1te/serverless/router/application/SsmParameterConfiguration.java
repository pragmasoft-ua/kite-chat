/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.k1te.serverless.router.application;

import java.util.Set;

public interface SsmParameterConfiguration {

  Set<String> getAvailableParameters();

  Set<String> getDependentParameters();

  String getEnv();

  String getEnvDelimiter();

  String getConfigName();
}
