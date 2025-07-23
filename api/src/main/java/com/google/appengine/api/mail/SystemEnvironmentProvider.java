package com.google.appengine.api.mail;

/**
 * A simple wrapper around {@link System} to allow for easier testing.
 */
class SystemEnvironmentProvider {
  /**
   * Gets the value of the specified environment variable.
   * @param name the name of the environment variable
   * @return the string value of the variable, or {@code null} if the variable is not defined
   */
  public String getenv(String name) {
    return System.getenv(name);
  }

  /**
   * Gets the value of the specified environment variable, returning a default value if the
   * variable is not defined.
   * @param name the name of the environment variable
   * @param defaultValue the default value to return
   * @return the string value of the variable, or the default value if the variable is not defined
   */
  public String getenv(String name, String defaultValue) {
    String value = System.getenv(name);
    return value != null ? value : defaultValue;
  }
}
