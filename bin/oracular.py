#!/usr/bin/env python
import os
import shutil
import sys

# This script should be executed from the root directory of Doop.

# ----------------- configuration -------------------------
DOOP = './doop' # './doopOffline'
PRE_ANALYSIS_1 = 'context-insensitive'
PRE_ANALYSIS_2 = '2-object-sensitive+heap'
MAIN_ANALYSIS = 'oracular'
DATABASE = 'last-analysis'
SOUFFLE = 'souffle'

APP = 'temp'
SEP = '"\t"'

ORACULAR_CACHE = 'oracular/cache'
ORACULAR_OUT = 'oracular/out'
# ---------------------------------------------------------

RESET = '\033[0m'
YELLOW = '\033[33m'
BOLD = '\033[1m'

def runPreAnalyses(initArgs):
    if not os.path.exists('oracular'):
        os.mkdir('oracular')
    if not os.path.exists(ORACULAR_CACHE):
        os.mkdir(ORACULAR_CACHE)
    args = [DOOP] + initArgs
    args = args + ['-a', PRE_ANALYSIS_1]
    args = args + ["--Xoracular-heuristics"]
    cmd = ' '.join(args)
    print YELLOW + BOLD + 'Running pre-analyses #1 ' + PRE_ANALYSIS_1 + RESET
    # print cmd
    os.system(cmd)
    from_path = os.path.join(DATABASE, 'MethodWeight.csv')
    dump_path = os.path.join(ORACULAR_CACHE, '%s' % "InsensitiveSum.facts")
    shutil.copyfile(from_path, dump_path)


    print YELLOW + BOLD + 'Running pre-analyses #2 ' + PRE_ANALYSIS_2 + RESET
    args = [DOOP] + initArgs
    args = args + ['-a', PRE_ANALYSIS_2]
    args = args + ["--Xoracular-heuristics"]
    cmd = ' '.join(args)
    os.system(cmd)
    from_path = os.path.join(DATABASE, 'MethodWeight.csv')
    dump_path = os.path.join(ORACULAR_CACHE, '%s' % "SensitiveSum.facts")
    shutil.copyfile(from_path, dump_path)


def runOracularClassification():
    print YELLOW + BOLD + 'Running Oracular classification ' + RESET
    args = [SOUFFLE] + ['souffle-logic/addons/oracular/method-classification.dl'] +  ['--fact-dir={0}'.format(ORACULAR_CACHE)] + ['--output-dir={0}'.format(ORACULAR_CACHE)] + []
    cmd = ' '.join(args)
    print cmd
    os.system(cmd)

def runMainAnalysis(args, oracular_file):
    args = [DOOP] + args
    args = args + ['-a', MAIN_ANALYSIS]
    args = args + ['--special-cs-methods', oracular_file]
    cmd = ' '.join(args)
    print YELLOW + BOLD + 'Running main (Oracular-guided) analysis ...' + RESET
    # print cmd
    os.system(cmd)

def run(args):
    runPreAnalyses(args)
    runOracularClassification()
    runMainAnalysis(args, ORACULAR_CACHE+"/SpecialCSMethods.csv")

if __name__ == '__main__':
    run(sys.argv[1:])
