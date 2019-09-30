#!/usr/bin/env python
import sys
import r2pipe
import re

debug = False

def main():
    argsNum = len(sys.argv)
    if (argsNum != 2 and argsNum != 4) or sys.argv[1] == '-h' or sys.argv[1] == '-help':
        usage()

    r = r2pipe.open(sys.argv[1], flags=['-e anal.timeout=600'])
    if (argsNum == 2):
        simple(r)
    elif (argsNum == 4):
        with open(sys.argv[2], 'r') as strings:
            with open(sys.argv[3], 'w') as out:
                xrefs(r, strings.readlines(), out)
    else:
        usage()
    r.quit()

def usage():
    print('Usage: radare-strings.py BINARY [STRINGS_FILE] [OUT_FILE]')
    print('')
    print('  BINARY                 a native code binary')
    print('  LOCALIZE_STRINGS_FILE  a file containing strings to localize')
    print('                         (expensive analysis)')
    print('  OUT_FILE               the file to use for output in localization mode')
    print('')
    print('Examples:')
    print('  radare-strings.py lib.so          # show all strings in library')
    print('  radare-strings.py lib.so in out   # show containing function for strings in file \'in\', write results to file \'out\'.')
    sys.exit(0)

def rCmd(r, c):
    if debug:
        print("Radare command: " + c)
    return r.cmd(c)

# Calculate string xrefs.
def xrefs(r, strings, out):
    print('Running xref analysis (output: ' + out.name + ')...')

    # analyze code using ESIL to find data references from code
    rCmd(r, 'aa')
    rCmd(r, 'aae')
    rCmd(r, 'aav')

    # Localize each string by (a) finding its offset and (b) following its xrefs.
    for line in strings:
        line = stripEOL(line)
        spaceIdx = line.find(" ")
        if spaceIdx == -1:
            print("ERROR, malformed line: " + line)
            continue
        addr = line[0:spaceIdx]
        s = line[(spaceIdx+1):]
        refs = rCmd(r, 'axt ' + addr)
        for ref in refs.splitlines():
            refParts = ref.split()
            func = refParts[0].encode('utf-8')
            if func != '(nofunc)':
                info = 'STRING_LOC:' + func + '\t' + s
                if (debug):
                    print(info)
                out.write(info + '\n')

def stripEOL(s):
    if s.endswith('\n'):
        return s[:-1]
    else:
        return s

# Simple mode, just list strings in the binary.
def simple(r):
    print('Running simple analysis...')
    # if you want to analyze binary file
    # rCmd(r, 'aaaa')
    stringLines = rCmd(r, 'iz')
    flagCounter = 0
    for stringLine in stringLines.splitlines():
        flagCounter += 1
        if flagCounter < 3:
            continue;
        lineParts = stringLine.split()
        string = ''.join(lineParts[7:])
        print(string.encode('utf-8'))

if __name__== "__main__":
	main()
