#!/usr/bin/env python3
# End-to-end Zipper driver for Doop, reconciled for the prebuilt jar from
# github.com/silverbullettt/zipper (local-dependencies/zipper.jar).
#
# Pipeline:
#   1. Doop context-insensitive pre-analysis (--Xzipper-pre) -> input relations
#   2. Zipper solver (via the `zipper` Gradle task) -> precision-critical methods
#   3. Doop main analysis guided by those methods (--Xzipper)
#
# Fixes vs the original python2 script (the old master jar it targeted is gone):
#   * python3; runs `./doop`/`./gradlew` which need JDK 17+ (export JAVA_HOME).
#   * the Gradle `zipper` task entry point is ptatoolkit.zipper.Main and is given
#     -pta ptatoolkit.zipper.doop.DoopPointsToAnalysis (this jar's Doop reader).
#   * -sep / -only-lib are dropped: this jar's Options rejects them and its reader
#     hardcodes ", " as the field separator, so the dumped relations are converted
#     from Doop's tab delimiter to ", " here instead.
#   * OBJ_TYPE is completed for objects that appear in the points-to set but carry
#     no type (e.g. <method type ...> constants), which would otherwise NPE.
#   * the pre-analysis database is located via the ./last-analysis symlink.
#
# Usage:
#   export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
#   bin/zipper.py -i app.jar --platform java_8 -a 2-object-sensitive+heap myapp

import os
import subprocess
import sys

DOOP = './doop'
PRE_ANALYSIS = 'context-insensitive'
ZIPPER_PTA = 'ptatoolkit.zipper.doop.DoopPointsToAnalysis'
WORK = 'zipper-work'
THREAD = 16

YELLOW, BOLD, RESET = '\033[33m', '\033[1m', '\033[0m'

# Relations the solver consumes. VPT/APP_METHODS come from the stats profile;
# the rest are emitted by souffle-logic/main/zipper-pre-analysis.dl.
SRC = {
    'APP_METHODS': 'Stats_Simple_Application_ReachableMethod',
    'VPT': 'Stats_Simple_InsensVarPointsTo',
}
QUERIES = ['APP_METHODS', 'VPT', 'ARRAY_LOAD', 'ARRAY_STORE', 'CALL_EDGE',
           'CALL_RETURN_TO', 'CALLSITEIN', 'DIRECT_SUPER_TYPE', 'INST_CALL_RECV',
           'INST_METHODS', 'INSTANCE_LOAD', 'INSTANCE_STORE', 'INTERPROCEDURAL_ASSIGN',
           'LOCAL_ASSIGN', 'METHOD_MODIFIER', 'OBJ_TYPE', 'OBJECT_ASSIGN', 'OBJECT_IN',
           'PARAMS', 'RET_VARS', 'SPECIAL_OBJECTS', 'THIS_VAR', 'VAR_IN']


def sh(cmd):
    print(YELLOW + BOLD + '$ ' + cmd + RESET)
    if subprocess.call(cmd, shell=True) != 0:
        sys.exit('FAILED: ' + cmd)


def strip_opts(args, opts):
    """Remove each `opt value` pair (e.g. -a xxx, --id yyy) from a doop arg list."""
    args = list(args)
    for opt in opts:
        while opt in args:
            i = args.index(opt)
            del args[i]          # the flag
            if i < len(args):
                del args[i]      # its value
    return args


def ensure_flags(args, flags):
    """Append boolean flags that aren't already present (project defaults)."""
    args = list(args)
    for f in flags:
        if f not in args:
            args.append(f)
    return args


def run_pre_analysis(app, doop_args):
    pre_id = app + '-zipper-ci'
    args = strip_opts(doop_args, ['-a', '--analysis', '--id'])
    args = ensure_flags(args, ['--light-reflection-glue', '--discover-main-methods'])
    args = [DOOP] + args + ['-a', PRE_ANALYSIS, '--Xzipper-pre',
                            '--stats', 'default', '--cache', '--id', pre_id]
    print(YELLOW + BOLD + 'Stage 1: Doop pre-analysis (--Xzipper-pre) ...' + RESET)
    sh(' '.join(args))
    db = os.path.realpath('last-analysis')
    if not os.path.isdir(db):
        sys.exit('no database at %s' % db)
    return db


