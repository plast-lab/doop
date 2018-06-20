package org.clyze.doop.soot.android;

import org.clyze.doop.soot.ArtifactEntry;
import org.clyze.doop.soot.BasicJavaSupport;
import org.clyze.doop.soot.FactWriter;
import org.clyze.doop.soot.Main;
import org.clyze.doop.soot.PropertyProvider;
import org.clyze.doop.soot.SootParameters;
import org.clyze.utils.AARUtils;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.coffi.Util;
import soot.dexpler.DexFileProvider;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.resources.PossibleLayoutControl;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.clyze.doop.soot.android.AndroidManifest.getAndroidManifest;
import static soot.dexpler.DexFileProvider.*;
import static soot.jimple.infoflow.android.InfoflowAndroidConfiguration.CallbackAnalyzer.Fast;

public class AndroidSupport extends BasicJavaSupport {

    private String rOutDir;
    private SootParameters sootParameters;
    private SootMethod dummyMain;

    private Set<String> appServices = new HashSet<>();
    private Set<String> appActivities = new HashSet<>();
    private Set<String> appContentProviders = new HashSet<>();
    private Set<String> appBroadcastReceivers = new HashSet<>();
    private Set<String> appCallbackMethods = new HashSet<>();
    private Set<PossibleLayoutControl> appUserControls = new HashSet<>();
    private String extraSensitiveControls;

    public AndroidSupport(Set<String> classesInApplicationJar, Map<String, Set<ArtifactEntry>> artifactToClassMap, PropertyProvider propertyProvider, String rOutDir, SootParameters sootParameters, String extraSensitiveControls) {
        super(classesInApplicationJar, artifactToClassMap, propertyProvider);
        this.rOutDir = rOutDir;
        this.sootParameters = sootParameters;
        this.extraSensitiveControls = extraSensitiveControls;
    }

    public SootMethod getDummyMain() {
        return dummyMain;
    }

    public void processInputs(String androidJars, Set<String> tmpDirs) throws Exception {
        if (sootParameters.getRunFlowdroid()) {
            String appInput = sootParameters.getInputs().get(0);
            SetupApplication app = new SetupApplication(androidJars, appInput);
            app.getConfig().setCallbackAnalyzer(Fast);
            String filename = Objects.requireNonNull(Main.class.getClassLoader().getResource("SourcesAndSinks.txt")).getFile();
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

            // R class linker used for AAR inputs.
            RLinker rLinker = RLinker.getInstance();

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

                    // Read R ids and then read controls. The
                    // order is important (controls read R ids).
                    rLinker.readRConstants(i, pkgs.get(i));
                    appUserControls.addAll(processMan.getUserControls());

                    processMan.printManifestHeader();
                }
            }

            printCollectedComponents();

            // Produce a JAR of the missing R classes.
            String generatedR = rLinker.linkRs(rOutDir, tmpDirs);
            if (generatedR != null) {
                System.out.println("Adding " + generatedR + "...");
                sootParameters.getLibraries().add(generatedR);
            }

            // If inputs are in AAR format, extract and use their JAR entries.
            sootParameters.setInputs(AARUtils.toJars(sootParameters.getInputs(), false, tmpDirs));
            sootParameters.setLibraries(AARUtils.toJars(sootParameters.getLibraries(), false, tmpDirs));

            sootParameters.getInputs().subList(1, sootParameters.getInputs().size()).clear();
            populateClassesInAppJar(sootParameters.getInputs(), sootParameters.getLibraries());
        }
    }

    private void printCollectedComponents() {
        System.out.println("Collected components:");
        System.out.println("activities: " + appActivities);
        System.out.println("content providers: " + appContentProviders);
        System.out.println("broadcast receivers: " + appBroadcastReceivers);
        System.out.println("services: " + appServices);
        System.out.println("callbacks: " + appCallbackMethods);
        System.out.println("possible layout controls: " + appUserControls.size());
    }

    @Override
    public void addClasses(Set<SootClass> classes, Scene scene) {
        for (String appInput : sootParameters.getInputs()) {
            if (appInput.endsWith(".apk")) {
                File apk = new File(appInput);
                System.out.println("Android mode, APK = " + appInput);
                try {
                    List<DexContainer> listContainers = DexFileProvider.v().getDexFromSource(apk);
                    Set<Object> allDexClasses = new HashSet<>();
                    for (DexContainer dexContainer : listContainers) {
                        allDexClasses.addAll(dexContainer.getBase().getClasses());
                        for (Object dexBackedClassDef : allDexClasses) {
                            String escapeClassName = Util.v().jimpleTypeOfFieldDescriptor(((DexBackedClassDef) dexBackedClassDef).getType()).getEscapedName();
                            SootClass c = scene.loadClass(escapeClassName, SootClass.BODIES);
                            classes.add(c);
                            String artifact = apk.getName();
                            registerArtifactClass(artifact, escapeClassName, dexContainer.getDexName());
                        }
                    }
                    System.out.println("Classes found in apk: " + allDexClasses.size());
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
                addSootClasses(classesInApplicationJars, classes, scene);
            }
        }
    }

    public void writeComponents(FactWriter writer) {
        for (String appInput : sootParameters.getInputs()) {
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
            writeExtraSensitiveControls(writer);
        }
    }

    // The extra sensitive controls are given as a String
    // "id1,type1,parentId1,id2,type2,parentId2,...".
    void writeExtraSensitiveControls(FactWriter writer) {
        if (extraSensitiveControls.equals("")) {
            return;
        }
        String[] parts = extraSensitiveControls.split(",");
        int partsLen = parts.length;
        if (partsLen % 3 != 0) {
            System.err.println("List size (" + partsLen + ") not a multiple of 3: \"" + extraSensitiveControls + "\"");
            return;
        }
        for (int i = 0; i < partsLen; i += 3) {
            String control = parts[i] + "," + parts[i+1] + "," + parts[i+2];
            try {
                int controlId = Integer.parseInt(parts[i]);
                String typeId = parts[i+1].trim();
                int parentId  = Integer.parseInt(parts[i+2]);
                System.out.println("Adding sensitive layout control: " + control);
                writer.writeSensitiveLayoutControl(controlId, typeId, parentId);
            } catch (Exception ex) {
                System.err.println("Ignoring control: " + control);
            }
        }
    }
}
