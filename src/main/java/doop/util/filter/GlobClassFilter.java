package doop.util.filter;

import static doop.util.TypeUtils.*;
import java.io.File;


public class GlobClassFilter implements ClassFilter
{
    private final String patterns[];


    public GlobClassFilter(String glob)
    {
        this.patterns = glob.split(File.pathSeparator);
    }

    @Override
    public boolean matches(String className)
    {
        for (String pattern : patterns)
        {
            if (pattern.endsWith(".*")) {
                String pkg = pattern.substring(0, pattern.length() - 2);

                if (getPackageName(className).equalsIgnoreCase(pkg))
                    return true;
            }
            else if (pattern.endsWith(".**")) {
                String prefix = pattern.substring(0, pattern.length() - 2);

                if (className.startsWith(prefix))
                    return true;
            }
            else if (pattern.equals("*")) {
                return getPackageName(className).isEmpty();
            }
            else if (pattern.equals("**")) {
                return true;
            }
            else if (className.equalsIgnoreCase(pattern)) {
                return true;
            }
        }

        return false;
    }
}
