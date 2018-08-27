package org.clyze.doop.common.android;

import org.clyze.doop.common.BasicJavaSupport;
import org.clyze.doop.common.JavaFactWriter;
import org.clyze.doop.common.Parameters;
import org.clyze.utils.AARUtils;

import java.io.IOException;
import java.util.*;

public abstract class AndroidSupport {

    protected Parameters parameters;
    protected BasicJavaSupport java;

    protected Set<String> appServices = new HashSet<>();
    protected Set<String> appActivities = new HashSet<>();
    protected Set<String> appContentProviders = new HashSet<>();
    protected Set<String> appBroadcastReceivers = new HashSet<>();
    protected Set<String> appCallbackMethods = new HashSet<>();
    protected Set<LayoutControl> appUserControls = new HashSet<>();

    public AndroidSupport(Parameters parameters, BasicJavaSupport java) {
        this.parameters = parameters;
        this.java = java;
    }

    public void processInputs(Set<String> tmpDirs) throws Exception {
        List<String> inputsAndLibs = parameters.getInputsAndLibraries();
        // Map AAR files to their package name.
        Map<String, String> pkgs = new HashMap<>();

        // R class linker used for AAR inputs.
        RLinker rLinker = RLinker.getInstance();

        // We merge the information from all resource files, not just
        // the application's. There are Android apps that use
        // components (e.g. activities) from AAR libraries.
        for (String i : inputsAndLibs) {
            if (i.endsWith(".apk") || i.endsWith(".aar")) {
                System.out.println("Processing application resources in " + i);
                AppResources resources = processAppResources(i);
                processAppResources(i, resources, pkgs, rLinker);
                resources.printManifestHeader();
            }
        }

        printCollectedComponents();

        // Produce a JAR of the missing R classes.
        String generatedR = rLinker.linkRs(parameters._rOutDir, tmpDirs);
        if (generatedR != null) {
            System.out.println("Adding " + generatedR + "...");
            parameters.getLibraries().add(generatedR);
        }

        // If inputs are in AAR format, extract and use their JAR entries.
        parameters.setInputs(AARUtils.toJars(parameters.getInputs(), false, tmpDirs));
        parameters.setLibraries(AARUtils.toJars(parameters.getLibraries(), false, tmpDirs));

        parameters.getInputs().subList(1, parameters.getInputs().size()).clear();
    }

    public void processAppResources(String input, AppResources manifest, Map<String, String> pkgs, RLinker rLinker) {
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
            AppResources processMan;
            try {
                processMan = processAppResources(appInput);
            } catch (Exception ex) {
                System.err.println("Error processing manifest in: " + appInput);
                ex.printStackTrace();
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
            writer.writeExtraSensitiveControls(parameters);
        }
    }

    // Parses Android manifests. Supports binary and plain-text XML
    // files (found in .apk and .aar files respectively).
    public AppResources processAppResources(String archiveLocation) throws Exception {
        String path = archiveLocation.toLowerCase();
        if (path.endsWith(".apk"))
            return AppResourcesXML.fromAPK(archiveLocation);
        else if (path.endsWith(".aar"))
            return AppResourcesXML.fromAAR(archiveLocation);
        else
            throw new RuntimeException("Unknown archive format: " + archiveLocation);

    }

}
