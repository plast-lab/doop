#! /usr/bin/python

"""Database analysis results anayser

Usage:
  db-analyser.py <db1> --delta <db2>

Options:
  -h --help     Show this screen.
  --version     Show version.

"""
from docopt import docopt
import subprocess
import pandas as pd

def getProcess(db):
    process = subprocess.run(['bloxbatch', '-db', db, '-popCount'], stdout=subprocess.PIPE, universal_newlines=True)
    return process

def parseOut(out):
    res = {}
    for line in out.split('\n'):
        linesplit = line.split(': ')
        try:
            res[linesplit[0]] = int(linesplit[1])
        except Exception:
            pass
    return pd.Series(res)


if __name__ == '__main__':
    arguments = docopt(__doc__, version='0.1')
    ratios = ((parseOut(getProcess(arguments['<db1>']).stdout) * 1.0)
              / parseOut(getProcess(arguments['<db2>']).stdout))
    pd.set_option('display.max_rows', len(ratios))
    print(ratios.sort_values())

