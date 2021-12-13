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

package com.google.appengine.tools.admin;

import com.google.appengine.tools.util.Action;
import com.google.appengine.tools.util.ActionsAndOptions;
import com.google.appengine.tools.util.Logging;
import com.google.appengine.tools.util.Option;
import com.google.appengine.tools.util.Parser;
import com.google.appengine.tools.util.Parser.ParseResult;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.EarHelper;
import com.google.apphosting.utils.config.EarInfo;
import com.google.apphosting.utils.config.StagingOptions;
import com.google.apphosting.utils.config.WebModule;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The command-line SDK tool for administration of App Engine applications.
 *
 */
public class AppCfg {
  private static final String OVERRIDE_MODULE_SHORT_ARG = "M";
  private static final String OVERRIDE_MODULE_LONG_ARG = "module";

  private AppCfgAction action;
  private String applicationDirectory;
  private String moduleName;
  private AppAdmin admin;

  // Here we have two variables keeping track of staging options, due to their layered nature.
  // The defaults constitute the bottom layer and the flags constitute the top layer. These are not
  // collapsed yet, because the appengine-web.xml <staging> options, the middle layer, isn't known
  // at this time. It is determined later on a per-application basis (yes, there can be multiple
  // applications within one invocation).
  private StagingOptions defaultStagingOptions = null;
  private StagingOptions.Builder stagingFlagsBuilder = null;

  private boolean disablePrompt = false;
  private File logFile = null;
  private String overrideAppId;
  private String overrideModule;
  private String overrideAppVersion;

  private boolean useAsyncQuickstart = false;
  private String runtime;
  private boolean allowAnyRuntime = false;
  private boolean disableUpdateCheck = false;
  private boolean failOnPrecompilationError = false;
  private boolean enableQuickstart = false;

  public static void main(String[] args) {
    Logging.initializeLogging();
    new AppCfg(args);
  }

  protected AppCfg(String[] cmdLineArgs) {
    this(new AppAdminFactory(), cmdLineArgs);
  }

  // TODO: Break up this constructor so this code is more
  // unit testable.  In particular, it should either be possible to
  // provide a mock AppAdminFactory or AppAdminFactory that returns
  // mocks, or it should be possible to test Action resolution without
  // referencing an AppAdminFactory.
  public AppCfg(AppAdminFactory factory, String[] cmdLineArgs) {
    Parser parser = new Parser();

    PrintWriter logWriter;
    defaultStagingOptions = StagingOptions.ANCIENT_DEFAULTS;
    stagingFlagsBuilder = StagingOptions.builder();

    try {
      logFile = File.createTempFile("appcfg", ".log");
      logWriter = new PrintWriter(new FileWriter(logFile), true);
    } catch (IOException e) {
      throw new RuntimeException("Unable to enable logging.", e);
    }

    try {
      ParseResult result =
          parser.parseArgs(actionsAndOptions.actions, actionsAndOptions.options, cmdLineArgs);
      action = (AppCfgAction) result.getAction();
      validateCommandLineForEar();
      try {
        result.applyArgs();
      } catch (IllegalArgumentException e) {
        e.printStackTrace(logWriter);
        System.out.println("Bad argument: " + e.getMessage());
        // We know which action they wanted, so just print the help
        // string for that one.
        System.out.println(action.getHelpString());
        System.exit(1);
      }


      // applicationDirectory is initialized via the result.applyArgs()
      // call above when the action requires it (and is left as null for
      // actions that don't use it).
      if (applicationDirectory != null) {
        File appDirectoryFile = new File(applicationDirectory);
        validateApplicationDirectory(appDirectoryFile);



        factory.setDefaultStagingOptions(defaultStagingOptions);
        factory.setStagingOptions(stagingFlagsBuilder.build());
        factory.setUseAsyncQuickstart(useAsyncQuickstart);
        // Order is important there: set the runtime first and then use Java8 or not.
        factory.setRuntime(runtime);
        factory.setAllowAnyRuntime(allowAnyRuntime);
        factory.setFailOnPrecompilationError(failOnPrecompilationError);
        factory.setQuickstart(enableQuickstart);
        System.out.println("Reading application configuration data...");
      }
      Iterable<Application> applications = readApplication();
      validateApplications(applications, action instanceof StagingAction);
      executeAction(factory, applications, logWriter, action);
      System.out.println("Success.");
      cleanStaging(applications);

    } catch (IllegalArgumentException e) {
      e.printStackTrace(logWriter);
      System.out.println("Bad argument: " + e.getMessage());
      printHelp();
      System.exit(1);
    } catch (AppEngineConfigException e) {
      e.printStackTrace(logWriter);
      System.out.println("Bad configuration: " + e.getMessage());
      if (e.getCause() != null) {
        System.out.println("  Caused by: " + e.getCause().getMessage());
      }
      printLogLocation();
      System.exit(1);
    } catch (Exception e) {
      System.out.println("Encountered a problem: " + e.getMessage());
      e.printStackTrace(logWriter);
      printLogLocation();
      System.exit(1);
    }
  }


