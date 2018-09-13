#!/usr/bin/env python
import os
import shutil
import sys

# This script should be executed from the root directory of Doop.

insens_method_weight_dict = {}
sens_method_weight_dict = {}
method_ratio_dict = {}
insens_method_cost_dict = {}
sens_method_cost_dict = {}
sorted_method_ratio_list = {}
ci_analysis_weight = 0

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

RESET = '\033[0m'
YELLOW = '\033[33m'
BOLD = '\033[1m'


def run_pre_analyses(init_args):
    if not os.path.exists('oracular'):
        os.mkdir('oracular')
    if not os.path.exists(ORACULAR_CACHE):
        os.mkdir(ORACULAR_CACHE)
    args = [DOOP] + init_args
    args = args + ['-a', PRE_ANALYSIS_1]
    args = args + ['--Xoracular-heuristics']
    cmd = ' '.join(args)
    print YELLOW + BOLD + 'Running pre-analyses #1 ' + PRE_ANALYSIS_1 + RESET
    # print cmd
    os.system(cmd)
    from_path = os.path.join(DATABASE, 'MethodWeight.csv')
    dump_path = os.path.join(ORACULAR_CACHE, '%s' % 'InsensMethodWeight.facts')
    shutil.copyfile(from_path, dump_path)

    from_path = os.path.join(DATABASE, 'MethodVPTCost.csv')
    dump_path = os.path.join(ORACULAR_CACHE, '%s' % 'InsensMethodCost.facts')
    shutil.copyfile(from_path, dump_path)

    print YELLOW + BOLD + 'Running pre-analyses #2 ' + PRE_ANALYSIS_2 + RESET
    args = [DOOP] + init_args
    args = args + ['-a', PRE_ANALYSIS_2]
    args = args + ['--Xoracular-heuristics']
    cmd = ' '.join(args)
    os.system(cmd)
    from_path = os.path.join(DATABASE, 'MethodWeight.csv')
    dump_path = os.path.join(ORACULAR_CACHE, '%s' % 'SensMethodWeight.facts')
    shutil.copyfile(from_path, dump_path)

    from_path = os.path.join(DATABASE, 'MethodVPTCost.csv')
    dump_path = os.path.join(ORACULAR_CACHE, '%s' % 'SensMethodCost.facts')
    shutil.copyfile(from_path, dump_path)


def run_oracular_analysis_classification():
    print YELLOW + BOLD + 'Running Oracular classification ' + RESET
    insens_weight_file = open(ORACULAR_CACHE + '/InsensMethodWeight.facts', 'r')
    sens_weight_file = open(ORACULAR_CACHE + '/SensMethodWeight.facts', 'r')
    insens_cost_file = open(ORACULAR_CACHE + '/InsensMethodCost.facts', 'r')
    sens_cost_file = open(ORACULAR_CACHE + '/SensMethodCost.facts', 'r')

    for line in insens_weight_file:
        pieces = line.split('\t')
        method = pieces[0]
        weight = int(pieces[1])
        insens_method_weight_dict.update({method: weight})

    print YELLOW + BOLD + 'INSENS methods ' + str(len(insens_method_weight_dict.keys())) + RESET
    insens_weight_file.close()

    for line in sens_weight_file:
        pieces = line.split('\t')
        method = pieces[0]
        weight = int(pieces[1])
        sens_method_weight_dict.update({method: weight})

    print YELLOW + BOLD + 'SENS methods ' + str(len(sens_method_weight_dict.keys())) + RESET
    sens_weight_file.close()

    for line in insens_cost_file:
        pieces = line.split('\t')
        method = pieces[0]
        cost = int(pieces[1])
        insens_method_cost_dict.update({method: cost})

    insens_cost_file.close()

    for line in sens_cost_file:
        pieces = line.split('\t')
        method = pieces[0]
        cost = int(pieces[1])
        sens_method_cost_dict.update({method: cost})

    sens_cost_file.close()

    global ci_analysis_weight
    for method_cost in insens_method_cost_dict.values():
        ci_analysis_weight += method_cost

    two_obj_analysis_weight = 0
    for method_cost in sens_method_cost_dict.values():
        two_obj_analysis_weight += method_cost

    print YELLOW + BOLD + 'context insensitive analysis weight: ' + str(ci_analysis_weight) + RESET
    print YELLOW + BOLD + '2 object sensitive analysis weight: ' + str(two_obj_analysis_weight) + RESET

    special_cs_file = open(ORACULAR_CACHE + '/SpecialCSMethods.csv', 'w')

    calculate_method_ratios(insens_method_weight_dict, sens_method_weight_dict, special_cs_file)

    global sorted_method_ratio_list
    sorted_method_ratio_list = sorted(method_ratio_dict.items(), key=lambda x: x[1])

    sorted_ratios_list = [e[1] for e in sorted_method_ratio_list]
    optimal_ratio_threshold = binary_search_threshold(sorted_ratios_list)

    print YELLOW + BOLD + 'optimal ratio threshold: ' + str(optimal_ratio_threshold) + RESET

    two_object_sensitive_methods = 0
    context_insensitive_methods = 0
    for method, ratio in sorted_method_ratio_list:

        if ratio <= optimal_ratio_threshold:
            special_cs_file.write(method + '\t' '2-object\n')
            two_object_sensitive_methods += 1
        else:
            special_cs_file.write(method + '\t' + 'context-insensitive\n')
            context_insensitive_methods += 1

    special_cs_file.close()

    print YELLOW + BOLD + '2-object methods: ' + str(two_object_sensitive_methods) + RESET
    print YELLOW + BOLD + 'context-insensitive methods: ' + str(context_insensitive_methods) + RESET


