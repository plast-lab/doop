#!/usr/bin/env python
import os
import shutil
import sys

# This script should be executed from the root directory of Doop.
# Should be invoked as ./bin/scaler.py -i path_to/input.jar --rest --of --doop --flags application_name TST
# ----------------- configuration -------------------------
DOOP = './doop'  # './doopOffline'
PRE_ANALYSIS = 'context-insensitive'
MAIN_ANALYSIS = 'fully-guided-context-sensitive'
SEP = '\\t'

SCALER_MAIN = 'ptatoolkit.scaler.doop.Main'
SCALER_CACHE = 'scaler/cache'
SCALER_OUT = 'scaler/out'
#SCALER_TST = 30000000
DOOP_OUT = 'out'
# ---------------------------------------------------------
RESET = '\033[0m'
YELLOW = '\033[33m'
BOLD = '\033[1m'


def run_pre_analysis(args):
    args = [DOOP] + args
    args = args + ['-a', PRE_ANALYSIS]
    args = args + ['--scaler-pre']
    args = args + ['--id', APP + "-scaler-ci"]
    args = args + ['--Xstart-after-facts', APP+"-facts"]
    args = args + ['--Xsymlink-cached-facts']
    cmd = ' '.join(args)
    print YELLOW + BOLD + 'Running pre-analysis ...' + RESET
    # print cmd
    os.system(cmd)
       

def dump_required_doop_results(app, db_dir, dump_dir):
    INPUT = {
        'VPT': 'Stats_Simple_InsensVarPointsTo'
    }

    REQUIRED_INPUT = [
        'CALL_EDGE', 'CALLSITEIN', 'DECLARING_CLASS_ALLOCATION', 'INST_METHODS',
        'OBJECT_IN', 'SPECIAL_OBJECTS', 'THIS_VAR', 'VAR_IN', 'VPT',
    ]

    def dump_doop_results(db_dir, dump_dir, app, query):
        file_name = INPUT.get(query, query) + '.csv'
        from_path = os.path.join(db_dir, file_name)
        dump_path = os.path.join(dump_dir, '%s.%s' % (app, query))
        if not os.path.exists(dump_dir):
            os.mkdir(dump_dir)
        shutil.copyfile(from_path, dump_path)

    print 'Dumping doop analysis results %s...' % app
    for query in REQUIRED_INPUT:
        dump_doop_results(db_dir, dump_dir, app, query)


def run_scaler(app, cache_dir, out_dir):
    cmd = './gradlew scaler -Pargs=\''
    cmd += ' -sep %s ' % SEP
    cmd += ' -app %s ' % app
    cmd += ' -cache %s ' % cache_dir
    cmd += ' -out %s ' % out_dir
    cmd += ' -tst %d\'' % SCALER_TST
    print cmd
    os.system(cmd)

    scaler_file = os.path.join(SCALER_OUT, app, '%s-ScalerMethodContext-TST%d.facts' % (app, SCALER_TST))
    from_path = os.path.join(SCALER_OUT, app, '%s-ScalerMethodContext-TST%d.facts' % (app, SCALER_TST))
    dump_path = os.path.join(os.path.join(SCALER_CACHE, app, 'SpecialContextSensitivityMethod.facts'))
    shutil.copyfile(from_path, dump_path)
    return scaler_file


def run_main_analysis(args, scaler_file):
    args = [DOOP] + args
    args = args + ['-a', MAIN_ANALYSIS]
    args = args + ['--special-cs-methods', scaler_file]
    args = args + ['--id', APP + '-scaler-fully-guided']
    args = args + ['--Xstart-after-facts', APP+"-facts"]
    args = args + ['--Xsymlink-cached-facts']
    
    cmd = ' '.join(args)
    print YELLOW + BOLD + 'Running main (Scaler-guided) analysis ...' + RESET
    # print cmd
    os.system(cmd)


def run(args):
    if not os.path.exists("scaler"):
        os.mkdir("scaler")
    if not os.path.exists(SCALER_CACHE):
        os.mkdir(SCALER_CACHE)
    if not os.path.exists(os.path.join(SCALER_CACHE, APP)):
        os.mkdir(os.path.join(SCALER_CACHE, APP))
    run_pre_analysis(args)
    ci_analysis_database = os.path.join(DOOP_OUT, 'context-insensitive', APP + '-scaler-ci', 'database')
    dump_required_doop_results(APP, ci_analysis_database, os.path.join(SCALER_CACHE, APP))
    scaler_file = run_scaler(APP, os.path.join(SCALER_CACHE, APP), os.path.join(SCALER_OUT, APP))
    run_main_analysis(args, scaler_file)


def run_cached(args):
    scaler_file = run_scaler(APP, os.path.join(SCALER_CACHE, APP), os.path.join(SCALER_OUT, APP))
    run_main_analysis(args, scaler_file)


if __name__ == '__main__':
    global SCALER_TST
    global APP
    if sys.argv[-1] == 'cache':
        SCALER_TST = int(sys.argv[-2])
        APP = sys.argv[-3]
        run_cached(sys.argv[1:-3])
    else:
        SCALER_TST = int(sys.argv[-1])
        APP = sys.argv[-2]
        run(sys.argv[1:-2])
