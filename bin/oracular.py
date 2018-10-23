#!/usr/bin/env python
import os
import shutil
import sys

# This script should be executed from the root directory of Doop.

insens_method_weight_dict = dict()
sens_method_weight_dict = dict()
method_ratio_dict = dict()
insens_method_cost_dict = dict()
sens_method_cost_dict = dict()
sorted_method_ratio_list = dict()
method_dependency_dict = dict()
ci_analysis_weight = 0

# ----------------- configuration -------------------------
DOOP = './doop'  # './doopOffline'
PRE_ANALYSIS_1 = 'context-insensitive'
PRE_ANALYSIS_2 = '2-object-sensitive+heap'
MAIN_ANALYSIS = 'fully-guided-context-sensitive'
DATABASE = 'last-analysis'
SOUFFLE = 'souffle'

APP = 'temp'
SEP = '"\t"'

ORACULAR_CACHE = 'oracular/cache'
ORACULAR_OUT = 'oracular/out'
DOOP_OUT = 'out'
RESET = '\033[0m'
YELLOW = '\033[33m'
BOLD = '\033[1m'


def run_pre_analyses(init_args, app):
    if not os.path.exists('oracular'):
        os.mkdir('oracular')
    if not os.path.exists(ORACULAR_CACHE):
        os.mkdir(ORACULAR_CACHE)
    if not os.path.exists(os.path.join(ORACULAR_CACHE, app)):
        os.mkdir(os.path.join(ORACULAR_CACHE, app))

    args = [DOOP] + init_args
    args = args + ['-a', PRE_ANALYSIS_1]
    args = args + ['--Xoracular-heuristics']
    args = args + ['--Xstart-after-facts', app + "-facts"]
    args = args + ['--id', app + "-ci"]
    args = args + ['--Xsymlink-cached-facts']

    cmd = ' '.join(args)
    print(YELLOW + BOLD + 'Running pre-analyses #1 ' + PRE_ANALYSIS_1 + RESET)
    # print cmd
    os.system(cmd)
    ci_analysis_database = os.path.join(DOOP_OUT, 'context-insensitive', app + '-ci', 'database')
    ci_analysis_facts = os.path.join(DOOP_OUT, 'context-insensitive', app + '-ci', 'facts')
    from_path = os.path.join(ci_analysis_database, 'MethodWeight.csv')
    dump_path = os.path.join(ORACULAR_CACHE, app, '%s' % 'InsensMethodWeight.facts')
    shutil.copyfile(from_path, dump_path)

    from_path = os.path.join(ci_analysis_database, 'MethodVPTCost.csv')
    dump_path = os.path.join(ORACULAR_CACHE, app, '%s' % 'InsensMethodCost.facts')
    shutil.copyfile(from_path, dump_path)

    print(YELLOW + BOLD + 'Running pre-analyses #2 ' + PRE_ANALYSIS_2 + RESET)
    args = [DOOP] + init_args
    args = args + ['-a', PRE_ANALYSIS_2]
    args = args + ['--Xoracular-heuristics']
    args = args + ['--Xcontext-dependency-heuristic']
    args = args + ['--Xstart-after-facts', app + "-facts"]
    args = args + ['--id', app + '-2obj']
    args = args + ['--Xsymlink-cached-facts']

    cmd = ' '.join(args)
    print(cmd)
    os.system(cmd)
    two_obj_analysis_database = os.path.join(DOOP_OUT, '2-object-sensitive+heap', app + '-2obj', 'database')
    two_obj_analysis_facts = os.path.join(DOOP_OUT, '2-object-sensitive+heap', app + '-2obj', 'facts')
    from_path = os.path.join(two_obj_analysis_database, 'MethodWeight.csv')
    dump_path = os.path.join(ORACULAR_CACHE, app, '%s' % 'SensMethodWeight.facts')
    shutil.copyfile(from_path, dump_path)

    from_path = os.path.join(two_obj_analysis_database, 'MethodVPTCost.csv')
    dump_path = os.path.join(ORACULAR_CACHE, app, '%s' % 'SensMethodCost.facts')
    shutil.copyfile(from_path, dump_path)

    from_path = os.path.join(two_obj_analysis_database, 'MethodContextDependsOnMethod.csv')
    dump_path = os.path.join(ORACULAR_CACHE, app, '%s' % 'MethodContextDependsOnMethod.facts')
    shutil.copyfile(from_path, dump_path)

    # if not os.path.exists(os.path.join(ORACULAR_CACHE, app, 'facts')):
    #     shutil.copytree(two_obj_analysis_facts, os.path.join(ORACULAR_CACHE, app, 'facts'))


