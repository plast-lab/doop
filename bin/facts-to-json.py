#!/usr/bin/python

import csv
import sys

if (len(sys.argv) < 2):
    print("Usage: facts-to-json.py file.facts")
    exit(0)

with open(sys.argv[1], 'rb') as csvfile:
    totalRows = sum(1 for line in csvfile)

with open(sys.argv[1], 'rb') as csvfile:
    r = csv.reader(csvfile, delimiter="\t")
    rowNum = 0
    print("[\n")
    for row in r:
        rowNum = rowNum + 1
        fields = []
        count = 0
        for field in row:
            fields = fields + ['"field' + str(count) + '" : "' + field + '"']
            count = count + 1
        sep = ",\n" if rowNum < totalRows else ""
        print("{ " + ", ".join(fields) + " }" + sep)
    print("]")
