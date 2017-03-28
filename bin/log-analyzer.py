#! /usr/bin/env python
#
# Script used to analyze log files created with BloxBatch.
#
# Currently supports log files created with debugDetail@factbus (the default) and
# debugDetail@benchmark. Has been tested with log files generated from LB version 3.8.
#
# Use -h for details on the options.
#
# Remember to keep the version of this script in sync with the supported LB engine versions.
#

import argparse
import sys
import re
import difflib

#
# Helper functions
#
def sort_dict_by_value(adict):
    return sorted(adict.items(), key=lambda (k,v): (v,k), reverse = True)

def sort_tuple_list_by_value(alist):
    return sorted(alist, key=lambda (k,v): v, reverse = True)

def print_predicates(predicates, unit = 's'):
    for tuple in predicates:
        if tuple[1] != 0:
            print tuple[0] + ' => ' + str(tuple[1]) + unit + "\n"

#
# Log file iterators
#
#  They return a tuple (predicate, time, facts) where:
#
#     predicate - is a string representing the executed predicate
#     time - is a float representing how long the execution took
#     facts = is an int representing how many facts were derived
#
class Benchmark:
    """Iterator for looping over entries in a benchmark log file."""
    def __init__(self, f):
        self.file = f
    def __iter__(self):
        return self
    def next(self):
        for line in self.file:
            if re.search('DEBUG_DETAIL. benchmark cache predicate', line):
                l = line.split(' ')
                pred = l[6]
                facts = int(l[4])
                time = float(l[7])
                return (pred, time, facts)
        raise StopIteration                

class Factbus:
    """Iterator for looping over entries in a factbus log file."""
    def __init__(self, f):
        self.file = f
        self.state = 0
    def __iter__(self):
        return self
    def next(self):
        for line in self.file:
            # starting a factbus
            if self.state == 0 and (re.search('Full evaluation', line) or re.search('Full aggregation', line) or re.search('Putback evaluation', line) or re.search('Assertion evaluation', line) or re.search('Retraction evaluation', line)):
                self.state = 1
                pred = ''
            # getting the predicate signature            
            elif self.state == 1:
                if re.search('DEBUG_DETAIL', line):
                    self.state = 2
                else:
                    pred += line[33:]
            elif self.state == 2 and re.search('new facts', line):
                l = line.split(' ')
                if 'derived' == l[3]:
                    s = l[7]
                    facts = int(l[4])
                else:
                    s = l[6]
                    facts = 0
                # s will be '(xyz' so we have to remove the (
                time = float(s[1:])        
                self.state = 0
                return (pred, time, facts)
        raise StopIteration


#
# Functions to process files
#
def aggregate(iterator, only_no_facts = False):
    """ Aggregates the records of this iterator.
    
    Returns a 3-tuple (dict, int, float) with predicate->time, count and total.
    """
    predicates = dict()
    count = 0
    total = float(0)
    for (pred, time, facts) in iterator:
        if facts == 0 or not only_no_facts:
            if not pred in predicates:
                predicates[pred] = 0
            predicates[pred] += time
            count += 1
            total += time
    return (predicates, count, total)

def collect(iterator, only_no_facts = False):
    """ Collects the records of this iterator.
    
    Returns a 3-tuple (list, int, float) with predicates, count and total.
    """
    predicates = []
    count = 0
    total = float(0)
    for (pred, time, facts) in iterator:
        if facts == 0 or not only_no_facts:
            predicates.append((pred, time))
            count += 1
            total += time
    return (predicates, count, total)

def compare_exact(main, baseline):
    """ Compares the results of a main file with those of a baseline using exact predicate match.
    
    Returns a list of predicate tuples with (predicate definition, measure difference).
    """
    diff = dict()
    for key, value in main.iteritems():
        if key in baseline:
            diff[key] = value - baseline[key]
        else:
            diff[key] = value 
            
    return diff.items()

def compare(main, baseline):
    """ Compares the results of a main file with those of a baseline using fuzzy matching.
    
    Returns a list of predicate tuples with (predicate definition, measure difference).
    """
    diff = dict()
    for key, value in main.iteritems():
        matches = difflib.get_close_matches(key, baseline.keys(), 1, 0.5)
        if len(matches) > 0:
            new_key = key + "Vs.\n" + matches[0]
            diff[new_key] = value - baseline[matches[0]]
            del baseline[matches[0]]
        else:
            new_key = key + "UNMATCHED.\n"
            diff[new_key] = value 
            
    return diff.items()



def process(args):
    """ Prints a sorted list of predicates that took more than 0 units to execute."""
    unit = 'ms' if args.benchmark else 's'
        
    # process main file
    mainIter = Benchmark(args.file) if args.benchmark else Factbus(args.file)
    mainRecords = collect(mainIter, args.nofacts) if args.noagg else aggregate(mainIter, args.nofacts)
    
    if (args.baseline):
        # also process baseline file, compare and print
        baselineIter = Benchmark(args.baseline) if args.benchmark else Factbus(args.baseline)
        baselineRecords = aggregate(baselineIter, args.nofacts)
        
        compare_function = compare_exact if args.exact else compare

        print_predicates(
            sort_tuple_list_by_value(compare_function(mainRecords[0], baselineRecords[0])),
            unit)
    else:
        # simply print predicates and perhaps the total
        predicates = mainRecords[0] if args.noagg else mainRecords[0].items()
        print_predicates(sort_tuple_list_by_value(predicates), unit)
        
    if (args.total):
        print str(mainRecords[1]) + " records total " + str(mainRecords[2]) + unit 


#
# Main script
#
parser = argparse.ArgumentParser(
    description='Analyzes a bloxbatch log file and prints a sorted list of records.')
parser.add_argument('file', metavar='FILE', type=argparse.FileType('r'), 
    help='the log file to analyze.')
parser.add_argument('-benchmark', '-bench', action='store_true',
    help='flags that the log file was created with a debugDetail@benchmark configuration. The default is degubDetail@factbus.')

group = parser.add_mutually_exclusive_group()
group.add_argument('-noagg', '-n', action='store_true',
    help='do not aggregate time measures based on predicate definitions.')
group.add_argument('-baseline', '-b', type=argparse.FileType('r'),
    help='a log file to compare against. Both files must have the same format and aggregation must be used.')

parser.add_argument('-nofacts', '-z', action='store_true',
    help='only process predicate executions that derived no facts.')
parser.add_argument('-exact', '-e', action='store_true',
    help='when comparing against a baseline, use exact predicate match. This is much faster than the default but will not match\
    predicates that have variables with artificial names or that slightly changed.')
parser.add_argument('-total', '-t', action='store_true',
    help='print the count of processed records and the total sum of measures.')
parser.add_argument('-version', action='version', version='%(prog)s 0.1 supports LB 3.8 ')


process(parser.parse_args())


























