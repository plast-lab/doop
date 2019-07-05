// This tests annotation handling. It includes a field, a variable and
// a method named "annotation" to test Doop's Jimple parser.

import java.lang.annotation.*;
import java.lang.reflect.*;

@TypeAnnotation(opt="test-opt-string1", annotation="test-annotation-string1", intVal=1, floatVal=2.0f, doubleVal=6.0001, shortVal=12, longVal=32_000_000_000_000l, classVal=Integer.class)
public class AnnotationsTest {

    @FieldAnnotation1(metadata="Metadata for the field.", inner=@FieldAnnotation2(metadata="Inner metadata."))
    static String field;

    public static void main(String[] args) {
        System.out.println("Annotation test.");

        try {
            Class<?> mainClass = AnnotationsTest.class;
            testClassAnnotations(mainClass);
            testFieldAnnotations(mainClass);
            testMethodAndParameterAnnotations(mainClass);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    static void testClassAnnotations(Class<?> mainClass) throws Exception {
        System.out.println("Class annotations:");
        Annotation[] cAnnotations = mainClass.getDeclaredAnnotations();
        for (Annotation annotation : cAnnotations) {
            String s = annotation.toString();
            System.out.println(annotation.toString());
        }
    }

    static void testFieldAnnotations(Class<?> mainClass) throws Exception {
        System.out.println("Field annotations:");
        Field fld = mainClass.getDeclaredField("field");
        Annotation[] fAnnotations = fld.getDeclaredAnnotations();
        for (Annotation annotation : fAnnotations) {
            String s = annotation.toString();
            System.out.println(annotation.toString());
        }
    }

    static void testMethodAndParameterAnnotations(Class<?> mainClass) throws Exception {
        System.out.println("Method annotations:");
        Method meth = mainClass.getDeclaredMethod("annotation", String.class);
        Annotation[] mAnnotations = meth.getDeclaredAnnotations();
        for (Annotation annotation : mAnnotations) {
            String s = annotation.toString();
            System.out.println(annotation.toString());
        }
        Annotation[][] pAnnotations = meth.getParameterAnnotations();
        System.out.println("Parameter annotations:");
        for (Annotation[] annotations : pAnnotations) {
            for (Annotation annotation : annotations) {
                String s = annotation.toString();
                System.out.println(annotation.toString());
            }
        }
    }

    @MethodAnnotation(opt="test-opt-string2", annotation="test-annotation-string2")
    @java.lang.Deprecated
    public void annotation(@ParameterAnnotation(p1="args", p2=true) String annotation) {
        System.out.println("annotation() called for: " + annotation);
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface TypeAnnotation {
    String   opt();
    String   annotation();
    int      intVal();
    float    floatVal();
    double   doubleVal();
    short    shortVal();
    long     longVal();
    Class<?> classVal();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface MethodAnnotation {
    String opt();
    String annotation();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface FieldAnnotation1 {
    String metadata();
    FieldAnnotation2 inner();
}
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface FieldAnnotation2 {
    String metadata();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@interface ParameterAnnotation {
    String p1();
    boolean p2();
}
