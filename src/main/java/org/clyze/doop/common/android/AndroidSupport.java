package org.clyze.doop.common.android;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.clyze.doop.common.BasicJavaSupport;
import org.clyze.doop.common.JavaFactWriter;
import org.clyze.doop.common.Parameters;
import org.clyze.utils.AARUtils;

import java.io.IOException;
import java.util.*;

public abstract class AndroidSupport {

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
            if (i.endsWith(".apk") || i.endsWith(".aar")) {
                System.out.println("Processing application resources in " + i);
                try {
                    AppResources resources = processAppResources(i);
                    computedResources.put(i, resources);
                    processAppResources(i, resources, pkgs, rLinker);
                    resources.printManifestHeader();
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

}
