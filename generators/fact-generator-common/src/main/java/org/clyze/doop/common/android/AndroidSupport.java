package org.clyze.doop.common.android;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.clyze.doop.common.*;
import org.clyze.doop.util.Resource;
import org.clyze.utils.ContainerUtils;
import org.clyze.utils.JHelper;

import static org.clyze.doop.util.Resource.APKTOOL_JAR;

public abstract class AndroidSupport {

    private static final String DECODE_DIR = "decoded";

    protected final Parameters parameters;
    protected final BasicJavaSupport java;

    private final Collection<String> appServices = new HashSet<>();
    private final Collection<String> appActivities = new HashSet<>();
    private final Collection<String> appContentProviders = new HashSet<>();
    private final Collection<String> appBroadcastReceivers = new HashSet<>();
    private final Collection<String> appCallbackMethods = new HashSet<>();
    private final Set<LayoutControl> appUserControls = new HashSet<>();
    private final Map<String, AppResources> computedResources = new HashMap<>();

    private final Logger logger;

    protected AndroidSupport(Parameters parameters, BasicJavaSupport java) {
        this.parameters = parameters;
        this.java = java;
        this.logger = Logger.getLogger(getClass());
    }

    public void processInputs(Set<String> tmpDirs) {
        logger.debug("Processing inputs...");

        List<String> allInputs = parameters.getAllInputs();
        // Map AAR files to their package name.
        Map<String, String> pkgs = new HashMap<>();

        // R class linker used for AAR inputs.
        RLinker rLinker = RLinker.getInstance();

        String decodeDir = parameters.getOutputDir() + File.separator + DECODE_DIR;

        // We merge the information from all resource files, not just
        // the application's. There are Android apps that use
        // components (e.g. activities) from AAR libraries.
        for (String i : allInputs) {
            boolean isApk = i.endsWith(".apk");
            if (isApk || i.endsWith(".aar")) {
                logger.info("Processing application resources in " + i);
                if (isApk && parameters.getDecodeApk()) {
                    logger.debug("Decoding...");
                    java.xmlRoots.add(decodeDir);
                    decodeApk(new File(i), decodeDir);
                }
                if (parameters._legacyAndroidProcessing) {
                    try {
                        AppResources resources = processAppResources(i);
                        computedResources.put(i, resources);
                        processAppResources(i, resources, pkgs, rLinker);
                        resources.printManifestInfo();
                    } catch (Exception ex) {
                        System.err.println("Resource processing failed: " + ex.getMessage());
                    }
                }
            }
        }

        if (parameters._legacyAndroidProcessing)
            printCollectedComponents();

        // Produce a JAR of the missing R classes.
        String generatedR = rLinker.linkRs(parameters._rOutDir, tmpDirs);
        if (generatedR != null) {
            logger.info("Adding " + generatedR + "...");
            parameters.getDependencies().add(generatedR);
        }

        // Process AAR inputs.
        parameters.processFatArchives(tmpDirs);

        List<String> inputs = parameters.getInputs();
        int inputsSize = inputs.size();
        if (inputsSize > 1) {
            logger.warn("WARNING: only the first input will be analyzed.");
            inputs.subList(1, inputsSize).clear();
        }
    }

    protected void processAppResources(String input, AppResources manifest, Map<String, String> pkgs, RLinker rLinker) {
        String appPackageName = manifest.getPackageName();
        pkgs.put(input, appPackageName);

        appServices.addAll(manifest.getServices());
        appActivities.addAll(manifest.getActivities());
        appContentProviders.addAll(manifest.getProviders());
        appBroadcastReceivers.addAll(manifest.getReceivers());
        try {
            appCallbackMethods.addAll(manifest.getCallbackMethods());
        } catch (IOException ex) {
            logger.error("Error while reading callbacks:");
            ex.printStackTrace();
        }

        // Read R ids and then read controls. The
        // order is important (controls read R ids).
        if (rLinker != null)
            rLinker.readRConstants(input, pkgs.get(input));

        appUserControls.addAll(manifest.getUserControls());
    }

    public void printCollectedComponents() {
        logger.info("Collected components:");
        logger.info("activities: " + appActivities);
        logger.info("content providers: " + appContentProviders);
        logger.info("broadcast receivers: " + appBroadcastReceivers);
        logger.info("services: " + appServices);
        logger.info("callbacks: " + appCallbackMethods);
        logger.info("possible layout controls: " + appUserControls.size());
    }