def run_oracular_analysis_classification(app, slowdown):
    print(YELLOW + BOLD + 'Running Oracular classification ' + RESET)
    insens_weight_file = open(os.path.join(ORACULAR_CACHE, app, 'InsensMethodWeight.facts'), 'r')
    sens_weight_file = open(os.path.join(ORACULAR_CACHE, app, 'SensMethodWeight.facts'), 'r')
    insens_cost_file = open(os.path.join(ORACULAR_CACHE, app, 'InsensMethodCost.facts'), 'r')
    sens_cost_file = open(os.path.join(ORACULAR_CACHE, app, 'SensMethodCost.facts'), 'r')
    method_dependency_file = open(os.path.join(ORACULAR_CACHE, app, 'MethodContextDependsOnMethod.facts'), 'r')

    for line in insens_weight_file:
        pieces = line.split('\t')
        method = pieces[0]
        weight = int(pieces[1])
        insens_method_weight_dict.update({method: weight})

    print(YELLOW + BOLD + 'INSENS methods ' + str(len(insens_method_weight_dict.keys())) + RESET)
    insens_weight_file.close()

    for line in sens_weight_file:
        pieces = line.split('\t')
        method = pieces[0]
        weight = int(pieces[1])
        sens_method_weight_dict.update({method: weight})

    print(YELLOW + BOLD + 'SENS methods ' + str(len(sens_method_weight_dict.keys())) + RESET)
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

    for line in method_dependency_file:
        pieces = line.split('\t')
        method = pieces[0]
        required_method = pieces[1].strip()
        if method_dependency_dict.__contains__(method):
            required_methods = method_dependency_dict.get(method)
            required_methods.add(required_method)
        else:
            method_dependency_dict.update({method: {required_method}})

    global ci_analysis_weight
    for method_cost in insens_method_cost_dict.values():
        ci_analysis_weight += method_cost

    two_obj_analysis_weight = 0
    for method_cost in sens_method_cost_dict.values():
        two_obj_analysis_weight += method_cost

    print(YELLOW + BOLD + 'context insensitive analysis weight: ' + str(ci_analysis_weight) + RESET)
    print(YELLOW + BOLD + '2 object sensitive analysis weight: ' + str(two_obj_analysis_weight) + RESET)

    special_cs_file = open(os.path.join(ORACULAR_CACHE, app, 'SpecialCSMethods.csv'), 'w')
    calculate_method_ratios(insens_method_weight_dict, sens_method_weight_dict, special_cs_file)

    global sorted_method_ratio_list
    sorted_method_ratio_list = sorted(method_ratio_dict.items(), key=lambda x: x[1])

    sorted_ratios_list = [e[1] for e in sorted_method_ratio_list]
    optimal_ratio_threshold = binary_search_threshold(sorted_ratios_list, slowdown)

    print(YELLOW + BOLD + 'optimal ratio threshold: ' + str(optimal_ratio_threshold) + RESET)

    two_object_sensitive_methods = 0
    context_insensitive_methods = 0
    calculated_methods = set()
    for method, ratio in sorted_method_ratio_list:
        if method not in calculated_methods:
            if ratio <= optimal_ratio_threshold:
                special_cs_file.write(method + '\t' '2-object\n')
                two_object_sensitive_methods += 1
                calculated_methods.add(method)
                if method_dependency_dict.__contains__(method):
                    required_methods = method_dependency_dict.get(method)
                    for required_method in required_methods:
                        if required_method not in calculated_methods:
                            special_cs_file.write(required_method + '\t' '2-object\n')
                            two_object_sensitive_methods += 1
                            calculated_methods.add(required_method)
            else:
                special_cs_file.write(method + '\t' + 'context-insensitive\n')
                context_insensitive_methods += 1

    special_cs_file.close()

    print(YELLOW + BOLD + '2-object methods: ' + str(two_object_sensitive_methods) + RESET)
    print(YELLOW + BOLD + 'context-insensitive methods: ' + str(context_insensitive_methods) + RESET)


def calculate_analysis_weight(ratio_threshold):
    analysis_weight = 0
    calculated_methods = set()
    for method, ratio in sorted_method_ratio_list:
        if method not in calculated_methods:
            if ratio <= ratio_threshold:
                if sens_method_cost_dict.__contains__(method):
                    analysis_weight += sens_method_cost_dict.get(method)
                    if method_dependency_dict.__contains__(method):
                        required_methods = method_dependency_dict.get(method)
                        for required_method in required_methods:
                            if required_method in calculated_methods:
                                continue
                            else:
                                if required_method not in calculated_methods:
                                    if sens_method_cost_dict.__contains__(required_method):
                                        analysis_weight += sens_method_cost_dict.get(required_method)
                                        calculated_methods.add(required_method)
                                    else:
                                        print(f'{YELLOW} {BOLD} Required 2-obj method missing from sensitive cost dict {required_method}')
            else:
                if insens_method_cost_dict.__contains__(method):
                    analysis_weight += insens_method_cost_dict.get(method)
                    calculated_methods.add(method)
                else:
                    print(YELLOW + BOLD + 'Reachable method missing from insensitive: ' + method + RESET)
    print(f'{YELLOW}{BOLD} Analysis weight: {analysis_weight} for threshold {ratio_threshold}')
    return analysis_weight