  private void validateCommandLineForEar() {
    if (EarHelper.isEar(applicationDirectory)) {
      if (!action.isEarAction()) {
        throw new IllegalArgumentException(
            "The requested action does not support EAR configurations");
      }
      if (overrideModule != null) {
        throw new IllegalArgumentException(
            "With an EAR configuration "
                + "-"
                + OVERRIDE_MODULE_SHORT_ARG
                + "/"
                + "--"
                + OVERRIDE_MODULE_LONG_ARG
                + " is not allowed.");
      }
    }
  }

  private Iterable<Application> readApplication() throws IOException {
    ImmutableList.Builder<Application> resultBuilder = ImmutableList.builder();
    if (applicationDirectory != null) {
      if (EarHelper.isEar(applicationDirectory, false)) {
        EarInfo earInfo =
            EarHelper.readEarInfo(
                applicationDirectory,
                new File(Application.getSdkDocsDir(), "appengine-application.xsd"));
        String applicationId =
            overrideAppId != null
                ? overrideAppId
                : earInfo.getAppengineApplicationXml().getApplicationId();
        for (WebModule webModule : earInfo.getWebModules()) {
          System.out.println("Processing module " + webModule.getModuleName());
          resultBuilder.add(
              readWar(webModule.getApplicationDirectory().getAbsolutePath(), applicationId, null));
          String contextRootWarning =
              "Ignoring application.xml context-root element, for details see "
                  + "https://developers.google.com/appengine/docs/java/modules/#config";
          System.out.println(contextRootWarning);
        }
      } else {
        resultBuilder.add(readWar(applicationDirectory, overrideAppId, overrideModule));
      }
    }
    return resultBuilder.build();
  }

  /**
   * @param isStaging true if this is a staging action (for gcloud deployments)
   */
  private void validateApplications(Iterable<Application> applications, boolean isStaging) {
    for (Application application : applications) {
      if (isStaging) {
        application.validateForStaging();
      } else {
        application.validate();
      }
    }
  }

  private Application readWar(String warDirectory, String applicationIdOrNull,
      String moduleNameOrNull) throws IOException {
    Application application =
        Application.readApplication(warDirectory, applicationIdOrNull, moduleNameOrNull,
            overrideAppVersion);

    // install a default progress-reporting-only listener; note update
    // replaces this with a more informative one
    application.setListener(
        new UpdateListener() {
          @Override
          public void onProgress(UpdateProgressEvent event) {
            System.out.println(event.getPercentageComplete() + "% " + event.getMessage());
          }

          @Override
          public void onSuccess(UpdateSuccessEvent event) {
            System.out.println("Operation complete.");
          }

          @Override
          public void onFailure(UpdateFailureEvent event) {
            System.out.println(event.getFailureMessage());
          }
        });
    return application;
  }

  private void newAdmin(AppAdminFactory factory, Application application, PrintWriter logWriter,
                        boolean firstModule) {
    admin = factory.createAppAdmin(application, logWriter);
  }

  private void executeAction(
      AppAdminFactory factory,
      Iterable<Application> applications,
      PrintWriter logWriter,
      AppCfgAction executeMe) {
    try {
      if (applications.iterator().hasNext()) {
        Application firstApplication = null;
        for (Application application : applications) {
          if (firstApplication == null) {
            firstApplication = application;
          }
          // Disable JSP compilation for VM runtimes. The VM will compile the JSPs to
          // the JSP/servlet version running on the VM.
          boolean doJsps =
              (!application.getAppEngineWebXml().getUseVm()
                  && (!application.getAppEngineWebXml().isFlexible()));
          factory.setCompileJsps(doJsps);
          newAdmin(factory, application, logWriter, application.equals(firstApplication));
          moduleName = WebModule.getModuleName(application.getAppEngineWebXml());
          try {
            System.out.printf("%n%nBeginning interaction for module %s...%n", moduleName);
            executeMe.execute();
          } finally {
            moduleName = null;
          }
        }

      } else {
        admin = factory.createAppAdmin(null, logWriter);
        executeMe.execute();
      }
    } catch (AdminException ex) {
      System.out.println(ex.getMessage());
      ex.printStackTrace(logWriter);
      printLogLocation();
      System.exit(1);
    } finally {
      admin = null;
    }
  }

