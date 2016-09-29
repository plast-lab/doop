#! /usr/bin/python
"""Database analysis results anayser

Usage:
  check-securibench-micro-results.py <db1>

Options:
  -h --help     Show this screen.
  --version     Show version.

"""

from docopt import docopt
import subprocess
import pandas as pd
from collections import defaultdict

correct_vulnerabilities = pd.Series({
    'Arrays1': 1,
    'Arrays2': 1,
    'Arrays3': 1,
    'Arrays4': 1,
    'Arrays5': 0,
    'Arrays6': 1,
    'Arrays7': 1,
    'Arrays8': 1,
    'Arrays9': 1,
    'Arrays10': 1,
    'Basic1': 1,
    'Basic2': 1,
    'Basic3': 1,
    'Basic4': 1,
    'Basic5': 3,
    'Basic6': 1,
    'Basic7': 1,
    'Basic8': 1,
    'Basic9': 1,
    'Basic10': 1,
    'Basic11': 2,
    'Basic12': 2,
    'Basic13': 1,
    'Basic14': 1,
    'Basic15': 1,
    'Basic16': 1,
    'Basic17': 1,
    'Basic18': 1,
    'Basic19': 1,
    'Basic20': 1,
    'Basic21': 4,
    'Basic22': 1,
    'Basic23': 3,
    'Basic24': 1,
    'Basic25': 1,
    'Basic26': 1,
    'Basic27': 1,
    'Basic28': 2,
    'Basic29': 2,
    'Basic30': 1,
    'Basic31': 2,
    'Basic32': 1,
    'Basic33': 1,
    'Basic34': 2,
    'Basic35': 6,
    'Basic36': 1,
    'Basic37': 1,
    'Basic38': 1,
    'Basic39': 1,
    'Basic40': 1,
    'Basic41': 1,
    'Basic42': 1,
    'Collections1': 1,
    'Collections2': 1,
    'Collections3': 2,
    'Collections4': 1,
    'Collections5': 1,
    'Collections6': 1,
    'Collections7': 1,
    'Collections8': 1,
    'Collections9': 0,
    'Collections10': 0,
    'Collections11': 1,
    'Collections11b': 1,
    'Collections12': 1,
    'Collections13': 1,
    'Collections14': 1,
    'Factories1': 1,
    'Factories2': 1,
    'Factories3': 1,
    'Inter1': 1,
    'Inter2': 2,
    'Inter3': 1,
    'Inter4': 1,
    'Inter5': 1,
    'Inter6': 1,
    'Inter7': 1,
    'Inter8': 1,
    'Inter9': 2,
    'Inter10': 2,
    'Inter11': 1,
    'Inter12': 1,
    'Inter13': 1,
    'Inter14': 1,
    'Pred1': 0,
    'Pred2': 0,
    'Pred3': 0,
    'Pred4': 1,
    'Pred5': 1,
    'Pred6': 0,
    'Pred7': 0,
    'Pred8': 1,
    'Pred9': 1,
    'Refl1': 1,
    'Refl2': 1,
    'Refl3': 1,
    'Refl4': 1,
    'Sanitizers1': 1,
    'Sanitizers2': 0,
    'Sanitizers3': 0,
    'Sanitizers4': 2,
    'Sanitizers5': 1,
    'Sanitizers6': 0,
    'Session1': 1,
    'Session2': 1,
    'Session3': 1,
    'StrongUpdates1': 0,
    'StrongUpdates2': 0,
    'StrongUpdates3': 0,
    'StrongUpdates4': 1,
    'StrongUpdates5': 0,
    'Aliasing1': 1,
    'Aliasing2': 0,
    'Aliasing3': 1,
    'Aliasing4': 1,
    'Aliasing5': 1,
    'Aliasing6': 7,
    'Datastructures1': 1,
    'Datastructures2': 1,
    'Datastructures3': 1,
    'Datastructures4': 1,
    'Datastructures5': 1,
    'Datastructures6': 1
})

query = '_(?invocation, ?obj) <- LeakingTaintedInformation(_, ?invocation, _, ?obj).'
def getProcess(db):
    process = subprocess.run(['bloxbatch', '-db', db, '-query',query],
                             stdout=subprocess.PIPE, universal_newlines=True)
    return process

def parseOut(out):
    res = dict((k,0) for k in correct_vulnerabilities.index)
    for line in out.split('\n'):
        linesplit = line.split(', ')
        if len(linesplit) < 2: continue
        try:
            if 'securibench.micro' not in linesplit[0]:
                continue
            if 'securibench.micro' not in linesplit[1]:
                continue
            interesting = linesplit[1].split('securibench.micro')[1].split('.doGet')[0].split('.')[-1].strip()
            res[interesting]+=1
        except:
            import pdb; pdb.set_trace()
            pass
    return res

if __name__ == '__main__':
    arguments = docopt(__doc__, version='0.1')
    detected = pd.Series(parseOut(getProcess(arguments['<db1>']).stdout)).sort_index()
    diff = (correct_vulnerabilities - detected)
    print(diff[diff != 0].sort_values())
    print('Recall: %f%%'%((139 - diff[diff > 0].count())/1.39))
    print('%d false positives'%int(diff[diff < 0].sum() * -1))

