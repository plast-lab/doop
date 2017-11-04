package org.clyze.doop.soot.android;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.clyze.doop.soot.FactWriter;
import org.clyze.doop.soot.Main;
import org.clyze.doop.soot.PropertyProvider;
import org.clyze.doop.soot.SootParameters;
import org.clyze.utils.AARUtils;
import soot.*;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.resources.PossibleLayoutControl;
import soot.options.Options;
import static org.clyze.doop.soot.android.AndroidManifest.*;
import static soot.DexClassProvider.classesOfDex;
import static soot.jimple.infoflow.android.InfoflowAndroidConfiguration.CallbackAnalyzer.Fast;

public class AndroidSupport {

    String appInput;
    SootParameters sootParameters;
    SootMethod dummyMain;

    Set<String> appServices = new HashSet<>();
    Set<String> appActivities = new HashSet<>();
    Set<String> appContentProviders = new HashSet<>();
    Set<String> appBroadcastReceivers = new HashSet<>();
    Set<String> appCallbackMethods = new HashSet<>();
    Set<PossibleLayoutControl> appUserControls = new HashSet<>();

    public AndroidSupport(String appInput, SootParameters sootParameters) {
        this.appInput = appInput;
        this.sootParameters = sootParameters;
    }

    public SootMethod getDummyMain() {
        return dummyMain;
    }

    public void processInputs(PropertyProvider propertyProvider, Set<String> classesInApplicationJar, String androidJars, Set<String> tmpDirs) throws Exception {
        if (sootParameters.getRunFlowdroid()) {
            SetupApplication app = new SetupApplication(androidJars, appInput);
            Options.v().set_process_multiple_dex(true);
            Options.v().set_src_prec(Options.src_prec_apk);
            app.getConfig().setCallbackAnalyzer(Fast);
            String filename = Main.class.getClassLoader().getResource("SourcesAndSinks.txt").getFile();
            try {
                app.calculateSourcesSinksEntrypoints(filename);
            } catch (Exception ex) {
                System.err.println("calculateSourcesSinksEntrypoints() failed:");
                ex.printStackTrace();
            }
            dummyMain = app.getDummyMainMethod();
            if (dummyMain == null) {
                throw new RuntimeException("Dummy main null");
            }
        } else {
            List<String> inputsAndLibs = sootParameters.getInputsAndLibraries();
            // Map AAR files to their package name.
            Map<String, String> pkgs = new HashMap<>();

            // We merge the information from all manifests, not just
            // the application's. There are Android apps that use
            // components (e.g. activities) from AAR libraries.
            for (String i : inputsAndLibs) {
                if (i.endsWith(".apk") || i.endsWith(".aar")) {
                    System.out.println("Processing manifest in " + i);

                    AndroidManifest processMan = getAndroidManifest(i);
                    String appPackageName = processMan.getPackageName();
                    pkgs.put(i, appPackageName);

                    appServices.addAll(processMan.getServices());
                    appActivities.addAll(processMan.getActivities());
                    appContentProviders.addAll(processMan.getProviders());
                    appBroadcastReceivers.addAll(processMan.getReceivers());
                    try {
                        appCallbackMethods.addAll(processMan.getCallbackMethods());
                    } catch (IOException ex) {
                        System.err.println("Error while reading callbacks:");
                        ex.printStackTrace();
                    }
                    try {
                        appUserControls.addAll(processMan.getUserControls());
                    } catch (IOException ex) {
                        System.err.println("Error while reading user controls:");
                        ex.printStackTrace();
                    }

                    processMan.printManifestInfo();
                }
            }

            // Process the R.txt entries in AAR files.
            String generatedR = RLinker.linkRs(inputsAndLibs, pkgs, tmpDirs);
            if (generatedR != null) {
                System.out.println("Adding " + generatedR + "...");
                sootParameters.getLibraries().add(generatedR);
            }

            // If inputs are in AAR format, extract and use their JAR entries.
            sootParameters.setInputs(AARUtils.toJars(sootParameters.getInputs(), false, tmpDirs));
            sootParameters.setLibraries(AARUtils.toJars(sootParameters.getLibraries(), false, tmpDirs));

            Main.populateClassesInAppJar(sootParameters.getInputs().get(0), classesInApplicationJar, propertyProvider);
        }
    }

    public void addClasses(Set<String> classesInApplicationJar, Set<SootClass> classes, Scene scene) {
        if (appInput.endsWith(".apk")) {
            File apk = new File(appInput);
            System.out.println("Android mode, APK = " + appInput);
            try {
                Set<String> dexClasses = classesOfDex(apk);
                for (String className : dexClasses) {
                    SootClass c = scene.loadClass(className, SootClass.BODIES);
                    classes.add(c);
                }
                System.out.println("Classes found in apk: " + dexClasses.size());
            } catch (IOException ex) {
                System.err.println("Could not read dex classes in " + apk);
                ex.printStackTrace();
                return;
            }
        } else {
            // We support both AAR and JAR inputs, although JARs
            // are not ideal for analysis in Android, as they
            // don't contain AndroidManifest.xml.
            System.out.println("Android mode, input = " + appInput);
            Main.addClasses(classesInApplicationJar, classes, scene);
        }
    }

    public void writeComponents(FactWriter writer) {
        AndroidManifest processMan = getAndroidManifest(appInput);

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
        for (String s : appActivities) {
            writer.writeActivity(processMan.expandClassName(s));
        }

        for (String s : appServices) {
            writer.writeService(processMan.expandClassName(s));
        }

        for (String s : appContentProviders) {
            writer.writeContentProvider(processMan.expandClassName(s));
        }

        for (String s : appBroadcastReceivers) {
            writer.writeBroadcastReceiver(processMan.expandClassName(s));
        }

        for (String callbackMethod : appCallbackMethods) {
            writer.writeCallbackMethod(callbackMethod);
        }

        for (PossibleLayoutControl possibleLayoutControl : appUserControls) {
            writer.writeLayoutControl(possibleLayoutControl.getID(), possibleLayoutControl.getViewClassName(), possibleLayoutControl.getParentID());
            if (possibleLayoutControl.isSensitive()) {
                writer.writeSensitiveLayoutControl(possibleLayoutControl.getID(), possibleLayoutControl.getViewClassName(), possibleLayoutControl.getParentID());
            }
        }
    }
}
