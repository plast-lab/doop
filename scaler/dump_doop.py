#!/usr/bin/env python
import os
import sys
import shutil

INPUT = {
    'VPT':'Stats_Simple_InsensVarPointsTo'
}

REQUIRED_INPUT = {
    'scaler':[
        'CALL_EDGE', 'CALLSITEIN', 'DECLARING_CLASS_ALLOCATION', 'INST_METHODS',
		'OBJECT_IN', 'SPECIAL_OBJECTS', 'THIS_VAR',  'VAR_IN', 'VPT',
    ],
}

def dumpDoopResults(db_dir, dump_dir, app, query):
    file_name = INPUT.get(query, query) + '.csv'
    from_path = os.path.join(db_dir, file_name)
    dump_path = os.path.join(dump_dir, '%s.%s' % (app, query))
    if not os.path.exists(dump_dir):
        os.mkdir(dump_dir)
    shutil.copyfile(from_path, dump_path)

def dumpRequiredDoopResults(app, analysis, db_dir, dump_dir):
    print 'Dumping doop analysis results for %s...' % app
    for query in REQUIRED_INPUT[analysis]:
        dumpDoopResults(db_dir, dump_dir, app, query)

if __name__ == '__main__':
    [db_dir, dump_dir, app] = sys.argv[1:]
    dumpRequiredDoopResults(app, 'scaler', db_dir, dump_dir)
