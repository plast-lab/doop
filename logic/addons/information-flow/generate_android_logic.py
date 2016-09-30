import urllib.request

   
FLOWDROID_SOURCE = 'https://raw.githubusercontent.com/secure-software-engineering/soot-infoflow-android/develop/SourcesAndSinks.txt'

SOURCE_TEMPLATE = 'TaintSourceMethod("%s").'
SINK_TEMPLATE = 'LeakingSinkMethod(n, "%s") <- (n = 0); (n = 1) ; (n = 2) ; (n = 3).'

with urllib.request.urlopen(FLOWDROID_SOURCE) as response:
   raw_sources_and_sinks = response.read().decode('utf-8')

for line in raw_sources_and_sinks.split('\n'):
    # remove comments
    line = line.split('%')[0]
    
    # parse line
    if '->' not in line:
        continue

    meth, kind = [l.strip() for l in line.split('->')]

    if kind == '_SOURCE_':
        template = SOURCE_TEMPLATE
    elif kind == '_SINK_':
        template = SINK_TEMPLATE
    else:
        assert False, kind
    print(template%meth)