  private void cleanStaging(Iterable<Application> applications) throws IOException {
    for (Application application : applications) {
      if (application != null) {
        String moduleName = WebModule.getModuleName(application.getAppEngineWebXml());

        File stage = application.getStagingDir();
        if (stage == null) {
          System.out.printf(
              "Temporary staging directory was not needed, and not created for module %s%n",
              moduleName);
        } else {
          System.out.printf(
              "Temporary staging for module %s directory left in %s%n",
              moduleName, stage.getCanonicalPath());
        }
      }
    }
  }

  /**
   * Prints a uniform message to direct the user to the given logfile for
   * more information.
   */
  private void printLogLocation() {
    if (logFile != null) {
      System.out.println(
          "Please see the logs [" + logFile.getAbsolutePath() + "] for further information.");
    }
  }

  // Our built-in generally-applicable options, in the order they
  // should appear in the help string. These are the options which
  // are associated with most actions, including upload and
  // backends upload. They appear at the top of the general help
  // and they are the default options for an action.
  private static final List<String> generalOptionNamesInHelpOrder =
      ImmutableList.of("server", "application", "module", "version");

  // All of our built-in options, in the order they should appear
  // in the help string. If an option name is not here it will not
  // appear in the general help.
  private static final List<String> optionNamesInHelpOrder =
      ImmutableList.<String>builder()
          .addAll(generalOptionNamesInHelpOrder)
          .add(
              "enable_new_staging_defaults",
              "enable_jar_splitting",
              "jar_splitting_excludes",
              "disable_jar_jsps",
              "enable_jar_classes",
              "delete_jsps",
              "retain_upload_dir",
              "compile_encoding",
              "num_days",
              "severity",
              "include_all",
              "append",
              "num_runs",
              "force",
              "no_usage_reporting",
              "use_google_application_default_credentials",
              "service_account_json_key_file",
              "auto_update_dispatch")
          .build();

  // Our built-in actions, in the order they should appear in the help string
  private static final List<String> actionNamesInHelpOrder =
      ImmutableList.of(
          "help",
          "download_app",
          "request_logs",
          "rollback",
          "start_module_version",
          "stop_module_version",
          "update",
          "update_indexes",
          "update_cron",
          "update_queues",
          "update_dispatch",
          "update_dos",
          "version",
          "set_default_version",
          "cron_info",
          "resource_limits_info",
          "vacuum_indexes",
          "backends list",
          "backends update",
          "backends rollback",
          "backends start",
          "backends stop",
          "backends delete",
          "backends configure",
          "list_versions",
          "delete_version");

  private String helpText = null;

  private void printHelp() {
    if (helpText == null) {
      List<String> helpLines = new ArrayList<String>();
      helpLines.add("usage: AppCfg [options] <action> [<app-dir>] [<argument>]");
      helpLines.add("");
      helpLines.add("Action must be one of:");
      for (String actionName : actionsAndOptions.actionNames) {
        Action action = actionsAndOptions.getAction(actionName);
        if (action != null) {
          helpLines.add("  " + actionName + ": " + action.getShortDescription());
        }
      }
      helpLines.add("Use 'help <action>' for a detailed description.");
      helpLines.add("");
      helpLines.add("options:");
      for (String optionName : actionsAndOptions.optionNames) {
        Option option = actionsAndOptions.getOption(optionName);
        if (option != null) {
          helpLines.addAll(option.getHelpLines());
        }
      }
      helpText = Joiner.on("\n").join(helpLines);
    }
    System.out.println(helpText);
    System.out.println();
  }

