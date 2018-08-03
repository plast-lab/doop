#!/usr/bin/env python
import os
import shutil
import sys

# This script should be executed from the root directory of Doop.

# ----------------- configuration -------------------------
DOOP = './doop' # './doopOffline'
PRE_ANALYSIS = 'context-insensitive'
MAIN_ANALYSIS = 'scaler'
DATABASE = 'last-analysis'

APP = 'temp'
SEP = '"\t"'

SCALER_CP = ':'.join(['scaler/lib/scaler.jar', 'scaler/lib/guava-23.0.jar'])
SCALER_MAIN = 'ptatoolkit.scaler.doop.Main'
SCALER_CACHE = 'scaler/cache'
SCALER_OUT = 'scaler/out'
SCALER_TST = 30000000
# ---------------------------------------------------------

RESET = '\033[0m'
YELLOW = '\033[33m'
BOLD = '\033[1m'

def runPreAnalysis(args):
    args = [DOOP] + args
    args = args + ['-a', PRE_ANALYSIS]
    args = args + ['--scaler-pre']
    cmd = ' '.join(args)
    print YELLOW + BOLD + 'Running pre-analysis ...' + RESET
    # print cmd
    os.system(cmd)

def dumpRequiredDoopResults(app, db_dir, dump_dir):
    INPUT = {
        'VPT':'Stats_Simple_InsensVarPointsTo'
    }

    REQUIRED_INPUT = [
        'CALL_EDGE', 'CALLSITEIN', 'DECLARING_CLASS_ALLOCATION', 'INST_METHODS',
        'OBJECT_IN', 'SPECIAL_OBJECTS', 'THIS_VAR',  'VAR_IN', 'VPT',
    ]

    def dumpDoopResults(db_dir, dump_dir, app, query):
        file_name = INPUT.get(query, query) + '.csv'
        from_path = os.path.join(db_dir, file_name)
        dump_path = os.path.join(dump_dir, '%s.%s' % (app, query))
        if not os.path.exists(dump_dir):
            os.mkdir(dump_dir)
        shutil.copyfile(from_path, dump_path)
    
    print 'Dumping doop analysis results %s...' % app
    for query in REQUIRED_INPUT:
        dumpDoopResults(db_dir, dump_dir, app, query)

def runScaler(app, cache_dir, out_dir):
    cmd = 'java -Xmx48g '
    cmd += ' -cp %s ' % SCALER_CP
    cmd += SCALER_MAIN
    cmd += ' -sep %s ' % SEP
    cmd += ' -app %s ' % app
    cmd += ' -cache %s ' % cache_dir
    cmd += ' -out %s ' % out_dir
    cmd += ' -tst %d ' % SCALER_TST
    # print cmd
    os.system(cmd)

    scaler_file = os.path.join(out_dir, \
        '%s-ScalerMethodContext-TST%d.facts' % (app, SCALER_TST))
    return scaler_file

def runMainAnalysis(args, scaler_file):
    args = [DOOP] + args
    args = args + ['-a', MAIN_ANALYSIS]
    args = args + ['--scaler', scaler_file]
    cmd = ' '.join(args)
    print YELLOW + BOLD + 'Running main (Scaler-guided) analysis ...' + RESET
    # print cmd
    os.system(cmd)

def run(args):
    runPreAnalysis(args)
    dumpRequiredDoopResults(APP, DATABASE, SCALER_CACHE)
    scaler_file = runScaler(APP, SCALER_CACHE, SCALER_OUT)
    runMainAnalysis(args, scaler_file)

if __name__ == '__main__':
    run(sys.argv[1:])