def dump_and_convert(db, app):
    """Copy each relation into <cache>/<app>.<QUERY>, converting tab -> ', '."""
    cache = os.path.join(WORK, 'cache', app)
    os.makedirs(cache, exist_ok=True)
    for q in QUERIES:
        src = os.path.join(db, SRC.get(q, q) + '.csv')
        dst = os.path.join(cache, '%s.%s' % (app, q))
        if os.path.isfile(src):
            with open(src) as fi, open(dst, 'w') as fo:
                for line in fi:
                    fo.write(line.replace('\t', ', '))
        else:
            print('  WARN missing %s (query %s) -> empty' % (src, q))
            open(dst, 'w').close()
    _complete_obj_type(db, app, cache)
    return cache


def _complete_obj_type(db, app, cache):
    """Add OBJ_TYPE entries for objects referenced in VPT but left untyped."""
    typed = set()
    with open(os.path.join(db, 'OBJ_TYPE.csv')) as f:
        for line in f:
            typed.add(line.split('\t', 1)[0])
    missing, seen = [], set()
    with open(os.path.join(db, 'Stats_Simple_InsensVarPointsTo.csv')) as f:
        for line in f:
            o = line.split('\t', 1)[0]
            if o not in typed and o not in seen:
                seen.add(o)
                missing.append(o)
    if missing:
        with open(os.path.join(cache, '%s.OBJ_TYPE' % app), 'a') as fo:
            for o in missing:
                t = ('java.lang.invoke.MethodType'
                     if o.startswith('<method type ') else 'java.lang.Object')
                fo.write('%s, %s\n' % (o, t))
        print('  completed %d untyped objects in OBJ_TYPE' % len(missing))


def run_zipper(app, cache):
    out = os.path.join(WORK, 'out', app)
    os.makedirs(out, exist_ok=True)
    inner = '-pta %s -app %s -cache %s -out %s -thread %d' % (
        ZIPPER_PTA, app, cache, out, THREAD)
    print(YELLOW + BOLD + 'Stage 2: Zipper solver ...' + RESET)
    sh("./gradlew zipper -Pargs='%s'" % inner)
    zf = os.path.join(out, '%s-ZipperPrecisionCriticalMethod.facts' % app)
    if not os.path.isfile(zf):
        sys.exit('solver produced no %s' % zf)
    print('  %s (%d precision-critical methods)' % (zf, sum(1 for _ in open(zf))))
    return zf


def run_main_analysis(app, doop_args, zipper_file):
    args = strip_opts(doop_args, ['--id'])
    args = [DOOP] + args + ['--Xzipper', zipper_file, '--id', app + '-zipper-main']
    print(YELLOW + BOLD + 'Stage 3: Doop main analysis guided by Zipper ...' + RESET)
    sh(' '.join(args))


def run(app, doop_args):
    db = run_pre_analysis(app, doop_args)
    cache = dump_and_convert(db, app)
    zf = run_zipper(app, cache)
    run_main_analysis(app, doop_args, zf)
    print(YELLOW + BOLD + 'DONE. Guided database: ./last-analysis' + RESET)


def run_cached(app, doop_args):
    """Reuse an existing zipper-work/cache/<app> dump; skip the pre-analysis."""
    cache = os.path.join(WORK, 'cache', app)
    if not os.path.isdir(cache):
        sys.exit('no cached dump at %s (run without "cache" first)' % cache)
    zf = run_zipper(app, cache)
    run_main_analysis(app, doop_args, zf)


if __name__ == '__main__':
    if len(sys.argv) < 3:
        print('Usage: zipper.py DOOP_ARGS... APP [cache]')
        print('  e.g. zipper.py -i app.jar --platform java_8 '
              '-a 2-object-sensitive+heap myapp')
        print('  (needs JAVA_HOME on JDK 17+ for the Doop/Gradle stages)')
        sys.exit(1)
    if sys.argv[-1] == 'cache':
        run_cached(sys.argv[-2], sys.argv[1:-2])
    else:
        run(sys.argv[-1], sys.argv[1:-1])
