package org.clyze.doop.common.android;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.clyze.doop.common.BasicJavaSupport;
import org.clyze.doop.common.JavaFactWriter;
import org.clyze.doop.common.Parameters;
import org.clyze.utils.AARUtils;
import org.clyze.utils.JHelper;

public abstract class AndroidSupport {

    private static final String APKTOOL_HOME_ENV_VAR = "APKTOOL_HOME";

    protected final Parameters parameters;
    protected final BasicJavaSupport java;

    private final Collection<String> appServices = new HashSet<>();
    private final Collection<String> appActivities = new HashSet<>();
    private final Collection<String> appContentProviders = new HashSet<>();
    private final Collection<String> appBroadcastReceivers = new HashSet<>();
    private final Collection<String> appCallbackMethods = new HashSet<>();
    private final Set<LayoutControl> appUserControls = new HashSet<>();
    private final Map<String, AppResources> computedResources = new HashMap<>();

    private final Log logger;

    protected AndroidSupport(Parameters parameters, BasicJavaSupport java) {
        this.parameters = parameters;
        this.java = java;
        this.logger = LogFactory.getLog(getClass());
    }

    public void processInputs(Set<String> tmpDirs) {
        logger.debug("Processing inputs...");

        List<String> allInputs = parameters.getAllInputs();
        // Map AAR files to their package name.
        Map<String, String> pkgs = new HashMap<>();

        // R class linker used for AAR inputs.
        RLinker rLinker = RLinker.getInstance();

        // We merge the information from all resource files, not just
        // the application's. There are Android apps that use
        // components (e.g. activities) from AAR libraries.
        for (String i : allInputs) {
            boolean isApk = i.endsWith(".apk");
            if (isApk || i.endsWith(".aar")) {
                System.out.println("Processing application resources in " + i);
                if (isApk && parameters.getDecodeApk()) {
                    System.out.println("Decoding...");
                    decodeApk(new File(i), parameters.getOutputDir() + File.separator + "decoded");
                }
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

        printCollectedComponents();

        // Produce a JAR of the missing R classes.
        String generatedR = rLinker.linkRs(parameters._rOutDir, tmpDirs);
        if (generatedR != null) {
            System.out.println("Adding " + generatedR + "...");
            parameters.getDependencies().add(generatedR);
        }

        // If inputs are in AAR format, extract and use their JAR entries.
        parameters.setInputs(AARUtils.toJars(parameters.getInputs(), false, tmpDirs));
        parameters.setDependencies(AARUtils.toJars(parameters.getDependencies(), false, tmpDirs));

        List<String> inputs = parameters.getInputs();
        int inputsSize = inputs.size();
        if (inputsSize > 0)
            inputs.subList(1, inputsSize).clear();
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
            System.err.println("Error while reading callbacks:");
            ex.printStackTrace();
        }

        // Read R ids and then read controls. The
        // order is important (controls read R ids).
        if (rLinker != null)
            rLinker.readRConstants(input, pkgs.get(input));

        try {
            appUserControls.addAll(manifest.getUserControls());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void printCollectedComponents() {
        System.out.println("Collected components:");
        System.out.println("activities: " + appActivities);
        System.out.println("content providers: " + appContentProviders);
        System.out.println("broadcast receivers: " + appBroadcastReceivers);
        System.out.println("services: " + appServices);
        System.out.println("callbacks: " + appCallbackMethods);
        System.out.println("possible layout controls: " + appUserControls.size());
    }

    public void writeComponents(JavaFactWriter writer, Parameters parameters) {
        for (String appInput : parameters.getInputs()) {
            AppResources processMan = computedResources.get(appInput);
            if (processMan == null) {
                System.err.println("Warning: missing resources for " + appInput);
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
                writer.writeCallbackMethod(callbackMethod);

            for (LayoutControl control : appUserControls) {
                writer.writeLayoutControl(control.getID(), control.getViewClassName(), control.getParentID(), control.getAppRId(), control.getAndroidRId());
                if (control.isSensitive()) {
                    writer.writeSensitiveLayoutControl(control.getID(), control.getViewClassName(), control.getParentID());
                }
            }
        }
        writer.writeExtraSensitiveControls(parameters);
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
     * Decode an APK input using apktool. The tool may either be found
     * in environment variable (see constant APKTOOL_HOME_ENV_VAR) or
     * a default version of apktool may be provided as a build dependency.
     *
     * @param apk                        the APK
     * @param decodeDir                  the target directory to use as root
     */
    private static void decodeApk(File apk, String decodeDir) {
        if (new File(decodeDir).mkdirs())
            System.out.println("Created " + decodeDir);

        String apktoolHome = System.getenv(APKTOOL_HOME_ENV_VAR);
        String apkPath;
        String[] cmdArgs;
        try {
            apkPath = apk.getCanonicalPath();
            String outDir = decodeDir + File.separator + apkBaseName(apk.getName());
            // Don't use "-f" option of apktool, delete manually.
            FileUtils.deleteDirectory(new File(outDir));
            cmdArgs = new String[] { "d", apkPath, "-o", outDir };
        } catch (IOException ex) {
            System.err.println("Error: could not initialize inputs for apktool: " + ex.getMessage());
            return;
        }

        System.out.println("Decoding " + apkPath + " using apktool...");
        if (apktoolHome == null || (!(new File(apktoolHome)).exists())) {
            System.err.println("Invalid environment variable: " + APKTOOL_HOME_ENV_VAR + "=" + apktoolHome + ", using default apktool...");
            try {
                Class.forName("brut.apktool.Main").getDeclaredMethod("main").invoke(cmdArgs);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
                System.err.println("Error: could not find default apktool.");
            }
        } else {
            String[] cmd = new String[cmdArgs.length + 1];
            cmd[0] = apktoolHome + File.separator + "apktool";
            System.arraycopy(cmdArgs, 0, cmd, 1, cmdArgs.length);
            System.out.println("Command: " + String.join(" ", cmd));
            try {
                JHelper.runWithOutput(cmd, "APKTOOL");
            } catch (IOException ex) {
                System.err.println("Error: could not run apktool (" + APKTOOL_HOME_ENV_VAR + " = " + apktoolHome + ").");
            }
        }
    }

    private static String apkBaseName(String apkName) {
        if (!apkName.endsWith(".apk"))
            throw new RuntimeException("Cannot find base name of " + apkName);
         return apkName.substring(0, apkName.length()-4);
    }
}