    public void writeComponents(JavaFactWriter writer) {
        for (String appInput : parameters.getInputs()) {
            AppResources processMan = computedResources.get(appInput);
            if (processMan == null) {
                System.err.println("WARNING: missing resources for " + appInput);
                continue;
            }

            if (processMan.getApplicationName() != null)
                writer.writeApplication(processMan.expandClassName(processMan.getApplicationName()));
            else {
                // If no application name, use Android's Application:
                // "The fully qualified name of an Application subclass
                // implemented for the application. ... In the absence of a
                // subclass, Android uses an instance of the base
                // Application class."
                // https://developer.android.com/guide/topics/manifest/application-element.html
                writer.writeApplication("android.app.Application");
            }
            String appPackageName = processMan.getPackageName();
            if (appPackageName != null)
                writer.writeAppPackage(appPackageName);

            for (String s : appActivities)
                writer.writeActivity(processMan.expandClassName(s));
            for (String s : appServices)
                writer.writeService(processMan.expandClassName(s));
            for (String s : appContentProviders)
                writer.writeContentProvider(processMan.expandClassName(s));
            for (String s : appBroadcastReceivers)
                writer.writeBroadcastReceiver(processMan.expandClassName(s));
            for (String callbackMethod : appCallbackMethods)
                writer.writeAndroidCallbackMethodName(callbackMethod);

            for (LayoutControl control : appUserControls) {
                writer.writeLayoutControl(control.getID(), control.getViewClassName(), control.getParentID(), control.getAppRId(), control.getAndroidRId());
                if (control.isSensitive()) {
                    writer.writeSensitiveLayoutControl(control.getID(), control.getViewClassName(), control.getParentID());
                }
            }
        }
    }

    // Parses Android manifests. Supports binary and plain-text XML
    // files (found in .apk and .aar files respectively).
    protected AppResources processAppResources(String archiveLocation) throws Exception {
        logger.debug("Processing app resources in " + archiveLocation);
        String path = archiveLocation.toLowerCase();
        if (path.endsWith(".apk"))
            return AppResourcesXML.fromAPK(archiveLocation);
        else if (path.endsWith(".aar"))
            return AppResourcesXML.fromAAR(archiveLocation);
        else
            throw new RuntimeException("Unknown archive format: " + archiveLocation);

    }

    /**
     * Decode an APK input using apktool.
     *
     * @param apk                        the APK
     * @param decodeDir                  the target directory to use as root
     */
    private void decodeApk(File apk, String decodeDir) {
        if (new File(decodeDir).mkdirs())
            logger.debug("Created " + decodeDir);

        String apkPath;
        String[] cmdArgs;
        try {
            apkPath = apk.getCanonicalPath();
            String outDir = decodeDir + File.separator + apkBaseName(apk.getName()) + "-sources";
            // Don't use "-f" option of apktool, delete manually.
            FileUtils.deleteDirectory(new File(outDir));
            cmdArgs = new String[] { "d", apkPath, "-o", outDir };
        } catch (IOException ex) {
            logger.error("Error: could not initialize inputs for apktool: " + ex.getMessage());
            return;
        }

        logger.debug("Decoding " + apkPath + " using apktool...");
        // First, check if the environment overrides the bundled apktool.
        final String APKTOOL_HOME_ENV_VAR = "APKTOOL_HOME";
        // Prefix for output lines of apktool.
        final String TAG = "APKTOOL";
        String apktoolHome = System.getenv(APKTOOL_HOME_ENV_VAR);
        try {
            if (apktoolHome != null) {
                logger.debug("Trying to use apktool in: " + apktoolHome);
                String[] cmd = new String[cmdArgs.length + 1];
                cmd[0] = apktoolHome + File.separator + "apktool";
                System.arraycopy(cmdArgs, 0, cmd, 1, cmdArgs.length);
                // Only show output in debug mode.
                JHelper.runWithOutput(cmd, parameters._debug ? TAG : null);
            } else {
                Resource.invokeResourceJar(AndroidSupport.class, logger, "APKTOOL", null, APKTOOL_JAR, cmdArgs);
            }
        } catch (IOException ex) {
            logger.error("Error: could not run apktool.");
            ex.printStackTrace();
        }
    }

    private static String apkBaseName(String apkName) {
        if (!apkName.endsWith(".apk"))
            throw new RuntimeException("Cannot find base name of " + apkName);
         return apkName.substring(0, apkName.length()-4);
    }
}
