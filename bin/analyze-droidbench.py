#! /usr/bin/python -u
"""Analyzes Droidbench benchmarks and checks the results

Usage:
  analyze-droidbench.py [--subset <SUBSET>] [--parallel] [--dry] [--full-results]
  analyze-droidbench.py -h | --help
  analyze-droidbench.py --version

Options:
  -h --help          Show this screen.
  --dry              Don't run analyses, only show report.
  --full-results     Generate full analysis results for each benchmark. May require > 1T disk space for all benchmarks.
  --parallel         Enable parallel mode.
  --subset <SUBSET>  The benchmarks subset to use, examples: GeneralJava, GeneralJava/Clone1.
  --version          Show version.

Notes:

* This script assumes the Droidbench repo is available locally as 'Droidbench'.

* To set analysis type/parameters, edit 'experiments_full' in analyze-droidbench.py or 'all_params' in doop_runner.py.

"""
from docopt import docopt
import subprocess
import pandas as pd
from collections import defaultdict
from doop_runner import *
from datetime import datetime
import os.path
import multiprocessing

TOTAL_PROCESSES = max(multiprocessing.cpu_count() // 16, 1)


benchmarks = {
'Aliasing/Merge1': 0,
'AndroidSpecific/ApplicationModeling1': 1,
'AndroidSpecific/DirectLeak1': 1,
'AndroidSpecific/InactiveActivity': 0,
'AndroidSpecific/Library2': 1,
'AndroidSpecific/LogNoLeak': 0,
'AndroidSpecific/Obfuscation1': 1,
'AndroidSpecific/Parcel1': 1,
'AndroidSpecific/PrivateDataLeak1': 1,
'AndroidSpecific/PrivateDataLeak2': 1,
'AndroidSpecific/PrivateDataLeak3': 2,
'AndroidSpecific/PublicAPIField1': 1,
'AndroidSpecific/PublicAPIField2': 1,
'ArraysAndLists/ArrayAccess1': 0,
'ArraysAndLists/ArrayAccess2': 0,
'ArraysAndLists/ArrayCopy1': 1,
'ArraysAndLists/ArrayToString1': 1,
'ArraysAndLists/HashMapAccess1': 0,
'ArraysAndLists/ListAccess1': 0,
'ArraysAndLists/MultidimensionalArray1': 1,
'Callbacks/AnonymousClass1': 1, # previously 2
'Callbacks/Button1': 1,
'Callbacks/Button2': 3,
'Callbacks/Button3': 1,
'Callbacks/Button4': 1,
'Callbacks/Button5': 1,
'Callbacks/LocationLeak1': 2,
'Callbacks/LocationLeak2': 2,
'Callbacks/LocationLeak3': 1,
'Callbacks/MethodOverride1': 1,
'Callbacks/MultiHandlers1': 0,
'Callbacks/MultiHandlers1': 0,
'Callbacks/Ordering1': 0,
'Callbacks/RegisterGlobal1': 1,
'Callbacks/RegisterGlobal2': 1,
'Callbacks/Unregister1': 0,
#'EmulatorDetection/ContentProvider1': 2,
#'EmulatorDetection/IMEI1': 2,
#'EmulatorDetection/PlayStore1': 2,
'FieldAndObjectSensitivity/FieldSensitivity1': 0,
'FieldAndObjectSensitivity/FieldSensitivity2': 0,
'FieldAndObjectSensitivity/FieldSensitivity3': 1,
'FieldAndObjectSensitivity/FieldSensitivity4': 0,
'FieldAndObjectSensitivity/InheritedObjects1': 1,
'FieldAndObjectSensitivity/ObjectSensitivity1': 0,
'FieldAndObjectSensitivity/ObjectSensitivity2': 0,
'GeneralJava/Clone1': 1,
'GeneralJava/Exceptions1': 1,
'GeneralJava/Exceptions2': 1,
'GeneralJava/Exceptions3': 1,
'GeneralJava/Exceptions4': 1,
'GeneralJava/FactoryMethods1': 2,
'GeneralJava/Loop1': 1,
'GeneralJava/Loop2': 1,
'GeneralJava/Serialization1': 1,
'GeneralJava/SourceCodeSpecific1': 1,
'GeneralJava/StartProcessWithSecret1': 1,
'GeneralJava/StaticInitialization1': 1,
'GeneralJava/StaticInitialization2': 1,
'GeneralJava/StaticInitialization3': 1,
'GeneralJava/StringFormatter1': 1,
'GeneralJava/StringPatternMatching1': 1,
'GeneralJava/StringToCharArray1': 1,
'GeneralJava/StringToOutputStream1': 1,
'GeneralJava/UnreachableCode': 0,
'GeneralJava/VirtualDispatch1': 1,
'GeneralJava/VirtualDispatch2': 1,
'GeneralJava/VirtualDispatch3': 0,
'GeneralJava/VirtualDispatch4': 0,
#'ImplicitFlows/ImplicitFlow1': 2,
#'ImplicitFlows/ImplicitFlow2': 2,
#'ImplicitFlows/ImplicitFlow3': 2,
#'ImplicitFlows/ImplicitFlow4': 2,
#'InterAppCommunication/SendSMS': 1,
'InterAppCommunication/StartActivityForResult1': 2, # previously 1
'InterComponentCommunication/ActivityCommunication1': 1,
'InterComponentCommunication/ActivityCommunication2': 1,
'InterComponentCommunication/ActivityCommunication3': 1,
'InterComponentCommunication/ActivityCommunication4': 1,
'InterComponentCommunication/ActivityCommunication5': 1,
'InterComponentCommunication/ActivityCommunication6': 1,
'InterComponentCommunication/ActivityCommunication7': 1,
'InterComponentCommunication/ActivityCommunication8': 1,
'InterComponentCommunication/BroadcastTaintAndLeak1': 1,
'InterComponentCommunication/ComponentNotInManifest1': 0 ,
'InterComponentCommunication/EventOrdering1': 1,
'InterComponentCommunication/IntentSink1': 1,
'InterComponentCommunication/IntentSink2': 1,
'InterComponentCommunication/IntentSource1': 1, # Manually changed, I only found one source/sink
'InterComponentCommunication/ServiceCommunication1': 1,
'InterComponentCommunication/SharedPreferences1': 1,
'InterComponentCommunication/Singletons1': 1,
'InterComponentCommunication/UnresolvableIntent1': 2 ,
'Lifecycle/ActivityLifecycle1': 1,
'Lifecycle/ActivityLifecycle2': 1,
'Lifecycle/ActivityLifecycle3': 1,
'Lifecycle/ActivityLifecycle4': 1,
'Lifecycle/ActivitySavedState1': 1,
'Lifecycle/ApplicationLifecycle1': 1,
'Lifecycle/ApplicationLifecycle2': 1,
'Lifecycle/ApplicationLifecycle3': 1,
'Lifecycle/AsynchronousEventOrdering1': 1,
'Lifecycle/BroadcastReceiverLifecycle1': 1,
'Lifecycle/BroadcastReceiverLifecycle2': 1,
'Lifecycle/EventOrdering1': 1,
'Lifecycle/FragmentLifecycle1': 1,
'Lifecycle/FragmentLifecycle2': 1,
'Lifecycle/ServiceLifecycle1': 1,
'Lifecycle/ServiceLifecycle2': 1,
'Lifecycle/SharedPreferenceChanged1': 1,
'Reflection/Reflection1': 1,
'Reflection/Reflection2': 1,
'Reflection/Reflection3': 1,
'Reflection/Reflection4': 1,
'Threading/AsyncTask1': 1,
'Threading/Executor1': 1,
'Threading/JavaThread1': 1,
'Threading/JavaThread2': 1,
'Threading/Looper1': 1
}

results = defaultdict(int)

def print_report():
    df=pd.DataFrame.from_dict(dict(
        actual_errors = benchmarks,
        predicted_errors=results
    ))
    df['benchmark_group'] = df.index.map(lambda a: a.split('/')[0])
    df['benchmark'] = df.index.map(lambda a: a.split('/')[1])
    max_zero = lambda a: max(a, 0)
    df['f_pos'] = (df.predicted_errors - df.actual_errors).map(max_zero)
    df['f_neg'] = (df.actual_errors - df.predicted_errors).map(max_zero)
    out = df.groupby('benchmark_group').sum()
    print(out)
    csvfilename = datetime.now().strftime('droidbench_%H_%M_%d_%m_%Y.csv')
    print('Saving CSV at %s'%csvfilename)
    out.to_csv(csvfilename)


experiments_full = [
#       ['s. 2obj+H', ['-a', 'selective-2-object-sensitive+heap']],
#       ['s. 2obj+H refl', ['-a', 'selective-2-object-sensitive+heap'] + REFL_PARAMS],
       ['ins', ['-a', 'context-insensitive']],
#       ['ins refl', ['-a', 'context-insensitive'] + REFL_PARAMS],
#       ['1call+H', ['-a', '1-call-site-sensitive+heap']],
#       ['1call+H refl', ['-a', '1-call-site-sensitive+heap'] + REFL_PARAMS],
#       ['1type+H', ['-a', '1-type-sensitive+heap']],
#       ['1type+H refl', ['-a', '1-type-sensitive+heap'] + REFL_PARAMS],
#       ['2obj+H', ['-a', '2-object-sensitive+heap']],
#       ['2obj+H refl', ['-a', '2-object-sensitive+heap'] + REFL_PARAMS],
#       ['df', ['-a', 'data-flow']],
]

class Experiment:
    def __init__(self, pname, params, runner, pattern):
        self.pname = pname
        self.params = params
        self.runner = runner
        self.pattern = pattern

    def __call__(self, benchmark):
        if self.pattern is None or self.pattern in benchmark:
            benchmark_id = benchmark.replace('/', '_')
            elapsed_time, dbdir = self.runner.run_doop(benchmark_id, ['-i', 'DroidBench/apk/' + benchmark + '.apk'] + self.params)
            # elapsed_time = 100
            # dbdir = 'out/GeneralJava_Clone1/database'
            res=check_benchmark(benchmark, dbdir)
            print('%s, %d seconds. %s: %d/%d              %s OK'%(
                self.pname, elapsed_time, benchmark,res, benchmarks[benchmark], '' if res == benchmarks[benchmark] else 'NOT'))
            print('DB available at: '+dbdir)
            print()
            return res

def run_benchmarks(dry, full_results, benchmarks, pattern):
    # baseParams = ['--information-flow', 'android', '--information-flow-high-soundness', '--souffle-mode', 'compiled']
    baseParams = ['--information-flow', 'android', '--cache', '--souffle-mode', 'compiled', '--stats', 'none']
    if not full_results:
        baseParams += ['--no-standard-exports']
    runner = DoopRunner('android_25_fulljars', dry, baseParams)

    for pname, params in experiments_full:
        experiment = Experiment(pname, params, runner, pattern)
        with multiprocessing.Pool(TOTAL_PROCESSES) as pool:
            sorted_benchmarks = sorted(list(benchmarks))
            if TOTAL_PROCESSES > 1:
                print('Running a single benchmark first for testing and caching..')
                pool.map(experiment, sorted_benchmarks[:1])
                print('Parallelizing..')
            res = pool.map(experiment, sorted_benchmarks)

        for b,r in zip(sorted(list(benchmarks)), res):
            results[b] = r

def get_query_process(db, query):
    process = subprocess.run(
        ['bloxbatch', '-db', db, '-query',query],
        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, universal_newlines=True)
    return process

droidbench_app_patterns = ['lu.uni.snt', 'de.ecspride', 'edu.mit', 'org.cert']

def in_droidbench(str):
    return any(pattern in str for pattern in droidbench_app_patterns)

def check_benchmark(benchmark, db):
    res = 0
    sinks = set()
    for source, sink in parseleaks(db):
        # checks provenance or source
        if not in_droidbench(source) or not in_droidbench(sink):
            continue
        sinks.add(sink)
    return len(sinks)


if __name__ == '__main__':
    arguments = docopt(__doc__, version='0.2')
    parallelize = arguments['--parallel']
    TOTAL_PROCESSES = max(multiprocessing.cpu_count() // 16, 1) if parallelize else 1

    try:
        run_benchmarks(arguments['--dry'], arguments['--full-results'], benchmarks, arguments['--subset'])
    finally:
        print_report()