def calculate_analysis_threshold(ratio_threshold):
    analysis_weight = 0
    for method, ratio in sorted_method_ratio_list:
        if ratio <= ratio_threshold:
            if sens_method_cost_dict.__contains__(method):
                analysis_weight += sens_method_cost_dict.get(method)
        else:
            if insens_method_cost_dict.__contains__(method):
                analysis_weight += insens_method_cost_dict.get(method)
            else:
                print YELLOW + BOLD + 'Reachable method missing from insensitive: ' + method + RESET
    return analysis_weight


def calculate_method_ratios(insens_method_weight_dict, sens_method_weight_dict, special_cs_file):
    missing_methods = 0
    for method in insens_method_weight_dict.keys():
        if insens_method_weight_dict.get(method) == 0:
            if sens_method_weight_dict.__contains__(method):
                method_ratio_dict.update({method: 0.0})
            else:
                print 'Possibly non-reachable method: ' + method
        else:
            if sens_method_weight_dict.__contains__(method):
                insens_weight = insens_method_weight_dict.get(method)
                sens_weight = sens_method_weight_dict.get(method)
                ratio = float(sens_weight) / float(insens_weight)
                method_ratio_dict.update({method: ratio})
            else:
                special_cs_file.write(method + '\t' '2-object\n')
                missing_methods += 1


def binary_search_threshold(threshold_list):

    loop_count = 0
    value = 1.5

    if value < float(calculate_analysis_threshold(threshold_list[0]))/float(ci_analysis_weight):
        print float(calculate_analysis_threshold(threshold_list[0]))
        print YELLOW + BOLD + 'Optimal threshold found after ' + str(loop_count) + ' steps' + RESET
        return threshold_list[0]
    if value > float(calculate_analysis_threshold(threshold_list[len(threshold_list) - 1]))/float(ci_analysis_weight):
        print float(calculate_analysis_threshold(threshold_list[len(threshold_list) - 1]))
        print YELLOW + BOLD + 'Optimal threshold found after ' + str(loop_count) + ' steps' + RESET
        return threshold_list[len(threshold_list)-1]

    low = 0
    high = len(threshold_list) - 1

    while low <= high:
        mid = low + (high - low) / 2
        loop_count += 1

        if value < float(calculate_analysis_threshold(threshold_list[mid]))/float(ci_analysis_weight):
            high = mid - 1
        elif value > float(calculate_analysis_threshold(threshold_list[mid]))/float(ci_analysis_weight):
            low = mid + 1
        else:
            print YELLOW + BOLD + 'Optimal threshold found after ' + str(loop_count) + ' steps' + RESET
            return threshold_list[mid]

    print YELLOW + BOLD + 'Optimal threshold found after ' + str(loop_count) + ' steps' + RESET
    print YELLOW + BOLD + 'low 2-obj cost ' + str(calculate_analysis_threshold(threshold_list[low])) + RESET
    print YELLOW + BOLD + 'high 2-obj cost ' + str(calculate_analysis_threshold(threshold_list[high])) + RESET

    if (float(calculate_analysis_threshold(threshold_list[low]))/float(ci_analysis_weight) - value) < (value - float(calculate_analysis_threshold(threshold_list[high]))/float(ci_analysis_weight)):
        return threshold_list[low]
    else:
        return threshold_list[high]


def run_main_analysis(args, oracular_file):
    args = [DOOP] + args
    args = args + ['-a', MAIN_ANALYSIS]
    args = args + ['--special-cs-methods', oracular_file]
    cmd = ' '.join(args)
    print YELLOW + BOLD + 'Running main (Oracular-guided) analysis ...' + RESET
    # print cmd
    os.system(cmd)


def run(args):
    run_pre_analyses(args)
    run_oracular_analysis_classification()
    run_main_analysis(args, ORACULAR_CACHE + '/SpecialCSMethods.csv')


if __name__ == '__main__':
    run(sys.argv[1:])