  private final List<Option> builtInOptions =
      Arrays.asList(
          new Option("h", "help", true) {
            @Override
            public List<String> getHelpLines() {
              return ImmutableList.<String>of(
                  "  -h, --help            Show the help message and exit.");
            }

            @Override
            public void apply() {
              printHelp();
              System.exit(1);
            }
          },
          new Option("s", "server", false) {
            @Override
            public void apply() {
              // legacy, kept for potential external users.
            }
          },
          new Option("e", "email", false) {
            @Override
            public void apply() {
              // legacy, kept for potential external users.
            }
          },
          new Option("H", "host", false) {
            @Override
            public void apply() {
              // legacy, kept for potential external users.
            }
          },
          new Option("p", "proxy", false) {
            @Override
            public void apply() {
              // legacy, kept for potential external users.
            }
          },
          new Option(null, "proxy_https", false) {
            @Override
            public void apply() {
              // legacy, kept for potential external users.
            }
          },

          // Undocumented.
          new Option(null, "insecure", true) {
            @Override
            public void apply() {
              // legacy, kept for potential external users.
            }
          },

          // Undocumented.
          new Option(null, "ignore_bad_cert", true) {
            @Override
            public void apply() {
              // legacy, kept for potential external users.
            }
          },
          new Option(null, "no_cookies", true) {
            @Override
            public void apply() {
              // legacy, kept for potential external users.
            }
          },
          new Option("f", "force", true) {
            @Override
            public void apply() {
              // legacy, kept for potential external users.
            }
          },
          new Option("a", "append", true) {
            @Override
            public void apply() {
              // legacy, kept for potential external users.
            }
          },
          new Option("n", "num_days", false) {
            @Override
            public void apply() {
              // legacy, kept for potential external users.
            }
          },
          new Option(null, "num_runs", false) {
            @Override
            public void apply() {
              // legacy, kept for potential external users.
            }
          },
          new Option(null, "severity", false) {
            @Override
            public void apply() {
              // legacy, kept for potential external users.
            }
          },
          new Option(null, "include_all", true) {
            @Override
            public void apply() {
              // legacy, kept for potential external users.
            }
          },
          new Option(null, "sdk_root", false) {
            @Override
            public void apply() {
              // legacy, kept for potential external users.
            }
          },
          new Option(null, "enable_new_staging_defaults", true) {
            @Override
            public List<String> getHelpLines() {
              return ImmutableList.<String>of(
                  "  --enable_new_staging_defaults",
                  "                        Use new set of staging options defaults (recommended).",
                  "                        --enable_jar_splitting, --enable_jar_classes, and ",
                  "                        --delete_jsps will all be set to true.");
            }

            @Override
            public void apply() {
              defaultStagingOptions = StagingOptions.SANE_DEFAULTS;
            }
          },
          new Option(null, "disable_jar_jsps", true) {
            @Override
            public List<String> getHelpLines() {
              return ImmutableList.<String>of(
                  "  --disable_jar_jsps",
                  "                        Do not jar the classes generated from JSPs.");
            }

            @Override
            public void apply() {
              stagingFlagsBuilder.setJarJsps(Optional.of(false));
            }
          },
          new Option(null, "enable_jar_classes", true) {
            @Override
            public List<String> getHelpLines() {
              return ImmutableList.<String>of(
                  "  --enable_jar_classes",
                  "                        Jar the WEB-INF/classes content.");
            }

            @Override
            public void apply() {
              stagingFlagsBuilder.setJarClasses(Optional.of(true));
            }
          },
          new Option(null, "delete_jsps", true) {
            @Override
            public List<String> getHelpLines() {
              return ImmutableList.<String>of(
                  "  --delete_jsps",
                  "                        Delete the JSP source files after compilation.");
            }

            @Override
            public void apply() {
              stagingFlagsBuilder.setDeleteJsps(Optional.of(true));
            }
          },
          new Option(null, "use_async_quickstart", true) {
            @Override
            public List<String> getHelpLines() {
              return ImmutableList.<String>of(
                  "  --use_async_quickstart", "                        Use Servlet Async mode.");
            }

            @Override
            public void apply() {
              useAsyncQuickstart = true;
            }
          },
          new Option(null, "enable_jar_splitting", true) {
            @Override
            public List<String> getHelpLines() {
              return ImmutableList.<String>of(
                  "  --enable_jar_splitting",
                  "                        Split large jar files (> 10M) into smaller fragments.");
            }

            @Override
            public void apply() {
              stagingFlagsBuilder.setSplitJarFiles(Optional.of(true));
            }
          },
          new Option(null, "jar_splitting_excludes", false) {
            @Override
            public List<String> getHelpLines() {
              return ImmutableList.<String>of(
                  "  --jar_splitting_excludes=SUFFIXES",
                  "                        When --enable-jar-splitting is set, files that match",
                  "                        the list of comma separated SUFFIXES will be excluded",
                  "                        from all jars.");
            }

            @Override
            public void apply() {
              stagingFlagsBuilder.setSplitJarFilesExcludes(
                  Optional.of(ImmutableSortedSet.copyOf(getValue().split(","))));
            }
          },

          // Undocumented. Only intended for use by GCD.
          new Option(null, "disable_update_check", true) {
            @Override
            public void apply() {
              // NO Op.
            }
          },
          new Option(OVERRIDE_MODULE_SHORT_ARG, OVERRIDE_MODULE_LONG_ARG, false) {
            @Override
            public List<String> getHelpLines() {
              return ImmutableList.<String>of(
                  "  -"
                      + OVERRIDE_MODULE_SHORT_ARG
                      + " MODULE, --"
                      + OVERRIDE_MODULE_LONG_ARG
                      + "=MODULE",
                  "                        Override module from appengine-web.xml or app.yaml");
            }

            @Override
            public void apply() {
              overrideModule = getValue();
            }
          },
          new Option("V", "version", false) {
            @Override
            public List<String> getHelpLines() {
              return ImmutableList.<String>of(
                  "  -V VERSION, --version=VERSION",
                  "                        Override (major) version from appengine-web.xml "
                      + "or app.yaml");
            }

            @Override
            public void apply() {
              overrideAppVersion = getValue();
            }
          },
          new Option(null, "noisy", true) {
            @Override
            public List<String> getHelpLines() {
              return ImmutableList.<String>of(
                  "  --noisy",
                  "                        "
                      + "Log much more information about what the tool is doing.");
            }

            @Override
            public void apply() {
              Logger rootLogger = Logger.getLogger("");
              rootLogger.getHandlers()[0].setLevel(Level.ALL);
            }
          },

          // Undocumented.
          new Option("r", "runtime", false) {
            @Override
            public void apply() {
              runtime = getValue();
            }
          },

          // Undocumented.
          new Option("R", "allow_any_runtime", true) {
            @Override
            public void apply() {
              allowAnyRuntime = true;
            }
          },

          // Undocumented. Added to address b/10110321.
          new Option(null, "fail_on_precompilation_error", true) {
            @Override
            public void apply() {
              failOnPrecompilationError = true;
            }
          },
          new Option(null, "enable_quickstart", true) {

            @Override
            public List<String> getHelpLines() {
              return ImmutableList.<String>of(
                  "  --enable_quickstart",
                  "                        Use jetty quickstart to process servlet annotations");
            }

            @Override
            public void apply() {
              enableQuickstart = true;
            }
          });

