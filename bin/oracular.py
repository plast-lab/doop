#!/usr/bin/env python
import os
import shutil
import sys
from collections import OrderedDict
from operator import itemgetter

# This script should be executed from the root directory of Doop.

# ----------------- configuration -------------------------
DOOP = './doop'  # './doopOffline'
PRE_ANALYSIS_1 = 'context-insensitive'
PRE_ANALYSIS_2 = '2-object-sensitive+heap'
MAIN_ANALYSIS = 'oracular'
DATABASE = 'last-analysis'
SOUFFLE = 'souffle'

APP = 'temp'
SEP = '"\t"'

ORACULAR_CACHE = 'oracular/cache'
ORACULAR_OUT = 'oracular/out'
TWO_OBJECT_THRESHOLD = 4.0
TWO_TYPE_THRESHOLD = 5.5
ONE_TYPE_THRESHOLD = 6
CI_THRESHOLD = 7
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


def runOracularClassification55():
    print YELLOW + BOLD + 'Running Oracular classification ' + RESET
    args = [SOUFFLE] + ['souffle-logic/addons/oracular/method-classification.dl'] + [
        '--fact-dir={0}'.format(ORACULAR_CACHE)] + ['--output-dir={0}'.format(ORACULAR_CACHE)] + []
    cmd = ' '.join(args)
    print cmd
    os.system(cmd)


def runOracularClassification():
    print YELLOW + BOLD + 'Running Oracular classification ' + RESET
    insens_file = open(ORACULAR_CACHE + "/InsensitiveSum.facts", 'r')
    sens_file = open(ORACULAR_CACHE + "/SensitiveSum.facts", 'r')
    insens_dict = {}
    sens_dict = {}
    method_ratio_dict = {}

    for line in insens_file:
        pieces = line.split('\t')
        method = pieces[0]
        weight = int(pieces[1])
        insens_dict.update({method: weight})

    print YELLOW + BOLD + 'INSENS methods ' + str(len(insens_dict.keys())) + RESET

    for line in sens_file:
        pieces = line.split('\t')
        method = pieces[0]
        weight = int(pieces[1])
        sens_dict.update({method: weight})

    print YELLOW + BOLD + 'SENS methods ' + str(len(sens_dict.keys())) + RESET


    insens_file.close()
    sens_file.close()
    missing_methods = 0

    special_cs_file = open(ORACULAR_CACHE + "/SpecialCSMethods.csv", "w")

    for method in insens_dict.keys():
        if insens_dict.get(method) == 0:
            method_ratio_dict.update({method: 0.0})
        else:
            if sens_dict.__contains__(method):
                insensWeight = insens_dict.get(method)
                sensWeight = sens_dict.get(method)
                ratio = float(sensWeight) / float(insensWeight)
                method_ratio_dict.update({method: ratio})
            else:
                special_cs_file.write(method + "\t" "2-object\n")
                missing_methods += 1

    print YELLOW + BOLD + "Missing methods: " + str(missing_methods)


    #method_ratio_list = sorted(method_ratio_dict.items(), key=lambda (k, v): v)

    print YELLOW + BOLD + 'Ratio methods ' + str(len(method_ratio_dict.keys())) + RESET


    method_ratio_sum = 0.0
    method_num = 0
    two_object_sensitive_methods = 0
    two_type_sensitive_methods = 0
    one_type_sensitive_methods = 0
    context_insensitive_methods = 0
    #print YELLOW + BOLD + "Method ratio list size: " + str(len(method_ratio_list))
    for method, ratio in sorted(method_ratio_dict.iteritems(), key=lambda (k, v): (v, k)):
        #print method + "\t" + str(ratio)
        method_ratio_sum += ratio
        method_num += 1
        method_ratio_average = float(method_ratio_sum) / float(method_num)

        #print YELLOW + BOLD + "Method num: " + str(method_num) + "Ratio average: " + str(method_ratio_average) + RESET

        if method_ratio_average <= TWO_OBJECT_THRESHOLD:
            special_cs_file.write(method + "\t" "2-object\n")
            two_object_sensitive_methods += 1
        elif method_ratio_average <= TWO_TYPE_THRESHOLD:
            special_cs_file.write(method + "\t" + "2-type\n")
            two_type_sensitive_methods += 1
        elif method_ratio_average <= ONE_TYPE_THRESHOLD:
            special_cs_file.write(method + "\t" + "1-type\n")
            one_type_sensitive_methods += 1
        else:
            special_cs_file.write(method + "\t" + "context-insensitive\n")
            context_insensitive_methods += 1

    special_cs_file.close()

    print YELLOW + BOLD + "2-object methods: " + str(two_object_sensitive_methods) + RESET
    print YELLOW + BOLD + "2-type methods: " + str(two_type_sensitive_methods) + RESET
    print YELLOW + BOLD + "1-type methods: " + str(one_type_sensitive_methods) + RESET
    print YELLOW + BOLD + "context-insensitive methods: " + str(context_insensitive_methods) + RESET


def runMainAnalysis(args, oracular_file):
    args = [DOOP] + args
    args = args + ['-a', MAIN_ANALYSIS]
    args = args + ['--special-cs-methods', oracular_file]
    cmd = ' '.join(args)
    print YELLOW + BOLD + 'Running main (Oracular-guided) analysis ...' + RESET
    # print cmd
    os.system(cmd)


def run(args):
    #runPreAnalyses(args)
    runOracularClassification()
    runMainAnalysis(args, ORACULAR_CACHE + "/SpecialCSMethods.csv")


if __name__ == '__main__':
    run(sys.argv[1:])
