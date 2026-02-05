/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.apphosting.utils.config;

import com.google.common.base.Preconditions;
import com.google.common.net.InetAddresses;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Parsed dos.xml file.
 *
 * Any additions to this class should also be made to the YAML
 * version in DosYamlReader.java.
 *
 */
public class DosXml {

  /**
   * Describes a single blacklist entry.
   */
  public static class BlacklistEntry {

    String subnet;
    String desc;

    /** Create an empty blacklist entry. */
    public BlacklistEntry() {
      desc = "";
      subnet = null;
    }

    /** Records the human-readable description of this blacklist entry. */
    public void setDescription(String description) {
      this.desc = description.replace('\n', ' ');
    }

    /** Records the subnet of this blacklist entry. */
    public void setSubnet(String subnet) {
      try {
        validateSubnet(subnet);
        this.subnet = subnet;
      } catch (IllegalArgumentException iae) {
        this.subnet = null;
        throw new AppEngineConfigException("subnet " + subnet + " failed to parse",
                                           iae.getCause());
      }
    }

    public String getSubnet() {
      return subnet;
    }

    public String getDescription() {
      return desc;
    }

    // Throws IllegalArgumentException (including possibly NumberFormatException) if subnet is
    // not a valid subnet specification.
    private static void validateSubnet(String subnet) {
      int netmask;
      InetAddress inetAddress;
      int slash = subnet.indexOf('/');
      if (slash > -1) {
        String ipAddress = subnet.substring(0, slash);
        inetAddress = InetAddresses.forString(ipAddress);
        String netmaskString = subnet.substring(slash + 1);
        netmask = Integer.parseInt(netmaskString);
      } else {
        inetAddress = InetAddresses.forString(subnet);
        netmask = 0;
      }
      Preconditions.checkArgument(netmask >= 0 && netmask <= inetAddress.getAddress().length * 8);
    }
  }

  private List<BlacklistEntry> blacklistEntries;

  /** Create an empty configuration object. */
  public DosXml() {
    blacklistEntries = new ArrayList<>();
  }

  /**
   * Puts a new blacklist entry into the list defined by the config file.
   *
   * @throws AppEngineConfigException if the previously-last blacklist entry is
   *    still incomplete.
   * @return the new entry
   */
  public BlacklistEntry addNewBlacklistEntry() {
    validateLastEntry();
    BlacklistEntry entry = new BlacklistEntry();
    blacklistEntries.add(entry);
    return entry;
  }

  public void addBlacklistEntry(BlacklistEntry entry) {
    validateLastEntry();
    blacklistEntries.add(entry);
    validateLastEntry();
  }

  /**
   * Check that the last blacklist entry defined is complete.
   * @throws AppEngineConfigException if it is not.
   */
  public void validateLastEntry() {
    if (blacklistEntries.size() == 0) {
      return;
    }
    BlacklistEntry last = blacklistEntries.get(blacklistEntries.size() - 1);
    if (last.getSubnet() == null) {
      throw new AppEngineConfigException("no subnet for blacklist");
    }
  }

  /**
   * Get the YAML equivalent of this dos.xml file.
   *
   * @return contents of an equivalent {@code dos.yaml} file.
   */
  public String toYaml() {
    StringBuilder builder = new StringBuilder("blacklist:\n");
    for (BlacklistEntry ent : blacklistEntries) {
      builder.append("- subnet: ").append(ent.getSubnet()).append("\n");
      if (!ent.getDescription().equals("")) {
        builder.append("  description: ").append(ent.getDescription()).append("\n");
      }
    }
    return builder.toString();
  }
}
