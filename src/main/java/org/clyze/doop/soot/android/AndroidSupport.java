package org.clyze.doop.soot.android;

import org.clyze.doop.common.ArtifactEntry;
import org.clyze.doop.common.JavaFactWriter;
import org.clyze.doop.common.Parameters;
import org.clyze.doop.common.android.AndroidManifest;
import org.clyze.doop.common.android.RLinker;
import org.clyze.doop.soot.*;
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

import static org.clyze.doop.common.android.AndroidManifest.getAndroidManifest;
import static soot.dexpler.DexFileProvider.DexContainer;
import static soot.jimple.infoflow.android.InfoflowAndroidConfiguration.CallbackAnalyzer.Fast;

public class AndroidSupport extends BasicJavaSupport {

    private String rOutDir;
    private Parameters parameters;
    private SootMethod dummyMain;

    protected Set<String> appServices = new HashSet<>();
    protected Set<String> appActivities = new HashSet<>();
    protected Set<String> appContentProviders = new HashSet<>();
    protected Set<String> appBroadcastReceivers = new HashSet<>();
    protected Set<String> appCallbackMethods = new HashSet<>();
    protected Set<PossibleLayoutControl> appUserControls = new HashSet<>();

    public AndroidSupport(Map<String, Set<ArtifactEntry>> artifactToClassMap, PropertyProvider propertyProvider, String rOutDir, Parameters parameters) {
        super(artifactToClassMap, propertyProvider);
        this.rOutDir = rOutDir;
        this.parameters = parameters;
    }

    public SootMethod getDummyMain() {
        return dummyMain;
    }

    public void processInputs(String androidJars, Set<String> tmpDirs) throws Exception {
        if ((parameters instanceof SootParameters) &&
	    ((SootParameters)parameters).getRunFlowdroid()) {
            String appInput = parameters.getInputs().get(0);
            SetupApplication app = new SetupApplication(androidJars, appInput);
            // TODO: fix this method call (refactored in newer
            // versions of FlowDroid):
            //app.getConfig().setCallbackAnalyzer(Fast);
            String filename = Objects.requireNonNull(Main.class.getClassLoader().getResource("SourcesAndSinks.txt")).getFile();
            try {
                // TODO: fix this method call (refactored in newer
                // versions of FlowDroid):
                // app.calculateSourcesSinksEntryPoints(filename);
                throw new RuntimeException("FlowDroid interface missing");
            } catch (Exception ex) {
                System.err.println("calculateSourcesSinksEntrypoints() failed:");
                ex.printStackTrace();
            }
            dummyMain = app.getDummyMainMethod();
            if (dummyMain == null) {
                throw new RuntimeException("Dummy main null");
            }
        } else {
            List<String> inputsAndLibs = parameters.getInputsAndLibraries();
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
                    AndroidManifest manifest = getAndroidManifest(i);
                    processManifest(i, manifest, pkgs, rLinker);
                    manifest.printManifestHeader();
                }
            }

            printCollectedComponents();

            SootParameters sootParameters = (SootParameters)parameters;
            // Produce a JAR of the missing R classes.
            String generatedR = rLinker.linkRs(rOutDir, tmpDirs);
            if (generatedR != null) {
                System.out.println("Adding " + generatedR + "...");
                sootParameters.getLibraries().add(generatedR);
            }

            // If inputs are in AAR format, extract and use their JAR entries.
            parameters.setInputs(AARUtils.toJars(parameters.getInputs(), false, tmpDirs));
            sootParameters.setLibraries(AARUtils.toJars(sootParameters.getLibraries(), false, tmpDirs));

            parameters.getInputs().subList(1, parameters.getInputs().size()).clear();
            populateClassesInAppJar(sootParameters);
        }
    }

    public void processManifest(String input, AndroidManifest manifest, Map<String, String> pkgs, RLinker rLinker) {
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

    @Override
    public void addAppClasses(Set<SootClass> classes, Scene scene) {
        for (String appInput : parameters.getInputs()) {
            if (appInput.endsWith(".apk")) {
                File apk = new File(appInput);
                System.out.println("Android mode, APK = " + appInput);
                String artifact = apk.getName();
                try {
                    List<DexContainer> listContainers = DexFileProvider.v().getDexFromSource(apk);
                    Set<Object> allDexClasses = new HashSet<>();
                    for (DexContainer dexContainer : listContainers) {
                        Set<? extends DexBackedClassDef> dexClasses = dexContainer.getBase().getClasses();
                        allDexClasses.addAll(dexClasses);
                        for (DexBackedClassDef dexBackedClassDef : dexClasses) {
                            String escapeClassName = Util.v().jimpleTypeOfFieldDescriptor((dexBackedClassDef).getType()).getEscapedName();
                            SootClass c = scene.loadClass(escapeClassName, SootClass.BODIES);
                            classes.add(c);
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

    public void writeComponents(JavaFactWriter writer, Parameters parameters) {
        for (String appInput : parameters.getInputs()) {
            AndroidManifest processMan;
            try {
                processMan = getAndroidManifest(appInput);
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
            writeExtraSensitiveControls(writer, parameters);
        }
    }

    // The extra sensitive controls are given as a String
    // "id1,type1,parentId1,id2,type2,parentId2,...".
    void writeExtraSensitiveControls(JavaFactWriter writer, Parameters parameters) {
        if (parameters._extraSensitiveControls.equals("")) {
            return;
        }
        String[] parts = parameters._extraSensitiveControls.split(",");
        int partsLen = parts.length;
        if (partsLen % 3 != 0) {
            System.err.println("List size (" + partsLen + ") not a multiple of 3: \"" + parameters._extraSensitiveControls + "\"");
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