  private final List<Action> builtInActions =
      Arrays.<Action>asList(
          // Note: The order of actions within this list is significant!

          new HelpAction(), new StagingAction());

  // A map from built-in option name to option
  private Map<String, Option> builtInOptionMap;

  // Fetch a list of built-in options by name
  private List<Option> builtInOptions(String... optionNames) {
    if (builtInOptionMap == null) {
      builtInOptionMap = new HashMap<String, Option>(builtInOptions.size());
      for (Option option : builtInOptions) {
        builtInOptionMap.put(option.getLongName(), option);
      }
    }
    List<Option> options = new ArrayList<Option>();
    for (String name : optionNames) {
      Option option = builtInOptionMap.get(name);
      if (option != null) {
        options.add(option);
      }
    }
    return options;
  }

  // Our Actions and Options. These include our built-in Actions and Options,
  // possibly modified and augmented by any SDK Runtime Plugins
  private final ActionsAndOptions actionsAndOptions = buildActionsAndOptions();

  private ActionsAndOptions buildActionsAndOptions() {
    ActionsAndOptions actionsAndOptions = getBuiltInActionsAndOptions();
    return actionsAndOptions;
  }

  /**
   * Builds the collection of built-in Actions and Options.
   */
  private ActionsAndOptions getBuiltInActionsAndOptions() {
    ActionsAndOptions actionsAndOptions = new ActionsAndOptions();
    actionsAndOptions.actions = builtInActions;
    actionsAndOptions.actionNames = actionNamesInHelpOrder;
    actionsAndOptions.options = builtInOptions;
    actionsAndOptions.optionNames = optionNamesInHelpOrder;
    actionsAndOptions.generalOptionNames = generalOptionNamesInHelpOrder;
    return actionsAndOptions;
  }