def calculate_method_ratios(insens_method_weight_dict, sens_method_weight_dict, special_cs_file):
    missing_methods = 0
    for method in insens_method_weight_dict.keys():
        if insens_method_weight_dict.get(method) == 0:
            if sens_method_weight_dict.__contains__(method):
                method_ratio_dict.update({method: 0.0})
            else:
                special_cs_file.write(method + '\t' '2-object\n')
                # print YELLOW + BOLD + 'Possibly non-reachable method in high precision given, 2-obj context: ' + method + RESET
                missing_methods += 1
        else:
            if sens_method_weight_dict.__contains__(method):
                insens_weight = insens_method_weight_dict.get(method)
                sens_weight = sens_method_weight_dict.get(method)
                ratio = float(sens_weight) / float(insens_weight)
                method_ratio_dict.update({method: ratio})
            else:
                special_cs_file.write(method + '\t' '2-object\n')
                # print YELLOW + BOLD + 'Possibly non-reachable method in high precision given, 2-obj context: ' + method + RESET
                missing_methods += 1
    print(YELLOW + BOLD + 'Total methods possibly unreachable in high precision: ' + str(missing_methods) + RESET)


def binary_search_threshold(threshold_list, slowdown):
    loop_count = 0
    value = float(slowdown)

    if value < float(calculate_analysis_weight(threshold_list[0])) / float(ci_analysis_weight):
        print(float(calculate_analysis_weight(threshold_list[0])))
        print(YELLOW + BOLD + 'Optimal threshold found after ' + str(loop_count) + ' steps' + RESET)
        return threshold_list[0]
    if value > float(calculate_analysis_weight(threshold_list[len(threshold_list) - 1])) / float(ci_analysis_weight):
        print(float(calculate_analysis_weight(threshold_list[len(threshold_list) - 1])))
        print(YELLOW + BOLD + 'Optimal threshold found after ' + str(loop_count) + ' steps' + RESET)
        return threshold_list[len(threshold_list) - 1]

    low = 0
    high = len(threshold_list) - 1

    while low <= high:
        mid = int(low + (high - low) / 2)
        print(mid)
        loop_count += 1

        if value < float(calculate_analysis_weight(threshold_list[mid])) / float(ci_analysis_weight):
            high = mid - 1
        elif value > float(calculate_analysis_weight(threshold_list[mid])) / float(ci_analysis_weight):
            low = mid + 1
        else:
            print(YELLOW + BOLD + 'Optimal threshold found after ' + str(loop_count) + ' steps' + RESET)
            return threshold_list[mid]

    print(YELLOW + BOLD + 'Optimal threshold found after ' + str(loop_count) + ' steps' + RESET)
    print(YELLOW + BOLD + 'low 2-obj cost ' + str(calculate_analysis_weight(threshold_list[low])) + RESET)
    print(YELLOW + BOLD + 'high 2-obj cost ' + str(calculate_analysis_weight(threshold_list[high])) + RESET)

    if (float(calculate_analysis_weight(threshold_list[low])) / float(ci_analysis_weight) - value) < (
            value - float(calculate_analysis_weight(threshold_list[high])) / float(ci_analysis_weight)):
        return threshold_list[low]
    else:
        return threshold_list[high]


def run_main_analysis(args, app, oracular_file):
    args = [DOOP] + args
    args = args + ['-a', MAIN_ANALYSIS]
    args = args + ['--special-cs-methods', oracular_file]
    args = args + ['--Xstart-after-facts', app + "-facts"]
    args = args + ['--id', app + '-oracular']
    args = args + ['--Xsymlink-cached-facts']

    cmd = ' '.join(args)
    print(YELLOW + BOLD + 'Running main (Oracular-guided) analysis ...' + RESET)
    # print cmd
    os.system(cmd)


def run(doop_args, app, slowdown):
    run_pre_analyses(doop_args, app)
    run_oracular_analysis_classification(app, slowdown)
    run_main_analysis(doop_args, app, os.path.join(ORACULAR_CACHE, app, 'SpecialCSMethods.csv'))


def run_cached(doop_args, app, slowdown):
    run_oracular_analysis_classification(app, slowdown)
    run_main_analysis(doop_args, app, os.path.join(ORACULAR_CACHE, app, 'SpecialCSMethods.csv'))


if __name__ == '__main__':
    cache = sys.argv[-1]

    if cache == "cache":
        run_cached(sys.argv[1:-3], sys.argv[-3], sys.argv[-2])
    else:
        run(sys.argv[1:-2], sys.argv[-2], sys.argv[-1])