  abstract class AppCfgAction extends Action {

    AppCfgAction(String... names) {
      this(null, names);
    }

    AppCfgAction(List<Option> options, String... names) {
      super(options, names);
    }

    @Override
    protected void setArgs(List<String> args) {
      super.setArgs(args);
    }

    @Override
    public void apply() {
      if (getArgs().size() < 1) {
          throw new IllegalArgumentException(
              "Expected the application directory" + " as an argument after the action name.");
      }
      applicationDirectory = getArgs().get(0);
      validateCommandLineForEar();
    }


    public abstract void execute();

    @Override
    protected List<String> getHelpLines() {
      List<String> helpLines = new ArrayList<String>();
      helpLines.addAll(getInitialHelpLines());
      helpLines.add("");
      helpLines.add("Options:");
      for (String optionName : actionsAndOptions.generalOptionNames) {
        Option option = actionsAndOptions.getOption(optionName);
        if (option != null) {
          helpLines.addAll(option.getHelpLines());
        }
      }
      if (extraOptions != null) {
        for (Option option : extraOptions) {
          helpLines.addAll(option.getHelpLines());
        }
      }
      return helpLines;
    }

    /**
     * Returns a list of Strings to be displayed as the initial lines of a help text. Subclasses
     * should override this method.
     * <p>
     * The text returned by this method should describe the base Action without any of its options.
     * Text describing the options will be added in lines below this text.
     */
    protected List<String> getInitialHelpLines() {
      return ImmutableList.of();
    }

    protected boolean isEarAction() {
      return false;
    }


    protected void outputBackendsMessage() {
      System.out.println(
          "Warning: This application uses Backends, a deprecated feature that "
              + "has been replaced by Modules, which offers additional functionality. Please "
              + "convert your backends to modules as described at: https://developers.google.com/"
              + "appengine/docs/java/modules/converting.");
    }
  }

  class HelpAction extends AppCfgAction {
    HelpAction() {
      super("help");
      shortDescription = "Print help for a specific action.";
    }

    @Override
    public void apply() {
      if (getArgs().isEmpty()) {
        printHelp();
      } else {
        Action foundAction =
            Parser.lookupAction(actionsAndOptions.actions, getArgs().toArray(new String[0]), 0);
        if (foundAction == null) {
          System.out.println("No such command \"" + getArgs().get(0) + "\"\n\n");
          printHelp();
        } else {
          System.out.println(foundAction.getHelpString());
          System.out.println();
        }
      }
      System.exit(1);
    }

    @Override
    public void execute() {
      // never called: apply() exits, to skip "application directory" parsing
    }

    @Override
    protected List<String> getHelpLines() {
      return ImmutableList.of(
          "AppCfg help <command>", "", "Prints help about a specific command.", "");
    }
  }

  class StagingAction extends AppCfgAction {
    private File stagingDir;
    private boolean useRemoteResourceLimits = false;

    StagingAction() {
      super(
          builtInOptions(
              "enable_new_staging_defaults",
              "enable_jar_splitting",
              "use_remote_resource_limits",
              "quickstart",
              "jar_splitting_excludes",
              "retain_upload_dir",
              "compile_encoding",
              "disable_jar_jsps",
              "delete_jsps",
              "enable_jar_classes"),
          "stage");
      shortDescription = "Generate a deploy-ready application directory";
    }

    @Override
    public void apply() {
      super.apply();
      if (getArgs().size() != 2) {
        throw new IllegalArgumentException("Expected <app-dir> <staging-dir>");
      }

      stagingDir = new File(getArgs().get(1));
    }

    @Override
    public void execute() {
        admin.stageApplicationWithDefaultResourceLimits(stagingDir);

    }

    @Override
    protected List<String> getInitialHelpLines() {
      return ImmutableList.of(
          "AppCfg [options] stage <app-dir> <staging-dir>",
          "",
          "Generate a deploy-ready application directory");
    }
  }

  private void validateApplicationDirectory(File war) {
    if (!war.exists()) {
      System.out.println("Unable to find the webapp directory " + war);
      printHelp();
      System.exit(1);
    } else if (!war.isDirectory()) {
      System.out.println("appcfg only accepts webapp directories, not war files.");
      printHelp();
      System.exit(1);
    }
  }
}
